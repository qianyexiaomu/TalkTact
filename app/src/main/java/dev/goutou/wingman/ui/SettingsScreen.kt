package dev.goutou.wingman.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.os.Build
import dev.goutou.wingman.config.ConfigData
import dev.goutou.wingman.config.ConfigStore
import dev.goutou.wingman.config.GLASS_QUALITY_AUTO
import dev.goutou.wingman.config.GLASS_QUALITY_HIGH
import dev.goutou.wingman.config.GLASS_QUALITY_LOW
import kotlinx.coroutines.launch
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

/**
 * 设置首页。
 *
 * 只留日常会动的东西：外观（玻璃 / 背景）和关于。「接口地址 / 微信内自动分析 / 备份迁移 /
 * 诊断」这些配一次就不常动的，全收进「高级设置」二级页 —— 原来它们铺在这里，把外观挤到了下面。
 */
@Composable
fun SettingsScreen(
    store: ConfigStore,
    ui: ConfigData,
    onUi: (ConfigData) -> Unit,
    onOpenAdvanced: () -> Unit,
) {
    val palette = LocalPalette.current
    val context = LocalContext.current
    var d by remember { mutableStateOf(ui) }
    // 见「高级设置」里同名的那段：拿草稿和上次落盘的那份比，才知道有没有没保存的改动
    var persisted by remember { mutableStateOf(ui) }
    val dirty = d != persisted

    fun update(next: ConfigData) {
        d = next
        onUi(next)
    }

    fun saveAppearance() {
        store.save(d)
        d = store.load()
        persisted = d
        onUi(d)
    }

    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (t: Throwable) {
                // 有的来源不支持持久化授权，那就只在本次运行里有效
            }
            update(d.copy(bgUri = uri.toString()))
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 120.dp)) {
        ScreenHeader("设置", "外观 · 高级设置 · 关于")

        GlassCard(d.glassAlpha) {
            Text("外观", fontSize = TextSize.cardTitle, fontWeight = FontWeight.SemiBold, color = palette.text)
            Spacer(Modifier.height(Space.sm))
            Text("玻璃不透明度：${(d.glassAlpha * 100).toInt()}%", fontSize = TextSize.body, color = palette.sub)
            Slider(value = d.glassAlpha, onValueChange = { update(d.copy(glassAlpha = it)) }, valueRange = 0.3f..1f)
            Spacer(Modifier.height(Space.xs))
            HintText(
                "玻璃背景模糊：${d.glassBlur.toInt()}dp（面板背后做一次真实模糊；内置渐变背景没有细节可模糊，会自动跳过）",
                fontSize = TextSize.body,
                color = palette.sub,
            )
            Slider(value = d.glassBlur, onValueChange = { update(d.copy(glassBlur = it)) }, valueRange = 0f..40f)
            Spacer(Modifier.height(Space.xs))
            // 设备分级：把「自动」实际判成了哪一档直接写出来，省得猜为什么效果不一样
            val lowRamDevice = remember { isLowRamDevice(context) }
            val effectiveQuality = decideGlassQuality(d.glassQuality, lowRamDevice, Build.VERSION.SDK_INT)
            Text(
                "玻璃效果：${glassQualityLabel(effectiveQuality)}" +
                    if (d.glassQuality == GLASS_QUALITY_AUTO) "　（自动判定）" else "　（手动选的）",
                fontSize = TextSize.body,
                color = palette.sub,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                GlassPill("自动", d.glassQuality == GLASS_QUALITY_AUTO, Modifier.weight(1f)) {
                    update(d.copy(glassQuality = GLASS_QUALITY_AUTO))
                }
                GlassPill("高（全开）", d.glassQuality == GLASS_QUALITY_HIGH, Modifier.weight(1f)) {
                    update(d.copy(glassQuality = GLASS_QUALITY_HIGH))
                }
                GlassPill("低（省电）", d.glassQuality == GLASS_QUALITY_LOW, Modifier.weight(1f)) {
                    update(d.copy(glassQuality = GLASS_QUALITY_LOW))
                }
            }
            Spacer(Modifier.height(Space.sm))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = { pickImage.launch(arrayOf("image/*")) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(RadiusR2),
                ) { Text("选择背景图") }
                OutlinedButton(
                    onClick = { update(d.copy(bgUri = "")) },
                    enabled = d.bgUri.isNotBlank(),
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(RadiusR2),
                ) { Text("恢复默认背景") }
            }
            Spacer(Modifier.height(Space.xs))
            Text(
                if (d.bgUri.isNotBlank()) {
                    "已设置自定义背景：玻璃面板背后会对它做真实的背景模糊 + 折射。"
                } else {
                    "当前用的是内置渐变背景（跟随系统明暗自动切换，不是图片）；" +
                        "选一张自己的图就会替换掉它，点「恢复默认背景」可以换回来。"
                },
                fontSize = TextSize.body,
                color = palette.sub,
            )
            // 压暗只对自定义背景图开放：内置渐变本身已经调过明度，
            // 再压一层会把配色一起糊掉，所以没选图时这个滑杆根本不出现。
            if (d.bgUri.isNotBlank()) {
                Spacer(Modifier.height(Space.xs))
                Text("背景压暗：${(d.bgDim * 100).toInt()}%", fontSize = TextSize.body, color = palette.sub)
                Slider(value = d.bgDim, onValueChange = { update(d.copy(bgDim = it)) }, valueRange = 0f..0.8f)
            }
            Spacer(Modifier.height(Space.xxl))
            // 外观现在自己带一个保存按钮：接口那套已经搬进「高级设置」，别再共用一个「保存」了。
            // 滑杆是即时预览的，但**不点这个按钮就不会落盘** —— 红字挨着它。
            if (dirty) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "未保存，你所做出的改动不会被保存",
                        modifier = Modifier.weight(1f),
                        fontSize = TextSize.body,
                        color = palette.bad,
                    )
                    Spacer(Modifier.width(10.dp))
                    Button(
                        onClick = { saveAppearance() },
                        modifier = Modifier.height(46.dp),
                        shape = RoundedCornerShape(RadiusR2),
                        colors = ButtonDefaults.buttonColors(containerColor = palette.primary),
                    ) { Text("保存外观") }
                }
            } else {
                Button(
                    onClick = { saveAppearance() },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(RadiusR2),
                    colors = ButtonDefaults.buttonColors(containerColor = palette.primary),
                ) { Text("已保存") }
            }
        }

        GlassCard(d.glassAlpha) {
            Text("高级设置", fontSize = TextSize.cardTitle, fontWeight = FontWeight.SemiBold, color = palette.text)
            // 三行「现在是什么样」：入口卡原来只有一句名词罗列（接口地址 / 分析 / 备份 / 诊断），
            // 看完仍然不知道当前配成了什么。摘要只**报状态**、不做判断，要改再进去。
            // 三行分别对应高级设置里的「接入 / 微信内 / 识别」三组，找东西时可以对上号。
            Spacer(Modifier.height(Space.sm))
            Text(
                "接入：${d.baseUrl.ifBlank { "未填" }}　模型 ${d.model.ifBlank { "未填" }}　" +
                    "${if (d.graded) "模型分级" else "直通"}",
                fontSize = TextSize.body,
                color = palette.text,
            )
            Text(
                "微信内：自动分析 ${if (d.enabled) "开" else "关"}　参考 ${d.ctx} 条　间隔 ${d.minIntervalSec}s　" +
                    "白名单 ${if (d.whitelistEnabled) "${d.whitelist.size} 个" else "关"}",
                fontSize = TextSize.body,
                color = palette.sub,
            )
            Text(
                "识别：图片文字 ${if (d.ocrEnabled) "开" else "关"}　本地代理 ${if (d.proxyEnabled) "开" else "关"}　" +
                    "归属地查询 ${if (d.geoEnabled) "开" else "关"}",
                fontSize = TextSize.body,
                color = palette.sub,
            )
            Spacer(Modifier.height(Space.md))
            Button(
                onClick = onOpenAdvanced,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(RadiusR2),
                colors = ButtonDefaults.buttonColors(containerColor = palette.primary),
            ) { Text("高级设置") }
        }

        GlassCard(d.glassAlpha) {
            Text("关于", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = palette.text)
            Spacer(Modifier.height(Space.sm))
            Text("版本：${appVersion(context)}", fontSize = TextSize.body, color = palette.text)
            Spacer(Modifier.height(Space.sm))
            HintText(
                "· 开启后，聊天页最近几条消息会发送到你填写的接口地址，请自行确认该服务可信。\n" +
                    "· API Key 以明文存放在本应用私有目录（不能加密：注入微信进程的代码需要跨进程读取，Keystore 密钥按 UID 隔离读不到）。\n" +
                    "· 只读消息、只把候选回复填进输入框，不会自动发送；但仍属于修改微信客户端行为，有风控风险，建议先用小号。\n" +
                    "· 日志关键字：[Goutou]。",
                fontSize = TextSize.body,
                color = palette.sub,
            )
        }
    }
}

// ================= 高级设置（设置 → 二级页） =================
