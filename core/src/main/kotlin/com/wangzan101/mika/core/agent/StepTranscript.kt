package com.wangzan101.mika.core.agent

/**
 * 一步的「思考与输出」记录，供侧边栏页面展示：[thinking] 是模型这步的思考原文（服务商回传了
 * 可读思考才有，否则为空），[output] 是这步的可见文字（带调用时是随调用写的话，不带调用时
 * 是回答），[tool] 是这步调用的工具名，没有调用时为 null。
 * [live] 为 true 表示这一步还在生成中，文字会继续变长。
 */
data class StepTranscript(
    val step: Int,
    val thinking: String = "",
    val output: String = "",
    val tool: String? = null,
    val live: Boolean = true,
)
