package com.controleco.app.data.budget

import java.time.YearMonth

/** Persistent allocations: each accounting month can receive at most one grant. */
data class ExperienceVault(
    val enabled: Boolean = false,
    val monthlyCents: Long = 0,
    val nextMonthlyCents: Long? = null,
    val lastMonth: YearMonth? = null,
    val grants: Map<YearMonth, Long> = emptyMap(),
    val clearedCents: Long = 0,
) {
    fun advance(month: YearMonth): ExperienceVault {
        val last = lastMonth ?: return copy(lastMonth = month)
        if (month <= last) return this
        val amount = nextMonthlyCents ?: monthlyCents
        val updated = grants.toMutableMap()
        var cursor = last.plusMonths(1)
        while (cursor <= month) {
            if (enabled) updated.putIfAbsent(cursor, amount)
            cursor = cursor.plusMonths(1)
        }
        return copy(monthlyCents = amount, nextMonthlyCents = null, lastMonth = month, grants = updated)
    }

    fun configure(month: YearMonth, enabled: Boolean, cents: Long): ExperienceVault {
        require(cents > 0 || !enabled) { "请输入大于零的每月体验额度" }
        val settled = advance(month)
        // First activation and reactivation grant this month once; edits apply next month.
        return if (enabled && !settled.enabled) settled.copy(
            enabled = true, monthlyCents = cents, nextMonthlyCents = null,
            grants = settled.grants + (month to (settled.grants[month] ?: cents)),
        ) else settled.copy(enabled = enabled,
            nextMonthlyCents = if (cents != settled.monthlyCents) cents else null)
    }

    fun available(spentCents: Long): Long = grants.values.sum() - spentCents - clearedCents
    fun clear(spentCents: Long): ExperienceVault = copy(clearedCents = grants.values.sum() - spentCents)
}
