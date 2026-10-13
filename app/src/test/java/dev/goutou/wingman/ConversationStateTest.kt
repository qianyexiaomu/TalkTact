package dev.goutou.wingman

import dev.goutou.wingman.wechat.*
import org.junit.Assert.*
import org.junit.Test

class ConversationStateTest {
    private val messages = listOf(ChatMsg(false, "好的"))

    @Test fun `same message in different conversations has separate replies and records`() {
        assertNotEquals(
            resultKey(screenIdentity("张三", "屏", messages), "cfg"),
            resultKey(screenIdentity("项目群", "屏", messages), "cfg"),
        )
        assertNotEquals(roleObservationKey("张三", false, "好的"), roleObservationKey("李四", false, "好的"))
        assertEquals(roleObservationKey("张三", false, "好的"), roleObservationKey("张三", false, "好的"))
    }

    /**
     * **回归测试**：角色档案长起来不许让键失效。
     *
     * 以前这把键里混了 `roleContext`（角色档案每 900ms 就可能长一条），于是：
     * 同一屏反复 miss、反复问模型（白烧 token）；用户刚点过的「仍然分析这一条」下一轮就失效
     * （一直弹回风险卡）—— 用户实测报的就是这个。现在键只看「这一屏 + 设置」。
     */
    @Test fun `growing archives must not invalidate the key settings still should`() {
        val screen = screenIdentity("张三", "这一屏", messages)
        assertEquals(resultKey(screen, "cfg"), resultKey(screen, "cfg"))
        assertNotEquals(resultKey(screen, "cfg"), resultKey(screen, "model2"))
    }

    @Test fun `entire transcript including author direction and OCR participates in the screen identity`() {
        val original = screenIdentity("群", "屏", messages)
        for (changed in listOf(
            listOf(ChatMsg(true, "好的")),
            listOf(ChatMsg(false, "好的", "小明")),
            listOf(ChatMsg(false, "好的", attachment = true)),
            listOf(ChatMsg(false, "前文")) + messages,
            listOf(ChatMsg(false, "[图片] 新文字")),
            // 图片短身份也必须参与
            listOf(ChatMsg(false, "好的", imageId = "abc123")),
        )) assertNotEquals(original, screenIdentity("群", "屏", changed))
    }

    /**
     * **回归测试**：两张不同的图不能共用同一个键。
     *
     * 用户实测的现象 —— 连着发两张图，第二张的回复还是第一张的内容。
     * 根因是 [screenIdentity] 以前只哈希 方向/昵称/文本/是否占位，
     * 而两张图的正文都是 `[图片/表情/语音]` 这个占位 → 文本完全相同 → 键相同 →
     * 命中结果缓存，把上一张图的答案直接还回去。
     *
     * 这里刻意把 text/attachment/imageId 造得完全一样，只让 imageId 不同 ——
     * 那正是「两张图」在旧键下的样子：除了图本身，别的都分不出来。
     */
    @Test fun `two different images with identical placeholder text must not share a key`() {
        val first = listOf(ChatMsg(false, "[图片/表情/语音]", attachment = true, imageId = "aaaa1111"))
        val second = listOf(ChatMsg(false, "[图片/表情/语音]", attachment = true, imageId = "bbbb2222"))
        // 文本那一层确实完全一样（证明这个测试测的就是图片身份这一处）
        assertEquals(first[0].text, second[0].text)
        assertEquals(first[0].attachment, second[0].attachment)
        assertNotEquals(
            resultKey(screenIdentity("张三", "屏", first), "cfg"),
            resultKey(screenIdentity("张三", "屏", second), "cfg"),
        )
        // 而同一个 imageId 必须稳定（否则缓存永远 miss、白烧 token）
        assertEquals(
            screenIdentity("张三", "屏", first),
            screenIdentity("张三", "屏", listOf(ChatMsg(false, "[图片/表情/语音]", attachment = true, imageId = "aaaa1111"))),
        )
    }

    @Test fun `length prefixed keys distinguish delimiter text and scopes`() {
        assertNotEquals(conversationDigest("a|b", "c"), conversationDigest("a", "b|c"))
        assertNotEquals(conversationDigest("ab", "c"), conversationDigest("a", "bc"))
        assertNotEquals(roleObservationKey("张三", true, "好的"), roleObservationKey("张三", false, "好的"))
        assertFalse(screenIdentity("张三", "屏", messages).contains("张三"))
    }
}
