package com.controleco.app.capture

import android.app.Application
import android.app.Notification
import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import android.os.Process
import android.service.notification.StatusBarNotification
import android.service.quicksettings.TileService
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.annotation.RealObject
import org.robolectric.shadows.ShadowService

/** Exercise real notification bundle formats and both quick-settings launch paths. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26, 29, 35], application = Application::class)
class NotificationCompatibilityTest {
    @Suppress("DEPRECATION") // This public constructor is available across the tested SDK range.
    private fun snapshot(notification: Notification): NotificationText {
        val sbn = StatusBarNotification(
            "com.tencent.mm", "com.tencent.mm", 1, "test", Process.myUid(), 0, 0,
            notification, Process.myUserHandle(), 1_700_000_000_000L,
        )
        return NotificationCaptureService().snapshotOf(sbn)
    }

    @Test fun messagingStyleRetainsAmountInEarlierMessageOnOldAndNewAndroid() {
        val context = RuntimeEnvironment.getApplication()
        val sender = Person.Builder().setName("微信支付").build()
        val notification = NotificationCompat.Builder(context, "test")
            .setSmallIcon(android.R.drawable.ic_menu_add)
            .setStyle(NotificationCompat.MessagingStyle(Person.Builder().setName("我").build())
                .addMessage("退款到账¥12.80", 1_700_000_000_000L, sender)
                .addMessage("请查看详情", 1_700_000_001_000L, sender))
            .build()
        val result = snapshot(notification)
        assertTrue(result.mergedText().contains("退款到账¥12.80"))
        assertTrue(result.mergedText().contains("请查看详情"))
    }

    @Test fun inboxDetailsAndOrdinaryTextSurviveMalformedMessagingExtras() {
        val notification = NotificationCompat.Builder(RuntimeEnvironment.getApplication(), "test")
            .setSmallIcon(android.R.drawable.ic_menu_add)
            .setContentTitle("微信支付")
            .setContentText("[2条]支付提醒")
            .setSubText("交易详情")
            .setStyle(NotificationCompat.InboxStyle().addLine("已支付¥8.50").addLine("商户：示例店"))
            .build()
        notification.extras.putParcelableArray(Notification.EXTRA_MESSAGES, arrayOf(Bundle()))
        val result = snapshot(notification)
        assertEquals("微信支付", result.title)
        assertTrue(result.mergedText().contains("已支付¥8.50"))
        assertTrue(result.mergedText().contains("商户：示例店"))
        assertTrue(result.mergedText().contains("交易详情"))
    }

    @Test
    @Config(shadows = [PendingIntentTileShadow::class])
    fun quickSettingsOpensQuickAddOnOldAndNewAndroid() {
        val controller = Robolectric.buildService(QuickAddTileService::class.java).create()
        try {
            controller.get().onClick()
            val intent = shadowOf(RuntimeEnvironment.getApplication()).nextStartedActivity
            assertNotNull(intent)
            assertEquals("com.controleco.app.MainActivity", intent.component?.className)
            assertEquals("quickAdd", intent.getStringExtra(CaptureCenter.EXTRA_ROUTE))
            assertTrue(intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
        } finally {
            controller.destroy()
        }
    }
}

/** Robolectric 4.14 only implements the Intent overload; model the new system entry. */
@Implements(TileService::class)
class PendingIntentTileShadow : ShadowService() {
    @RealObject private lateinit var service: TileService

    @Implementation(maxSdk = 33)
    protected fun startActivityAndCollapse(intent: Intent) {
        service.startActivity(intent)
    }

    @Implementation(minSdk = 34)
    protected fun startActivityAndCollapse(pendingIntent: PendingIntent) {
        assertTrue(pendingIntent.isImmutable)
        pendingIntent.send()
    }
}
