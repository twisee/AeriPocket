package com.controleco.app.capture

import com.controleco.app.capture.page.*
import org.junit.Assert.*
import org.junit.Test

class CaptureRecoveryTest {
    private val pkg = "com.ss.android.ugc.livelite"
    private val sdk = "com.android.ttcjpaysdk.thirdparty.front.counter.dypay.activity.DyPayETActivity"
    private fun snapshot(vararg text: String) = PageSnapshot(pkg, captureTime = 1794793067000L,
        nodes = text.map { PageNode(text = it) })

    @Test fun `cold disconnected listener requests immediately without disconnect callback`() {
        assertEquals(0L, ListenerRecoveryPolicy().waitMillis(500, true, false))
    }
    @Test fun `repeated foreground checks respect recovery backoff`() {
        val p = ListenerRecoveryPolicy()
        p.attempted(500)
        assertEquals(1500L, p.waitMillis(500, true, false))
        assertEquals(1000L, p.waitMillis(1000, true, false))
        assertEquals(0L, p.waitMillis(2000, true, false))
        p.attempted(2000)
        assertEquals(6000L, p.waitMillis(2000, true, false))
        p.attempted(8000)
        assertEquals(30000L, p.waitMillis(8000, true, false))
        p.attempted(38000)
        assertEquals(300000L, p.waitMillis(38000, true, false))
    }
    @Test fun `connected or unpermitted listener stops recovery and resets backoff`() {
        val p = ListenerRecoveryPolicy()
        p.attempted(0)
        assertNull(p.waitMillis(100, true, true))
        assertEquals(0, p.attempts)
        assertEquals(0L, p.waitMillis(200, true, false))
        p.attempted(200)
        assertNull(p.waitMillis(300, false, false))
        assertEquals(0, p.attempts)
    }
    @Test fun `short success frame preserved only after observed payment SDK`() {
        val p = TransientPaymentCapture()
        val success = snapshot("支付成功", "实付款", "¥9.90", "完成")
        assertFalse(p.preserve(success, 1000))
        p.onWindow(pkg, sdk, 1000)
        assertTrue(p.preserve(success, 1400))
        val parsed = DouyinPageParser.parse(success, PageFlowTracker().flow(pkg)) as PageParseResult.Candidate
        assertEquals(990L, parsed.candidate.amountCents)
    }
    @Test fun `checkout price and empty windows are never successful evidence`() {
        val p = TransientPaymentCapture()
        p.onWindow(pkg, sdk, 1000)
        assertTrue(p.active(pkg, 2500)) // 旧版三次 320ms 补采结束后仍可继续有限补采
        assertFalse(p.preserve(snapshot("现在付款", "实付价¥9.9", "极速付¥9.90"), 2500))
        assertFalse(p.preserve(snapshot(), 2500))
    }
    @Test fun `payment retries expire and window storms do not extend them`() {
        val p = TransientPaymentCapture()
        p.onWindow(pkg, sdk, 1000)
        p.onWindow(pkg, sdk, 12000)
        assertTrue(p.active(pkg, 12999))
        assertFalse(p.active(pkg, 13000))
        assertFalse(p.preserve(snapshot("支付成功", "¥9.90"), 13000))
    }
    @Test fun `leaving payment app or service reset clears transient session`() {
        val p = TransientPaymentCapture()
        p.onWindow(pkg, sdk, 1000)
        p.onWindow("com.tencent.mm", "LauncherUI", 1500)
        assertFalse(p.active(pkg, 1500))
        p.onWindow(pkg, sdk, 2000)
        p.clear()
        assertFalse(p.active(pkg, 2000))
    }
}
