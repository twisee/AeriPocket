package com.controleco.app.capture.page

import android.app.Application
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.zip.ZipFile

/** 私有诊断仅从外部路径读取，不把原文/商户/卡号写入仓库或测试报告。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class DiagnosticReplayTest {
    @Test fun `external diagnostic pages preserve parsed amounts and recover the two missed results`() {
        val file = System.getenv("AERIPOCKET_DIAGNOSTIC_ZIP")?.let(::File)
        assumeTrue("Optional private diagnostic not supplied", file?.isFile == true)
        var recoveredFive = false
        var recoveredNinetyNine = false
        var pages = 0
        ZipFile(file!!).use { zip ->
            zip.entries().asSequence().filter { it.name.endsWith(".jsonl") }.forEach { entry ->
                zip.getInputStream(entry).bufferedReader().useLines { lines -> lines.forEach { line ->
                    val event = JSONObject(line)
                    if (event.optString("kind") != "page") return@forEach
                    pages++
                    val fields = event.getJSONObject("fields")
                    val pkg = event.getString("source")
                    val text = fields.optString("text")
                    val s = PageSnapshot(pkg, activity = fields.optString("activity"),
                        captureTime = event.getLong("time"), nodes = text.split(" | ").map { PageNode(text = it) })
                    val result = PageParserRegistry.parse(s, PageFlowTracker().flow(pkg))
                    val candidate = (result as? PageParseResult.Candidate)?.candidate
                    if (event.getString("status") == "已解析") {
                        assertEquals("Previously parsed amount must be preserved", fields.getString("amountCents").toLong(),
                            candidate?.amountCents)
                    }
                    if (text.contains("支付成功￥5.00") && event.getLong("time") > 1794787200000L) {
                        assertEquals(500L, candidate?.amountCents)
                        recoveredFive = true
                    }
                    if (text.contains("支付成功￥99.00")) {
                        assertEquals(9900L, candidate?.amountCents)
                        recoveredNinetyNine = true
                    }
                } }
            }
        }
        assertEquals(3469, pages)
        assertTrue(recoveredFive)
        assertTrue(recoveredNinetyNine)
    }
}
