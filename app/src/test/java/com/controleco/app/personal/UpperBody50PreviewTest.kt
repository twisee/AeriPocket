package com.controleco.app.personal

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.controleco.app.AccBookApp
import com.controleco.app.ui.eco.*
import com.controleco.app.ui.personal.actorPlacement
import com.controleco.app.ui.theme.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.runner.RunWith
import org.robolectric.*
import org.robolectric.annotation.*
import java.io.File

/** Static layout proposal only; app assets and PersonalHome are not modified. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = AccBookApp::class, qualifiers = "w390dp-h844dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class UpperBody50PreviewTest {
    @get:Rule val ui = createAndroidComposeRule<ComponentActivity>()
    @Test fun renderOpenAndClosedEyesInHomepageLayout() {
        val file = File("../../design/alice-card-and-50-preview/pose-50-upper-body-v5.png")
        Assume.assumeTrue("Local artwork proposal is optional", file.isFile)
        val app = RuntimeEnvironment.getApplication() as AccBookApp
        ui.waitUntil(10000) { runBlocking { app.db.categoryDao().countAll() > 0 } }
        val bitmap = BitmapFactory.decodeFile(file.absolutePath)
        Assert.assertEquals("Two equally sized square frames", bitmap.height * 2, bitmap.width)
        val atlas = bitmap.asImageBitmap()
        val frame = bitmap.width / 2
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        var minX = frame
        var maxX = 0
        for (y in 0 until bitmap.height) for (x in 0 until frame) {
            if ((pixels[y * bitmap.width + x] ushr 24) > 64) {
                minX = minOf(minX, x)
                maxX = maxOf(maxX, x)
            }
        }
        val subjectWidth = (maxX - minX + 1).coerceAtLeast(1)
        val subjectCenter = (minX + maxX) / 2f
        var closed by mutableStateOf(false)
        ui.setContent { AccBookTheme(accent = EcoAccent.White) {
            CompositionLocalProvider(LocalAnimationsEnabled provides false) {
                val c = MaterialTheme.eco
                Box(Modifier.fillMaxSize().background(c.paper)) {
                    Box(Modifier.fillMaxSize().padding(bottom = 78.dp).navigationBarsPadding()) {
                        androidx.compose.foundation.Canvas(Modifier.fillMaxSize().padding(top = 200.dp)) {
                            drawRect(Brush.verticalGradient(listOf(c.paper, c.blueWash.copy(alpha = .6f))))
                            val floor = size.height * .73f
                            drawRect(c.surfaceInset, Offset(0f, floor), Size(size.width, size.height - floor))
                            for (i in 1..9) drawLine(Color.White.copy(alpha = .3f), Offset(0f, floor + i * 35.dp.toPx()), Offset(size.width, floor + i * 35.dp.toPx()), 1f)
                            val place = actorPlacement(size.width, size.height, 0f, 50, frame, frame / 2f, frame / 2f, closed, 0f, 0f)
                            // Fit the visible character, keeping the whole notebook and braid inside the preview.
                            val side = minOf(place.side.toFloat(), (size.width - 24.dp.toPx()) * frame / subjectWidth).toInt()
                            val left = (size.width / 2 - subjectCenter * side / frame).toInt()
                            val top = (size.height - side - 8.dp.toPx()).toInt()
                            drawImage(atlas, srcOffset = IntOffset(if (closed) frame else 0, 0), srcSize = IntSize(frame, frame),
                                dstOffset = IntOffset(left, top), dstSize = IntSize(side, side), filterQuality = FilterQuality.Medium)
                        }
                        Column(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 28.dp, vertical = 14.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text("染的账本", color = c.ink, fontSize = 19.sp, fontWeight = FontWeight.Medium)
                                Text("本月预算", color = c.muted, fontSize = 12.sp)
                            }
                            Spacer(Modifier.height(20.dp))
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text("50%", color = c.ink, fontSize = 49.sp, fontWeight = FontWeight.Light, letterSpacing = (-2).sp)
                                Spacer(Modifier.width(10.dp))
                                Text("已用", color = c.muted, fontSize = 13.sp, modifier = Modifier.padding(bottom = 10.dp))
                            }
                            Spacer(Modifier.height(10.dp))
                            Box(Modifier.fillMaxWidth().height(4.dp).clip(CircleShape).background(c.rule)) {
                                Box(Modifier.fillMaxHeight().fillMaxWidth(.5f).background(c.blue))
                            }
                            Spacer(Modifier.height(12.dp))
                            Text("¥1,000.00  /  ¥2,000.00", fontSize = 14.sp, color = c.muted)
                        }
                        Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp).width(140.dp).height(32.dp)
                            .clip(CircleShape).border(1.dp, Color.White.copy(alpha = .7f), CircleShape)
                            .background(c.surface.copy(alpha = .75f)), contentAlignment = Alignment.Center) {
                            Text("⌃  上滑查看概览", color = c.ink, fontSize = 11.sp)
                        }
                    }
                    EcoBottomNav(selected = 0, onSelect = {}, modifier = Modifier.align(Alignment.BottomCenter))
                }
            }
        } }
        val folder = File("build/upper-body-50-preview").apply { mkdirs() }
        for (value in listOf(false, true)) {
            ui.runOnIdle { closed = value }
            ui.waitForIdle()
            ui.runOnIdle {
                val view = ui.activity.window.decorView
                val shot = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
                view.draw(Canvas(shot))
                File(folder, if (value) "homepage-50-closed.png" else "homepage-50-open.png").outputStream().use { shot.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
        }
    }
}
