package com.wangzan101.mika.ui

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.wangzan101.mika.R
import com.wangzan101.mika.core.agent.StepTranscript
import com.wangzan101.mika.task.RunLiveLog
import com.wangzan101.mika.ui.components.BarIconButton
import com.wangzan101.mika.ui.components.PageBackground
import com.wangzan101.mika.ui.components.TopBar
import com.wangzan101.mika.ui.theme.MikaIcons
import com.wangzan101.mika.ui.theme.MikaShapes
import com.wangzan101.mika.ui.theme.MikaTheme
import com.wangzan101.mika.ui.theme.MikaType
import kotlinx.coroutines.delay

/**
 * 「思考与输出」页：最近一次任务每一步的模型思考与可见输出，按步骤分段。
 * 任务运行时实时变长（跟随最新时自动滚到底），结束后保留到下一个任务开始。
 * 模型不回传可读思考时只有输出段；思考是加密的（如某些 Responses 接口）同样只有输出。
 */
@Composable
fun ThinkingScreen(onBack: () -> Unit) {
    val c = MikaTheme.colors
    val context = LocalContext.current
    val log by RunLiveLog.state.collectAsState()
    val steps = log.steps
    val list = rememberLazyListState()
    var follow by remember { mutableStateOf(true) }
    var copied by remember { mutableStateOf(false) }
    // 内容签名：步数或任一步的文字长度变了都算新内容，跟随时滚到最后一步的末尾
    val signature = steps.size * 1_000_003 + steps.sumOf { it.thinking.length + it.output.length }
    LaunchedEffect(signature, follow) {
        if (follow && steps.isNotEmpty()) list.animateScrollToItem(steps.lastIndex, Int.MAX_VALUE / 2)
    }
    LaunchedEffect(copied) {
        if (copied) { delay(1_500); copied = false }
    }
    // 复制全文在组合阶段先拼好（点击回调里不能再取字符串资源）；
    // joinToString 的 lambda 不是 @Composable 上下文，标签改用 context.getString 取
    val copyText = steps.joinToString("\n\n") { s ->
        buildString {
            append(context.getString(R.string.live_step, s.step))
            s.tool?.let { append(" · ").append(it) }
            if (s.thinking.isNotBlank()) {
                append("\n").append(context.getString(R.string.live_thinking)).append(":\n").append(s.thinking.trim())
            }
            if (s.output.isNotBlank()) {
                append("\n").append(context.getString(R.string.live_output)).append(":\n").append(s.output.trim())
            }
        }
    }
    fun copyAll() {
        context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("mika", copyText))
        copied = true
    }
    PageBackground {
        Column(Modifier.fillMaxSize()) {
            TopBar(
                title = stringResource(R.string.live_title),
                left = MikaIcons.Back,
                onLeft = onBack,
                leftLabel = stringResource(R.string.back),
                subtitle = when {
                    steps.isEmpty() -> null
                    log.running -> stringResource(R.string.live_running)
                    else -> stringResource(R.string.live_last)
                },
                right = if (steps.isNotEmpty()) {
                    {
                        BarIconButton(
                            if (copied) MikaIcons.Check else MikaIcons.Copy,
                            stringResource(if (copied) R.string.live_copied else R.string.live_copy_all),
                            onClick = ::copyAll,
                        )
                    }
                } else null,
            )
            if (!log.title.isNullOrBlank()) {
                Text(
                    log.title!!, Modifier.padding(start = 20.dp, end = 20.dp, top = 10.dp),
                    style = MikaType.BodyStrong, color = c.ink, maxLines = 2,
                )
            }
            if (steps.isEmpty()) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(
                        stringResource(R.string.live_empty),
                        Modifier.padding(horizontal = 40.dp),
                        style = MikaType.Body, color = c.ink3,
                    )
                }
            } else {
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    LazyColumn(
                        state = list,
                        modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.navigationBars),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp),
                    ) {
                        items(steps, key = { it.step }) { s -> StepSection(s) }
                    }
                    if (!follow) {
                        Row(
                            Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 18.dp)
                                .clip(MikaShapes.Pill).background(c.glass)
                                .clickable { follow = true }
                                .padding(horizontal = 14.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(MikaIcons.Download, null, Modifier.size(14.dp), tint = c.ink2)
                            Text(stringResource(R.string.live_follow), style = MikaType.Caption, color = c.ink)
                        }
                    }
                }
            }
            if (steps.isNotEmpty()) {
                Row(
                    Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars)
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(Modifier.size(7.dp).background(if (follow) c.ok else c.ink3, CircleShape))
                    Text(
                        stringResource(if (follow) R.string.live_follow_on else R.string.live_follow_off),
                        style = MikaType.Caption, color = c.ink3,
                        modifier = Modifier.clickable { follow = !follow },
                    )
                }
            }
        }
    }
}

/** 一步：标题行（第几步 · 工具 · 生成中），下面思考段（次要色）和输出段。 */
@Composable
private fun StepSection(s: StepTranscript) {
    val c = MikaTheme.colors
    Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.live_step, s.step), style = MikaType.BodyStrong, color = c.ink)
            s.tool?.takeIf { it.isNotBlank() }?.let { tool ->
                Text(
                    tool, Modifier.clip(MikaShapes.Pill).background(c.glass).padding(horizontal = 9.dp, vertical = 3.dp),
                    style = MikaType.Caption, color = c.ink2, maxLines = 1,
                )
            }
            if (s.live) {
                Text(stringResource(R.string.live_generating), style = MikaType.Caption, color = c.ok)
            }
        }
        SelectionContainer {
            Column {
                if (s.thinking.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.live_thinking), style = MikaType.Label, color = c.ink3)
                    Spacer(Modifier.height(3.dp))
                    Text(s.thinking.trim(), style = MikaType.Body, color = c.ink2)
                }
                if (s.output.isNotBlank()) {
                    Spacer(Modifier.height(10.dp))
                    Text(stringResource(R.string.live_output), style = MikaType.Label, color = c.ink3)
                    Spacer(Modifier.height(3.dp))
                    Text(s.output.trim(), style = MikaType.Body, color = c.ink)
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Box(Modifier.fillMaxWidth().height(0.5.dp).background(c.hair))
    }
}
