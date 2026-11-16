package com.controleco.app.data

import com.controleco.app.data.db.*
import org.junit.Assert.*
import org.junit.Test

class MerchantEntrySupportTest {
    private val rule = MerchantRuleEntity(id = 1, matchKey = "学校食堂", type = TxType.EXPENSE, categoryId = 1)
    private val cats = listOf(CategoryEntity(id = 1, name = "餐饮", type = TxType.EXPENSE), CategoryEntity(id = 2, name = "日常", type = TxType.EXPENSE))
    @Test fun `partial merchant suggestion supplies saved category and respects direction`() {
        val result = MerchantEntrySupport.suggestions("学校", TxType.EXPENSE, listOf(rule), cats, emptyList())
        assertEquals("学校食堂", result.single().name)
        assertEquals(1L, result.single().categoryId)
        assertTrue(MerchantEntrySupport.suggestions("学校", TxType.INCOME, listOf(rule), cats, emptyList()).isEmpty())
    }
    @Test fun `explicit one off category always takes priority over merchant default`() {
        assertEquals(2L, MerchantEntrySupport.categoryForMerchant(true, 1, 2))
        assertEquals(1L, MerchantEntrySupport.categoryForMerchant(false, 1, 2))
        assertEquals(2L, MerchantEntrySupport.categoryForMerchant(false, null, 2))
    }
    @Test fun `associated count includes one off categories and does not use stale hits`() {
        val history = listOf(MerchantBillKey(TxType.EXPENSE, "学校食堂", ""), MerchantBillKey(TxType.EXPENSE, "学校食堂", ""),
            MerchantBillKey(TxType.INCOME, "学校食堂", ""), MerchantBillKey(TxType.EXPENSE, "学校超市", ""))
        assertEquals(2, MerchantEntrySupport.associatedCounts(listOf(rule.copy(hits = 99)), history)[1])
        assertEquals(1, MerchantEntrySupport.associatedCounts(listOf(rule), history.drop(1))[1])
    }
    @Test fun `removed classification and blank input yield no merchant suggestion`() {
        assertTrue(MerchantEntrySupport.suggestions("学校", TxType.EXPENSE, listOf(rule), emptyList(), emptyList()).isEmpty())
        assertTrue(MerchantEntrySupport.suggestions("", TxType.EXPENSE, listOf(rule), cats, emptyList()).isEmpty())
    }
}
