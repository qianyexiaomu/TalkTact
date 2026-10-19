package dev.goutou.wingman.ui

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.goutou.wingman.config.ConfigData
import dev.goutou.wingman.config.ConfigStore
import dev.goutou.wingman.wechat.filterNames
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

/**
 * 设置 → 高级设置 → 「拉取到的联系人」。
 *
 * 从微信读回来的会话名（候选）以前直接铺在白名单卡里：一次只列 24 条、也没法搜 ——
 * 拉回来的名字一多，那一块就成了整页最长的一段。所以搬到单独一层：顶上一个搜索框，下面是完整名单。
 *
 * 名单**不因为「加入」而消失**：加进去的那条挪到下面的「已经在白名单里」那一段。
 * 「微信里到底读到了谁」始终是一份看得全的名单，比「加一个少一个」好找。
 */
@Composable
fun ChatCandidatesScreen(
    store: ConfigStore,
    ui: ConfigData,
    onSaved: () -> Unit,
    onBack: () -> Unit,
) {
    val palette = LocalPalette.current
    var d by remember { mutableStateOf(ui) }
    var note by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    var picking by remember { mutableStateOf(false) }
    var picked by remember { mutableStateOf(emptySet<String>()) }
    var ignoredCount by remember { mutableStateOf(store.chatIgnored().size) }

    // 候选是注入侧用广播回传的，这一页不会自己重组 —— 停在这里时每 1.5 秒自己看一眼。
    // 轮询**有上限**（约 5 分钟），理由和白名单卡那边一样：别留一个永远挂着的定时任务，
    // 也让渲染回归测试不会被一个无限协程挂住。
    val resumed = resumedTick()
    val pulled by produceState(
        initialValue = store.chatCandidates() to store.chatPullInfo(),
        key1 = resumed,
    ) {
        repeat(200) {
            kotlinx.coroutines.delay(1500)
            value = store.chatCandidates() to store.chatPullInfo()
        }
    }
    val cands = pulled.first
    val reqAt = store.chatRequestAt()
    val pullInfo = pulled.second.let { (info, at) ->
        when {
            at > reqAt -> info.ifBlank { "已收到回传" } + " · " + clockText(at)
            reqAt > 0L -> "已请求 ${clockText(reqAt)} · 微信侧还没回过话"
            else -> "还没拉过"
        }
    }

    fun saveWhitelist(enabled: Boolean, chats: Set<String>) {
        store.saveWhitelist(enabled, chats)
        d = store.load()
        // App 层的 ui 也要跟着刷新，否则回上一页看到的是旧名单（再点一次保存还会把它写回去）
        onSaved()
    }

    val all = cands.sorted()
    val todo = all.filter { it !in d.whitelist }
    val added = all.filter { it in d.whitelist }
    val hitTodo = filterNames(todo, query)
    val hitAdded = filterNames(added, query)

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 120.dp)) {
        item {
            ScreenHeader("拉取到的联系人", "微信里读回来的名单 · 点一下加入白名单") {
                HeaderButton("← 返回", onBack)
            }
        }

        item {
            GlassCard(d.glassAlpha) {
                Text("搜索", fontSize = TextSize.cardTitle, fontWeight = FontWeight.SemiBold, color = palette.text)
                Text(
                    "拉回来的名字可能有几十个 —— 输一两个字就能筛出来（只筛这一页的名单）。",
                    fontSize = TextSize.body,
                    color = palette.sub,
                )
                Spacer(Modifier.height(Space.md))
                GlassField(
                    value = query,
                    onValueChange = {
                        query = it
                        // 边筛边选容易错位：一改搜索词就退出多选，重新选
                        if (picking) {
                            picking = false
                            picked = emptySet()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("搜索名字") },
                    singleLine = true,
                )
                if (!d.whitelistEnabled) {
                    Spacer(Modifier.height(Space.lg))
                    WhiteCard {
                        Text(
                            "白名单现在是关着的",
                            fontSize = TextSize.body,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFFB3261E),
                        )
                        Text(
                            "这里加进去的名字，在开关打开之前都不会生效 —— " +
                                "回上一页把「只对白名单里的聊天生效」打开。",
                            fontSize = TextSize.body,
                            color = Color(0xFF55525E),
                            modifier = Modifier.padding(top = 3.dp),
                        )
                    }
                }
                Spacer(Modifier.height(Space.lg))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = {
                            store.requestChats()
                            note = "已请求 · 回微信首页 / 通讯录停两秒，再回来看"
                        },
                        modifier = Modifier.height(44.dp),
                        shape = RoundedCornerShape(RadiusR2),
                        colors = ButtonDefaults.buttonColors(containerColor = palette.primary),
                    ) { Text("拉取会话列表") }
                    Text(pullInfo, fontSize = TextSize.body, color = palette.sub, modifier = Modifier.weight(1f))
                }
                note?.let {
                    Spacer(Modifier.height(Space.sm))
                    Text(it, fontSize = TextSize.body, color = palette.ok)
                }
            }
        }

        if (picking) {
            item {
                GlassCard(d.glassAlpha, border = palette.primary.copy(alpha = CardBorderAlpha)) {
                    PickBar(
                        count = picked.size,
                        total = hitTodo.size,
                        palette = palette,
                        primaryLabel = "加入白名单",
                        onPrimary = {
                            val add = picked
                            saveWhitelist(d.whitelistEnabled, d.whitelist + add)
                            store.unignoreChats(add)
                            ignoredCount = store.chatIgnored().size
                            note = "已加入 ${add.size} 个"
                            picked = emptySet()
                            picking = false
                        },
                        dangerLabel = "删除（不再出现）",
                        onDanger = {
                            val gone = picked
                            store.ignoreChats(gone)
                            ignoredCount = store.chatIgnored().size
                            note = "已删除 ${gone.size} 个杂项 —— 以后拉取不会再冒出来"
                            picked = emptySet()
                            picking = false
                        },
                        onSelectAll = {
                            picked = if (picked.size == hitTodo.size) emptySet() else hitTodo.toSet()
                        },
                        onCancel = {
                            picked = emptySet()
                            picking = false
                        },
                    )
                }
            }
        }

        item {
            Text(
                "还没加进去的（${todo.size}）" + if (query.isNotBlank()) " · 筛出 ${hitTodo.size} 个" else "",
                fontSize = TextSize.body,
                color = palette.sub,
                modifier = Modifier.padding(start = 30.dp, end = 30.dp, top = 10.dp, bottom = 4.dp),
            )
        }

        if (hitTodo.isEmpty()) {
            item {
                GlassCard(d.glassAlpha) {
                    Text(
                        when {
                            all.isEmpty() -> "这一页还是空的 —— 点上面的「拉取会话列表」，然后回微信首页 / 通讯录停两秒。"
                            query.isNotBlank() -> "没搜到「${query.trim()}」—— 换个字试试，或者清空搜索框。"
                            else -> "拉回来的 ${all.size} 个都已经加进白名单了。"
                        },
                        fontSize = TextSize.body,
                        color = palette.sub,
                    )
                }
            }
        }

        items(hitTodo) { name ->
            Box(Modifier.padding(horizontal = 30.dp)) {
                PickRow(
                    label = name,
                    picking = picking,
                    selected = name in picked,
                    palette = palette,
                    actionLabel = "加入",
                    actionColor = palette.primary,
                    onToggle = { picked = if (name in picked) picked - name else picked + name },
                    onLongPress = {
                        picking = true
                        picked = picked + name
                    },
                    onAction = {
                        saveWhitelist(d.whitelistEnabled, d.whitelist + name)
                        store.unignoreChats(setOf(name))
                        ignoredCount = store.chatIgnored().size
                        note = "已加入「$name」"
                    },
                )
            }
        }

        if (hitAdded.isNotEmpty()) {
            item {
                Text(
                    "已经在白名单里的（${added.size}）" + if (query.isNotBlank()) " · 筛出 ${hitAdded.size} 个" else "",
                    fontSize = TextSize.body,
                    color = palette.sub,
                    modifier = Modifier.padding(start = 30.dp, end = 30.dp, top = 14.dp, bottom = 4.dp),
                )
            }
            items(hitAdded) { name ->
                Box(Modifier.padding(horizontal = 30.dp)) {
                    PickRow(
                        label = name,
                        picking = false,
                        selected = false,
                        palette = palette,
                        actionLabel = "移出",
                        actionColor = palette.bad,
                        onToggle = {},
                        onLongPress = {},
                        onAction = {
                            saveWhitelist(d.whitelistEnabled, d.whitelist - name)
                            note = "已移出「$name」"
                        },
                    )
                }
            }
        }

        item {
            Column(Modifier.padding(horizontal = 30.dp, vertical = 10.dp)) {
                Text(
                    "长按任意一项可多选，然后一次加入或删除。删除的杂项会被记下来，下次拉取不再出现。",
                    fontSize = TextSize.body,
                    color = palette.sub,
                )
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
                                    note = "已恢复：删掉的候选下次拉取会重新出现"
                                }
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                }
            }
        }
    }
}

// ================= 诊断（设置 → 高级设置 → 诊断） =================
