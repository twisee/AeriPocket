package com.controleco.app.personal

import android.app.Application
import androidx.room.Room
import com.controleco.app.data.CategoryPresets
import com.controleco.app.data.db.*
import com.controleco.app.ui.common.Glyph
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class CategoryPresetsTest {
    @Test fun presetsHaveUniqueIconsAndNeverRepeatThePinnedThree() {
        val presets = CategoryPresets.all
        assertEquals(16, presets.size)
        assertEquals(16, presets.map { it.glyphName }.toSet().size)
        assertEquals(16, presets.map { it.path }.toSet().size)
        assertTrue(presets.none { it.name in CategoryPresets.pinnedExpenseNames })
        presets.forEach { assertNotNull(Glyph.valueOf(it.glyphName)) }
    }

    @Test fun upgradingCategoriesPreservesAssignmentsAndCustomIconsWithoutDuplicates() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), AccBookDatabase::class.java).build()
        try {
            DefaultData.seedIfNeeded(db)
            val dao = db.categoryDao()
            val id = dao.insert(CategoryEntity(name = "医疗", type = TxType.EXPENSE, icon = "EMOJI:💊"))
            val account = db.accountDao().getAll().first().id
            val tx = db.transactionDao().insert(TransactionEntity(type = TxType.EXPENSE, amountCents = 1200,
                accountId = account, categoryId = id, timestamp = 1))
            DefaultData.seedCommonPresets(db)
            DefaultData.seedCommonPresets(db)
            val expense = dao.getAll().filter { it.type == TxType.EXPENSE }
            assertEquals(19, expense.size)
            assertEquals(19, expense.map { it.name }.toSet().size)
            assertEquals("EMOJI:💊", expense.single { it.name == "医疗" }.icon)
            assertEquals(id, requireNotNull(db.transactionDao().getById(tx)).categoryId)
        } finally { db.close() }
    }
}
