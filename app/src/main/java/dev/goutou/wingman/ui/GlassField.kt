package dev.goutou.wingman.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.VisualTransformation

/**
 * 全站统一的输入框。
 *
 * 在这之前，22 处输入框每一处都手抄同样两行样板：
 * `colors = glassFieldColors()` 与 `shape = RoundedCornerShape(RadiusR2)`。
 * 抄漏一行就会在深色下变成一块不透明的 Material 底板，和周围的玻璃卡明显不是一层
 * —— 而且这种「漏一行」在截图里一眼能看出来、在代码里却很难 grep（因为它**没写**）。
 *
 * 参数名与顺序**刻意和 `OutlinedTextField` 保持一致**：这样每一处调用只需要改个名字，
 * 不必重排参数、也不会把夹在参数之间的注释挤到别处去。
 *
 * 剩下的差异（label 的字号、密码框的 `visualTransformation`、`keyboardOptions`）
 * 由调用方显式传，不做统一 —— 统一字号会悄悄改变已有界面的观感。
 */
@Composable
fun GlassField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth(),
    label: @Composable (() -> Unit)? = null,
    singleLine: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    enabled: Boolean = true,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        label = label,
        singleLine = singleLine,
        keyboardOptions = keyboardOptions,
        visualTransformation = visualTransformation,
        enabled = enabled,
        colors = glassFieldColors(),
        shape = RoundedCornerShape(RadiusR2),
    )
}
