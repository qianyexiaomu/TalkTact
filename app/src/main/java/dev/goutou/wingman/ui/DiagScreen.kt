package dev.goutou.wingman.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.content.Context
import android.os.Build
import dev.goutou.wingman.config.ConfigData
import dev.goutou.wingman.config.ConfigStore
import dev.goutou.wingman.config.DiagExport
import kotlinx.coroutines.launch
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

/**
 * 网络信息（归属地）—— 从「高级设置」沉到「诊断」页顶部。
 *
 * 它是只读的排障信息（自检里那两行省份是从哪儿来的），本质属于网络排障，和接口地址那种
 * 「要你去配的」不是一类东西；放在九张配置卡中间，它是唯一一张「看一眼就行」的卡。
 *
 * 开关与自定义地址都**立刻落盘**（跟白名单 / 识图 / 生成模式同一个道理），没有「保存」按钮 ——
 * 所以这里自己读一份 store，不依赖外面那页的草稿（诊断页本来也没有草稿）。
 */
@Composable
internal fun GeoCard(store: ConfigStore, glassAlpha: Float) {
    val palette = LocalPalette.current
    var d by remember { mutableStateOf(store.load()) }
    var persisted by remember { mutableStateOf(d) }

    fun saveGeoNow(enabled: Boolean, endpoint: String) {
        store.saveGeo(enabled, endpoint)
        d = store.load()
        persisted = d
    }

    GlassCard(glassAlpha) {
        Text("网络信息（归属地）", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = palette.text)
        Text(
            "「运行状态 → 接口自检」里那两行省份，是拿 IP 去问第三方库要的 —— 这是整个模块唯一一处" +
                "会把 IP 发给别人的地方。关掉之后自检只显示 IP，一次都不问。",
            fontSize = 13.sp,
            color = palette.sub,
        )
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("查询 IP 归属地", fontSize = 15.sp, color = palette.text)
                Text(
                    if (d.geoEnabled) "自检时带上省份（结果缓存 10 分钟）" else "已关闭：不请求任何第三方",
                    fontSize = 13.sp,
                    color = palette.sub,
                )
            }
            Switch(checked = d.geoEnabled, onCheckedChange = { saveGeoNow(it, d.geoEndpoint) })
        }
        Spacer(Modifier.height(8.dp))
        GlassField(
            value = d.geoEndpoint,
            onValueChange = { d = d.copy(geoEndpoint = it) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("自定义接口地址（留空 = 用内置的）") },
            singleLine = true,
            enabled = d.geoEnabled,
        )
        if (d.geoEnabled && d.geoEndpoint.trim() != persisted.geoEndpoint.trim()) {
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("地址还没保存", modifier = Modifier.weight(1f), fontSize = 13.sp, color = palette.bad)
                Button(
                    onClick = { saveGeoNow(d.geoEnabled, d.geoEndpoint) },
                    modifier = Modifier.height(42.dp),
                    shape = RoundedCornerShape(RadiusR2),
                    colors = ButtonDefaults.buttonColors(containerColor = palette.primary),
                ) { Text("保存地址") }
            }
        }
        Spacer(Modifier.height(4.dp))
        HintText(
            "自己填的地址要能返回 JSON（内置用的是 ip.useragentinfo.com）；认不出的字段一律当没有，不会报错。",
            fontSize = 10.sp,
            color = palette.sub,
        )
    }
}

/**
 * 设置 → 高级设置 → 诊断（第三层）。
 *
 * 这三块原来是挤在「运行状态」页上的（抓取界面 / 微信进程的诊断 / 最后一次调用），
 * 把那一页撑得很长，而且它们都是「出问题了才看」的东西，所以搬进设置这条线里。
 * 「返回」回高级设置 —— 它就是从那儿进来的。
 */
@Composable
fun DiagScreen(store: ConfigStore, glassAlpha: Float, onBack: () -> Unit) {
    val palette = LocalPalette.current
    val clipboard = LocalClipboardManager.current
    var tick by remember { mutableStateOf(0) }
    var diagAsked by remember { mutableStateOf(false) }
    val diag = remember(tick) { store.diag() }
    val diagAt = remember(tick) { store.diagAt() }
    val lastCall = remember(tick) { store.lastCall() }
    val lastCallAt = remember(tick) { store.lastCallAt() }
    val trace = remember(tick) { store.trace() }
    val traceAt = remember(tick) { store.traceAt() }
    val context = LocalContext.current
    var exportNote by remember { mutableStateOf("") }
    // 页底的「导出诊断包」：排障时「抓完就能打包发给对方」，不用退回「高级设置 → 备份」去找。
    val diagLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri ->
        if (uri != null) {
            exportNote = try {
                context.contentResolver
                    .openOutputStream(uri)
                    ?.use { it.write(buildDiagZip(context, store, store.load())) }
                "已导出 —— 发之前建议先打开看一眼（含聊天内容，不含 API Key）"
            } catch (t: Throwable) {
                "导出失败：${t.message}"
            }
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 120.dp)) {
        ScreenHeader("诊断", "抓界面 · 看轨迹与调用 · 复制发我") {
            HeaderButton("↻ 刷新") { tick++ }
            Spacer(Modifier.width(8.dp))
            HeaderButton("← 返回", onBack)
        }

        // 「最近一次调用」提到**页首**：出问题时第一眼看的就是「实际发出去的到底是什么」，
        // 而它原本排在整页最后，滚半天才到 —— 偏偏这是最常被复制的那一段。
        if (lastCall.isNotBlank()) {
            GlassCard(glassAlpha, border = palette.primary.copy(alpha = CardBorderAlpha)) {
                Text("最近一次调用（注入侧真正发出去的）", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = palette.text)
                Text(
                    "发生于 ${formatTime(lastCallAt)} · 这里是微信进程实际拿去调接口的那一份，不是本 App 里的配置。" +
                        "核对「当前军师」有没有真的生效，看 system 长度那一行。",
                    fontSize = 13.sp,
                    color = palette.sub,
                )
                Spacer(Modifier.height(8.dp))
                Text(lastCall, fontSize = 10.sp, color = palette.text)
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { clipboard.setText(AnnotatedString(lastCall)) },
                    shape = RoundedCornerShape(RadiusR2),
                ) { Text("复制这一段") }
            }
        } else {
            GlassCard(glassAlpha) {
                Text("还没有调用记录", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = palette.text)
                Text("在微信里生成过一次候选回复之后，那份请求就会留在这里。", fontSize = 13.sp, color = palette.sub)
            }
        }

        // 归属地从「高级设置」沉到这里：只读的排障信息，跟「要配的」分开。
        GeoCard(store, glassAlpha)

        GlassCard(glassAlpha) {
            Text("抓取微信界面", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = palette.text)
            Text(
                "某些聊天页连卡片都不弹时用这个：先在微信里停在那个聊天页 → 切回这里点下面的按钮 → " +
                    "再切回微信（那个页面重新出现就会自动抓）→ 回来点「刷新」→ 复制下面的「诊断」发我。",
                fontSize = 13.sp,
                color = palette.sub,
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = { store.requestDiag(); diagAsked = true },
                    shape = RoundedCornerShape(RadiusR2),
                    colors = ButtonDefaults.buttonColors(containerColor = palette.primary),
                ) { Text("抓当前微信界面") }
                if (diagAsked) {
                    Text("已排队，切回微信那一页即抓", fontSize = 13.sp, color = palette.ok)
                }
            }
        }

        if (trace.isNotBlank()) {
            GlassCard(glassAlpha, border = palette.okMark.copy(alpha = CardBorderAlpha)) {
                Text("决策轨迹（每一轮读到什么、停在哪一步）", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = palette.text)
                Text(
                    "更新于 ${formatTime(traceAt)} · 卡片不弹时先看它：注入侧每一轮都留一条，" +
                        "扫一眼就知道是卡在「找不到输入框」还是「最后一条是我发的」。它刻意不含聊天正文。",
                    fontSize = 13.sp,
                    color = palette.sub,
                )
                Spacer(Modifier.height(8.dp))
                Text(trace, fontSize = 10.sp, color = palette.text)
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { clipboard.setText(AnnotatedString(trace)) },
                    shape = RoundedCornerShape(RadiusR2),
                ) { Text("复制轨迹") }
            }
        } else {
            GlassCard(glassAlpha) {
                Text("还没有轨迹", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = palette.text)
                Text(
                    "在微信里打开一个聊天页停一会儿，注入侧就会把判定记下来并自动回传；" +
                        "也可以点上面那个「抓当前微信界面」立刻带一份回来。",
                    fontSize = 13.sp,
                    color = palette.sub,
                )
            }
        }

        if (diag.isNotBlank()) {
            GlassCard(glassAlpha, border = palette.warnMark.copy(alpha = CardBorderAlpha)) {
                Text("诊断（来自微信进程）", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = palette.text)
                Text(
                    "生成于 ${formatTime(diagAt)} · 排查「读不到消息 / 全是图片」时把它复制给对方",
                    fontSize = 13.sp,
                    color = palette.sub,
                )
                Spacer(Modifier.height(8.dp))
                Text(diag, fontSize = 10.sp, color = palette.text)
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { clipboard.setText(AnnotatedString(diag)) },
                    shape = RoundedCornerShape(RadiusR2),
                ) { Text("复制诊断") }
            }
        } else {
            GlassCard(glassAlpha) {
                Text("还没有诊断", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = palette.text)
                Text("上面那个按钮抓过一次之后，微信进程会把界面结构留在这里。", fontSize = 13.sp, color = palette.sub)
            }
        }

        // 页底固定一个次级导出入口（排障动线：抓完 → 打包 → 发出去）
        Spacer(Modifier.height(6.dp))
        OutlinedButton(
            onClick = { diagLauncher.launch("TalkTact-诊断包.zip") },
            modifier = Modifier.fillMaxWidth().height(46.dp),
            shape = RoundedCornerShape(RadiusR2),
        ) { Text("导出诊断包（.zip）") }
        if (exportNote.isNotBlank()) {
            Text(exportNote, fontSize = 13.sp, color = palette.sub, modifier = Modifier.padding(top = 6.dp))
        }
    }
}

// ================= 角色 =================

/**
 * 组装诊断包。
 *
 * 刻意**不含 API Key** —— 诊断包是要发给别人的，Key 不该跟着走。
 * 里面的 04 / 05 会带聊天内容，所以说明文件（00）里明确提醒了「发之前先看一眼」。
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
