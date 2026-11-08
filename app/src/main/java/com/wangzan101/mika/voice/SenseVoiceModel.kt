package com.wangzan101.mika.voice

import android.content.Context
import android.os.Build
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * 阿里 SenseVoice 离线识别模型的存放与下载:模型不随安装包,由用户在设置 › 语音转文字里按需下载,
 * 存在应用私有目录的 sensevoice/ 下(两个文件:model.int8.onnx + tokens.txt)。
 * 下载先试国内镜像(hf-mirror),不通再走 Hugging Face 本站;下完按字节数和 SHA-256 校验,坏的不要。
 */
object SenseVoiceModel {
    const val MODEL_FILE = "model.int8.onnx"
    const val TOKENS_FILE = "tokens.txt"
    const val MODEL_SIZE = 237_115_547L
    const val TOKENS_SIZE = 315_894L
    const val MODEL_SHA256 = "12ca1a2ae7ecf3e0019ef2822307ee0b5cadc9196569e379b4c4026f8205276d"
    const val TOTAL_SIZE = MODEL_SIZE + TOKENS_SIZE

    private const val REPO = "csukuangfj/sherpa-onnx-sense-voice-zh-en-ja-ko-yue-int8-2025-09-09"
    private val HOSTS = listOf("https://hf-mirror.com", "https://huggingface.co")

    sealed interface State {
        data object NotDownloaded : State
        data class Downloading(val done: Long, val total: Long) : State
        data object Ready : State
        data object Failed : State
    }

    private val _state = MutableStateFlow<State>(State.NotDownloaded)
    val state: StateFlow<State> = _state.asStateFlow()

    @Volatile
    private var downloading = false

    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    fun dir(context: Context): File = File(context.applicationContext.filesDir, "sensevoice")

    fun modelFile(context: Context): File = File(dir(context), MODEL_FILE)

    fun tokensFile(context: Context): File = File(dir(context), TOKENS_FILE)

    /** 识别运行库只有 arm64 版;别的架构的设备选不了这个引擎。 */
    val supportedAbi: Boolean get() = Build.SUPPORTED_ABIS.any { it == "arm64-v8a" }

    /** 两个文件都在、大小都对,才算下好了(内容校验在下载完成时做过)。 */
    fun isReady(context: Context): Boolean = supportedAbi &&
        modelFile(context).let { it.isFile && it.length() == MODEL_SIZE } &&
        tokensFile(context).let { it.isFile && it.length() == TOKENS_SIZE }

    /** 进设置页时对一下磁盘状态;正在下载时不打断它的进度显示。 */
    fun refresh(context: Context) {
        if (_state.value is State.Downloading) return
        _state.value = if (isReady(context)) State.Ready else State.NotDownloaded
    }

    /** 删掉模型并作废已建好的识别器;正在用这个引擎时界面那边会按「模型缺失」处理。 */
    fun delete(context: Context) {
        SenseVoiceEngine.invalidate()
        dir(context).deleteRecursively()
        if (_state.value !is State.Downloading) _state.value = State.NotDownloaded
    }

    /** 下载两个文件(先小后大);任一步失败都清掉半成品并记为 [State.Failed],再点一次就是重试。 */
    suspend fun download(context: Context) {
        if (downloading) return
        downloading = true
        try {
            withContext(Dispatchers.IO) {
                val dir = dir(context).apply { mkdirs() }
                var done = 0L
                _state.value = State.Downloading(0, TOTAL_SIZE)
                for ((name, size, sha256) in FILES) {
                    val target = File(dir, name)
                    if (target.isFile && target.length() == size && (sha256 == null || sha256Of(target) == sha256)) {
                        done += size
                        _state.value = State.Downloading(done, TOTAL_SIZE)
                        continue
                    }
                    downloadOne(name, File(dir, "$name.part"), size, sha256) { written ->
                        _state.value = State.Downloading(done + written, TOTAL_SIZE)
                    }
                    check(File(dir, "$name.part").renameTo(target)) { "rename failed: $name" }
                    done += size
                }
                check(isReady(context)) { "model files incomplete" }
                _state.value = State.Ready
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            dir(context).listFiles()?.filter { it.name.endsWith(".part") }?.forEach { it.delete() }
            _state.value = State.Failed
        } finally {
            downloading = false
        }
    }

    /** 一个文件:依次试每个下载源,流式写盘同时算 SHA-256(只模型文件需要);大小或校验对不上就换源重试。 */
    private fun downloadOne(name: String, part: File, size: Long, sha256: String?, onProgress: (Long) -> Unit) {
        var lastError: Exception? = null
        for (host in HOSTS) {
            try {
                val request = Request.Builder().url("$host/$REPO/resolve/main/$name").build()
                http.newCall(request).execute().use { response ->
                    check(response.isSuccessful) { "HTTP ${response.code} from $host" }
                    val body = response.body ?: error("empty body from $host")
                    val digest = sha256?.let { MessageDigest.getInstance("SHA-256") }
                    var written = 0L
                    body.byteStream().use { input ->
                        FileOutputStream(part).use { output ->
                            val buffer = ByteArray(256 * 1024)
                            while (true) {
                                val n = input.read(buffer)
                                if (n < 0) break
                                output.write(buffer, 0, n)
                                digest?.update(buffer, 0, n)
                                written += n
                                if (written % (4 * 1024 * 1024) < buffer.size) onProgress(written)
                            }
                        }
                    }
                    onProgress(written)
                    check(written == size) { "$name size $written != $size" }
                    if (digest != null) {
                        val actual = digest.digest().joinToString("") { "%02x".format(it) }
                        check(actual == sha256) { "$name sha256 mismatch" }
                    }
                    return
                }
            } catch (e: Exception) {
                lastError = e
                part.delete()
            }
        }
        throw lastError ?: IllegalStateException("download failed: $name")
    }

    private fun sha256Of(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(1024 * 1024)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                digest.update(buffer, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private data class FileSpec(val name: String, val size: Long, val sha256: String?)

    private val FILES = listOf(
        FileSpec(TOKENS_FILE, TOKENS_SIZE, null),
        FileSpec(MODEL_FILE, MODEL_SIZE, MODEL_SHA256),
    )
}
