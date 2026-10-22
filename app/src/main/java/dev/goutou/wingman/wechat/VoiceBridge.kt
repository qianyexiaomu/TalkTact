package dev.goutou.wingman.wechat

import android.content.Context
import android.view.View
import dev.goutou.wingman.XposedApi
import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.query.FindClass
import org.luckypray.dexkit.query.FindMethod
import org.luckypray.dexkit.query.matchers.ClassMatcher
import org.luckypray.dexkit.query.matchers.MethodMatcher
import java.io.File
import java.lang.ref.WeakReference
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.Collections
import java.util.WeakHashMap

/**
 * 语音转写：用 DexKit 在微信 dex 里找到它的转写组件，提交语音让它自己转。
 *
 * 类名每个版本都混淆，所以只按字符串字面量找类、按方法签名形状认方法。
 * 转写结果不自己读 —— 微信转完会渲染在气泡下面，下一轮读屏就能拿到。
 */
internal object VoiceBridge {

    private class Contract(
        val submit: Method,
        val state: Method,
        val text: Method,
        val chatField: Field,
    ) {
        val messageClass: Class<*> get() = submit.parameterTypes[0]
    }

    @Volatile private var contract: Contract? = null
    @Volatile private var chatClass: Class<*>? = null
    @Volatile private var talkerMethod: Method? = null
    @Volatile private var status = "还没开始（等微信加载）"
    @Volatile private var action = ""
    @Volatile private var started = false
    @Volatile private var libLoaded = false

    /** 转写宿主的活实例。submit 是实例方法，只能靠 hook 构造器收。 */
    private val hosts = ArrayList<WeakReference<Any>>()

    /** 已请求过的消息 id。不能只按 View 去重：RecyclerView 会把同一行复用给别的消息。 */
    private val submittedIds = LinkedHashSet<Long>()
    private val requestedRows: MutableSet<View> =
        Collections.newSetFromMap(WeakHashMap<View, Boolean>())

    fun status(): String = status

    fun action(): String = action

    fun ready(): Boolean = contract != null

    /** 第一次进聊天页时调一次。DexKit 扫 dex 要几秒 CPU，不适合放在冷启动。 */
    fun install(context: Context) {
        if (started) return
        started = true
        val path = runCatching { context.applicationInfo?.sourceDir }.getOrNull()
        if (path.isNullOrBlank()) {
            status = "拿不到微信的 APK 路径，探测没起来"
            return
        }
        val loader = context.classLoader
        Thread({
            runCatching { locate(path, loader) }.onFailure {
                status = "探测异常：${it.javaClass.simpleName}: ${it.message}"
                XposedApi.log("语音探测失败: $it")
            }
        }, "wingman-voice").apply { isDaemon = true }.start()
    }

    /** DexKit 不会自己加载 so，而微信进程的 library path 里没有我们的 lib 目录。 */
    private fun ensureNativeLibrary(): String? {
        if (libLoaded) return null
        if (runCatching { System.loadLibrary("dexkit") }.isSuccess) {
            libLoaded = true
            return null
        }
        val apk = runCatching {
            VoiceBridge::class.java.protectionDomain?.codeSource?.location?.path
        }.getOrNull()
        if (apk.isNullOrBlank()) return "拿不到模块 APK 路径，无法加载 libdexkit.so"
        val dir = File(apk).parentFile ?: return "模块 APK 路径异常：$apk"
        val tried = listOf("lib/arm64/libdexkit.so", "lib/arm64-v8a/libdexkit.so", "lib/libdexkit.so")
        for (rel in tried) {
            val f = File(dir, rel)
            if (!f.exists()) continue
            if (runCatching { System.load(f.absolutePath) }.isSuccess) {
                libLoaded = true
                return null
            }
        }
        return "找不到能加载的 libdexkit.so（试过：$dir/${tried.joinToString("、")}）"
    }

    private fun locate(apkPath: String, loader: ClassLoader) {
        val t0 = System.currentTimeMillis()
        fun elapsed(): String = "%.1fs".format((System.currentTimeMillis() - t0) / 1000.0)

        ensureNativeLibrary()?.let {
            status = it
            return
        }

        val bridge = try {
            DexKitBridge.create(apkPath)
        } catch (t: Throwable) {
            status = "DexKit 打不开 dex：${t.javaClass.simpleName}: ${t.message}"
            return
        }

        try {
            val hosts = runCatching {
                bridge.findClass(
                    FindClass.create().matcher(
                        ClassMatcher.create()
                            .usingStrings("MicroMsg.TransformComponent", "[onChattingPause]"),
                    ),
                )
            }.getOrElse {
                status = "扫描类失败：${it.javaClass.simpleName}: ${it.message}"
                return
            }
            if (hosts.isEmpty()) {
                status = "没找到转写宿主（MicroMsg.TransformComponent）· ${elapsed()}"
                return
            }
            val host = hosts.firstOrNull()?.let { runCatching { it.getInstance(loader) }.getOrNull() }
            if (host == null) {
                status = "找到了 ${hosts.size} 个候选宿主，但一个都加载不出来 · ${elapsed()}"
                return
            }

            val chat = runCatching {
                bridge.findClass(
                    FindClass.create().matcher(
                        ClassMatcher.create()
                            .usingStrings("MicroMsg.ChattingContext", "[notifyDataSetChange]"),
                    ),
                ).firstOrNull()?.getInstance(loader)
            }.getOrNull()
            if (chat == null) {
                status = "有宿主（${host.name}），但没找到聊天上下文 · ${elapsed()}"
                return
            }

            val talker = runCatching {
                bridge.findMethod(
                    FindMethod.create().matcher(
                        MethodMatcher.create().usingStrings("getTalker returns null."),
                    ),
                ).mapNotNull { runCatching { it.getMethodInstance(loader) }.getOrNull() }
                    .firstOrNull {
                        it.declaringClass == chat && it.parameterCount == 0 && it.returnType == String::class.java
                    }
            }.getOrNull()

            val methods = host.methods.filter { !Modifier.isStatic(it.modifiers) }
            val submit = methods.firstOrNull { m ->
                val p = m.parameterTypes
                p.size == 4 && !p[0].isPrimitive &&
                    p[1] == Boolean::class.javaPrimitiveType &&
                    p[2] == Int::class.javaPrimitiveType &&
                    p[3] == Int::class.javaPrimitiveType &&
                    m.returnType == Void.TYPE
            }
            val state = methods.firstOrNull { m ->
                m.parameterTypes.contentEquals(arrayOf(Long::class.javaPrimitiveType)) &&
                    m.returnType.isEnum &&
                    m.returnType.enumConstants.orEmpty().map { (it as Enum<*>).name }
                        .containsAll(listOf("NoTransform", "PreTransform", "Transforming", "Transformed"))
            }
            val text = methods.firstOrNull { m ->
                m.parameterTypes.contentEquals(
                    arrayOf(Long::class.javaPrimitiveType, String::class.java),
                ) && m.returnType == String::class.java
            }
            val chatField = generateSequence(host) { it.superclass }
                .flatMap { it.declaredFields.asSequence() }
                .firstOrNull { it.type == chat && !Modifier.isStatic(it.modifiers) }

            if (submit == null || state == null || text == null || chatField == null) {
                status = "宿主找到了（${host.name}），但转写入口对不上：" +
                    "提交=${submit != null} 状态=${state != null} 文本=${text != null} " +
                    "上下文字段=${chatField != null} · ${elapsed()}"
                return
            }

            listOf<Method>(submit, state, text).forEach { it.isAccessible = true }
            chatField.isAccessible = true
            talker?.isAccessible = true

            contract = Contract(submit, state, text, chatField)
            chatClass = chat
            talkerMethod = talker
            hookHost(host)
            status = "已连接 · 消息类 ${submit.parameterTypes[0].simpleName}" +
                (if (talker == null) " · talker 未解析" else "") +
                " · ${elapsed()}"
            XposedApi.log("语音转写入口已连接：${host.name}")
        } catch (t: Throwable) {
            status = "探测失败：${t.javaClass.simpleName}: ${t.message} · ${elapsed()}"
        } finally {
            runCatching { bridge.close() }
        }
    }

    /** submit 是实例方法，微信没公开入口拿宿主，所以挂构造器收实例。 */
    private fun hookHost(clazz: Class<*>) {
        val module = XposedApi.moduleOrNull()
        if (module == null) {
            action = "拿不到模块实例，hook 不了转写宿主"
            return
        }
        var hooked = 0
        clazz.declaredConstructors.forEachIndexed { i, ctor ->
            val ok = runCatching {
                module.hook(ctor).setId("wingman.voice.host.$i").intercept { chain ->
                    val result = chain.proceed()
                    runCatching { chain.thisObject?.let(::observeHost) }
                    result
                }
                true
            }.getOrDefault(false)
            if (ok) hooked++
        }
        action = if (hooked > 0) "已挂上 $hooked 个构造器，等微信创建宿主" else "构造器一个都没挂上"
    }

    private fun observeHost(host: Any) {
        synchronized(hosts) {
            hosts.removeAll { it.get() == null || it.get() === host }
            hosts.add(0, WeakReference(host))
            while (hosts.size > 4) hosts.removeAt(hosts.lastIndex)
        }
    }

    private fun activeHost(): Any? = synchronized(hosts) { hosts.firstOrNull()?.get() }

    /**
     * 请微信转写这一行语音。主线程调用，只在还没有转写文字时调。
     *
     * 不读结果：微信转完会渲染在气泡下方，读屏那一步自然拿到。
     */
    fun requestTranscribe(row: View) {
        val c = contract ?: return
        val host = activeHost()
        if (host == null) {
            action = "还没有转写宿主实例（换一次聊天页会重建）"
            return
        }
        val msg = msgInfoOf(row, c.messageClass)
        if (msg == null) {
            action = "这一行里没找到消息对象"
            return
        }

        val id = msgIdOf(msg)
        if (id != null && id > 0) {
            synchronized(submittedIds) {
                if (!submittedIds.add(id)) return
                if (submittedIds.size > 300) submittedIds.firstOrNull()?.let(submittedIds::remove)
            }
            val state = runCatching { (c.state.invoke(host, id) as Enum<*>).name }.getOrNull()
            if (state != null && state != "NoTransform") {
                action = "这一条微信已经处理过了（$state）"
                return
            }
        } else if (!requestedRows.add(row)) {
            return
        }

        runCatching { c.submit.invoke(host, msg, false, -1, 0) }
            .onSuccess {
                action = "已请微信转写这一条"
                XposedApi.log("语音转写已提交")
            }
            .onFailure {
                action = "提交转写失败：${it.javaClass.simpleName}: ${it.message}"
                if (id != null) {
                    synchronized(submittedIds) { submittedIds.remove(id) }
                } else {
                    requestedRows.remove(row)
                }
            }
    }

    /** 消息 id：无参、返回 long、名字含 msgid。先找方法再找字段。 */
    private fun msgIdOf(msg: Any): Long? {
        generateSequence(msg.javaClass as Class<*>) { it.superclass }
            .flatMap { it.declaredMethods.asSequence() }
            .firstOrNull {
                it.parameterCount == 0 && it.returnType == Long::class.javaPrimitiveType &&
                    it.name.contains("msgid", ignoreCase = true)
            }
            ?.let { m ->
                return runCatching { m.apply { isAccessible = true }.invoke(msg) as? Long }.getOrNull()
            }
        generateSequence(msg.javaClass as Class<*>) { it.superclass }
            .flatMap { it.declaredFields.asSequence() }
            .firstOrNull {
                it.type == Long::class.javaPrimitiveType && it.name.contains("msgid", ignoreCase = true)
            }
            ?.let { f ->
                return runCatching { f.apply { isAccessible = true }.getLong(msg) }.getOrNull()
            }
        return null
    }

    /** 从 ViewHolder 里挖微信的消息对象（类型就是 submit 的第一个参数）。 */
    private fun msgInfoOf(row: View, type: Class<*>): Any? {
        val holder = row.tag ?: return null
        val field = generateSequence(holder.javaClass as Class<*>) { it.superclass }
            .flatMap { it.declaredFields.asSequence() }
            .firstOrNull { it.type == type && !Modifier.isStatic(it.modifiers) }
            ?: return null
        return runCatching { field.apply { isAccessible = true }.get(holder) }.getOrNull()
    }

    /** 诊断：当前解析到的类与方法。 */
    fun probe(): String = buildString {
        append(status)
        chatClass?.let { append("\n聊天上下文：").append(it.name) }
        talkerMethod?.let { append("\ntalker：").append(it.name) }
        contract?.let { c ->
            append("\n提交：").append(c.submit.name).append("(")
                .append(c.submit.parameterTypes.joinToString(", ") { it.simpleName }).append(")")
            append("\n状态：").append(c.state.name)
            append("\n文本：").append(c.text.name)
        }
    }
}
