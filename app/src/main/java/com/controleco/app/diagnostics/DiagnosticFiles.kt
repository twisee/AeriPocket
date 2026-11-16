package com.controleco.app.diagnostics

import org.json.JSONObject
import org.json.JSONArray
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

internal data class DiagnosticRecord(
    val time: Long, val kind: String, val source: String, val status: String,
    val detail: String, val fields: Map<String, String> = emptyMap(),
) {
    fun json(): JSONObject = JSONObject().put("time", time).put("kind", kind).put("source", source)
        .put("status", status).put("detail", DiagnosticPrivacy.mask(detail))
        .put("fields", JSONObject(fields.mapValues { DiagnosticPrivacy.field(it.key, it.value) }))

    companion object {
        fun read(line: String): DiagnosticRecord {
            val value = JSONObject(line)
            return DiagnosticRecord(value.getLong("time"), value.getString("kind"), value.getString("source"),
                value.getString("status"), value.getString("detail"))
        }
    }
}

internal object DiagnosticPrivacy {
    private val numericFields = setOf("sourceTime", "amountCents", "nodeCount", "windowId", "transactionId")
    fun field(key: String, value: String): String = if (key in numericFields && value.matches(Regex("-?[0-9]+"))) value else mask(value)
    private val phone = Regex("(?<![0-9])(?:\\+?86[ -]?)?1[3-9][0-9]{9}(?![0-9])")
    private val longNumber = Regex("(?<![0-9])[0-9](?:[ -]?[0-9]){12,}(?![0-9])")
    private val cardTail = Regex("(?i)(?:\\*{2,}[ ]*|尾号[：: ]*|卡号[：: ]*)[0-9]{4,}")
    private val email = Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}")
    fun mask(text: String): String = email.replace(cardTail.replace(longNumber.replace(phone.replace(text) {
        "[手机号已遮罩]"
    }) { "[长号码已遮罩]" }) { "[卡号已遮罩]" }) { "[邮箱已遮罩]" }

    fun maskJson(value: JSONObject): JSONObject {
        fun clean(item: Any?): Any? = when (item) {
            is String -> mask(item)
            is JSONObject -> maskJson(item)
            is JSONArray -> JSONArray().also { out -> for (i in 0 until item.length()) {
                val member = item.get(i)
                out.put(when (member) { is JSONObject -> maskJson(member); is String -> mask(member); else -> member })
            } }
            else -> item
        }
        return JSONObject().also { out -> value.keys().forEach { key ->
            val member = value.get(key)
            out.put(key, if (member is String) field(key, member) else clean(member))
        } }
    }
}

internal data class DiagnosticDiskSummary(
    val count: Long = 0, val bytes: Long = 0, val recent: List<DiagnosticRecord> = emptyList(),
    val evictedChunks: Long = 0, val damagedLines: Long = 0,
    val firstAt: Long = 0, val lastAt: Long = 0,
    val expiredChunks: Long = 0,
)

/** Used by a single IO worker. Daily chunks bound disk use without deleting an entire busy day. */
internal class DiagnosticFiles(
    private val root: File,
    private val now: () -> Long = System::currentTimeMillis,
    private val maximumBytes: Long = 32L * 1024 * 1024,
    private val chunkBytes: Long = 512L * 1024,
) {
    private val name = Regex("events-\\d{4}-\\d{2}-\\d{2}-\\d{5}\\.jsonl")
    private val counters get() = File(root, "retention.json")
    private var evicted = 0L
    private var damaged = 0L
    private var expired = 0L
    private var loaded = false
    private var summary = DiagnosticDiskSummary()

    private fun files(): List<File> = root.listFiles().orEmpty().filter { it.isFile && name.matches(it.name) }.sortedBy { it.name }
    private fun day(time: Long): LocalDate = Instant.ofEpochMilli(time).atZone(ZoneId.systemDefault()).toLocalDate()

    fun refresh(): DiagnosticDiskSummary {
        root.mkdirs()
        if (!loaded) {
            runCatching { JSONObject(counters.readText()).let { evicted = it.optLong("evictedChunks"); damaged = it.optLong("damagedLines"); expired = it.optLong("expiredChunks") } }
            loaded = true
        }
        val cutoff = day(now()).minusDays(6).toString()
        files().filter { it.name.substring(7, 17) < cutoff }.forEach { if (it.delete()) expired++ }
        var retained = files()
        var bytes = retained.sumOf { it.length() }
        for (file in retained) {
            if (bytes <= maximumBytes) break
            val length = file.length()
            if (file.delete()) { bytes -= length; evicted++ }
        }
        retained = files()
        var count = 0L
        var invalid = 0L
        var first = 0L
        var last = 0L
        val recent = ArrayDeque<DiagnosticRecord>()
        retained.forEach { file -> file.useLines { lines -> lines.forEach { line ->
            runCatching { DiagnosticRecord.read(line) }.onSuccess {
                count++
                first = if (first == 0L) it.time else minOf(first, it.time)
                last = maxOf(last, it.time)
                recent.addLast(it)
                if (recent.size > 15) recent.removeFirst()
            }.onFailure { invalid++ }
        } } }
        summary = DiagnosticDiskSummary(count, retained.sumOf { it.length() }, recent.reversed(), evicted, damaged + invalid, first, last, expired)
        persistCounters()
        return summary
    }

    fun append(record: DiagnosticRecord): DiagnosticDiskSummary {
        if (!loaded) refresh()
        val line = record.json().toString() + "\n"
        val prefix = "events-${day(now())}-"
        val existing = files().lastOrNull { it.name.startsWith(prefix) }
        val file = if (existing == null || existing.length() + line.toByteArray(Charsets.UTF_8).size > chunkBytes) {
            val sequence = existing?.name?.substringAfterLast('-')?.substringBefore('.')?.toIntOrNull()?.plus(1) ?: 0
            File(root, prefix + sequence.toString().padStart(5, '0') + ".jsonl")
        } else existing
        // Remove an interrupted final line before appending, leaving a readable JSONL stream.
        if (file.exists() && file.length() > 0) {
            java.io.RandomAccessFile(file, "rw").use { io ->
                io.seek(io.length() - 1)
                if (io.readByte().toInt() != 10) {
                    var cursor = io.length() - 1
                    while (cursor >= 0) { io.seek(cursor); if (io.readByte().toInt() == 10) break; cursor-- }
                    io.setLength(cursor + 1)
                    damaged++
                }
            }
        }
        file.appendText(line, Charsets.UTF_8)
        // Avoid scanning all retained lines for every page callback.
        summary = summary.copy(count = summary.count + 1, bytes = summary.bytes + line.toByteArray(Charsets.UTF_8).size,
            firstAt = if (summary.firstAt == 0L) record.time else minOf(summary.firstAt, record.time), lastAt = maxOf(summary.lastAt, record.time),
            recent = (listOf(record.copy(detail = DiagnosticPrivacy.mask(record.detail))) + summary.recent).take(15))
        if (summary.bytes > maximumBytes || files().any { it.name.substring(7, 17) < day(now()).minusDays(6).toString() }) return refresh()
        return summary
    }

    fun clear(): DiagnosticDiskSummary {
        files().forEach { check(it.delete()) { "日志文件无法清除" } }
        evicted = 0; damaged = 0; expired = 0
        summary = DiagnosticDiskSummary()
        persistCounters()
        return summary
    }

    private fun persistCounters() {
        root.mkdirs()
        counters.writeText(JSONObject().put("evictedChunks", evicted).put("damagedLines", damaged).put("expiredChunks", expired).toString())
    }

    fun export(destination: File, metadata: JSONObject): DiagnosticDiskSummary {
        val current = refresh()
        metadata.put("retentionDays", 7).put("capacityBytes", maximumBytes).put("recordCount", current.count)
            .put("retainedBytes", current.bytes).put("evictedChunks", current.evictedChunks).put("damagedLines", current.damagedLines)
            .put("firstRecordAt", current.firstAt).put("lastRecordAt", current.lastAt)
            .put("expiredChunks", current.expiredChunks)
            .put("scope", "已监听应用的相关捕获；未收到的系统事件无法还原。诊断不启用任何采集或入账开关。")
        destination.parentFile?.mkdirs()
        val partial = File(destination.parentFile, destination.name + ".partial")
        try {
            ZipOutputStream(partial.outputStream().buffered()).use { zip ->
                fun entry(name: String, body: String) {
                    zip.putNextEntry(ZipEntry(name)); zip.write(body.toByteArray(Charsets.UTF_8)); zip.closeEntry()
                }
                entry("manifest.json", DiagnosticPrivacy.maskJson(metadata).toString(2))
                entry("请先阅读.txt", "AeriPocket 记账诊断包\n包含通知/页面文字、金额、商户、规则与状态。手机号、卡号、邮箱和长号码已尽力遮罩，仍可能有其他个人信息，请手动决定分享对象。\n按 time（毫秒时间戳）关联 events/ 下的 JSONL；eventId/session/flowId/identity 用于关联同笔捕获，不能仅凭同额推断同笔。\n检查 manifest.json 的 droppedEvents/writeFailures/evictedChunks/damagedLines。无事件不能证明没有付款；可能是通知关闭、服务被系统终止或系统未提供页面文字。心跳只在应用进程存活时产生。\n")
                files().forEach { file ->
                    zip.putNextEntry(ZipEntry("events/" + file.name))
                    // Re-mask at export as a second boundary, and omit damaged JSONL lines.
                    file.useLines { lines -> lines.forEach { line ->
                        runCatching { JSONObject(line) }.onSuccess {
                            zip.write((DiagnosticPrivacy.maskJson(it).toString() + "\n").toByteArray(Charsets.UTF_8))
                        }
                    } }
                    zip.closeEntry()
                }
            }
            check(partial.renameTo(destination)) { "诊断文件保存失败" }
        } finally { partial.delete() }
        return current
    }
}
