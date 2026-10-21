package dev.goutou.wingman

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.goutou.wingman.config.ConfigStore
import dev.goutou.wingman.llm.MAX_REPLIES
import dev.goutou.wingman.llm.REPLY_STYLES
import dev.goutou.wingman.config.Keys
import dev.goutou.wingman.config.PREF_NAME
import dev.goutou.wingman.llm.FULL_PROMPT
import dev.goutou.wingman.llm.coreOf
import dev.goutou.wingman.ui.GlassQuality
import dev.goutou.wingman.ui.miuix.MiuixPreviewScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream

/**
 * 截图回归（JVM 上跑，不需要真机/模拟器）。
 *
 * 目的不是像素级严丝合缝（那种对比一升依赖就狂飘），而是：
 * ① **界面还能渲染出来** —— 盲改 Screens.kt 时最容易犯的错就是某个 composable 直接崩；
 * ② 关键文案还在（改版顺手把入口删了这种事，断言能立刻发现）；
 * ③ 每次跑完把图丢进 `build/screenshots/`，CI 作为 artifacts 上传，可以肉眼扫一眼。
 *
 * 两个 Robolectric 上的坑（都踩过了）：
 * - `captureToImage()` 底层是 PixelCopy，Robolectric 没有 —— 所以这里改成**直接把 decorView 画到
 *   Bitmap**（Roborazzi 干的就是这件事）；前提是 `@GraphicsMode(NATIVE)`。
 * - Compose 测试要启动的 ComponentActivity 必须由 `ui-test-manifest` 提供，而它得是
 *   **debugImplementation**（放进被测 APK 的 manifest），放 testImplementation 会找不到 Activity。
 *
 * 玻璃的模糊 / AGSL 折射在这个环境里本来就不生效（代码里已按能力降级，见 ui/GlassQuality.kt），
 * 所以这些图是「没有玻璃特效的骨架图」—— 对回归来说够用。
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ScreenRenderTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val store: ConfigStore
        get() = ConfigStore(ApplicationProvider.getApplicationContext<Context>())

    /** 把当前界面画成 build/screenshots/<name>.png（CI 会作为 artifacts 上传）。 */
    private fun capture(name: String) {
        val view: View = rule.activity.window.decorView
        // Robolectric 里视图可能还没被测过尺寸：先强制量一遍，否则画出来是空图
        if (view.width <= 0 || view.height <= 0) {
            val w = View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY)
            val h = View.MeasureSpec.makeMeasureSpec(2160, View.MeasureSpec.EXACTLY)
            view.measure(w, h)
            view.layout(0, 0, view.measuredWidth, view.measuredHeight)
        }
        val bitmap = Bitmap.createBitmap(
            view.width.coerceAtLeast(1),
            view.height.coerceAtLeast(1),
            Bitmap.Config.ARGB_8888,
        )
        view.draw(Canvas(bitmap))

        val dir = File("build/screenshots").apply { mkdirs() }
        val file = File(dir, "$name.png")
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        assertTrue("截图没写出来：$file", file.length() > 0)
    }

    /**
     * Miuix 新版界面（预览）。
     *
     * 这一页**自己铺底**（Miuix 的 `background`：浅色近白、深色黑），所以不用套 `page {}` ——
     * 那层是给玻璃卡片准备的渐变背景，铺了也看不见，反而会让人以为这页还在用渐变底。
     */
    @Test
    fun `Miuix 预览页能渲染`() {
        rule.setContent { MiuixPreviewScreen(store) {} }
        rule.onNodeWithText("微信军师").assertExists()
        rule.onNodeWithText("运行状态").assertExists()
        capture("miuix-home")
    }

    /** Miuix 标签页：设置列表（图标最多的那一页）。 */
    @Test
    fun `Miuix 设置页能渲染`() {
        rule.setContent { MiuixPreviewScreen(store, initialTab = 3) {} }
        rule.onNodeWithText("常用").assertExists()
        rule.onNodeWithText("维护").assertExists()
        capture("miuix-settings")
    }

    /** Miuix 二级页：外观（玻璃三档）。 */
    @Test
    fun `Miuix 外观页能渲染`() {
        rule.setContent { MiuixPreviewScreen(store, initialPage = 1) {} }
        rule.onNodeWithText("外观与背景").assertExists()
        rule.onNodeWithText("玻璃效果").assertExists()
        capture("miuix-appearance")
    }

    /** Miuix 二级页：自动分析（开关 + 步进器）。 */
    @Test
    fun `Miuix 自动分析页能渲染`() {
        rule.setContent { MiuixPreviewScreen(store, initialPage = 2) {} }
        // 不要断言「自动分析」：页标题和开关标题都叫这个，会匹配到两个节点
        rule.onNodeWithText("节奏").assertExists()
        rule.onNodeWithText("参考条数").assertExists()
        capture("miuix-auto")
    }

    /** Miuix 二级页：回复风格（多选）。 */
    @Test
    fun `Miuix 回复风格页能渲染`() {
        rule.setContent { MiuixPreviewScreen(store, initialPage = 3) {} }
        rule.onNodeWithText("勾几种就写几条").assertExists()
        capture("miuix-style")
    }

    /** Miuix 二级页：接入与模型（三个输入框 + 保存）。 */
    @Test
    fun `Miuix 接入页能渲染`() {
        rule.setContent { MiuixPreviewScreen(store, initialPage = 4) {} }
        rule.onNodeWithText("接入与模型").assertExists()
        rule.onNodeWithText("接口").assertExists()
        capture("miuix-endpoint")
    }

    /** Miuix 二级页：图片与 OCR（开关 + 三档）。 */
    @Test
    fun `Miuix 图片页能渲染`() {
        rule.setContent { MiuixPreviewScreen(store, initialPage = 5) {} }
        rule.onNodeWithText("图片与 OCR").assertExists()
        rule.onNodeWithText("只认字").assertExists()
        capture("miuix-image")
    }

    /** Miuix 标签页：角色列表。 */
    @Test
    fun `Miuix 角色页能渲染`() {
        rule.setContent { MiuixPreviewScreen(store, initialTab = 2) {} }
        rule.onNodeWithText("档案").assertExists()
        capture("miuix-roles")
    }

    /** Miuix 标签页：军师（提示词 + Skill）。 */
    @Test
    fun `Miuix 军师页能渲染`() {
        rule.setContent { MiuixPreviewScreen(store, initialTab = 1) {} }
        rule.onNodeWithText("提示词").assertExists()
        rule.onNodeWithText("Skill").assertExists()
        capture("miuix-mentor")
    }

    /** Miuix 标签页：试一试。 */
    @Test
    fun `Miuix 试一试页能渲染`() {
        rule.setContent { MiuixPreviewScreen(store, initialPage = 16) {} }
        rule.onNodeWithText("聊天内容").assertExists()
        capture("miuix-trial")
    }

    /** Miuix 二级页：关于（从设置进）。 */
    @Test
    fun `Miuix 关于页能渲染`() {
        rule.setContent { MiuixPreviewScreen(store, initialPage = 7) {} }
        rule.onNodeWithText("版本").assertExists()
        capture("miuix-about")
    }

    /** Miuix 二级页：诊断。 */
    @Test
    fun `Miuix 诊断页能渲染`() {
        rule.setContent { MiuixPreviewScreen(store, initialPage = 8) {} }
        rule.onNodeWithText("操作").assertExists()
        capture("miuix-diag")
    }

    /** Miuix 二级页：会话白名单。 */
    @Test
    fun `Miuix 白名单页能渲染`() {
        rule.setContent { MiuixPreviewScreen(store, initialPage = 9) {} }
        rule.onNodeWithText("开关").assertExists()
        capture("miuix-whitelist")
    }

    /** Miuix 二级页：备份与迁移。 */
    @Test
    fun `Miuix 备份页能渲染`() {
        rule.setContent { MiuixPreviewScreen(store, initialPage = 10) {} }
        rule.onNodeWithText("选择文件导入").assertExists()
        capture("miuix-backup")
    }

    /** Miuix 二级页：本地代理。 */
    @Test
    fun `Miuix 代理页能渲染`() {
        rule.setContent { MiuixPreviewScreen(store, initialPage = 11) {} }
        rule.onNodeWithText("端口").assertExists()
        capture("miuix-proxy")
    }

    /** Miuix 二级页：按联系人设风格（列表）。 */
    @Test
    fun `Miuix 按联系人页能渲染`() {
        rule.setContent { MiuixPreviewScreen(store, initialPage = 12) {} }
        rule.onNodeWithText("已设置", substring = true).assertExists()
        capture("miuix-chatstyles")
    }

    /** Miuix 三级页：给某个会话挑风格。 */
    @Test
    fun `Miuix 会话风格页能渲染`() {
        rule.setContent {
            MiuixPreviewScreen(store, initialPage = 13, initialChatKey = "测试会话") {}
        }
        rule.onNodeWithText("当前：跟随全局").assertExists()
        capture("miuix-chatstyle-edit")
    }

    /** Miuix 二级页：角色详情（用常驻的「本人」那一条）。 */
    @Test
    fun `Miuix 角色详情能渲染`() {
        rule.setContent {
            MiuixPreviewScreen(
                store,
                initialPage = 6,
                initialRoleKey = dev.goutou.wingman.config.SELF_ROLE_KEY,
            ) {}
        }
        rule.onNodeWithText("危险操作").assertExists()
        capture("miuix-role-detail")
    }

    /**
     * 改条数/风格之后，**提示词页显示的那份文本也跟着变**。
     *
     * 这是踩过的坑：规格重拼只放在调用时（`LlmClient` 里），
     * 于是实际行为已经变了、设置页存了新值，唯独「提示词」页那份快照纹丝不动 ——
     * 三个地方有两个是新的，只有给人看的那个是旧的，比「真没生效」还难查。
     *
     * 所以这里断的是**落盘的那份 prompt 本身**，不是界面上某个 Text 节点：
     * 界面那条已经被「高级设置里有条数与风格的选择」覆盖了。
     */
    @Test
    fun `改风格会同步重拼提示词快照，条数跟着勾选变`() {
        store.saveReplyPlan(listOf("safe", "humor", "advance"))
        val before = store.load().prompt
        assertTrue("勾了 3 种就该写 3 条，实际含：$before", before.contains("恰好给 3 条"))

        store.saveReplyPlan(listOf("empathy", "brief"))
        val after = store.load().prompt

        assertTrue("换规格后提示词快照必须变，否则提示词页看不到任何变化", before != after)
        assertTrue("勾了 2 种就是 2 条（条数从勾选派生），实际含：$after", after.contains("恰好给 2 条"))
        assertTrue("风格清单也要跟着换，含共情/简短：$after", after.contains("共情") && after.contains("简短"))
        assertTrue("旧条数不该残留：$after", !after.contains("恰好给 3 条"))
        // 正文是用户的，不能因为改了规格就被换掉
        assertTrue("正文必须保留", coreOf(before) == coreOf(after))
    }

    /**
     * 反复改条数不能把提示词越改越乱（规格段会重复叠加 / 契约会重复追加）。
     *
     * 重拼是「摘掉旧的两段再拼新的」，这里确认它真的幂等 ——
     * 如果某次没摘干净，用户每点一次设置就多一段，提示词会越滚越长还多花 token。
     *
     * 只断规格段标题，不拿 `"replies":` 的出现次数当指标：满血版 skill 正文里的
     * few-shot 示例本身也带 replies，那个数天然是 2，用它做判据会在换 skill 时误报。
     */
    @Test
    fun `反复改条数不会让提示词里叠加出多份规格段`() {
        // 连点四次，每次勾的个数都不一样（2、3、4、5）—— 规格段里的条数要跟着走
        repeat(4) { i -> store.saveReplyPlan(REPLY_STYLES.map { it.id }.take(2 + i)) }
        val p = store.load().prompt
        assertEquals(1, Regex("【这次要几条、什么风格】").findAll(p).count())
        assertEquals("最后一次勾了 5 种，就该写 5 条", true, p.contains("恰好给 5 条"))
    }

    /**
     * 内置 skill 的 few-shot 示例里也有 replies —— 换规格时它必须跟着换，
     * 否则满血版会出现「正文示例说 3 条、契约说 5 条」这种自相矛盾的提示词。
     */
    @Test
    fun `切到满血版 skill 时示例也跟着当前规格走`() {
        val cur = store.load()
        store.save(cur.copy(prompt = FULL_PROMPT))
        store.saveReplyPlan(listOf("empathy", "direct"))

        val p = store.load().prompt
        // 规格段：勾了 2 种 → 2 条，风格共情/直接
        assertTrue("规格段要写 2 条：$p", p.contains("恰好给 2 条"))
        assertTrue("规格段要写共情：$p", p.contains("共情"))
        // few-shot 示例：2 个 style 元素
        val example = p.substringAfter("【例子】").substringBefore("只输出下面这个 JSON")
        assertEquals("few-shot 示例要给 2 条：$example", 2, Regex("\"style\":").findAll(example).count())
    }
}
