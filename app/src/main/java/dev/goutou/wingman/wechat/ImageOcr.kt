package dev.goutou.wingman.wechat

import dev.goutou.wingman.config.ConfigData
import dev.goutou.wingman.proxy.ProxyProtocol
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.Executors

/**
 * 图片文字识别的**注入侧**那一半：这里不认字，只负责把图送过去、把文字拿回来。
 *
 * 认字在 App 进程里做（ML Kit 只装在 App 里，见 `ocr/Ocr.kt`）——
 * 走的还是 ⑥ 建好的那条回环代理：`POST http://127.0.0.1:PORT/proxy/ocr`（带 token），
 * 响应体就是**纯文本**。刻意不用 JSON：注入侧只用系统 API + kotlin stdlib，不引任何序列化库。
 *
 * ## 为什么是「后台认、认完再问」
 *
 * 读屏这一套跑在**微信的主线程**上（tick 是 handler 消息）。识图要发一次回环请求 + 在 App 侧跑模型，
 * 几百毫秒起步 —— 在主线程上等它，微信就卡住了。所以分成两拍：
 *
 * 1. [prepare]：把这屏的图**排进后台**去认，当场返回「还有没有图在认」；
 *    主循环看到「还有」就这一轮先不分析（只把按钮上的字改成「正在认图…」）。
 * 2. 认完了置一个标志，主循环下一次就带着文字重新读一遍（[takeDirty]）。
 *
 * 为什么不「先用 [图片] 占位问一遍、认完再问一遍」：那样一次分析要花两次 token，
 * 而且用户会先看到一张「不知道图里写了什么」的答案，一秒后又变成另一张 —— 观感很差。
 *
 * ## 五层「别把事搞砸」的保险
 *
 * 1. 开关关着、或没走本地代理 —— 直接不试（没代理就没有地方认，连回环只会白等一次超时）；
 * 2. 同一张图只发一次（按压缩后字节的 sha256 记住结果，LRU 16 张）；
 * 3. 图取不到 / 压完还是太大 / 空 —— 都只记一行状态，回落成 `[图片]` 占位；
 * 4. 失败**不进缓存**，但要冷却 [FAIL_COOLDOWN_MS] —— 否则「代理没起来」会变成每轮都重试，
 *    卡片会永远卡在「正在认图…」；
 * 5. **任何异常都不往上抛** —— 一次识图失败绝不能让整轮分析挂掉。
 */
internal class ImageOcr {

    private val pool = Executors.newSingleThreadExecutor { r ->
        Thread(r, "talktact-ocr").also { it.isDaemon = true }
    }

    /** 图 sha256 -> 认出来的文字（空串 = 确实没字，也算认过，别再发）。按访问顺序 LRU。 */
    private val done = object : LinkedHashMap<String, String>(8, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?): Boolean =
            size > DONE_MAX
    }

    /** 正在后台认的（键同上）。 */
    private val inFlight = HashSet<String>()

    /** 失败过、还在冷却期里的：键 -> 失败时间。 */
    private val failed = HashMap<String, Long>()

    /**
     * 这一轮「行里的图」-> 它对应的键。
     *
     * 只在 [prepare] 里填、只在同一轮的 [textOf] 里读 —— RowImage 没有重写 equals，
     * 所以这就是按对象身份查，正好是我们想要的（同一轮里行和图都是同一批对象）。
     */
    private val keysOfTick = HashMap<RowImage, String>()

    /**
     * 本轮每个键对应的**原图** data URL（直传模式才填）。
     *
     * 在 [prepare]（后台线程）里算好，解析时只是查表 —— 见 [prepare] 的说明：
     * 解析跑在微信主线程上，不能在那儿编码图片。
     */
    private val dataUrlsOfTick = HashMap<String, String>()

    /**
     * 本轮「可以用大图版（[HdShot]）」的那一行。
     *
     * 为什么只能是一行：一屏可能有好几张图，而用户点开的只可能是其中一张。
     * 把大图版套给每一张，等于给别的图配了一张不相干的高清照片 —— 那种错不报错，
     * 只会让模型对着另一张图一本正经地说话。
     *
     * 挑法（见 [prepare]）：**最后一个**长宽比对得上的图行。用的是最后一条，
     * 因为用户点开的多半是他正在看的那条（屏幕上最下面那张）。
     */
    private var hdRowFor: RowImage? = null

    /** 本轮每个键对应的「找图现场」——认不出字时把它写进状态行（不然只看得出「没字」，看不出取错了图）。 */
    private val probeOfKey = HashMap<String, String>()

    /** 有新的识别结果落地（让主循环强制重读一次）。 */
    @Volatile
    private var dirty = false

    /** 最近一次的结果（一句话）。设置页那条状态行显示的就是它。 */
    @Volatile
    private var line: String? = null

    @Volatile
    private var reported: String? = null

    /** 取一条「还没回传过的」状态行；没有新的就返回 null（别把广播通道淹了）。 */
    fun takeLine(): String? = synchronized(this) {
        val cur = line
        if (cur == null || cur == reported) null else cur.also { reported = it }
    }

    /** 刚认完一批？取一次就清掉。 */
    fun takeDirty(): Boolean {
        val d = dirty
        dirty = false
        return d
    }

    /**
     * 把这一屏的图交出去认（**不阻塞**）。
     *
     * 直传那两档还顺带把**原图**（data URL）算好放进 [dataUrlsOfTick] ——
     * 这一步本来就要在后台线程里重新编码 JPEG，正好一次算完两用。
     * 刻意不留给解析那一侧按需取：`parse` 跑在**微信主线程**上，
     * 在那儿编码一张图会直接卡住微信。
     *
     * @return true = 还有图没认完，这一轮先别分析
     */
    fun prepare(rows: List<RowSnapshot>, cfg: ConfigData): Boolean = synchronized(this) {
        keysOfTick.clear()
        dataUrlsOfTick.clear()
        hdRowFor = null

        // 认字和直传是**两件独立的事**，各自判断，不该互相绑死：
        // - 认字必须在 App 进程里做（ML Kit 只装在 App 里）—— **只有这一条**需要回环代理；
        // - 直传全在本进程完成（取图 → JPEG → base64 → 由 LlmClient 拼进请求体），
        //   一个字节都不经过代理，所以**代理关着照样能直传**。
        //
        // ⚠️ 这里以前写的是 `cfg.imageMode.usesDirect && viaProxy`，把「直传」按在了代理上。
        // 但从头到尾没有理由：图本来就取自本进程的 View，编码也在本进程，代理在直传这条路上
        // 什么也不做。多这一个条件的结果是「代理一关，图连取都不取」——用户明明选了发图，
        // 正文却永远是 [图片/表情/语音] 占位，而且界面还说是"设计如此"。
        // 现在直传只看它自己的开关，代理开不开都能用。
        //
        // 早退必须发生在**编码图片之前**：每轮都白编一张图不便宜。
        val viaProxy = ProxyProtocol.ocrUsable(true, ProxyProtocol.routeOf(cfg.proxyEnabled, cfg.proxyToken))
        val doOcr = cfg.ocrEnabled && viaProxy
        val doDirect = cfg.imageMode.usesDirect
        if (!doOcr && !doDirect) {
            // 能走到这里 → 直传那两档已经在上面的判断里排除了，只剩下「只认字」这一档。
            // 此时若代理没开，认字这台机器（ML Kit 在 App 进程）就够不着 —— 说清楚，
            // 并顺手告诉用户还有一条不依赖代理的路（改成发图档）。
            line = when {
                cfg.ocrEnabled && !viaProxy ->
                    "跳过：认字需要本地代理（ML Kit 在 App 进程里）。图片不想离开手机就得打开代理；" +
                        "不想开代理就把「图片怎么交给模型」改成发图那两档 —— 直传不需要代理"
                else -> "跳过：既没开认字也没开直传，图片只能是占位"
            }
            return false
        }

        val now = System.currentTimeMillis()
        var pending = false
        var sawImage = false
        var probe: String? = null
        var directCount = 0
        for (row in rows) {
            val img = row.image
            if (img == null) {
                // 「像附件的一行却找不到图」——把现场留下（只记第一条，状态行就那么长）
                if (row.imageProbe != null && probe == null) probe = row.imageProbe
                continue
            }
            sawImage = true

            // 认字这一步只为 OCR 服务，用 OCR 自己的边长
            val ocrBytes = if (doOcr) img.jpeg(ProxyProtocol.OCR_MAX_SIDE) else null
            if (doOcr && (ocrBytes == null || ocrBytes.isEmpty())) {
                line = "跳过：这一行的图取不到字节（不是普通位图 / 已回收）"
                continue
            }
            if (doOcr && ocrBytes != null && ocrBytes.size > ProxyProtocol.OCR_MAX_BYTES) {
                line = "跳过：这张图压完还是太大（${ocrBytes.size / 1024}KB）"
                continue
            }

            // 直传那张图（自己的边长）。编出来的字节**两用**：既当本轮缓存的键，也当 data URL ——
            // 两档边长不同所以要编两次，但绝不能同一档编三次（每轮都在主流程上，白烧 CPU）。
            val directBytes = if (doDirect) img.jpeg(cfg.imageMaxSide) else null
            val keyBytes = directBytes ?: ocrBytes
            if (keyBytes == null || keyBytes.isEmpty()) {
                // 直传模式下到这儿说明**这张图取不到字节** —— 那正是「选了发图却还是占位」的原因，
                // 必须说出来。以前这里一声不响地 continue，用户只能看到"没反应"。
                if (doDirect) line = "跳过：直传取不到这张图的字节（不是普通位图 / 已回收）"
                continue
            }
            val key = sha256(keyBytes)
            keysOfTick[img] = key
            // 这一行是不是「用户点开的那张」？长宽比对得上才算。**覆盖式**：一屏有多张时，
            // 最后一条（屏幕上最下面那张）胜出 —— 用户点开的多半就是他正在看的那条。
            if (directBytes != null && HdShot.plausibleFor(img)) hdRowFor = img
            if (directBytes != null) {
                ImageBase64.jpegDataUrl(directBytes)?.let { dataUrlsOfTick[key] = it }
                if (dataUrlsOfTick.containsKey(key)) directCount++
            }
            row.imageProbe?.let { probeOfKey[key] = it }
            if (!doOcr || ocrBytes == null) continue
            if (done.containsKey(key)) continue
            if (inFlight.contains(key)) {
                pending = true
                continue
            }
            val lastFail = failed[key] ?: 0L
            if (now - lastFail < FAIL_COOLDOWN_MS) continue

            inFlight.add(key)
            pending = true
            submit(key, ocrBytes, cfg, probeOfKey[key])
        }

        // 这一屏连一张图都没找到，但确实有像附件的行 —— 直接把看到的现场写出来。
        // 真机上「图上有字却一个字都没认」全靠这一句分辨：是没找到图，还是找到了认不出字。
        if (!pending && !sawImage && probe != null) {
            line = "这一屏没找到能认的图（$probe）"
        } else if (!pending && doDirect && directCount > 0) {
            // 直传模式：能不能看到这句，就说明「图到底有没有被取到并编成 data URL」。
            // 看不到它 = 取图/编码那一环断了；看到它却仍是占位 = ChatParser 那一环断了。
            // 这两种原因的修法完全不同，所以这里必须把数字写出来。
            //
            // 顺带说清这一轮用的是**哪一份**：用户点开过大图时就该看到「大图」这两个字，
            // 否则他没法判断「点了重新识别到底有没有变清楚」，只能靠肉眼猜。
            val hd = if (hdRowFor != null) HdShot.peek() else null
            line = if (hd != null) {
                "直传：已备好 $directCount 张图；其中一张走的是**你点开的大图**" +
                    "（${hd.width}x${hd.height} · 约 ${hd.kb}KB）"
            } else {
                "直传：已备好 $directCount 张图（长边 ${cfg.imageMaxSide}px，都是气泡里的缩略图）"
            }
        }
        pending
    }

    /**
     * 这张图认出来什么字？**只看已经认好的** —— 没有就返回 null（这一轮先用 `[图片]` 占位）。
     *
     * 正常流程下调用它时 [prepare] 已经说过「没有在认的了」，所以这里基本都能拿到结果；
     * 兜底返回 null 只是为了让解析这一步永远有个确定的行为。
     */
    fun textOf(img: RowImage): String? = synchronized(this) {
        val key = keysOfTick[img] ?: return null
        done[key]?.ifEmpty { null }
    }

    /**
     * 这张图的**原图**（data URL）。
     *
     * 两档来源，**优先用大的那档**：
     *
     * 1. [HdShot] —— 用户刚刚把这张图点开看过，于是手上有一份大图查看器里解码的结果
     *    （通常 1000px 以上）。这是「更清晰」唯一现实的来源，有就一定要用；
     * 2. 否则查表拿 [prepare] 在后台编好的那一份 —— 它是气泡里那张**缩略图**（几百像素）。
     *
     * 大图那一档只在 [hdRowFor] 这一行上生效（那一行是怎么选出来的，见 [prepare]）：
     * 一张屏幕上可能有好几张图，而用户点开的只可能是其中一张 —— 把大图版套给每一张，
     * 就等于给别的图配了一张不相干的高清照片。
     *
     * 拿不到就返回 null，解析照常走。
     */
    fun dataUrlOf(img: RowImage): String? = synchronized(this) {
        if (img === hdRowFor) {
            HdShot.peek()?.let { return it.dataUrl }
        }
        val key = keysOfTick[img] ?: return null
        dataUrlsOfTick[key]
    }

    /**
     * 这张图的**短身份**（认字节的 sha256 前若干位）—— 给「结果缓存键」用。
     *
     * 为什么必需：缓存键（[screenIdentity]）是**每 tick** 算一遍的，把几十 KB 的 data URL
     * 塞进去等于每轮都哈希一遍图片。但键里又不带图片身份的话，两张不同的图都是
     * `[图片/表情/语音]` 这个占位 → 文本相同 → 键相同 → **命中缓存，把上一张图的答案还回去**。
     * 这个 sha256 本来就在 [prepare] 里算过了（当缓存键用），这里只是把它取出来 —— 零额外成本。
     *
     * 取前 16 位（64 bit）足够：碰撞概率远低于"用户刚好遇到"的量级，而键短了 memo 也省。
     *
     * ⚠️ 走 [HdShot] 那档（用户点开过这张图，用大图重发）时必须**换成另一个身份**：
     * 缩略图字节的 sha 一个字都没变，缓存键（`screenIdentity`）就会和上一轮**按缩略图**算出来的
     * 那次完全相同 —— 于是用户点了「重新识别」，拿回来的却是缩略图那轮的旧答案。
     * 这层最不该有的就是这种「看起来跑了、其实没跑」的静默失败，所以身份要跟着来源走。
     */
    fun imageIdOf(img: RowImage): String? = synchronized(this) {
        if (img === hdRowFor) {
            HdShot.identityOf()?.let { return it }
        }
        keysOfTick[img]?.take(16)
    }

    /** 认字到底给出多少字（给「OCR 认不出就回退直传」那档判断用；没认过返回 0）。 */
    fun charsOf(img: RowImage): Int = textOf(img)?.trim()?.length ?: 0

    /** 诊断用的一句话：认过几张、正在认几张、冷却几张、最近一次是什么。 */
    fun debugLine(): String = synchronized(this) {
        "已认=${done.size} 正在认=${inFlight.size} 冷却中=${failed.size} 本轮候选=${keysOfTick.size}" +
            "｜最近=" + (line ?: "（还没有记录）")
    }

    private fun submit(key: String, bytes: ByteArray, cfg: ConfigData, probe: String?) {
        pool.execute {
            try {
                val started = System.currentTimeMillis()
                val text = post(bytes, cfg)
                val cost = System.currentTimeMillis() - started
                synchronized(this) {
                    done[key] = text
                    inFlight.remove(key)
                    failed.remove(key)
                    line = if (text.isEmpty()) {
                        // 没认到字时把现场带上：这样能一眼看出「取的可能不是那张图」（比如兜底取成了头像）
                        "认了：这张图里没字 · ${cost}ms" + (probe?.let { "｜$it" } ?: "")
                    } else {
                        "认到 ${text.length} 字 · ${cost}ms"
                    }
                    dirty = true
                }
            } catch (t: Throwable) {
                // 失败不进 done：也可能只是代理刚起来 / App 刚被杀，冷却过后值得重试。
                // 这里绝不 rethrow —— 识图失败只是「这次没有文字可用」。
                synchronized(this) {
                    inFlight.remove(key)
                    failed[key] = System.currentTimeMillis()
                    line = "失败：${t.message}"
                    dirty = true
                }
            }
        }
    }

    /** 真正那一跳：带 token 把 JPEG 丢给 App 的回环端口，响应体就是文字。 */
    private fun post(bytes: ByteArray, cfg: ConfigData): String {
        val conn = URL(ProxyProtocol.ocrUrl(cfg.proxyPort)).openConnection() as HttpURLConnection
        return try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 3_000
            conn.readTimeout = 20_000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "image/jpeg")
            conn.setRequestProperty("Authorization", "Bearer ${cfg.proxyToken}")
            // 长度必须写死：代理那边是按 content-length 读 body 的（分块传输会读到 -1）
            conn.setFixedLengthStreamingMode(bytes.size)
            conn.outputStream.use { it.write(bytes) }

            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty().trim()
            if (code !in 200..299) throw IllegalStateException("HTTP $code ${body.take(120)}")
            body
        } finally {
            runCatching { conn.disconnect() }
        }
    }

    private fun sha256(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        val sb = StringBuilder(digest.size * 2)
        for (b in digest) sb.append("%02x".format(b))
        return sb.toString()
    }

    private companion object {
        /** 记住最近多少张图（按访问顺序 LRU）。聊天里同时在看的图没几张。 */
        const val DONE_MAX = 16

        /** 失败之后多久才值得再试 —— 避免「代理没起来」时每轮都重试、卡片永远卡在「正在认图…」。 */
        const val FAIL_COOLDOWN_MS = 60_000L
    }
}
