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

    /**
     * 屏身份只看**最后两条**。
     *
     * 这是「往上翻聊天记录不该重新识别」的守门测试：以前整屏消息都算进身份，
     * 于是用户往上翻两行 → 可见集合变了 → 键变了 → 重新问一遍模型。
     */
    @Test fun `screen identity only looks at the last two messages`() {
        val a = ChatMsg(false, "A")
        val b = ChatMsg(true, "B")
        val c = ChatMsg(false, "C")
        val d = ChatMsg(false, "D")
        val base = screenIdentity("群", "屏", listOf(a, b, c, d))

        // 方向 / 发送者 / 图片占位仍然参与身份
        assertNotEquals(base, screenIdentity("群", "屏", listOf(a, b, c, ChatMsg(true, "D"))))
        assertNotEquals(base, screenIdentity("群", "屏", listOf(a, b, c, ChatMsg(false, "D", "小明"))))
        assertNotEquals(base, screenIdentity("群", "屏", listOf(a, b, c, ChatMsg(false, "D", attachment = true))))

        // 往上翻：前面多了历史消息、最后两条没变 → 身份不变（不重新识别）
        assertEquals(base, screenIdentity("群", "屏", listOf(ChatMsg(false, "更早"), a, b, c, d)))
        assertEquals(
            base,
            screenIdentity("群", "屏", listOf(ChatMsg(false, "更早1"), ChatMsg(false, "更早2"), a, b, c, d)),
        )

        // 来了新消息 → 身份变（应该重新识别）
        assertNotEquals(base, screenIdentity("群", "屏", listOf(a, b, c, d, ChatMsg(false, "E"))))
    }

    /** 账本：把每轮读到的窗口拼起来，不重复、不乱序。 */
    @Test fun `history merges overlapping windows without duplicates`() {
        val h = ChatHistory()
        val b = ChatMsg(false, "B")
        val c = ChatMsg(true, "C")
        val d = ChatMsg(false, "D")
        val e = ChatMsg(false, "E")

        assertEquals(listOf("B", "C", "D"), h.merge("张三", listOf(b, c, d)).map { it.text })
        // 新消息到达：窗口右移一格，账本应该接上而不是重复 B C D
        assertEquals(listOf("B", "C", "D", "E"), h.merge("张三", listOf(c, d, e)).map { it.text })
        // 往上翻（窗口左移）：账本前面接上更早的消息
        assertEquals(
            listOf("A", "B", "C", "D", "E"),
            h.merge("张三", listOf(ChatMsg(false, "A"), b, c)).map { it.text },
        )
        // 窗口落在账本中间（翻上去又翻回来）：前后都要保住，不能把 E 丢了
        assertEquals(
            listOf("A", "B", "C", "D", "E"),
            h.merge("张三", listOf(b, c)).map { it.text },
        )
    }

    @Test fun `history resets when the conversation changes`() {
        val h = ChatHistory()
        h.merge("张三", listOf(ChatMsg(false, "A"), ChatMsg(false, "B")))
        assertEquals(listOf("X"), h.merge("李四", listOf(ChatMsg(false, "X"))).map { it.text })
    }

    /**
     * 找不到锚点（这一屏和账本完全没重叠）时**不动账本**。
     *
     * 如果这时把新窗口追加到末尾，账本的 `last()` 就会变成一条历史消息 ——
     * 接着分析的就是错的对象（把几天前的话当成刚收到的新消息）。
     */
    @Test fun `history keeps its tail when a window does not overlap`() {
        val h = ChatHistory()
        h.merge("张三", listOf(ChatMsg(false, "A"), ChatMsg(false, "B")))
        assertEquals(listOf("A", "B"), h.merge("张三", listOf(ChatMsg(false, "X"), ChatMsg(false, "Y"))).map { it.text })
    }

    @Test fun `history is capped`() {
        val h = ChatHistory(max = 5)
        h.merge("张三", (1..4).map { ChatMsg(false, "m$it") })
        val out = h.merge("张三", (4..9).map { ChatMsg(false, "m$it") })
        assertEquals(5, out.size)
        assertEquals("m9", out.last().text)
    }

    @Test fun `length prefixed keys distinguish delimiter text and scopes`() {
        assertNotEquals(conversationDigest("a|b", "c"), conversationDigest("a", "b|c"))
        assertNotEquals(conversationDigest("ab", "c"), conversationDigest("a", "bc"))
        assertNotEquals(roleObservationKey("张三", true, "好的"), roleObservationKey("张三", false, "好的"))
        assertFalse(screenIdentity("张三", "屏", messages).contains("张三"))
    }
}
