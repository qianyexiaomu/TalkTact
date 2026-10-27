package dev.goutou.wingman

import dev.goutou.wingman.config.Host
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HostTest {

    @Test fun `known hosts resolve by package`() {
        assertEquals(Host.WECHAT, Host.of("com.tencent.mm"))
        assertEquals(Host.QQ, Host.of("com.tencent.mobileqq"))
    }

    @Test fun `sub process names are not hosts`() {
        // QQ 的 :MSF / :qzone 是进程名，不是包名。拿进程名去套宿主必须落空 ——
        // 否则入口会把子进程也当成宿主挂一遍钩子（入口里另外还有 processName 那道闸）。
        assertNull(Host.of("com.tencent.mobileqq:MSF"))
        assertNull(Host.of("com.tencent.mm:push"))
        assertNull(Host.of(null))
    }

    @Test fun `only wechat is adapted`() {
        // 这条是探针模式的开关：判据没按 QQ 调过之前，面板不许参与聊天页判定。
        // 哪天真把 QQ 适配好了，改的是 Host.adapted，这个测试会跟着变红提醒改测试 —— 这是故意的。
        assertTrue(Host.WECHAT.adapted)
        assertFalse(Host.QQ.adapted)
    }

    @Test fun `main process equals package`() {
        // 主进程名就是包名（微信 / QQ 都是这样），入口用它过滤子进程
        for (host in Host.values()) assertEquals(host.pkg, host.mainProcess)
    }
}
