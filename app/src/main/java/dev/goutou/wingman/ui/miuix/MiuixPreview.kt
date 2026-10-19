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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.goutou.wingman.config.ConfigData
import dev.goutou.wingman.config.ConfigStore
import dev.goutou.wingman.config.replyPlan
import dev.goutou.wingman.ui.appVersion
import dev.goutou.wingman.ui.moduleActive
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.extra.SuperArrow
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
 * 玻璃导航条是**独立的一份**（[MiuixGlassNavBar]），用的是项目原有的 `GlassSurface` ——
 * 只有它会在背景图上做折射/模糊；Miuix 自己的 NavigationBar 是不透明实心板，对不上参考图。
 */
@Composable
fun MiuixPreviewScreen(store: ConfigStore, onBack: () -> Unit) {
    MiuixTheme(colors = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
        val c = MiuixTheme.colorScheme
        val cfg = remember { store.load() }
        var tab by remember { mutableIntStateOf(0) }

        // **不涂底色**（透明）：下面那层是 App 自己的渐变背景（App() 里的 BackgroundLayer），
        // 玻璃导航条要靠它才有东西可模糊。这里一旦铺上 Miuix 的实色 background，
        // 就等于把渐变盖死 —— 导航条会变成一块「糊在灰底上的半透明板」。
        Box(Modifier.fillMaxSize()) {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .statusBarsPadding()
                    // 给底部导航条留位置：不留的话最后一张卡会被压在半透明玻璃底下
                    .padding(bottom = 108.dp),
            ) {
                when (tab) {
                    0 -> HomeTab(c, store, cfg, onBack)
                    1 -> StatusTab(c, store, cfg)
                    2 -> SettingsTab(c, onBack)
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
                glassAlpha = cfg.glassAlpha,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            ) { tab = it }
        }
    }
}

@Composable
private fun HomeTab(c: Colors, store: ConfigStore, cfg: ConfigData, onBack: () -> Unit) {
    val context = LocalContext.current
    val (calls, tokens) = remember { store.usage() }
    val active = moduleActive(store)
    val styles = cfg.replyPlan.styles.size

    Row(
        Modifier.fillMaxWidth().padding(start = 28.dp, end = 20.dp, top = 22.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("微信军师", fontSize = 30.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(2.dp))
            Text("TalkTact · ${appVersion(context)}", fontSize = 13.sp, color = c.onBackgroundVariant)
        }
        Text(
            "返回",
            fontSize = 15.sp,
            color = c.primary,
            modifier = Modifier.clickable(onClick = onBack).padding(10.dp),
        )
    }

    SmallTitle(text = "运行状态")
    // 正文颜色必须**跟着卡片底色走**：启用时卡是 primaryContainer、未启用换成 secondaryContainer，
    // 两种情况的前景色不一样。之前这里写死 onPrimaryContainer，未启用时就是「灰底浅字」，
    // 截图里几乎看不清 —— 这种错在代码里一眼扫不出来，只能靠看图才发现。
    val fg = if (active) c.onPrimaryContainer else c.onSecondaryContainer
    Card(
        modifier = Modifier.padding(horizontal = 12.dp),
        color = if (active) c.primaryContainer else c.secondaryContainer,
    ) {
        Column(Modifier.padding(horizontal = 18.dp, vertical = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(10.dp)
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
            Spacer(Modifier.height(10.dp))
            Text("模型　${cfg.model.ifBlank { "还没填" }}", fontSize = 14.sp, color = fg)
            Spacer(Modifier.height(4.dp))
            Text(
                "自动分析　${if (cfg.enabled) "开" else "关"}　·　回复风格 $styles 种",
                fontSize = 14.sp,
                color = fg,
            )
            if (!active) {
                Spacer(Modifier.height(6.dp))
                Text("到 LSPosed 里勾选微信，再强行停止微信重开", fontSize = 12.sp, color = fg)
            }
        }
    }

    // 2×2 小信息卡（参考图里「WAN / 网速 / 订阅 / 资源占用」那四块）
    Spacer(Modifier.height(12.dp))
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

    Spacer(Modifier.height(18.dp))
    Text(
        "这是 Miuix 新版界面：底部是液态玻璃导航条（项目原有的 GlassSurface，会真的模糊背景）。" +
            "设置分页与其余页面在后续迁移。",
        fontSize = 12.sp,
        color = c.onBackgroundVariant,
        modifier = Modifier.padding(horizontal = 28.dp),
    )
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
private fun SettingsTab(c: Colors, onBack: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 28.dp, end = 24.dp, top = 22.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("设置", fontSize = 30.sp, fontWeight = FontWeight.SemiBold)
    }

    SmallTitle(text = "常用")
    Card(modifier = Modifier.padding(horizontal = 12.dp)) {
        SuperArrow(title = "外观与背景", summary = "玻璃强度 / 背景图 / 深色", onClick = onBack)
        HorizontalDivider(thickness = 0.5.dp, color = c.dividerLine)
        SuperArrow(title = "接入与模型", summary = "接口地址 / API Key / 模型", onClick = onBack)
        HorizontalDivider(thickness = 0.5.dp, color = c.dividerLine)
        SuperArrow(title = "回复风格", summary = "可多选 + 自定义 + 按联系人", onClick = onBack)
    }
    SmallTitle(text = "微信内")
    Card(modifier = Modifier.padding(horizontal = 12.dp)) {
        SuperArrow(title = "自动分析", summary = "开关 / 频率 / 参考条数", onClick = onBack)
        HorizontalDivider(thickness = 0.5.dp, color = c.dividerLine)
        SuperArrow(title = "会话白名单", summary = "只在这些聊天里工作", onClick = onBack)
        HorizontalDivider(thickness = 0.5.dp, color = c.dividerLine)
        SuperArrow(title = "图片与 OCR", summary = "认字 / 认不出才发图 / 总是发图", onClick = onBack)
    }
    SmallTitle(text = "维护")
    Card(modifier = Modifier.padding(horizontal = 12.dp)) {
        SuperArrow(title = "备份与迁移", summary = "导出 / 导入配置", onClick = onBack)
        HorizontalDivider(thickness = 0.5.dp, color = c.dividerLine)
        SuperArrow(title = "诊断", summary = "决策轨迹 / 抓界面 / 诊断包", onClick = onBack)
    }

    Spacer(Modifier.height(18.dp))
    Text(
        "这些条目现在都回到旧界面（迁移中）。第二阶段会把每个二级页做成独立的 Miuix 页面。",
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
private fun MiniStat(c: Colors, title: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Text(title, fontSize = 12.sp, color = c.onSurfaceVariantSummary)
            Spacer(Modifier.height(4.dp))
            Text(value, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}
