package com.wangzan101.mika.core.knowledge

/**
 * 用户自己建的 App 说明:一份 = 一个目录,正文是 APP.md 全文(含头部),附件是 references/ 下的 .md。
 * 与内置知识同构(同样的头部、同样的正文),所以能走同一条注入与读取通道;同名时用户版整份优先。
 */
data class UserAppGuide(
    val id: String,
    /** APP.md 全文(含头部)。 */
    val markdown: String,
    /** 附件:相对路径(如 references/upgrade.md) → 全文。 */
    val files: Map<String, String> = emptyMap(),
)

/** 用户 App 说明的存取;落盘由 app 层实现。每次现读:设置里刚改完,下一个任务就该用上。 */
interface UserAppGuides {
    fun list(): List<UserAppGuide>
    fun get(id: String): UserAppGuide?
    /** 整份写下(正文 + 附件对齐);校验由调用方先跑 [UserAppGuideParse.validate]。 */
    fun put(guide: UserAppGuide)
    fun remove(id: String)
    /** 给一个已装 App 新建说明:生成不冲突的 id 和骨架,写下后返回整份。 */
    fun create(packageName: String, display: String): UserAppGuide
}

/** 解析与校验:规则对齐构建期 [KnowledgeIndex] 对内置条目的校验子集,保存时先过这一道。 */
object UserAppGuideParse {
    /** 条目 id:小写字母、数字、连字符(同个人技能名的规矩,也是目录名)。 */
    val ID = Regex("[a-z0-9]+(?:-[a-z0-9]+)*")

    /** 附件路径:一律 references/ 下的 .md 文件。 */
    val FILE = Regex("references/[A-Za-z0-9_-]+(?:/[A-Za-z0-9_-]+)*\\.md")

    private fun one(meta: Map<String, Any>, k: String) = meta[k] as? String
    private fun many(meta: Map<String, Any>, k: String) =
        (meta[k] as? List<*>)?.filterIsInstance<String>() ?: listOfNotNull(one(meta, k)?.takeIf { it.isNotEmpty() })

    /** 头部解析结果;[body] 是去掉头部的正文。头部缺失时 meta 为空、body 是全文。 */
    fun front(guide: UserAppGuide): Pair<Map<String, Any>, String> = FrontMatter.parse(guide.markdown)

    /**
     * 变成知识条目;头部不全(name 对不上、没有包名、正文为空)返回 null:这份不生效,但不拖垮别人。
     * 用户说明只管安卓 App,不带链接与站点命令。
     */
    fun toEntry(guide: UserAppGuide): KnowledgeEntry? {
        val (meta, body) = front(guide)
        if (body.isBlank()) return null
        if (one(meta, "name") != guide.id) return null
        val packages = many(meta, "packages")
        if (packages.isEmpty()) return null
        val display = one(meta, "display")?.takeIf { it.isNotEmpty() } ?: return null
        val description = one(meta, "description")?.takeIf { it.isNotEmpty() } ?: return null
        return KnowledgeEntry(
            name = guide.id,
            display = display,
            surface = Surface.Android,
            packages = packages,
            domains = emptyList(),
            aliases = many(meta, "aliases"),
            open = one(meta, "open"),
            description = description,
            body = body,
        )
    }

    /**
     * 保存前校验:返回 null 表示能存,否则是错误码(界面翻成提示)。
     * [id] 是这份说明的目录名;正文里 references/xxx.md 的引用必须在 [UserAppGuide.files] 里存在。
     */
    fun validate(guide: UserAppGuide): String? {
        if (!ID.matches(guide.id)) return "bad_id"
        val (meta, body) = front(guide)
        val name = one(meta, "name") ?: return "name_missing"
        if (name != guide.id) return "name_mismatch"
        if (one(meta, "display").isNullOrEmpty()) return "display_missing"
        if (many(meta, "packages").isEmpty()) return "packages_empty"
        if (one(meta, "description").isNullOrEmpty()) return "description_missing"
        if (body.isBlank()) return "body_empty"
        for ((path, _) in guide.files) if (!FILE.matches(path)) return "file_bad_name"
        for (m in REF.findAll(body)) if (m.value !in guide.files) return "ref_missing"
        return null
    }

    private val REF = Regex("references/[A-Za-z0-9_./-]*[A-Za-z0-9_/-]")

    /** 由包名末段清洗出条目 id;冲突由调用方追加后缀解决。 */
    fun idFor(packageName: String): String {
        val tail = packageName.substringAfterLast('.').lowercase()
        val cleaned = tail.replace(Regex("[^a-z0-9]+"), "-").trim('-')
        return cleaned.ifEmpty { "app" }
    }
}

/** 新建说明时的骨架:头部齐全、正文是游戏向的填空结构,不改一个字也能通过校验并生效。 */
object UserAppGuideSkeleton {
    fun markdown(id: String, display: String, packageName: String): String = """
---
name: $id
display: $display
packages: ["$packageName"]
aliases: ["$display"]
sites: []
open: $display
description: (一句话:这是什么 App/游戏,什么时候用这份说明)
---
## 是什么
(它是什么,横屏还是竖屏,界面是系统控件还是整块画布。核心资源/货币在这里说清。)

## 全局规矩
(所有页面通用的:返回键在哪、弹窗怎么关、红点是什么意思;绝对不许点的按钮,比如充值和付费确认。)

## 主界面地图
(先写怎么认出主界面:屏幕上有哪些固定元素。再逐行写入口:编号、名字、位置(0-999 网格)、长什么样、点开去哪。)

## 功能索引
(一个功能一行:入口编号 + 详情附件。附件先在附件管理里新建,再在这里写 references/文件名.md 引用它。)

## 已知现象
(进 App 先弹什么、常见异常弹窗长什么样、加载慢时怎么办。)
""".trimIndent() + "\n"
}
