package dev.goutou.wingman.ui

import androidx.compose.runtime.Composable
import dev.goutou.wingman.config.ConfigStore
import dev.goutou.wingman.ui.miuix.MiuixPreviewScreen

/**
 * App 的根。
 *
 * 现在只有一条路：**新界面（Miuix）**。旧界面（`App()` + 整套玻璃 UI）已经删掉了 ——
 * 之前它是以「首页右上角 → 旧界面」的形式留的过渡入口，功能全部搬完之后就不需要了。
 *
 * 保留这一层而不是让 `MainActivity` 直接调 `MiuixPreviewScreen`：
 * 以后要加启动路由（首次引导、崩溃恢复之类）有地方放，不用动 Activity。
 */
@Composable
fun RootScreen(store: ConfigStore) {
    MiuixPreviewScreen(store) {
        // onBack：旧界面入口已经没了，留空实现（二级页自己的返回走 page 状态）
    }
}
