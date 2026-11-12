package com.controleco.app.ui.personal

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.controleco.app.data.LedgerViewModel
import com.controleco.app.ui.eco.LocalAnimationsEnabled
import com.controleco.app.ui.eco.EcoAutoMoney
import com.controleco.app.ui.theme.eco
import com.controleco.app.util.Money
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import kotlin.math.roundToLong

private class TapReward(val origin: Offset) {
    val progress = Animatable(0f)
}

/** 展示层只读 ECO 的预算数据；角色包与账本逻辑独立。 */
@Composable
fun PersonalHomeScreen(vm: LedgerViewModel, onOverview: () -> Unit) {
    val spent by vm.homeSpent.collectAsStateWithLifecycle()
    val budget by vm.monthlyBudgetCents.collectAsStateWithLifecycle()
    PersonalHome(spent, budget, onOverview)
}

@Composable
internal fun PersonalHome(spent: Long?, budget: Long, onOverview: () -> Unit) {
    val c = MaterialTheme.eco
    val context = LocalContext.current
    val motion = LocalAnimationsEnabled.current
    val lifecycle = LocalLifecycleOwner.current
    val progress = remember { Animatable(0f) }
    val amountProgress = remember { Animatable(0f) }
    var entryPlayed by remember { mutableStateOf(false) }
    val target = if (budget > 0 && spent != null) spent.toFloat() / budget else 0f
    val paths = remember {
        val json = context.assets.open("characters/aerith/pack.json").bufferedReader().use { JSONObject(it.readText()) }
        val poses = json.getJSONArray("poses")
        (0 until poses.length()).associate { i ->
            poses.getJSONObject(i).let {
                val poseId = it.getInt("threshold")
                poseId to ActorPoseSpec("characters/aerith/" + it.getString("atlas"),
                    it.optBoolean("fitVisibleBounds", false), it.optInt("minBudgetPercent", poseId))
            }
        }
    }
    // Pose follows actual spending, independently of the animated number and artwork ID.
    val stage = remember(target, paths) { actorStageForProgress(target, paths) }
    val cache = remember { mutableMapOf<Int, ActorAtlas>() }
    var atlas by remember { mutableStateOf<ActorAtlas?>(null) }
    var displayedStage by remember { mutableIntStateOf(0) }
    var sceneSize by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current
    val enter = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var idleBlink by remember { mutableStateOf(false) }
    var responseBlink by remember { mutableStateOf(false) }
    var responding by remember { mutableStateOf(false) }
    var interactive by remember { mutableStateOf(false) }
    var pressed by remember { mutableStateOf(false) }
    var pressGeneration by remember { mutableIntStateOf(0) }
    var reactionJob by remember { mutableStateOf<Job?>(null) }
    var pressJob by remember { mutableStateOf<Job?>(null) }
    val rewards = remember { mutableStateListOf<TapReward>() }
    val rewardJobs = remember { mutableSetOf<Job>() }
    val rewardText = rememberTextMeasurer().measure("+1￥", TextStyle(
        color = Color(0xFF945B70), fontSize = 22.sp, fontWeight = FontWeight.Medium))
    val pressScale by animateFloatAsState(if (pressed && motion) .965f else 1f,
        animationSpec = tween(if (pressed) 90 else 220, easing = FastOutSlowInEasing), label = "character-press")
    val blink = idleBlink || responseBlink
    fun respond(origin: Offset, semanticClick: Boolean = false) {
        if (atlas == null || !interactive || !motion) return
        if (semanticClick) {
            pressJob?.cancel()
            val generation = ++pressGeneration
            pressed = true
            pressJob = scope.launch { try { delay(80) } finally { if (pressGeneration == generation) pressed = false } }
        }
        val reward = TapReward(origin)
        // Decorative only: this never touches the ledger or the budget.
        if (rewards.size >= 24) rewards.removeAt(0)
        rewards.add(reward)
        val job = scope.launch {
            try { reward.progress.animateTo(1f, tween(1050, easing = LinearEasing)) }
            finally { rewards.remove(reward) }
        }
        rewardJobs.add(job)
        job.invokeOnCompletion { rewardJobs.remove(job) }
        if (!responding) {
            responding = true
            responseBlink = idleBlink
            idleBlink = false
            reactionJob = scope.launch {
                try {
                    delay(65)
                    responseBlink = true
                    delay(455)
                    responseBlink = false
                    delay(130)
                } finally { responseBlink = false; responding = false }
            }
        }
    }
    LaunchedEffect(spent != null, target, motion) {
        if (spent != null) {
            if (motion) {
                if (!entryPlayed) { progress.snapTo(0f); entryPlayed = true }
                progress.animateTo(target, tween(1000, easing = FastOutSlowInEasing))
            } else progress.snapTo(target)
        }
    }
    LaunchedEffect(spent, motion) {
        if (spent == null) return@LaunchedEffect
        if (motion) {
            amountProgress.snapTo(0f)
            amountProgress.animateTo(1f, tween(1000, easing = CubicBezierEasing(.25f, 1f, .5f, 1f)))
        } else amountProgress.snapTo(1f)
    }
    LaunchedEffect(stage, spent != null, motion) {
        if (spent == null) return@LaunchedEffect
        val loaded = cache[stage] ?: withContext(Dispatchers.IO) {
            val pose = paths.getValue(stage)
            ActorAtlas.load(context, pose.path, pose.fitVisibleBounds)
        }.also { cache[stage] = it }
        if (atlas === loaded) return@LaunchedEffect
        if (motion && atlas != null) enter.animateTo(0f, tween(160))
        atlas = loaded
        displayedStage = stage
        if (motion) enter.animateTo(1f, tween(380)) else enter.snapTo(1f)
    }
    LaunchedEffect(lifecycle, motion) {
        lifecycle.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            interactive = true
            try {
                if (motion) while (true) {
                    delay(4300)
                    if (!responding) { idleBlink = true; delay(130); idleBlink = false }
                } else awaitCancellation()
            } finally {
                interactive = false
                pressGeneration++
                reactionJob?.cancel(); pressJob?.cancel()
                rewardJobs.toList().forEach { it.cancel() }
                rewards.clear()
                idleBlink = false; responseBlink = false; responding = false; pressed = false
            }
        }
    }
    var drag by remember { mutableFloatStateOf(0f) }
    val dragOffset by animateFloatAsState(if (motion) drag.coerceIn(-130f, 100f) * .25f else 0f,
        animationSpec = tween(90), label = "home-drag-follow")
    Box(Modifier.fillMaxSize().graphicsLayer { translationY = dragOffset }.padding(bottom = 78.dp).navigationBarsPadding()
        .background(c.paper)
        .pointerInput(onOverview) {
            detectVerticalDragGestures(onDragStart = { drag = 0f }, onVerticalDrag = { _, y -> drag += y },
                onDragCancel = { drag = 0f },
                onDragEnd = { if (drag < -80.dp.toPx()) onOverview() else drag = 0f })
        }) {
        Canvas(Modifier.fillMaxSize().padding(top = 200.dp).onSizeChanged { sceneSize = it }.semantics {
                contentDescription = if (atlas == null) "正在读取角色" else "爱丽丝，预算使用阶段 $displayedStage%"
                if (atlas == null) disabled()
                onClick("与爱丽丝互动") {
                    respond(Offset(sceneSize.width * .65f, sceneSize.height * .35f), semanticClick = true)
                    atlas != null
                }
            }
            .pointerInput(atlas, motion, interactive) {
                if (atlas != null && interactive) detectTapGestures(
                    onPress = {
                        if (motion) {
                            pressJob?.cancel()
                            val generation = ++pressGeneration
                            pressed = true
                            try {
                                coroutineScope {
                                    val minimumHold = launch { delay(80) }
                                    if (tryAwaitRelease()) minimumHold.join() else minimumHold.cancel()
                                }
                            } finally { if (pressGeneration == generation) pressed = false }
                        } else tryAwaitRelease()
                    },
                    onTap = { respond(it) })
            }
            .testTag("homeCharacter")) {
            drawRect(Brush.verticalGradient(listOf(c.paper, c.surfaceInset)))
            val current = atlas
            if (current != null) {
                val place = current.placement(size.width, size.height, displayedStage, blink, 12.dp.toPx(), 8.dp.toPx())
                // One complete original frame, without local eye overlays or frame blending.
                scale(pressScale.coerceIn(.965f, 1f), pivot = Offset(size.width / 2, (place.top + place.side).toFloat())) {
                    drawImage(current.image, srcOffset = IntOffset(if (blink) current.frameWidth else 0, 0),
                        srcSize = IntSize(current.frameWidth, current.image.height), dstOffset = IntOffset(place.left, place.top),
                        dstSize = IntSize(place.side, place.side), alpha = enter.value, filterQuality = FilterQuality.Medium)
                }
                for (reward in rewards) {
                    val p = reward.progress.value
                    val alpha = (p / .1f).coerceAtMost(1f) * ((1f - p) / .4f).coerceAtMost(1f)
                    val x = (reward.origin.x - rewardText.size.width / 2).coerceIn(0f, (size.width - rewardText.size.width).coerceAtLeast(0f))
                    drawText(rewardText, topLeft = Offset(x, (reward.origin.y - 16.dp.toPx() - p * 78.dp.toPx()).coerceAtLeast(0f)), alpha = alpha)
                }
            }
        }
        Column(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 28.dp, vertical = 14.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("AeriPocket", color = c.ink, fontSize = 19.sp, fontWeight = FontWeight.Medium)
            }
            Spacer(Modifier.height(20.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(if (spent == null) "—" else if (budget <= 0) "未设置" else "${(progress.value * 100).toInt()}%",
                    color = c.ink, fontSize = if (budget > 0) 49.sp else 32.sp, fontWeight = FontWeight.Light, letterSpacing = (-2).sp)
                Spacer(Modifier.width(10.dp))
                Text(if (budget > 0) "已用" else "月预算", color = c.muted, fontSize = 13.sp, modifier = Modifier.padding(bottom = 10.dp))
            }
            Spacer(Modifier.height(10.dp))
            Box(Modifier.fillMaxWidth().height(4.dp).clip(CircleShape).background(c.rule)) {
                Box(Modifier.fillMaxHeight().fillMaxWidth(progress.value.coerceIn(0f, 1f)).background(c.blue))
            }
            Spacer(Modifier.height(12.dp))
            if (spent == null) Text("正在读取账本…", fontSize = 14.sp, color = c.muted)
            else {
                // Interpolate a fraction, then use exact cents at the end (large values must not lose cents to Float).
                val shown = if (amountProgress.value >= 1f) spent else (spent * amountProgress.value.toDouble()).roundToLong()
                EcoAutoMoney(text = Money.formatYuan(shown), fitText = Money.formatYuan(spent),
                    style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium), color = c.blue,
                    modifier = Modifier.fillMaxWidth().testTag("homeSpentAmount"), minSize = 10.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Start,
                    suffix = "  /  ${Money.formatYuan(budget)}", suffixStyle = SpanStyle(color = c.muted, fontWeight = FontWeight.Normal))
            }
        }
        Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp).width(140.dp).height(32.dp)
            .clip(CircleShape).border(1.dp, Color.White.copy(alpha = .7f), CircleShape)
            .clickable(onClick = onOverview).testTag("homeOverview")) {
            Canvas(Modifier.matchParentSize().graphicsLayer { renderEffect = BlurEffect(14f, 14f) }) {
                val current = atlas
                if (current != null && sceneSize.height > 0) {
                    val width = sceneSize.width.toFloat(); val height = sceneSize.height.toFloat()
                    val cropX = (width - size.width) / 2
                    val cropY = height - with(density) { 16.dp.toPx() } - size.height
                    translate(-cropX, -cropY) {
                        drawRect(Brush.verticalGradient(listOf(c.paper, c.surfaceInset), endY = height), size = Size(width,height))
                        val place = current.placement(width, height, displayedStage, blink, 12.dp.toPx(), 8.dp.toPx())
                        scale(pressScale.coerceIn(.965f, 1f), pivot = Offset(width / 2, (place.top + place.side).toFloat())) {
                            drawImage(current.image,srcOffset=IntOffset(if(blink) current.frameWidth else 0,0),
                                srcSize=IntSize(current.frameWidth,current.image.height),dstOffset=IntOffset(place.left,place.top),
                                dstSize=IntSize(place.side,place.side),filterQuality=FilterQuality.Low)
                        }
                    }
                }
            }
            Box(Modifier.matchParentSize().background(c.surface.copy(alpha=.55f)), contentAlignment = Alignment.Center) {
                Text("⌃  上滑查看概览", color = c.ink, fontSize = 11.sp)
            }
        }
    }
}
