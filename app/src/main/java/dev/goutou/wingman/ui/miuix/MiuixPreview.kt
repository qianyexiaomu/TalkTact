package dev.goutou.wingman.ui.miuix

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.goutou.wingman.config.ConfigData
import dev.goutou.wingman.config.ConfigStore
import dev.goutou.wingman.config.GLASS_QUALITY_AUTO
import dev.goutou.wingman.config.GLASS_QUALITY_HIGH
import dev.goutou.wingman.config.GLASS_QUALITY_LOW
import dev.goutou.wingman.llm.DEFAULT_STYLE_IDS
import dev.goutou.wingman.llm.MAX_REPLIES
import dev.goutou.wingman.llm.REPLY_STYLES
import dev.goutou.wingman.llm.toggledStyleIds
import dev.goutou.wingman.config.replyPlan
import dev.goutou.wingman.ui.RadiusR2
import dev.goutou.wingman.ui.appVersion
import dev.goutou.wingman.ui.moduleActive
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.extra.SuperArrow
import top.yukonga.miuix.kmp.extra.SuperSwitch
import top.yukonga.miuix.kmp.theme.Colors
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.darkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme
import top.yukonga.miuix.kmp.utils.HorizontalDivider

/**
 * Miuix 新版界面（迁移第一阶段）。
 *
 * 布局参照 KernelSU / BoxProxy 那一类工具首页：
 * **大标题 → 状态大卡 → 2×2 小信息卡 → 底部液态玻璃导航条**。
 *
 * 导航条是**独立的一份**（[MiuixGlassNavBar]）：Miuix 自己的 NavigationBar 是不透明实心板，
 * 而旧玻璃系统的 `GlassSurface` 读的是项目旧调色板（紫），跟这里的蓝不搭 ——
 * 所以这里用 Miuix 的 surface 半透明 + 细描边 + 投影，深浅色都跟着主题走。
 */
@Composable
fun MiuixPreviewScreen(
    store: ConfigStore,
    /** 打开时停在哪一页：0 = 首页标签，1 = 外观，2 = 自动分析。给截图测试用，正常调用不用传。 */
    initialPage: Int = 0,
    // onBack 必须是**最后一个参数**：调用点是 `MiuixPreviewScreen(store) { ... }` 的尾随 lambda 写法，
    // 末尾一旦换成 Int，lambda 就绑不上去了。
    onBack: () -> Unit,
) {
    MiuixTheme(colors = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
        val c = MiuixTheme.colorScheme
        var cfg by remember { mutableStateOf(store.load()) }
        var tab by remember { mutableIntStateOf(0) }
        // 二级页：0 = 没有（底部导航那一层），1 = 外观，2 = 自动分析
        var page by remember { mutableIntStateOf(initialPage) }
        // 立刻落盘：这些页都是「点一下就该生效」的选择型，不跟旧界面那个「保存」按钮走
        val save: (ConfigData) -> Unit = { next ->
            store.save(next)
            cfg = store.load()
        }
        // 风格勾选规则**必须**走 llm/toggledStyleIds：新点的一定留下、位置不够让末尾、
        // 点掉最后一个 = 保持现状。在这里另写一遍 = 迟早和旧界面对不上。
        val toggleStyle: (String) -> Unit = { id ->
            val known = REPLY_STYLES.map { it.id }.toSet() + cfg.replyCustomStyles.map { it.id }
            val base = cfg.replyStyleIds.ifEmpty { DEFAULT_STYLE_IDS }
            store.saveReplyPlan(toggledStyleIds(base, id, MAX_REPLIES, known))
            cfg = store.load()
        }

        // 底色用 Miuix 自己的 `background`（浅色是近白灰、深色是黑）。
        //
        // 之前试过「透明，露出 App 的紫色渐变」：渐变本身带一条竖向光带，白卡贴上去很脏，
        // 而且大标题压在紫底上刺眼 —— 参考图那种干净感全没了。
        // 代价是玻璃导航条没东西可模糊、退化成「白磨砂 + 描边」；这个取舍是刻意的：
        // 干净比"真折射"重要，导航条在白底上也一样好看。
        Box(Modifier.fillMaxSize().background(c.background)) {
            if (page == 0) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        // 不加 statusBarsPadding：外层 App() 的 Column 已经有了一次，
                        // 这里再加就是**双重内边距**（真机上标题会离状态栏特别远）。
                        // 给底部导航条留位置：不留的话最后一张卡会被压在半透明玻璃底下
                        .padding(bottom = 108.dp),
                ) {
                    when (tab) {
                        0 -> HomeTab(c, store, cfg, onOpenTab = { tab = it }, onBack = onBack)
                        1 -> StatusTab(c, store, cfg)
                        2 -> SettingsTab(c, onOpenPage = { page = it }, onOld = onBack)
                        else -> AboutTab(c, store)
                    }
                }

                MiuixGlassNavBar(
                    items = listOf(
                        "首页" to Icons.Filled.Home,
                        "状态" to Icons.Filled.Pets,
                        "设置" to Icons.Filled.Settings,
                        "关于" to Icons.Filled.Info,
                    ),
                    selected = tab,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                ) { tab = it }
            } else {
                // 二级页：不显示底部导航（避免两套导航打架），顶部一个返回行
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(bottom = 28.dp),
                ) {
                    when (page) {
                        1 -> AppearancePage(c, cfg, save, onOld = onBack) { page = 0 }
                        2 -> AutoAnalyzePage(c, cfg, save, onOld = onBack) { page = 0 }
                        else -> ReplyStylePage(c, cfg, toggleStyle, onOld = onBack) { page = 0 }
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeTab(
    c: Colors,
    store: ConfigStore,
    cfg: ConfigData,
    onOpenTab: (Int) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val (calls, tokens) = remember { store.usage() }
    val active = moduleActive(store)
    val styles = cfg.replyPlan.styles.size
    // 卡片右侧那三行小指标用的都是真实配置：勾了几种风格 / 单独设过几个联系人 / 白名单几个人
    val chats = cfg.chatStyles.size
    val white = cfg.whitelist.size

    Row(
        Modifier.fillMaxWidth().padding(start = 26.dp, end = 18.dp, top = 22.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("微信军师", fontSize = 30.sp, fontWeight = FontWeight.SemiBold, color = c.onBackground)
            Spacer(Modifier.height(2.dp))
            Text("TalkTact · ${appVersion(context)}", fontSize = 13.sp, color = c.onBackgroundVariant)
        }
        Text(
            "返回",
            fontSize = 14.sp,
            color = c.primary,
            modifier = Modifier.clickable(onClick = onBack).padding(8.dp),
        )
    }

    SmallTitle(text = "运行状态")
    // 正文颜色跟着底色走：运行中是浅色强调底（深色字用 onSurface），未启用是白卡。
    // 未启用**不用 secondaryContainer**：那色压在底上会发浑（试过）。
    Card(
        modifier = Modifier.padding(horizontal = 12.dp),
        color = if (active) c.primary.copy(alpha = 0.12f) else c.surface,
    ) {
        Column(Modifier.padding(horizontal = 18.dp, vertical = 16.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                // 左：状态 + 模型 + 自动分析（宽度让给右边那三行，模型名太长就省略号）
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(9.dp)
                                .clip(CircleShape)
                                .background(if (active) c.primary else c.onSurfaceVariantSummary),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (active) "运行中" else "未启用",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = c.onSurface,
                        )
                    }
                    Spacer(Modifier.height(9.dp))
                    Text(
                        "模型　${cfg.model.ifBlank { "还没填" }}",
                        fontSize = 13.sp,
                        color = c.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        "自动分析　${if (cfg.enabled) "开" else "关"}",
                        fontSize = 13.sp,
                        color = c.onSurface,
                        maxLines = 1,
                    )
                }
                Spacer(Modifier.width(14.dp))
                // 右：三行小指标（右对齐）。都是真实配置，不是装饰数字。
                Column(horizontalAlignment = Alignment.End) {
                    MetaRow(c, "风格", "$styles 种")
                    Spacer(Modifier.height(5.dp))
                    MetaRow(c, "联系人", "$chats 个")
                    Spacer(Modifier.height(5.dp))
                    MetaRow(c, "白名单", "$white 个")
                }
            }
            if (!active) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "到 LSPosed 里勾选微信，再强行停止微信重开",
                    fontSize = 12.sp,
                    color = c.onSurfaceVariantSummary,
                )
            }
        }
    }

    // 三宫格入口（参考图里 WebUI / Sub-Store / 日志 那排）
    Spacer(Modifier.height(10.dp))
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        QuickTile(c, "设置", "外观 / 接入", Modifier.weight(1f)) { onOpenTab(2) }
        QuickTile(c, "状态", "模块 / 用量", Modifier.weight(1f)) { onOpenTab(1) }
        QuickTile(c, "关于", "版本 / 说明", Modifier.weight(1f)) { onOpenTab(3) }
    }

    SmallTitle(text = "用量")
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        MiniStat(c, "调用次数", "$calls", Modifier.weight(1f))
        MiniStat(c, "累计 tokens", tokens.toString(), Modifier.weight(1f))
    }
    Spacer(Modifier.height(10.dp))
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        MiniStat(c, "参考条数", "${cfg.ctx} 条", Modifier.weight(1f))
        MiniStat(c, "最短间隔", "${cfg.minIntervalSec}s", Modifier.weight(1f))
    }

    Spacer(Modifier.height(14.dp))
    Card(modifier = Modifier.padding(horizontal = 12.dp)) {
        Column(Modifier.padding(horizontal = 18.dp, vertical = 14.dp)) {
            Text("提示", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = c.onSurface)
            Spacer(Modifier.height(4.dp))
            Text(
                "改完设置要「强行停止微信」再打开 —— 注入侧的模块代码是微信启动时加载的。",
                fontSize = 12.sp,
                color = c.onSurfaceVariantSummary,
            )
        }
    }
}

/** 状态卡右侧的一行小指标：左边灰色标签、右边深色数值（整列右对齐）。 */
@Composable
private fun MetaRow(c: Colors, label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 11.sp, color = c.onSurfaceVariantSummary, maxLines = 1)
        Spacer(Modifier.width(6.dp))
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = c.onSurface, maxLines = 1)
    }
}

/** 等宽小入口（三宫格那一排）：标题 + 一句副标题，整块可点。 */
@Composable
private fun QuickTile(c: Colors, title: String, sub: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Card(
        modifier = modifier.clip(RoundedCornerShape(RadiusR2)).clickable(onClick = onClick),
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 13.dp)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = c.onSurface, maxLines = 1)
            Spacer(Modifier.height(2.dp))
            Text(sub, fontSize = 11.sp, color = c.onSurfaceVariantSummary, maxLines = 1)
        }
    }
}

@Composable
private fun StatusTab(c: Colors, store: ConfigStore, cfg: ConfigData) {
    val (calls, tokens) = remember { store.usage() }
    val active = moduleActive(store)
    val styles = cfg.replyPlan.styles.size

    Row(
        Modifier.fillMaxWidth().padding(start = 28.dp, end = 24.dp, top = 22.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("状态", fontSize = 30.sp, fontWeight = FontWeight.SemiBold)
    }

    SmallTitle(text = "模块")
    Card(modifier = Modifier.padding(horizontal = 12.dp)) {
        BasicComponent(
            title = "微信内模块",
            summary = if (active) "已启用，微信侧已加载" else "未启用",
            rightActions = {
                Text(
                    if (active) "运行中" else "未启用",
                    fontSize = 14.sp,
                    color = if (active) c.primary else c.onSurfaceVariantSummary,
                )
            },
        )
        HorizontalDivider(thickness = 0.5.dp, color = c.dividerLine)
        BasicComponent(
            title = "自动分析",
            summary = "在聊天页自动出候选",
            rightActions = {
                Text(if (cfg.enabled) "开" else "关", fontSize = 14.sp, color = c.onSurfaceVariantSummary)
            },
        )
        HorizontalDivider(thickness = 0.5.dp, color = c.dividerLine)
        BasicComponent(
            title = "模型",
            summary = cfg.model.ifBlank { "还没填" },
            rightActions = { Text("$styles 种风格", fontSize = 14.sp, color = c.onSurfaceVariantSummary) },
        )
    }

    SmallTitle(text = "用量")
    Card(modifier = Modifier.padding(horizontal = 12.dp)) {
        BasicComponent(title = "调用次数", rightActions = { Text("$calls", fontSize = 15.sp) })
        HorizontalDivider(thickness = 0.5.dp, color = c.dividerLine)
        BasicComponent(title = "累计 tokens", rightActions = { Text(tokens.toString(), fontSize = 15.sp) })
    }
}

@Composable
private fun SettingsTab(c: Colors, onOpenPage: (Int) -> Unit, onOld: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 28.dp, end = 24.dp, top = 22.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("设置", fontSize = 30.sp, fontWeight = FontWeight.SemiBold)
    }

    SmallTitle(text = "常用")
    Card(modifier = Modifier.padding(horizontal = 12.dp)) {
        SuperArrow(title = "外观与背景", summary = "玻璃强度 / 背景图 / 深色", onClick = { onOpenPage(1) })
        HorizontalDivider(thickness = 0.5.dp, color = c.dividerLine)
        SuperArrow(title = "接入与模型", summary = "接口地址 / API Key / 模型", onClick = onOld)
        HorizontalDivider(thickness = 0.5.dp, color = c.dividerLine)
        SuperArrow(title = "回复风格", summary = "可多选 + 自定义 + 按联系人", onClick = { onOpenPage(3) })
    }
    SmallTitle(text = "微信内")
    Card(modifier = Modifier.padding(horizontal = 12.dp)) {
        SuperArrow(title = "自动分析", summary = "开关 / 频率 / 参考条数", onClick = { onOpenPage(2) })
        HorizontalDivider(thickness = 0.5.dp, color = c.dividerLine)
        SuperArrow(title = "会话白名单", summary = "只在这些聊天里工作", onClick = onOld)
        HorizontalDivider(thickness = 0.5.dp, color = c.dividerLine)
        SuperArrow(title = "图片与 OCR", summary = "认字 / 认不出才发图 / 总是发图", onClick = onOld)
    }
    SmallTitle(text = "维护")
    Card(modifier = Modifier.padding(horizontal = 12.dp)) {
        SuperArrow(title = "备份与迁移", summary = "导出 / 导入配置", onClick = onOld)
        HorizontalDivider(thickness = 0.5.dp, color = c.dividerLine)
        SuperArrow(title = "诊断", summary = "决策轨迹 / 抓界面 / 诊断包", onClick = onOld)
    }

    Spacer(Modifier.height(18.dp))
    Text(
        "外观、自动分析已经是 Miuix 页面；其余条目暂时回旧界面（逐页迁移中）。",
        fontSize = 12.sp,
        color = c.onBackgroundVariant,
        modifier = Modifier.padding(horizontal = 28.dp),
    )
}

@Composable
private fun AboutTab(c: Colors, store: ConfigStore) {
    val context = LocalContext.current
    Row(Modifier.fillMaxWidth().padding(start = 28.dp, end = 24.dp, top = 22.dp)) {
        Text("关于", fontSize = 30.sp, fontWeight = FontWeight.SemiBold)
    }
    SmallTitle(text = "版本")
    Card(modifier = Modifier.padding(horizontal = 12.dp)) {
        BasicComponent(title = "TalkTact", rightActions = { Text(appVersion(context), fontSize = 14.sp) })
        HorizontalDivider(thickness = 0.5.dp, color = c.dividerLine)
        BasicComponent(title = "界面", rightActions = { Text("Miuix 0.2.9", fontSize = 14.sp) })
    }
    SmallTitle(text = "说明")
    Card(modifier = Modifier.padding(horizontal = 12.dp)) {
        Column(Modifier.padding(horizontal = 18.dp, vertical = 14.dp)) {
            Text(
                "· 只读聊天页上的消息，只把候选回复填进输入框，不会自动发送。\n" +
                    "· 修改微信客户端行为存在风控风险，建议先用小号。\n" +
                    "· API Key 以明文存在本应用私有目录（注入侧要跨进程读取，无法用 Keystore 加密）。",
                fontSize = 13.sp,
                color = c.onSurfaceVariantSummary,
            )
        }
    }
}

@Composable
private fun DetailHeader(c: Colors, title: String, onBack: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 22.dp, end = 24.dp, top = 18.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "‹ 返回",
            fontSize = 15.sp,
            color = c.primary,
            modifier = Modifier.clickable(onClick = onBack).padding(vertical = 8.dp, horizontal = 2.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(title, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = c.onBackground)
    }
}

/**
 * 外观：玻璃效果三档。
 *
 * 只搬「档位」这一组 —— 不透明度/模糊是连续滑杆，Miuix 这边还没有对应的控件，
 * 先用一条 SuperArrow 指回旧界面（迁移中，不做半吊子替代）。
 */
@Composable
private fun AppearancePage(
    c: Colors,
    cfg: ConfigData,
    save: (ConfigData) -> Unit,
    onOld: () -> Unit,
    onBack: () -> Unit,
) {
    DetailHeader(c, "外观与背景", onBack)

    SmallTitle(text = "玻璃效果")
    Card(modifier = Modifier.padding(horizontal = 12.dp)) {
        val options = listOf(
            Triple("自动", "按设备性能和内存自己挑", GLASS_QUALITY_AUTO),
            Triple("高（全开）", "模糊和折射都开，最好看也最费", GLASS_QUALITY_HIGH),
            Triple("低（省电）", "不模糊、不掉帧", GLASS_QUALITY_LOW),
        )
        options.forEachIndexed { i, opt ->
            val label = opt.first
            val desc = opt.second
            val id = opt.third
            BasicComponent(
                title = label,
                summary = desc,
                onClick = { save(cfg.copy(glassQuality = id)) },
                rightActions = {
                    if (cfg.glassQuality == id) {
                        Text("✓", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = c.primary)
                    }
                },
            )
            if (i != options.lastIndex) HorizontalDivider(thickness = 0.5.dp, color = c.dividerLine)
        }
    }

    SmallTitle(text = "更多")
    Card(modifier = Modifier.padding(horizontal = 12.dp)) {
        SuperArrow(title = "不透明度 / 模糊 / 背景图", summary = "这三项在旧界面里调", onClick = onOld)
    }
    Spacer(Modifier.height(24.dp))
}

/** 自动分析：开关 + 两个节奏参数。参考条数/间隔用「− 数值 ＋」步进，比数字输入框稳。 */
@Composable
private fun AutoAnalyzePage(
    c: Colors,
    cfg: ConfigData,
    save: (ConfigData) -> Unit,
    onOld: () -> Unit,
    onBack: () -> Unit,
) {
    DetailHeader(c, "自动分析", onBack)

    SmallTitle(text = "开关")
    Card(modifier = Modifier.padding(horizontal = 12.dp)) {
        SuperSwitch(
            title = "自动分析",
            summary = "在聊天页自动出候选；关掉就只能手动点「识别」",
            checked = cfg.enabled,
            onCheckedChange = { save(cfg.copy(enabled = it)) },
        )
    }

    SmallTitle(text = "节奏")
    Card(modifier = Modifier.padding(horizontal = 12.dp)) {
        StepperRow(c, "参考条数", cfg.ctx, 2, 20, 1) { save(cfg.copy(ctx = it)) }
        HorizontalDivider(thickness = 0.5.dp, color = c.dividerLine)
        StepperRow(c, "最短间隔", cfg.minIntervalSec, 0, 600, 5, suffix = "s") {
            save(cfg.copy(minIntervalSec = it))
        }
    }

    SmallTitle(text = "更多")
    Card(modifier = Modifier.padding(horizontal = 12.dp)) {
        SuperArrow(title = "白名单 / 敏感内容 / 温度", summary = "这些在旧界面里调", onClick = onOld)
    }
    Spacer(Modifier.height(24.dp))
}

/** 回复风格：内置风格多选。勾几种就写几条（条数是从勾选数派生的，没有单独的「条数」选项）。 */
@Composable
private fun ReplyStylePage(
    c: Colors,
    cfg: ConfigData,
    onToggle: (String) -> Unit,
    onOld: () -> Unit,
    onBack: () -> Unit,
) {
    DetailHeader(c, "回复风格", onBack)

    // 「亮着的」= **显式勾选**那一份（replyStyleIds），不是 plan.styles ——
    // 后者掺了「为凑够条数自动补齐」的风格，拿它当选中态会出现「点了没反应」。
    val chosen = cfg.replyStyleIds.ifEmpty { DEFAULT_STYLE_IDS }.toSet()

    SmallTitle(text = "勾几种就写几条")
    Card(modifier = Modifier.padding(horizontal = 12.dp)) {
        REPLY_STYLES.forEachIndexed { i, st ->
            BasicComponent(
                title = st.label,
                onClick = { onToggle(st.id) },
                rightActions = {
                    if (st.id in chosen) {
                        Text("✓", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = c.primary)
                    }
                },
            )
            if (i != REPLY_STYLES.lastIndex) HorizontalDivider(thickness = 0.5.dp, color = c.dividerLine)
        }
    }

    SmallTitle(text = "更多")
    Card(modifier = Modifier.padding(horizontal = 12.dp)) {
        SuperArrow(title = "自定义风格 / 按联系人单独设置", summary = "这些在旧界面里调", onClick = onOld)
    }
    Spacer(Modifier.height(24.dp))
}

@Composable
private fun StepperRow(
    c: Colors,
    title: String,
    value: Int,
    min: Int,
    max: Int,
    step: Int,
    suffix: String = "",
    onChange: (Int) -> Unit,
) {
    BasicComponent(
        title = title,
        rightActions = {
            Text(
                "−",
                fontSize = 20.sp,
                color = c.primary,
                modifier = Modifier
                    .clickable { onChange((value - step).coerceAtLeast(min)) }
                    .padding(horizontal = 10.dp),
            )
            Text(
                "$value$suffix",
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = c.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(min = 42.dp),
            )
            Text(
                "＋",
                fontSize = 18.sp,
                color = c.primary,
                modifier = Modifier
                    .clickable { onChange((value + step).coerceAtMost(max)) }
                    .padding(horizontal = 10.dp),
            )
        },
    )
}

@Composable
private fun MiniStat(c: Colors, title: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Text(title, fontSize = 12.sp, color = c.onSurfaceVariantSummary)
            Spacer(Modifier.height(4.dp))
            Text(value, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}
