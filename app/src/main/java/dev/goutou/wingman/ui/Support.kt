package dev.goutou.wingman.ui

import android.app.ActivityManager
import android.content.Context
import dev.goutou.wingman.ModuleStatus
import dev.goutou.wingman.config.ConfigStore
import dev.goutou.wingman.config.RemoteSync
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 新界面（Miuix）用到的一小撮通用东西。
 *
 * 这些原本散在旧界面的 `Ui.kt` / `ScreenCommon.kt` 里。旧界面删掉时**只把这几个搬过来**，
 * 其余（玻璃、主题、导航、八张旧页面）都随旧壳一起删了。
 *
 * 放在同一个包 `dev.goutou.wingman.ui` 下：调用处（`ui/miuix/*`）的 import 不用改。
 */

/** 普通圆角（列表行 / 小按钮）。旧界面里那套 R1–R4 只剩这一个还在用。 */
val RadiusR2 = 16.dp

/** skillId → 给人看的名字。运行状态、军师页共用，别再各写一份 if-else。 */
fun skillName(id: String): String = when (id) {
    "full" -> "狗头军师·满血版"
    "coder" -> "程序员搭子"
    "custom" -> "自定义 skill"
    else -> "原版狗头军师"
}

/**
 * 「模块到底生效了没」的判定，返回能说清缘由的说明；没生效返回 null。
 *
 * 三个信号（缺一不可，原因见旧 Ui.kt 的长注释）：
 * 1. 框架把模块注入了本 App；
 * 2. service 通道已建立；
 * 3. 微信进程报过心跳 —— 最硬的证据。
 */
fun moduleActiveReason(store: ConfigStore): String? = when {
    ModuleStatus.isActive() -> "框架已把模块注入本应用"
    RemoteSync.bound -> "框架已连上本模块（service 通道已建立）"
    store.heartbeatAt() > 0 -> "微信进程里跑过本模块"
    else -> null
}

fun moduleActive(store: ConfigStore): Boolean = moduleActiveReason(store) != null

fun appVersion(context: Context): String = try {
    val info = context.packageManager.getPackageInfo(context.packageName, 0)
    "v${info.versionName}(${info.versionCode})"
} catch (t: Throwable) {
    "v?"
}

fun formatTime(ts: Long): String =
    if (ts <= 0) "从未" else SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(ts))

/** 「多久以前」：心跳 / 最近路线这类「越新越正常」的字段用它。 */
internal fun agoText(at: Long, now: Long = System.currentTimeMillis()): String {
    if (at <= 0L) return "从来没有过"
    val sec = ((now - at) / 1000L).coerceAtLeast(0L)
    return when {
        sec < 5 -> "刚刚"
        sec < 60 -> "${sec} 秒前"
        sec < 3600 -> "${sec / 60} 分钟前"
        sec < 86_400 -> "${sec / 3600} 小时前"
        else -> "${sec / 86_400} 天前"
    }
}

/** 低内存设备。拿不到就按「不是」处理 —— 宁可多开效果，也别因一个查询失败把人降级。 */
internal fun isLowRamDevice(context: Context): Boolean = runCatching {
    (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).isLowRamDevice
}.getOrDefault(false)
