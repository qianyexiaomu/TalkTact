package dev.goutou.wingman.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import dev.goutou.wingman.llm.ApiShape
import dev.goutou.wingman.llm.PROVIDER_PRESETS
import dev.goutou.wingman.llm.presetOf
import dev.goutou.wingman.llm.shapeOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import dev.goutou.wingman.config.ConfigData
import dev.goutou.wingman.config.Backup
import dev.goutou.wingman.config.ConfigStore
import androidx.core.content.ContextCompat
import dev.goutou.wingman.proxy.ProxyProtocol
import dev.goutou.wingman.proxy.ProxyState
import dev.goutou.wingman.config.IMAGE_MODE_OCR
import dev.goutou.wingman.config.IMAGE_MODE_OCR_THEN_VISION
import dev.goutou.wingman.config.IMAGE_MODE_VISION
import dev.goutou.wingman.proxy.ProxyService
import dev.goutou.wingman.config.Roles
import dev.goutou.wingman.config.replyPlan
import dev.goutou.wingman.config.replyPlanFor
import dev.goutou.wingman.llm.ReplyStyle
import dev.goutou.wingman.llm.REPLY_STYLES
import dev.goutou.wingman.llm.DEFAULT_STYLE_IDS
import dev.goutou.wingman.llm.toggledStyleIds
import dev.goutou.wingman.llm.MAX_CUSTOM_LABEL_LEN
import dev.goutou.wingman.llm.MAX_CUSTOM_STYLES
import dev.goutou.wingman.llm.MAX_REPLIES
import dev.goutou.wingman.llm.customStyle
import dev.goutou.wingman.llm.applyReplyPlan
import dev.goutou.wingman.llm.LlmClient
import dev.goutou.wingman.llm.ModelList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

@Composable
fun AdvancedScreen(
    store: ConfigStore,
    ui: ConfigData,
    onSaved: () -> Unit,
    onOpenDiag: () -> Unit,
    onOpenCandidates: () -> Unit,
    onBack: () -> Unit,
) {
    val palette = LocalPalette.current
    val context = LocalContext.current
    var d by remember { mutableStateOf(ui) }
    var includeKey by remember { mutableStateOf(true) }
    var backupNote by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    // 「拉取模型列表」：拉回来存本机（列表 + 时间），进页面读缓存；手动输入永远保留
    val cachedModels = remember { store.modelList() }
    var models by remember { mutableStateOf(cachedModels.first) }
    var modelsAt by remember { mutableStateOf(cachedModels.second) }
    var modelsOpen by remember { mutableStateOf(false) }
    var modelsNote by remember { mutableStateOf<String?>(null) }
    var pulling by remember { mutableStateOf(false) }
    // 第二套接口（分级模式那一跳）的模型列表：跟第一套各存各的
    val cachedModels2 = remember { store.modelList(second = true) }
    var models2 by remember { mutableStateOf(cachedModels2.first) }
    var models2At by remember { mutableStateOf(cachedModels2.second) }
    var models2Open by remember { mutableStateOf(false) }
    var modelsNote2 by remember { mutableStateOf<String?>(null) }
    var pulling2 by remember { mutableStateOf(false) }
    // 「有没有还没点保存的改动」：拿草稿和**上次落盘的那份**比。
    // 不能只看一个「保存过没有」的标志 —— 它一开始就是「没保存过」，
    // 会把刚打开的页面也说成「未保存」，红字就废了。
    var persisted by remember { mutableStateOf(ui) }
    val dirty = d != persisted

    fun update(next: ConfigData) {
        d = next
    }

    fun saveAll() {
        store.save(d)
        d = store.load()
        persisted = d
        onSaved()
    }

    /** 疑似非对话模型（embedding / tts 这些）排到后面 —— 只排序，不隐藏。 */
    fun orderedModels(list: List<String>): List<String> =
        list.filterNot { ModelList.looksNonChat(it) } + list.filter { ModelList.looksNonChat(it) }

    /**
     * 拉一次模型列表。用的是**当前草稿**里的地址与 Key（可能还没保存）——
     * 「填完 Key 先试一下能不能拉」不用先按保存。失败也不清空手里那份：服务商偶尔抽风。
     */
    fun pullModels(second: Boolean = false) {
        if (second) {
            pulling2 = true
            modelsNote2 = null
        } else {
            pulling = true
            modelsNote = null
        }
        // 第二套留空 = 复用第一套（和「生成模式」里那三格的规矩一致）
        val base = if (second) d.baseUrl2.trim().ifBlank { d.baseUrl } else d.baseUrl
        val key = if (second) d.apiKey2.trim().ifBlank { d.apiKey } else d.apiKey
        scope.launch {
            val got = withContext(Dispatchers.IO) { runCatching { ModelList.fetch(base, key) } }
            got.onSuccess { list ->
                val note = if (list.isEmpty()) "接口通了，但没解析出模型名 —— 手动填吧" else "拉到 ${list.size} 个模型"
                if (second) {
                    models2 = list
                    models2At = System.currentTimeMillis()
                    modelsNote2 = note
                } else {
                    models = list
                    modelsAt = System.currentTimeMillis()
                    modelsNote = note
                }
                store.saveModelList(list, second)
            }.onFailure { t ->
                if (second) modelsNote2 = "拉取失败：${t.message}" else modelsNote = "拉取失败：${t.message}"
            }
            if (second) pulling2 = false else pulling = false
        }
    }

    /**
     * 白名单的开关 / 添加 / 移除都**立刻落盘**（跟「本地代理」那个开关同一个道理）。
     *
     * 这张卡是全页唯一没有自己「保存」按钮的卡 —— 只改草稿 d 的话，注入侧读到的还是旧值，
     * 表现出来就是「白名单明明开了，没加的聊天照样被分析」（用户实测踩到，还以为是缓存）。
     * 只写这两项（`ConfigStore.saveWhitelist`）：这页还有别的卡在编辑、还没点保存，
     * 不能顺手把它们一起定死。二级页也要用同一份写法，所以这件事放在 store 上。
     */
    fun saveWhitelist(enabled: Boolean, chats: Set<String>) {
        store.saveWhitelist(enabled, chats)
        val fresh = store.load()
        // 只把这两项同步回草稿 —— 别的地方可能还有没保存的编辑，不能整份覆盖
        d = d.copy(whitelistEnabled = fresh.whitelistEnabled, whitelist = fresh.whitelist)
        persisted = fresh
        onSaved()
    }

    /**
     * 「图片文字识别」开关也**立刻落盘** —— 和它上面那张「本地代理」卡连着用：
     * 代理开着、识图关着，效果是白装十几 MB；两个开关的行为一致，才不会让人以为坏了。
     */
    fun saveOcr(on: Boolean) {
        store.saveOcr(on)
        val fresh = store.load()
        d = d.copy(ocrEnabled = fresh.ocrEnabled)
        persisted = fresh
        onSaved()
    }

    /**
     * 「图片怎么交给模型」也**立刻落盘** —— 它和识别开关是同一件事的两半，
     * 改完就要去微信里试，不该还要记得回来点一次保存。
     */
    fun saveImageMode(mode: String) {
        store.saveImageMode(mode)
        val fresh = store.load()
        d = d.copy(imageMode = fresh.imageMode)
        persisted = fresh
        onSaved()
    }

    /**
     * 「生成模式」两个 pill 也**立刻落盘**（原因见 `ConfigStore.saveGraded`）。
     * 只把这一项同步回草稿与「上次落盘的那份」—— 别的卡可能还在编辑、还没点保存，
     * 不能整份覆盖，也不能让假红字亮起来。
     */
    fun saveGraded(on: Boolean) {
        store.saveGraded(on)
        val fresh = store.load()
        d = d.copy(graded = fresh.graded)
        persisted = fresh
        onSaved()
    }

    /**
 * 「勾了哪些风格」也**立刻落盘**（原因见 `ConfigStore.saveReplyPlan`）—— 顺手把条数一起更新，
 * 因为**条数就是从这份勾选派生的**（勾几种就写几条，见 `llm/plannedReplyCount`）。
 *
 * 撇开草稿直接落盘是因为它是纯选择型的：用户点一下药丸，期待的就是下次调用按新规格来。
 * 让它只改草稿、混在页底那个「保存」后面，一定会出现「我明明勾了怎么还是旧的」。
 * 同步回草稿时**只动这两个字段**，别把这一页还在编辑的接口地址一起定死。
 */
fun saveReplyPlan(styleIds: List<String>) {
    store.saveReplyPlan(styleIds)
    val fresh = store.load()
    d = d.copy(replyCount = fresh.replyCount, replyStyleIds = fresh.replyStyleIds)
    persisted = fresh
    onSaved()
}

/**
 * 「自定义风格」加/删也**立刻落盘**，顺带把提示词快照重拼（见 `ConfigStore.saveCustomStyles`）。
 *
 * 这里多同步一个 [ConfigData.replyStyleIds]：加了风格顺手就把它选上（见下面卡片的说明），
 * 只回写风格列表的话，界面上「已选中」的那颗药丸会和配置里存的对不上。
 */
fun saveCustomStyles(styles: List<ReplyStyle>, styleIds: List<String>) {
    store.saveCustomStyles(styles)
    store.saveReplyPlan(styleIds)
    val fresh = store.load()
    d = d.copy(
        replyCustomStyles = fresh.replyCustomStyles,
        replyStyleIds = fresh.replyStyleIds,
    )
    persisted = fresh
    onSaved()
}

/** 「按联系人单独设置」落盘（立刻生效，见 `ConfigStore.setChatStyles`）。 */
fun saveChatStyles(map: Map<String, List<String>>) {
    store.setChatStyles(map)
    val fresh = store.load()
    d = d.copy(chatStyles = fresh.chatStyles)
    persisted = fresh
    onSaved()
}

/**
 * 把**当前生效范围**的勾选落盘。
 *
 * 范围 = 所有人 → 走原来那条（[saveReplyPlan]，顺带重拼提示词快照）；
 * 范围 = 某个会话 → 只写这一项，**不重拼快照**：那位用的规格是请求时按会话现拼的
 *（见 llm/LlmClient 的 `applyReplyPlan(systemText, plan)`），快照保持全局那份。
 */
fun saveScopedStyles(scope: String, ids: List<String>) {
    if (scope.isBlank()) {
        saveReplyPlan(ids)
        return
    }
    saveChatStyles(d.chatStyles + (scope to ids.filter { it.isNotBlank() }.distinct()))
}

val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            backupNote = try {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(Backup.export(store.load(), store.roles(), includeKey).toByteArray(Charsets.UTF_8))
                }
                "已导出到所选文件"
            } catch (t: Throwable) {
                "导出失败：${t.message}"
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            backupNote = try {
                val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }.orEmpty()
                val result = Backup.import(text, store.load(), store.roles())
                if (result == null) {
                    "导入失败：不是有效的备份文件"
                } else {
                    store.save(result.config)
                    store.saveRoles(result.roles)
                    d = store.load()
                    persisted = d
                    onSaved()
                    "已导入：配置 + ${result.roleCount} 个角色"
                }
            } catch (t: Throwable) {
                "导入失败：${t.message}"
            }
        }
    }

    var diagNote by remember { mutableStateOf<String?>(null) }
    var proxyNote by remember { mutableStateOf<String?>(null) }
    var ocrNote by remember { mutableStateOf<String?>(null) }
    var whitelistNote by remember { mutableStateOf<String?>(null) }
    // 会话名候选是注入侧用广播回传的，这一页不会自己重组 —— 停在这里时每 1.5 秒自己看一眼。
    // （去微信转一圈再回来，看到的就是刚回传的那份。）
    // 这里的第三项是「图片识别」的最近一次结果 —— 它同样是注入侧广播回传的，
    // 借同一次轮询一起读，省掉第二个定时器。
    //
    // ⚠️ 轮询是**有上限**的（下面 repeat(200)，约 5 分钟）。而典型用法恰好是
    // 「打开这一页看一眼 → 去微信里试 → 切回来」，那往往已经超过 5 分钟了 ——
    // 于是页面还停在旧值上（真机反馈过「明明认了字，App 里还是『还没试过』」就是这个）。
    // 所以每次回到前台（ON_RESUME）都重启一轮。
    val resumed = resumedTick()
    val pulled by produceState(
        initialValue = Triple(store.chatCandidates(), store.chatPullInfo(), store.ocrInfo()),
        key1 = resumed,
    ) {
        // 轮询**有上限**（约 5 分钟）：一个是别让这个页面永远挂着定时任务，
        // 另一个是单元测试里跑渲染回归时，无限循环的协程可能把测试挂住 —— 有界就没有这种风险。
        repeat(200) {
            kotlinx.coroutines.delay(1500)
            value = Triple(store.chatCandidates(), store.chatPullInfo(), store.ocrInfo())
        }
    }
    val cands = pulled.first
    val reqAt = store.chatRequestAt()
    // 拿「我什么时候点的」和「微信侧什么时候回的」比 —— 这两件事分开显示，
    // 才能一眼看出是「请求没送到」还是「送到了但没读出名字」（排查方向完全相反）
    val pullInfo = pulled.second.let { (info, at) ->
        when {
            at > reqAt -> info.ifBlank { "已收到回传" } + " · " + clockText(at)
            reqAt > 0L -> "已请求 ${clockText(reqAt)} · 微信侧还没回过话"
            else -> "还没拉过"
        }
    }
    val ocrStatus = pulled.third.let { (info, at) ->
        val head = info.ifBlank { "还没试过（微信里遇到图片消息才会有记录）" }
        if (at > 0L) "$head · ${clockText(at)}" else head
    }
    var newChat by remember { mutableStateOf("") }
    // 白名单的「长按多选」状态。
    // （候选名单那一份跟着名单一起搬到二级页了，见 ChatCandidatesScreen —— 那边自己持有一份。）
    var whitePicking by remember { mutableStateOf(false) }
    var whitePicked by remember { mutableStateOf(emptySet<String>()) }
    // 被「删掉」的候选个数（记在「已忽略」里）—— 界面靠它显示「恢复」
    var ignoredCount by remember { mutableStateOf(store.chatIgnored().size) }
    val proxyScope = rememberCoroutineScope()
    // 13+ 才有的通知权限。注意：前台服务**没有**它也照样能跑（系统仍会显示这条常驻通知），
    // 但既然要弹，就在用户打开开关时顺手要一下 —— 不然以后那条通知可能被折叠/静音。
    val notifPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        proxyNote = if (granted) "已授予通知权限" else "没给通知权限 —— 代理照跑，但系统更容易把它回收"
    }
    val diagLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri ->
        if (uri != null) {
            diagNote = try {
                context.contentResolver.openOutputStream(uri)?.use { it.write(buildDiagZip(context, store, d)) }
                "已导出诊断包 —— 发之前建议先打开看一眼"
            } catch (t: Throwable) {
                "导出失败：${t.message}"
            }
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 120.dp)) {
        ScreenHeader("高级设置", "接口 · 分析 · 备份 · 诊断") {
            HeaderButton("← 返回", onBack)
        }

        // 这一页很长，页底那个红字常常不在视野里 —— 顶部也放一条，滚到哪儿都知道「有东西没存」。
        // 只是提示，不强制：接口地址那种改一半的情况，本来就该由你决定什么时候存。
        if (dirty) {
            Text(
                "⚠ 本页有未保存的改动（白名单 / 识图 / 生成模式是改完立刻生效，其余要按页底那个「保存」）",
                fontSize = TextSize.body,
                color = palette.bad,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
            )
        }

        GroupTitle("接入")

        GlassCard(d.glassAlpha) {
            // 公告栏放最上面：换接口/换模型最该先知道的就是「这俩大概要等多久」
            val (replyMs, riskMs) = store.probeMs()
            NoticeBanner(
                title = "自检延迟（和接口、模型都有关）",
                lines = buildList {
                    add(if (replyMs > 0) "${d.model}（写回复）：${replyMs}ms" else "${d.model}（写回复）：还没测过")
                    if (d.graded) {
                        val m2 = d.model2.ifBlank { d.model }
                        add(if (riskMs > 0) "$m2（风险）：${riskMs}ms" else "$m2（风险）：还没测过")
                        if (replyMs > 0L && riskMs > 0L) {
                            add("两路差 ${kotlin.math.abs(replyMs - riskMs)}ms —— 卡片要等慢的那一路")
                        }
                    }
                    add("换接口或换模型都会让它变；回首页点「接口自检」重新测一次。")
                },
            )
            Spacer(Modifier.height(Space.lg))
            Text("接口地址", fontSize = TextSize.cardTitle, fontWeight = FontWeight.SemiBold, color = palette.text)
            Spacer(Modifier.height(Space.md))

            // 服务商预设：**只省掉「去官网查地址」这一步**。选中后地址 / 模型照样能手改，
            // 也不改变任何请求逻辑 —— 我们本来就是「OpenAI 兼容 + 自定义 base_url + 自定义 model」。
            val chosenPreset = presetOf(d.provider)
            var providerOpen by remember { mutableStateOf(false) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("服务商", fontSize = TextSize.body, color = palette.sub, modifier = Modifier.weight(1f))
                Box {
                    OutlinedButton(
                        onClick = { providerOpen = true },
                        shape = RoundedCornerShape(RadiusR1),
                    ) { Text(chosenPreset?.label ?: "自定义（自己填）", fontSize = TextSize.body) }
                    DropdownMenu(expanded = providerOpen, onDismissRequest = { providerOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("自定义（自己填）", fontSize = TextSize.body) },
                            onClick = {
                                providerOpen = false
                                update(d.copy(provider = ""))
                            },
                        )
                        PROVIDER_PRESETS.forEach { p ->
                            DropdownMenuItem(
                                text = { Text(p.label, fontSize = TextSize.body) },
                                onClick = {
                                    providerOpen = false
                                    // 选预设 = 把「地址 / 形态 / 模型（有才填）」写进草稿，仍然可改
                                    update(
                                        d.copy(
                                            provider = p.id,
                                            baseUrl = p.baseUrl,
                                            apiShape = p.shape.id,
                                            model = p.model.ifBlank { d.model },
                                        ),
                                    )
                                },
                            )
                        }
                    }
                }
            }
            chosenPreset?.note?.let {
                Spacer(Modifier.height(Space.xs))
                Text(it, fontSize = TextSize.body, color = palette.sub)
            }
            Spacer(Modifier.height(Space.lg))

            // 接口形态：差异只有 URL 路径 / 请求体 / 鉴权头三处（见 llm/ApiShape.kt）。
            val shapeNow = shapeOf(d.apiShape)
            var shapeOpen by remember { mutableStateOf(false) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("接口形态", fontSize = TextSize.body, color = palette.sub, modifier = Modifier.weight(1f))
                Box {
                    OutlinedButton(
                        onClick = { shapeOpen = true },
                        shape = RoundedCornerShape(RadiusR1),
                    ) { Text(shapeNow.label, fontSize = TextSize.body) }
                    DropdownMenu(expanded = shapeOpen, onDismissRequest = { shapeOpen = false }) {
                        ApiShape.values().forEach { s ->
                            DropdownMenuItem(
                                text = { Text(s.label, fontSize = TextSize.body) },
                                onClick = {
                                    shapeOpen = false
                                    update(d.copy(apiShape = s.id))
                                },
                            )
                        }
                    }
                }
            }
            Text(shapeNow.note, fontSize = TextSize.body, color = palette.sub)
            if (d.proxyEnabled && !shapeNow.usableThroughProxy) {
                Spacer(Modifier.height(Space.xs))
                Text(
                    "⚠ 本地代理只转发 OpenAI 兼容形态：「${shapeNow.label}」请直连 —— " +
                        "到下面把「本地代理」关掉，或者把形态换回 OpenAI 兼容。",
                    fontSize = TextSize.body,
                    color = palette.warn,
                )
            }
            if (shapeNow == ApiShape.CUSTOM) {
                Spacer(Modifier.height(Space.md))
                GlassField(
                    value = d.customPath,
                    onValueChange = { update(d.copy(customPath = it)) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("路径（留空 = 这个地址本身就是完整端点）") },
                    singleLine = true,
                )
            }
            Spacer(Modifier.height(Space.lg))

            GlassField(
                value = d.baseUrl,
                onValueChange = { update(d.copy(baseUrl = it)) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("接口地址（OpenAI 兼容，写到 /v1）") },
                singleLine = true,
            )
            Spacer(Modifier.height(Space.md))
            GlassField(
                value = d.apiKey,
                onValueChange = { update(d.copy(apiKey = it)) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("API Key") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
            )
            Spacer(Modifier.height(Space.md))
            GlassField(
                value = d.model,
                onValueChange = { update(d.copy(model = it)) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("模型") },
                singleLine = true,
            )
            Spacer(Modifier.height(Space.md))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(
                    onClick = { pullModels(second = false) },
                    enabled = !pulling,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(RadiusR2),
                ) { Text(if (pulling) "拉取中…" else "从服务端拉取模型列表") }
                if (models.isNotEmpty()) {
                    Spacer(Modifier.width(8.dp))
                    Box {
                        OutlinedButton(onClick = { modelsOpen = true }, shape = RoundedCornerShape(RadiusR2)) {
                            Text("选模型（${models.size}）")
                        }
                        DropdownMenu(expanded = modelsOpen, onDismissRequest = { modelsOpen = false }) {
                            orderedModels(models).take(80).forEach { id ->
                                DropdownMenuItem(
                                    text = { Text(id, fontSize = TextSize.body) },
                                    onClick = {
                                        update(d.copy(model = id))
                                        modelsOpen = false
                                    },
                                )
                            }
                        }
                    }
                }
            }
            modelsNote?.let {
                Spacer(Modifier.height(Space.xs))
                Text(it, fontSize = TextSize.body, color = if (it.startsWith("拉取失败")) palette.bad else palette.ok)
            }
            if (modelsAt > 0L) {
                Spacer(Modifier.height(2.dp))
                Text("上次拉取 ${formatTime(modelsAt)}（存在本机；换了接口 / Key 记得重新拉一次）", fontSize = TextSize.caption, color = palette.sub)
            }
            Spacer(Modifier.height(Space.xs))
            HintText(
                "拉不到不代表接口不能用：有的服务商没这个接口，有的中转只回一份目录（列出来 ≠ 你的 Key 能用）。手动填一样用。",
                fontSize = TextSize.caption,
                color = palette.sub,
            )
            Spacer(Modifier.height(Space.xl))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("严格 JSON 输出", fontSize = TextSize.emphasis, color = palette.text)
                    HintText(
                        "请求里带 response_format=json_object，让服务端保证回的是合法 JSON，" +
                            "能少一些「模型没返回 JSON」。有些中转不支持 —— 开了报错就关掉。" +
                            "只影响生成候选，不影响单条改写和风格提炼。",
                        fontSize = TextSize.body,
                        color = palette.sub,
                    )
                }
                Switch(checked = d.jsonMode, onCheckedChange = { update(d.copy(jsonMode = it)) })
            }
            Spacer(Modifier.height(Space.xl))
            Text("单次回复的 token 上限", fontSize = TextSize.body, color = palette.sub)
            Spacer(Modifier.height(Space.sm))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                GlassPill("200", d.maxTokens == 200, Modifier.weight(1f)) { update(d.copy(maxTokens = 200)) }
                GlassPill("400", d.maxTokens == 400, Modifier.weight(1f)) { update(d.copy(maxTokens = 400)) }
                GlassPill("600", d.maxTokens == 600, Modifier.weight(1f)) { update(d.copy(maxTokens = 600)) }
                GlassPill("无限制", d.maxTokens == 0, Modifier.weight(1f)) { update(d.copy(maxTokens = 0)) }
            }
            Spacer(Modifier.height(Space.sm))
            Text(
                if (d.maxTokens == 0) "无限制：请求里不带 max_tokens，由服务端决定（进阶 skill 建议用这档）"
                else "越小越省额度，但 skill 提示词较长时可能被截断",
                fontSize = TextSize.body,
                color = palette.sub,
            )
        }

        SectionCard(d.glassAlpha, "本地代理（API Key 不出本应用）") {
            Text(
                "开启后：聊天内容先送到本机的 127.0.0.1，由这个 App 带上 Key 去调你的接口。\n" +
                    "注入到微信里的那段代码从此**拿不到 Key** —— 做法不是加密，是根本不给它。",
                fontSize = TextSize.body,
                color = palette.sub,
            )
            Spacer(Modifier.height(Space.md))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("启用本地代理", fontSize = TextSize.emphasis, color = palette.text)
                    Text(
                        if (d.proxyEnabled) {
                            "已开：App 需要保持运行（前台服务 + 一条常驻通知）"
                        } else {
                            "关着：走直连（Key 明文存在本机，并且会推给微信进程）"
                        },
                        fontSize = TextSize.body,
                        color = palette.sub,
                    )
                }
                Switch(
                    checked = d.proxyEnabled,
                    onCheckedChange = { on ->
                        // 开关立刻落盘：注入侧是按配置里的 proxyEnabled 决定走哪条路的，
                        // 等用户再点一次「保存」的话，这里会有一段「看起来开了其实没开」的空窗。
                        store.save(d.copy(proxyEnabled = on))
                        d = store.load()
                        persisted = d
                        if (on) {
                            store.ensureProxyToken()
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                                PackageManager.PERMISSION_GRANTED
                            ) {
                                notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                            ProxyService.start(context)
                            proxyNote = "已开启。到微信里试一次识别；如果一直失败，把这里关掉就退回直连。"
                        } else {
                            ProxyService.stop(context)
                            proxyNote = "已关闭：Key 会重新推给微信进程（下次识别生效）。"
                        }
                    },
                )
            }
            if (d.proxyEnabled) {
                Spacer(Modifier.height(Space.sm))
                // 状态行直接读进程内的 ProxyState：服务到底起没起来、为什么没起来，一眼可见
                Text(
                    when {
                        ProxyState.running ->
                            "● 服务在跑：127.0.0.1:${ProxyState.port}" +
                                (ProxyState.lastResult?.let { " · 最近：$it" } ?: " · 还没被请求过")
                        ProxyState.lastError != null -> "● 服务没起来：${ProxyState.lastError}"
                        else -> "● 服务没起来（点上面的开关关掉再开一次；若仍不行点「测试代理」看详情）"
                    },
                    fontSize = TextSize.body,
                    color = if (ProxyState.running) palette.ok else palette.bad,
                )
                // 活体诊断（issue #7①）：光说「服务在跑」不够 —— 得能分辨「一直好好跑着」
                // 和「刚被系统杀掉又被拉起来」。这两件事的处置方式完全不同：
                // 前者什么都不用做，后者要去加电池白名单。
                if (ProxyState.running) {
                    Text(
                        buildString {
                            append("● 前台服务：")
                            append(
                                if (ProxyState.foreground) "已挂上（系统不会轻易回收）"
                                else "没挂上 —— 系统随时可能连进程一起回收，点下面那个按钮",
                            )
                            append("\n● 已运行 ").append(agoText(ProxyState.startedAt))
                            append("　最近被请求：").append(
                                if (ProxyState.lastRequestAt <= 0L) "还没有过（微信侧还没走代理）"
                                else agoText(ProxyState.lastRequestAt),
                            )
                            if (ProxyState.restarts > 0) {
                                append("\n● 被系统重启过 ").append(ProxyState.restarts).append(" 次（进程被杀过）")
                            }
                        },
                        fontSize = TextSize.body,
                        color = if (ProxyState.foreground) palette.sub else palette.warn,
                    )
                    Spacer(Modifier.height(Space.sm))
                    OutlinedButton(onClick = { openBatteryWhitelist(context) }) {
                        Text("加电池优化白名单")
                    }
                }
                val (lastRoute, lastRouteAt) = store.lastRoute()
                if (lastRoute.isNotBlank() && lastRouteAt > 0L) {
                    val ok = lastRoute == ProxyProtocol.ROUTE_PROXY
                    Text(
                        "微信侧最近一次走的是：" + (if (ok) "本地代理 ✅" else "直连（代理还没被用上）") +
                            " · " + java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
                                .format(java.util.Date(lastRouteAt)),
                        fontSize = TextSize.body,
                        color = if (ok) palette.ok else palette.warn,
                    )
                } else {
                    Text("微信侧还没回传过路线 —— 到微信里点一次「↻ 重新识别」就会有了", fontSize = TextSize.body, color = palette.sub)
                }
                Spacer(Modifier.height(Space.xs))
                Text(
                    "提示：识别结果**有缓存**（同一条消息不会重复调接口）。开了代理之后，" +
                        "要对**新消息**点一次「↻ 重新识别」才会真正走代理。",
                    fontSize = TextSize.body,
                    color = palette.sub,
                )
                Spacer(Modifier.height(Space.md))
                GlassField(
                    value = d.proxyPort.toString(),
                    onValueChange = { update(d.copy(proxyPort = it.toIntOrNull() ?: d.proxyPort)) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("端口（1024-65535）") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                Spacer(Modifier.height(Space.md))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            proxyNote = "测试中…"
                            val port = d.proxyPort
                            proxyScope.launch {
                                proxyNote = withContext(Dispatchers.IO) {
                                    runCatching {
                                        val c = (java.net.URL("http://127.0.0.1:$port${ProxyProtocol.PATH_HEALTH}")
                                            .openConnection() as java.net.HttpURLConnection)
                                        c.connectTimeout = 3_000
                                        c.readTimeout = 3_000
                                        val code = c.responseCode
                                        runCatching { c.disconnect() }
                                        code
                                    }.fold(
                                        { code -> if (code == 200) "代理正常（HTTP 200）" else "代理回话：HTTP $code" },
                                        { err ->
                                            "连不上：${err.message}" + (
                                                ProxyState.lastError?.let { " · 服务端说：$it" }
                                                    ?: " · 服务没起来（把上面的开关关掉再打开一次）"
                                                )
                                        },
                                    )
                                }
                            }
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(RadiusR2),
                    ) { Text("测试代理") }
                    OutlinedButton(
                        onClick = {
                            store.resetProxyToken()
                            d = store.load()
                            proxyNote = "已换新 token（微信里下次识别生效）"
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(RadiusR2),
                    ) { Text("换新 token") }
                }
                Spacer(Modifier.height(Space.sm))
                Text(
                    "⚠ 微信进程对 http 明文的策略不归我们管：如果开了之后微信里识别一直失败，" +
                        "把这里关掉就退回直连（注入侧给的报错里也会这么提示）。\n" +
                        "重启手机 / 覆盖安装之后代理会自己回来（不必再开一次 App）；" +
                        "微信侧连不上时它还会把 App 叫醒一次 —— 这两件事有没有真的发生，" +
                        "看上面那行的「已运行」和「最近被请求」。",
                    fontSize = TextSize.body,
                    color = palette.warn,
                )
                Spacer(Modifier.height(Space.xs))
                HintText(
                    "如果状态行显示「服务在跑」但过一会儿就没了：这个应用需要常驻，请把它加入系统的" +
                        "「电池优化白名单 / 允许后台运行」（各家名字不同：华为「应用启动管理」、小米「省电策略·无限制」、" +
                        "OPPO / vivo 类似）。",
                    fontSize = TextSize.body,
                    color = palette.sub,
                )
            }
            proxyNote?.let {
                Spacer(Modifier.height(Space.sm))
                Text(it, fontSize = TextSize.body, color = palette.sub)
            }
        }

        GroupTitle("生成")

        SectionCard(d.glassAlpha, "生成模式") {
            Spacer(Modifier.height(Space.md))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                GlassPill("直通（一套接口）", !d.graded, Modifier.weight(1f)) { saveGraded(false) }
                GlassPill("模型分级", d.graded, Modifier.weight(1f)) { saveGraded(true) }
            }
            Spacer(Modifier.height(Space.sm))
            HintText(
                if (!d.graded) {
                    "直通：所有事都交给一套接口 + 一份提示词，一次调用搞定（默认，和以前一样）。"
                } else {
                    "分级：拆成两路**并行**跑 —— 一路只判意图与风险，另一路只写 3 条回复，最后合成一张卡片。" +
                        "任一路挂了另一路照常显示（风险会标成「未评估」）。"
                },
                fontSize = TextSize.body,
                color = palette.sub,
            )
            Spacer(Modifier.height(Space.sm))
            Text(
                "这两个 pill 点一下就生效（立刻落盘，不用等页底那个「保存」）；下面的接口地址仍然是草稿。",
                fontSize = TextSize.body,
                color = palette.ok,
            )
            if (d.graded) {
                Spacer(Modifier.height(Space.lg))
                Text("第二套接口（只给「风险评估」那一路用）", fontSize = TextSize.body, color = palette.sub)
                Spacer(Modifier.height(Space.sm))
                GlassField(
                    value = d.baseUrl2,
                    onValueChange = { update(d.copy(baseUrl2 = it)) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("接口地址（留空 = 复用上面那套）") },
                    singleLine = true,
                )
                Spacer(Modifier.height(Space.md))
                GlassField(
                    value = d.apiKey2,
                    onValueChange = { update(d.copy(apiKey2 = it)) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("API Key（留空 = 复用）") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                )
                Spacer(Modifier.height(Space.md))
                GlassField(
                    value = d.model2,
                    onValueChange = { update(d.copy(model2 = it)) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("模型（留空 = 复用）") },
                    singleLine = true,
                )
                Spacer(Modifier.height(Space.md))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(
                        onClick = { pullModels(second = true) },
                        enabled = !pulling2,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(RadiusR2),
                    ) { Text(if (pulling2) "拉取中…" else "从服务端拉取模型列表（第二套）") }
                    if (models2.isNotEmpty()) {
                        Spacer(Modifier.width(8.dp))
                        Box {
                            OutlinedButton(onClick = { models2Open = true }, shape = RoundedCornerShape(RadiusR2)) {
                                Text("选模型（${models2.size}）")
                            }
                            DropdownMenu(expanded = models2Open, onDismissRequest = { models2Open = false }) {
                                orderedModels(models2).take(80).forEach { id ->
                                    DropdownMenuItem(
                                        text = { Text(id, fontSize = TextSize.body) },
                                        onClick = {
                                            update(d.copy(model2 = id))
                                            models2Open = false
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
                modelsNote2?.let {
                    Spacer(Modifier.height(Space.xs))
                    Text(it, fontSize = TextSize.body, color = if (it.startsWith("拉取失败")) palette.bad else palette.ok)
                }
                if (models2At > 0L) {
                    Spacer(Modifier.height(2.dp))
                    Text("上次拉取 ${formatTime(models2At)}（存在本机）", fontSize = TextSize.caption, color = palette.sub)
                }
                Spacer(Modifier.height(Space.xs))
                Text(
                    "拉取用的是第二套自己的地址 / Key；这两格也留空的话，就跟第一套一模一样。",
                    fontSize = TextSize.caption,
                    color = palette.sub,
                )
                Spacer(Modifier.height(Space.md))
                GlassPill("同上（三格都清空 = 复用第一套）", false, Modifier.fillMaxWidth()) {
                    update(d.copy(baseUrl2 = "", apiKey2 = "", model2 = ""))
                }
                Spacer(Modifier.height(Space.sm))
                Text(
                    "留空就复用第一套 —— 那等于「同一个模型拆两路提示词」，也完全能用。" +
                        "想省钱可以给风险那一路单独配个便宜的小模型（比如轻量档）。" +
                        "第二套不参与首页的「接口自检」，配错了会在卡片上直接写出来。",
                    fontSize = TextSize.body,
                    color = palette.sub,
                )
            }
        }

        Spacer(Modifier.height(Space.lg))

        ReplyStyleCard(
            d = d,
            store = store,
            palette = palette,
            saveChatStyles = { saveChatStyles(it) },
            saveCustomStyles = { styles, ids -> saveCustomStyles(styles, ids) },
            saveScopedStyles = { range, ids -> saveScopedStyles(range, ids) },
        )

        GroupTitle("识别")

        SectionCard(d.glassAlpha, "图片文字识别（OCR）") {
            Text(
                "聊天里的图片会先在这台手机上认成文字，再和别的消息一起交给模型 —— " +
                    "截图、长图里的话也能被读懂。\n" +
                    "认字完全离线：内置的中文模型，不联网、不下载、不需要 Play 服务，图片也不会上传" +
                    "（只在本机两个进程之间走一趟）。代价是这个安装包大了十几 MB。",
                fontSize = TextSize.body,
                color = palette.sub,
            )
            Spacer(Modifier.height(Space.md))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("识别聊天里的图片", fontSize = TextSize.emphasis, color = palette.text)
                    Text(
                        if (d.ocrEnabled) {
                            "开着：每轮分析前，屏幕上的图会先认一遍字"
                        } else {
                            "关着：图片还是老样子 —— 用 [图片/表情/语音] 占位"
                        },
                        fontSize = TextSize.body,
                        color = palette.sub,
                    )
                }
                Switch(
                    checked = d.ocrEnabled,
                    onCheckedChange = { on ->
                        saveOcr(on)
                        ocrNote = if (on) "已开 · 微信里下次识别生效" else "已关 · 图片回落成占位"
                    },
                )
            }
            if (d.ocrEnabled && !d.proxyEnabled) {
                Spacer(Modifier.height(Space.sm))
                Text(
                    "⚠ 还差一步：认字这件事在 App 进程里做，所以得先把上面那张卡的「本地代理」打开 —— " +
                        "否则微信那边连不上本机端口，会直接跳过（图片仍是占位）。",
                    fontSize = TextSize.body,
                    color = palette.warn,
                )
            }
            if (d.ocrEnabled) {
                Spacer(Modifier.height(Space.lg))
                Text("图片怎么交给模型", fontSize = TextSize.emphasis, color = palette.text)
                Text(
                    "「只认字」是默认：图片只在本机认成文字，不进任何请求，图不出手机。\n" +
                        "后两档会把**图片本体**一起发给你配置的那个接口 —— 换来的是「字认不出来 / " +
                        "表情包」这类内容也能被看懂。发出去的图是压缩过的 JPEG（最长边 1280）。",
                    fontSize = TextSize.body,
                    color = palette.sub,
                )
                Spacer(Modifier.height(Space.sm))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    GlassPill("只认字", d.imageMode == IMAGE_MODE_OCR, Modifier.weight(1f)) {
                        saveImageMode(IMAGE_MODE_OCR)
                    }
                    GlassPill("认不出才发图", d.imageMode == IMAGE_MODE_OCR_THEN_VISION, Modifier.weight(1f)) {
                        saveImageMode(IMAGE_MODE_OCR_THEN_VISION)
                    }
                    GlassPill("总是发图", d.imageMode == IMAGE_MODE_VISION, Modifier.weight(1f)) {
                        saveImageMode(IMAGE_MODE_VISION)
                    }
                }
                Spacer(Modifier.height(Space.sm))
                Text(
                    when (d.imageMode) {
                        IMAGE_MODE_VISION ->
                            "总是发图：每条带图的聊天都会把图发给接口（连表情包一起）。看图最准，也最费。"
                        IMAGE_MODE_OCR_THEN_VISION ->
                            "认不出才发图：只有在「一个字都没认出来」或「认出来的字少于 12 个」时才把图发出去 —— " +
                                "正是截图认不出、表情包没内容这两种情况。"
                        else ->
                            "只认字：图片不出手机。代价是字太小的缩略图认不出来，表情包也始终是个占位。"
                    },
                    fontSize = TextSize.body,
                    color = if (d.imageMode == IMAGE_MODE_OCR) palette.sub else palette.warn,
                )
            }
            Spacer(Modifier.height(Space.md))
            Text("最近一次：$ocrStatus", fontSize = TextSize.body, color = palette.sub)
            ocrNote?.let {
                Spacer(Modifier.height(Space.xs))
                Text(it, fontSize = TextSize.body, color = palette.ok)
            }
        }

        GroupTitle("微信内")

        GenerationParamsCard(d, dirty, palette, onUpdate = { update(it) }, onSave = { saveAll() })

        SectionCard(d.glassAlpha, "会话白名单") {
            // 白底说明卡放最上面：这一页最容易踩的坑（名字对不上、开关开着名单空着）先说清楚。
            // 白卡和玻璃卡刻意不同 —— 玻璃是界面的一部分，白卡是「贴上去的说明书」。
            WhiteCard {
                Text("怎么用", fontSize = TextSize.body, fontWeight = FontWeight.Medium, color = Color(0xFF1C1B22))
                listOf(
                    "打开开关后，只有名单里的聊天会触发小助手；不在名单里的，不读内容、不记角色、也不调接口。",
                    "加名字三种办法：① 下面的输入框手打；② 点「拉取会话列表」，回微信的首页 / 通讯录停两秒；" +
                        "③ 白名单开着时，打开某个聊天，它的名字会自己进来。",
                    "开关和增删都是改完立刻生效 —— 这张卡没有「保存」按钮。",
                ).forEach { line ->
                    Text(
                        "· $line",
                        fontSize = TextSize.body,
                        color = Color(0xFF55525E),
                        modifier = Modifier.padding(top = 3.dp),
                    )
                }
                Spacer(Modifier.height(Space.sm))
                Text("⚠ 注意事项", fontSize = TextSize.body, fontWeight = FontWeight.Medium, color = Color(0xFFB3261E))
                listOf(
                    "名字要和微信里显示的一致：群聊填群名；改过备注 / 昵称的，用改过之后的名字。",
                    "认不出会话名的聊天按「没勾」处理 —— 宁可不动，也不误发。",
                    "开关开着、名单却是空的 = 所有聊天都不工作。",
                    "个人资料页不是来源：那一页只有「微信号 / 地区」这类字段，读出来全是杂项。",
                    "拉取时要停在微信的列表页（首页 / 通讯录）；聊天页里读到的是消息正文，不是名字。",
                    "白名单关着时，模块不收集任何会话名（不打算用它的人，不会被攒一份联系人名单）。",
                    "移出白名单只是不再分析它；已经记下的角色和聊天记录不会被删掉。",
                ).forEach { line ->
                    Text(
                        "· $line",
                        fontSize = TextSize.body,
                        color = Color(0xFF55525E),
                        modifier = Modifier.padding(top = 3.dp),
                    )
                }
            }
            Spacer(Modifier.height(Space.md))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("只对白名单里的聊天生效", fontSize = TextSize.emphasis, color = palette.text)
                    Text(
                        if (d.whitelistEnabled) {
                            "已开：只有下面 ${d.whitelist.size} 个会话会工作"
                        } else {
                            "关着：所有聊天都会分析（默认）"
                        },
                        fontSize = TextSize.body,
                        color = palette.sub,
                    )
                }
                Switch(
                    checked = d.whitelistEnabled,
                    onCheckedChange = { on ->
                        saveWhitelist(on, d.whitelist)
                        whitelistNote = if (on) {
                            if (d.whitelist.isEmpty()) "已开，但名单还是空的 —— 等于所有聊天都不工作"
                            else "已开 · 只对下面这几个会话生效"
                        } else {
                            "已关 · 所有聊天都会分析"
                        }
                    },
                )
            }
            if (d.whitelistEnabled) {
                Spacer(Modifier.height(Space.md))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GlassField(
                        value = newChat,
                        onValueChange = { newChat = it },
                        modifier = Modifier.weight(1f),
                        label = { Text("写微信里显示的那个名字") },
                        singleLine = true,
                    )
                    Button(
                        onClick = {
                            val k = Roles.normalizeKey(newChat)
                            if (k.isNotBlank()) {
                                saveWhitelist(d.whitelistEnabled, d.whitelist + k)
                                whitelistNote = "已加入「$k」"
                                newChat = ""
                            }
                        },
                        shape = RoundedCornerShape(RadiusR2),
                        colors = ButtonDefaults.buttonColors(containerColor = palette.primary),
                    ) { Text("添加") }
                }
                if (d.whitelist.isEmpty()) {
                    Spacer(Modifier.height(Space.sm))
                    Text(
                        "还是空的 —— 开着白名单却一条都没加，等于所有聊天都不工作。",
                        fontSize = TextSize.body,
                        color = palette.warn,
                    )
                } else {
                    Spacer(Modifier.height(Space.sm))
                    val whiteList = d.whitelist.sorted()
                if (whitePicking) {
                    PickBar(
                        count = whitePicked.size,
                        total = whiteList.size,
                        palette = palette,
                        primaryLabel = "移出白名单",
                        onPrimary = {
                            val gone = whitePicked
                            saveWhitelist(d.whitelistEnabled, d.whitelist - gone)
                            whitelistNote = "已移出 ${gone.size} 个"
                            whitePicked = emptySet()
                            whitePicking = false
                        },
                        dangerLabel = "删除（不再出现）",
                        onDanger = {
                            val gone = whitePicked
                            saveWhitelist(d.whitelistEnabled, d.whitelist - gone)
                            store.ignoreChats(gone)
                            ignoredCount = store.chatIgnored().size
                            whitelistNote = "已删除 ${gone.size} 个，以后拉取不再出现"
                            whitePicked = emptySet()
                            whitePicking = false
                        },
                        onSelectAll = {
                            whitePicked = if (whitePicked.size == whiteList.size) emptySet() else whiteList.toSet()
                        },
                        onCancel = {
                            whitePicked = emptySet()
                            whitePicking = false
                        },
                    )
                    Spacer(Modifier.height(Space.sm))
                }
                whiteList.forEach { name ->
                    PickRow(
                        label = name,
                        picking = whitePicking,
                        selected = name in whitePicked,
                        palette = palette,
                        actionLabel = "移除",
                        actionColor = palette.bad,
                        onToggle = {
                            whitePicked = if (name in whitePicked) whitePicked - name else whitePicked + name
                        },
                        onLongPress = {
                            whitePicking = true
                            whitePicked = whitePicked + name
                        },
                        onAction = {
                            saveWhitelist(d.whitelistEnabled, d.whitelist - name)
                            whitelistNote = "已移除「$name」"
                        },
                    )
                }
                Text("长按任意一项可多选，然后一次移出 / 删除。", fontSize = TextSize.body, color = palette.sub)
                }
                Spacer(Modifier.height(Space.sm))
                HintText(
                    "名字要和微信里显示的一致（群聊填群名；改过备注的用改过的名字）；懒得敲字就用下面的「拉取会话列表」。",
                    fontSize = TextSize.body,
                    color = palette.sub,
                )
            }

            Spacer(Modifier.height(Space.xl))
            Text("从微信里列出来", fontSize = TextSize.body, fontWeight = FontWeight.Medium, color = palette.text)
            HintText(
                "点一下，然后回微信的首页或通讯录停两秒（滚一下能多收几个），再回来打开下面的「拉取到的联系人」去挑。\n" +
                    "白名单开着的时候，你打开过的那个聊天也会自己进来（认的是聊天页顶上那个名字，也就是备注）。\n" +
                    "⚠ 个人资料页不是来源 —— 那一页只有「微信号 / 地区」这种字段，读出来全是杂项，别在那里等。",
                fontSize = TextSize.body,
                color = palette.sub,
            )
            Spacer(Modifier.height(Space.sm))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = {
                        store.requestChats()
                        whitelistNote = "已请求 · 回微信首页 / 通讯录停两秒，再回来这里看"
                    },
                    modifier = Modifier.height(44.dp),
                    shape = RoundedCornerShape(RadiusR2),
                    colors = ButtonDefaults.buttonColors(containerColor = palette.primary),
                ) { Text("拉取会话列表") }
                Text(pullInfo, fontSize = TextSize.body, color = palette.sub, modifier = Modifier.weight(1f))
            }
            val todo = cands.filter { it !in d.whitelist }
            Spacer(Modifier.height(Space.lg))
            // 名单本身搬到二级页了：拉到几十个名字时铺在卡里会把整页撑得很长，也没法搜。
            // 这里只留一个入口 + 数量。
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(RadiusR2))
                    .background(palette.primary.copy(alpha = ChipBgAlpha))
                    .clickable { onOpenCandidates() }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text("拉取到的联系人（${cands.size}）", fontSize = 14.sp, color = palette.text)
                    Text(
                        when {
                            cands.isEmpty() -> "还没收到候选 —— 点上面的按钮，然后去微信首页 / 通讯录停两秒"
                            todo.isEmpty() -> "拉回来的都加进去了 · 点开可以搜、也可以移出"
                            else -> "还没加进去的 ${todo.size} 个 · 点开可以搜索、批量加入"
                        },
                        fontSize = TextSize.body,
                        color = palette.sub,
                    )
                }
                Text("查看 ›", fontSize = TextSize.body, color = palette.primary)
            }
            if (ignoredCount > 0) {
                Spacer(Modifier.height(Space.xs))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "已忽略 $ignoredCount 个（删掉的杂项）",
                        fontSize = TextSize.body,
                        color = palette.sub,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        "恢复",
                        fontSize = TextSize.body,
                        color = palette.primary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(RadiusR1))
                            .clickable {
                                store.clearIgnored()
                                ignoredCount = 0
                                whitelistNote = "已恢复：删掉的候选下次拉取会重新出现"
                            }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }
            whitelistNote?.let {
                Spacer(Modifier.height(Space.sm))
                Text(it, fontSize = TextSize.body, color = palette.ok)
            }
        }

        GroupTitle("维护与排障")

        SectionCard(d.glassAlpha, "备份 / 迁移") {
            Text(
                "换包名、换手机的时候用：导出成一个 json 文件，装好新的再导入回来。\n" +
                    "导入是合并：角色只覆盖同名的，备份里没有的会保留。",
                fontSize = TextSize.body,
                color = palette.sub,
            )
            Spacer(Modifier.height(Space.md))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("导出时包含 API Key", fontSize = TextSize.body, color = palette.text)
                    Text("开了的话导出文件里有明文 Key，别往公开地方放", fontSize = TextSize.body, color = palette.sub)
                }
                Switch(checked = includeKey, onCheckedChange = { includeKey = it })
            }
            Spacer(Modifier.height(Space.sm))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = { exportLauncher.launch(Backup.FILE_NAME) },
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(RadiusR2),
                    colors = ButtonDefaults.buttonColors(containerColor = palette.primary),
                ) { Text("导出配置") }
                OutlinedButton(
                    onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) },
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(RadiusR2),
                ) { Text("导入配置") }
            }
            backupNote?.let {
                Spacer(Modifier.height(Space.sm))
                Text(it, fontSize = TextSize.body, color = if (it.startsWith("已")) palette.ok else palette.bad)
            }
            Spacer(Modifier.height(Space.lg))
            OutlinedButton(
                onClick = { diagLauncher.launch("TalkTact-诊断包.zip") },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(RadiusR2),
            ) { Text("导出诊断包（.zip）") }
            Spacer(Modifier.height(Space.xs))
            HintText(
                "出问题时用它：环境 / 配置（不含 Key）/ 角色条数 / 抓到的界面结构 / 最近一次调用。\n" +
                    "⚠️ 里面有你和对方的聊天内容，发出去之前先自己看一眼。",
                fontSize = TextSize.body,
                color = palette.sub,
            )
            diagNote?.let {
                Spacer(Modifier.height(Space.sm))
                Text(it, fontSize = TextSize.body, color = if (it.startsWith("已")) palette.ok else palette.bad)
            }
        }

        DiagnosticEntryCard(d, palette, onOpenDiag)
    }
}

// ================= 拉取到的联系人（设置 → 高级设置 → 白名单卡的二级页） =================
