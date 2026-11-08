package com.wangzan101.mika.ui.components

import android.os.Build
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.dp
import com.wangzan101.mika.ui.theme.MikaColors
import com.wangzan101.mika.ui.theme.MikaShapes
import com.wangzan101.mika.ui.theme.MikaSize
import com.wangzan101.mika.ui.theme.MikaTheme
import com.wangzan101.mika.ui.theme.MikaType

/**
 * 页面底(Liquid Glass v2):近白(深色下近黑)的底上铺三团彩色光晕——左上蓝、右下绿、右侧橙。
 * 半透的卡片与玻璃悬浮件压在上面时能透出这些颜色,才有玻璃折射的感觉。
 * v2 起光晕会缓慢流动:三团光以不同的周期(17/23/29 秒)往返漂移、浓度随之呼吸,
 * 类似 iOS 的动态流光底;不随内容滚动。系统关了动画(reduceMotion)时停在中间相位,完全静止。
 */
@Composable
fun PageBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val c = MikaTheme.colors
    val reduce = MikaTheme.reduceMotion
    val flow = rememberInfiniteTransition(label = "page-halo-flow")
    val fa by flow.animateFloat(0f, 1f, infiniteRepeatable(tween(17_000, easing = LinearEasing), RepeatMode.Reverse), label = "halo-a")
    val fb by flow.animateFloat(0f, 1f, infiniteRepeatable(tween(23_000, easing = LinearEasing), RepeatMode.Reverse), label = "halo-b")
    val fc by flow.animateFloat(0f, 1f, infiniteRepeatable(tween(29_000, easing = LinearEasing), RepeatMode.Reverse), label = "halo-c")
    val f1 = if (reduce) 0.35f else fa
    val f2 = if (reduce) 0.6f else fb
    val f3 = if (reduce) 0.45f else fc
    Box(
        modifier.fillMaxSize().background(c.bg).drawBehind {
            val r = size.maxDimension * 0.85f
            val a = if (c.isDark) 1.55f else 1f
            val blue = Offset(size.width * (0.02f + 0.28f * f1), size.height * (0.02f + 0.13f * f1))
            drawCircle(
                Brush.radialGradient(listOf(c.accent.copy(alpha = (0.16f + 0.05f * f2) * a), Color.Transparent), center = blue, radius = r),
                radius = r, center = blue,
            )
            val green = Offset(size.width * (1.02f - 0.25f * f2), size.height * (0.98f - 0.18f * f2))
            drawCircle(
                Brush.radialGradient(listOf(c.series[2].copy(alpha = (0.11f + 0.04f * f3) * a), Color.Transparent), center = green, radius = r),
                radius = r, center = green,
            )
            val orange = Offset(size.width * (1.05f - 0.12f * f3), size.height * (0.30f + 0.25f * f3))
            drawCircle(
                Brush.radialGradient(listOf(c.series[1].copy(alpha = (0.08f + 0.03f * f1) * a), Color.Transparent), center = orange, radius = r * 0.8f),
                radius = r * 0.8f, center = orange,
            )
        },
        content = content,
    )
}

/**
 * 卡片的底(Liquid Glass):半透的面 + 纵向微光泽(上略亮、下略沉)+ 高光描边
 *(上边是近白的折射亮线,往下渐隐到原来的描边色)+ 极淡阴影;深色下只有描边。
 */
fun Modifier.cardSurface(colors: MikaColors, shape: Shape = MikaShapes.Card): Modifier =
    (if (colors.cardShadow.alpha > 0f) {
        shadow(6.dp, shape, clip = false, ambientColor = colors.cardShadow.copy(alpha = 0.45f), spotColor = colors.cardShadow.copy(alpha = 0.16f))
    } else this)
        .background(
            Brush.verticalGradient(
                listOf(
                    colors.surface.copy(alpha = (colors.surface.alpha + 0.07f).coerceAtMost(1f)),
                    colors.surface.copy(alpha = (colors.surface.alpha - 0.05f).coerceAtLeast(0f)),
                ),
            ),
            shape,
        )
        .border(
            0.5.dp,
            Brush.verticalGradient(
                listOf(
                    Color.White.copy(alpha = if (colors.isDark) 0.32f else 0.9f),
                    colors.edge,
                ),
            ),
            shape,
        )

/** 悬浮元素的阴影(输入栏、浮在内容上的按钮)。 */
fun Modifier.floatShadow(colors: MikaColors, shape: Shape): Modifier =
    shadow(14.dp, shape, clip = false, ambientColor = colors.floatShadow.copy(alpha = 0.3f), spotColor = colors.floatShadow.copy(alpha = 0.35f))

/** 一张卡片,内容裁在圆角里(行的按压效果不出角)。 */
@Composable
fun MikaCard(modifier: Modifier = Modifier, shape: Shape = MikaShapes.Card, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth().cardSurface(MikaTheme.colors, shape).clip(shape), content = content)
}

/**
 * 分组卡片:一组 [ListRow]。下面自带 12dp 间距,和下一组的 [GroupLabel](上 8)合起来是分组之间的 20。
 * 行与行的分隔线由行自己画,从文字处开始。
 */
@Composable
fun GroupCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    MikaCard(modifier.padding(bottom = 12.dp), content = content)
}

/** 状态卡:左边圆形图标,右边标题和说明。用于页面顶上的总状态。 */
@Composable
fun StateCard(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    tone: TileTone = TileTone.Neutral,
    modifier: Modifier = Modifier,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    val c = MikaTheme.colors
    MikaCard(modifier.padding(bottom = 12.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val (bg, fg) = tone.colors(c)
            Box(Modifier.size(36.dp).background(bg, CircleShape), contentAlignment = Alignment.Center) {
                Icon(icon, null, Modifier.size(20.dp), tint = fg)
            }
            Column(Modifier.weight(1f)) {
                Text(title, style = MikaType.BodyStrong, color = c.ink)
                if (!subtitle.isNullOrEmpty()) Text(subtitle, style = MikaType.Caption, color = c.ink3)
            }
            trailing()
        }
    }
}

// ---------- 悬浮玻璃 ----------

/** 能做背景模糊(安卓 12 起有 RenderEffect);不能时玻璃退回不透明的卡片底。 */
val GlassBlurSupported: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

/** 玻璃后面那层内容的画面:内容用 [glassSource] 画进来,[GlassBar] 取它模糊后垫在自己底下。 */
@Stable
class GlassBackdrop internal constructor(internal val layer: GraphicsLayer) {
    internal var origin by mutableStateOf(Offset.Zero)
}

/** 不支持模糊时为 null,[GlassBar] 就用不透明底。 */
@Composable
fun rememberGlassBackdrop(): GlassBackdrop? {
    if (!GlassBlurSupported) return null
    val layer = rememberGraphicsLayer()
    return remember(layer) { GlassBackdrop(layer) }
}

/** 把这块内容记成玻璃的背景。玻璃本身不能在这块内容里面。 */
fun Modifier.glassSource(backdrop: GlassBackdrop?): Modifier =
    if (backdrop == null) this
    else onGloballyPositioned { backdrop.origin = it.positionInRoot() }
        .drawWithContent {
            backdrop.layer.record { this@drawWithContent.drawContent() }
            drawLayer(backdrop.layer)
        }

private val GlassBlur = BlurEffect(64f, 64f, TileMode.Clamp)

/**
 * 玻璃输入栏的外壳:半透明底 + 背景模糊 + 悬浮阴影,高至少 56。
 * [backdrop] 为 null(不支持模糊、或后面没有内容)时用不透明底。
 */
@Composable
fun GlassBar(
    modifier: Modifier = Modifier,
    backdrop: GlassBackdrop? = null,
    shape: Shape = MikaShapes.Pill,
    content: @Composable RowScope.() -> Unit,
) {
    val c = MikaTheme.colors
    var origin by remember { mutableStateOf(Offset.Zero) }
    Box(
        modifier.floatShadow(c, shape).clip(shape)
            .then(if (backdrop != null) Modifier.onGloballyPositioned { origin = it.positionInRoot() } else Modifier),
    ) {
        if (backdrop != null) {
            Box(
                Modifier.matchParentSize()
                    .graphicsLayer { renderEffect = GlassBlur; clip = true }
                    .drawBehind {
                        val d = backdrop.origin - origin
                        translate(d.x, d.y) { drawLayer(backdrop.layer) }
                    },
            )
        }
        Row(
            Modifier.fillMaxWidth()
                .then(
                    if (backdrop != null) {
                        // Liquid Glass:上半更透亮、往下渐沉的玻璃面
                        Modifier.background(
                            Brush.verticalGradient(
                                listOf(
                                    c.glass.copy(alpha = (c.glass.alpha + 0.12f).coerceAtMost(1f)),
                                    c.glass.copy(alpha = (c.glass.alpha - 0.08f).coerceAtLeast(0f)),
                                ),
                            ),
                        )
                    } else Modifier.background(c.surface),
                )
                .border(
                    0.5.dp,
                    Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = if (c.isDark) 0.5f else 0.85f),
                            c.glassEdge,
                        ),
                    ),
                    shape,
                )
                .heightIn(min = MikaSize.Composer).padding(6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            content = content,
        )
    }
}
