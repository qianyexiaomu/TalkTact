package dev.goutou.wingman.llm

/**
 * 服务商预设：**只省掉「去官网查地址」这一步**。
 *
 * 我们本来就是「OpenAI 兼容 + 自定义 base_url + 自定义 model」，所以预设不是什么特殊通道，
 * 就是一张字段映射表 —— 选中之后照样能手改，也不改变任何请求逻辑。
 *
 * ⚠️ **模型名只在查过官方文档时才预填**：模型名各家迭代很快，写死一个过期的比留空更坑 ——
 * 留空时界面上有「从服务端拉取模型列表」。
 */
data class ProviderPreset(
    val id: String,
    val label: String,
    /** 「接口地址」该填什么（这是用户最难猜的一项，所以预设主要在填它） */
    val baseUrl: String,
    /** 模型名。空 = 不预填，让用户拉列表或自己填。 */
    val model: String = "",
    /** 这个服务商该用哪种接口形态 */
    val shape: ApiShape = ApiShape.OPENAI,
    /** 一行注意事项，选中后显示在卡片里 */
    val note: String = "",
)

val PROVIDER_PRESETS: List<ProviderPreset> = listOf(
    ProviderPreset(
        id = "deepseek",
        label = "DeepSeek 深度求索",
        baseUrl = "https://api.deepseek.com",
        // 这两个名字来自官方文档（老名 deepseek-chat / deepseek-reasoner 已不再出现）
        model = "deepseek-flash",
        note = "思考模式默认开着：temperature 设了不报错，但**不生效**；按峰谷计价（低谷半价）",
    ),
    ProviderPreset(
        id = "anthropic",
        label = "Anthropic Claude",
        baseUrl = "https://api.anthropic.com/v1",
        shape = ApiShape.ANTHROPIC,
        note = "鉴权走 x-api-key（不是 Bearer）；本地代理不支持这种形态，请直连",
    ),
    ProviderPreset(
        id = "dashscope",
        label = "通义千问（DashScope）",
        baseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1",
        note = "地址必须带 /compatible-mode",
    ),
    ProviderPreset(id = "zhipu", label = "智谱 GLM", baseUrl = "https://open.bigmodel.cn/api/paas/v4"),
    ProviderPreset(id = "kimi", label = "月之暗面 Kimi", baseUrl = "https://api.moonshot.cn/v1"),
    ProviderPreset(
        id = "ark",
        label = "火山方舟（豆包）",
        baseUrl = "https://ark.cn-beijing.volces.com/api/v3",
        note = "模型要填「推理接入点 ID」（ep-xxxxxxxx），**不是模型名**",
    ),
    ProviderPreset(id = "qianfan", label = "百度千帆", baseUrl = "https://qianfan.baidubce.com/v2"),
    ProviderPreset(
        id = "siliconflow",
        label = "硅基流动（聚合）",
        baseUrl = "https://api.siliconflow.cn/v1",
        note = "聚合平台：一个 Key 能调多家模型",
    ),
    ProviderPreset(id = "stepfun", label = "阶跃星辰", baseUrl = "https://api.stepfun.com/v1"),
    ProviderPreset(id = "minimax", label = "MiniMax", baseUrl = "https://api.minimax.chat/v1"),
    ProviderPreset(id = "openai", label = "OpenAI", baseUrl = "https://api.openai.com/v1"),
    /*
     * OpenCode 的网关（Console）。官方文档（2026-11 核对）里它是「一个基址 + 三条路径」：
     *   按量 https://opencode.ai/inference        Go 套餐 https://opencode.ai/inference/go
     *   再拼 /openai/v1/chat/completions、/openai/v1/responses、/anthropic/v1/messages。
     * 所以这里的 baseUrl 是「基址 + 形态那一段」，我们那套 apiUrl 拼上去正好是完整端点。
     *
     * 三条路径都吃全部付费模型（Console 会替我们翻译），**所以统一走 Chat 兼容就行** ——
     * 不用为了 GPT 系切 Responses、也不用为了 Claude 系切 Anthropic（后者的 x-api-key
     * 鉴权我们这边也发不出去，走 Chat 兼容反而绕开了这个问题）。
     */
    ProviderPreset(
        id = "opencode-go",
        label = "OpenCode Go（包月套餐）",
        baseUrl = "https://opencode.ai/inference/go/openai/v1",
        // 官方「Go 套餐」模型表里最便宜的一档，名字取自 2026-11 的文档
        model = "deepseek-v4.1-flash",
        note = "包月套餐（Go $10/月、Plus $40/月），Key 在 opencode.ai/console 的「Personal API keys」里建。" +
            "免费模型（名字带 -free 的）只有 OpenCode 客户端能用，这里调会返回 403，得选付费模型。" +
            "「拉取模型列表」在这儿可能 404（官方列表在 /inference/go/v1/models），模型名手填即可：deepseek-v4.1-flash、glm-5.3-flash、kimi-k3",
    ),
    ProviderPreset(
        id = "opencode-zen",
        label = "OpenCode 按量（Zen）",
        baseUrl = "https://opencode.ai/inference/openai/v1",
        model = "deepseek-v4.1-flash",
        note = "和上面同一套接口，只是走 Console 余额按量计费（地址少一段 /go）。" +
            "免费模型同样只在 OpenCode 客户端里可用，第三方调用会 403",
    ),
)

fun presetOf(id: String): ProviderPreset? = PROVIDER_PRESETS.firstOrNull { it.id == id }
