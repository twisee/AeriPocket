package com.controleco.app.personal

import com.controleco.app.capture.*
import com.controleco.app.capture.page.*
import com.controleco.app.data.db.TxType
import org.junit.Assert.*
import org.junit.Test

/** Synthetic regressions for the reported Meituan gross/net and cross-source pattern. */
class CaptureRegressionTest {
    private val pkg = NotificationRules.MEITUAN_TAKEOUT
    private fun snapshot(vararg text: String) = PageSnapshot(packageName = pkg, captureTime = 1800000000000L,
        nodes = text.map { PageNode(text = it) })
    @Test fun explicitPaidAmountOutranksPriceBesideSuccessTitle() {
        val result = MeituanPageParser.parse(snapshot("支付成功", "¥38.00", "商品总额 ¥38.00", "优惠券 ¥10.00",
            "订单实付", "28.00", "支付方式", "支付宝", "完成"), PageFlowTracker().flow(pkg)) as PageParseResult.Candidate
        assertEquals(2800L, result.candidate.amountCents)
    }
    @Test fun grossOnlyPageDoesNotConsumeFlowAndLaterPaidPageCanEmit() {
        val flow = PageFlowTracker().flow(pkg)
        val incomplete = MeituanPageParser.parse(snapshot("支付成功", "¥38.00", "商品总额 ¥38.00", "优惠券 ¥0.00", "完成"), flow)
        assertFalse(incomplete is PageParseResult.Candidate)
        val complete = MeituanPageParser.parse(snapshot("支付成功", "¥38.00", "商品总额 ¥38.00", "优惠券 ¥0.00", "实付款 ¥38.00", "完成"), flow)
        assertEquals(3800L, (complete as PageParseResult.Candidate).candidate.amountCents)
    }
    private fun evidence(pkg: String, merchant: String, order: String = "", instrument: String = "") = CrossAppLinker.Evidence(
        pkg, 2800, TxType.EXPENSE, false, 1800000000000L, counterparty = merchant, orderId = order, instrument = instrument)
    @Test fun genericAlipayConsumptionLabelDoesNotConflictWithMeituan() {
        assertTrue(CrossAppLinker.isSamePurchase(evidence(pkg, "美团外卖", instrument = "支付宝"),
            evidence(NotificationRules.ALIPAY, "支付宝消费")))
        assertFalse(CrossAppLinker.isSamePurchase(evidence(pkg, "演示甲店", instrument = "支付宝"),
            evidence(NotificationRules.ALIPAY, "演示乙店")))
    }
    @Test fun sameOrderIdOverridesDisplayNameButStillHonorsAmountAndChannel() {
        val page = evidence(pkg, "演示店铺", "SYNTHETIC-ORDER", "支付宝")
        val pay = evidence(NotificationRules.ALIPAY, "演示收单公司", "SYNTHETIC-ORDER")
        assertEquals(CrossAppLinker.LinkReason.ORDER_ID, CrossAppLinker.linkReason(page, pay))
        assertFalse(CrossAppLinker.isSamePurchase(page, pay.copy(amountCents = 3800)))
        assertFalse(CrossAppLinker.isSamePurchase(page, pay.copy(sourcePackage = NotificationRules.WECHAT)))
    }
}
