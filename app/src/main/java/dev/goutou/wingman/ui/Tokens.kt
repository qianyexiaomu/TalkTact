package dev.goutou.wingman.ui

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 字号：只留**有语义**的几档。
 *
 * 之前全站是直接把数字写进 `fontSize`：`13.sp` 出现 183 次、`17.sp` 31 次，
 * 中间还夹着 12/14/15/16 各几处 —— 想改「正文大一号」时根本没法一处改完，
 * 而且「这两个 13 是不是同一个意思」每次都得重新判断。
 *
 * 约定：
 * - 新代码用这里的常量；**一次性的特殊字号**（22/24 那种大标题）继续写字面值，别为它造名字；
 * - 这几档的值就是原来最常用的那几个，替换是**等值**的 —— 换完之后界面一个像素都不该变。
 */
object TextSize {
    /** 正文 / 说明（原 13.sp，183 处）。 */
    val body = 13.sp

    /** 卡片标题（原 17.sp，31 处）。 */
    val cardTitle = 17.sp

    /** 小节强调（原 15.sp，12 处）。 */
    val emphasis = 15.sp

    /** 小字：输入框 label、按钮里的次要说明（原 12.sp，10 处）。 */
    val small = 12.sp

    /** 注脚：时间、来源这种「扫一眼就够」的（原 10.sp，13 处）。 */
    val caption = 10.sp
}

/**
 * 纵向间距：卡片里实际只用到这几档（4 / 6 / 8 / 10 占 139 处）。
 *
 * 命名用尺寸档而不是语义（「文字间距」「块间距」那种）—— 因为同一个 8dp 在这里是
 * 「标题与正文之间」、在那里是「两个按钮之间」，硬给它起个语义名反而会误导。
 */
object Space {
    val xs = 4.dp
    val sm = 6.dp
    val md = 8.dp
    val lg = 10.dp
    val xl = 12.dp
    val xxl = 14.dp
}
