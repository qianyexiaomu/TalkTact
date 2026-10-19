package dev.goutou.wingman.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.goutou.wingman.config.ConfigStore
import dev.goutou.wingman.llm.Geo
import dev.goutou.wingman.llm.LlmClient
import dev.goutou.wingman.llm.LlmException
import dev.goutou.wingman.llm.NetInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

@Composable
fun StatusScreen(store: ConfigStore, onTrial: () -> Unit) {
    val palette = LocalPalette.current
    val context = LocalContext.current
    var tick by remember { mutableStateOf(0) }
    var cfg by remember { mutableStateOf(store.load()) }
    val usage = remember(tick) { store.usage() }
    val beat = remember(tick) { store.heartbeatAt() }
    val probeReqAt = remember(tick) { store.probeAt() }
    val probeAckAt = remember(tick) { store.probeAck() }
    val probeWhere = remember(tick) { store.probeInfo() }
    var probeVerdict by remember { mutableStateOf<String?>(null) }
    var probeDetail by remember { mutableStateOf<String?>(null) }
    var probeOk by remember { mutableStateOf(false) }
    var probing by remember { mutableStateOf(false) }
    var filter by remember { mutableStateOf(0) }
    /** 「详细状态」展开了没有。默认收起：这一页只需要一眼看健康。 */
    var detail by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val glass = cfg.glassAlpha
    val active = moduleActive(store)
    val fresh = beat > 0 && System.currentTimeMillis() - beat < 6 * 3600_000L
    val confirmed = store.scopeConfirmed()

    // 实时探测的结论（问题 5）：模块「现在还在不在」只认这条，心跳时间戳退居「历史」。
    val nowMs = System.currentTimeMillis()
    // 回执里带着请求号（「#<那次请求的时间戳>」）：只有**号和当前这一轮对得上**才算「这一轮被回答了」。
    // 否则新建的面板会去答一个很久以前的旧请求，界面会误报「刚刚回过话」。
    val ackId = probeWhere.removePrefix("#").substringBefore(' ').toLongOrNull() ?: 0L
    val answered = probeReqAt > 0 && ackId == probeReqAt
    val whereText = probeWhere.substringAfter(" · ", "").ifBlank { probeWhere }

    val liveText: String
    val liveLevel: Level
    when {
        answered -> {
            val secs = (nowMs - probeAckAt) / 1000
            liveText = "刚刚回过话：${secs} 秒前" + if (whereText.isBlank()) "" else " · $whereText"
            liveLevel = if (nowMs - probeAckAt < 120_000L) Level.OK else Level.WARN
        }
        probeReqAt > 0 && nowMs - probeReqAt < 10_000L -> {
            liveText = "已发出探测，正在等微信进程回话…（要微信在运行）"
            liveLevel = Level.WARN
        }
        probeReqAt > 0 -> {
            liveText = "探测没有回应（${formatTime(probeReqAt)}）—— 确认微信在运行、作用域勾了微信，再强杀微信重开；也可以点这一行重试"
            liveLevel = Level.BAD
        }
        else -> {
            liveText = "还没探测过 —— 点这一行探测一下（会在微信进程里跑一轮再回话）"
            liveLevel = Level.WARN
        }
    }

    // 进这一页自动探一次；等回话的那十几秒里每秒刷一下。
    // 有界轮询（repeat 12 次）—— 无限协程会把截图回归测试挂住（这个坑踩过）。
    LaunchedEffect(Unit) {
        store.requestProbe()
        tick++
    }
    LaunchedEffect(answered) {
        if (!answered) repeat(12) {
            kotlinx.coroutines.delay(1000)
            tick++
        }
    }

    val checks = listOf(
        Check(
            "模块激活",
            moduleActiveReason(store) ?: "未激活：在框架里启用本模块并勾选微信，然后强杀微信重开",
            if (active) Level.OK else Level.BAD,
        ),
        Check(
            "模块是否在跑（实时探测）",
            liveText,
            liveLevel,
        ) {
            // 点这一行 = 重新探一次
            store.requestProbe()
            tick++
        },
        Check(
            "微信内已生效（历史）",
            when {
                fresh -> "微信进程最近报过心跳：${formatTime(beat)}"
                confirmed -> "你手动确认过（还没收到心跳）"
                else -> "没收到微信进程心跳：确认作用域勾了微信并强杀重开"
            },
            if (fresh) Level.OK else Level.WARN,
        ) {
            store.setScopeConfirmed(!confirmed)
            cfg = store.load()
        },
        Check(
            "API Key",
            if (cfg.apiKey.isBlank()) "未填写，到「设置」填" else "已填写（明文存本机，见设置页说明）",
            if (cfg.apiKey.isBlank()) Level.BAD else Level.OK,
        ),
        Check(
            "接口与模型",
            if (cfg.graded) {
                "${cfg.model}（写回复）+ ${cfg.riskEndpoint().model}（风险） · 分级模式"
            } else {
                "${cfg.model} · ${cfg.baseUrl}"
            },
            if (cfg.baseUrl.startsWith("http")) Level.OK else Level.BAD,
        ),
        Check(
            "当前军师",
            "${skillName(cfg.skillId)}" +
                " · ${cfg.prompt.length} 字 · " + if (cfg.prompt.contains("replies")) "含 JSON 契约" else "缺少 JSON 契约",
            if (cfg.prompt.length >= 80 && cfg.prompt.contains("replies")) Level.OK else Level.WARN,
        ),
        Check(
            "调用节奏",
            "参考最近 ${cfg.ctx} 条 · 最短间隔 ${cfg.minIntervalSec}s · " +
                if (cfg.maxTokens == 0) "token 无限制" else "单次上限 ${cfg.maxTokens} token",
            Level.OK,
        ),
        Check(
            "敏感内容检查",
            if (cfg.allowSensitive) "已关闭：聊天内容会直接发给接口" else "开启：命中验证码/银行卡/转账等会先问一次",
            if (cfg.allowSensitive) Level.WARN else Level.OK,
        ),
    )
    val bad = checks.count { it.level == Level.BAD }
    val warn = checks.count { it.level == Level.WARN }
    val ok = checks.count { it.level == Level.OK }
    val overall = if (bad > 0) Level.BAD else if (warn > 0) Level.WARN else Level.OK
    // 明细按「**异常优先**」排：有问题 → 待确认 → 通过（同档内保持原来的顺序 —— Kotlin 的 sortedBy 是稳定排序）。
    // 以前固定按声明顺序铺开，出问题时最该动的那一条可能排在第 5 位，一眼看不出该先处理哪个。
    val ordered = checks.sortedBy {
        when (it.level) {
            Level.BAD -> 0
            Level.WARN -> 1
            Level.OK -> 2
        }
    }
    val shown = when (filter) {
        1 -> ordered.filter { it.level == Level.BAD }
        2 -> ordered.filter { it.level == Level.WARN }
        3 -> ordered.filter { it.level == Level.OK }
        else -> ordered
    }

    LazyColumn(contentPadding = PaddingValues(bottom = 120.dp)) {
        item {
            ScreenHeader("运行状态", "WeChat · 聊天助手 ${appVersion(context)}") {
                HeaderButton("↻ 刷新") { tick++ }
                Button(
                    onClick = onTrial,
                    shape = RoundedCornerShape(RadiusR2),
                    colors = ButtonDefaults.buttonColors(containerColor = palette.primary),
                ) { Text("试聊") }
            }
        }
        item {
            val tone = levelColor(palette, overall)
            GlassCard(glass, border = tone.copy(alpha = CardBorderAlpha)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // 批 4c：不再铺一块 46dp 实心色 —— 淡底 + 1dp 描边 + 状态色符号。
                    // 全屏「大面积」只留这一处，而且是空心的圈，红起来也不刺眼。
                    Box(
                        Modifier.size(46.dp).clip(CircleShape)
                            .background(tone.copy(alpha = ChipBgAlpha))
                            .border(1.dp, tone.copy(alpha = CardBorderAlpha), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            when (overall) { Level.OK -> "✓"; Level.WARN -> "!"; Level.BAD -> "✕" },
                            fontSize = 24.sp,
                            color = tone,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Column(Modifier.weight(1f).padding(start = 14.dp)) {
                        Text(
                            when (overall) { Level.OK -> "全部就绪"; Level.WARN -> "基本可用"; Level.BAD -> "需要处理" },
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = palette.text,
                        )
                        Text(
                            "${checks.size} 项已检测 · 通过 $ok · 待确认 $warn · 有问题 $bad",
                            fontSize = TextSize.body,
                            color = palette.sub,
                        )
                        // hero 的职责收窄成两件事：一眼看健康 + 直接点出**最该处理的那一条**。
                        // 完整明细整包交给下面的「详细状态」（那边是异常优先排序）。
                        val firstIssue = ordered.firstOrNull { it.level != Level.OK }
                        if (firstIssue != null) {
                            Text(
                                "最该处理：${firstIssue.title} —— ${firstIssue.desc.take(70)}",
                                fontSize = TextSize.body,
                                color = levelText(palette, firstIssue.level),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(Space.xl))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatCell("${usage.first}", "调用次数", Modifier.weight(1f))
                    StatCell("${usage.second}", "累计 token", Modifier.weight(1f))
                    StatCell(formatTime(beat).takeIf { beat > 0 } ?: "—", "最近心跳", Modifier.weight(1f))
                }
            }
        }
        item {
            Column {
                // 原来那 7 项明细是直接铺在页面上的，把这一页撑得很长；现在收在折叠菜单里
                DetailToggle(
                    expanded = detail,
                    // 摘要里把「异常优先」写明：展开后第一眼就是该动的那几条，不用自己往下翻
                    summary = "详细状态（异常优先）· ${checks.size} 项：有问题 $bad · 待确认 $warn · 通过 $ok",
                    glassAlpha = glass,
                ) { detail = !detail }
                AnimatedVisibility(
                    visible = detail,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut(),
                ) {
                    Column {
                        GlassCard(glass) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                GlassPill("全部 ${checks.size}", filter == 0, Modifier.weight(1f)) { filter = 0 }
                                GlassPill("有问题 $bad", filter == 1, Modifier.weight(1f)) { filter = 1 }
                                GlassPill("待确认 $warn", filter == 2, Modifier.weight(1f)) { filter = 2 }
                                GlassPill("通过 $ok", filter == 3, Modifier.weight(1f)) { filter = 3 }
                            }
                        }
                        shown.forEach { CheckRow(it, glass) }
                    }
                }
            }
        }
        item {
            GlassCard(glass) {
                Text("接口自检", fontSize = TextSize.cardTitle, fontWeight = FontWeight.SemiBold, color = palette.text)
                Text(
                    "只看「连不连得上、要多久」：发一次最小请求（一句 ping、只让它回 1 个 token）。\n" +
                        "不拼当前 skill、也不看模型回了什么 —— 模型没按 JSON 回复算不上连接问题，" +
                        "那种情况去「试一试」页验证。",
                    fontSize = TextSize.body,
                    color = palette.sub,
                )

                val baseUrl = cfg.baseUrl
                val host = remember(baseUrl) { NetInfo.hostOf(baseUrl) }
                // 内网 IP / 目标 IP：本地或 DNS 就能拿到，不依赖任何第三方
                val localIp by produceState<String?>(null, tick) {
                    value = withContext(Dispatchers.IO) { NetInfo.localIpv4() }
                }
                val targetIp by produceState<String?>(null, tick) {
                    value = withContext(Dispatchers.IO) { NetInfo.resolve(host) }
                }
                // 归属地：要问第三方，拿不到就 null（缓存 10 分钟；点「开始自检」会强制重查）。
                // 关掉开关就一次都不问；地址也能换成自己的（见「高级设置 → 诊断 → 网络信息」）。
                val geoUrl = remember(cfg.geoEndpoint) { cfg.geoEndpoint.trim().ifBlank { NetInfo.GEO_ENDPOINT } }
                val localGeo by produceState<Geo?>(null, tick, cfg.geoEnabled, geoUrl) {
                    value = if (!cfg.geoEnabled) null
                    else withContext(Dispatchers.IO) { NetInfo.geo(geoUrl) }
                }
                val targetGeo by produceState<Geo?>(null, tick, targetIp, cfg.geoEnabled, geoUrl) {
                    value = if (!cfg.geoEnabled || targetIp == null) null
                    else withContext(Dispatchers.IO) { NetInfo.geo(geoUrl, ip = targetIp) }
                }

                Spacer(Modifier.height(Space.lg))
                NetRow("本机内网", localIp)
                NetRow(
                    "本机公网",
                    if (!cfg.geoEnabled) "（归属地查询已关闭）"
                    else joinInfo(localGeo?.ip, localGeo?.province ?: localGeo?.country, localGeo?.isp),
                )
                NetRow("目标服务器", joinInfo(targetIp, targetGeo?.province ?: targetGeo?.country), host)
                Spacer(Modifier.height(Space.xs))
                HintText(
                    if (!cfg.geoEnabled) "归属地查询已在「高级设置 → 诊断 → 网络信息」里关掉（IP 仍然只在本机解析）。"
                    else "省份来自第三方 IP 库，仅供参考；取不到就显示 null。可在「高级设置 → 诊断 → 网络信息」里关掉或换接口。",
                    fontSize = TextSize.caption,
                    color = palette.sub,
                )

                Spacer(Modifier.height(Space.lg))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Button(
                        onClick = {
                            probing = true
                            probeVerdict = null
                            probeDetail = null
                            val conf = store.load()
                            scope.launch {
                                var verdict: String
                                var detail: String? = null
                                try {
                                    val r = withContext(Dispatchers.IO) { LlmClient(conf).probe() }
                                    if (r.totalTokens > 0) store.addUsage(r.totalTokens)
                                    probeOk = true
                                    verdict = "通 · 往返 ${r.totalMs}ms · 模型 ${r.model} · ${r.totalTokens} token"
                                    val sb = StringBuilder()
                                    sb.append("写回复一路：连接 ")
                                        .append(r.connectMs?.let { "${it}ms" } ?: "未测得")
                                        .append(" · DNS ${r.dnsMs}ms · 往返 ${r.totalMs}ms")
                                    r.targetIp?.let { sb.append(" · 目标 ").append(it) }

                                    // 分级模式：第二套接口也得单独测 —— 它可能是完全不同的服务商。
                                    // 两路的延迟要分别列出来；慢的那一路决定整张卡片什么时候能出来。
                                    var riskMs = 0L
                                    if (conf.graded) {
                                        try {
                                            val r2 = withContext(Dispatchers.IO) {
                                                LlmClient(conf.riskEndpoint(), second = true).probe()
                                            }
                                            if (r2.totalTokens > 0) store.addUsage(r2.totalTokens)
                                            riskMs = r2.totalMs
                                            sb.append("\n风险一路：连接 ")
                                                .append(r2.connectMs?.let { "${it}ms" } ?: "未测得")
                                                .append(" · DNS ${r2.dnsMs}ms · 往返 ${r2.totalMs}ms")
                                            r2.targetIp?.let { sb.append(" · 目标 ").append(it) }
                                            val diff = kotlin.math.abs(r.totalMs - r2.totalMs)
                                            if (diff > 300) {
                                                sb.append("\n两路差 ${diff}ms —— 快的那路要等慢的那路（自检只量最小请求，实际生成更久）")
                                            }
                                        } catch (t: Throwable) {
                                            sb.append("\n风险一路：失败 —— ").append(t.message)
                                        }
                                    }
                                    detail = sb.toString()
                                    // 记下来：设置页的公告栏要显示，运行时「两路对齐等待」也用它
                                    store.saveProbeMs(r.totalMs, riskMs)
                                } catch (t: Throwable) {
                                    probeOk = false
                                    verdict = "失败：${t.message}"
                                    detail = (t as? LlmException)?.hint
                                }
                                // 让归属地跟着这次自检重新查一遍
                                NetInfo.clearGeoCache()
                                tick++
                                probeVerdict = verdict
                                probeDetail = detail
                                probing = false
                            }
                        },
                        enabled = !probing,
                        shape = RoundedCornerShape(RadiusR2),
                        colors = ButtonDefaults.buttonColors(containerColor = palette.primary),
                    ) { Text(if (probing) "测试中…" else "开始自检") }
                    Spacer(Modifier.width(10.dp))
                    Text(
                        probeVerdict ?: "",
                        fontSize = TextSize.body,
                        color = if (probeOk) palette.ok else palette.bad,
                    )
                }
                probeDetail?.let {
                    Spacer(Modifier.height(Space.xs))
                    Text(it, fontSize = TextSize.body, color = palette.sub)
                }
            }
        }
    }
}

// ================= 试一试 =================
