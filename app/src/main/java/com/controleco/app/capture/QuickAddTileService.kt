package com.controleco.app.capture

import android.app.PendingIntent
import android.content.Intent
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.core.service.quicksettings.PendingIntentActivityWrapper
import androidx.core.service.quicksettings.TileServiceCompat
import com.controleco.app.MainActivity

/**
 * 快捷设置磁贴「记一笔」（手动入口，见
 * docs/自动记账捕获机制.md 第一节）：下拉快捷面板点一下就进快速记账页，
 * 无权限要求、无界面后台逻辑。
 */
class QuickAddTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        qsTile?.apply {
            label = "记一笔"
            state = Tile.STATE_INACTIVE
            updateTile()
        }
    }

    override fun onClick() {
        super.onClick()
        val intent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra(CaptureCenter.EXTRA_ROUTE, ROUTE_QUICK_ADD)
        // AndroidX 统一选择新旧系统的入口，API 34+ 使用不可变 PendingIntent。
        TileServiceCompat.startActivityAndCollapse(
            this,
            PendingIntentActivityWrapper(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT, false),
        )
    }

    private companion object {
        const val ROUTE_QUICK_ADD = "quickAdd"
    }
}
