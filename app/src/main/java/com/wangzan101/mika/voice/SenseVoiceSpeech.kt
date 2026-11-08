package com.wangzan101.mika.voice

import android.content.Context
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineSenseVoiceModelConfig
import com.wangzan101.mika.core.model.ModelError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * sherpa-onnx 的离线识别器,进程内共用一个:建一次要读 237 MB 模型,每次听写都重建太浪费。
 * 原生解码是阻塞调用且识别器不可重入,用 [Mutex] 串行;模型被删/重下时 [invalidate] 作废重建。
 */
internal object SenseVoiceEngine {
    private val mutex = Mutex()

    @Volatile
    private var recognizer: OfflineRecognizer? = null

    @Volatile
    private var recognizerKey: String? = null

    fun invalidate() {
        synchronized(this) {
            recognizer?.release()
            recognizer = null
            recognizerKey = null
        }
    }

    /** 整段解码:[samples] 是 16 kHz 单声道浮点样本(-1..1)。失败抛异常,由会话那边转成 Failed。 */
    suspend fun decode(context: Context, samples: FloatArray): String = mutex.withLock {
        withContext(Dispatchers.IO) {
            val rec = recognizer(context)
            val stream = rec.createStream()
            try {
                stream.acceptWaveform(samples, Wav.RATE)
                rec.decode(stream)
                rec.getResult(stream).text.trim()
            } finally {
                stream.release()
            }
        }
    }

    private fun recognizer(context: Context): OfflineRecognizer {
        val key = SenseVoiceModel.modelFile(context).absolutePath
        recognizer?.takeIf { recognizerKey == key }?.let { return it }
        synchronized(this) {
            recognizer?.takeIf { recognizerKey == key }?.let { return it }
            val config = OfflineRecognizerConfig(
                featConfig = FeatureConfig(sampleRate = Wav.RATE, featureDim = 80),
                modelConfig = OfflineModelConfig(
                    senseVoice = OfflineSenseVoiceModelConfig(
                        model = SenseVoiceModel.modelFile(context).absolutePath,
                        language = "auto",
                        useInverseTextNormalization = true,
                    ),
                    tokens = SenseVoiceModel.tokensFile(context).absolutePath,
                    numThreads = 4,
                    provider = "cpu",
                ),
            )
            // 必须传 null:assetManager 非空时 sherpa 会走 newFromAsset、按 assets 相对路径找文件,
            // 而这里是私有目录的绝对路径,走 assets 通道必然创建失败;传 null 才走 newFromFile 按文件路径加载
            return OfflineRecognizer(null, config).also {
                recognizer = it
                recognizerKey = key
            }
        }
    }
}

/**
 * 本地离线识别(SenseVoice):整段模型没有真正的流式输出,按着说话时每约 1.4 秒拿已录到的全部音频
 * 重解一次当中间结果(旗舰机上十秒内音频一次解码只要零点几秒),松开后再整段解一次定稿。
 * 录音走 [PcmRecorder] 的流式模式:音频块一边喂给中间结果,一边由录音器自己留底,松开时取全段。
 * 超过 [PARTIAL_MAX_SEC] 秒后不再做中间解码(整段重解越来越贵),只等松开定稿;60 秒封顶同其他非流式引擎。
 */
class SenseVoiceSpeech(
    private val recorder: PcmRecorder,
    context: Context,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) : SpeechSession {
    private val appContext = context.applicationContext
    private val out = SessionEvents()
    override val events: Flow<SpeechEvent> = out.flow
    override val live = true

    private var levels: Job? = null
    private var consumer: Job? = null
    private var work: Job? = null
    private var released = false

    init {
        if (!recorder.start()) {
            out.finish(SpeechEvent.Failed(SpeechFailure.MicUnavailable))
        } else {
            levels = scope.launch { recorder.level.collect { out.send(SpeechEvent.Level(it)) } }
            consumer = scope.launch { consumePartials() }
        }
    }

    /** 攒音频块;够长且到了间隔,就把到目前为止的全部音频解一次,结果当作新的中间全文发出去。 */
    private suspend fun consumePartials() {
        val chunks = ArrayList<ShortArray>()
        var total = 0
        var lastDecodeAt = 0L
        var lastText = ""
        for (chunk in recorder.chunks) {
            chunks.add(chunk)
            total += chunk.size
            val now = System.currentTimeMillis()
            if (released || total < MIN_PARTIAL_SAMPLES || total > PARTIAL_MAX_SAMPLES || now - lastDecodeAt < PARTIAL_EVERY_MS) continue
            lastDecodeAt = now
            val samples = FloatArray(total)
            var offset = 0
            for (c in chunks) {
                for (s in c) samples[offset++] = s / 32768f
            }
            val text = runCatching { SenseVoiceEngine.decode(appContext, samples) }
                .getOrNull()?.takeIf { it.isNotEmpty() } ?: continue
            if (!released && text != lastText) {
                lastText = text
                out.send(Segments.split(text))
            }
        }
    }

    @Synchronized
    override fun release() {
        if (released || out.finished || out.cancelled) return
        released = true
        levels?.cancel()
        consumer?.cancel()
        // stop 会等读循环退出(短暂阻塞),放到后台线程,别卡住主线程
        work = scope.launch {
            val pcm = recorder.stop()
            if (pcm.size < Wav.RATE * ProviderSpeech.MIN_MS / 1000) {
                out.finish(SpeechEvent.Final(""))
                return@launch
            }
            out.send(SpeechEvent.Converting)
            val samples = FloatArray(pcm.size) { pcm[it] / 32768f }
            val result = try {
                SpeechEvent.Final(SenseVoiceEngine.decode(appContext, samples))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                // 原生层加载/解码失败可能抛 Error(如链接、初始化错误),一并转成失败提示,不能让它掀翻整个 App
                SpeechEvent.Failed(SpeechFailure.Provider(ModelError.BadResponse(e.message.orEmpty(), e)))
            }
            out.finish(result)
        }
    }

    @Synchronized
    override fun cancel() {
        if (!out.cancel()) return
        levels?.cancel()
        consumer?.cancel()
        work?.cancel()
        recorder.cancel()
    }

    companion object {
        /** 短于这么多音频不做中间解码(刚开口,解出来多半是空或半个字)。 */
        private const val MIN_PARTIAL_SAMPLES = Wav.RATE * 4 / 5

        /** 中间解码的间隔;一次整段解码在旗舰机上远快于这个间隔。 */
        private const val PARTIAL_EVERY_MS = 1_400L

        /** 超过这么长就只等定稿:整段重解的成本随长度涨,中间结果也越来越不值。 */
        private const val PARTIAL_MAX_SAMPLES = Wav.RATE * 30
    }
}
