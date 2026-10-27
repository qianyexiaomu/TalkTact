package dev.goutou.wingman.config

/**
 * 宿主：本模块可以挂进去的 App。
 *
 * 为什么要有这一层：注入侧**按宿主调出来的**只有两件事 —— 怎么认聊天页（读屏判据）、
 * 怎么判「这句是我说的」（判边）；而代理通道 / 大模型 / 提示词 / 风格 / 角色档案跟宿主毫无关系。
 * 把「有哪些宿主」收在一个地方，入口、scope、诊断、界面才有同一个出处；
 * 否则每加一个 App 就是散在四五个文件里的字符串，改漏一处就是静默失效。
 *
 * ⚠️ 新增宿主时要同时改三处（都在这里能查到）：
 *   1. 这个枚举；
 *   2. `src/main/resources/META-INF/xposed/scope.list` —— 不写进去，框架根本不会把模块注入那个 App；
 *   3. `AndroidManifest.xml` 的 `<queries>` —— 不声明就查不到对方版本号（Android 11+ 包可见性）。
 */
enum class Host(val pkg: String, val label: String) {

    WECHAT("com.tencent.mm", "微信"),

    /**
     * QQ。
     *
     * **目前只做到「能挂进去 + 能抓界面结构」**（探针阶段，见 [adapted]）。
     * 读屏判据还是照微信的形状调的：输入框浮在列表上层、气泡绿=我、标题在状态栏下 96dp 那一带 ——
     * 这几条在 QQ 上都没验证过（QQ 默认蓝色气泡且可换肤、输入栏固定在底部、
     * 整机只有 SplashActivity + Fragment），所以在 QQ 里**不出候选、不往输入框写字**，
     * 只保留「抓当前界面」这条通道，先拿真机结构树当依据。
     */
    QQ("com.tencent.mobileqq", "QQ");

    /**
     * 判据是否已经按这个宿主调过。
     *
     * false = 探针模式：模块加载、心跳、诊断都正常，但面板不参与聊天页判定。
     * 宁可什么都不显示，也不能把微信的判据套在别的 App 上 —— 那只会读出错的上下文，
     * 甚至把候选填进不相干的输入框。
     */
    val adapted: Boolean get() = this == WECHAT

    /**
     * 主进程名。
     *
     * QQ 除了主进程还有 `:MSF` / `:qzone` / `:peak` 这些子进程。模块只该在主进程里干活：
     * 子进程没有界面，挂 Activity 钩子没有意义，还会让「已加载」的日志重复好几遍。
     */
    val mainProcess: String get() = pkg

    companion object {
        /** 当前进程挂的是哪个宿主。只在 [dev.goutou.wingman.WingmanEntry] 里写一次。 */
        @Volatile
        var current: Host? = null
            internal set

        /**
         * 按包名找宿主。
         *
         * 用 `values()` 而不是 `entries`：`entries` 是 Kotlin 1.9 新加的 stdlib API，
         * 而这段代码跑在**宿主进程里**，类加载是 parent-first —— 微信 / QQ 自己带着一份
         * kotlin-stdlib，万一它那份比 1.9 老，`entries` 会在真机上变成 NoSuchMethodError，
         * 而且只在注入侧炸、App 里一点事没有，很难查。`values()` 只是个数组拷贝，没有这个风险。
         */
        fun of(pkg: String?): Host? = pkg?.let { p -> values().firstOrNull { it.pkg == p } }
    }
}
