package dev.goutou.wingman.ui.miuix

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.ui.graphics.Brush
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
import dev.goutou.wingman.ui.GlassSurface
import dev.goutou.wingman.ui.LocalPalette
import dev.goutou.wingman.ui.RadiusR3
import dev.goutou.wingman.ui.RadiusR4
import kotlin.math.roundToInt

private val NavShape = RoundedCornerShape(RadiusR4)
private val IndicatorShape = RoundedCornerShape(RadiusR3)
private val NavHeight = 62.dp

/**
 * 底部「液态玻璃」导航条（参考 BoxProxy / HyperOS 那种悬浮圆角玻璃板）。
 *
 * 直接用项目已有的真玻璃（[GlassSurface]）而不是 Miuix 的 NavigationBar：
 * - 这块**压在背景图上**，要的是「透出下面颜色」的效果，`GlassSurface` 的折射/模糊就是干这个的；
 * - Miuix 的 NavigationBar 是不透明 surface，放上去会是一块实心板，跟参考图不是一回事。
 *
 * 选中态是一整块**会滑动的药丸**（弹簧跟随），不是每格各自变色 —— 切页时是连续位移。
 * 指示块用 `offset { }` 的 lambda 重载：只走布局阶段、不触发重组，滑动才满帧。
 */
@Composable
fun MiuixGlassNavBar(
    items: List<Pair<String, ImageVector>>,
    selected: Int,
    glassAlpha: Float,
    modifier: Modifier = Modifier,
    onSelect: (Int) -> Unit,
) {
    val palette = LocalPalette.current
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

    GlassSurface(shape = NavShape, glassAlpha = glassAlpha, big = true, modifier = modifier) {
        Box(Modifier.fillMaxWidth().height(NavHeight)) {
            if (cellPx > 0f) {
                Box(
                    Modifier
                        .align(Alignment.CenterStart)
                        .offset { IntOffset((slide.value * cellPx).roundToInt(), 0) }
                        .width(with(LocalDensity.current) { cellPx.toDp() })
                        .fillMaxHeight()
                        .padding(horizontal = 4.dp, vertical = 6.dp)
                        .clip(IndicatorShape)
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    palette.primary.copy(alpha = 0.34f),
                                    palette.primary.copy(alpha = 0.14f),
                                ),
                            ),
                        )
                        .border(1.dp, palette.primary.copy(alpha = 0.36f), IndicatorShape),
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
                            .clip(RoundedCornerShape(RadiusR3))
                            // 已经有指示块了，不要再叠一层涟漪（会闪出个不重合的方块）
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
                            tint = lerp(palette.sub, palette.primary, p),
                            modifier = Modifier.size(20.dp + 2.dp * p),
                        )
                        Text(
                            label,
                            fontSize = 10.sp,
                            color = lerp(palette.sub, palette.primary, p),
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}
