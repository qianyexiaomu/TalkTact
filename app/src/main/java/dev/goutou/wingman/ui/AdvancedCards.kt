package dev.goutou.wingman.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.goutou.wingman.config.ConfigData
import dev.goutou.wingman.config.ConfigStore
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

@Composable
internal fun DiagnosticEntryCard(
    d: ConfigData,
    palette: Palette,
    onOpenDiag: () -> Unit,
) {
    GlassCard(d.glassAlpha) {
        Text("诊断", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = palette.text)
        Text(
            "某个聊天页连按钮都不弹、或者「运行状态」报错的时候用这里：\n" +
                "让微信进程抓一次当前界面、看它真正发出去的那次调用，再复制出来发我。",
            fontSize = 13.sp,
            color = palette.sub,
        )
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = onOpenDiag,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(RadiusR2),
            colors = ButtonDefaults.buttonColors(containerColor = palette.primary),
        ) { Text("进入诊断") }
    }
}

@Composable
internal fun GenerationParamsCard(
    d: ConfigData,
    dirty: Boolean,
    palette: Palette,
    onUpdate: (ConfigData) -> Unit,
    onSave: () -> Unit,
) {
    GlassCard(d.glassAlpha) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GlassField(
                value = d.ctx.toString(),
                onValueChange = { onUpdate(d.copy(ctx = it.toIntOrNull() ?: d.ctx)) },
                modifier = Modifier.weight(1f),
                label = { Text("参考条数 2-20") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
            GlassField(
                value = d.minIntervalSec.toString(),
                onValueChange = { onUpdate(d.copy(minIntervalSec = it.toIntOrNull() ?: d.minIntervalSec)) },
                modifier = Modifier.weight(1f),
                label = { Text("最短间隔(s)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
        }
        Spacer(Modifier.height(8.dp))
        GlassField(
            value = d.temperature.toString(),
            onValueChange = { onUpdate(d.copy(temperature = it.toDoubleOrNull() ?: d.temperature)) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("temperature（0.2 稳 / 0.8 活）") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        )
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("微信内自动分析", fontSize = 15.sp, color = palette.text)
                Text("关掉后只在手动点「识别」时出现卡片", fontSize = 13.sp, color = palette.sub)
            }
            Switch(checked = d.enabled, onCheckedChange = { onUpdate(d.copy(enabled = it)) })
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("敏感内容检查", fontSize = 15.sp, color = palette.text)
                Text("命中验证码/银行卡/转账时先拦一次（推荐开）", fontSize = 13.sp, color = palette.sub)
            }
            Switch(checked = !d.allowSensitive, onCheckedChange = { onUpdate(d.copy(allowSensitive = !it)) })
        }
        Spacer(Modifier.height(14.dp))
        // 这一页只有这一个保存按钮，而接口地址 / 生成模式 / 参考条数 / temperature 全是草稿 ——
        // 不点它就一概不生效。所以红字必须挨着它，别让人以为改完就已经算数了。
        if (dirty) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "未保存，你所做出的改动不会被保存",
                    modifier = Modifier.weight(1f),
                    fontSize = 13.sp,
                    color = palette.bad,
                )
                Spacer(Modifier.width(10.dp))
                Button(
                    onClick = { onSave() },
                    modifier = Modifier.height(46.dp),
                    shape = RoundedCornerShape(RadiusR2),
                    colors = ButtonDefaults.buttonColors(containerColor = palette.primary),
                ) { Text("保存") }
            }
        } else {
            Button(
                onClick = { onSave() },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(RadiusR2),
                colors = ButtonDefaults.buttonColors(containerColor = palette.primary),
            ) { Text("已保存（微信里下次识别即生效）") }
        }
    }
}

@Composable
internal fun ReplyStyleCard(
    d: ConfigData,
    store: ConfigStore,
    palette: Palette,
    saveChatStyles: (Map<String, List<String>>) -> Unit,
    saveCustomStyles: (List<ReplyStyle>, List<String>) -> Unit,
    saveScopedStyles: (String, List<String>) -> Unit,
) {
    GlassCard(d.glassAlpha) {
        Text("回复风格（可多选）", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = palette.text)
        Spacer(Modifier.height(8.dp))

        // ── 生效范围：下面这排药丸改的是谁 ────────────────────────────────────
        // 「按联系人单独设置」的入口就做在这里：不另开一块表单，而是**切换这排药丸的作用域** ——
        // 选「所有人」= 全局那套（老行为），选某个人 = 只改 TA 的。这样只有一套勾选交互要维护，
        // 也不会出现「上面勾的」和「下面表单里勾的」两套状态互相打架。
        //
        // 只在**添加过**的人之间切换（用户要求：自选添加，其余全部默认）。
        // 以前这里会把注入侧回传的候选名一并摊出来（最多 9 个），于是卡片上永远堆着一堆
        // 你根本没打算单独设置的人；现在候选名收在「＋ 添加联系人」后面，点开才列。
        var scope by remember { mutableStateOf("") }
        var adding by remember { mutableStateOf(false) }
        val added = remember(d.chatStyles) { d.chatStyles.keys.sorted() }
        // 选中的那个会话可能刚从表里删掉了 → 落回全局，免得勾选写到一个不存在的会话上
        if (scope.isNotBlank() && scope !in added) scope = ""
        val scoped = scope.isNotBlank()

        Text("生效范围（这排药丸改的是谁）", fontSize = 13.sp, color = palette.sub)
        Spacer(Modifier.height(4.dp))
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            (listOf("") + added).chunked(3).forEach { rowNames ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    rowNames.forEach { name ->
                        GlassPill(
                            // 不在这里切字数：胶囊内部是「单行 + 省略号」，能显示多少由实际宽度决定
                            //（手动切一次再被省略号切一次，会变成「老王…」这种双截断，很怪）。
                            // 全名在下面那行提示里照旧能看到。
                            if (name.isBlank()) "所有人" else name,
                            name == scope,
                            Modifier.weight(1f),
                        ) { scope = name; adding = false }
                    }
                    repeat(3 - rowNames.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }

        // 「添加联系人」：候选名来自注入侧回传（和白名单页同一份），没在微信里打开过的不会出现。
        //
        // 做成**卡内小卡**而不是一行文字按钮：折叠时是「＋ 标题 / 副标题 / 展开」的一整块，
        // 点开才摊开候选名单；名单封顶 168dp、可滚 —— 会话多的时候不会把风格卡撑长。
        // 候选块用 primary 色（和上面那排风格药丸区分开）：一个是「选中态」，一个是「可添加」。
        val addableCandidates = remember(d.chatStyles) {
            store.chatCandidates().map { Roles.normalizeKey(it) }
                .filter { it.isNotBlank() && it !in d.chatStyles.keys }
                .distinct()
                .sorted()
        }
        val addShape = RoundedCornerShape(RadiusR2)
        val chipShape = RoundedCornerShape(RadiusR1)
        Spacer(Modifier.height(6.dp))
        if (!adding) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(addShape)
                    .background(palette.glassTint.copy(alpha = 0.10f))
                    .border(1.dp, palette.glassBorder.copy(alpha = 0.45f), addShape)
                    .clickable { adding = true }
                    .padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AddBadge()
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text("添加联系人", fontSize = 13.sp, color = palette.text)
                    Text("没添加的人一律用「所有人」那套", fontSize = 11.sp, color = palette.sub)
                }
                Text("展开", fontSize = 12.sp, color = palette.sub)
            }
        } else {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(addShape)
                    .background(palette.glassTint.copy(alpha = 0.10f))
                    .border(1.dp, palette.primary.copy(alpha = 0.30f), addShape)
                    .padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 10.dp),
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    AddBadge()
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            "添加联系人",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = palette.text,
                        )
                        Text("点名字就加进来（都是你在微信里打开过的会话）", fontSize = 11.sp, color = palette.sub)
                    }
                    TextButton(
                        onClick = { adding = false },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                    ) { Text("收起", fontSize = 12.sp, color = palette.sub) }
                }
                Spacer(Modifier.height(8.dp))
                if (addableCandidates.isEmpty()) {
                    Text(
                        "还没有可添加的会话名：在微信里打开几个聊天，名字会自己出现在这里。",
                        fontSize = 12.sp,
                        color = palette.sub,
                    )
                } else {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(max = 168.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        // **两列而不是三列**：三列时每颗只有 ~63dp 的文字位（360dp 宽的机器上），
                        // 名字会被省略号切得只剩两三个字；两列能把 8~10 个字的名字整整齐齐显示完。
                        addableCandidates.take(24).chunked(2).forEach { rowNames ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                rowNames.forEach { name ->
                                    Box(
                                        Modifier
                                            .weight(1f)
                                            .clip(chipShape)
                                            .background(palette.primary.copy(alpha = 0.10f))
                                            .border(1.dp, palette.primary.copy(alpha = 0.28f), chipShape)
                                            .clickable {
                                                // 添加 = 先建一条「跟进全局」的记录（见 llm/decodeChatStyles）：
                                                // TA 的条数与风格**跟「所有人」一模一样**，改哪条才分哪条 ——
                                                // 所以刚添加完，TA 的表现和以前没有任何变化。
                                                saveChatStyles(d.chatStyles + (name to emptyList<String>()))
                                                scope = name
                                                adding = false
                                            }
                                            .padding(horizontal = 8.dp, vertical = 7.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            "＋ $name",
                                            fontSize = 12.sp,
                                            color = palette.primary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                                repeat(2 - rowNames.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "加进来之后 TA 先是跟「所有人」完全一样，改哪条才只对 TA 生效。",
                    fontSize = 11.sp,
                    color = palette.sub,
                )
            }
        }

        if (scoped) {
            Spacer(Modifier.height(4.dp))
            val scopedIds = d.chatStyles[scope].orEmpty()
            Text(
                if (scopedIds.isEmpty()) "「$scope」已添加，现在跟着「所有人」走 —— 改一下就只对 TA 生效。"
                else "「$scope」单独设过（${scopedIds.size} 条），下面勾的就是 TA 的。",
                fontSize = 12.sp,
                color = palette.sub,
            )
            TextButton(
                onClick = {
                    // 先把这个范围收回来再删：删完它就不在表里了，留着 scope 会指向一个空范围
                    val gone = scope
                    scope = ""
                    saveChatStyles(d.chatStyles - gone)
                },
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
            ) { Text("删掉 TA 的单独设置（回到所有人）", fontSize = 12.sp, color = palette.bad) }
        }
        Spacer(Modifier.height(8.dp))

        // 「当前会写」也跟着范围走 —— 否则选了某个人、底下那行还在报全局的条数
        val plan = if (scope.isBlank()) d.replyPlan else d.replyPlanFor(scope)
        // 界面上「亮着」的 = 用户**显式勾选**的那几个，**不是** [plan.styles]。
        //
        // [plan.styles] 里掺了「为凑够条数自动补齐」的风格，拿它当选中态一定会出问题：
        // 点一个「被补齐进来、界面上正亮着」的风格 → 从集合里减掉 → 下一轮又被补齐回来
        // → 表现是**点了没反应**（多勾几个时必然踩到）。补齐只留在提示词那一侧（见 ReplyPlan.of），
        // 界面永远只亮真正勾了的那几个。
        //
        // 空 = 还没配过 → 显示默认三样（那正是实际会用的那三种，见 DEFAULT_STYLE_IDS）。
        val globalIds = d.replyStyleIds.ifEmpty { DEFAULT_STYLE_IDS }
        // 当前范围那一套：某位单独设过就用 TA 的，否则（以及选「所有人」时）用全局那套
        val explicitIds = if (scoped) d.chatStyles[scope].orEmpty().ifEmpty { globalIds } else globalIds
        // 现存风格 id（内置 + 自定义）：勾选集里可能残留「加过又删掉」的自定义风格，
        // 不清掉它会白占一个位置（条数就那么几个），而界面上又找不到它。
        val knownIds = REPLY_STYLES.map { it.id }.toSet() + d.replyCustomStyles.map { it.id }
        val chosenIds = explicitIds.filter { it in knownIds }.toSet()

        // 这里**只讲「可以多选」**：条数没有单独的选项了，勾几种就写几条
        //（见 llm/plannedReplyCount）。顺序那个细节挪到卡片底部 —— 放在这儿要换两行。
        Text(
            "可多选（最多 $MAX_REPLIES 种）：勾几种就写几条",
            fontSize = 13.sp,
            color = palette.sub,
        )
        Spacer(Modifier.height(6.dp))
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            REPLY_STYLES.chunked(4).forEach { rowStyles ->
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    rowStyles.forEach { st ->
                        GlassPill(
                            st.label,
                            st.id in chosenIds,
                            Modifier.weight(1f),
                        ) {
                            // 勾选规则全在 `llm/toggledStyleIds` 里（新点的一定留下、位置不够让末尾、
                            // 点掉最后一个 = 保持现状），而且只认**显式勾选**那一份。
                            // 这里**不能**自己算 `chosenIds ± st.id`：那会把自动补齐的风格也当成
                            // 用户勾过的存下去，于是点一个亮着的风格会「减掉又被补回来」，像没反应。
                            // 只改**当前范围**的勾选，不偷偷动别的范围
                            saveScopedStyles(scope, toggledStyleIds(explicitIds, st.id, MAX_REPLIES, knownIds))
                        }
                    }
                    // 一行放不满时用空 Spacer 补齐权重，避免最后几个被拉得特别宽
                    repeat(4 - rowStyles.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }

        // ── 自定义风格：自己敲一个风格名加进来 ──────────────────────────────────
        // 加进来的风格和内置 8 种**完全同等**（能勾选、能写进提示词、能参与补齐），
        // 不是「只给候选贴个标签」—— 所以它必须真的被算进 ReplyPlan，见 llm/ReplyPlan.kt。
        Spacer(Modifier.height(10.dp))
        var newStyleName by remember { mutableStateOf("") }
        var newStyleHint by remember { mutableStateOf("") }
        var styleNote by remember { mutableStateOf("") }

        Text("自定义风格（加完直接选中，和上面同等）", fontSize = 13.sp, color = palette.sub)
        Spacer(Modifier.height(4.dp))

        if (d.replyCustomStyles.isNotEmpty()) {
            // 已经加过的清单：行距压到 2dp（它们是列表项，不必每个都占满一行高度）
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                d.replyCustomStyles.forEach { st ->
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        GlassPill(st.displayLabel, st.id in chosenIds, Modifier.weight(1f)) {
                            // 只改**当前范围**的勾选，不偷偷动别的范围
                            saveScopedStyles(scope, toggledStyleIds(explicitIds, st.id, MAX_REPLIES, knownIds))
                        }
                        TextButton(
                            onClick = {
                                // 删掉它，连同它的勾选一起清 —— 只从列表里删、把 id 留在
                                // 勾选集里的话，它会一直占着一个位置（条数就那么几个），
                                // 而界面上又找不到它，表现是「条数明明给了 3 条却只出来 2 条」。
                                val rest = d.replyCustomStyles.filter { it.id != st.id }
                                // 这条风格可能被**任何范围**勾着（全局 + 每个「按联系人」）——
                                // 只清全局的话，它会在别的范围里以「认不出来的 id」留着，
                                // 而条数就是从勾选数派生的（见 llm/plannedReplyCount），白占一个位置。
                                val cleaned = d.chatStyles
                                    .mapValues { (_, ids) -> ids.filter { it != st.id } }
                                    .filterValues { it.isNotEmpty() }
                                saveCustomStyles(rest, globalIds.filter { it != st.id })
                                saveChatStyles(cleaned)
                            },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                        ) { Text("删除", fontSize = 12.sp, color = palette.bad) }
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
        }

        // 名字输入和按钮挤在**同一行**：以前是「两个输入框 + 一个整行按钮」三行，
        // 而这里平时只用得上第一格 —— 那块高度全被白占掉了。
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            GlassField(
                value = newStyleName,
                // 先在输入处就按字数上限拦住，免得粘一大段进来把药丸撑破
                onValueChange = { if (it.length <= MAX_CUSTOM_LABEL_LEN) newStyleName = it },
                modifier = Modifier.weight(1f),
                singleLine = true,
                label = { Text("风格名（比如：撒娇）", fontSize = 12.sp) },
            )
            Spacer(Modifier.width(6.dp))
            Button(
                onClick = {
                    val st = customStyle(newStyleName, newStyleHint)
                    styleNote = when {
                        st == null -> "风格名不能为空"
                        // 提示语用 displayLabel（短的那个）：全名可以有 16 个字，
                        // 塞进这行提示里会把它撑成两行。
                        d.replyCustomStyles.any { it.id == st.id } -> "「${st.displayLabel}」已经加过了"
                        d.replyCustomStyles.size >= MAX_CUSTOM_STYLES -> "最多加 $MAX_CUSTOM_STYLES 个"
                        else -> {
                            // 加完**直接选中，并且排在勾选集最前**：条数只有 n 个位置，
                            // 加了不选中、或者被末尾挤掉，用户看到的就是「加了没用」。
                            val list = d.replyCustomStyles + st
                            val next = (listOf(st.id) + explicitIds).distinct().take(MAX_REPLIES)
                            // 只动**当前范围**的勾选：选着某个人时，加一条新风格不该把
                            // 全局那套（别人用的）也改掉。
                            saveCustomStyles(list, if (scoped) globalIds else next)
                            if (scoped) saveChatStyles(d.chatStyles + (scope to next))
                            newStyleName = ""
                            newStyleHint = ""
                            "已加「${st.displayLabel}」并选中"
                        }
                    }
                },
                shape = RoundedCornerShape(RadiusR2),
                contentPadding = PaddingValues(horizontal = 16.dp),
            ) { Text("添加风格") }
        }
        Spacer(Modifier.height(4.dp))
        GlassField(
            value = newStyleHint,
            onValueChange = { newStyleHint = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("说明（可选，给模型看：比如用撒娇的语气带点抱怨）", fontSize = 12.sp) },
        )
        if (styleNote.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text(styleNote, fontSize = 13.sp, color = palette.sub)
        }

        Spacer(Modifier.height(10.dp))

        HintText(
            (if (scoped) "「$scope」单独用：" else "") +
                "当前会写：${plan.summary} —— 勾选顺序就是第 1/2/3 条的顺序。" +
                "这段会写进提示词，让模型按你选的类型各写一条 —— " +
                "所以「风格」不是给候选贴标签，是真的在要求模型换着写。",
            fontSize = 13.sp,
            color = palette.sub,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "点一下就生效（立刻落盘，不用等页底那个「保存」）。勾得越多每条会被摊薄、越容易" +
                "雷同，模型输出也更容易顶到长度上限（本模块会按条数把输出预算抬到够用）；" +
                "2~6 条通常最实用。",
            fontSize = 13.sp,
            color = palette.ok,
        )
        Spacer(Modifier.height(6.dp))
        // 用户要求「提醒用户重启微信就好」，随后又把措辞改成**强行停止**：
        // 从最近任务里划掉往往杀不掉微信进程，只有系统的「强行停止」才真的把它按下去。
        // 卡片上只留这一句行动指引，不解释原理（原理写在 CHANGELOG 与项目笔记里）。
        Text(
            "⚠ 选完记得强行停止微信，再重新打开",
            fontSize = 13.sp,
            color = palette.warn,
        )
    }
}
