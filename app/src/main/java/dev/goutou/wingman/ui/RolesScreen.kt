package dev.goutou.wingman.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.goutou.wingman.config.ConfigStore
import dev.goutou.wingman.config.Role
import dev.goutou.wingman.config.Roles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import dev.goutou.wingman.config.SELF_ROLE_KEY
import dev.goutou.wingman.llm.StyleSkill
import dev.goutou.wingman.style.SelfStyle
import kotlin.math.roundToInt
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

/**
 * 角色页。
 *
 * 列表里每个人 = 一个微信会话名。数据全自动来：模块在你打开某个聊天页时，把它读到的消息
 * 按会话名归档回来（1 小时内重复只留一条）。点进某人可以写「TA 是你什么人」和「平时的关系」，
 * 这两样 + 之前攒下的聊天记录会一起拼进提示词，直接影响当前 skill 生成出来的回复。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RolesScreen(store: ConfigStore, glassAlpha: Float, open: String?, onOpen: (String?) -> Unit) {
    val palette = LocalPalette.current
    var tick by remember { mutableStateOf(0) }
    val roles = remember(tick) { store.roles() }
    var pendingDelete by remember { mutableStateOf<String?>(null) }
    val current = open?.let { k -> roles.firstOrNull { it.key == k } }

    if (open != null && current != null) {
        RoleDetail(
            store = store,
            role = current,
            glassAlpha = glassAlpha,
            onBack = {
                onOpen(null)
                tick++
            },
            onRename = { newName ->
                store.renameRole(current.key, newName)
                tick++
            },
            onChanged = { tick++ },
        )
        return
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 120.dp)) {
        ScreenHeader("角色", "每个人一份档案，直接影响生成") {
            HeaderButton("↻ 刷新") { tick++ }
        }

        GlassCard(glassAlpha) {
            Text("这是干什么的", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = palette.text)
            Text(
                "模块会在你打开某个聊天页时，把读到的消息按联系人归档到这里（1 小时内重复的内容只留一条）。\n" +
                    "点进某个人，写上「TA 是你什么人」和「平时的关系」—— 这些会连同之前攒下的聊天记录一起，\n" +
                    "拼进提示词，直接影响「军师」生成出来的回复。\n" +
                    "长按某一项可以直接删除它；点进去可以改名字，\n" +
                        "也能「把另一个角色合并进来」—— 同一个人被记成两条时用那个。",
                fontSize = 13.sp,
                color = palette.sub,
            )
        }

        if (roles.isEmpty()) {
            GlassCard(glassAlpha) {
                Text("还没有记录", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = palette.text)
                Text(
                    "去微信里打开几个聊天页，每个停两三秒，再回来点「刷新」。\n" +
                        "（只在聊天页可见时读得到，所以记录是「你在场时看到的那几条」慢慢攒起来的。）",
                    fontSize = 13.sp,
                    color = palette.sub,
                )
            }
        }

        roles.forEach { role ->
            val self = role.key == SELF_ROLE_KEY
            GlassCard(
                glassAlpha,
                // 批 4c：卡片描边只表达「本人 = 绿」这一件事（原来「没写关系」也染一道紫，
                // 紫色当成了「正常」用，跟状态语义打架）。关系没写靠下面那行琥珀文字说。
                border = if (self) palette.okMark.copy(alpha = CardBorderAlpha) else null,
            ) {
                // 卡面差异化（设计师 P1）：「本人」不是「某个联系人」—— 它管的是我自己的说话风格，
                // 是系统条目。给它一条**渐变竖条** + 一枚「系统」chip，一眼就和下面的联系人区分开。
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    if (self) {
                        Box(
                            Modifier
                                .width(4.dp)
                                .height(52.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(Brush.verticalGradient(listOf(palette.primary, palette.okMark))),
                        )
                        Spacer(Modifier.width(12.dp))
                    }
                    Column(
                        Modifier.weight(1f).combinedClickable(
                            onClick = { onOpen(role.key) },
                            // 「本人」不给删：它是常驻条目，删了也会立刻回来（见 Roles.withSelf）
                            onLongClick = { if (!self) pendingDelete = role.key },
                        ),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (self) {
                                StatusDot(palette.okMark)
                            }
                            Text(
                                role.name,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (self) palette.ok else palette.text,
                                modifier = Modifier.padding(start = if (self) 6.dp else 0.dp).weight(1f, fill = false),
                            )
                            if (self) {
                                Spacer(Modifier.width(8.dp))
                                StatusChip("系统", palette.okMark)
                            }
                            if (role.renamed) {
                                Text(
                                    " ◦ 识别名 ${role.key.take(10)}",
                                    fontSize = 10.sp,
                                    color = palette.warn,
                                    modifier = Modifier.weight(1f),
                                )
                            } else {
                                Spacer(Modifier.weight(1f))
                            }
                            Text("${role.msgs.size} 条", fontSize = 13.sp, color = palette.sub)
                        }
                        Text(
                            when {
                                self && store.selfStyleEnabled() && store.selfSkill().isNotBlank() ->
                                    "说话风格 skill：已开启 · ${formatTime(store.selfSkillAt())} 生成"
                                self && store.selfStyleEnabled() -> "说话风格 skill：已开启（还没生成）"
                                self -> "说话风格 skill：未开启（点进去打开）"
                                role.relation.isBlank() -> "还没写 TA 是你什么人"
                                else -> "${role.relation}${if (role.note.isBlank()) "" else " · ${role.note.take(18)}"}"
                            },
                            fontSize = 13.sp,
                            color = when {
                                self -> palette.ok
                                role.relation.isBlank() -> palette.warn
                                // 批 4c：紫色只表示「选中 / 主操作」，不承担「正常」—— 普通档案用次要文字色
                                else -> palette.sub
                            },
                        )
                        Text(
                            "最近一条：${if (role.lastAt > 0) formatTime(role.lastAt) else "—"}",
                            fontSize = 13.sp,
                            color = palette.sub,
                        )
                    }
                }
            }
        }
    }

    pendingDelete?.let { key ->
        val shown = roles.firstOrNull { it.key == key }?.name ?: key
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除角色「$shown」？") },
            text = { Text("档案和记录一起删掉，无法恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    store.removeRole(key)
                    pendingDelete = null
                    tick++
                }) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("取消") } },
        )
    }
}

@Composable
internal fun RoleDetail(
    store: ConfigStore,
    role: Role,
    glassAlpha: Float,
    onBack: () -> Unit,
    onRename: (String) -> Unit,
    onChanged: () -> Unit,
) {
    // 「本人」这条没有「TA 是你什么人」，它管的是另一件事（我的说话风格），单独一页
    if (role.key == SELF_ROLE_KEY) {
        SelfStyleDetail(store, glassAlpha)
        return
    }
    val palette = LocalPalette.current
    var relation by remember(role.key) { mutableStateOf(role.relation) }
    var note by remember(role.key) { mutableStateOf(role.note) }
    // 跟「外观」「高级设置」「军师」同一套判据：草稿和上次落盘的那份比。
    // 用本地快照而不是拿 role 再比一次 —— 上面刷新列表的时机不确定，靠 role 会出现「保存完红字还亮着」。
    var persisted by remember(role.key) { mutableStateOf(role.relation to role.note) }
    val dirty = (relation to note) != persisted
    var confirm by remember { mutableStateOf<String?>(null) }
    var renameOpen by remember { mutableStateOf(false) }
    var draft by remember(role.key) { mutableStateOf(role.name) }
    // 合并：先选「把谁并进来」，再确认一次（被并掉的那个角色会消失，不可撤销）
    var mergeOpen by remember { mutableStateOf(false) }
    var pendingMerge by remember { mutableStateOf<Role?>(null) }
    var mergedNote by remember(role.key) { mutableStateOf<String?>(null) }
    var listTick by remember { mutableStateOf(0) }
    // 候选：除自己以外的普通角色。「本人」不参与合并 —— 它装的是「我自己说过的话」，
    // 混进别人的话会直接污染说话风格 skill
    val mergeCandidates = remember(role.key, listTick) {
        store.roles().filterNot { it.key == role.key || it.key == SELF_ROLE_KEY }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 120.dp)) {
        ScreenHeader(role.name, "档案 · ${role.msgs.size} 条记录") {
            TextButton(onClick = { renameOpen = true }) { Text("改名字") }
            Spacer(Modifier.width(8.dp))
            HeaderButton("← 返回", onBack)
        }

        GlassCard(glassAlpha) {
            Text("TA 是你什么人", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = palette.text)
            if (role.renamed) {
                Text(
                    "（识别到的会话名是「${role.key}」，它负责匹配、不会被改动，所以改了名字以后消息还是记到这一条）",
                    fontSize = 13.sp,
                    color = palette.sub,
                )
            }
            RELATIONS.chunked(4).forEach { row ->
                Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    row.forEach { r ->
                        GlassPill(r, relation == r, Modifier.weight(1f)) {
                            relation = if (relation == r) "" else r
                        }
                    }
                    repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                colors = glassFieldColors(),
                shape = RoundedCornerShape(RadiusR2),
                value = note,
                onValueChange = { note = it },
                modifier = Modifier.fillMaxWidth().heightIn(min = 110.dp),
                label = { Text("平时的关系（越具体越有用）") },
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "例：同一个组的后端，说话直接，最近在催我 review；上次帮他带过饭。",
                fontSize = 13.sp,
                color = palette.sub,
            )
            Spacer(Modifier.height(10.dp))
            if (dirty) {
                Text("未保存，你所做出的改动不会被保存", fontSize = 13.sp, color = palette.bad)
                Spacer(Modifier.height(6.dp))
            }
            Button(
                onClick = {
                    store.setRoleProfile(role.key, relation, note)
                    persisted = relation to note
                    // 顺手刷新外层列表：原来这里没通知，保存完返回还显示旧档案
                    onChanged()
                },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(RadiusR2),
                colors = ButtonDefaults.buttonColors(containerColor = palette.primary),
            ) { Text(if (dirty) "保存" else "已保存") }
        }

        GlassCard(glassAlpha) {
            Text("记下来的聊天（${role.msgs.size} 条）", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = palette.text)
            Text(
                "时间是我「看到」它的时间，不是微信里那条消息的真实时间 —— 微信不给这条信息，" +
                    "而模块只在聊天页可见时读得到。生成回复时会带上最近 20 条（屏幕上已有的不再重复）。",
                fontSize = 13.sp,
                color = palette.sub,
            )
            Spacer(Modifier.height(6.dp))
            if (role.msgs.isEmpty()) {
                Text("（还没有）", fontSize = 13.sp, color = palette.sub)
            } else {
                var lastShown = 0L
                role.msgs.takeLast(60).forEach { m ->
                    if (m.at - lastShown > 10 * 60_000L) {
                        lastShown = m.at
                        Text(formatTime(m.at), fontSize = 10.sp, color = palette.sub, modifier = Modifier.padding(top = 6.dp))
                    }
                    Text(
                        "${if (m.fromMe) "我" else "对方"}：${m.text}",
                        fontSize = 13.sp,
                        color = if (m.fromMe) palette.sub else palette.text,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = { mergeOpen = true },
                enabled = mergeCandidates.isNotEmpty(),
                modifier = Modifier.fillMaxWidth().height(46.dp),
                shape = RoundedCornerShape(RadiusR2),
            ) { Text("把另一个角色合并进来") }
            Spacer(Modifier.height(4.dp))
            Text(
                if (mergeCandidates.isEmpty()) {
                    "还没有别的角色可以合并。"
                } else {
                    "同一个人被记成两条时用这个：选一个角色，把它的记录并进这一条（按时间排好）。"
                },
                fontSize = 13.sp,
                color = palette.sub,
            )
            mergedNote?.let {
                Spacer(Modifier.height(6.dp))
                Text(it, fontSize = 13.sp, color = palette.ok)
            }
        }

        // 危险操作独立成一张卡（设计师 P1）：它们原来挤在「记下来的聊天」卡底部、
        // 和「合并进来」只隔一行，而这两个都**不可撤销** —— 误触的代价太大，不该混在浏览区里。
        GlassCard(glassAlpha, border = palette.badMark.copy(alpha = CardBorderAlpha)) {
            Text("危险操作", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = palette.bad)
            Text(
                "下面两个都不能撤销：清空只删记下来的聊天（改名与档案保留）；" +
                    "删除会把这条角色整个删掉（记录 + 档案一起）。",
                fontSize = 13.sp,
                color = palette.sub,
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = { confirm = "clear" },
                    enabled = role.msgs.isNotEmpty(),
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(RadiusR2),
                ) { Text("清空记录") }
                OutlinedButton(
                    onClick = { confirm = "remove" },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(RadiusR2),
                ) { Text("删除角色") }
            }
        }
    }

    if (renameOpen) {
        AlertDialog(
            onDismissRequest = { renameOpen = false },
            title = { Text("给这个角色改个名字") },
            text = {
                Column {
                    OutlinedTextField(
                        colors = glassFieldColors(),
                        shape = RoundedCornerShape(RadiusR2),
                        value = draft,
                        onValueChange = { draft = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("显示名字") },
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "这里只改显示。识别到的会话名「${role.key}」不变 —— 所以改完名字，" +
                            "以后这个会话的消息还是记到同一条上，不会分家。",
                        fontSize = 13.sp,
                        color = palette.sub,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    onRename(draft)
                    renameOpen = false
                }) { Text("保存") }
            },
            dismissButton = { TextButton(onClick = { renameOpen = false }) { Text("取消") } },
        )
    }

    if (mergeOpen) {
        AlertDialog(
            onDismissRequest = { mergeOpen = false },
            title = { Text("把哪个角色合并进来？") },
            text = {
                Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
                    Text(
                        "合并后：两边记录按时间排好留在「${role.name}」，被并的那个角色会消失。",
                        fontSize = 13.sp,
                        color = palette.sub,
                    )
                    Spacer(Modifier.height(8.dp))
                    mergeCandidates.forEach { o ->
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp)
                                .clip(RoundedCornerShape(RadiusR2))
                                .clickable {
                                    mergeOpen = false
                                    pendingMerge = o
                                }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                        ) {
                            Text(o.name, fontSize = 14.sp, color = palette.text)
                            Text(
                                "${o.msgs.size} 条记录" +
                                    if (o.relation.isNotBlank()) " · ${o.relation}" else "",
                                fontSize = 13.sp,
                                color = palette.sub,
                            )
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { mergeOpen = false }) { Text("取消") } },
        )
    }

    pendingMerge?.let { src ->
        AlertDialog(
            onDismissRequest = { pendingMerge = null },
            title = { Text("把「${src.name}」合并进来？") },
            text = {
                Text(
                    "「${src.name}」的 ${src.msgs.size} 条记录会并进「${role.name}」，按时间重新排好；" +
                        "合并完「${src.name}」这个角色就没了，无法撤销。\n\n" +
                        if (role.relation.isBlank() && (src.relation.isNotBlank() || src.note.isNotBlank())) {
                            "这边的「TA 是你什么人 / 平时的关系」还空着，会用它的补上。"
                        } else {
                            "「TA 是你什么人 / 平时的关系」保留这边的。"
                        },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    store.mergeRoles(src.key, role.key)
                    pendingMerge = null
                    listTick++
                    mergedNote = "已把「${src.name}」并进来"
                    onChanged()
                }) { Text("合并") }
            },
            dismissButton = { TextButton(onClick = { pendingMerge = null }) { Text("取消") } },
        )
    }

    confirm?.let { what ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text(if (what == "clear") "清空聊天记录？" else "删除这个角色？") },
            text = {
                Text(
                    if (what == "clear") "只清掉记录，「TA 是你什么人 / 平时的关系」会保留。"
                    else "档案和记录一起删掉，无法恢复。",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (what == "clear") store.clearRoleMsgs(role.key) else store.removeRole(role.key)
                    confirm = null
                    onBack()
                }) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("取消") } },
        )
    }
}

/**
 * 「本人」这条的详情页。
 *
 * 和其它角色最大的不同：这里没有「TA 是你什么人 / 平时的关系」，只有一个开关 +
 * 现在的状态 + 手动生成。因为这条线的产出不是"关系描述"，而是一份**说话风格档案**。
 */
@Composable
internal fun SelfStyleDetail(store: ConfigStore, glassAlpha: Float) {
    val palette = LocalPalette.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var tick by remember { mutableStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf<String?>(null) }
    val on = remember(tick) { store.selfStyleEnabled() }
    var hour by remember(tick) { mutableStateOf(store.selfStyleHour()) }
    val samples = remember(tick) { StyleSkill.count(store.selfSamples()) }
    val skill = remember(tick) { store.selfSkill() }
    val skillAt = remember(tick) { store.selfSkillAt() }

    LazyColumn(Modifier.fillMaxSize()) {
        item { ScreenHeader("本人", "我的说话风格") }

        item {
            GlassCard(glassAlpha) {
                Text("这是干什么的", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = palette.text)
                Text(
                    "把「你自己发出去的话」攒起来，每天在你设定的时间交给模型提炼成一份说话风格档案。\n" +
                        "「军师」生成候选回复时会参考它 —— 回复就会更像你平时说话的样子。\n\n" +
                        "关掉之后：不再采集、不再生成，也不再使用（已经生成的那份留着，重新打开立刻可用）；\n" +
                        "已经攒下的原始记录会被清掉。\n" +
                        "样本只在你打开聊天页时才读得到，所以是「你在场时看到的那几句」慢慢攒起来的。",
                    fontSize = 13.sp,
                    color = palette.sub,
                )
            }
        }

        item {
            GlassCard(glassAlpha, border = if (on) palette.okMark.copy(alpha = CardBorderAlpha) else null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "把我的说话风格做成 skill",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            color = palette.text,
                        )
                        Text(
                            if (on) "已开启：会采集我说的话" else "未开启：不采集、不生成、不存储",
                            fontSize = 13.sp,
                            color = if (on) palette.ok else palette.sub,
                        )
                    }
                    GlassPill(if (on) "已开启" else "开启", selected = on) {
                        val next = !on
                        store.setSelfStyleEnabled(next)
                        // 开关和定时任务是绑在一起的：开了才排任务，关了立刻撤掉
                        if (next) SelfStyle.schedule(context, hour) else SelfStyle.cancel(context)
                        msg = null
                        tick++
                    }
                }
            }
        }

        item {
            GlassCard(glassAlpha) {
                Text(
                    "每天什么时候生成",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = palette.text,
                )
                Text(
                    "现在是 ${"$hour".padStart(2, '0')}:00 —— 0 点到 23 点里挑都行。" +
                        if (on) "改完立刻生效（按新的时间重新排）。" else "打开上面的开关后按这个时间跑。",
                    fontSize = 13.sp,
                    color = palette.sub,
                )
                Slider(
                    value = hour.toFloat(),
                    onValueChange = {
                        val h = it.roundToInt().coerceIn(0, 23)
                        if (h != hour) {
                            hour = h
                            store.setSelfStyleHour(h)
                        }
                    },
                    // 改完再重排：拖动过程中每变一格都去动 WorkManager 太浪费
                    onValueChangeFinished = { if (on) SelfStyle.schedule(context, hour) },
                    valueRange = 0f..23f,
                    steps = 22,
                )
            }
        }

        item {
            GlassCard(glassAlpha) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatCell("$samples", "条样本", Modifier.weight(1f))
                    StatCell(if (skillAt > 0) formatTime(skillAt) else "—", "上次生成", Modifier.weight(1f))
                }
                if (skill.isBlank()) {
                    HintText(
                        "还没有生成过。攒够 ${StyleSkill.MIN_SAMPLES} 条样本之后，可以点下面手动生成一次（不用等到中午）。",
                        fontSize = 13.sp,
                        color = palette.sub,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                } else {
                    Text(
                        "当前 skill（生成回复时会带上）",
                        fontSize = 13.sp,
                        color = palette.sub,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                    Text(
                        skill,
                        fontSize = 13.sp,
                        color = palette.text,
                        modifier = Modifier
                            .padding(top = 4.dp)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(RadiusR2))
                            .background(palette.glassTint.copy(alpha = 0.10f))
                            .padding(10.dp),
                    )
                }
                msg?.let {
                    Text(it, fontSize = 13.sp, color = palette.primary, modifier = Modifier.padding(top = 8.dp))
                }
                Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GlassPill(if (busy) "生成中…" else "立即生成", selected = false) {
                        if (busy) return@GlassPill
                        busy = true
                        msg = null
                        scope.launch {
                            val r = withContext(Dispatchers.IO) {
                                runCatching { SelfStyle.generateNow(store) }
                            }
                            busy = false
                            msg = r.fold({ it }, { it.message ?: "生成失败" })
                            tick++
                        }
                    }
                    if (samples > 0) {
                        GlassPill("清空样本", selected = false) {
                            store.clearRoleMsgs(SELF_ROLE_KEY)
                            msg = "已清空采集到的样本"
                            tick++
                        }
                    }
                }
            }
        }

        // 底部导航是浮在上面的，留出空位免得被它挡住
        item { Spacer(Modifier.height(110.dp)) }
    }
}
