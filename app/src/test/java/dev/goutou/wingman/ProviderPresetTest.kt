package dev.goutou.wingman

import dev.goutou.wingman.llm.ApiShape
import dev.goutou.wingman.llm.PROVIDER_PRESETS
import dev.goutou.wingman.llm.apiUrl
import dev.goutou.wingman.llm.presetOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 服务商预设。
 *
 * 预设是一张「地址表」，写错一个字在真机上就是 404，而这里没法一家家去试 ——
 * 所以把「拼出来的端点长什么样」钉在单测里：地址不带末尾斜杠、路径不重复、
 * 每种形态都落到自己那条路径上。
 */
class ProviderPresetTest {

    @Test
    fun `id 不重复且能反查`() {
        val ids = PROVIDER_PRESETS.map { it.id }
        assertEquals(ids.size, ids.distinct().size)
        PROVIDER_PRESETS.forEach { assertEquals(it.id, presetOf(it.id)?.id) }
        assertNull(presetOf("不存在的服务商"))
    }

    @Test
    fun `地址都是干净的 https`() {
        PROVIDER_PRESETS.forEach { p ->
            val u = p.baseUrl
            assertTrue("${p.id} 不是 https：$u", u.startsWith("https://"))
            // 末尾斜杠会和 shape.path 拼出双斜杠
            assertTrue("${p.id} 末尾带斜杠：$u", !u.endsWith("/"))
            assertTrue("${p.id} 有空白：$u", u.none { it.isWhitespace() })
        }
    }

    @Test
    fun `拼出来的端点落在各自形态的路径上`() {
        PROVIDER_PRESETS.forEach { p ->
            val url = apiUrl(p.shape, p.baseUrl)
            val tail = when (p.shape) {
                ApiShape.OPENAI, ApiShape.CUSTOM -> "chat/completions"
                ApiShape.RESPONSES -> "responses"
                ApiShape.ANTHROPIC -> "messages"
            }
            assertTrue("${p.id} 拼出来是 $url", url.endsWith("/$tail"))
            // scheme 之后的 // 只该出现在 https:// 这一处
            assertEquals("${p.id} 拼出双斜杠：$url", 1, Regex("//").findAll(url).count())
        }
    }

    @Test
    fun `opencode 两个套餐落在 inference 那套地址上`() {
        // 官方文档（2026-11）：按量 https://opencode.ai/inference，Go 是 /inference/go
        val go = presetOf("opencode-go")!!
        val zen = presetOf("opencode-zen")!!
        assertEquals("https://opencode.ai/inference/go/openai/v1/chat/completions", apiUrl(go.shape, go.baseUrl))
        assertEquals("https://opencode.ai/inference/openai/v1/chat/completions", apiUrl(zen.shape, zen.baseUrl))
        // 三条路径都吃全部付费模型，所以统一走 Chat 兼容；Anthropic 形态的 x-api-key 我们发不出去
        assertEquals(ApiShape.OPENAI, go.shape)
        assertEquals(ApiShape.OPENAI, zen.shape)
    }

    @Test
    fun `预填的模型名不带空白`() {
        PROVIDER_PRESETS.filter { it.model.isNotBlank() }.forEach { p ->
            assertEquals("${p.id} 的模型名有空白：${p.model}", p.model.trim(), p.model)
        }
    }
}
