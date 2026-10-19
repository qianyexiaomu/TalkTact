package dev.goutou.wingman.ui.miuix

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.roundToInt

private val NavShape = RoundedCornerShape(30.dp)
private val IndicatorShape = RoundedCornerShape(24.dp)
private val NavHeight = 60.dp

/**
 * 底部导航条（悬浮、圆角、半透明，选中项一颗会滑动的药丸）。
 *
 * **不再用 `GlassSurface`**：那是给「压在渐变背景上、要真折射」的场景做的，而新版页面
 * 自带一层近白底（见 `MiuixPreviewScreen` 的注释），底下没有东西可模糊 —— 用它只会
 * 换来一块**紫色调**的板（它读的是项目旧的 `LocalPalette`），跟 Miuix 的蓝完全不搭。
 *
 * 现在用的是 Miuix 自己的 `surface` 半透明 + 细描边 + 投影：在白底上读作「浮起来的磨砂玻璃」，
 * 深浅色都跟着主题走。
 */
@Composable
fun MiuixGlassNavBar(
    items: List<Pair<String, ImageVector>>,
    selected: Int,
    modifier: Modifier = Modifier,
    onSelect: (Int) -> Unit,
) {
    val c = MiuixTheme.colorScheme
    val haptics = LocalHapticFeedback.current
    var rowWidth by remember { mutableIntStateOf(0) }
    val cellPx = if (rowWidth > 0) rowWidth.toFloat() / items.size else 0f
    val slide = remember { Animatable(0f) }
    LaunchedEffect(selected, cellPx) {
        if (cellPx <= 0f) return@LaunchedEffect
        slide.animateTo(
            selected.toFloat(),
            spring(dampingRatio = 0.76f, stiffness = Spring.StiffnessMediumLow),
        )
    }

    Box(
        modifier
            .shadow(
                elevation = 16.dp,
                shape = NavShape,
                clip = false,
                ambientColor = Color.Black.copy(alpha = 0.10f),
                spotColor = Color.Black.copy(alpha = 0.14f),
            )
            .clip(NavShape)
            .background(c.surface.copy(alpha = 0.94f))
            .border(0.5.dp, c.dividerLine, NavShape),
    ) {
        Box(Modifier.fillMaxWidth().height(NavHeight)) {
            if (cellPx > 0f) {
                Box(
                    Modifier
                        .align(Alignment.CenterStart)
                        .offset { IntOffset((slide.value * cellPx).roundToInt(), 0) }
                        .width(with(LocalDensity.current) { cellPx.toDp() })
                        .fillMaxHeight()
                        .padding(horizontal = 5.dp, vertical = 6.dp)
                        .clip(IndicatorShape)
                        .background(c.primary.copy(alpha = 0.14f))
                        .border(0.5.dp, c.primary.copy(alpha = 0.30f), IndicatorShape),
                )
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(NavHeight)
                    .onGloballyPositioned { rowWidth = it.size.width },
            ) {
                items.forEachIndexed { index, (label, icon) ->
                    val on = index == selected
                    val p by animateFloatAsState(if (on) 1f else 0f, tween(260))
                    val interaction = remember { MutableInteractionSource() }
                    Column(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(IndicatorShape)
                            // 已经有指示块，不要再叠一层涟漪（会闪出个和它不重合的方块）
                            .clickable(
                                interactionSource = interaction,
                                indication = null,
                                onClick = {
                                    if (index != selected) {
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    }
                                    onSelect(index)
                                },
                            )
                            .graphicsLayer {
                                val scale = 0.94f + 0.06f * p
                                scaleX = scale
                                scaleY = scale
                                translationY = -1.5f * p
                            },
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(
                            icon,
                            contentDescription = label,
                            tint = lerp(c.onSurfaceVariantSummary, c.primary, p),
                            modifier = Modifier.size(20.dp + 2.dp * p),
                        )
                        Text(
                            label,
                            fontSize = 10.sp,
                            color = lerp(c.onSurfaceVariantSummary, c.primary, p),
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}
