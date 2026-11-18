package com.controleco.app.personal

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.controleco.app.AccBookApp
import com.controleco.app.data.LedgerViewModel
import com.controleco.app.data.db.*
import com.controleco.app.ui.common.AnimationPrefs
import com.controleco.app.ui.profile.ProfileScreen
import com.controleco.app.ui.theme.AccBookTheme
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.*
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = AccBookApp::class, qualifiers = "w390dp-h844dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class ExperienceFlowTest {
    @get:Rule val ui = createAndroidComposeRule<ComponentActivity>()

    @Test fun nativeProfileUsesSplitBudgetAndClearSurvivesViewModelRecreation() {
        val app = RuntimeEnvironment.getApplication() as AccBookApp
        ui.waitUntil(15000) { runBlocking { app.db.categoryDao().countAll() > 0 } }
        val vm = LedgerViewModel(app)
        ui.runOnIdle {
            AnimationPrefs.set(app, false)
            vm.setMonthStartDay(1)
            vm.setMonthlyBudget(100000)
            assertNull(vm.configureExperienceVault(true, 30000))
        }
        val (account, category) = runBlocking {
            app.db.transactionDao().deleteAll()
            app.db.accountDao().getAll().first().id to app.db.categoryDao().getAll().first { it.type == TxType.EXPENSE }.id
        }
        runBlocking {
            val dao = app.db.transactionDao()
            dao.insert(TransactionEntity(type = TxType.EXPENSE, amountCents = 20000, accountId = account,
                categoryId = category, timestamp = System.currentTimeMillis()))
            dao.insert(TransactionEntity(type = TxType.EXPENSE, amountCents = 10000, experienceBudget = true,
                accountId = account, categoryId = category, timestamp = System.currentTimeMillis()))
        }
        ui.setContent { AccBookTheme(darkTheme = false) { ProfileScreen(vm, {}, {}, {}, {}, {}, {}) } }
        ui.waitUntil(15000) { vm.monthlyBudgetCents.value == 70000L && vm.homeSpent.value == 20000L && vm.experienceAvailableCents.value == 20000L }
        ui.onNodeWithText("日常预算").assertExists()
        ui.onNodeWithContentDescription("设置本月预算").performClick()
        ui.onAllNodesWithText("月预算").onLast().assertExists()
        ui.onNodeWithText("1000.00").assertExists()
        ui.activity.onBackPressedDispatcher.onBackPressed()
        ui.waitForIdle()
        ui.onNodeWithText("日常预算").performClick()
        ui.onNodeWithTag("experienceBudgetBack", useUnmergedTree = true).assertExists()
        ui.waitUntil(15000) { ui.onAllNodesWithContentDescription("月夜庭院体验小金库").fetchSemanticsNodes().isNotEmpty() }
        ui.runOnIdle {
            val view = ui.activity.window.decorView
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            val screenshot = File("build/experience-preview/profile-native.png")
            screenshot.parentFile!!.mkdirs()
            screenshot.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            assertNull(vm.configureExperienceVault(true, 50000))
        }
        ui.waitUntil(15000) { vm.experienceVault.value.nextMonthlyCents == 50000L }
        assertEquals(70000L, vm.monthlyBudgetCents.value)
        ui.runOnIdle { assertNull(vm.configureExperienceVault(false, 50000)) }
        ui.waitUntil(15000) { !vm.experienceVault.value.enabled }
        assertEquals(70000L, vm.monthlyBudgetCents.value)
        ui.runOnIdle { assertNull(vm.configureExperienceVault(true, 50000)) }
        ui.waitUntil(15000) { vm.experienceVault.value.enabled }
        assertEquals(70000L, vm.monthlyBudgetCents.value)
        assertEquals(1, vm.experienceVault.value.grants.size)
        var cleared = false
        ui.runOnIdle { vm.clearExperienceVault { assertNull(it); cleared = true } }
        ui.waitUntil(15000) {
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            cleared && vm.experienceAvailableCents.value == 0L
        }
        ui.runOnIdle {
            val restored = LedgerViewModel(app)
            assertEquals(vm.experienceVault.value, restored.experienceVault.value)
            assertEquals(70000L, restored.monthlyBudgetCents.value)
            assertEquals(2, runBlocking { app.db.transactionDao().getAll().size })
        }
    }
}
