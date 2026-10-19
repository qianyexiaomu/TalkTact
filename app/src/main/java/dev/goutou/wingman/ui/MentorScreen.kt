package dev.goutou.wingman.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.goutou.wingman.config.ConfigData
import dev.goutou.wingman.config.ConfigStore
import dev.goutou.wingman.config.replyPlan
import dev.goutou.wingman.llm.BUILT_IN_SKILLS
import dev.goutou.wingman.llm.applyReplyPlan
import dev.goutou.wingman.llm.coreOf
import dev.goutou.wingman.llm.RemoteSkill
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

@Composable
fun MentorScreen(store: ConfigStore, glassAlpha: Float, onSaved: () -> Unit) {
    val palette = LocalPalette.current
    var cfg by remember { mutableStateOf(store.load()) }
    // 「新手 / 进阶」以前是用 cfg.maxTokens == 0 推断出来的，会粘住：
    // 只要进过一次进阶（token 档位被改成无限制），以后每次进来都自动是进阶，切回新手也甩不掉。
    // 现在改成独立的界面偏好记下来。
    var advanced by remember { mutableStateOf(cfg.mentorAdvanced) }
    var tweak by remember { mutableStateOf(false) }
    // skill 那两块默认收起：进来先看到「现在用的是哪个」，想换再点开
    var skillsOpen by remember { mutableStateOf(false) }
    var importOpen by remember { mutableStateOf(false) }
    var text by remember { mutableStateOf(cfg.prompt) }
    var url by remember { mutableStateOf("") }
    var importing by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf<String?>(null) }
    var dialog by remember { mutableStateOf<String?>(null) }
    var confirmReset by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    // 「有没有还没保存的改动」= 草稿提示词和**上次落盘的那份**比（跟「外观」「高级设置」同一套判据）。
    // 不能只看一个「保存过没有」的标志 —— 刚进页面它就说你没保存，红字等于天天亮着。
    val dirty = text != cfg.prompt

    fun persist(prompt: String, skillId: String, unlimited: Boolean) {
        store.save(store.load().copy(prompt = prompt, skillId = skillId, maxTokens = if (unlimited) 0 else store.load().maxTokens))
        cfg = store.load()
        text = cfg.prompt
        onSaved()
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 120.dp)) {
        ScreenHeader("军师", "提示词 · skill")
        GlassCard(glassAlpha) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GlassPill("新手设置", !advanced, Modifier.weight(1f)) {
                    advanced = false
                    tweak = false
                    store.save(store.load().copy(mentorAdvanced = false))
                    cfg = store.load()
                }
                GlassPill("进阶设置", advanced, Modifier.weight(1f)) {
                    advanced = true
                    tweak = false
                    val wasLimited = cfg.maxTokens != 0
                    store.save(store.load().copy(maxTokens = 0, mentorAdvanced = true))
                    cfg = store.load()
                    onSaved()
                    dialog = if (wasLimited) {
                        "已切到进阶设置。\n\n为避免 skill 输出被截断，Token 限制已改为：无限制。"
                    } else {
                        "已切到进阶设置。"
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                if (!advanced) "用内置的原版狗头军师，提示词可以直接编辑微调。"
                else "从内置 skill 里选一个，或者粘一个 GitHub 上开源 skill 的地址导入。",
                fontSize = 13.sp,
                color = palette.sub,
            )
        }

        if (!advanced) {
            GlassCard(glassAlpha) {
                Text(
                    if (text.contains("replies")) "✓ 含 JSON 输出契约 · ${text.length} 字" else "⚠ 缺少 JSON 契约，卡片会解析不了",
                    fontSize = 13.sp,
                    color = if (text.contains("replies")) palette.ok else palette.warn,
                )
                Spacer(Modifier.height(8.dp))
                GlassField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 280.dp),
                )
                Spacer(Modifier.height(10.dp))
                if (dirty) {
                    Text("未保存，你所做出的改动不会被保存", fontSize = 13.sp, color = palette.bad)
                    Spacer(Modifier.height(6.dp))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = { text = ConfigData().prompt },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(RadiusR2),
                    ) { Text("恢复默认") }
                    Button(
                        onClick = { persist(text, "classic", unlimited = false) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(RadiusR2),
                        colors = ButtonDefaults.buttonColors(containerColor = palette.primary),
                    ) { Text(if (dirty) "保存" else "已保存") }
                }
            }
        } else {
            GroupTitle("当前军师")
            // 「当前军师」是当前生效的状态，不能藏起来 —— 所以它是一张常驻卡，放在最前面；
            // 换 skill 的入口收进下面的「Skill 库」分组，全页最多一层折叠。
            GlassCard(glassAlpha) {
                val src = BUILT_IN_SKILLS.firstOrNull { it.id == cfg.skillId }
                val canReset = src != null && text != src.prompt
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(skillName(cfg.skillId), fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = palette.text)
                            Spacer(Modifier.width(8.dp))
                            SourceChip(cfg.skillId)
                        }
                        Text(
                            "${text.length} 字 · " +
                                if (text.contains("replies")) "含 JSON 契约" else "⚠ 缺少 JSON 契约",
                            fontSize = 13.sp,
                            color = if (text.contains("replies")) palette.sub else palette.warn,
                        )
                    }
                    if (canReset) {
                        TextButton(onClick = { confirmReset = true }) { Text("重置") }
                    }
                    TextButton(onClick = { tweak = !tweak }) { Text(if (tweak) "收起" else "微调") }
                }
                Spacer(Modifier.height(6.dp))
                // 这里只给「这是什么」；真要逐字看/改，点右上那个「微调」—— 那块才是编辑区。
                HintText(
                    src?.summary ?: text.lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty(),
                    fontSize = 13.sp,
                    color = palette.sub,
                )
                if (tweak) {
                    Spacer(Modifier.height(8.dp))
                    GlassField(
                        value = text,
                        onValueChange = { text = it },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 220.dp),
                    )
                    Spacer(Modifier.height(10.dp))
                    if (dirty) {
                        Text("未保存，你所做出的改动不会被保存", fontSize = 13.sp, color = palette.bad)
                        Spacer(Modifier.height(6.dp))
                    }
                    Button(
                        onClick = {
                            // 内容跟哪个内置 skill 逐字一样，就还算那个 skill；动过了才算「自定义」。
                            // 比的是**正文**（去掉规格段和契约），不是整段逐字比：
                            // 用户改过条数/风格之后，尾部两段会跟着重拼，
                            // 整段比对会把内置 skill 误判成「自定义」，再切一次就把 skillId 冲掉了。
                            val known = BUILT_IN_SKILLS.firstOrNull { coreOf(it.prompt) == coreOf(text) }?.id
                            persist(text, known ?: "custom", unlimited = true)
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(RadiusR2),
                        colors = ButtonDefaults.buttonColors(containerColor = palette.primary),
                    ) { Text(if (dirty) "保存" else "已保存") }
                }
            }

            GroupTitle("Skill 库")
            FoldCard(
                title = "内置 skill",
                summary = "当前是「${skillName(cfg.skillId)}」· 点开可换",
                expanded = skillsOpen,
                glassAlpha = glassAlpha,
                onToggle = { skillsOpen = !skillsOpen },
            ) {
                Text("点一下直接启用（会替换当前提示词）", fontSize = 13.sp, color = palette.sub)
                Spacer(Modifier.height(4.dp))
                BUILT_IN_SKILLS.forEach { skill ->
                    // 同上：比正文，不比整段 —— 否则改过条数后当前 skill 会被显示成「没选中」。
                    val selected = cfg.skillId == skill.id && coreOf(cfg.prompt) == coreOf(skill.prompt)
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp)
                            .clip(RoundedCornerShape(RadiusR2))
                            .background(palette.glassTint.copy(alpha = if (selected) 0.18f else 0.09f))
                            .clickable {
                                // 内置 skill 存的是「默认三条」那份快照（[BUILT_IN_SKILLS] 是常量，
                                // 不该因为用户改过设置就被改写）。直接存它会把用户选的条数和风格丢掉 ——
                                // 所以按**当前**规格重拼一份再存，正文照样是内置 skill 的。
                                val cur = store.load()
                                store.save(
                                    cur.copy(
                                        prompt = applyReplyPlan(
                                            skill.prompt,
                                            // 用配置上的 replyPlan：它会带上用户自定义的风格。
                                            // 这里漏掉那个参数的话，切到内置 skill 时自定义风格会
                                            // 从提示词快照里消失（下一次调用才又被拼回来，很怪）。
                                            cur.replyPlan,
                                        ),
                                        skillId = skill.id,
                                        maxTokens = 0,
                                    )
                                )
                                cfg = store.load()
                                text = cfg.prompt
                                tweak = false
                                onSaved()
                                dialog = "已启用「${skill.name}」。\n\nToken 限制已改为：无限制。"
                            }
                            .padding(14.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(skill.name, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = palette.text, modifier = Modifier.weight(1f))
                            Text(
                                if (selected) "使用中" else "启用",
                                fontSize = 13.sp,
                                color = if (selected) palette.ok else palette.primary,
                            )
                        }
                        Text(skill.summary, fontSize = 13.sp, color = palette.sub)
                    }
                }
            }

            FoldCard(
                title = "从 GitHub 导入 skill",
                summary = "粘 SKILL.md 的地址，导入并启用",
                expanded = importOpen,
                glassAlpha = glassAlpha,
                onToggle = { importOpen = !importOpen },
            ) {
                HintText(
                    "粘 SKILL.md 的地址即可（网页地址也行，会自动换成 raw 直链）。导入时会自动补齐 JSON 输出契约。",
                    fontSize = 13.sp,
                    color = palette.sub,
                )
                Spacer(Modifier.height(8.dp))
                GlassField(
                    value = url,
                    onValueChange = { url = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("https://github.com/…/SKILL.md") },
                    singleLine = true,
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = {
                        importing = true
                        note = null
                        val target = url
                        scope.launch {
                            note = try {
                                val fetched = withContext(Dispatchers.IO) { RemoteSkill.fetch(target) }
                                text = fetched
                                persist(fetched, "custom", unlimited = true)
                                dialog = "已导入并启用这个 skill。\n\nToken 限制已改为：无限制。"
                                "导入成功 · ${fetched.length} 字"
                            } catch (t: Throwable) {
                                "导入失败：${t.message}"
                            }
                            importing = false
                        }
                    },
                    enabled = !importing && url.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(RadiusR2),
                    colors = ButtonDefaults.buttonColors(containerColor = palette.primary),
                ) { Text(if (importing) "导入中…" else "导入并启用") }
                note?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(it, fontSize = 13.sp, color = if (it.startsWith("导入成功")) palette.ok else palette.bad)
                }
            }
        }
    }

    dialog?.let { msg ->
        AlertDialog(
            onDismissRequest = { dialog = null },
            confirmButton = { TextButton(onClick = { dialog = null }) { Text("好") } },
            title = { Text("进阶设置") },
            text = { Text(msg) },
        )
    }

    // 重置是**不可逆**的（改过的那份不留备份），所以先问一句再动。
    if (confirmReset) {
        val target = BUILT_IN_SKILLS.firstOrNull { it.id == cfg.skillId }
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            confirmButton = {
                TextButton(onClick = {
                    confirmReset = false
                    target?.let { persist(it.prompt, it.id, unlimited = true) }
                    tweak = false
                }) { Text("重置") }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("取消") } },
            title = { Text("重置提示词") },
            text = { Text("把提示词恢复成「${skillName(cfg.skillId)}」的原版内容。你改过的那一份不会留备份。") },
        )
    }
}

// ================= 设置 =================
