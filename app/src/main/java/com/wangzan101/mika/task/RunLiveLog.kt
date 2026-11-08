package com.wangzan101.mika.task

import com.wangzan101.mika.core.agent.StepTranscript
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 最近一次任务的「思考与输出」：任务运行时由 [TaskRun] 把每步的思考与输出同步进来，
 * 侧边栏的「思考与输出」页读它。任务结束后保留，直到下一个任务开始才换掉。
 * 同时有几个任务在跑时，只跟最后开始的那个（页面顶上会写明是哪个任务）。
 */
object RunLiveLog {
    data class State(
        val title: String? = null,
        val steps: List<StepTranscript> = emptyList(),
        val running: Boolean = false,
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private var token = 0L

    /** 一个任务开始：清掉上一个的记录，返回这次的凭据（并发时旧任务的写入会被挡掉）。 */
    @Synchronized
    fun begin(title: String?): Long {
        token += 1
        _state.value = State(title = title?.takeIf { it.isNotBlank() }, running = true)
        return token
    }

    @Synchronized
    fun update(token: Long, steps: List<StepTranscript>) {
        if (token != this.token) return
        _state.value = _state.value.copy(steps = steps, running = true)
    }

    @Synchronized
    fun finish(token: Long) {
        if (token != this.token) return
        _state.value = _state.value.copy(running = false)
    }
}
