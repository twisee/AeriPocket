package com.controleco.app.personal

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.controleco.app.AccBookApp
import com.controleco.app.ui.eco.*
import com.controleco.app.ui.theme.*
import com.controleco.app.util.Money
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*
import java.io.File

/** Layout proposal only. This does not change the application's calendar component. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = AccBookApp::class, qualifiers = "w390dp-h550dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class CompactMonthPreviewTest {
    @get:Rule val ui = createAndroidComposeRule<ComponentActivity>()
    @Test fun renderSixColumnsForApproval() {
        val app = org.robolectric.RuntimeEnvironment.getApplication() as AccBookApp
        ui.waitUntil(10000) { kotlinx.coroutines.runBlocking { app.db.categoryDao().countAll() > 0 } }
        ui.setContent { AccBookTheme(accent = EcoAccent.White) {
            CompositionLocalProvider(LocalAnimationsEnabled provides false) {
                val c = MaterialTheme.eco
                Box(Modifier.fillMaxSize()) {
                    EcoBackdrop()
                    Column(Modifier.fillMaxWidth()) {
                        EcoTopBar(micro = "LAYOUT PREVIEW / 示例数据", title = "概览")
                        Spacer(Modifier.height(18.dp))
                        Text("月份方格 · 每行 6 格", style = EcoType.RowTitle, color = c.ink,
                            modifier = Modifier.padding(horizontal = 22.dp))
                        Spacer(Modifier.height(12.dp))
                        EcoPane(Modifier.fillMaxWidth().padding(horizontal = 22.dp), shape = RoundedCornerShape(20.dp)) {
                            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Text("月度支出", color = c.ink, style = EcoType.SectionTitle)
                                    Spacer(Modifier.weight(1f))
                                    TextButton(onClick = {}) { Text("查看 11月流水", color = c.blueDeep, fontSize = 11.sp) }
                                }
                                val values = listOf(32100L, 26800, 42000, 35700, 50800, 27600, 38900, 46000, 20700, 18900, 17100, 0)
                                repeat(2) { row ->
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        repeat(6) { column ->
                                            val index = row * 6 + column
                                            val bg = if (values[index] == 0L) c.surface2 else lerp(c.surface2, c.blue, .14f + .72f * (values[index].toFloat() / 50800f))
                                            val fg = contentColorOn(bg)
                                            Column(Modifier.weight(1f).height(48.dp).clip(RoundedCornerShape(6.dp)).background(bg)
                                                .border(if (index == 10) 1.5.dp else .5.dp, if (index == 10) c.blueDeep else c.rule, RoundedCornerShape(6.dp)),
                                                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                                Text("${index + 1}月", color = fg, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                                                Spacer(Modifier.height(3.dp))
                                                Text(Money.formatCents(values[index]), color = fg, fontSize = 9.sp, maxLines = 1)
                                            }
                                        }
                                    }
                                }
                                Text("颜色越深，支出越多", color = c.quiet, fontSize = 9.sp, modifier = Modifier.padding(top = 4.dp))
                            }
                        }
                        Spacer(Modifier.height(24.dp))
                        Text("先选月份，再点“查看流水”\n保留现有主题颜色与花枝装饰", color = c.muted, fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 22.dp))
                        Spacer(Modifier.height(20.dp))
                        Text("这是布局预览，正式界面尚未修改。", color = c.quiet, fontSize = 11.sp,
                            modifier = Modifier.padding(horizontal = 22.dp))
                    }
                }
            }
        } }
        ui.waitForIdle()
        ui.runOnIdle {
            val view = ui.activity.window.decorView
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            val file = File("build/compact-month-preview/month-six-columns.png")
            file.parentFile?.mkdirs()
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
}
