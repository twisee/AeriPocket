package com.controleco.app.capture

import com.controleco.app.capture.page.PageSnapshot

/** 仅在已观察到抖音支付 SDK 窗口之后短时补采，不凭结算金额推断成功。 */
internal class TransientPaymentCapture {
    private var paymentPackage: String? = null
    private var until = 0L

    fun onWindow(packageName: String, activity: String, now: Long) {
        if (paymentPackage != null && paymentPackage != packageName) clear()
        if (packageName in douyinPackages && activity.startsWith("com.android.ttcjpaysdk.")) {
            // 同次 SDK 切换不续期，避免窗口事件风暴让补采无限运行。
            if (!active(packageName, now)) {
                paymentPackage = packageName
                until = now + 12_000L
            }
        }
    }

    fun active(packageName: String, now: Long): Boolean = paymentPackage == packageName && now < until

    fun preserve(snapshot: PageSnapshot, now: Long): Boolean = active(snapshot.packageName, now) &&
        snapshot.lines.take(2).any { line -> successTitles.any { line.trim().startsWith(it) } }

    fun clear() { paymentPackage = null; until = 0L }

    private companion object {
        val douyinPackages = setOf("com.ss.android.ugc.aweme", "com.ss.android.ugc.live", "com.ss.android.ugc.livelite")
        val successTitles = listOf("支付成功", "付款成功", "下单成功", "交易成功", "已支付")
    }
}
