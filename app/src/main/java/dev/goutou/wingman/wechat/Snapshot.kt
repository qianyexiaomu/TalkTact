package dev.goutou.wingman.wechat

import dev.goutou.wingman.proxy.ProxyProtocol

/**
 * View 树 -> 纯数据的中间表示。
 *
 * 这一层之所以存在，是因为「读取聊天记录」原来是整个模块最容易坏、又最没法测的地方：
 * 判定逻辑和 View/Canvas 绞在一起，微信一更新就只能装到手机上肉眼试。
 * 现在 ViewReader（依赖 Android）只负责产出下面的快照，ChatParser（纯 Kotlin）负责判定，
 * 判定逻辑因此可以在 JVM 上跑单元测试。
 */

/** 消息方向。UNKNOWN = 判不了，宁可丢掉也不要猜。 */
enum class Side { ME, OTHER, UNKNOWN }

/** 行内文字块的类型（由 ViewReader 标注）。 */
enum class Kind { BUBBLE, NICKNAME, TIMESTAMP, TAG, OTHER }

data class TextNode(
    val text: String,
    val top: Int,
    val kind: Kind = Kind.OTHER,
    val size: Float = 0f,
    /** 控件是否真的可见。微信会把正文放在 INVISIBLE 的占位控件里，所以只当参考权重用。 */
    val visible: Boolean = true,
)

data class AvatarNode(val centerX: Int, val top: Int, val size: Int)

/**
 * 一行的「主气泡」。
 * @param text null 表示这一行是图片/表情/语音之类没有文字的附件
 * @param colorSide 气泡底色给出的那一票（微信自己的绿色 = 我）
 * @param centerRatio 气泡中心 x / 屏宽，用来判左右
 */
data class Bubble(
    val text: String?,
    val colorSide: Side,
    val centerRatio: Double?,
    val top: Int,
)

data class RowSnapshot(
    val bubble: Bubble?,
    val texts: List<TextNode> = emptyList(),
    val avatars: List<AvatarNode> = emptyList(),
    /**
     * 这一行里那张「消息图」（没有图就是 null）。
     *
     * 只带尺寸和一个**按需取字节**的入口：抓图 + 重新编码是有成本的，不该每轮读屏都做一遍；
     * 而取字节要碰 Android API，所以做成回调 —— 快照这一层仍然是纯数据，单测照样能造。
     */
    val image: RowImage? = null,
    /**
     * 这一行「找图」的现场（只在像附件的行上填），例如 `候选 2｜最大 MMImageView 640x480`。
     *
     * 为什么要带着它：真机上「图上有字、却一个字都没认出来」没法调试（改一次要发版）。
     * 把看到的东西带回 App 显示出来，就能一眼分辨是「这行压根没找到图」还是「找到了但认不出字」。
     */
    val imageProbe: String? = null,
    /**
     * 这一行那张图**落在屏幕的哪半边**（图控件中心 x / 屏宽，0=最左、1=最右）。
     *
     * 为什么解析层需要它：图片行往往**一个字都没有** —— 没有文字就没有气泡容器，
     * 头像一旦也没被认出来，这一行就只剩「图摆在哪边」这一条线索了。没有它，
     * [ChatParser] 只能把整行丢掉（连图一起），用户看到的就是「明明选了一律发图，
     * 模型却说没收到东西」——而图其实**已经取到了**，卡在方向判定这一步。
     *
     * ⚠️ 它只在其它证据**一张都没有**时才会被当成一票（见 `ChatParser.resolveSide`）：
     * 它和气泡位置是同一类几何证据，没资格去推翻已经明确的头像 / 气泡判定
     * （真出现过「气泡明说靠左、图偏偏画在右半边」的排版）。
     */
    val imageRatio: Double? = null,
    /**
     * 这一行被 [isSystemHint] 认出来的**微信系统提示**原文（例如「轻触重试」），没有则 null。
     *
     * 为什么要单独留着这个字段，而不是靠 `bubble.text` 就能看出来 —— 因为提示**已经被
     * `okText` 从文字节点里剔掉了**（它是微信压在图上的，不是对方说的话），所以下游看
     * `bubble.text` 只看到 null，分不清「这一行本来一个字都没有」和「这一行字被剔掉了」。
     *
     * 有了它才能说清一件对用户很关键的事：**这一行确实有张图，只是微信没加载出来**。
     * 否则用户只看到「模型说没收到图」，而真实原因是网络/原图没下完 ——
     * 让他去点开那张图触发下载就能解决，和「模块坏了」是两回事。
     */
    val systemHint: String? = null,
)

/**
 * 一行里的那张图（图片消息用）。
 *
 * @param width/height 图在屏幕上的尺寸（诊断里用它说明「取的是哪一张」）
 * @param grab 按「最长边」取 JPEG 字节；null = 这张图不打算送（取不到位图 / 编码失败）
 */
class RowImage(
    val width: Int,
    val height: Int,
    private val grab: (Int) -> ByteArray?,
) {
    /** 压成 JPEG。默认最长边走 [ProxyProtocol.OCR_MAX_SIDE]。 */
    fun jpeg(maxSide: Int = ProxyProtocol.OCR_MAX_SIDE): ByteArray? =
        runCatching { grab(maxSide) }.getOrNull()
}

/** 交给模型的一条消息。 */
data class ChatMsg(
    val fromMe: Boolean,
    val text: String,
    val who: String = "",
    /** true = 占位文本，不是原文（图片/表情/语音） */
    val attachment: Boolean = false,
    /**
     * 这一行的**原图**（`data:image/jpeg;base64,...`），只有「直传多模态」那两档才填。
     *
     * 为什么不复用 [text]：认字（OCR）能说出「图里写了什么」，但说不出表情、手势、版式 ——
     * 而模型直接看图两样都能懂。两者是不同的信息，各有各的用处，所以并存。
     *
     * ⚠️ 刻意**不进** [dev.goutou.wingman.wechat.conversationDigest]：指纹是每 tick 算的，
     * 把几十 KB 的 base64 塞进去等于每轮都哈希一遍图片，纯属浪费。
     * 图片在缓存键里的身份由下面的 [imageId] 承担。
     */
    val imageDataUrl: String? = null,
    /**
     * 这张图的**短身份**（压缩后字节的 sha256 前 16 位）—— 只为**结果缓存键**而存在。
     *
     * 为什么必需：[imageDataUrl] 有几十 KB，而缓存键（`screenIdentity`）是**每 tick** 算一遍的，
     * 把 data URL 塞进去等于每轮哈希一遍图片。可键里一旦不带图片身份，两张不同的图
     * 正文都是 `[图片/表情/语音]` 这个占位 → 键相同 → **命中缓存，把上一张图的答案还回去**
     * （用户实测：连着发两张图，第二张的回复还是第一张的内容）。
     *
     * ⚠️ 它和 [imageDataUrl] 是**两件事**，别合并：一个是发给模型的载荷（刻意不进摘要，太重），
     * 一个是缓存键里的短身份（必须进）。这个 sha256 在 ImageOcr.prepare 里本来就算过，取出来零成本。
     */
    val imageId: String? = null,
)

const val ATTACHMENT_TEXT = "[图片/表情/语音]"

/**
 * 微信给「没有文字的附件」用的**通用占位**（无障碍描述里也会出现这几个）。
 *
 * 刻意**不含**具体表情名（`[微笑]` 这种）：那种行本来就是有内容的文字消息，
 * 不该被当成「图片消息」重写一遍。
 */
private val GENERIC_ATTACHMENT = setOf(
    "[图片]", "[照片]", "[表情]", "[动画表情]", "[语音]", "[视频]", "[文件]", "[链接]", "[位置]", ATTACHMENT_TEXT,
)

/** 这一行是不是「通用附件占位」——凡是这种行，都可以拿图里的字把它换掉。 */
fun isGenericAttachment(t: String): Boolean = t.trim() in GENERIC_ATTACHMENT

/**
 * 「值得去找图认字」的行 —— 比 [GENERIC_ATTACHMENT] 窄得多。
 *
 * 只有图片 / 照片两类占位、以及模块自己那个通用占位才去找图。语音 / 视频 / 文件 / 位置
 * 这些行里没有能认的文字，`[表情]` / `[动画表情]` 同理 —— 以前对着它们也要把控件画下来送去
 * OCR，结果永远是「认了：这张图里没字」，白占一次后台识别，还让状态行一屏一屏地刷。
 *
 * ⚠️ 判定的是**微信给的占位**；正文为空（`null`，内容未知）那一支由调用方处理，仍会去找图。
 */
private val IMAGE_LIKE = setOf("[图片]", "[照片]", ATTACHMENT_TEXT)

/** 这一行值不值得去找图认字（见 [IMAGE_LIKE]）。 */
fun isImageLikeAttachment(t: String): Boolean = t.trim() in IMAGE_LIKE

/** 认出来的图片文字写进上下文时的前缀（模型靠它知道「这是从图里认出来的，可能有错字」）。 */
const val IMAGE_PREFIX = "[图片] "

/**
 * 图片那一行最终写进上下文的样子。
 *
 * 认出字 → `[图片] 认出来的文字`（告诉模型这是从图里认出来的，可能有错别字 ——
 * 但总比「内容未知，不要猜」强）；没认出来 / 没开识图 / 认字失败 → 退回老占位 [ATTACHMENT_TEXT]。
 *
 * 截断而不是丢弃：普通文字消息超过 [ChatParser] 的上限会被整条丢掉，
 * 但一张图的文字往往只是一部分有用 —— 留前面这些字比什么都不留强。
 */
fun imageMessageText(ocr: String?, max: Int = 400): String {
    val t = ocr?.trim().orEmpty()
    if (t.isEmpty()) return ATTACHMENT_TEXT
    val body = if (max > 0 && t.length > max) t.take(max) + "…" else t
    return IMAGE_PREFIX + body
}
