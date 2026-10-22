package dev.goutou.wingman.wechat

import java.security.MessageDigest

/** Length-prefixed fields prevent ambiguous keys; digests keep content out of cache identifiers. */
internal fun conversationDigest(vararg fields: String): String {
    val digest = MessageDigest.getInstance("SHA-256")
    fields.forEach { field ->
        val bytes = field.toByteArray(Charsets.UTF_8)
        digest.update(bytes.size.toString().toByteArray(Charsets.UTF_8))
        digest.update(':'.code.toByte())
        digest.update(bytes)
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}

/**
 * 决定「这一屏」身份时看最后几条。
 *
 * 取 2 而不是 1：连着两条「好的」很常见，只比一条就分不出「又来了一条」。
 * 也**不能取整屏** —— 用户往上翻一行，可见集合就全变了，会导致莫名其妙地重新识别。
 */
private const val SCREEN_TAIL = 2

/**
 * 「这一屏」的稳定身份：会话名 + 屏幕指纹 + **最后两条**消息。
 *
 * **刻意不含角色档案 / 设置 / 说话风格** —— 那些每轮都在长（[recordToRoles] 一直在往档案里补），
 * 混进来就会得到一个每轮都变的键。
 *
 * 也**刻意不含整屏的消息列表**：以前是 `msgs.map{...}` 全算进去，于是
 * 「往上翻两行看历史」= 可见集合变了 = 键变了 = 重新问一遍模型。
 * 「这一屏」真正的语义是「这条会话的最新一句是什么」，不是「此刻屏幕上恰好显示哪几条」。
 */
internal fun screenIdentity(name: String, screen: String, msgs: List<ChatMsg>): String =
    conversationDigest(
        name,
        screen,
        *msgs.takeLast(SCREEN_TAIL)
            .map { conversationDigest(it.fromMe.toString(), it.who, it.text, it.attachment.toString()) }
            .toTypedArray(),
    )

/**
 * 本次聊天的**消息账本**：把每轮在屏幕上读到的消息合并成一份会话历史。
 *
 * 为什么需要：屏幕上一次只能看到十来条，往上翻也就多几条；而「参考前文」这件事
 * 天然需要更长的上下文。合并之后，模型看到的是「这一屏 + 之前翻到过的」。
 *
 * ⚠️ 但**分析身份不跟着账本走**（见 [screenIdentity]）——
 * 账本变长不该触发重新识别，否则用户每翻一屏就被问一次模型。
 */
internal class ChatHistory(private val max: Int = 60) {

    private var chat = ""
    private val items = ArrayList<ChatMsg>()

    fun reset() {
        chat = ""
        items.clear()
    }

    /**
     * 把这一轮读到的 [fresh] 合并进账本，返回合并后的历史。
     *
     * 对齐方式：在账本里找 [fresh] 第一条**最后出现**的位置，
     * 保留它之前的内容，后面全部用这一轮覆盖（这一轮是更新的窗口）。
     *
     * 找不到锚点（这一屏和账本没有重叠，多半是翻到了很久以前）时**不动账本** ——
     * 追加到末尾会让 `last()` 变成一条历史消息，那分析的就是错的对象了。
     */
    fun merge(name: String, fresh: List<ChatMsg>): List<ChatMsg> {
        if (name != chat) {
            chat = name
            items.clear()
        }
        if (fresh.isEmpty()) return items.toList()
        if (items.isEmpty()) {
            items.addAll(fresh)
        } else {
            val anchor = items.indexOfLast { it == fresh.first() }
            if (anchor >= 0) {
                while (items.size > anchor) items.removeAt(items.lastIndex)
                items.addAll(fresh)
            }
        }
        while (items.size > max) items.removeAt(0)
        return items.toList()
    }
}

/**
 * 结果缓存 / 请求去重 / 敏感放行 的键：只由「这一屏 + 设置」决定。
 *
 * ⚠️ 这里以前是 `conversationRequestKey(name, settings, roleContext, msgs)`，把角色档案也算了进去。
 * 档案每 900ms 就可能长一条，于是同一屏的键一直在变：
 * ① 缓存必 miss → 同一屏反复问模型（白烧 token）；
 * ② `pendingRequestKey` 对不上 → 在飞的请求被作废后立刻重发；
 * ③ 用户刚点过的「仍然分析这一条」下一轮就失效 → 一直弹回风险卡（用户实测就是这个）。
 * 「档案变了要不要重新问」由 ask() 那一刻的 roleContext 决定，**不该让缓存键跟着抖**。
 */
internal fun resultKey(screen: String, settings: String): String = conversationDigest(screen, settings)

internal fun roleObservationKey(name: String, fromMe: Boolean, text: String): String =
    conversationDigest(name, fromMe.toString(), text)
