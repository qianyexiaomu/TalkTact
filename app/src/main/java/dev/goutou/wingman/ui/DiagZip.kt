package dev.goutou.wingman.ui

import android.content.Context
import android.os.Build
import dev.goutou.wingman.config.ConfigData
import dev.goutou.wingman.config.ConfigStore
import dev.goutou.wingman.config.DiagExport

/**
 * 组装诊断包。
 *
 * 刻意**不含 API Key** —— 诊断包是要发给别人的，Key 不该跟着走。
 * 里面的 04 / 05 会带聊天内容，所以说明文件（00）里明确提醒了「发之前先看一眼」。
 *
 * 这段原来在 `DiagScreen.kt` 里（和旧的诊断页面混在一起）。旧页面删了，
 * 但导出逻辑要留下 —— 新的诊断页（`ui/miuix`）还用它。
 */
internal fun buildDiagZip(context: Context, store: ConfigStore, cfg: ConfigData): ByteArray {
    val roles = store.roles()
    val (calls, tokens) = store.usage()
    val lowRam = isLowRamDevice(context)
    val stamp = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
        .format(java.util.Date())
    return DiagExport.zip(
        linkedMapOf(
            "00-说明.txt" to DiagExport.readme(appVersion(context)),
            "01-环境.txt" to buildString {
                append("App 版本：${appVersion(context)}\n")
                append("Android：SDK ${Build.VERSION.SDK_INT}（${Build.VERSION.RELEASE}）\n")
                append("机型：${Build.MANUFACTURER} ${Build.MODEL}\n")
                append("低内存设备：$lowRam\n")
                append("玻璃效果：设置=${cfg.glassQuality} → 实际=${decideGlassQuality(cfg.glassQuality, lowRam, Build.VERSION.SDK_INT)}\n")
                append("导出时间：$stamp\n")
            },
            "02-配置.txt" to buildString {
                append("接口地址：${cfg.baseUrl}\n")
                append("模型：${cfg.model}\n")
                append("生成模式：")
                append(
                    if (cfg.graded) {
                        "模型分级（第二套：${cfg.baseUrl2.ifBlank { "复用第一套" }} / ${cfg.model2.ifBlank { "复用" }}）"
                    } else {
                        "直通"
                    },
                )
                append("\n参考条数：${cfg.ctx}　最短间隔：${cfg.minIntervalSec}s　temperature：${cfg.temperature}　max_tokens：${cfg.maxTokens}\n")
                append("严格 JSON 输出：${cfg.jsonMode}　敏感内容检查：${!cfg.allowSensitive}\n")
                append("当前 skill：${cfg.skillId}　提示词 ${cfg.prompt.length} 字　含 JSON 契约：${cfg.prompt.contains("replies")}\n")
                // 识图排不进问题时最需要这两行：开关到底开没开、和「本地代理」有没有配套
                val (ocrLine, ocrAt) = store.ocrInfo()
                val ocrWhen = if (ocrAt > 0L) {
                    " · " + java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.getDefault())
                        .format(java.util.Date(ocrAt))
                } else {
                    ""
                }
                append("图片文字识别：${if (cfg.ocrEnabled) "开" else "关"}　本地代理：${if (cfg.proxyEnabled) "开" else "关"}\n")
                append("最近一次识图：${ocrLine.ifBlank { "（还没有记录）" }}$ocrWhen\n")
                append("API Key：已省略（不导出）\n")
            },
            "03-角色.txt" to buildString {
                append("角色数：${roles.size}\n")
                roles.forEach {
                    append("· ${it.name}（key=${it.key}）关系=${it.relation.ifBlank { "-" }} 记录=${it.msgs.size} 条\n")
                }
            },
            "04-诊断.txt" to store.diag().ifBlank { "（还没有诊断数据：到「诊断」页点一次「抓取微信界面」）" },
            "05-最近一次调用.txt" to store.lastCall().ifBlank { "（还没有调用记录）" },
            "06-用量.txt" to "调用次数：$calls\n累计 token：$tokens\n",
            "07-决策轨迹.txt" to store.trace().ifBlank {
                "（还没有轨迹：在微信里打开一个聊天页停一会儿，轨迹会自动回传）"
            },
        ),
    )
}
