package dev.goutou.wingman.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.goutou.wingman.config.ConfigStore
import dev.goutou.wingman.config.replyPlan
import dev.goutou.wingman.llm.LlmClient
import dev.goutou.wingman.llm.Graded
import dev.goutou.wingman.llm.REWRITE_PRESETS
import dev.goutou.wingman.llm.Suggestion
import dev.goutou.wingman.llm.gradedWaitMs
import dev.goutou.wingman.llm.isReplyTooLong
import dev.goutou.wingman.wechat.ChatMsg
import dev.goutou.wingman.wechat.Sensitive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

@Composable
fun TrialScreen(store: ConfigStore, glassAlpha: Float) {
    val palette = LocalPalette.current
    var input by remember { mutableStateOf("对方: 在吗\n我: 在\n对方: 周末有空吗，想约你吃个饭") }
    var suggestion by remember { mutableStateOf<Suggestion?>(null) }
    var rewriting by remember { mutableStateOf<Int?>(null) }
    /** 哪一条的「✎」被点开了 —— 预设只在点开时出现（和微信里那张卡片同一套做法）。 */
    var presetsOpen by remember { mutableStateOf<Int?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var info by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val hits = remember(input) { Sensitive.hits(input) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 120.dp)) {
        ScreenHeader("试一试", "粘贴聊天 · 预览回复 · 验证接口")
        GlassCard(glassAlpha) {
            GlassField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.fillMaxWidth().heightIn(min = 130.dp),
                label = { Text("每行一句，以「我:」或「对方:」开头") },
            )
            if (hits.isNotEmpty()) {
                Spacer(Modifier.height(Space.md))
                Text("⚠ 含敏感内容：${hits.joinToString("、")}（微信里会先问你一次）", fontSize = TextSize.body, color = palette.warn)
            }
            Spacer(Modifier.height(Space.lg))
            Button(
                onClick = {
                    loading = true
                    error = null
                    info = null
                    suggestion = null
                    val conf = store.load()
                    scope.launch {
                        try {
                            val msgs = input.lines().filter { it.isNotBlank() }.map {
                                val me = it.startsWith("我:") || it.startsWith("我：")
                                ChatMsg(me, it.substringAfter(':').substringAfter('：').trim().ifEmpty { it })
                            }
                            if (conf.graded) {
                                val out = withContext(Dispatchers.IO) {
                                    Graded.run(
                                        replyClient = LlmClient(conf),
                                        riskClient = LlmClient(conf.riskEndpoint(), second = true),
                                        skillPrompt = conf.prompt,
                                        msgs = msgs,
                                        roleContext = null,
                                        waitMs = gradedWaitMs(conf.probeReplyMs, conf.probeRiskMs),
                                        plan = conf.replyPlan,
                                    )
                                }
                                suggestion = out.suggestion
                                if (out.totalTokens > 0) store.addUsage(out.totalTokens)
                                info = "${out.millis}ms · ${out.totalTokens} token · 分级模式（两路并行）"
                                if (out.suggestion == null) {
                                    error = "两路都没跑通：风险一路 ${out.riskError ?: "-"}；写回复一路 ${out.replyError ?: "-"}"
                                }
                            } else {
                                val result = withContext(Dispatchers.IO) { LlmClient(conf).analyze(msgs) }
                                suggestion = result.suggestion
                                if (result.totalTokens > 0) store.addUsage(result.totalTokens)
                                info = "${result.millis}ms · ${result.totalTokens} token · ${result.model}"
                            }
                        } catch (t: Throwable) {
                            error = t.message
                        }
                        loading = false
                    }
                },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                enabled = !loading,
                shape = RoundedCornerShape(RadiusR2),
                colors = ButtonDefaults.buttonColors(containerColor = palette.primary),
            ) { Text(if (loading) "思考中…" else "生成候选回复") }
        }
        error?.let { msg ->             GlassCard(glassAlpha, border = palette.badMark.copy(alpha = CardBorderAlpha)) { Text(msg, color = palette.bad, fontSize = TextSize.body) } }
        info?.let { GlassCard(glassAlpha) { Text(it, fontSize = TextSize.body, color = palette.sub) } }
        suggestion?.let { s ->
            GlassCard(glassAlpha) {
                Text("意图：${s.intent}", fontSize = TextSize.emphasis, fontWeight = FontWeight.Bold, color = palette.text)
                Text("风险：${s.risk}　${s.note}", fontSize = TextSize.body, color = palette.sub)
                Text("AI 生成 · 发送前请自行判断", fontSize = TextSize.body, color = palette.sub)
            }
            if (s.partial) {
                Text(
                    "⚠ 模型输出被截断，这条是从残缺 JSON 里抢救出来的，建议重新生成",
                    fontSize = TextSize.body,
                    color = palette.warn,
                )
            }
            // 分级模式：哪一路挂了/没拿到回复，直接写出来，别让人对着空卡片猜
            s.warnings.forEach { w -> Text(w, fontSize = TextSize.body, color = palette.warn) }
            if (s.replies.isEmpty()) {
                Text("⚠ 这次没拿到可用回复，点上面「生成候选回复」重来", fontSize = TextSize.body, color = palette.warn)
            }
            // 与微信里那张卡片**同一套规格**（设计师 P1）：三条候选装在**同一张卡**里，
            // 每条是一个圆角填充块（头行「★ 风格 · 偏长」+ 右侧 ✎），预设**点 ✎ 才出现**。
            // 以前这里是「每条候选一张 GlassCard + 三个预设永远铺在外面」，和微信卡片是两套做法 ——
            // 同一个东西两副长相，用户得学两遍。
            GlassCard(glassAlpha) {
                s.replies.forEachIndexed { index, reply ->
                    val starred = s.best == index
                    Column(
                        Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(RadiusR2))
                            .background(palette.soft)
                            .clickable { clipboard.setText(AnnotatedString(reply.text)) }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                buildString {
                                    append(if (starred) "★ " else "")
                                    append(reply.style)
                                    if (isReplyTooLong(reply.text)) append(" · 偏长")
                                },
                                color = palette.primary,
                                fontSize = TextSize.body,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.weight(1f),
                            )
                            // ✎ = 展开这一条的预设（再短点 / 更正式 / 换个说法），另外两条不动
                            Text(
                                "✎",
                                fontSize = TextSize.body,
                                color = palette.sub,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(RadiusR1))
                                    .background(palette.primary.copy(alpha = ChipBgAlpha))
                                    .clickable { presetsOpen = if (presetsOpen == index) null else index }
                                    .padding(horizontal = 10.dp, vertical = 3.dp),
                            )
                        }
                        Text(reply.text, fontSize = TextSize.emphasis, color = palette.text)
                        if (starred && s.why.isNotBlank()) {
                            Text("★ 最推荐：${s.why}", fontSize = TextSize.body, color = palette.sub)
                        }
                        if (isReplyTooLong(reply.text)) {
                            Text(
                                "${reply.text.length} 字，偏长 —— 发出去不太像人话，点右上角 ✎ 选「再短点」",
                                fontSize = TextSize.body,
                                color = palette.warn,
                            )
                        }
                        if (presetsOpen == index) {
                            Spacer(Modifier.height(Space.sm))
                            // 只改这一条：走 complete()（一句话进一句话出），另外两条不动
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                REWRITE_PRESETS.forEach { preset ->
                                    Text(
                                        text = if (rewriting == index) "改写中…" else preset.first,
                                        fontSize = TextSize.body,
                                        color = palette.primary,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(RadiusR1))
                                            .background(palette.primary.copy(alpha = ChipBgAlpha))
                                            .clickable(enabled = rewriting == null) {
                                                val old = suggestion?.replies?.getOrNull(index)?.text ?: return@clickable
                                                rewriting = index
                                                val conf = store.load()
                                                scope.launch {
                                                    try {
                                                        val (newText, tokens) = withContext(Dispatchers.IO) {
                                                            LlmClient(conf).rewrite(old, preset.second)
                                                        }
                                                        if (tokens > 0) store.addUsage(tokens)
                                                        suggestion = suggestion?.let { cur ->
                                                            val list = cur.replies.toMutableList()
                                                            if (index in list.indices) {
                                                                list[index] = list[index].copy(text = newText)
                                                            }
                                                            cur.copy(replies = list)
                                                        }
                                                    } catch (t: Throwable) {
                                                        error = "改写失败：${t.message}"
                                                    }
                                                    rewriting = null
                                                }
                                            }
                                            .padding(horizontal = 8.dp, vertical = 3.dp),
                                    )
                                }
                            }
                        }
                        Text("点一下复制", fontSize = TextSize.body, color = palette.sub)
                    }
                    Spacer(Modifier.height(Space.md))
                }
            }
        }
    }
}

// ================= 军师（提示词 / skill，两级） =================
