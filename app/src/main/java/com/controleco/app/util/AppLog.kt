package com.controleco.app.util

import android.util.Log

/**
 * 统一日志出口。tag 固定为 AeriPocket + 子标签，便于 Logcat 过滤；
 * 调试开关保留给后续自动记账链路（通知/无障碍采集需要大量排查日志）。
 */
object AppLog {
    private const val TAG = "AeriPocket"

    @Volatile
    var debugEnabled: Boolean = true

    fun d(sub: String, msg: String) {
        diagnostic(sub, msg)
        if (debugEnabled) Log.d(TAG, "[$sub] $msg")
    }

    fun i(sub: String, msg: String) {
        diagnostic(sub, msg)
        Log.i(TAG, "[$sub] $msg")
    }

    fun w(sub: String, msg: String, tr: Throwable? = null) {
        diagnostic(sub, msg, tr, warning = true)
        Log.w(TAG, "[$sub] $msg", tr)
    }

    fun e(sub: String, msg: String, tr: Throwable? = null) {
        diagnostic(sub, msg, tr, warning = true)
        Log.e(TAG, "[$sub] $msg", tr)
    }

    private fun diagnostic(sub: String, msg: String, error: Throwable? = null, warning: Boolean = false) {
        // Deliberate allow-list: do not persist general Logcat, chat messages or unrelated app data.
        if (sub !in setOf("Page", "Ntf", "Overlay", "Dedup", "Trace", "Sms", "tx", "Refund")) return
        val metadata = msg.startsWith("a11y ") || msg.startsWith("collect empty") || msg.startsWith("overlay") ||
            msg.startsWith("autoEntry") || msg.startsWith("captureEnabled") || msg.startsWith("batch deleted")
        if (!warning && !metadata) return
        val safe = msg.substringBefore("texts=[").substringBefore("text=[").substringBefore("body=").substringBefore("title=[")
        com.controleco.app.diagnostics.CaptureDiagnostics.record("service", sub,
            if (warning) "异常或提示" else "状态变化", safe, mapOf("errorType" to error?.javaClass?.simpleName.orEmpty()))
    }
}
