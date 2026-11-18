package com.controleco.app.ui.common

import android.graphics.BitmapFactory
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.controleco.app.R
import com.controleco.app.ui.eco.EcoAutoMoney
import com.controleco.app.ui.theme.eco
import com.controleco.app.util.Money
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val ExperienceMoneyFont = FontFamily(Font(R.font.caveat))
@Volatile private var cachedExperienceArtwork: ImageBitmap? = null
private val experienceArtworkLock = Any()
private fun loadExperienceArtwork(context: android.content.Context): ImageBitmap = cachedExperienceArtwork ?: synchronized(experienceArtworkLock) {
    cachedExperienceArtwork ?: context.assets.open("characters/aerith/card_experience_moon.png").use {
        BitmapFactory.decodeStream(it).asImageBitmap().also { image -> cachedExperienceArtwork = image }
    }
}

@Composable
internal fun FlippableBudgetCard(
    budgetCents: Long, spentCents: Long, currency: String,
    onCurrency: () -> Unit, onOpenAccount: () -> Unit, onManageBudget: () -> Unit,
    experienceEnabled: Boolean, experienceAvailable: Long, experienceAdded: Long, experienceUsed: Long,
    budgetEditLabel: String = "设置本月预算",
) {
    val context = LocalContext.current
    LaunchedEffect(experienceEnabled) {
        if (experienceEnabled) withContext(Dispatchers.IO) { loadExperienceArtwork(context) }
    }
    var back by remember(experienceEnabled) { mutableStateOf(false) }
    val rotation by animateFloatAsState(if (back) 180f else 0f,
        animationSpec = tween(if (AnimationPrefs.enabled) 500 else 0), label = "budgetCardFlip")
    val showBack = experienceEnabled && rotation > 90f
    Box(Modifier.fillMaxWidth().graphicsLayer {
        rotationY = if (!experienceEnabled) 0f else if (showBack) rotation - 180f else rotation
        shape = RoundedCornerShape(28.dp)
        clip = true
        shadowElevation = 0f
        cameraDistance = 14 * density
    }.then(if (experienceEnabled) Modifier.clickable { back = !back } else Modifier)
        .testTag("flippableBudgetCard")) {
        if (showBack) ExperienceBudgetBack(experienceAvailable, experienceAdded, experienceUsed)
        else AliceBudgetCard(budgetCents, spentCents, currency, onCurrency, onOpenAccount, onManageBudget,
            title = if (experienceEnabled) "日常预算" else "本月预算", budgetEditLabel = budgetEditLabel)
    }
}

@Composable
private fun ExperienceBudgetBack(available: Long, added: Long, used: Long) {
    val context = LocalContext.current
    var art by remember { mutableStateOf(cachedExperienceArtwork) }
    LaunchedEffect(Unit) {
        art = withContext(Dispatchers.IO) {
            loadExperienceArtwork(context)
        }
    }
    val ink = Color(0xFF4E2B57)
    val support = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium, color = ink)
    Box(Modifier.fillMaxWidth().height(197.dp).clip(RoundedCornerShape(28.dp))
        .background(Color(0xFFD9D2F4)).testTag("experienceBudgetBack")) {
        art?.let { Image(it, "月夜庭院体验小金库", Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
        Column(Modifier.fillMaxSize().padding(17.dp)) {
            Text("体验小金库", style = support, modifier = Modifier.align(Alignment.End))
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                EcoAutoMoney(Money.formatYuanGroupedFixed(available),
                    TextStyle(fontFamily = ExperienceMoneyFont, fontSize = 37.sp, color = ink), ink,
                    Modifier.fillMaxWidth(.57f).testTag("experienceAvailable"), minSize = 18.sp, textAlign = TextAlign.End)
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                EcoAutoMoney("本月新增 ${Money.formatYuanGrouped(added)}", support, ink,
                    Modifier.weight(1f), minSize = 9.sp, textAlign = TextAlign.Start)
                Spacer(Modifier.width(8.dp))
                EcoAutoMoney("本月已用 ${Money.formatYuanGrouped(used)}", support, ink,
                    Modifier.weight(1f), minSize = 9.sp, textAlign = TextAlign.End)
            }
        }
    }
}
