package com.controleco.app.capture.page

import com.controleco.app.capture.NotificationRules
import org.junit.Assert.*
import org.junit.Test

class AlipayResultRegionTest {
    // 2026-11-14/16 诊断版式，商户/卡号使用合成值；结果容器 id 来自真机。
    private fun snapshot(amount: String, merchant: String, methodLabel: String, method: String,
                         result: List<String>? = null): PageSnapshot {
        val background = listOf("首页", "消息盒子 测试商户 付款成功￥88.00 刚刚", "收付款", "支付中...", "花呗")
        val nodes = mutableListOf(PageNode(depth = 0))
        nodes += background.map { PageNode(text = it, depth = 1, parentIndex = 0) }
        val root = nodes.size
        nodes += PageNode(viewId = "onsitepay_result_page", depth = 1, parentIndex = 0)
        nodes += (result ?: listOf("支付成功￥$amount", "支付成功", amount, merchant, "￥$amount",
            methodLabel, method, "完成", "回首页")).map { PageNode(text = it, depth = 2, parentIndex = root) }
        nodes += PageNode(text = "背景里的其他金额￥999.00", depth = 1, parentIndex = 0)
        return PageSnapshot(NotificationRules.ALIPAY, captureTime = 1794790718000L, nodes = nodes)
    }

    private fun parse(snapshot: PageSnapshot, flow: PageFlow = PageFlowTracker().flow(NotificationRules.ALIPAY)) =
        AlipayPageParser.parse(snapshot, flow)

    @Test fun `canteen result under payment code background is captured`() {
        val result = parse(snapshot("5.00", "测试食堂", "交易方式", "花呗")) as PageParseResult.Candidate
        assertEquals(500L, result.candidate.amountCents)
        assertEquals("测试食堂", result.candidate.counterparty)
        assertEquals("花呗", result.candidate.paymentMethod)
    }

    @Test fun `99 result ignores home preview and background instrument`() {
        val result = parse(snapshot("99.00", "测试俱乐部", "付款方式", "中国银行储蓄卡(0000)")) as PageParseResult.Candidate
        assertEquals(9900L, result.candidate.amountCents)
        assertEquals("测试俱乐部", result.candidate.counterparty)
        assertEquals("0000", result.candidate.instrument?.tail)
    }

    @Test fun `home preview without result container remains rejected`() {
        val s = snapshot("5.00", "商户", "付款方式", "花呗")
        assertEquals(PageParseResult.NotATradePage, parse(s.copy(nodes = s.nodes.take(6))))
    }

    @Test fun `empty result does not fall back to background successful preview`() {
        assertFalse(parse(snapshot("5.00", "商户", "付款方式", "花呗", emptyList())) is PageParseResult.Candidate)
    }

    @Test fun `historical detail inside result region is rejected`() {
        assertEquals(PageParseResult.NotATradePage, parse(snapshot("5.00", "商户", "付款方式", "花呗",
            listOf("支付成功￥5.00", "账单详情", "交易单号", "1234"))))
    }

    @Test fun `zero result does not use positive background amount`() {
        assertFalse(parse(snapshot("0.00", "商户", "付款方式", "花呗")) is PageParseResult.Candidate)
    }

    @Test fun `background changes do not duplicate same successful result`() {
        val flow = PageFlowTracker().flow(NotificationRules.ALIPAY)
        val s = snapshot("5.00", "商户", "付款方式", "花呗")
        assertTrue(parse(s, flow) is PageParseResult.Candidate)
        assertFalse(parse(s.copy(nodes = s.nodes.toMutableList().also {
            it[1] = it[1].copy(text = "首页刷新后的背景")
        }), flow) is PageParseResult.Candidate)
    }

    private fun inlineResult() = PageSnapshot(NotificationRules.ALIPAY, nodes = listOf(
        "首页", "消息盒子 测试商户 付款成功￥99.00 刚刚", "收付款", "支付中...", "条形码", "二维码",
        "花呗", "支付成功￥99.00", "支付成功", "99.00", "测试俱乐部", "￥99.00",
        "付款方式", "中国银行储蓄卡(0000)", "完成", "收付款", "花呗",
    ).map { PageNode(text = it) })

    @Test fun `99 diagnostic text result works when structure was truncated`() {
        val result = parse(inlineResult()) as PageParseResult.Candidate
        assertEquals(9900L, result.candidate.amountCents)
        assertEquals("测试俱乐部", result.candidate.counterparty)
        assertEquals("0000", result.candidate.instrument?.tail)
    }

    @Test fun `inline success without payment code or with mismatched amount is rejected`() {
        val s = inlineResult()
        assertFalse(parse(s.copy(nodes = s.nodes.filterNot { it.text == "二维码" })) is PageParseResult.Candidate)
        assertFalse(parse(s.copy(nodes = s.nodes.map {
            if (it.text == "￥99.00") it.copy(text = "￥199.00") else it
        })) is PageParseResult.Candidate)
    }
}
