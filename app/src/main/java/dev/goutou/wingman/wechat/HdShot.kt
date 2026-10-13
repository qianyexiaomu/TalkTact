package dev.goutou.wingman.wechat

import kotlin.math.abs

/**
 * 「用户刚点开看过的那张大图」—— 整个进程只有一份，只在内存里，不落盘。
 *
 * ## 它解决的到底是什么问题
 *
 * 气泡里那张图是**列表缩略图**：尺寸被气泡在屏幕上的大小卡死，通常长边只有几百像素。
 * 把它发给多模态模型，图上的小字基本读不出来。而真正清晰的来源只有一个 ——
 * 用户把图点开之后，**大图查看器里那份按屏幕分辨率解码的结果**（通常 1000px 以上）。
 *
 * ## 为什么是「用户自己点开」而不是模块替他点
 *
 * 模块完全可以自己去点那个气泡（`performClick()`）再自动退回，拿到的东西一模一样。
 * 但代价是三条都绕不开：
 *
 * 1. **那是替用户操作微信界面** —— 在这条路上这是唯一真正会被风控注意到的动作，
 *    比读本地文件显眼得多（读文件是纯离线行为，不上报）；
 * 2. **必须可靠地退回来** —— 退不干净就把用户留在查看器里，而读屏会继续对着查看器
 *    读一屏「不是聊天页」，等于这一轮分析彻底跑偏；
 * 3. **不能同步做** —— 查看器要靠微信自己的主线程去布局和解码，我们蹲在主线程上等它，
 *    它就永远渲染不出来。于是只能做成跨多轮 tick 的异步状态机，而那是整个模块最脆弱的一段。
 *
 * 让用户自己点开，这三条一起消失：他点开的就是他要发的那张（意图明确），退回也是他自己退。
 * 模块只做一件事 —— 趁他在看的时候把那张图抓下来。
 *
 * ## 为什么只留一份
 *
 * 「刚点开的那张」在语义上本来就是唯一的一张。留多份只会带来「现在用的是哪一张」的歧义，
 * 而过期（[FRESH_MS]）或换会话时自然失效，不需要用户管。
 */
internal object HdShot {

    /** 超过这么久没再点开过，就不再拿它当「这张图的高清版」。 */
    const val FRESH_MS = 3 * 60 * 1000L

    /**
     * 一张备好的大图。
     *
     * [width]/[height] 刻意记的是**控件在屏幕上的尺寸**，不是位图内部尺寸 —— 它就是这次实际
     * 能拿到的像素上限，写出来才不会让人以为拿到的是原图。
     */
    class Shot(val jpeg: ByteArray, val width: Int, val height: Int, val at: Long) {

        /**
         * OpenAI `image_url` 要的 data URL。**懒算**：一份 base64 有几百 KB，
         * 而这个对象可能被好几轮 tick 查到，重编一次就是白烧几百毫秒。
         */
        val dataUrl: String by lazy { ImageBase64.jpegDataUrl(jpeg).orEmpty() }

        val kb: Int get() = (jpeg.size + 512) / 1024

        /** 长宽比 —— 判「这张大图是不是那一行那张」只用它。 */
        val ratio: Double get() = if (height <= 0) 0.0 else width.toDouble() / height
    }

    @Volatile
    private var shot: Shot? = null

    /**
     * 收下这一张。
     *
     * @return true = 真的换了（内容不同）；false = 和手上那份一模一样，白抓了一次
     */
    fun hold(jpeg: ByteArray, width: Int, height: Int): Boolean {
        if (jpeg.isEmpty()) return false
        val old = shot
        if (old != null && old.jpeg.size == jpeg.size && old.jpeg.contentEquals(jpeg)) return false
        shot = Shot(jpeg, width, height, System.currentTimeMillis())
        return true
    }

    /**
     * 还新鲜的那一张；没有、或已经过期就返回 null。
     *
     * 过期顺手丢掉：一份 base64 有几百 KB，没用了就该让它能被回收，而不是等下一次抓取覆盖。
     */
    fun peek(maxAgeMs: Long = FRESH_MS): Shot? {
        val s = shot ?: return null
        if (System.currentTimeMillis() - s.at > maxAgeMs) {
            shot = null
            return null
        }
        return s
    }

    /** 换会话、或者确认这一张不再需要了。 */
    fun clear() {
        shot = null
    }

    /**
     * 这张大图能不能当成 [img]（气泡里那一行）的高清版？
     *
     * 只比长宽比，不比内容 —— 比内容要先解码，太贵；而长宽比对不上时，两张图**几乎不可能**
     * 是同一张（同一个控件位置、同一个比例）。
     *
     * 为什么要这道闸：用户完全可能点开的是**上一张**图，或者点开的是别人发的图表、回来却想
     * 分析这一条。宁可退回缩略图（糊一点），也绝不能把**别的图**当作这一张发出去 ——
     * 那种错不会报错，只会让模型对着不相干的图一本正经地说话。
     */
    fun plausibleFor(img: RowImage, tol: Double = 0.12): Boolean {
        val s = shot ?: return false
        if (img.width <= 0 || img.height <= 0) return false
        val a = s.ratio
        val b = img.width.toDouble() / img.height
        if (a <= 0.0 || b <= 0.0) return false
        return abs(a - b) <= tol * maxOf(a, b)
    }

    /**
     * 缓存键里用的**短身份**。
     *
     * 为什么必需：结果缓存键（`screenIdentity`）里带的是缩略图字节的 sha。同一屏、同一张图，
     * 用了大图版之后那个 sha **一个字都没变** —— 于是「重新识别」会命中上一轮按缩略图算出来的
     * 缓存，把旧答案原样还回来。这正是这一层最不该有的那种静默失败，所以身份必须跟着来源变。
     */
    fun identityOf(): String? = shot?.let { "hd${it.width}x${it.height}s${it.jpeg.size}" }

    /** 诊断用的一句话。 */
    fun line(): String {
        val s = shot ?: return "（还没抓到过：用户没点开过图片）"
        val ageSec = (System.currentTimeMillis() - s.at) / 1000
        val expired = ageSec * 1000 > FRESH_MS
        return "${s.width}x${s.height} · 约 ${s.kb}KB · ${ageSec}s 前" +
            if (expired) "（已过期，不再采用）" else ""
    }
}
