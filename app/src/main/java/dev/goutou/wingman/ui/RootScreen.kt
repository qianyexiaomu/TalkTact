package dev.goutou.wingman.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.goutou.wingman.config.ConfigStore
import dev.goutou.wingman.ui.miuix.MiuixPreviewScreen

/**
 * App 的根：**默认进新界面（Miuix）**，旧界面退成它里面的一个入口。
 *
 * 为什么不是「直接删掉旧界面」：还有没迁完的功能（试一试、诊断、白名单、备份），
 * 那些只存在于旧界面里。先把新界面做成门面，旧界面留一个明确的入口，等那些页面搬完
 * 再把 [App] 整个删掉 —— 这样任何一步都不会让功能消失。
 *
 * 旧界面里那张「回到新界面」的卡走的就是 [onExitLegacy]；这里再补一个系统返回键，
 * 免得进去之后只能靠点卡片出来。
 */
@Composable
fun RootScreen(store: ConfigStore) {
    var legacy by remember { mutableStateOf(false) }

    if (legacy) {
        BackHandler { legacy = false }
        App(store, onExitLegacy = { legacy = false })
    } else {
        // onBack 的位置就是「去旧界面」：底部导航只有 5 格，未迁移的功能需要一个去处
        MiuixPreviewScreen(store, onBack = { legacy = true })
    }
}
