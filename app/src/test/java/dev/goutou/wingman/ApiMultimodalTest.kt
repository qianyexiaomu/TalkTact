package dev.goutou.wingman

import dev.goutou.wingman.llm.ApiShape
import dev.goutou.wingman.llm.Json
import dev.goutou.wingman.llm.asArr
import dev.goutou.wingman.llm.asStr
import dev.goutou.wingman.llm.at
import dev.goutou.wingman.llm.buildApiRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 图片直传给多模态模型时，请求体长什么样。
 *
 * 两条最要紧的：
 * 1. **没有图时必须与以前逐字节一致** —— 纯文字会话要吃到服务端的前缀缓存
 *    （OpenAI / DeepSeek 按前缀命中，价差一个数量级）。所以 content 仍然是**字符串**，
 *    不能顺手统一改成数组。
 * 2. 有图时 content 是 OpenAI 那套内容数组，文字在前、图在后，且带 `detail`。
 */
class ApiMultimodalTest {

    private val jpegUrl = "data:image/jpeg;base64,/9j/4AAQSkZJRgABAQ=="

    private fun build(
        shape: ApiShape = ApiShape.OPENAI,
        user: String = "USER",
        images: List<String> = emptyList(),
    ) = buildApiRequest(
        shape = shape,
        base = "https://a.b/v1",
        customPath = "",
        apiKey = "sk-1",
        model = "m1",
        system = "SYS",
        user = user,
        temperature = 0.8,
        maxTokens = 400,
        jsonMode = true,
        imageDataUrls = images,
    )

    @Test
    fun `没有图时 content 仍是字符串`() {
        val root = Json.parse(build().body)
        val content = root.at("messages", "1", "content")
        // 必须是字符串，不是数组 —— 否则纯文字会话的请求体变了，前缀缓存全失效
        assertEquals("USER", content.asStr())
        assertNull("不该被换成数组", content.asArr())
    }

    @Test
    fun `有图时 content 是文字加图片的数组`() {
        val root = Json.parse(build(images = listOf(jpegUrl)).body)
        val parts = root.at("messages", "1", "content").asArr()
        assertNotNull("content 应该是数组", parts)
        assertEquals(2, parts!!.size)

        // 第 1 段：文字
        assertEquals("text", parts[0].at("type").asStr())
        assertEquals("USER", parts[0].at("text").asStr())

        // 第 2 段：图片
        assertEquals("image_url", parts[1].at("type").asStr())
        assertEquals(jpegUrl, parts[1].at("image_url", "url").asStr())
        // detail=high 是刻意的：聊天截图上的小字正是重点，low 会把分辨率压下去等于白传
        assertEquals("high", parts[1].at("image_url", "detail").asStr())
    }

    @Test
    fun `多张图按顺序附在文字之后`() {
        val a = "data:image/jpeg;base64,AAAA"
        val b = "data:image/jpeg;base64,BBBB"
        val parts = Json.parse(build(images = listOf(a, b)).body)
            .at("messages", "1", "content").asArr()!!
        assertEquals(3, parts.size)
        assertEquals("text", parts[0].at("type").asStr())
        assertEquals(a, parts[1].at("image_url", "url").asStr())
        assertEquals(b, parts[2].at("image_url", "url").asStr())
    }

    @Test
    fun `base64 里的加号与斜杠不被转义坏`() {
        // JSON 转义很容易顺手把 '/' 也转掉；'+/=' 必须原样留着，否则图解不开
        val tricky = "data:image/jpeg;base64,ab+/cd=="
        val body = build(images = listOf(tricky)).body
        assertTrue("加号被动了", body.contains("ab+/cd=="))
        val back = Json.parse(body).at("messages", "1", "content").asArr()!![1]
            .at("image_url", "url").asStr()
        assertEquals(tricky, back)
    }

    @Test
    fun `system 为空时仍然只有一条 user 消息`() {
        val r = buildApiRequest(
            ApiShape.OPENAI, "https://a.b/v1", "", "k", "m",
            "", "USER", 0.5, 0, jsonMode = false,
            imageDataUrls = listOf(jpegUrl),
        )
        val root = Json.parse(r.body)
        assertNull("不该有 system", root.at("messages", "0", "role").asStr()?.takeIf { it == "system" })
        assertEquals("user", root.at("messages", "0", "role").asStr())
        assertEquals(2, root.at("messages", "0", "content").asArr()!!.size)
    }

    @Test
    fun `自定义形态也支持图片`() {
        val r = buildApiRequest(
            ApiShape.CUSTOM, "https://a.b/v1", "my/path", "k", "m",
            "SYS", "USER", 0.5, 0, jsonMode = false,
            imageDataUrls = listOf(jpegUrl),
        )
        assertEquals("https://a.b/v1/my/path", r.url)
        assertEquals(2, Json.parse(r.body).at("messages", "1", "content").asArr()!!.size)
    }

    /**
     * Responses / Anthropic 这两种形态的多模态格式**和 OpenAI 不一样**，
     * 目前没有实现。这里钉住的是「传了图也不会崩、不会塞坏请求体」——
     * 降级成纯文字，而不是产出一个服务端看不懂的 body。
     */
    @Test
    fun `未支持的形态传图时降级成纯文字而不破坏请求体`() {
        for (shape in listOf(ApiShape.RESPONSES, ApiShape.ANTHROPIC)) {
            val r = buildApiRequest(
                shape, "https://a.b/v1", "", "k", "m",
                "SYS", "USER", 0.5, 100, jsonMode = false,
                imageDataUrls = listOf(jpegUrl),
            )
            assertNotNull("$shape 的请求体应该仍是合法 JSON", Json.parse(r.body))
            assertTrue("$shape 不该出现 image_url", !r.body.contains("image_url"))
        }
    }
}
