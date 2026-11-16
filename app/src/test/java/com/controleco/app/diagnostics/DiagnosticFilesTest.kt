package com.controleco.app.diagnostics

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import java.util.zip.ZipFile

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class DiagnosticFilesTest {
    @get:Rule val temp = TemporaryFolder()
    private fun at(day: String) = LocalDate.parse(day).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    private fun event(time: Long, text: String = "支付成功 ¥15.00") = DiagnosticRecord(time, "page", "支付宝", "已解析", text)

    @Test fun retainsSevenCalendarDaysAndReportsEvictionsAfterRestart() {
        var now = at("2026-11-01")
        val root = temp.newFolder()
        val disk = DiagnosticFiles(root, { now })
        disk.refresh()
        for (day in 1..10) {
            now = at("2026-11-${day.toString().padStart(2, '0')}")
            disk.append(event(now))
        }
        val reopened = DiagnosticFiles(root, { now }).refresh()
        assertEquals(7L, reopened.count)
        assertEquals(at("2026-11-04"), reopened.firstAt)
        assertEquals(3L, reopened.expiredChunks)
        assertEquals(0L, reopened.evictedChunks)
        now = at("2026-11-18")
        assertEquals(0L, DiagnosticFiles(root, { now }).refresh().count)
    }

    @Test fun capacityRotationKeepsNewestChunksAndPersistsMissingHistoryCount() {
        val now = at("2026-11-14")
        val root = temp.newFolder()
        val disk = DiagnosticFiles(root, { now }, maximumBytes = 1000, chunkBytes = 350)
        disk.refresh()
        repeat(30) { disk.append(event(now + it, "交易$it " + "样本".repeat(40))) }
        val current = disk.refresh()
        assertTrue(current.bytes <= 1000)
        assertTrue(current.evictedChunks > 0)
        assertEquals("交易29 " + "样本".repeat(40), current.recent.first().detail)
        assertEquals(current.evictedChunks, DiagnosticFiles(root, { now }, 1000, 350).refresh().evictedChunks)
    }

    @Test fun masksSensitiveStringsWithoutCorruptingJsonNumbersOrMoney() {
        val root = temp.newFolder()
        val now = at("2026-11-14")
        val disk = DiagnosticFiles(root, { now })
        disk.refresh()
        disk.append(DiagnosticRecord(now, "notification", "支付宝", "已捕获", "付款成功 ¥15.00 手机13812345678 卡6222021234567890123 邮箱test@example.com 尾号1234 卡 **** 5678",
            mapOf("sourceTime" to now.toString(), "amountCents" to "1500", "text" to "电话 +86 13912345678")))
        val destination = File(temp.newFolder(), "diagnostic.zip")
        disk.export(destination, JSONObject().put("exportedAt", now))
        ZipFile(destination).use { zip ->
            val manifest = JSONObject(zip.getInputStream(zip.getEntry("manifest.json")).reader().readText())
            assertEquals(now, manifest.getLong("exportedAt"))
            val entry = zip.entries().asSequence().first { it.name.endsWith(".jsonl") }
            val body = zip.getInputStream(entry).reader().readText()
            assertFalse(body.contains("13812345678")); assertFalse(body.contains("13912345678"))
            assertFalse(body.contains("6222021234567890123")); assertFalse(body.contains("test@example.com")); assertFalse(body.contains("尾号1234"))
            assertFalse(body.contains("5678"))
            val line = JSONObject(body.trim())
            assertEquals(now, line.getLong("time"))
            assertEquals(now.toString(), line.getJSONObject("fields").getString("sourceTime"))
            assertTrue(body.contains("¥15.00"))
        }
    }

    @Test fun interruptedTrailingLineIsRecoveredBeforeAppending() {
        val root = temp.newFolder()
        val now = at("2026-11-14")
        val disk = DiagnosticFiles(root, { now })
        disk.refresh(); disk.append(event(now, "第一笔"))
        root.listFiles()!!.first { it.name.endsWith(".jsonl") }.appendText("{\"time\":")
        disk.append(event(now + 1, "第二笔"))
        val current = disk.refresh()
        assertEquals(2L, current.count)
        assertEquals(1L, current.damagedLines)
    }

    @Test fun exportIsAllowListedAndClearDoesNotTouchOtherFiles() {
        val root = temp.newFolder()
        val now = at("2026-11-14")
        val disk = DiagnosticFiles(root, { now })
        disk.refresh(); disk.append(event(now))
        val unrelated = File(root, "private-ledger.db").apply { writeText("private") }
        val destination = File(temp.newFolder(), "diagnostic.zip")
        disk.export(destination, JSONObject())
        ZipFile(destination).use { zip -> assertFalse(zip.entries().asSequence().any { it.name.contains("private-ledger") }) }
        assertEquals(0L, disk.clear().count)
        assertTrue(unrelated.isFile)
    }
}
