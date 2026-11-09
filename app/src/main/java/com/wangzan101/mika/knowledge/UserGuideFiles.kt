package com.wangzan101.mika.knowledge

import com.wangzan101.mika.core.knowledge.UserAppGuide
import com.wangzan101.mika.core.knowledge.UserAppGuideParse
import com.wangzan101.mika.core.knowledge.UserAppGuideSkeleton
import com.wangzan101.mika.core.knowledge.UserAppGuides
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * 用户自建 App 说明的落盘:一份说明一个目录,APP.md 是正文,附件在 references/ 下。
 * 校验在写之前由调用方跑 [UserAppGuideParse.validate];这里只管存取,不让坏文件进目录。
 */
class UserGuideFiles(dir: File) : UserAppGuides {
    private val root = File(File(dir, "guides"), "apps")

    @Synchronized override fun list(): List<UserAppGuide> =
        root.listFiles { f -> f.isDirectory && UserAppGuideParse.ID.matches(f.name) }.orEmpty()
            .sortedBy { it.name }
            .mapNotNull { d -> read(d) }

    @Synchronized override fun get(id: String): UserAppGuide? {
        if (!UserAppGuideParse.ID.matches(id)) return null
        return read(File(root, id))
    }

    private fun read(d: File): UserAppGuide? {
        val main = File(d, "APP.md").takeIf { it.isFile } ?: return null
        val markdown = try { main.readText() } catch (e: Exception) { return null }
        val refs = File(d, "references")
        val files = refs.walkTopDown().filter { it.isFile && it.extension == "md" }
            .map { it.relativeTo(d).invariantSeparatorsPath to it.readText() }
            .toMap()
        return UserAppGuide(d.name, markdown, files)
    }

    /** 整份写下:正文覆盖、附件按 [UserAppGuide.files] 对齐(不在表里的旧附件删掉)。 */
    @Synchronized override fun put(guide: UserAppGuide) {
        if (!UserAppGuideParse.ID.matches(guide.id)) return
        val d = File(root, guide.id).apply { mkdirs() }
        atomic(File(d, "APP.md"), guide.markdown)
        val refs = File(d, "references")
        val wanted = guide.files.keys
        refs.walkTopDown().filter { it.isFile }.forEach { f ->
            if (f.relativeTo(d).invariantSeparatorsPath !in wanted) f.delete()
        }
        for ((path, text) in guide.files) {
            val f = File(d, path)
            f.parentFile?.mkdirs()
            atomic(f, text)
        }
        if (refs.isDirectory && refs.listFiles().isNullOrEmpty()) refs.delete()
    }

    @Synchronized override fun remove(id: String) {
        if (!UserAppGuideParse.ID.matches(id)) return
        File(root, id).deleteRecursively()
    }

    /** 给一个已装 App 新建说明:生成不冲突的 id 和骨架,写下后返回整份。 */
    @Synchronized override fun create(packageName: String, display: String): UserAppGuide {
        val base = UserAppGuideParse.idFor(packageName)
        var id = base
        var n = 2
        while (File(root, id).exists()) { id = "$base-${n++}" }
        val guide = UserAppGuide(id, UserAppGuideSkeleton.markdown(id, display, packageName))
        put(guide)
        return guide
    }

    private fun atomic(file: File, content: String) {
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(content)
        Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
    }
}
