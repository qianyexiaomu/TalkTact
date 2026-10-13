package dev.goutou.wingman

import dev.goutou.wingman.config.ImageMode
import dev.goutou.wingman.wechat.ATTACHMENT_TEXT
import dev.goutou.wingman.wechat.AvatarNode
import dev.goutou.wingman.wechat.Bubble
import dev.goutou.wingman.wechat.ChatMsg
import dev.goutou.wingman.wechat.ChatParser
import dev.goutou.wingman.wechat.Kind
import dev.goutou.wingman.wechat.RowImage
import dev.goutou.wingman.wechat.RowSnapshot
import dev.goutou.wingman.wechat.Side
import dev.goutou.wingman.wechat.TextNode
import dev.goutou.wingman.llm.asTranscript
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「图片怎么交给模型」三档的行为（见 [ImageMode]）。
 *
 * 这一层最容易出错的不是取图，而是**开关的语义**：
 * 默认档必须一个字节都不变（老用户升上来行为不动），
 * 而「认不出才发」那档的判据要能说清（认出来的字太少也算没认出来）。
 */
class ImageModeTest {

    private val w = 1080
    private val parser = ChatParser(w)

    private fun row(text: String?, top: Int, image: RowImage? = null): RowSnapshot {
        val texts = ArrayList<TextNode>()
        text?.let { texts.add(TextNode(it, top, Kind.BUBBLE, 36f)) }
        return RowSnapshot(
            Bubble(text, Side.OTHER, 0.35, top),
            texts,
            listOf(AvatarNode(60, top, 96)),
            image,
        )
    }

    private fun img() = RowImage(600, 800) { ByteArray(0) }

    /**
     * 一行「图没加载出来」的行：微信把「轻触重试」压在图上，那个提示被读成了气泡文字。
     *
     * ⚠️ 必须带上 [img]：这个提示出现恰恰证明**这一行有张图**，而它以前正因为「有文字」
     * 被判成普通文字行 → 图从来没被取过。这里把图备上，钉住「提示不许连累图」。
     */
    private fun hintRow(top: Int): RowSnapshot = RowSnapshot(
        bubble = Bubble("轻触重试", Side.OTHER, 0.35, top),
        texts = listOf(TextNode("轻触重试", top, Kind.BUBBLE, 28f)),
        avatars = listOf(AvatarNode(60, top, 96)),
        image = img(),
    )

    private val dataUrl = "data:image/jpeg;base64,AAAA"

    /** 这一档跑一次解析，返回唯一的（或最后一条）消息。 */
    private fun parseOne(mode: ImageMode, ocr: String?, withImage: Boolean = true): ChatMsg {
        // raw = null = 微信那一行**一个字都没有**（附件行的更严苛形态）：
        // 它逼着解析器「没有原文可退回」时也必须留下占位，而不是把整行丢掉。
        val rows = listOf(row(null, 300, if (withImage) img() else null))
        val msgs = parser.parse(
            rows = rows,
            imageText = { ocr },
            imageDataUrl = { dataUrl },
            imageMode = mode,
        )
        return msgs.last()
    }

    @Test
    fun `默认档一个图都不带`() {
        val m = parseOne(ImageMode.OCR, "图里的字")
        assertNull("默认档不该带原图", m.imageDataUrl)
        assertTrue("认到字了就写进正文", m.text.contains("图里的字"))
    }

    @Test
    fun `默认档认不出字时回落成占位`() {
        val m = parseOne(ImageMode.OCR, null)
        assertEquals(ATTACHMENT_TEXT, m.text)
        assertTrue("仍然是「内容未知」的占位", m.attachment)
        assertNull(m.imageDataUrl)
    }

    /**
     * 这条钉的是一个**真踩过的坑**：老逻辑写成 `raw == null || !ocr.isNullOrBlank()` 才进分支，
     * 于是「认不出字的图」被整个挡在门外 —— 而表情包、没有文字的截图恰恰是这一类，
     * 它们永远进不了模型。直传模式存在的意义就是救这一类。
     */
    @Test
    fun `直传时认不出字的图照样能进模型`() {
        val m = parseOne(ImageMode.DIRECT, null)
        // raw = null → 正文用通用占位；**关键是图带上了**（那才是直传要解决的问题）
        assertEquals(ATTACHMENT_TEXT, m.text)
        assertEquals("认不出字才是直传最该处理的情况", dataUrl, m.imageDataUrl)
    }

    @Test
    fun `一律直传时无论认没认到字都带图`() {
        val got = parseOne(ImageMode.DIRECT, "图里的字")
        assertEquals(dataUrl, got.imageDataUrl)
        assertTrue("认到字时是真消息", !got.attachment)
        assertEquals("[图片] 图里的字", got.text)
    }

    @Test
    fun `回退档在认不出字时带图`() {
        assertEquals(dataUrl, parseOne(ImageMode.OCR_FALLBACK, null).imageDataUrl)
        assertEquals(dataUrl, parseOne(ImageMode.OCR_FALLBACK, "  ").imageDataUrl)
    }

    @Test
    fun `回退档在认出的字够多时不带图`() {
        // 4 个字以上算「本地 OCR 帮上忙了」，不必外发
        assertNull(parseOne(ImageMode.OCR_FALLBACK, "周末有空吗").imageDataUrl)
        assertEquals("周末有空吗", parseOne(ImageMode.OCR_FALLBACK, "周末有空吗").text.substringAfter("[图片] "))
    }

    @Test
    fun `回退档把零星几个字也算没认出来`() {
        // 缩略图上常见「只认出半个词」，这种等于没帮上忙 —— 该把图发出去
        assertEquals(dataUrl, parseOne(ImageMode.OCR_FALLBACK, "你好").imageDataUrl)
        assertEquals(dataUrl, parseOne(ImageMode.OCR_FALLBACK, "abc").imageDataUrl)
    }

    @Test
    fun `这一行没有图时三档都只是纯文字`() {
        for (mode in ImageMode.values()) {
            val m = parseOne(mode, null, withImage = false)
            // raw = null（微信那一行没有文字）→ 用我们那个通用占位
            assertEquals(ATTACHMENT_TEXT, m.text)
            assertNull("没有图可取，$mode 不该凭空造一个", m.imageDataUrl)
        }
    }

    /**
     * 这条是回归测试：附件行**一个字都没有**（raw == null）、又没认到字也没拿到图时，
     * 必须仍然留下一条占位，而不是把整行丢掉。
     *
     * 上一版 `imageMsg` 在这种情况返回 null 表示「交回普通文字路径」，而那条路会因为
     * raw == null 直接丢行 —— 于是「微信发来一张图」在上下文里彻底消失，
     * 表现为 ChatParserTest 里那 5 个 IndexOutOfBounds / expected:<1> but was:<0>。
     */
    @Test
    fun `附件行一个字都没有时也留下占位而不是丢掉整行`() {
        val m = parseOne(ImageMode.OCR, null)
        assertEquals(ATTACHMENT_TEXT, m.text)
        assertTrue(m.attachment)
    }

    @Test
    fun `取图抛异常时不影响解析`() {
        // raw = null（微信发来的图，那一行没有文字），直传模式去取图时抛异常
        val rows = listOf(row(null, 300, img()))
        val msgs = parser.parse(
            rows = rows,
            imageMode = ImageMode.DIRECT,
            imageDataUrl = { throw IllegalStateException("位图已回收") },
        ) { null }
        assertEquals(1, msgs.size)
        assertNull("取图失败就退回占位，绝不连累整轮分析", msgs[0].imageDataUrl)
        assertEquals(ATTACHMENT_TEXT, msgs[0].text)
    }

    // ---------- 「一个字都没有的图片行」不能整行丢掉 ----------

    /**
     * 真机上就是这么丢的：图片行一个字都没有 → 没有气泡容器；头像要是也没被认出来，
     * `row.bubble` 就是 null，而老代码第一句就是 `row.bubble ?: continue` ——
     * 图**已经取到并编好**（状态行照常写「直传：已备好 1 张图」），上下文里却连占位都没有。
     * 用户看到的是「我明明选了一律发图，模型却说没收到东西」，一路往「模型不支持看图」上查。
     *
     * 现在这种行**只要找到了图就留下**，方向由图控件落在哪半边决定（微信里对方的图永远靠左）。
     */
    @Test
    fun `判不出气泡的图片行照样进上下文，方向由图的位置决定`() {
        val rows = listOf(RowSnapshot(bubble = null, image = img(), imageRatio = 0.2))
        val msgs = parser.parse(
            rows = rows,
            imageMode = ImageMode.DIRECT,
            imageText = { null },
            imageDataUrl = { dataUrl },
        )
        assertEquals("找到了图就不该整行丢掉", 1, msgs.size)
        assertEquals("靠左的图 = 对方发的", false, msgs[0].fromMe)
        assertEquals(dataUrl, msgs[0].imageDataUrl)
        assertEquals(ATTACHMENT_TEXT, msgs[0].text)
    }

    /** 同一条规则的另一半：靠右的图 = 我发的。 */
    @Test
    fun `判不出气泡时靠右的图算我发的`() {
        val msgs = parser.parse(
            rows = listOf(RowSnapshot(bubble = null, image = img(), imageRatio = 0.85)),
            imageMode = ImageMode.DIRECT,
            imageText = { null },
            imageDataUrl = { dataUrl },
        )
        assertEquals(1, msgs.size)
        assertTrue(msgs[0].fromMe)
    }

    /**
     * 「图的位置」只是**最后一票**：气泡已经指明方向时不许它翻案。
     *
     * 真机上出现过「气泡靠左、图偏偏画在右半边」的排版 —— 那时若让图也投一票，
     * 两票打平 → 判不出方向 → 整行丢掉，反而比只用气泡更糟。
     */
    @Test
    fun `气泡已经指明方向时不会被图的位置翻案`() {
        val rows = listOf(
            RowSnapshot(
                bubble = Bubble("帮我看看这个", Side.UNKNOWN, 0.35, 300),
                image = img(),
                imageRatio = 0.9,
            ),
        )
        val msgs = parser.parse(
            rows = rows,
            imageMode = ImageMode.DIRECT,
            imageText = { null },
            imageDataUrl = { dataUrl },
        )
        assertEquals(1, msgs.size)
        assertEquals("气泡靠左 = 对方，不能被图的位置翻过来", false, msgs[0].fromMe)
    }

    /** 什么证据都没有的行仍然要丢掉：不猜（老行为一点都不能松）。 */
    @Test
    fun `既没气泡也没图的行仍然丢掉`() {
        assertTrue(parser.parse(listOf(RowSnapshot(null)), imageMode = ImageMode.DIRECT).isEmpty())
    }

    // ---------- 正文里的 [图N] 标记 ----------

    @Test
    fun `没有图时正文与以前逐字一致`() {
        val msgs = listOf(
            ChatMsg(fromMe = false, text = "在吗"),
            ChatMsg(fromMe = true, text = "在"),
        )
        // 关键：不能因为加了新参数就顺手改动纯文字会话的正文（会打掉服务端前缀缓存）
        assertEquals("对方: 在吗\n我: 在", msgs.asTranscript(emptyMap()))
    }

    @Test
    fun `带图的消息标上图片序号`() {
        val msgs = listOf(
            ChatMsg(fromMe = false, text = "看这个", imageDataUrl = dataUrl),
            ChatMsg(fromMe = true, text = "我看看"),
            ChatMsg(fromMe = false, text = "还有这个", imageDataUrl = dataUrl),
        )
        // markers: 第 0 条 -> 图1，第 2 条 -> 图2（按对话顺序编号）
        val out = msgs.asTranscript(mapOf(0 to 1, 2 to 2))
        assertEquals("对方: [图1] 看这个\n我: 我看看\n对方: [图2] 还有这个", out)
    }

    @Test
    fun `内容重复的两条不会被标错`() {
        // 以前用 indexOf 找下标，重复内容会全部指向第一条 —— 这条钉住那个 bug
        val msgs = listOf(
            ChatMsg(fromMe = false, text = "好"),
            ChatMsg(fromMe = false, text = "好", imageDataUrl = dataUrl),
        )
        val out = msgs.asTranscript(mapOf(1 to 1))
        assertEquals("对方: 好\n对方: [图1] 好", out)
    }

    /**
     * 同文字的**不同图**不能被去重合并掉。
     *
     * 一串表情包的文字都是 `[图片]`，若去重只看文字，后面的图全被吃掉 ——
     * 而表情包恰恰是这次要让模型看到的东西。
     */
    @Test
    fun `文字相同但图片不同的相邻两行都要保留`() {
        val urlA = "data:image/jpeg;base64,AAAA"
        val urlB = "data:image/jpeg;base64,BBBB"
        val rows = listOf(
            row("[图片]", 300, RowImage(600, 800) { ByteArray(0) }),
            row("[图片]", 400, RowImage(600, 800) { ByteArray(0) }),
        )
        var n = 0
        val msgs = parser.parse(
            rows = rows,
            imageText = { null },
            imageDataUrl = { if (n++ == 0) urlA else urlB },
            imageMode = ImageMode.DIRECT,
        )
        assertEquals("两张图都该留下", 2, msgs.size)
        assertEquals(urlA, msgs[0].imageDataUrl)
        assertEquals(urlB, msgs[1].imageDataUrl)
    }

    // ---------- 微信压在图上的「轻触重试」不是聊天内容 ----------

    /**
     * 「轻触重试」是微信在**图没加载出来**时压在图上的提示（网络差 / 原图没下完），
     * 它是一个真真切切的 TextView，被读到之后会变成「对方:轻触重试」混进上下文 ——
     * 模型于是顺着它答非所问（「对方让你重试？」）。
     *
     * 判定只认**精确匹配**：用户在聊天里真的打「轻触重试」四个字是有效对话，不该被吃掉。
     */
    @Test
    fun `轻触重试这类系统提示不会被当成对方说的话`() {
        val rows = listOf(
            row("帮我看看这个", 300),
            hintRow(400),
        )
        val msgs = parser.parse(rows = rows, imageMode = ImageMode.OCR, imageText = { null })
        assertEquals(2, msgs.size)
        // ⚠️ `ChatMsg.text` 是**裸正文**，「对方:」前缀是 asTranscript 才加的 ——
        //    这里断言错格式会得到一条看似合理的失败，而根因跟被测行为毫无关系。
        assertEquals("帮我看看这个", msgs[0].text)
        assertTrue(
            "系统提示不该出现在上下文里，实际=「${msgs[1].text}」",
            !msgs[1].text.contains("轻触重试"),
        )
        // 整段转写里也不该有它（这才是真正发给模型的形态）
        assertTrue(
            "转写里不该有系统提示",
            !msgs.asTranscript(emptyMap()).contains("轻触重试"),
        )
    }

    /**
     * 同一个提示还有更要命的一层：它出现恰恰说明「这一行有张图」。
     *
     * 所以这一行必须仍然被当成**图片行**处理 —— 图还得有机会发出去。
     * 这条钉的就是它：以前「有文字就不找图」，系统提示一占位，图就再也没被取过，
     * 现象和「对方根本没发图」一模一样。
     */
    @Test
    fun `压着轻触重试的行仍然按图片行处理，图照样发出去`() {
        val msgs = parser.parse(
            rows = listOf(hintRow(400)),
            imageMode = ImageMode.DIRECT,
            imageText = { null },
            imageDataUrl = { dataUrl },
        )
        assertEquals(1, msgs.size)
        assertEquals("图必须照发", dataUrl, msgs[0].imageDataUrl)
        assertEquals(ATTACHMENT_TEXT, msgs[0].text)
        assertFalse(msgs[0].fromMe)
    }

    /** 用户真的在聊天里打出「轻触重试」时，那句话是有效上下文，必须留着。 */
    @Test
    fun `聊天里真的提到轻触重试不会被误伤`() {
        val msgs = parser.parse(listOf(row("轻触重试也不管用", 300)), imageMode = ImageMode.OCR)
        assertEquals(1, msgs.size)
        assertEquals("轻触重试也不管用", msgs[0].text)
    }

    /**
     * 这一行压根没有图时，系统提示就该彻底消失。
     *
     * ⚠️ 精确的期望是：留下一条 `[图片/表情/语音]` **占位、但不带图**（占位是「这一行的性质是附件」
     * 这个结论，与有没有取到字节无关）。要挡的是**凭空多一句「对方:轻触重试」**，
     * 以及在没有图的情况下靠这个提示编出一张图。
     */
    @Test
    fun `没有图的轻触重试行不会凭空多出系统提示或图片`() {
        val rows = listOf(
            RowSnapshot(
                bubble = Bubble("轻触重试", Side.OTHER, 0.35, 400),
                texts = listOf(TextNode("轻触重试", 400, Kind.BUBBLE, 28f)),
                avatars = listOf(AvatarNode(60, 400, 96)),
            ),
        )
        val msgs = parser.parse(rows = rows, imageMode = ImageMode.DIRECT, imageDataUrl = { dataUrl })
        assertTrue("没有图就不该凭空造出一张图", msgs.none { it.imageDataUrl != null })
        assertTrue(
            "系统提示不该以任何形式出现在上下文里，实际=「${msgs.joinToString { it.text }}」",
            msgs.none { it.text.contains("轻触重试") },
        )
    }
}
