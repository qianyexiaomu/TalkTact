package dev.goutou.wingman.ui.miuix

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.goutou.wingman.config.ConfigStore
import dev.goutou.wingman.config.replyPlan
import dev.goutou.wingman.ui.appVersion
import dev.goutou.wingman.ui.moduleActive
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.extra.SuperArrow
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.darkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme
import top.yukonga.miuix.kmp.utils.HorizontalDivider

/**
 * Miuix 预览页（第一步：只验证「依赖能用 + 观感」）。
 *
 * 为什么先做成一个**独立预览页**而不是直接换掉整套界面：
 * Miuix 的版本被工具链锁在 0.2.9（见 `app/build.gradle.kts` 里那段注释），
 * 先把依赖、主题、组件在这个项目里跑通，再逐页迁移 —— 不然一处 API 猜错就是整包编不过。
 *
 * 布局参考 KernelSU 的首页：**大标题 + 状态卡 + 用量卡 + 入口列表**，
 * 而不是这个项目原来的「一长串玻璃卡片」。
 */
@Composable
fun MiuixPreviewScreen(store: ConfigStore, onBack: () -> Unit) {
    MiuixTheme(colors = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
        val c = MiuixTheme.colorScheme
        val context = LocalContext.current
        val cfg = remember { store.load() }
        val (calls, tokens) = remember { store.usage() }
        val active = moduleActive(store)
        val styles = cfg.replyPlan.styles.size

        Column(
            Modifier
                .fillMaxSize()
                .background(c.background)
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .padding(bottom = 40.dp),
        ) {
            // ── 顶部：大标题（KSU 风：左边标题+副标题，右边一个轻量返回） ──
            Row(
                Modifier.fillMaxWidth().padding(start = 28.dp, end = 20.dp, top = 22.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("微信军师", fontSize = 30.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "TalkTact · ${appVersion(context)}",
                        fontSize = 13.sp,
                        color = c.onBackgroundVariant,
                    )
                }
                Text(
                    "返回",
                    fontSize = 15.sp,
                    color = c.primary,
                    modifier = Modifier.clickable(onClick = onBack).padding(10.dp),
                )
            }

            // ── 运行状态 ──
            SmallTitle(text = "运行状态")
            Card(modifier = Modifier.padding(horizontal = 12.dp)) {
                BasicComponent(
                    title = "模块",
                    summary = if (active) "已启用，微信侧已加载" else "未启用：到 LSPosed 里勾选微信",
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
                    summary = if (cfg.enabled) "在聊天页自动出候选" else "已关闭，只手动识别",
                    rightActions = {
                        Text(
                            if (cfg.enabled) "开" else "关",
                            fontSize = 14.sp,
                            color = c.onSurfaceVariantSummary,
                        )
                    },
                )
                HorizontalDivider(thickness = 0.5.dp, color = c.dividerLine)
                BasicComponent(
                    title = "模型",
                    summary = cfg.model.ifBlank { "还没填" },
                    rightActions = {
                        Text("$styles 种风格", fontSize = 14.sp, color = c.onSurfaceVariantSummary)
                    },
                )
            }

            // ── 用量 ──
            SmallTitle(text = "用量")
            Card(modifier = Modifier.padding(horizontal = 12.dp)) {
                BasicComponent(
                    title = "调用次数",
                    rightActions = {
                        Text("$calls", fontSize = 15.sp, color = c.onSurface)
                    },
                )
                HorizontalDivider(thickness = 0.5.dp, color = c.dividerLine)
                BasicComponent(
                    title = "累计 tokens",
                    rightActions = {
                        Text(tokens.toString(), fontSize = 15.sp, color = c.onSurface)
                    },
                )
            }

            // ── 入口列表（设置分页的样子先在这里试） ──
            SmallTitle(text = "设置")
            Card(modifier = Modifier.padding(horizontal = 12.dp)) {
                SuperArrow(
                    title = "外观与背景",
                    summary = "玻璃强度 / 背景图 / 深色",
                    onClick = onBack,
                )
                HorizontalDivider(thickness = 0.5.dp, color = c.dividerLine)
                SuperArrow(
                    title = "接入与模型",
                    summary = "接口地址 / API Key / 模型",
                    onClick = onBack,
                )
                HorizontalDivider(thickness = 0.5.dp, color = c.dividerLine)
                SuperArrow(
                    title = "微信内自动分析",
                    summary = "白名单 / 频率 / 敏感内容",
                    onClick = onBack,
                )
                HorizontalDivider(thickness = 0.5.dp, color = c.dividerLine)
                SuperArrow(
                    title = "备份与诊断",
                    summary = "导出配置 / 诊断包 / 决策轨迹",
                    onClick = onBack,
                )
            }

            Spacer(Modifier.height(18.dp))
            Text(
                "这是 Miuix 预览：上面的卡片都是真组件。设置分页与其余页面的迁移在下一步。",
                fontSize = 12.sp,
                color = c.onBackgroundVariant,
                modifier = Modifier.padding(horizontal = 28.dp),
            )
        }
    }
}
