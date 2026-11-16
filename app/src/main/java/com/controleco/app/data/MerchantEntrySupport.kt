package com.controleco.app.data

import com.controleco.app.capture.MerchantRules
import com.controleco.app.data.db.*

internal data class MerchantSuggestion(val name: String, val categoryId: Long, val categoryName: String)

internal object MerchantEntrySupport {
    fun suggestions(query: String, type: TxType, rules: List<MerchantRuleEntity>, categories: List<CategoryEntity>,
                    history: List<MerchantBillKey>): List<MerchantSuggestion> {
        if (query.isBlank()) return emptyList()
        val needle = query.trim().lowercase()
        val key = MerchantRules.explicitKeyOf(query)
        return rules.filter { it.type == type }.sortedByDescending { it.updatedAt }.mapNotNull { rule ->
            val cat = categories.firstOrNull { it.id == rule.categoryId && it.type == type && !CategoryRules.isRefundIncome(it.type, it.name) }
                ?: return@mapNotNull null
            val display = history.firstOrNull { it.type == type && MerchantRules.match(listOf(rule), type, it.counterparty, it.note) != null }
                ?.counterparty?.takeIf { it.isNotBlank() } ?: rule.matchKey
            if (!display.lowercase().contains(needle) && !rule.matchKey.contains(key ?: needle, ignoreCase = true)) return@mapNotNull null
            MerchantSuggestion(display, cat.id, cat.name)
        }.distinctBy { it.name }.take(8)
    }

    fun associatedCounts(rules: List<MerchantRuleEntity>, history: List<MerchantBillKey>): Map<Long, Int> {
        val counts = mutableMapOf<Long, Int>()
        val exact = mutableMapOf<Pair<TxType, String>, MerchantRuleEntity>()
        val legacy = mutableMapOf<Pair<TxType, String>, MerchantRuleEntity>()
        rules.sortedByDescending { it.updatedAt }.forEach { rule ->
            exact.putIfAbsent(rule.type to rule.matchKey, rule)
            MerchantRules.keyOf(rule.matchKey, "")?.let { legacy.putIfAbsent(rule.type to it, rule) }
        }
        history.forEach { bill ->
            val key = MerchantRules.keyOf(bill.counterparty, bill.note) ?: MerchantRules.explicitKeyOf(bill.counterparty) ?: return@forEach
            val rule = exact[bill.type to key] ?: legacy[bill.type to key] ?: return@forEach
            counts[rule.id] = (counts[rule.id] ?: 0) + 1
        }
        return counts
    }

    fun categoryForMerchant(manualCategory: Boolean, matchedCategory: Long?, current: Long?): Long? =
        if (manualCategory) current else matchedCategory ?: current
}
