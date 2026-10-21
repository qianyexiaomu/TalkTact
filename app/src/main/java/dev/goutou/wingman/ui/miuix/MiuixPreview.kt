package dev.goutou.wingman.ui.miuix

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.goutou.wingman.config.Backup
import dev.goutou.wingman.config.ConfigData
import dev.goutou.wingman.config.ConfigStore
import dev.goutou.wingman.config.Role
import dev.goutou.wingman.llm.Geo
import dev.goutou.wingman.llm.Graded
import dev.goutou.wingman.llm.LlmException
import dev.goutou.wingman.llm.NetInfo
import dev.goutou.wingman.llm.LlmClient
import dev.goutou.wingman.llm.Suggestion
import dev.goutou.wingman.llm.gradedWaitMs
import dev.goutou.wingman.config.SELF_ROLE_KEY
import dev.goutou.wingman.config.GLASS_QUALITY_AUTO
import dev.goutou.wingman.config.GLASS_QUALITY_HIGH
import dev.goutou.wingman.config.GLASS_QUALITY_LOW
import dev.goutou.wingman.config.IMAGE_MODE_OCR
import dev.goutou.wingman.config.IMAGE_MODE_OCR_THEN_VISION
import dev.goutou.wingman.config.IMAGE_MODE_VISION
import dev.goutou.wingman.llm.ApiShape
import dev.goutou.wingman.llm.PROVIDER_PRESETS
import dev.goutou.wingman.llm.presetOf
import dev.goutou.wingman.llm.BUILT_IN_SKILLS
import dev.goutou.wingman.llm.DEFAULT_STYLE_IDS
import dev.goutou.wingman.llm.MAX_CUSTOM_LABEL_LEN
import dev.goutou.wingman.llm.MAX_CUSTOM_STYLES
import dev.goutou.wingman.llm.ReplyStyle
import dev.goutou.wingman.llm.customStyle
import dev.goutou.wingman.llm.FULL_PROMPT
import dev.goutou.wingman.llm.applyReplyPlan
import dev.goutou.wingman.llm.MAX_REPLIES
import dev.goutou.wingman.llm.REPLY_STYLES
import dev.goutou.wingman.llm.RemoteSkill
import dev.goutou.wingman.llm.toggledStyleIds
import dev.goutou.wingman.wechat.ChatMsg
import dev.goutou.wingman.wechat.Sensitive
import dev.goutou.wingman.config.replyPlan
import dev.goutou.wingman.ui.RadiusR2
import dev.goutou.wingman.proxy.ProxyService
import dev.goutou.wingman.ui.agoText
import dev.goutou.wingman.ui.appVersion
import dev.goutou.wingman.ui.moduleActiveReason
import dev.goutou.wingman.ui.buildDiagZip
import dev.goutou.wingman.ui.formatTime
import dev.goutou.wingman.ui.skillName
import dev.goutou.wingman.ui.moduleActive
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Slider
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.extra.SuperArrow
import top.yukonga.miuix.kmp.extra.SuperSwitch
import top.yukonga.miuix.kmp.theme.Colors
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.darkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    /** 打开时停在哪个标签：0 首页 / 1 军师 / 2 角色 / 3 设置。给截图测试用。 */
    initialTab: Int = 0,
    /** 打开时停在哪一页：1 外观 / 2 自动分析 / 3 回复风格 / 4 接入 / 5 图片 / 6 角色详情 / 7 关于 /
     *  8 诊断 / 9 白名单 / 10 备份 / 11 代理 / 12 按联系人 / 13 会话风格 / 14 自检 / 16 试一试。 */
    initialPage: Int = 0,
    /** 配合 initialPage = 6：直接打开哪一份角色档案。 */
    initialRoleKey: String? = null,
    /** 配合 initialPage = 13：直接打开哪个会话的「单独风格」。 */
    initialChatKey: String? = null,
    // onBack 必须是**最后一个参数**：调用点是 `MiuixPreviewScreen(store) { ... }` 的尾随 lambda 写法，
    // 末尾一旦换成 Int，lambda 就绑不上去了。
    onBack: () -> Unit,
) {
    MiuixTheme(colors = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
        val c = MiuixTheme.colorScheme
        var cfg by remember { mutableStateOf(store.load()) }
        var tab by remember { mutableIntStateOf(initialTab) }
        // 二级页：0 = 标签层；1 外观 / 2 自动分析 / 3 回复风格 / 4 接入 / 5 图片 / 6 角色 / 7 关于
        // 8 诊断 / 9 白名单 / 10 备份 / 11 代理 / 12 按联系人 / 13 会话风格 / 14 自检 / 16 试一试
        var page by remember { mutableIntStateOf(initialPage) }
        // 打开的是哪一份角色档案（page == 6 时有效）
        var roleKey by remember { mutableStateOf(initialRoleKey) }
        // 正在编辑「按联系人设风格」的哪一个会话（page == 13）
        var chatKey by remember { mutableStateOf(initialChatKey) }
        // 滚动位置按「页面」记住：以前每个分支各自 `rememberScrollState()`，
        // 一离开就被销毁 → 返回时永远从顶部开始。这里按 key 存一份，回来还是原来的位置。
        val tabScroll = remember { mutableStateMapOf<Int, ScrollState>() }
        val pageScroll = remember { mutableStateMapOf<Int, ScrollState>() }
        // 系统返回：**先退二级页 → 再回首页标签 → 才交还给系统（退出）**。
        // 以前没挂 BackHandler，系统直接按默认行为退出 —— 在一堆二级页里按返回，App 就没了。
        BackHandler(enabled = page != 0 || tab != 0) {
            when {
                page != 0 -> page = 0
                tab != 0 -> tab = 0
            }
        }
        // 立刻落盘：这些页都是「点一下就该生效」的选择型，不跟旧界面那个「保存」按钮走
        val save: (ConfigData) -> Unit = { next ->
            store.save(next)
            cfg = store.load()
        }
        // 自定义风格：新增后**顺手选上**（和旧界面一致），否则加了还要再点一次药丸
        val addCustomStyle: (String, String) -> Boolean = { label, hint ->
            val st = customStyle(label, hint)
            if (st == null) {
                false
            } else {
                store.saveCustomStyles(cfg.replyCustomStyles + st)
                val known = REPLY_STYLES.map { it.id }.toSet() + (cfg.replyCustomStyles + st).map { it.id }
                val base = cfg.replyStyleIds.ifEmpty { DEFAULT_STYLE_IDS }
                store.saveReplyPlan(toggledStyleIds(base, st.id, MAX_REPLIES, known))
                cfg = store.load()
                true
            }
        }
        // 删除：`saveCustomStyles` 会按「还存在的风格」重拼提示词，所以不用手动改勾选
        val removeCustomStyle: (ReplyStyle) -> Unit = { st ->
            store.saveCustomStyles(cfg.replyCustomStyles.filterNot { it.id == st.id })
            cfg = store.load()
        }
        // 按联系人设风格：`setChatStyles` 会把空列表原样保留（= 已添加、先跟随全局）
        val setChatStyles: (String, List<String>) -> Unit = { name, ids ->
            store.setChatStyles(cfg.chatStyles + (name to ids))
            cfg = store.load()
        }
        val removeChatStyle: (String) -> Unit = { name ->
            store.setChatStyles(cfg.chatStyles - name)
            cfg = store.load()
        }
        // 白名单这类**窄写入**（只动一两个字段）用完重读一次
        val reload: () -> Unit = { cfg = store.load() }
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
                        .verticalScroll(tabScroll.getOrPut(tab) { ScrollState(0) })
                        // 新界面是**根**了（RootScreen 直接渲染它），状态栏内边距得自己加 ——
                        // 以前挂在旧 App() 的 Column 里时这里不能加（会双份），位置一换就得跟着换。
                        .statusBarsPadding()
                        // 给底部导航条留位置：不留的话最后一张卡会被压在半透明玻璃底下
                        .padding(bottom = 108.dp),
                ) {
                    when (tab) {
                        0 -> HomeTab(c, store, cfg, onOpenPage = { page = it }, onBack = onBack)
                        1 -> MentorTab(c, store, cfg, save)
                        2 -> RolesTab(c, store, onOpen = { roleKey = it; page = 6 })
                        else -> SettingsTab(c, onOpenPage = { page = it }, onOld = onBack)
                    }
                }

                MiuixGlassNavBar(
                    items = listOf(
                        "首页" to Icons.Filled.Home,
                        "军师" to Icons.Filled.Edit,
                        "角色" to Icons.Filled.Person,
                        "设置" to Icons.Filled.Settings,
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
                        .verticalScroll(pageScroll.getOrPut(page) { ScrollState(0) })
                        .statusBarsPadding()
                        .padding(bottom = 28.dp),
                ) {
                    when (page) {
                        1 -> AppearancePage(c, cfg, save, onOld = onBack) { page = 0 }
                        2 -> AutoAnalyzePage(c, cfg, save, onWhitelist = { page = 9 }) { page = 0 }
                        3 -> ReplyStylePage(
                            c, cfg, toggleStyle, addCustomStyle, removeCustomStyle,
                            onOpenChatStyles = { page = 12 },
                        ) { page = 0 }
                        4 -> EndpointPage(c, cfg, save, onOld = onBack) { page = 0 }
                        5 -> ImagePage(c, cfg, store, onOld = onBack) { page = 0 }
                        6 -> RoleDetailPage(c, store, roleKey, onBack = { page = 1 })
                        7 -> AboutPage(c, store) { page = 0 }
                        8 -> DiagnosticPage(c, store, cfg, save) { page = 0 }
                        9 -> WhitelistPage(c, store, cfg, reload) { page = 0 }
                        10 -> BackupPage(c, store, cfg, reload) { page = 0 }
                        11 -> ProxyPage(c, store, cfg) { page = 0 }
                        14 -> SelfCheckPage(c, store, cfg) { page = 0 }
                        12 -> ChatStylesPage(
                            c, store, cfg, setChatStyles,
                            onOpen = { chatKey = it; page = 13 },
                        ) { page = 3 }
                        16 -> TrialPage(c, store) { page = 0 }
                        else -> ChatStyleEditPage(
                            c, cfg, chatKey, setChatStyles,
                            onRemove = { removeChatStyle(it); page = 12 },
                        ) { page = 12 }
                    }
                }
            }
        }
    }
}

/**
 * 列表行左边那个图标块：**统一色调**（中性浅底 + 主色图标）。
 *
 * 之前按语义给每个图标配了不同颜色（紫/蓝/青/橙/粉…），一屏看下来像调色盘，
 * 反而抢了文字的位置 —— 图标的作用是「快速定位这一行」，不是分区上色。
 * 现在全部同色：靠灰度层次分块，靠图标形状区分条目。
 */
@Composable
private fun IconChip(icon: ImageVector) {
    Box(
        Modifier
            .size(30.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(MiuixTheme.colorScheme.secondaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MiuixTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.size(17.dp),
        )
    }
}

@Composable
private fun HomeTab(
    c: Colors,
    store: ConfigStore,
    cfg: ConfigData,
    onOpenPage: (Int) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val (calls, tokens) = remember { store.usage() }
    val active = moduleActive(store)
    val reason = moduleActiveReason(store)
    val styles = cfg.replyPlan.styles.size
    val chats = cfg.chatStyles.size
    val white = cfg.whitelist.size
    val route = remember { store.lastRoute() }
    val heartbeat = remember { store.heartbeatAt() }
    val roleCount = remember { store.roles().size }
    val candCount = remember { store.chatCandidates().size }
    // Android 11+ 要清单里声明 <queries> 才读得到别的 App 版本（已加，见 AndroidManifest）
    val wechatVersion = remember {
        runCatching {
            context.packageManager.getPackageInfo("com.tencent.mm", 0).versionName
        }.getOrNull() ?: "未安装"
    }

    Row(
        Modifier.fillMaxWidth().padding(start = 26.dp, end = 18.dp, top = 22.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("微信军师", fontSize = 30.sp, fontWeight = FontWeight.SemiBold, color = c.onBackground)
            Spacer(Modifier.height(2.dp))
            Text("TalkTact · ${appVersion(context)}", fontSize = 13.sp, color = c.onBackgroundVariant)
        }
    }

    // ── 运行状态（hero）──
    SmallTitle(text = "运行状态")
    val fg = if (active) c.onPrimaryContainer else c.onSurface
    Card(
        modifier = Modifier.padding(horizontal = 12.dp),
        color = if (active) c.primaryContainer else c.surface,
    ) {
        Column(Modifier.padding(horizontal = 18.dp, vertical = 16.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
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
                            color = fg,
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        reason ?: "到 LSPosed 里勾选微信，再强行停止微信重开",
                        fontSize = 12.sp,
                        color = if (active) fg else c.onSurfaceVariantSummary,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "模型　${cfg.model.ifBlank { "还没填" }}",
                        fontSize = 13.sp,
                        color = fg,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.width(14.dp))
                Column(horizontalAlignment = Alignment.End) {
                    MetaRow(c, "风格", "$styles 种")
                    Spacer(Modifier.height(5.dp))
                    MetaRow(c, "联系人", "$chats 个")
                    Spacer(Modifier.height(5.dp))
                    MetaRow(c, "白名单", "$white 个")
                }
            }
        }
    }

    // ── 模块 / 微信 / 信息 ──
    SmallTitle(text = "模块")
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        BasicComponent(
            title = "注入状态",
            leftAction = { IconChip(Icons.Filled.Memory) },
            summary = reason ?: "还没在微信里跑起来",
            rightActions = {
                Text(
                    if (active) "已注入" else "未注入",
                    fontSize = 13.sp,
                    color = if (active) c.primary else c.onSurfaceVariantSummary,
                )
            },
        )
        HorizontalDivider(thickness = 0.5.dp, color = c.dividerLine)
        BasicComponent(
            title = "最近一次走的路",
            leftAction = { IconChip(Icons.Filled.NetworkCheck) },
            summary = if (route.first.isBlank()) "还没有回传" else route.first,
            rightActions = {
                if (route.second > 0) {
                    Text(agoText(route.second, System.currentTimeMillis()), fontSize = 12.sp, color = c.onSurfaceVariantSummary)
                }
            },
        )
        HorizontalDivider(thickness = 0.5.dp, color = c.dividerLine)
        BasicComponent(
            title = "模块版本",
            leftAction = { IconChip(Icons.Filled.Info) },
            rightActions = { Text(appVersion(context), fontSize = 13.sp, color = c.onSurfaceVariantSummary) },
        )
    }

    SmallTitle(text = "微信")
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        BasicComponent(
            title = "微信版本",
            leftAction = { IconChip(Icons.Filled.Forum) },
            summary = "com.tencent.mm",
            rightActions = { Text(wechatVersion, fontSize = 13.sp, color = c.onSurfaceVariantSummary) },
        )
        HorizontalDivider(thickness = 0.5.dp, color = c.dividerLine)
        BasicComponent(
            title = "最近心跳",
            leftAction = { IconChip(Icons.Filled.Favorite) },
            summary = if (heartbeat > 0) agoText(heartbeat, System.currentTimeMillis()) else "微信里还没跑过模块",
            rightActions = {
                Text(
                    if (heartbeat > 0) "正常" else "无",
                    fontSize = 13.sp,
                    color = if (heartbeat > 0) c.primary else c.onSurfaceVariantSummary,
                )
            },
        )
        HorizontalDivider(thickness = 0.5.dp, color = c.dividerLine)
        BasicComponent(
            title = "自动分析",
            leftAction = { IconChip(Icons.Filled.Bolt) },
            summary = if (cfg.enabled) "在聊天页自动出候选" else "已关闭，只手动识别",
            rightActions = { Text(if (cfg.enabled) "开" else "关", fontSize = 13.sp, color = c.onSurfaceVariantSummary) },
        )
    }

    SmallTitle(text = "信息")
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
        MiniStat(c, "角色", "$roleCount", Modifier.weight(1f))
        MiniStat(c, "候选会话", "$candCount", Modifier.weight(1f))
    }

    // ── 快捷入口（把「军师 / 试一试」从底部挪到这里）──
    Spacer(Modifier.height(12.dp))
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        QuickTile(c, "试一试", "粘聊天看输出", Modifier.weight(1f)) { onOpenPage(16) }
        QuickTile(c, "诊断", "自检 / 抓界面", Modifier.weight(1f)) { onOpenPage(8) }
        QuickTile(c, "关于", "版本 / 说明", Modifier.weight(1f)) { onOpenPage(7) }
    }
    Spacer(Modifier.height(24.dp))
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
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.96f else 1f, tween(90))
    Card(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(18.dp))
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 13.dp)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = c.onSurface, maxLines = 1)
            Spacer(Modifier.height(2.dp))
            Text(sub, fontSize = 11.sp, color = c.onSurfaceVariantSummary, maxLines = 1)
        }
    }
}

/** 角色列表：每个人一份档案。点进去看详情、改关系。 */
/**
 * 军师 = 提示词编辑器。
 *
 * 三块：**提示词**（草稿 + 保存）、**Skill**（内置几套，点一下连提示词一起换）、**重置**。
 *
 * 注意 Skill 切换不是「只改个 id」：它要拿 `applyReplyPlan(skill.prompt, cur.replyPlan)`
 * **重拼一份提示词快照** —— 只改 skillId 的话，页面上显示的提示词还是旧的，两边对不上。
 * 同时把 maxTokens 置 0（旧界面的同一条约定：别让新 skill 的输出被长度截断）。
 *
 * 远程导入（粘 GitHub 链接）还没搬：它要挂协程和网络状态，单独一轮做。
 */
@Composable
private fun MentorTab(
    c: Colors,
    store: ConfigStore,
    cfg: ConfigData,
    save: (ConfigData) -> Unit,
) {
    var text by remember(cfg.prompt) { mutableStateOf(cfg.prompt) }
    var confirmReset by remember { mutableStateOf(false) }
    var importOpen by remember { mutableStateOf(false) }
    var url by remember { mutableStateOf("") }
    var importing by remember { mutableStateOf(false) }
    var importNote by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val dirty = text != cfg.prompt

    Row(
        Modifier.fillMaxWidth().padding(start = 26.dp, end = 18.dp, top = 22.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("军师", fontSize = 30.sp, fontWeight = FontWeight.SemiBold, color = c.onBackground)
            Spacer(Modifier.height(2.dp))
            Text("当前 skill：${skillName(cfg.skillId)}", fontSize = 13.sp, color = c.onBackgroundVariant)
        }
    }

    SmallTitle(text = "提示词")
    Column(Modifier.padding(horizontal = 12.dp)) {
        TextField(
            value = text,
            onValueChange = { text = it },
            label = "提示词（改完点下面的保存）",
            // 高度要封顶：提示词动辄几十行，不限高的话它会把 Skill / 重置整个挤出屏幕
            //（真机截图里就是这样，底下的字还透在导航条底下）。超出的部分在框内滚动。
            minLines = 5,
            maxLines = 10,
            modifier = Modifier.fillMaxWidth(),
        )
    }
    Spacer(Modifier.height(12.dp))
    PrimaryButton(
        text = if (dirty) "保存" else "已保存",
        enabled = dirty,
        modifier = Modifier.padding(horizontal = 12.dp),
    ) {
        val cur = store.load()
        save(cur.copy(prompt = text))
    }

    SmallTitle(text = "Skill")
    Card(modifier = Modifier.padding(horizontal = 12.dp)) {
        BUILT_IN_SKILLS.forEachIndexed { i, sk ->
            BasicComponent(
                title = sk.name,
                summary = sk.summary,
                onClick = {
                    val cur = store.load()
                    save(
                        cur.copy(
                            prompt = applyReplyPlan(sk.prompt, cur.replyPlan),
                            skillId = sk.id,
                            maxTokens = 0,
                        ),
                    )
                },
                rightActions = {
                    if (cfg.skillId == sk.id) {
                        Text("✓", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = c.primary)
                    }
                },
            )
            if (i != BUILT_IN_SKILLS.lastIndex) HorizontalDivider(thickness = 0.5.dp, color = c.dividerLine)
        }
    }

    SmallTitle(text = "从链接导入")
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        BasicComponent(
            title = if (importOpen) "收起" else "粘一个 SKILL.md 的地址",
            summary = "GitHub 页面地址也行（会自动换成 raw 直链）",
            onClick = { importOpen = !importOpen },
            rightActions = { Text(if (importOpen) "收起" else "展开", fontSize = 13.sp, color = c.primary) },
        )
        if (importOpen) {
            HorizontalDivider(thickness = 0.5.dp, color = c.dividerLine)
            Column(Modifier.padding(16.dp)) {
                TextField(
                    value = url,
                    onValueChange = { url = it },
                    label = "https://github.com/…/SKILL.md",
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                PrimaryButton(
                    text = if (importing) "导入中…" else "导入并启用",
                    enabled = !importing && url.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    importing = true
                    importNote = null
                    val target = url
                    scope.launch {
                        importNote = try {
                            val fetched = withContext(Dispatchers.IO) { RemoteSkill.fetch(target) }
                            // 和旧界面同一套：导入的内容**原样**当提示词（不套 applyReplyPlan），
                            // skillId 记 "custom"，token 放开 —— 导入的 skill 可能很长
                            val cur = store.load()
                            save(cur.copy(prompt = fetched, skillId = "custom", maxTokens = 0))
                            url = ""
                            importOpen = false
                            "导入成功 · ${fetched.length} 字，已启用"
                        } catch (t: Throwable) {
                            "导入失败：${t.message}"
                        }
                        importing = false
                    }
                }
                importNote?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        it,
                        fontSize = 12.sp,
                        color = if (it.startsWith("导入成功")) c.primary else Color(0xFFD64545),
                    )
                }
            }
        }
    }

    SmallTitle(text = "重置")
    Card(modifier = Modifier.padding(horizontal = 12.dp)) {
        BasicComponent(
            title = if (confirmReset) "再点一次确认恢复" else "恢复默认提示词",
            summary = if (confirmReset) "会把当前提示词覆盖掉" else "换回原版那份",
            onClick = {
                if (confirmReset) {
                    val cur = store.load()
                    save(cur.copy(prompt = FULL_PROMPT))
                    confirmReset = false
                } else {
                    confirmReset = true
                }
            },
        )
    }
    Spacer(Modifier.height(24.dp))
}

/**
 * 试一试：粘一段聊天，直接打接口看输出。
 *
 * 逻辑**照搬**旧页面（分级模式走 `Graded.run` 两路并行、直通走 `LlmClient.analyze`），
 * 没有另写一套 —— 这页的意义就是"验证接口和提示词"，换个实现就失去意义了。
 * 结果点一下复制，省得手动选中。
 */
@Composable
private fun TrialPage(c: Colors, store: ConfigStore, onBack: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    var input by remember { mutableStateOf("对方: 在吗\n我: 在\n对方: 周末有空吗，想约你吃个饭") }
    var suggestion by remember { mutableStateOf<Suggestion?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var info by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val hits = remember(input) { Sensitive.hits(input) }

    DetailHeader(c, "试一试", onBack)
    Text(
        "粘一段聊天，直接看接口会写出什么",
        fontSize = 12.sp,
        color = c.onBackgroundVariant,
        modifier = Modifier.padding(horizontal = 26.dp),
    )

    SmallTitle(text = "聊天内容")
    Column(Modifier.padding(horizontal = 12.dp)) {
        TextField(
            value = input,
            onValueChange = { input = it },
            label = "每行一句，以「我:」或「对方:」开头",
            minLines = 5,
            maxLines = 9,
            modifier = Modifier.fillMaxWidth(),
        )
    }
    if (hits.isNotEmpty()) {
        Spacer(Modifier.height(8.dp))
        Text(
            "⚠ 含敏感内容：${hits.joinToString("、")}（微信里会先问你一次）",
            fontSize = 12.sp,
            color = Color(0xFFC97A00),
            modifier = Modifier.padding(horizontal = 26.dp),
        )
    }

    Spacer(Modifier.height(12.dp))
    PrimaryButton(
        text = if (loading) "思考中…" else "生成候选回复",
        enabled = !loading,
        modifier = Modifier.padding(horizontal = 12.dp),
    ) {
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
    }

    error?.let { msg ->
        Spacer(Modifier.height(12.dp))
        Card(modifier = Modifier.padding(horizontal = 12.dp)) {
            Column(Modifier.padding(16.dp)) {
                Text("出错了", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFFD64545))
                Spacer(Modifier.height(4.dp))
                Text(msg, fontSize = 12.sp, color = c.onSurfaceVariantSummary)
            }
        }
    }

    info?.let { msg ->
        Spacer(Modifier.height(10.dp))
        Card(modifier = Modifier.padding(horizontal = 12.dp)) {
            Column(Modifier.padding(16.dp)) {
                Text(msg, fontSize = 12.sp, color = c.onSurfaceVariantSummary)
            }
        }
    }

    suggestion?.let { s ->
        SmallTitle(text = "判断")
        Card(modifier = Modifier.padding(horizontal = 12.dp)) {
            BasicComponent(
                title = "意图",
                summary = s.intent.ifBlank { "—" },
            )
            HorizontalDivider(thickness = 0.5.dp, color = c.dividerLine)
            BasicComponent(
                title = "风险",
                summary = s.note.ifBlank { null },
                rightActions = { Text(s.risk, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = c.onSurface) },
            )
        }

        SmallTitle(text = "候选回复（点一下复制）")
        s.replies.forEachIndexed { i, r ->
            Spacer(Modifier.height(8.dp))
            Card(
                modifier = Modifier
                    .padding(horizontal = 12.dp)
                    .clip(RoundedCornerShape(RadiusR2))
                    .clickable { clipboard.setText(AnnotatedString(r.text)) },
            ) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                    Text(
                        "第 ${i + 1} 条${if (s.best == i) " · 推荐" else ""}",
                        fontSize = 11.sp,
                        color = if (s.best == i) c.primary else c.onSurfaceVariantSummary,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(r.text, fontSize = 14.sp, color = c.onSurface)
                }
            }
        }
    }
    Spacer(Modifier.height(24.dp))
}

@Composable
private fun RolesTab(c: Colors, store: ConfigStore, onOpen: (String) -> Unit) {
    var tick by remember { mutableIntStateOf(0) }
    val roles = remember(tick) { store.roles() }

    Row(
        Modifier.fillMaxWidth().padding(start = 26.dp, end = 18.dp, top = 22.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "角色",
            fontSize = 30.sp,
            fontWeight = FontWeight.SemiBold,
            color = c.onBackground,
            modifier = Modifier.weight(1f),
        )
        Text(
            "刷新",
            fontSize = 14.sp,
            color = c.primary,
            modifier = Modifier.clickable { tick++ }.padding(8.dp),
        )
    }
    Spacer(Modifier.height(6.dp))

    SmallTitle(text = "档案")
    // 「本人」是 `Roles.withSelf` 永远会加的那条，所以 roles 永远不会真的为空 ——
    // 判断「有没有别人的档案」要按 key 排除它，否则新手看到的就是一页空白 + 一条本人。
    val hasOthers = roles.any { it.key != SELF_ROLE_KEY }
    if (!hasOthers) {
        Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
            Column(Modifier.padding(16.dp)) {
                Text("还没有别人", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = c.onSurface)
                Spacer(Modifier.height(3.dp))
                Text(
                    "去微信里打开一个聊天页停两三秒，回来点右上角刷新。",
                    fontSize = 12.sp,
                    color = c.onSurfaceVariantSummary,
                )
            }
        }
        Spacer(Modifier.height(10.dp))
    }
    if (roles.isEmpty()) {
        Card(modifier = Modifier.padding(horizontal = 12.dp)) {
            Column(Modifier.padding(18.dp)) {
                Text("还没有记录", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = c.onSurface)
                Spacer(Modifier.height(4.dp))
                Text(
                    "去微信里打开几个聊天页、各停两三秒，回来点「刷新」。",
                    fontSize = 12.sp,
                    color = c.onSurfaceVariantSummary,
                )
            }
        }
    } else {
        Card(modifier = Modifier.padding(horizontal = 12.dp)) {
            roles.forEachIndexed { i, r ->
                BasicComponent(
                    title = r.name,
                    summary = roleSummary(r),
                    leftAction = { RoleAvatar(c, r) },
                    onClick = { onOpen(r.key) },
                    rightActions = {
                        Text(
                            if (r.lastAt > 0) formatTime(r.lastAt) else "—",
                            fontSize = 12.sp,
                            color = c.onSurfaceVariantSummary,
                        )
                    },
                )
                if (i != roles.lastIndex) HorizontalDivider(thickness = 0.5.dp, color = c.dividerLine)
            }
        }
    }
    Spacer(Modifier.height(24.dp))
}

private fun roleSummary(r: Role): String {
    val parts = mutableListOf<String>()
    if (r.relation.isNotBlank()) parts += r.relation
    parts += "${r.msgs.size} 条记录"
    if (r.note.isNotBlank()) parts += r.note.take(12)
    return parts.joinToString(" · ")
}

@Composable
private fun RoleAvatar(c: Colors, r: Role) {
    val self = r.key == SELF_ROLE_KEY
    Box(
        Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(if (self) c.primary.copy(alpha = 0.18f) else c.secondaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            r.name.take(1).ifBlank { "?" },
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            color = if (self) c.primary else c.onSecondaryContainer,
        )
    }
}

/** 单个角色的档案页：名字/关系/备注可改（草稿 + 保存），记录只读，底部是危险操作。 */
@Composable
private fun RoleDetailPage(c: Colors, store: ConfigStore, key: String?, onBack: () -> Unit) {
    var tick by remember { mutableIntStateOf(0) }
    val role = remember(key, tick) { key?.let { k -> store.roles().firstOrNull { it.key == k } } }

    if (role == null) {
        // 找不到（比如刚被删掉）：不在这里跳转（组合期做导航是副作用），只给一句话 + 返回
        DetailHeader(c, "角色", onBack)
        SmallTitle(text = "档案")
        Card(modifier = Modifier.padding(horizontal = 12.dp)) {
            Column(Modifier.padding(18.dp)) {
                Text("这份档案已经不在了", fontSize = 14.sp, color = c.onSurface)
            }
        }
        return
    }

    DetailHeader(c, role.name, onBack)

    var name by remember(role.name) { mutableStateOf(role.name) }
    var relation by remember(role.relation) { mutableStateOf(role.relation) }
    var note by remember(role.note) { mutableStateOf(role.note) }
    val dirty = name != role.name || relation != role.relation || note != role.note

    SmallTitle(text = "档案")
    Column(Modifier.padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        TextField(
            value = name,
            onValueChange = { name = it },
            label = "名字（只改显示，不影响匹配）",
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        TextField(
            value = relation,
            onValueChange = { relation = it },
            label = "TA 是你什么人（例：同事 / 客户）",
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        TextField(
            value = note,
            onValueChange = { note = it },
            label = "平时的关系 / 备注",
            minLines = 2,
            modifier = Modifier.fillMaxWidth(),
        )
    }
    Spacer(Modifier.height(14.dp))
    PrimaryButton(
        text = if (dirty) "保存" else "已保存",
        enabled = dirty,
        modifier = Modifier.padding(horizontal = 12.dp),
    ) {
        if (name.trim() != role.name) store.renameRole(role.key, name.trim())
        store.setRoleProfile(role.key, relation.trim(), note.trim())
        tick++
    }

    SmallTitle(text = "聊天记录")
    Card(modifier = Modifier.padding(horizontal = 12.dp)) {
        BasicComponent(
            title = "共 ${role.msgs.size} 条",
            summary = if (role.lastAt > 0) "最近一次 ${formatTime(role.lastAt)}" else "还没有记录",
        )
    }

    // 合并：同一个人被记成两条时用。`mergeRoles(from, to)` 把 from 的记录并进 to
    val others = remember(role.key, tick) {
        store.roles().filter { it.key != role.key && it.key != SELF_ROLE_KEY }
    }
    var mergeOpen by remember { mutableStateOf(false) }
    SmallTitle(text = "合并")
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        BasicComponent(
            title = if (mergeOpen) "收起" else "把另一个角色合并进来",
            summary = if (others.isEmpty()) "没有别的角色可合并" else "同一个人被记成两条时用",
            onClick = { mergeOpen = !mergeOpen },
            rightActions = {
                if (others.isNotEmpty()) {
                    Text(if (mergeOpen) "收起" else "选择", fontSize = 13.sp, color = c.primary)
                }
            },
        )
        if (mergeOpen) {
            others.forEach { o ->
                HorizontalDivider(thickness = 0.5.dp, color = c.dividerLine)
                BasicComponent(
                    title = o.name,
                    summary = "${o.msgs.size} 条记录 · 并进来之后这条会消失",
                    onClick = {
                        store.mergeRoles(o.key, role.key)
                        tick++
                        mergeOpen = false
                    },
                )
            }
        }
    }

    SmallTitle(text = "危险操作")
    var confirmDelete by remember { mutableStateOf(false) }
    Card(modifier = Modifier.padding(horizontal = 12.dp)) {
        BasicComponent(
            title = "清空聊天记录",
            summary = "档案保留，只删记录",
            onClick = {
                store.clearRoleMsgs(role.key)
                tick++
            },
        )
        HorizontalDivider(thickness = 0.5.dp, color = c.dividerLine)
        BasicComponent(
            title = if (confirmDelete) "再点一次确认删除" else "删除这个角色",
            titleColor = Color(0xFFD64545),
            summary = if (confirmDelete) "删掉之后记录也没了，不能恢复" else null,
            onClick = {
                if (confirmDelete) {
                    store.removeRole(role.key)
                    onBack()
                } else {
                    confirmDelete = true
                }
            },
        )
    }
    Spacer(Modifier.height(24.dp))
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
        SuperArrow(
            title = "外观与背景", summary = "玻璃强度 / 背景图 / 深色", onClick = { onOpenPage(1) },
            leftAction = { IconChip(Icons.Filled.Palette) },
        )
        HorizontalDivider(thickness = 0.5.dp, color = c.dividerLine)
        SuperArrow(
            title = "本地代理", summary = "API Key 不出本应用", onClick = { onOpenPage(11) },
            leftAction = { IconChip(Icons.Filled.Security) },
        )
        HorizontalDivider(thickness = 0.5.dp, color = c.dividerLine)
        SuperArrow(
            title = "接入与模型", summary = "接口地址 / API Key / 模型", onClick = { onOpenPage(4) },
            leftAction = { IconChip(Icons.Filled.Cloud) },
        )
        HorizontalDivider(thickness = 0.5.dp, color = c.dividerLine)
        SuperArrow(
            title = "回复风格", summary = "可多选 + 自定义 + 按联系人", onClick = { onOpenPage(3) },
            leftAction = { IconChip(Icons.Filled.Face) },
        )
    }
    SmallTitle(text = "微信内")
    Card(modifier = Modifier.padding(horizontal = 12.dp)) {
        SuperArrow(
            title = "自动分析", summary = "开关 / 频率 / 参考条数", onClick = { onOpenPage(2) },
            leftAction = { IconChip(Icons.Filled.Bolt) },
        )
        HorizontalDivider(thickness = 0.5.dp, color = c.dividerLine)
        SuperArrow(
            title = "会话白名单", summary = "只在这些聊天里工作", onClick = { onOpenPage(9) },
            leftAction = { IconChip(Icons.Filled.People) },
        )
        HorizontalDivider(thickness = 0.5.dp, color = c.dividerLine)
        SuperArrow(
            title = "图片与 OCR", summary = "认字 / 认不出才发图 / 总是发图", onClick = { onOpenPage(5) },
            leftAction = { IconChip(Icons.Filled.Image) },
        )
    }
    SmallTitle(text = "维护")
    Card(modifier = Modifier.padding(horizontal = 12.dp)) {
        SuperArrow(
            title = "接口自检", summary = "网络 / 能不能连上 / 要多久", onClick = { onOpenPage(14) },
            leftAction = { IconChip(Icons.Filled.NetworkCheck) },
        )
        HorizontalDivider(thickness = 0.5.dp, color = c.dividerLine)
        SuperArrow(
            title = "关于", summary = "版本 / 风控说明", onClick = { onOpenPage(7) },
            leftAction = { IconChip(Icons.Filled.Info) },
        )
        HorizontalDivider(thickness = 0.5.dp, color = c.dividerLine)
        SuperArrow(
            title = "备份与迁移", summary = "导出 / 导入配置", onClick = { onOpenPage(10) },
            leftAction = { IconChip(Icons.Filled.Archive) },
        )
        HorizontalDivider(thickness = 0.5.dp, color = c.dividerLine)
        SuperArrow(
            title = "诊断", summary = "决策轨迹 / 抓界面 / 诊断包", onClick = { onOpenPage(8) },
            leftAction = { IconChip(Icons.Filled.BugReport) },
        )
    }

}

@Composable
private fun AboutPage(c: Colors, store: ConfigStore, onBack: () -> Unit) {
    val context = LocalContext.current
    DetailHeader(c, "关于", onBack)
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

/**
 * 二级页的头：圆形图标返回 + 标题。
 *
 * 试过官方 `TopAppBar`，但它的标题有个前置条件：**未滚动时显示 `largeTitle`、
 * 滚动后才显示 `title`**（alpha 按 `scrolledOffset` 算）。不绑 `MiuixScrollBehavior` 时
 * `expandedHeightPx` 是 0，除出来是 NaN，两个槽都是透明的 —— 等于把标题丢了
 * （截图测试就是因为找不到标题文本而失败）。
 *
 * 要真正用上那个「大标题随滚动收起」的效果，得把顶栏提到根级、和每个页面的滚动联动，
 * 那是另一轮的结构改动。在那之前这里保持自己拼：一个圆形按钮 + 标题，行为确定。
 */
@Composable
private fun DetailHeader(c: Colors, title: String, onBack: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 14.dp, end = 24.dp, top = 14.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(c.secondaryContainer)
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "返回",
                tint = c.onSurface,
                modifier = Modifier.size(19.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(title, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = c.onBackground)
    }
}

/**
 * 外观：玻璃效果三档。
 *
 * 档位是**离散选择**（点一下立刻落盘）；不透明度 / 模糊 / 压暗是连续滑杆，走草稿 + 保存。
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

    // 滑杆是**连续**的：每动一格就写一次 prefs 太重（`save` 还会重拼提示词快照），
    // 所以和地址一样走草稿 + 显式保存；档位那种离散选择才立刻生效。
    var alpha by remember(cfg.glassAlpha) { mutableStateOf(cfg.glassAlpha) }
    var blur by remember(cfg.glassBlur) { mutableStateOf(cfg.glassBlur) }
    var dim by remember(cfg.bgDim) { mutableStateOf(cfg.bgDim) }
    val frostedDirty = alpha != cfg.glassAlpha || blur != cfg.glassBlur || dim != cfg.bgDim
    val context = LocalContext.current
    // 选图走 OpenDocument + 持久授权：重启之后还得能读到这张图。
    // 有的来源不支持持久授权（云盘之类），那就只在本次运行里有效 —— 不能因此崩掉。
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            save(cfg.copy(bgUri = uri.toString()))
        }
    }

    SmallTitle(text = "磨砂")
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Text(
                "不透明度 ${(alpha * 100).toInt()}%",
                fontSize = 13.sp,
                color = c.onSurface,
            )
            Slider(
                progress = alpha,
                minValue = 0.3f,
                maxValue = 1f,
                onProgressChange = { alpha = it },
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "背景模糊 ${blur.toInt()}dp",
                fontSize = 13.sp,
                color = c.onSurface,
            )
            Slider(
                progress = blur,
                minValue = 0f,
                maxValue = 40f,
                decimalPlaces = 0,
                onProgressChange = { blur = it },
            )
            // 压暗只对自定义背景图开放：内置渐变本身已经调过明度，再压一层会把配色一起糊掉
            if (cfg.bgUri.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "背景压暗 ${(dim * 100).toInt()}%",
                    fontSize = 13.sp,
                    color = c.onSurface,
                )
                Slider(
                    progress = dim,
                    minValue = 0f,
                    maxValue = 0.7f,
                    onProgressChange = { dim = it },
                )
            }
        }
    }
    Spacer(Modifier.height(12.dp))
    PrimaryButton(
        text = if (frostedDirty) "保存" else "已保存",
        enabled = frostedDirty,
        modifier = Modifier.padding(horizontal = 12.dp),
    ) {
        save(cfg.copy(glassAlpha = alpha, glassBlur = blur, bgDim = dim))
    }

    SmallTitle(text = "背景图")
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        BasicComponent(
            title = if (cfg.bgUri.isBlank()) "选择背景图" else "更换背景图",
            summary = if (cfg.bgUri.isBlank()) {
                "当前是内置渐变（跟随系统明暗），选一张图会替换掉它"
            } else {
                "已设置自定义背景；玻璃面板背后会对它做模糊 + 折射"
            },
            onClick = { pickImage.launch(arrayOf("image/*")) },
            rightActions = { Text("选择", fontSize = 13.sp, color = c.primary) },
        )
        if (cfg.bgUri.isNotBlank()) {
            HorizontalDivider(thickness = 0.5.dp, color = c.dividerLine)
            BasicComponent(
                title = "恢复默认背景",
                summary = "换回内置渐变",
                onClick = { save(cfg.copy(bgUri = "")) },
                rightActions = { Text("恢复", fontSize = 13.sp, color = Color(0xFFD64545)) },
            )
        }
    }
    Spacer(Modifier.height(24.dp))
}

/** 自动分析：开关 + 两个节奏参数。参考条数/间隔用「− 数值 ＋」步进，比数字输入框稳。 */
@Composable
private fun AutoAnalyzePage(
    c: Colors,
    cfg: ConfigData,
    save: (ConfigData) -> Unit,
    onWhitelist: () -> Unit,
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

    SmallTitle(text = "内容与温度")
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        SuperSwitch(
            title = "允许敏感内容",
            summary = "关掉时，检测到敏感内容会先问你一次再发",
            checked = cfg.allowSensitive,
            onCheckedChange = { save(cfg.copy(allowSensitive = it)) },
        )
        HorizontalDivider(thickness = 0.5.dp, color = c.dividerLine)
        TempRow(c, cfg.temperature) { save(cfg.copy(temperature = it)) }
    }

    SmallTitle(text = "更多")
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        SuperArrow(title = "会话白名单", summary = "转到「会话白名单」页", onClick = { onWhitelist() })
    }
    Spacer(Modifier.height(24.dp))
}

/** 回复风格：内置风格多选。勾几种就写几条（条数是从勾选数派生的，没有单独的「条数」选项）。 */
@Composable
private fun ReplyStylePage(
    c: Colors,
    cfg: ConfigData,
    onToggle: (String) -> Unit,
    onAddCustom: (String, String) -> Boolean,
    onRemoveCustom: (ReplyStyle) -> Unit,
    onOpenChatStyles: () -> Unit,
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

    val customs = cfg.replyCustomStyles
    var newName by remember { mutableStateOf("") }
    var newHint by remember { mutableStateOf("") }
    var addNote by remember { mutableStateOf<String?>(null) }

    SmallTitle(text = "自定义（${customs.size}/$MAX_CUSTOM_STYLES）")
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        if (customs.isEmpty()) {
            Column(Modifier.padding(16.dp)) {
                Text("还没有自定义风格", fontSize = 13.sp, color = c.onSurface)
                Spacer(Modifier.height(3.dp))
                Text("比如「毒舌」「哄小孩」，写完会在上面多一个可勾选的药丸", fontSize = 12.sp, color = c.onSurfaceVariantSummary)
            }
        } else {
            customs.forEachIndexed { i, st ->
                BasicComponent(
                    title = st.label,
                    summary = st.hint.ifBlank { null },
                    rightActions = { Text("删除", fontSize = 13.sp, color = Color(0xFFD64545)) },
                    onClick = { onRemoveCustom(st) },
                )
                if (i != customs.lastIndex) HorizontalDivider(thickness = 0.5.dp, color = c.dividerLine)
            }
        }
    }

    if (customs.size < MAX_CUSTOM_STYLES) {
        SmallTitle(text = "加一个")
        Column(Modifier.padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            TextField(
                value = newName,
                onValueChange = { if (it.length <= MAX_CUSTOM_LABEL_LEN) newName = it },
                label = "风格名（最多 $MAX_CUSTOM_LABEL_LEN 字，比如：撒娇）",
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            TextField(
                value = newHint,
                onValueChange = { newHint = it },
                label = "说明（给模型看：用撒娇的语气带点抱怨）",
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            PrimaryButton(text = "添加", enabled = newName.isNotBlank()) {
                addNote = if (onAddCustom(newName.trim(), newHint.trim())) {
                    "已添加并选中"
                } else {
                    "加不上：名字为空、重复、或已满 $MAX_CUSTOM_STYLES 个"
                }
                if (addNote == "已添加并选中") {
                    newName = ""
                    newHint = ""
                }
            }
            addNote?.let {
                Text(it, fontSize = 12.sp, color = c.onSurfaceVariantSummary)
            }
        }
    }

    SmallTitle(text = "更多")
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        SuperArrow(title = "按联系人单独设置风格", summary = "给某几个人换一套风格", onClick = { onOpenChatStyles() })
    }
    Spacer(Modifier.height(24.dp))
}

/**
 * 接入与模型。
 *
 * 这三个是**草稿型**输入（改到一半的地址不该立刻生效），所以本地先存一份，
 * 变了才出现「保存」；其它页（档位/开关）都是点一下立刻落盘 —— 两类交互不能混。
 */
@Composable
private fun EndpointPage(
    c: Colors,
    cfg: ConfigData,
    save: (ConfigData) -> Unit,
    onOld: () -> Unit,
    onBack: () -> Unit,
) {
    DetailHeader(c, "接入与模型", onBack)

    var url by remember(cfg.baseUrl) { mutableStateOf(cfg.baseUrl) }
    var key by remember(cfg.apiKey) { mutableStateOf(cfg.apiKey) }
    var model by remember(cfg.model) { mutableStateOf(cfg.model) }
    var shape by remember(cfg.apiShape) { mutableStateOf(cfg.apiShape) }
    var customPath by remember(cfg.customPath) { mutableStateOf(cfg.customPath) }
    var provider by remember(cfg.provider) { mutableStateOf(cfg.provider) }
    var url2 by remember(cfg.baseUrl2) { mutableStateOf(cfg.baseUrl2) }
    var key2 by remember(cfg.apiKey2) { mutableStateOf(cfg.apiKey2) }
    var model2 by remember(cfg.model2) { mutableStateOf(cfg.model2) }
    val dirty = url != cfg.baseUrl || key != cfg.apiKey || model != cfg.model ||
        shape != cfg.apiShape || customPath != cfg.customPath ||
        url2 != cfg.baseUrl2 || key2 != cfg.apiKey2 || model2 != cfg.model2 || provider != cfg.provider

    // 服务商预设：只省掉「去官网查地址」这一步 —— 选完地址/形态/模型照样能手改，
    // 也不改变任何请求逻辑（本来就是「OpenAI 兼容 + 自定义地址 + 自定义模型」）。
    // 服务商预设：只省掉「去官网查地址」这一步 —— 选完地址/形态/模型照样能手改，
    // 也不改变任何请求逻辑（本来就是「OpenAI 兼容 + 自定义地址 + 自定义模型」）。
    //
    // **默认折叠**：预设有十几条，全铺开会把下面的地址/Key/模型整个推到屏幕外，
    // 而那三个才是日常要改的。这里只显示当前选中的一条，点「更换」才展开。
    val preset = presetOf(provider)
    var presetOpen by remember { mutableStateOf(false) }
    SmallTitle(text = "服务商")
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        BasicComponent(
            title = preset?.label ?: "自定义（自己填）",
            summary = preset?.baseUrl ?: "不套预设，地址和模型都自己写",
            onClick = { presetOpen = !presetOpen },
            rightActions = {
                Text(
                    if (presetOpen) "收起" else "更换",
                    fontSize = 13.sp,
                    color = c.primary,
                )
            },
        )
        if (presetOpen) {
            HorizontalDivider(thickness = 0.5.dp, color = c.dividerLine)
            BasicComponent(
                title = "自定义（自己填）",
                onClick = {
                    provider = ""
                    presetOpen = false
                },
                rightActions = {
                    if (provider.isBlank()) {
                        Text("✓", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = c.primary)
                    }
                },
            )
            PROVIDER_PRESETS.forEach { pr ->
                HorizontalDivider(thickness = 0.5.dp, color = c.dividerLine)
                BasicComponent(
                    title = pr.label,
                    summary = pr.baseUrl,
                    onClick = {
                        // 和旧界面同一套：写草稿，不改别的
                        provider = pr.id
                        url = pr.baseUrl
                        shape = pr.shape.id
                        if (pr.model.isNotBlank()) model = pr.model
                        presetOpen = false
                    },
                    rightActions = {
                        if (provider == pr.id) {
                            Text("✓", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = c.primary)
                        }
                    },
                )
            }
        }
    }
    preset?.note?.takeIf { it.isNotBlank() }?.let {
        Spacer(Modifier.height(8.dp))
        Text(it, fontSize = 12.sp, color = c.onSurfaceVariantSummary, modifier = Modifier.padding(horizontal = 26.dp))
    }

    SmallTitle(text = "接口")
    Column(Modifier.padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        TextField(
            value = url,
            onValueChange = { url = it },
            label = "接口地址（写到 /v1）",
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        TextField(
            value = key,
            onValueChange = { key = it },
            label = "API Key",
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )
        TextField(
            value = model,
            onValueChange = { model = it },
            label = "模型",
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }

    SmallTitle(text = "接口形态")
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        ApiShape.values().forEachIndexed { i, sh ->
            BasicComponent(
                title = sh.label,
                summary = sh.note,
                onClick = { shape = sh.id },
                rightActions = {
                    if (shape == sh.id) {
                        Text("✓", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = c.primary)
                    }
                },
            )
            if (i != ApiShape.values().lastIndex) HorizontalDivider(thickness = 0.5.dp, color = c.dividerLine)
        }
    }
    if (shape == ApiShape.CUSTOM.id) {
        Spacer(Modifier.height(10.dp))
        Column(Modifier.padding(horizontal = 12.dp)) {
            TextField(
                value = customPath,
                onValueChange = { customPath = it },
                label = "自定义路径（留空 = 地址本身就是完整端点）",
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    Spacer(Modifier.height(14.dp))
    PrimaryButton(
        text = if (dirty) "保存" else "已保存",
        enabled = dirty,
        modifier = Modifier.padding(horizontal = 12.dp),
    ) {
        save(
            cfg.copy(
                provider = provider,
                baseUrl = url.trim(),
                apiKey = key.trim(),
                model = model.trim(),
                apiShape = shape,
                customPath = customPath.trim(),
                baseUrl2 = url2.trim(),
                apiKey2 = key2.trim(),
                model2 = model2.trim(),
            ),
        )
    }

    SmallTitle(text = "生成模式")
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        SuperSwitch(
            title = "分级模式（两路并行）",
            summary = "风险判断和写回复分开跑，写得更快；要配第二套接口",
            checked = cfg.graded,
            // 这是运行时开关，点一下立刻生效（和上面的地址不同：改到一半不该生效）
            onCheckedChange = { save(cfg.copy(graded = it)) },
        )
    }

    SmallTitle(text = "第二套接口（分级模式的风险一路）")
    Text(
        "分级模式下风险判断走这里；留空 = 复用上面那套。",
        fontSize = 12.sp,
        color = c.onSurfaceVariantSummary,
        modifier = Modifier.padding(horizontal = 26.dp).padding(bottom = 8.dp),
    )
    Column(Modifier.padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        TextField(
            value = url2,
            onValueChange = { url2 = it },
            label = "接口地址（留空 = 复用）",
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        TextField(
            value = key2,
            onValueChange = { key2 = it },
            label = "API Key（留空 = 复用）",
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )
        TextField(
            value = model2,
            onValueChange = { model2 = it },
            label = "模型（留空 = 复用）",
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
    Spacer(Modifier.height(10.dp))
    PrimaryButton(
        text = if (dirty) "保存" else "已保存",
        enabled = dirty,
        modifier = Modifier.padding(horizontal = 12.dp),
    ) {
        save(
            cfg.copy(
                baseUrl = url.trim(),
                apiKey = key.trim(),
                model = model.trim(),
                apiShape = shape,
                customPath = customPath.trim(),
                baseUrl2 = url2.trim(),
                apiKey2 = key2.trim(),
                model2 = model2.trim(),
            ),
        )
    }

    Spacer(Modifier.height(24.dp))
}

@Composable
private fun ImagePage(
    c: Colors,
    cfg: ConfigData,
    store: ConfigStore,
    onOld: () -> Unit,
    onBack: () -> Unit,
) {
    DetailHeader(c, "图片与 OCR", onBack)

    SmallTitle(text = "识别")
    Card(modifier = Modifier.padding(horizontal = 12.dp)) {
        SuperSwitch(
            title = "本机识图（OCR）",
            summary = "不开的话图片消息只剩一个占位文字",
            checked = cfg.ocrEnabled,
            onCheckedChange = { store.saveOcr(it) },
        )
    }

    SmallTitle(text = "图片怎么交给模型")
    Card(modifier = Modifier.padding(horizontal = 12.dp)) {
        val options = listOf(
            Triple("只认字", "图片不出手机，只发识别出来的文字", IMAGE_MODE_OCR),
            Triple("认不出才发图", "字太少或没字时，才把图本体发出去", IMAGE_MODE_OCR_THEN_VISION),
            Triple("总是发图", "每条带图的聊天都把图发给接口，最准也最费", IMAGE_MODE_VISION),
        )
        options.forEachIndexed { i, opt ->
            val label = opt.first
            val desc = opt.second
            val id = opt.third
            BasicComponent(
                title = label,
                summary = desc,
                onClick = { store.saveImageMode(id) },
                rightActions = {
                    if (cfg.imageMode == id) {
                        Text("✓", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = c.primary)
                    }
                },
            )
            if (i != options.lastIndex) HorizontalDivider(thickness = 0.5.dp, color = c.dividerLine)
        }
    }

    SmallTitle(text = "最近一次识图")
    var tick by remember { mutableIntStateOf(0) }
    val (ocrLine, ocrAt) = remember(tick) { store.ocrInfo() }
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        BasicComponent(
            title = if (ocrLine.isBlank()) "还没有记录" else ocrLine,
            summary = if (ocrAt > 0) formatTime(ocrAt) else "去微信里打开一条带图的聊天，回来看这里",
            onClick = { tick++ },
            rightActions = { Text("刷新", fontSize = 13.sp, color = c.primary) },
        )
    }
    Spacer(Modifier.height(24.dp))
}

/** 主按钮（HyperOS 那种实心圆角）。Miuix 0.2.9 的 Button 签名没把握，先用 Box 画一个稳的。 */
/**
 * 诊断：三份运行时快照 + 抓取 + 导出。
 *
 * 数据全在 `ConfigStore` 里（注入侧回传后落盘）：`lastCall` / `trace` / `diag`。
 * 三块都给「时间 + 长度 + 点一下复制全文」—— 这些内容动辄几千字，塞进卡片里没法读，
 * 真要排查时都是复制出去看。
 */
@Composable
private fun DiagnosticPage(
    c: Colors,
    store: ConfigStore,
    cfg: ConfigData,
    save: (ConfigData) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var tick by remember { mutableIntStateOf(0) }
    var exportNote by remember { mutableStateOf<String?>(null) }

    val lastCall = remember(tick) { store.lastCall() }
    val lastCallAt = remember(tick) { store.lastCallAt() }
    val trace = remember(tick) { store.trace() }
    val traceAt = remember(tick) { store.traceAt() }
    val diag = remember(tick) { store.diag() }
    val diagAt = remember(tick) { store.diagAt() }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri ->
        if (uri != null) {
            exportNote = runCatching {
                context.contentResolver.openOutputStream(uri)?.use {
                    it.write(buildDiagZip(context, store, store.load()))
                }
                "已导出"
            }.getOrElse { "导出失败：${it.message}" }
        }
    }

    DetailHeader(c, "诊断", onBack)
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 26.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "出问题先看三份快照；还不行就抓界面、导出诊断包。",
            fontSize = 12.sp,
            color = c.onBackgroundVariant,
            modifier = Modifier.weight(1f),
        )
        Text("刷新", fontSize = 14.sp, color = c.primary, modifier = Modifier.clickable { tick++ }.padding(8.dp))
    }

    SmallTitle(text = "快照（点一下复制）")
    Card(modifier = Modifier.padding(horizontal = 12.dp)) {
        DiagRow(c, "最近一次调用", lastCall, lastCallAt) { clipboard.setText(AnnotatedString(lastCall)) }
        HorizontalDivider(thickness = 0.5.dp, color = c.dividerLine)
        DiagRow(c, "决策轨迹", trace, traceAt) { clipboard.setText(AnnotatedString(trace)) }
        HorizontalDivider(thickness = 0.5.dp, color = c.dividerLine)
        DiagRow(c, "界面诊断", diag, diagAt) { clipboard.setText(AnnotatedString(diag)) }
    }

    SmallTitle(text = "操作")
    Card(modifier = Modifier.padding(horizontal = 12.dp)) {
        BasicComponent(
            title = "抓取微信界面",
            summary = "让微信进程把当前界面结构回传一次（几秒后刷新看「界面诊断」）",
            onClick = {
                store.requestDiag()
                tick++
            },
        )
        HorizontalDivider(thickness = 0.5.dp, color = c.dividerLine)
        BasicComponent(
            title = "导出诊断包（zip）",
            summary = "环境 / 配置（不含 Key）/ 角色条数 / 界面结构 / 最近一次调用",
            onClick = { exportLauncher.launch("TalkTact-诊断包.zip") },
        )
    }
    exportNote?.let {
        Spacer(Modifier.height(8.dp))
        Text(it, fontSize = 12.sp, color = c.onBackgroundVariant, modifier = Modifier.padding(horizontal = 26.dp))
    }

    SmallTitle(text = "归属地")
    Card(modifier = Modifier.padding(horizontal = 12.dp)) {
        SuperSwitch(
            title = "IP 归属地",
            summary = "「网络信息」里那几项归属地要查一次接口",
            checked = cfg.geoEnabled,
            onCheckedChange = { save(cfg.copy(geoEnabled = it)) },
        )
    }
    Spacer(Modifier.height(24.dp))
}

@Composable
private fun DiagRow(
    c: Colors,
    title: String,
    body: String,
    at: Long,
    onCopy: () -> Unit,
) {
    val head = if (at > 0) formatTime(at) else ""
    val size = if (body.isBlank()) "还没有" else "${body.length} 字"
    BasicComponent(
        title = title,
        summary = if (body.isBlank()) "还没有数据" else "$head · $size · 点一下复制",
        onClick = if (body.isBlank()) null else onCopy,
        rightActions = {
            if (body.isNotBlank()) {
                Text("复制", fontSize = 13.sp, color = c.primary)
            }
        },
    )
}

/**
 * 会话白名单。
 *
 * 写入走 `store.saveWhitelist(enabled, chats)` 这个**窄接口**，不用 `save(整份配置)` ——
 * 这页只动这两个字段，整份覆盖会把别处还没保存的草稿一起冲掉（旧界面当初也是这么分开的）。
 *
 * 候选来自 `store.chatCandidates()`（注入侧读到的会话名）。只列**还没加入**的，
 * 不然一份几百人的名单里混着已加入的，找起来很烦。
 */
@Composable
private fun WhitelistPage(
    c: Colors,
    store: ConfigStore,
    cfg: ConfigData,
    reload: () -> Unit,
    onBack: () -> Unit,
) {
    var tick by remember { mutableIntStateOf(0) }
    var typed by remember { mutableStateOf("") }
    var query by remember { mutableStateOf("") }
    val cands = remember(tick) { store.chatCandidates() }

    fun write(enabled: Boolean, chats: Set<String>) {
        store.saveWhitelist(enabled, chats)
        reload()
    }

    DetailHeader(c, "会话白名单", onBack)
    Text(
        "名单空着 = 哪儿都不工作。",
        fontSize = 12.sp,
        color = c.onBackgroundVariant,
        modifier = Modifier.padding(horizontal = 26.dp),
    )

    SmallTitle(text = "开关")
    Card(modifier = Modifier.padding(horizontal = 12.dp)) {
        SuperSwitch(
            title = "只在这些聊天里工作",
            summary = "关掉的话所有聊天都会自动分析",
            checked = cfg.whitelistEnabled,
            onCheckedChange = { write(it, cfg.whitelist) },
        )
    }

    SmallTitle(text = "已加入（${cfg.whitelist.size}）")
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        if (cfg.whitelist.isEmpty()) {
            Column(Modifier.padding(16.dp)) {
                Text("名单是空的", fontSize = 13.sp, color = c.onSurface)
                Spacer(Modifier.height(3.dp))
                Text("下面可以手动填，也可以从「微信里读到的」里点", fontSize = 12.sp, color = c.onSurfaceVariantSummary)
            }
        } else {
            val names = cfg.whitelist.toList()
            names.forEachIndexed { i, name ->
                BasicComponent(
                    title = name,
                    rightActions = { Text("移除", fontSize = 13.sp, color = Color(0xFFD64545)) },
                    onClick = { write(cfg.whitelistEnabled, cfg.whitelist - name) },
                )
                if (i != names.lastIndex) HorizontalDivider(thickness = 0.5.dp, color = c.dividerLine)
            }
        }
    }

    SmallTitle(text = "手动加一个")
    Column(Modifier.padding(horizontal = 12.dp)) {
        TextField(
            value = typed,
            onValueChange = { typed = it },
            label = "会话名（和微信里显示的一致）",
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(10.dp))
        PrimaryButton(text = "加入名单", enabled = typed.isNotBlank()) {
            write(cfg.whitelistEnabled, cfg.whitelist + typed.trim())
            typed = ""
        }
    }

    // 候选可能有几百个，搜索框是必须的（旧界面那个「拉取到的联系人」页也有）
    val rest = cands.filter { it !in cfg.whitelist && (query.isBlank() || it.contains(query, ignoreCase = true)) }
    SmallTitle(text = "微信里读到的（${rest.size} 个未加入）")
    Column(Modifier.padding(horizontal = 12.dp)) {
        TextField(
            value = query,
            onValueChange = { query = it },
            label = "搜索名字",
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
    Spacer(Modifier.height(8.dp))
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        if (rest.isEmpty()) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    "还没有候选：去微信首页 / 通讯录停两秒，再回来点下面刷新。",
                    fontSize = 12.sp,
                    color = c.onSurfaceVariantSummary,
                )
            }
        } else {
            rest.take(30).forEachIndexed { i, name ->
                BasicComponent(
                    title = name,
                    rightActions = { Text("加入", fontSize = 13.sp, color = c.primary) },
                    onClick = { write(cfg.whitelistEnabled, cfg.whitelist + name) },
                )
                if (i != rest.take(30).lastIndex) HorizontalDivider(thickness = 0.5.dp, color = c.dividerLine)
            }
        }
    }
    Spacer(Modifier.height(10.dp))
    Text(
        "刷新",
        fontSize = 14.sp,
        color = c.primary,
        modifier = Modifier
            .padding(horizontal = 26.dp)
            .clickable { tick++ }
            .padding(vertical = 8.dp),
    )
    Spacer(Modifier.height(24.dp))
}

/**
 * 备份与迁移：导出成 json、装新包后再导入。
 *
 * 导出/导入都用系统的文件选择器（`CreateDocument` / `OpenDocument`）—— 这项目**没有**
 * 存储权限，也不该为了备份去要一个。
 *
 * 导入走 `Backup.import(text, current, currentRoles)`：它逐字段「有才覆盖」，
 * 所以备份里故意没带 API Key 时，也不会把新包里已经填好的 Key 冲掉。导入成功要
 * **同时**写配置和角色（`saveRoles`），只写一半会变成"配置是新的、角色还是旧的"。
 */
@Composable
private fun BackupPage(
    c: Colors,
    store: ConfigStore,
    cfg: ConfigData,
    reload: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var includeKey by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf<String?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri != null) {
            note = runCatching {
                context.contentResolver.openOutputStream(uri)?.use {
                    it.write(Backup.export(store.load(), store.roles(), includeKey).toByteArray(Charsets.UTF_8))
                }
                "已导出"
            }.getOrElse { "导出失败：${it.message}" }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            note = try {
                val text = context.contentResolver.openInputStream(uri)
                    ?.bufferedReader()?.use { it.readText() }.orEmpty()
                val result = Backup.import(text, store.load(), store.roles())
                if (result == null) {
                    "导入失败：不是有效的备份文件"
                } else {
                    store.save(result.config)
                    store.saveRoles(result.roles)
                    reload()
                    "已导入：配置 + ${result.roleCount} 个角色"
                }
            } catch (t: Throwable) {
                "导入失败：${t.message}"
            }
        }
    }

    DetailHeader(c, "备份与迁移", onBack)

    SmallTitle(text = "导出")
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        SuperSwitch(
            title = "导出时包含 API Key",
            summary = "开了的话文件里有明文 Key，别往公开地方放",
            checked = includeKey,
            onCheckedChange = { includeKey = it },
        )
    }
    Spacer(Modifier.height(10.dp))
    PrimaryButton(text = "导出配置", enabled = true, modifier = Modifier.padding(horizontal = 12.dp)) {
        exportLauncher.launch(Backup.FILE_NAME)
    }

    SmallTitle(text = "导入")
    PrimaryButton(text = "选择文件导入", enabled = true, modifier = Modifier.padding(horizontal = 12.dp)) {
        importLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
    }

    note?.let {
        Spacer(Modifier.height(10.dp))
        Text(it, fontSize = 12.sp, color = c.onBackgroundVariant, modifier = Modifier.padding(horizontal = 26.dp))
    }
    Spacer(Modifier.height(24.dp))
}

/**
 * 本地代理：开了之后，聊天内容先发到 127.0.0.1，由 App 带上 Key 再打出去。
 *
 * 开关**立刻落盘 + 立刻起停服务**（`ProxyService.start/stop`）—— 注入侧是按
 * `proxyEnabled` 决定走哪条路的，只写配置不启停等于开了个假开关。
 * 换端口需要重启服务才生效，所以端口是「保存端口」按钮，不是边输边生效。
 */
@Composable
private fun ProxyPage(c: Colors, store: ConfigStore, cfg: ConfigData, onBack: () -> Unit) {
    val context = LocalContext.current
    var portText by remember(cfg.proxyPort) { mutableStateOf(cfg.proxyPort.toString()) }
    val port = portText.toIntOrNull()
    val portOk = port != null && port in 1024..65535
    var note by remember { mutableStateOf<String?>(null) }

    fun apply(next: ConfigData, restart: Boolean) {
        store.save(next)
        if (restart) {
            ProxyService.stop(context)
            if (next.proxyEnabled) ProxyService.start(context)
        }
    }

    DetailHeader(c, "本地代理", onBack)
    Text(
        "聊天内容走本机，由 App 带 Key 转发；Key 不出 App 进程。",
        fontSize = 12.sp,
        color = c.onBackgroundVariant,
        modifier = Modifier.padding(horizontal = 26.dp),
    )

    SmallTitle(text = "开关")
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        SuperSwitch(
            title = "走本地代理",
            summary = "只支持 OpenAI 兼容形态；别的形态请直连",
            checked = cfg.proxyEnabled,
            onCheckedChange = { on ->
                apply(cfg.copy(proxyEnabled = on), restart = true)
                note = if (on) "已开启" else "已关闭"
            },
        )
    }

    SmallTitle(text = "端口")
    Column(Modifier.padding(horizontal = 12.dp)) {
        TextField(
            value = portText,
            onValueChange = { portText = it.filter { ch -> ch.isDigit() }.take(5) },
            label = "端口（1024 - 65535）",
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(10.dp))
        PrimaryButton(
            text = "保存端口",
            enabled = portOk && portText != cfg.proxyPort.toString(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (port != null) {
                apply(cfg.copy(proxyPort = port), restart = cfg.proxyEnabled)
                note = "端口已改为 $port${if (cfg.proxyEnabled) "，服务已重启" else ""}"
            }
        }
    }

    note?.let {
        Spacer(Modifier.height(10.dp))
        Text(it, fontSize = 12.sp, color = c.onBackgroundVariant, modifier = Modifier.padding(horizontal = 26.dp))
    }
    Spacer(Modifier.height(24.dp))
}

/**
 * 按联系人设风格：列表。
 *
 * `cfg.chatStyles` 里 **空列表是合法状态** = 「已添加、但先跟随全局」——
 * 所以这一页不能把空列表当成「没有设置」删掉，添加时也故意写成空列表。
 */
@Composable
private fun ChatStylesPage(
    c: Colors,
    store: ConfigStore,
    cfg: ConfigData,
    onSet: (String, List<String>) -> Unit,
    onOpen: (String) -> Unit,
    onBack: () -> Unit,
) {
    var tick by remember { mutableIntStateOf(0) }
    var pickOpen by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    val cands = remember(tick) { store.chatCandidates() }

    DetailHeader(c, "按联系人设风格", onBack)
    Text(
        "没列在这里的会话，都用「回复风格」里那套。",
        fontSize = 12.sp,
        color = c.onBackgroundVariant,
        modifier = Modifier.padding(horizontal = 26.dp),
    )

    SmallTitle(text = "已设置（${cfg.chatStyles.size}）")
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        if (cfg.chatStyles.isEmpty()) {
            Column(Modifier.padding(16.dp)) {
                Text("还没有单独的设置", fontSize = 13.sp, color = c.onSurface)
                Spacer(Modifier.height(3.dp))
                Text("下面可以选一个人加上", fontSize = 12.sp, color = c.onSurfaceVariantSummary)
            }
        } else {
            val names = cfg.chatStyles.keys.toList()
            names.forEachIndexed { i, name ->
                val ids = cfg.chatStyles[name].orEmpty()
                BasicComponent(
                    title = name,
                    summary = if (ids.isEmpty()) "跟随全局" else "${ids.size} 种（单独）",
                    onClick = { onOpen(name) },
                )
                if (i != names.lastIndex) HorizontalDivider(thickness = 0.5.dp, color = c.dividerLine)
            }
        }
    }

    SmallTitle(text = "添加")
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        BasicComponent(
            title = if (pickOpen) "收起候选" else "从微信读到的会话里选",
            summary = "已添加的不会再出现",
            onClick = { pickOpen = !pickOpen; tick++ },
            rightActions = { Text(if (pickOpen) "收起" else "展开", fontSize = 13.sp, color = c.primary) },
        )
        if (pickOpen) {
            val rest = cands.filter {
                it !in cfg.chatStyles && (query.isBlank() || it.contains(query, ignoreCase = true))
            }
            HorizontalDivider(thickness = 0.5.dp, color = c.dividerLine)
            Column(Modifier.padding(12.dp)) {
                TextField(
                    value = query,
                    onValueChange = { query = it },
                    label = "搜索名字",
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (rest.isEmpty()) {
                HorizontalDivider(thickness = 0.5.dp, color = c.dividerLine)
                Column(Modifier.padding(16.dp)) {
                    Text(
                        "没有可选的了：去微信首页 / 通讯录停两秒再回来展开。",
                        fontSize = 12.sp,
                        color = c.onSurfaceVariantSummary,
                    )
                }
            } else {
                rest.take(30).forEach { name ->
                    HorizontalDivider(thickness = 0.5.dp, color = c.dividerLine)
                    BasicComponent(
                        title = name,
                        onClick = { onSet(name, emptyList()) },
                        rightActions = { Text("加入", fontSize = 13.sp, color = c.primary) },
                    )
                }
            }
        }
    }
    Spacer(Modifier.height(24.dp))
}

/** 单个会话的风格勾选。一个都不勾 = 跟随全局（所以不做「至少选一个」的限制）。 */
@Composable
private fun ChatStyleEditPage(
    c: Colors,
    cfg: ConfigData,
    key: String?,
    onSet: (String, List<String>) -> Unit,
    onRemove: (String) -> Unit,
    onBack: () -> Unit,
) {
    val name = key
    val ids = name?.let { cfg.chatStyles[it] }.orEmpty()

    DetailHeader(c, name ?: "按联系人设风格", onBack)
    if (name == null) {
        SmallTitle(text = "设置")
        Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
            Column(Modifier.padding(16.dp)) {
                Text("没选中会话", fontSize = 13.sp, color = c.onSurface)
            }
        }
        return
    }


    val known = REPLY_STYLES.map { it.id }.toSet() + cfg.replyCustomStyles.map { it.id }
    val all = REPLY_STYLES + cfg.replyCustomStyles
    SmallTitle(text = if (ids.isEmpty()) "当前：跟随全局" else "当前：${ids.size} 种")
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        all.forEachIndexed { i, st ->
            BasicComponent(
                title = st.label,
                summary = st.hint.takeIf { st.hint.isNotBlank() && st.id !in REPLY_STYLES.map { it.id } },
                onClick = {
                    val next = toggledStyleIds(ids, st.id, MAX_REPLIES, known)
                    onSet(name, next)
                },
                rightActions = {
                    if (st.id in ids) {
                        Text("✓", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = c.primary)
                    }
                },
            )
            if (i != all.lastIndex) HorizontalDivider(thickness = 0.5.dp, color = c.dividerLine)
        }
    }
    Spacer(Modifier.height(10.dp))
    Text(
        "清空（改为跟随全局）",
        fontSize = 14.sp,
        color = c.primary,
        modifier = Modifier.padding(horizontal = 26.dp).clickable {
            onSet(name, emptyList())
        }.padding(vertical = 8.dp),
    )

    SmallTitle(text = "危险操作")
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        BasicComponent(
            title = "移除这个联系人",
            summary = "从「按联系人设风格」里去掉，回到跟随全局",
            titleColor = Color(0xFFD64545),
            onClick = { onRemove(name) },
        )
    }
    Spacer(Modifier.height(24.dp))
}

/** 温度：0.0–2.0，步进 0.1。用 − / ＋ 而不是滑杆 —— 这个值需要的是精确到 0.1，滑杆对不准。 */
@Composable
private fun TempRow(c: Colors, value: Double, onChange: (Double) -> Unit) {
    BasicComponent(
        title = "温度",
        summary = "越高越放飞，越低越稳（一般 0.7–1.0）",
        rightActions = {
            Text(
                "−",
                fontSize = 20.sp,
                color = c.primary,
                modifier = Modifier
                    .clickable { onChange((value - 0.1).coerceAtLeast(0.0)) }
                    .padding(horizontal = 10.dp),
            )
            Text(
                "%.1f".format(value),
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = c.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(min = 44.dp),
            )
            Text(
                "＋",
                fontSize = 18.sp,
                color = c.primary,
                modifier = Modifier
                    .clickable { onChange((value + 0.1).coerceAtMost(2.0)) }
                    .padding(horizontal = 10.dp),
            )
        },
    )
}

/**
 * 接口自检：**只看「连不连得上、要多久」**。
 *
 * 发一次最小请求（一句 ping、只让它回 1 个 token），不拼 skill、不看回复内容 ——
 * 模型没按 JSON 回算不上连接问题，那种情况去「试一试」。
 *
 * 判定逻辑**照搬旧界面**（`LlmClient.probe()` + 分级模式下第二套接口单独再测一次 +
 * 记 `saveProbeMs` 给运行时「两路对齐等待」用）。这两处很容易凭感觉写错：
 * 1. 分级模式两路**各自**测，慢的那路决定整卡多久出来；
 * 2. 两路差 > 300ms 要明确提示「快的那路要等慢的」，否则用户会以为快的坏了。
 */
@Composable
private fun SelfCheckPage(c: Colors, store: ConfigStore, cfg: ConfigData, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var tick by remember { mutableIntStateOf(0) }
    var probing by remember { mutableStateOf(false) }
    var verdict by remember { mutableStateOf<String?>(null) }
    var detail by remember { mutableStateOf<String?>(null) }
    var ok by remember { mutableStateOf(false) }

    val host = remember(cfg.baseUrl) { NetInfo.hostOf(cfg.baseUrl) }
    val localIp by produceState<String?>(null, tick) {
        value = withContext(Dispatchers.IO) { NetInfo.localIpv4() }
    }
    val targetIp by produceState<String?>(null, tick) {
        value = withContext(Dispatchers.IO) { NetInfo.resolve(host) }
    }
    val geoUrl = remember(cfg.geoEndpoint) { cfg.geoEndpoint.trim().ifBlank { NetInfo.GEO_ENDPOINT } }
    val localGeo by produceState<Geo?>(null, tick, cfg.geoEnabled, geoUrl) {
        value = if (!cfg.geoEnabled) null else withContext(Dispatchers.IO) { NetInfo.geo(geoUrl) }
    }
    val targetGeo by produceState<Geo?>(null, tick, targetIp, cfg.geoEnabled, geoUrl) {
        value = if (!cfg.geoEnabled || targetIp == null) {
            null
        } else {
            withContext(Dispatchers.IO) { NetInfo.geo(geoUrl, ip = targetIp) }
        }
    }

    fun join(vararg parts: String?): String? =
        parts.filterNotNull().filter { it.isNotBlank() }.joinToString(" · ").ifBlank { null }

    DetailHeader(c, "接口自检", onBack)
    Text(
        "只量连接和首包，不拼 skill、不看回复内容。",
        fontSize = 12.sp,
        color = c.onBackgroundVariant,
        modifier = Modifier.padding(horizontal = 26.dp),
    )

    SmallTitle(text = "网络")
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        BasicComponent(
            title = "本机内网",
            summary = localIp ?: "取不到",
        )
        HorizontalDivider(thickness = 0.5.dp, color = c.dividerLine)
        BasicComponent(
            title = "本机公网",
            summary = if (!cfg.geoEnabled) {
                "归属地查询已关闭"
            } else {
                join(localGeo?.ip, localGeo?.province ?: localGeo?.country, localGeo?.isp) ?: "取不到"
            },
        )
        HorizontalDivider(thickness = 0.5.dp, color = c.dividerLine)
        BasicComponent(
            title = "目标服务器",
            summary = join(targetIp, targetGeo?.province ?: targetGeo?.country) ?: "解析不到",
            rightActions = {
                host?.let { Text(it, fontSize = 12.sp, color = c.onSurfaceVariantSummary, maxLines = 1) }
            },
        )
    }

    SmallTitle(text = "连通性")
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Spacer(Modifier.height(12.dp))
            PrimaryButton(
                text = if (probing) "测试中…" else "开始自检",
                enabled = !probing,
                modifier = Modifier.fillMaxWidth(),
            ) {
                probing = true
                verdict = null
                detail = null
                val conf = store.load()
                scope.launch {
                    var v: String
                    var d: String? = null
                    try {
                        val r = withContext(Dispatchers.IO) { LlmClient(conf).probe() }
                        if (r.totalTokens > 0) store.addUsage(r.totalTokens)
                        ok = true
                        v = "通 · 往返 ${r.totalMs}ms · 模型 ${r.model} · ${r.totalTokens} token"
                        val sb = StringBuilder()
                        sb.append("写回复一路：连接 ")
                            .append(r.connectMs?.let { "${it}ms" } ?: "未测得")
                            .append(" · DNS ${r.dnsMs}ms · 往返 ${r.totalMs}ms")
                        r.targetIp?.let { sb.append(" · 目标 ").append(it) }

                        // 分级模式：第二套接口可能是完全不同的服务商，必须单独测
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
                        d = sb.toString()
                        // 记下来：首页公告栏显示、运行时「两路对齐等待」也用它
                        store.saveProbeMs(r.totalMs, riskMs)
                    } catch (t: Throwable) {
                        ok = false
                        v = "失败：${t.message}"
                        d = (t as? LlmException)?.hint
                    }
                    // 让归属地跟着这次自检重查一遍
                    NetInfo.clearGeoCache()
                    tick++
                    verdict = v
                    detail = d
                    probing = false
                }
            }
            verdict?.let {
                Spacer(Modifier.height(10.dp))
                Text(
                    it,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (ok) c.primary else Color(0xFFD64545),
                )
            }
            detail?.let {
                Spacer(Modifier.height(6.dp))
                Text(it, fontSize = 12.sp, color = c.onSurfaceVariantSummary)
            }
        }
    }
    Spacer(Modifier.height(24.dp))
}

/**
 * 主按钮：直接用官方 `Button`（`cornerRadius` 调大一点、`minHeight` 给到 52dp 好点）。
 *
 * 之前是自己用 `Box + background + 缩放` 拼的 —— 那样按下反馈是假的，
 * 官方组件自带按压态、禁用态和触控反馈，也没有「缩放 + 涟漪」二选一的纠结。
 */
@Composable
private fun PrimaryButton(
    text: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Button(
        text = text,
        onClick = onClick,
        enabled = enabled,
        // `submit = true` 才是主色（primary）填充；默认 false 是次要的浅灰。
        // 这页这些按钮都是「主操作」（保存 / 导出 / 导入 / 开始自检），所以必须开。
        submit = true,
        modifier = modifier.fillMaxWidth(),
        cornerRadius = 20.dp,
        minHeight = 52.dp,
    )
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
