package dev.goutou.wingman.ui

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.TextUnit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.compose.ui.unit.sp
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

internal enum class Level(val label: String) { OK("通过"), WARN("待确认"), BAD("有问题") }

internal data class Check(val title: String, val desc: String, val level: Level, val onClick: (() -> Unit)? = null)

/**
 * 状态等级 → **图形档**状态色（8dp 点 / hero 那个圈 / 卡片描边）。
 *
 * 批 4c：状态色只有这一个出口，别再各写一份 when。
 * 第 7 版又拆了一档：浅色下当**文字**用对比度不够（warn 只有 2.2:1），所以图形用 [Palette.okMark]
 * 这一组，文字用 [levelText] 那一组（更深）。
 */
internal fun levelColor(palette: Palette, level: Level): Color = when (level) {
    Level.OK -> palette.okMark
    Level.WARN -> palette.warnMark
    Level.BAD -> palette.badMark
}

/** 状态等级 → **文字档**状态色（正文里那行说明）。 */
internal fun levelText(palette: Palette, level: Level): Color = when (level) {
    Level.OK -> palette.ok
    Level.WARN -> palette.warn
    Level.BAD -> palette.bad
}

/**
 * 页面顶栏上的小按钮（返回 / 刷新）。
 *
 * 以前这两个动作是纯文字（TextButton），看着像标题的一部分、点起来也没有「按钮」的反馈；
 * 现在统一给它们一个描边框 —— 一眼能看出是能点的东西。
 */
/**
 * 会折叠的玻璃卡片：标题行一直在（带一行「现在是什么」的摘要），点一下才展开内容。
 *
 * 和「运行状态」页那个 [DetailToggle] 是同一套观感，区别是这里直接包成一张卡片 ——
 * 页面上那些「配一次就不常看」的大块内容用它，页面能短一大截。
 */
@Composable
internal fun FoldCard(
    title: String,
    summary: String,
    expanded: Boolean,
    glassAlpha: Float,
    onToggle: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val palette = LocalPalette.current
    val angle by animateFloatAsState(if (expanded) 180f else 0f, tween(180))
    GlassCard(glassAlpha) {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onToggle),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = palette.text)
                Text(summary, fontSize = 13.sp, color = palette.sub)
            }
            Icon(
                Icons.Filled.ExpandMore,
                contentDescription = if (expanded) "收起" else "展开",
                tint = palette.sub,
                modifier = Modifier.rotate(angle),
            )
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            Column { content() }
        }
    }
}

/**
 * 长说明的折叠显示 —— 只做呈现，不参与任何逻辑。
 *
 * 默认最多显示 [collapsedLines] 行；**只有真的被截断**（hasVisualOverflow）
 * 才在下面给一个「展开 / 收起」。短文案不会多出任何按钮，
 * 所以调大调小 [collapsedLines] 只影响观感，不影响功能。
 */
@Composable
internal fun HintText(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 11.sp,
    collapsedLines: Int = 2,
) {
    var expanded by remember { mutableStateOf(false) }
    var overflowed by remember { mutableStateOf(false) }
    Column(modifier) {
        Text(
            text = text,
            fontSize = fontSize,
            color = color,
            maxLines = if (expanded) Int.MAX_VALUE else collapsedLines,
            overflow = TextOverflow.Ellipsis,
            onTextLayout = { result -> if (!expanded) overflowed = result.hasVisualOverflow },
        )
        if (overflowed) {
            Text(
                text = if (expanded) "收起" else "展开",
                fontSize = fontSize,
                fontWeight = FontWeight.Medium,
                color = LocalPalette.current.primary,
                modifier = Modifier
                    .padding(top = 3.dp)
                    .clickable(onClickLabel = if (expanded) "收起这段说明" else "展开这段说明") {
                        expanded = !expanded
                    },
            )
        }
    }
}

/** 一行「标题 + 值」的网络信息。值取不到就显示 null —— 不编。 */
@Composable
internal fun NetRow(label: String, value: String?, hint: String? = null) {
    val palette = LocalPalette.current
    val missing = value.isNullOrBlank()
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 13.sp, color = palette.sub, modifier = Modifier.width(76.dp))
        Column(Modifier.weight(1f)) {
            Text(value ?: "null", fontSize = 13.sp, color = if (missing) palette.warn else palette.text)
            if (!hint.isNullOrBlank()) Text(hint, fontSize = 10.sp, color = palette.sub)
        }
    }
}

/** 把「IP · 省份 · 运营商」拼成一行；全空返回 null（UI 那边显示 null）。 */
internal fun joinInfo(vararg parts: String?): String? =
    parts.filter { !it.isNullOrBlank() }.joinToString(" · ").ifBlank { null }

@Composable
internal fun HeaderButton(text: String, onClick: () -> Unit) {
    val palette = LocalPalette.current
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.height(36.dp),
        shape = RoundedCornerShape(RadiusR1),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
    ) { Text(text, fontSize = 13.sp, color = palette.primary) }
}

@Composable
internal fun CheckRow(check: Check, glassAlpha: Float) {
    val palette = LocalPalette.current
    val color = levelColor(palette, check.level)
    // 状态行是顶层元素（直接躺在 LazyColumn 上），所以用玻璃面板：背后是真实的背景模糊
    GlassSurface(
        shape = RoundedCornerShape(RadiusR2),
        glassAlpha = glassAlpha,
        // 直接铺在渐变上的行也是 L1 —— 统一走 L1 的填充数值（以前自己写一套 0.26/0.18，
        // 结果比卡片还亮，一眼就分成两种材质）
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp),
        onClick = check.onClick,
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            // 批 4c：状态点统一 8dp（原来 10dp / 8dp 各写各的）
            StatusDot(color)
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(check.title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = palette.text)
                Text(check.desc, fontSize = 13.sp, color = palette.sub)
            }
            Text(check.level.label, fontSize = 13.sp, color = levelText(palette, check.level), fontWeight = FontWeight.Medium)
        }
    }
}

/**
 * 「详细状态」的折叠开关。
 *
 * 顶上那张「全部就绪」卡片保持原样不动，7 项明细收在这一行下面 ——
 * 不点开就只占一行，点一下才铺出来。
 */
@Composable
internal fun DetailToggle(
    expanded: Boolean,
    summary: String,
    glassAlpha: Float,
    onClick: () -> Unit,
) {
    val palette = LocalPalette.current
    val angle by animateFloatAsState(if (expanded) 180f else 0f, tween(180))
    GlassSurface(
        shape = RoundedCornerShape(RadiusR2),
        glassAlpha = glassAlpha,
        // 直接铺在渐变上的行也是 L1 —— 统一走 L1 的填充数值（以前自己写一套 0.26/0.18，
        // 结果比卡片还亮，一眼就分成两种材质）
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp),
        onClick = onClick,
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("详细状态", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = palette.text)
                Text(summary, fontSize = 13.sp, color = palette.sub)
            }
            Icon(
                Icons.Filled.ExpandMore,
                contentDescription = if (expanded) "收起" else "展开",
                tint = palette.sub,
                modifier = Modifier.rotate(angle),
            )
        }
    }
}

// ================= 运行状态 =================

/**
 * 高级设置的分组标题：12sp、次级色、上面留 24dp 的呼吸位。
 *
 * 九张卡平铺时是一串并列名词，扫视没有动线；加上「接入 / 生成 / 识别 / 微信内 / 维护与排障」
 * 之后，看到的是五个词而不是九个名词，也更不容易把「要配的」和「排障用的」混在一起。
 * 左对齐到卡片外沿再往右 4dp，跟卡内文字错开半格 —— 看起来是「标题」而不是「正文」。
 */
/**
 * 「＋」小圆标：用在「添加联系人」那张小卡的左边。
 *
 * 单独抽出来，是因为**折叠态和展开态都要用同一个标记** —— 不抽的话两个状态各写一遍，
 * 哪天改一个漏一个，两个状态就长成了两种东西。
 */
@Composable
internal fun AddBadge() {
    val palette = LocalPalette.current
    Box(
        Modifier
            .size(20.dp)
            .clip(CircleShape)
            .background(palette.primary.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center,
    ) {
        Text("＋", fontSize = 12.sp, color = palette.primary, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
internal fun GroupTitle(text: String) {
    val palette = LocalPalette.current
    Text(
        text,
        modifier = Modifier.padding(start = 18.dp, top = 18.dp, bottom = 3.dp),
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        color = palette.sub.copy(alpha = 0.75f),
        letterSpacing = 0.8.sp,
    )
}

/**
 * 来源小标签：一眼看出这份提示词是「内置」的还是「导入 / 自定义」的。
 * 只做染色 + 一个词，不占语义 —— 「现在到底生效的是哪一份」由旁边的名字负责。
 */
@Composable
internal fun SourceChip(skillId: String) {
    // 形态统一交给 StatusChip（chip 底色 alpha 0.14 + 1dp 描边 + 13sp 字），别再手搓一份
    StatusChip(if (skillId == "custom") "导入 / 自定义" else "内置", LocalPalette.current.primary)
}

/**
 * 设置 → 高级设置。
 *
 * 从设置首页搬过来的四块：接口地址、微信内自动分析（含敏感检查）、备份 / 迁移、诊断入口。
 * 它们的共同点是「配一次就不常动」，所以单独一层；诊断再往里一层（三级）。
 * 「会话白名单」卡里的候选人名单同理 —— 拉回来的联系人放 [ChatCandidatesScreen]（也是三级）。
 */
/**
 * 「多久以前」的一句话 —— 设置页那行代理保活诊断用它（issue #7①）。
 *
 * 只有四档（秒 / 分钟 / 小时 / 天）：这里要回答的是「服务是刚起来的、还是一直活着」，
 * 精确到秒没有意义，而「刚刚 / 2 分钟前 / 1 小时前」一眼就能读。
 * [at] 为 0 或负数表示「从来没有过」。[now] 可传是为了让它保持纯函数、能直接单测。
 */
internal fun agoText(at: Long, now: Long = System.currentTimeMillis()): String {
    if (at <= 0L) return "从来没有过"
    val sec = ((now - at) / 1000L).coerceAtLeast(0L)
    return when {
        sec < 5 -> "刚刚"
        sec < 60 -> "${sec} 秒前"
        sec < 3600 -> "${sec / 60} 分钟前"
        sec < 86_400 -> "${sec / 3600} 小时前"
        else -> "${sec / 86_400} 天前"
    }
}

/**
 * 跳到「把本应用加入电池优化白名单」。
 *
 * 为什么必须由用户点一下（issue #7①）：国产 ROM 的后台冻结只认系统的白名单，
 * 代码怎么写都绕不过去 —— 这也是「App 一退到后台代理就没了」最后一公里的唯一解法。
 * 先试**直接请求**那条（一次点击搞定）；有些系统不认、或没装那个 Activity，
 * 就退回打开那个列表页让用户自己找。
 */
internal fun openBatteryWhitelist(context: android.content.Context) {
    val direct = android.content.Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
        .setData(android.net.Uri.parse("package:${context.packageName}"))
        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
    val list = android.content.Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(direct) }
        .recoverCatching { context.startActivity(list) }
}

/** 「TA 是你什么人」的备选。用户也可以直接把自定义的写进「平时的关系」里。 */
internal val RELATIONS = listOf("家人", "恋人", "暧昧", "朋友", "同学", "同事", "上级", "客户", "其他")

/**
 * 「回到前台」的信号：每次 ON_RESUME 自增，给 [produceState] 当重启键。
 *
 * 为什么需要：页面上的那些「注入侧广播回传」的状态（会话名候选、识图结果）都是靠
 * **有上限的轮询**刷新的（5 分钟就停了）。而典型用法是「打开看一眼 → 去微信里试 → 切回来」，
 * 那时候轮询早就停了，页面会一直显示旧值 —— 真机反馈过
 * 「微信里明明认了字，App 里还是『还没试过』」，就是它。
 */
@Composable
internal fun resumedTick(): Int {
    var tick by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { tick++ }
    return tick
}

/**
 * 「贴在界面上的纸条」：白底 + 深色字、完全不透明，扫一眼就能读到，不会和背景混在一起。
 *
 * 和玻璃卡片刻意不同 —— 玻璃是界面的一部分，白卡是**说明书 / 公告**：说明、注意事项、
 * 延迟数字这类「要看清」的东西用它；能塞进玻璃卡的普通内容就别用，不然整页会花。
 */
@Composable
fun WhiteCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(RadiusR2))
            .background(Color.White)
            .padding(12.dp),
        content = content,
    )
}

/** 白底公告栏：一行标题 + 若干行小字（[WhiteCard] 的一种用法）。 */
@Composable
fun NoticeBanner(title: String, lines: List<String>) {
    WhiteCard {
        Text(title, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Color(0xFF1C1B22))
        lines.forEach { line ->
            Text(
                line,
                fontSize = 13.sp,
                color = Color(0xFF55525E),
                modifier = Modifier.padding(top = 3.dp),
            )
        }
    }
}

/** 时间戳 -> 本地 HH:mm（只用在「上次拉取于 …」这种一句话里）。 */
/**
 * 白名单 / 候选共用的一行：不在多选态时右边是一个文字动作；**长按**任意一项进入多选。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun PickRow(
    label: String,
    picking: Boolean,
    selected: Boolean,
    palette: Palette,
    actionLabel: String,
    actionColor: Color,
    onToggle: () -> Unit,
    onLongPress: () -> Unit,
    onAction: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(RadiusR1))
            .combinedClickable(
                onClick = { if (picking) onToggle() },
                onLongClick = onLongPress,
            ),
    ) {
        if (picking) {
            Checkbox(
                checked = selected,
                onCheckedChange = { onToggle() },
                modifier = Modifier.size(26.dp),
            )
            Spacer(Modifier.width(2.dp))
        }
        Text(
            label,
            fontSize = 13.sp,
            color = palette.text,
            modifier = Modifier.weight(1f),
        )
        if (!picking) {
            Text(
                actionLabel,
                fontSize = 13.sp,
                color = actionColor,
                modifier = Modifier
                    .clip(RoundedCornerShape(RadiusR1))
                    .clickable { onAction() }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
    }
}

/** 多选态的顶部：已选几个 / 全选 / 退出多选，外加两个批量动作。 */
@Composable
internal fun PickBar(
    count: Int,
    total: Int,
    palette: Palette,
    primaryLabel: String,
    onPrimary: () -> Unit,
    dangerLabel: String,
    onDanger: () -> Unit,
    onSelectAll: () -> Unit,
    onCancel: () -> Unit,
) {
    Text("已选 $count / $total（长按进入多选，点一下勾选）", fontSize = 13.sp, color = palette.text)
    Spacer(Modifier.height(4.dp))
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            if (total > 0 && count >= total) "取消全选" else "全选",
            fontSize = 13.sp,
            color = palette.primary,
            modifier = Modifier
                .clip(RoundedCornerShape(RadiusR1))
                .clickable { onSelectAll() }
                .padding(horizontal = 6.dp, vertical = 4.dp),
        )
        Text(
            "退出多选",
            fontSize = 13.sp,
            color = palette.sub,
            modifier = Modifier
                .clip(RoundedCornerShape(RadiusR1))
                .clickable { onCancel() }
                .padding(horizontal = 6.dp, vertical = 4.dp),
        )
    }
    Spacer(Modifier.height(4.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        Button(
            onClick = onPrimary,
            enabled = count > 0,
            modifier = Modifier.weight(1f).height(42.dp),
            shape = RoundedCornerShape(RadiusR2),
            colors = ButtonDefaults.buttonColors(containerColor = palette.primary),
        ) { Text(primaryLabel, fontSize = 13.sp) }
        OutlinedButton(
            onClick = onDanger,
            enabled = count > 0,
            modifier = Modifier.weight(1f).height(42.dp),
            shape = RoundedCornerShape(RadiusR2),
        ) { Text(dangerLabel, fontSize = 13.sp, color = palette.bad) }
    }
}

internal fun clockText(at: Long): String =
    java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date(at))
