package dev.goutou.wingman.wechat

import dev.goutou.wingman.config.ImageMode

/**
 * 快照 -> 聊天记录。纯函数，不碰 Android API，可直接单测。
 *
 * 方向判定三票制：头像位置(2) / 气泡左右(2) / 气泡颜色(1)。
 * 原版只看气泡颜色 —— 深色模式、换主题、微信改配色都会瞎；而头像左右是微信最稳定的约束。
 */
class ChatParser(
    private val screenWidth: Int,
    private val maxTextLen: Int = 400,
) {

    /**
     * @param imageMode 见 [ImageMode]。只影响「要不要把原图带上」，不影响认字。
     * @param imageDataUrl 这一行的**原图**（data URL），只有直传多模态那两档才会被调用。
     *   同样是按需调用 —— 默认那档一次都不该碰它（重新编码一张图不便宜）。
     * @param imageId 这一行的**图片短身份**（压缩后字节的 sha256 前几位）。
     *   只为**结果缓存键**服务，与 [imageDataUrl] 是两件事（见 [ChatMsg.imageId]）。
     * @param imageText 这一行的图认出来什么字（图片消息才问）。默认为「认不出来」——
     *   注入侧传的是「走本地代理去 App 认字」，单测传的是现成的字符串。
     *   **它抛异常也不会影响解析**：这一行走兜底（回落成 [ATTACHMENT_TEXT]）。
     *
     * ⚠️ `imageText` 必须留在**最后一个参数**：既有调用点（以及 `ChatParserTest` 里那 8 处）
     * 用的是尾随 lambda 写法 `parse(rows) { "认出字" }`，而 **Kotlin 的尾随 lambda 永远绑定到
     * 最后一个参数**。当初把 imageMode 加到末尾，直接把那些调用点的 lambda 顶成了 imageMode
     * （报 `Function0<String>` 与 `ImageMode` 类型不匹配）——真踩过。
     */
    fun parse(
        rows: List<RowSnapshot>,
        imageMode: ImageMode = ImageMode.OCR,
        imageDataUrl: (RowImage) -> String? = { null },
        imageId: (RowImage) -> String? = { null },
        imageText: (RowImage) -> String? = { null },
    ): List<ChatMsg> {
        val out = ArrayList<ChatMsg>(rows.size)
        for (row in rows) {
            val bubble = row.bubble
            // 一行要进上下文，至少得有点「读得出来的东西」：一个气泡，**或者一张图**。
            //
            // ⚠️ 以前这里只认气泡（`row.bubble ?: continue`），于是「找到了图、却没判出气泡」
            // 的行会被**整行丢掉**。而图片行恰恰是最常见的那一类：它一个字都没有 → 没有气泡
            // 容器，头像要是也没被认出来，气泡就是 null。结果图**已经取到并编好了**
            // （状态行会照常写「直传：已备好 1 张图」），上下文里却连 `[图片/表情/语音]`
            // 占位都没有 —— 用户看到的是「我明明选了一律发图，模型却说没收到东西」，
            // 一路往「模型不支持看图」上查，方向全错。真机上就是这么丢的。
            if (bubble == null && row.image == null) continue
            val side = resolveSide(row, bubble)
            if (side == Side.UNKNOWN) continue
            val fromMe = side == Side.ME
            val who = if (fromMe) "" else nickname(row, bubble)

            // 没有气泡时 raw 就是 null —— 这正是「微信那一行一个字都没有」的形态，
            // 下面的附件分支本来就负责它（见 [imageMsg] 的 KDoc）。
            //
            // ⚠️ 微信压在图上的系统提示（「轻触重试」等）要**当成没有文字**：
            // 它出现恰恰说明「这一行有张图，只是微信没加载出来」（见 [RowSnapshot.systemHint]）。
            // 当成文字用有两个后果 —— 上下文里凭空多一句「对方:轻触重试」，
            // 而且这一行被判成**普通文字行**，图就再也没被取过。
            // 读屏那层（`okText`）已经先剔过一次，这里是不依赖读屏实现的第二道。
            val raw = bubble?.text?.takeUnless { isSystemHint(it) }
            // 附件行 = 「一个字都没有」或者微信给的通用占位（[图片] 这种，无障碍描述里也会有）。
            // 问一句「这张图认出来什么字」——认字本身在 App 进程里做，这里可能什么都拿不到。
            //
            // ⚠️ 刻意写成嵌套 if 而不是 `imageMsg(...)?.let { add(...); continue }`：
            // `continue` 落在 `let` 这个 **inline lambda** 里属于实验特性（Kotlin 会直接报
            // 「break continue in inline lambdas is experimental」而编译失败 —— 真踩过）。
            if (raw == null || isGenericAttachment(raw)) {
                val ocr = row.image?.let { runCatching { imageText(it) }.getOrNull() }
                // imageMsg 不会返回 null：附件行**一旦被它接手就完全由它负责**，
                // 「这一行什么都不给模型」也是它自己的结论（见它的 KDoc）。
                imageMsg(row, raw, ocr, fromMe, who, imageDataUrl, imageId, imageMode)?.let { add(out, it) }
            } else {
                addPlain(out, raw, fromMe, who)
            }
        }
        return out
    }

    /** 普通文字消息：清洗、过滤掉超长与界面文案，然后进上下文。 */
    private fun addPlain(out: MutableList<ChatMsg>, raw: String?, fromMe: Boolean, who: String) {
        if (raw == null) return
        val text = clean(raw)
        if (text.isEmpty() || text.length > maxTextLen) return
        if (Chrome.isChrome(text)) return
        add(out, ChatMsg(fromMe, text, who))
    }

    /**
     * 附件行 -> 一条消息。
     *
     * ⚠️ **它一旦接手就完全负责**：附件行永远由它给出结论，绝不返回「交回普通文字路径」
     * 那种 null。原因是一个真踩过的坑 —— 附件行常常是 `raw == null`（微信那一行一个字都没有），
     * 而普通文字路径遇到 `raw == null` 会**直接把整行丢掉**，于是「微信发来一张图」
     * 在上下文里彻底消失（表现为既有测试里那 5 个 IndexOutOfBounds / expected:<1> but was:<0>）。
     *
     * 三个来源的优先级与取舍：
     *
     * 1. **认出来的字**：有就写进正文（`[图片] 认出来的字`）。注意认字和「直传原图」
     *    并不是二选一 —— 字是模型能直接读的文本，图能补上表情与版式，两者一起给最全。
     * 2. **原图**（直传那两档）：⚠️ 关键点是**认不出字时更要给图**。
     *    老逻辑是「没认到字就只剩占位」，而表情包、没有文字的截图恰恰是「认不出字」的那一类 ——
     *    按老逻辑它们永远进不了模型。直传模式存在的意义就是救这一类，所以不能沿用那个条件。
     * 3. **占位**：什么额外信息都没有时留一条占位。原文是 `[图片]` 这种微信自己给的占位就照旧用它
     *    （它比 `[ATTACHMENT_TEXT]` 精确，换成后者是信息倒退）；原文为空则用 `[ATTACHMENT_TEXT]`。
     */
    private fun imageMsg(
        row: RowSnapshot,
        raw: String?,
        ocr: String?,
        fromMe: Boolean,
        who: String,
        imageDataUrl: (RowImage) -> String?,
        imageId: (RowImage) -> String?,
        imageMode: ImageMode,
    ): ChatMsg? {
        // 只有「已经要发这条消息」时才取图 —— 兜底那档要看 OCR 结果决定，所以放这儿。
        val attach = row.image != null && shouldAttachImage(imageMode, ocr)
        val image = if (attach) runCatching { imageDataUrl(row.image) }.getOrNull() else null
        // 图片短身份：只要这一行有图就带上，**与要不要外发无关**。
        // 它服务的是结果缓存键 —— 无论走 OCR 还是直传，两张不同的图都必须得到不同的键，
        // 否则「发了第二张图，回复的还是第一张」。
        val id = if (row.image != null) runCatching { imageId(row.image) }.getOrNull() else null
        val hasOcr = !ocr.isNullOrBlank()
        // 认到字 -> 当成真消息（进上下文、也进角色记录）
        if (hasOcr) {
            return ChatMsg(fromMe, imageMessageText(ocr, maxTextLen), who, imageDataUrl = image, imageId = id)
        }
        // 没认到字但拿到了原图 -> 给模型一张图，它自己会看
        if (image != null) {
            val text = raw ?: ATTACHMENT_TEXT
            return ChatMsg(fromMe, text, who, attachment = text == ATTACHMENT_TEXT, imageDataUrl = image, imageId = id)
        }
        // 什么都没拿到：原文本来就是 `[图片]` 这种占位就照旧用它 —— 它比我们那个更笼统的
        // `[ATTACHMENT_TEXT]` 精确，换成后者是信息倒退。
        //
        // ⚠️ attachment 必须是 `raw == ATTACHMENT_TEXT` 而不是无条件 true：
        // 微信给的 `[图片]` 是「我们知道这是什么，只是内容未知」，它本来就不算「未知占位」
        // （既有测试「占位行没认出字就照旧用原文」钉着这一点）。
        if (raw != null) return ChatMsg(fromMe, raw, who, attachment = raw == ATTACHMENT_TEXT, imageId = id)
        // 原文一个字都没有、又没字也没图 —— 仍要留一条占位（老行为：这一行的附件性质是真的）。
        // 原文一个字都没有、又没字也没图 —— 仍要留一条占位（老行为：这一行的附件性质是真的）。
        // 若在这里返回 null 让调用方走「普通文字」那条路，这一行会因为 raw == null 被整个丢掉，
        // 于是「微信发来一张图」在上下文里**彻底消失**（真踩过：附件行整行不见了）。
        return ChatMsg(fromMe, ATTACHMENT_TEXT, who, attachment = true)
    }

    /**
     * 这一行的图要不要直传给模型。
     *
     * [ImageMode.OCR_FALLBACK] 的判据是「认字没给出有用的东西」——
     * 空串、或者只有零星几个字（缩略图上常见的「只认出半个词」），
     * 这两种情况下本地 OCR 等于没帮上忙，才值得把图外发一次。
     */
    private fun shouldAttachImage(mode: ImageMode, ocr: String?): Boolean = when (mode) {
        ImageMode.OCR -> false
        ImageMode.DIRECT -> true
        ImageMode.OCR_FALLBACK -> ocr.orEmpty().trim().length < FALLBACK_MIN_CHARS
    }

    /**
     * 相邻重复（合并转发、刷屏的相同回复）只留一条，省 token。
     *
     * ⚠️ 比较里必须带上 [ChatMsg.imageDataUrl]：两张**不同的图**配上同样的占位文字
     * （一串表情包都是 `[图片]`）合并掉的话，后面的图就丢了。
     */
    private fun add(out: MutableList<ChatMsg>, msg: ChatMsg) {
        val last = out.lastOrNull()
        val same = last != null &&
            last.fromMe == msg.fromMe &&
            last.text == msg.text &&
            last.who == msg.who &&
            last.imageDataUrl == msg.imageDataUrl
        if (same) return
        out.add(msg)
    }

    private fun resolveSide(row: RowSnapshot, bubble: Bubble?): Side {
        var me = 0
        var other = 0

        // 票 1：头像在左还是右 —— 微信里对方的头像永远在左、我的永远在右
        val nearest = row.avatars.minByOrNull { minOf(it.centerX, screenWidth - it.centerX) }
        if (nearest != null) {
            if (nearest.centerX >= screenWidth * 0.5) me += 2 else other += 2
        }

        // 票 2：气泡靠左还是靠右
        bubble?.centerRatio?.let { if (it >= 0.5) me += 2 else other += 2 }

        // 票 3：气泡颜色（微信自己的绿色）
        when (bubble?.colorSide) {
            Side.ME -> me += 1
            Side.OTHER -> other += 1
            Side.UNKNOWN -> Unit
            null -> Unit
        }

        // 票 4：**这一行的图**落在哪半边 —— 只在上面三票一分都没投出来时才用。
        //
        // 它是给「一个字都没有、头像也没认出来」的图片行准备的：那时气泡是 null、前三票
        // 全是 0，整行本来要被丢掉，可图的位置明明摆在那儿（微信里对方发的图永远靠左）。
        // 只在 0:0 时才用是有意的 —— 它和气泡位置是同一类几何证据，没资格推翻已经明确的
        // 头像 / 气泡判定（真机上出现过「气泡靠左、图却画在右半边」的排版）。
        if (me == other) {
            row.imageRatio?.let { if (it >= 0.5) me += 2 else other += 2 }
        }

        return when {
            me > other -> Side.ME
            other > me -> Side.OTHER
            else -> Side.UNKNOWN
        }
    }

    /** 群聊发言人昵称：同一行、在气泡上方、字号更小的那块文字。没有气泡就没有昵称。 */
    private fun nickname(row: RowSnapshot, bubble: Bubble?): String {
        val top = bubble?.top ?: return ""
        return row.texts
            .filter { it.kind == Kind.NICKNAME && it.top <= top }
            .maxByOrNull { it.top }
            ?.text
            ?.trim()
            .orEmpty()
            .take(24)
    }

    private fun clean(raw: String): String =
        raw.replace("\u200b", "")
            .replace(Regex("[ \\t]+"), " ")
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()

    private companion object {
        /**
         * [ImageMode.OCR_FALLBACK] 的判据：认出来的字少于这个数，就当「本地 OCR 没帮上忙」。
         *
         * 取 4：真实截图上的正文很少只有三个字（那更像缩略图上认出来的半个词）。
         * 这是个体验阀值，不是正确性判据 —— 调大 = 更多图会被外发，调小 = 更少。
         */
        const val FALLBACK_MIN_CHARS = 4
    }
}
