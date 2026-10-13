package dev.goutou.wingman

import dev.goutou.wingman.wechat.ImageBase64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

/**
 * 自己写的 base64（见 [ImageBase64]）。
 *
 * 为什么值得单测：这段代码的输出会**直接进请求体**，错了就是「所有图都传坏」，
 * 而症状是服务端报一个和图片无关的解析错 —— 很难联想到是编码问题。
 *
 * 判据刻意用 `java.util.Base64`（只跑在 JVM 测试里，不参与 APK）做**交叉验证**，
 * 比手写期望值可信：手写的话，我把同一个错误写两遍就测不出来了。
 */
class ImageBase64Test {

    private fun expect(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)

    @Test
    fun `与 JDK 的 base64 逐字节一致`() {
        // 覆盖三种尾部：0 字节余、1 字节余（==）、2 字节余（=）
        val cases = listOf(
            byteArrayOf(),
            byteArrayOf(0x00),
            byteArrayOf(0x41),
            "A".toByteArray(),
            "AB".toByteArray(),
            "ABC".toByteArray(),
            "Hello, 世界!".toByteArray(Charsets.UTF_8),
            ByteArray(257) { (it % 256).toByte() },
            ByteArray(1000) { ((it * 7) % 256).toByte() },
        )
        for (b in cases) {
            assertEquals("长度 ${b.size} 不一致", expect(b), ImageBase64.encode(b))
        }
    }

    @Test
    fun `空数组编码成空串（而不是 null 或 =）`() {
        assertEquals("", ImageBase64.encode(ByteArray(0)))
    }

    @Test
    fun `补齐用的等号数量正确`() {
        // 1 字节 -> 两个 '='；2 字节 -> 一个 '='；3 字节 -> 没有
        assertEquals(2, ImageBase64.encode(byteArrayOf(1)).count { it == '=' })
        assertEquals(1, ImageBase64.encode(byteArrayOf(1, 2)).count { it == '=' })
        assertEquals(0, ImageBase64.encode(byteArrayOf(1, 2, 3)).count { it == '=' })
    }

    @Test
    fun `编码结果只含 base64 字母表里的字符`() {
        val out = ImageBase64.encode(ByteArray(600) { (it * 13).toByte() })
        assertTrue("出现了字母表外的字符", out.all { it.isLetterOrDigit() || it == '+' || it == '/' || it == '=' })
        assertTrue("不该有换行", !out.contains('\n') && !out.contains('\r'))
    }

    @Test
    fun `data URL 的形状与内容`() {
        val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0x01, 0x02, 0x03)
        val url = ImageBase64.jpegDataUrl(jpeg)
        assertTrue("前缀必须是 image/jpeg", url!!.startsWith("data:image/jpeg;base64,"))
        val payload = url.removePrefix("data:image/jpeg;base64,")
        assertEquals(expect(jpeg), payload)
        // 再解回去，确认没有多余字符混进去
        assertTrue(jpeg.contentEquals(Base64.getDecoder().decode(payload)))
    }

    @Test
    fun `空字节或 null 不给 data URL`() {
        assertNull("null 应返回 null", ImageBase64.jpegDataUrl(null))
        assertNull("空数组应返回 null（没有图可发）", ImageBase64.jpegDataUrl(ByteArray(0)))
    }
}
