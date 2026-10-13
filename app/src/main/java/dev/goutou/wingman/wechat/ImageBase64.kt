package dev.goutou.wingman.wechat

/**
 * 把图片字节包成 OpenAI `image_url` 要的 data URL。
 *
 * ## 为什么自己写 base64 而不用 `android.util.Base64`
 *
 * [ChatParser] 那条链路会用到这里（经由 ImageOcr），而 ChatParser 是**纯 Kotlin、
 * 可直接在 JVM 上单测**的（`ChatParserTest` 用的是纯 JUnit，没有 Robolectric）。
 * 一旦这里引 `android.util.Base64`，那个测试类会在 JVM 上抛 `RuntimeException: not mocked`
 * —— 为了省十几行代码把整层可测性丢掉不值得。
 *
 * ## 为什么不用 `java.util.Base64`
 *
 * 注入侧跑在微信进程里，属于 Android；`java.util.Base64` 要到 API 26 才有。
 * 虽然模块的 minSdk 是 31（够用），但这里刻意只用 Kotlin 标准库 —— 这一层的约定就是
 * 「不碰平台 API」，改了要单独想清楚。
 */
internal object ImageBase64 {

    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"

    /** 标准 base64（带 `=` 补齐，不换行）—— 正是 data URL 要的形态。 */
    fun encode(bytes: ByteArray): String {
        if (bytes.isEmpty()) return ""
        val sb = StringBuilder((bytes.size + 2) / 3 * 4)
        var i = 0
        while (i + 2 < bytes.size) {
            val n = (bytes[i].toInt() and 0xFF shl 16) or
                (bytes[i + 1].toInt() and 0xFF shl 8) or
                (bytes[i + 2].toInt() and 0xFF)
            sb.append(ALPHABET[(n ushr 18) and 0x3F])
                .append(ALPHABET[(n ushr 12) and 0x3F])
                .append(ALPHABET[(n ushr 6) and 0x3F])
                .append(ALPHABET[n and 0x3F])
            i += 3
        }
        // 尾巴：剩 1 或 2 字节，补 '=' 到 4 的倍数
        when (bytes.size - i) {
            1 -> {
                val n = bytes[i].toInt() and 0xFF shl 16
                sb.append(ALPHABET[(n ushr 18) and 0x3F])
                    .append(ALPHABET[(n ushr 12) and 0x3F])
                    .append("==")
            }
            2 -> {
                val n = (bytes[i].toInt() and 0xFF shl 16) or (bytes[i + 1].toInt() and 0xFF shl 8)
                sb.append(ALPHABET[(n ushr 18) and 0x3F])
                    .append(ALPHABET[(n ushr 12) and 0x3F])
                    .append(ALPHABET[(n ushr 6) and 0x3F])
                    .append('=')
            }
        }
        return sb.toString()
    }

    /** JPEG 的 data URL。空字节返回 null（调用方按「这张图没拿到」处理）。 */
    fun jpegDataUrl(bytes: ByteArray?): String? {
        if (bytes == null || bytes.isEmpty()) return null
        return "data:image/jpeg;base64," + encode(bytes)
    }
}

