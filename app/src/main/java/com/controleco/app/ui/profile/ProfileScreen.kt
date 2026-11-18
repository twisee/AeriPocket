package com.controleco.app.ui.profile

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.controleco.app.BuildConfig
import com.controleco.app.data.LedgerViewModel
import com.controleco.app.data.budget.MonthPeriod
import com.controleco.app.data.db.CategoryEntity
import com.controleco.app.data.db.TxType
import com.controleco.app.ui.common.EcoIcons
import com.controleco.app.ui.common.WheelPicker
import com.controleco.app.ui.common.WheelTrigger
import com.controleco.app.ui.eco.EcoArrival
import com.controleco.app.ui.eco.EcoBudgetSheet
import com.controleco.app.ui.eco.EcoCategoryTile
import com.controleco.app.ui.eco.EcoDialog
import com.controleco.app.ui.eco.EcoGhostButton
import com.controleco.app.ui.eco.EcoHeroCard
import com.controleco.app.ui.eco.EcoPane
import com.controleco.app.ui.eco.EcoPrimaryButton
import com.controleco.app.ui.eco.EcoRow
import com.controleco.app.ui.eco.EcoRowDivider
import com.controleco.app.ui.eco.EcoSectionHead
import com.controleco.app.ui.eco.EcoSheet
import com.controleco.app.ui.eco.EcoTopBar
import com.controleco.app.ui.eco.ecoBottomContentPadding
import com.controleco.app.ui.theme.AccentThemeStore
import com.controleco.app.ui.theme.EcoAccent
import com.controleco.app.ui.theme.EcoType
import com.controleco.app.ui.theme.contentColorOn
import com.controleco.app.ui.theme.eco
import com.controleco.app.ui.theme.ecoColors
import com.controleco.app.ui.theme.heroPlate
import com.controleco.app.ui.theme.heroProgress
import com.controleco.app.ui.theme.onHero
import com.controleco.app.ui.theme.onHeroBorder
import com.controleco.app.ui.theme.onHeroChip
import com.controleco.app.ui.theme.onHeroMuted
import com.controleco.app.util.Money
import java.time.LocalDate
import kotlin.math.roundToInt

/** 开源仓库（个人页「更多」里的一行，点按用系统浏览器打开） */
private const val GITHUB_REPO_PATH = "twisee/AeriPocket"
private const val GITHUB_REPO_URL = "https://github.com/$GITHUB_REPO_PATH"

// ---- 版式常量：按原型最终级联值（hero 197 / duo 161 / mid 150 / bottom 66）----
private val DUO_HEIGHT = 161.dp
private val PENDING_WIDTH = 111.dp
private val UTILITY_WIDTH = 127.dp
private val MID_HEIGHT = 150.dp
private val START_CARD_HEIGHT = 80.dp
private val SETTINGS_CARD_HEIGHT = 62.dp
private val MID_GAP = 8.dp
private val BOTTOM_HEIGHT = 66.dp

/**
 * 书签形待确认卡：上边与左右是圆角，下边中间向上凹一个 V 形口。
 * 对应原型里那张异形卡（不是贴图，是路径）。
 */
private object BookmarkShape : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val w = size.width
        val h = size.height
        val r = with(density) { 16.dp.toPx() }.coerceAtMost(w / 2f).coerceAtMost(h / 4f)
        val notchHalf = w * 0.26f
        val notchDepth = h * 0.22f
        val path = Path().apply {
            moveTo(r, 0f)
            lineTo(w - r, 0f)
            quadraticBezierTo(w, 0f, w, r)
            lineTo(w, h - r)
            quadraticBezierTo(w, h, w - r, h)
            // 下边：右角 → 中间凸峰（峰顶略作圆角）→ 左角
            lineTo(w * 0.5f + notchHalf, h)
            quadraticBezierTo(
                w * 0.5f + notchHalf * 0.35f, h - notchDepth * 0.72f,
                w * 0.5f, h - notchDepth,
            )
            quadraticBezierTo(
                w * 0.5f - notchHalf * 0.35f, h - notchDepth * 0.72f,
                w * 0.5f - notchHalf, h,
            )
            lineTo(r, h)
            quadraticBezierTo(0f, h, 0f, h - r)
            lineTo(0f, r)
            quadraticBezierTo(0f, 0f, r, 0f)
            close()
        }
        return Outline.Generic(path)
    }
}

// 深色 hero 上的前景色：**由主题推导**（见 Theme.kt 的 onHero* 扩展），
// 原来是固定的青绿常量，换紫色/珊瑚主题后会出现「紫底绿字」。
// 保留常量名以兼容既有调用点，但值改为读当前主题。
private val HERO_MINT: Color
    @Composable get() = MaterialTheme.eco.onHeroMuted
private val HERO_MINT_SOFT: Color
    @Composable get() = MaterialTheme.eco.onHero
private val HERO_INK: Color
    @Composable get() = MaterialTheme.eco.onHero
private val HERO_BTN_INK: Color
    @Composable get() = MaterialTheme.eco.onHeroChip

/**
 * 个人页：预算卡 + 支出趋势与待确认书签 + 分类/每月首日/设置三卡 + 底排（导入/打赏/关于）+ 更多。
 *
 * 版式与原型 1:1（5.6 profile）：hero 197 高、非对称圆角 12/33/12/33；
 * 中排左侧分类卡（25/9/25/9），右侧「每月首日」（内置常驻滚轮）+「设置」；
 * 底排 63 / 1.03fr / 1.27fr 三个胶囊；其余既有入口（货币、主题色、GitHub、版本、关于）收进「更多」。
 */
@Composable
fun ProfileScreen(
    vm: LedgerViewModel,
    onOpenAccount: () -> Unit,
    onOpenImport: () -> Unit,
    onOpenPending: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenCategories: () -> Unit,
    onOpenReward: () -> Unit,
    onOpenSpendingTrend: () -> Unit = {},
) {
    val context = LocalContext.current
    val budget by vm.monthlyBudgetCents.collectAsStateWithLifecycle()
    val totalBudget by vm.totalMonthlyBudgetCents.collectAsStateWithLifecycle()
    val vault by vm.experienceVault.collectAsStateWithLifecycle()
    val experienceAvailable by vm.experienceAvailableCents.collectAsStateWithLifecycle()
    val experienceUsed by vm.experienceMonthSpent.collectAsStateWithLifecycle()
    val spent by vm.homeSpent.collectAsStateWithLifecycle()
    val currency by vm.currencySymbol.collectAsStateWithLifecycle()
    val pendingCount by vm.pendingCount.collectAsStateWithLifecycle()
    val monthStartDay by vm.monthStartDay.collectAsStateWithLifecycle()
    val dailyBudgetMode by vm.dailyBudgetMode.collectAsStateWithLifecycle()
    val dailyBudgetCents by vm.dailyBudgetCents.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()

    // 与首页同口径：本月净支出未到位前整页不画（避免预算卡先显示 0 再跳数）
    val spentReady = spent ?: return
    val today = LocalDate.now()
    val period = remember(today, monthStartDay) { MonthPeriod.of(today, monthStartDay) }

    var showBudgetSheet by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }
    var showTheme by remember { mutableStateOf(false) }
    var showMonthStart by remember { mutableStateOf(false) }

    // 开源仓库链接：用系统浏览器打开（没有浏览器时静默失败，不影响其它功能）
    val openRepo: () -> Unit = {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(GITHUB_REPO_URL))) }
    }

    Column(Modifier.fillMaxSize()) {
        EcoTopBar(micro = "PERSONAL SPACE", title = "我的")
        Column(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                // 卡片视觉上比槽位小一圈（余量由 EcoArrival 在动画图层内预留），
                // 这里保持原设计的页面边距。
                .padding(horizontal = 22.dp)
                .padding(top = 6.dp, bottom = ecoBottomContentPadding(extra = 12.dp)),
            verticalArrangement = Arrangement.spacedBy(13.dp),
        ) {
            EcoArrival(randomDelay = true, delayMillis = 60, turn = 3.2f) {
                BudgetHero(
                    budgetCents = budget,
                    dailyMode = dailyBudgetMode,
                    spentCents = spentReady,
                    experienceEnabled = vault.enabled,
                    experienceAvailable = experienceAvailable,
                    experienceAdded = vault.grants[java.time.YearMonth.from(period.start)] ?: 0L,
                    experienceUsed = experienceUsed,
                    currency = currency,
                    onCurrency = {},
                    onOpenAccount = onOpenAccount,
                    onManageBudget = { showBudgetSheet = true },
                )
            }

            SpendingAndPendingRow(
                vm = vm,
                pendingCount = pendingCount,
                onOpenPending = onOpenPending,
                onOpenSpendingTrend = onOpenSpendingTrend,
            )

            MidRow(
                categories = categories,
                monthStartDay = monthStartDay,
                onOpenCategories = onOpenCategories,
                onOpenSettings = onOpenSettings,
                onOpenMonthStart = { showMonthStart = true },
            )

            BottomRow(
                onOpenImport = onOpenImport,
                onReward = onOpenReward,
                onOpenRepo = openRepo,
                onTheme = { showTheme = true },
            )
        }
    }

    if (showBudgetSheet) {
        EcoBudgetSheet(
            minimumCents = run {
                val reserved = maxOf(vault.grants[java.time.YearMonth.from(period.start)] ?: 0L, if (vault.enabled) vault.nextMonthlyCents ?: vault.monthlyCents else 0L)
                if (dailyBudgetMode) (reserved + 27) / 28 else reserved
            },
            dailyMode = dailyBudgetMode,
            currentCents = if (dailyBudgetMode) dailyBudgetCents else totalBudget,
            onSave = {
                if (dailyBudgetMode) vm.setDailyBudget(it) else vm.setMonthlyBudget(it)
                showBudgetSheet = false
            },
            onDismiss = { showBudgetSheet = false },
        )
    }


    if (showAbout) {
        AboutDialog(onOpenRepo = openRepo, onDismiss = { showAbout = false })
    }

    if (showTheme) {
        ThemeSheet(
            accent = AccentThemeStore.current,
            onPickAccent = { AccentThemeStore.set(context, it) },
            onDismiss = { showTheme = false },
        )
    }

    if (showMonthStart) {
        MonthStartSheet(
            monthStartDay = monthStartDay,
            onPick = { vm.setMonthStartDay(it) },
            onDismiss = { showMonthStart = false },
        )
    }
}

// ---------- 1. 预算 hero ----------

/** Approved floral budget card; the existing settings callbacks and budget calculation are retained. */
@Composable
private fun BudgetHero(
    budgetCents: Long,
    dailyMode: Boolean,
    spentCents: Long,
    experienceEnabled: Boolean,
    experienceAvailable: Long,
    experienceAdded: Long,
    experienceUsed: Long,
    currency: String,
    onCurrency: () -> Unit,
    onOpenAccount: () -> Unit,
    onManageBudget: () -> Unit,
) {
    com.controleco.app.ui.common.FlippableBudgetCard(budgetCents, spentCents, currency,
        onCurrency, onOpenAccount, onManageBudget, experienceEnabled, experienceAvailable, experienceAdded, experienceUsed,
        budgetEditLabel = if (dailyMode) "设置日预算" else "设置本月预算")
}

// ---------- 2. 双列：分段 + 日预算瓦片 / 待确认椭圆 ----------

@Composable
private fun SpendingAndPendingRow(
    vm: LedgerViewModel,
    pendingCount: Int,
    onOpenPending: () -> Unit,
    onOpenSpendingTrend: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().height(DUO_HEIGHT),
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        EcoArrival(
            randomDelay = true,
            delayMillis = 140,
            turn = -2.6f,
            modifier = Modifier.weight(1f).fillMaxHeight(),
        ) {
            ProfileSpendingTrend(vm, onOpenSpendingTrend, Modifier.fillMaxSize())
        }
        EcoArrival(randomDelay = true, delayMillis = 280, turn = -2.2f, modifier = Modifier.width(PENDING_WIDTH).fillMaxHeight()) {
            PendingPill(
                count = pendingCount,
                onClick = onOpenPending,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/**
 * 待确认书签：收据/对勾图标和待确认笔数。
 * 有未确认时用陶土（clay，真语义）；清零时用**次要色**（装饰性，随主题）。
 */
@Composable
private fun PendingPill(count: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = MaterialTheme.eco
    val alert = count > 0
    // 原型 pendingWiggle：每 4.5s 末尾摇一下，只在有待确认时
    val wiggleT = rememberInfiniteTransition(label = "pending-wiggle")
    val wiggle by wiggleT.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            keyframes {
                durationMillis = 4500
                0f at 0
                0f at 3960
                -11f at 4095
                9f at 4275
                -4f at 4410
                0f at 4500
            },
            repeatMode = RepeatMode.Restart,
        ),
        label = "pending-wiggle-angle",
    )
    val background = if (alert) c.claySoft else c.secondSoft
    val fg = if (alert) {
        if (c.isDark) c.clayHi else lerp(c.clay, Color.Black, 0.22f)
    } else {
        if (c.isDark) c.second else lerp(c.second, Color.Black, 0.30f)
    }
    Box(
        modifier
            .clip(BookmarkShape)
            .background(background)
            .clickable(onClick = onClick),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 9.dp, vertical = 11.dp)
                // 卡片底部有书签凹口，几何中心偏高一点，整组上移 7dp 视觉更居中
                .offset(y = (-7).dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                if (alert) EcoIcons.Receipt else EcoIcons.Check,
                contentDescription = null,
                tint = fg.copy(alpha = 0.92f),
                modifier = Modifier
                    .size(32.dp)
                    .graphicsLayer { if (alert) rotationZ = wiggle },
            )
            if (alert) {
                Spacer(Modifier.height(5.dp))
                Text(
                    "%02d".format(count),
                    style = EcoType.PendingCount,
                    color = fg,
                    maxLines = 1,
                )
            }
            Spacer(Modifier.height(1.dp))
            Text(
                "待确认",
                style = EcoType.RowTrail.copy(fontSize = 15.5.sp, fontWeight = FontWeight(740)),
                color = fg.copy(alpha = 0.86f),
                maxLines = 1,
            )
        }
    }
}

// ---------- 3. 中排：分类卡 / 每月首日（常驻滚轮）/ 设置 ----------

@Composable
private fun MidRow(
    categories: List<CategoryEntity>,
    monthStartDay: Int,
    onOpenCategories: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenMonthStart: () -> Unit,
) {
    val tiles = remember(categories) {
        val expense = categories.filter { it.type == TxType.EXPENSE }
        (if (expense.isNotEmpty()) expense else categories).take(4)
    }
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        EcoArrival(
            randomDelay = true,
            delayMillis = 350,
            turn = 2.4f,
            modifier = Modifier
                .weight(1f)
                .height(MID_HEIGHT),
        ) {
            CategoryCard(
                tiles = tiles,
                onClick = onOpenCategories,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Column(
            Modifier.width(UTILITY_WIDTH),
            verticalArrangement = Arrangement.spacedBy(MID_GAP),
        ) {
            EcoArrival(randomDelay = true, delayMillis = 420, turn = -2.6f) {
                MonthStartCard(
                    monthStartDay = monthStartDay,
                    onOpen = onOpenMonthStart,
                    modifier = Modifier.fillMaxWidth().height(START_CARD_HEIGHT),
                )
            }
            EcoArrival(randomDelay = true, delayMillis = 490, turn = 2.2f) {
                SettingsCard(
                    onClick = onOpenSettings,
                    modifier = Modifier.fillMaxWidth().height(SETTINGS_CARD_HEIGHT),
                )
            }
        }
    }
}

/** 分类卡：次要色底，显示实际分类图标，点击进入分类管理。 */
@Composable
private fun CategoryCard(
    tiles: List<CategoryEntity>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = MaterialTheme.eco
    val shape = RoundedCornerShape(25.dp, 9.dp, 25.dp, 9.dp)
    Box(
        modifier
            .clip(shape)
            .background(c.secondSoft)
            .clickable(onClick = onClick),
    ) {
        Column(
            // 原型 justify-content:space-evenly：标题/图标上下留白相等，不再是"标题贴顶、图标贴底"
            Modifier.fillMaxSize().padding(start = 9.dp, end = 9.dp, top = 15.dp, bottom = 14.dp),
            verticalArrangement = Arrangement.SpaceEvenly,
        ) {
            Text(
                "分类设置",
                style = EcoType.CatAmount.copy(
                    fontSize = 19.sp,
                    fontWeight = FontWeight(790),
                    letterSpacing = 0.66.sp,
                    lineHeight = 24.sp,
                ),
                color = c.ink,
                maxLines = 1,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            // 瓦片宽度随可用宽度自适应（窄屏 30dp、常规 42dp），保证 4 个永远排得下
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val gap = 5.dp
                val tile = ((maxWidth - gap * 3f) / 4f).coerceIn(30.dp, 42.dp)
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    tiles.forEach { category ->
                        EcoCategoryTile(
                            categoryName = category.name,
                            icon = category.icon,
                            size = tile,
                            height = 43.dp,
                            radius = 12.dp,
                            glyphScale = 0.57f,
                            showBorder = false,
                        )
                    }
                }
            }
        }
    }
}

/**
 * 「每月首日」卡：日历图标、标题、当前值与选择入口。
 *
 * **滚轮常驻在卡内**（3 行 / 96x84）：点击 → 原位浮出滚轮卡选择；长按 → 不松手直接拖动，
 * 抬手后由 WheelPicker 自己惯性吸附并回写 —— 与改造前完全一致，只是从"整行右侧"变成"卡片内下方"。
 * 卡面不用 clip（用 background(brush, shape) 画圆角），长按展开的滚轮卡可以溢出卡边不被裁掉。
 */
@Composable
private fun MonthStartCard(
    monthStartDay: Int,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = MaterialTheme.eco
    // 每月首日：圆角方形
    val shape = RoundedCornerShape(24.dp)
    val dayLabel = "%02d 日".format(monthStartDay)

    Box(
        modifier
            .clip(shape)
            .background(c.blueSoft)
            .clickable(onClick = onOpen),
    ) {
        Column(
            Modifier.fillMaxSize().padding(start = 12.dp, end = 11.dp, top = 12.dp, bottom = 11.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.Top) {
                Icon(
                    EcoIcons.Calendar,
                    contentDescription = null,
                    tint = c.blue,
                    modifier = Modifier.size(17.dp),
                )
                Spacer(Modifier.width(6.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "每月首日",
                        style = EcoType.Chip.copy(fontSize = 9.5.sp, fontWeight = FontWeight(730), letterSpacing = 0.sp),
                        color = c.muted,
                        maxLines = 1,
                    )
                    Spacer(Modifier.height(1.dp))
                    Text(
                        dayLabel,
                        style = EcoType.CatAmount.copy(fontSize = 21.sp, fontWeight = FontWeight(780), lineHeight = 25.sp),
                        color = c.ink,
                        maxLines = 1,
                    )
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Icon(
                    EcoIcons.ChevDown,
                    contentDescription = "选择每月首日",
                    tint = c.quiet,
                    modifier = Modifier.size(12.dp),
                )
            }
        }
    }
}

/**
 * 「设置」卡：暖金浅底和齿轮图标。
 */
@Composable
private fun SettingsCard(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = MaterialTheme.eco
    // 设置：横向胶囊（上下直边、左右半圆）
    val shape = RoundedCornerShape(50)
    Box(
        modifier
            .clip(shape)
            .background(c.amberSoft)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
    ) {  
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            Icon(
                imageVector = EcoIcons.Gear,
                contentDescription = null,
                tint = c.amber,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .size(18.dp)
            )

            Text(
                text = "设置",
                style = EcoType.CatAmount.copy(
                    fontSize = 16.sp,
                    fontWeight = FontWeight(750),
                    lineHeight = 20.sp
                ),
                color = c.ink,
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier.align(Alignment.Center)
            )

            Icon(
                imageVector = EcoIcons.Chev,
                contentDescription = null,
                tint = c.quiet,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .size(13.dp)
            )
        }
    }
}

// ---------- 4. 底排：导入 / 打赏 / 关于 ----------


@Composable
private fun BottomRow(
    onOpenImport: () -> Unit,
    onReward: () -> Unit,
    onOpenRepo: () -> Unit,
    onTheme: () -> Unit,
) {
    BoxWithConstraints(
        modifier = Modifier.fillMaxWidth()
    ) {
        val gap = 8.dp

        // 三个圆始终相同大小
        // 窄屏自动缩小，给导入账单保留至少约 94dp
        val circleSize = (
            (maxWidth - 94.dp - gap * 3f) / 3f
        ).coerceIn(48.dp, 66.dp)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(BOTTOM_HEIGHT),
            horizontalArrangement = Arrangement.spacedBy(gap),
            verticalAlignment = Alignment.CenterVertically
        ) {

            // 1. GitHub
            EcoArrival(
                randomDelay = true,
                delayMillis = 560,
                turn = -2.4f,
                modifier = Modifier.size(circleSize)
            ) {
                GitHubOrb(
                    onClick = onOpenRepo,
                    modifier = Modifier.fillMaxSize()
                )
            }

            // 2. 打赏
            EcoArrival(
                randomDelay = true,
                delayMillis = 610,
                turn = 2.4f,
                modifier = Modifier.size(circleSize)
            ) {
                RewardTile(
                    onClick = onReward,
                    modifier = Modifier.fillMaxSize()
                )
            }

            // 3. 主题
            EcoArrival(
                randomDelay = true,
                delayMillis = 650,
                turn = -2.4f,
                modifier = Modifier.size(circleSize)
            ) {
                AccentTile(
                    onClick = onTheme,
                    modifier = Modifier.fillMaxSize()
                )
            }

            // 4. 导入账单：保留胶囊形
            EcoArrival(
                randomDelay = true,
                delayMillis = 680,
                turn = 2.2f,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                ImportTile(
                    onClick = onOpenImport,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

/** 开源仓库：深墨圆钮（原型 github-orb），点击直接开系统浏览器。 */
@Composable
private fun GitHubOrb(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = MaterialTheme.eco
    // 深墨圆钮：用中性墨色（白主题下是石墨，冷调主题下是深青墨），不再写死深青
    val orbBg = lerp(c.ink, Color.Black, 0.55f)
    Box(
        modifier
            .clip(CircleShape)
            .background(orbBg)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        GitHubOctocat()
    }
}

/** 主题按钮：扁平底色和原有画笔。 */
@Composable
private fun AccentTile(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val background = MaterialTheme.eco.blue
    Box(
        modifier
            .clip(CircleShape)
            .background(background)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            EcoIcons.Brush,
            contentDescription = "主题色",
            tint = contentColorOn(background),
            modifier = Modifier.size(28.dp),
        )
    }
}

/** 导入账单：横向主 CTA 胶囊（原型 import-tile）。 */
@Composable
private fun ImportTile(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = MaterialTheme.eco
    Box(
        modifier
            .clip(RoundedCornerShape(50))
            .background(c.heroB)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(EcoIcons.Download, contentDescription = null, tint = c.onHero, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                "导入 / 导出",
                style = EcoType.Chip.copy(fontSize = 12.5.sp, fontWeight = FontWeight(800), letterSpacing = 0.sp),
                color = c.onHero,
                maxLines = 1,
            )
        }
    }
}

/** 账单导入：63 的圆形胶囊（hero 渐变），下载/文件图标 + 「导入账单」。 */
@Composable
private fun ImportOrb(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = MaterialTheme.eco
    Box(
        modifier
            .shadow(
                elevation = 3.dp,
                shape = CircleShape,
                clip = false,
                ambientColor = Color.Transparent,
                spotColor = c.heroB.copy(alpha = 0.42f),
            )
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(c.heroC, c.heroB), start = Offset.Zero, end = Offset.Infinite))
            .border(1.dp, Color.White.copy(alpha = 0.2f), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                EcoIcons.File,
                contentDescription = "账单导入与导出",
                tint = c.onHero,
                modifier = Modifier.size(19.dp),
            )
            Spacer(Modifier.height(3.dp))
            Text(
                "导入 / 导出",
                style = EcoType.Micro.copy(fontSize = 8.5.sp, fontWeight = FontWeight(800), letterSpacing = 0.sp),
                color = c.onHero,
                maxLines = 1,
            )
        }
    }
}

/** 打赏：陶土色圆钮和咖啡杯图标。 */
@Composable
private fun RewardTile(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = MaterialTheme.eco
    Box(
        modifier
            .clip(CircleShape)
            .background(c.clay)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                EcoIcons.Coffee,
                contentDescription = null,
                tint = lerp(c.clay, Color.White, 0.90f),
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.height(1.dp))
            Text(
                "打赏",
                style = EcoType.Chip.copy(fontSize = 10.5.sp, fontWeight = FontWeight(800), letterSpacing = 0.sp),
                color = lerp(c.clay, Color.White, 0.92f),
                maxLines = 1,
            )
        }
    }
}

/** 关于：与导入同族 hero 渐变，信息图标 + 关于。 */
@Composable
private fun AboutTile(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = MaterialTheme.eco
    Box(
        modifier
            .shadow(
                elevation = 2.dp,
                shape = RoundedCornerShape(50),
                clip = false,
                ambientColor = Color.Transparent,
                spotColor = c.heroB.copy(alpha = 0.4f),
            )
            .clip(RoundedCornerShape(50))
            .background(Brush.linearGradient(listOf(c.heroC, c.heroB), start = Offset.Zero, end = Offset.Infinite))
            .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(50))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                EcoIcons.Info,
                contentDescription = null,
                tint = c.onHero,
                modifier = Modifier.size(17.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                "关于",
                style = EcoType.Chip.copy(fontSize = 15.sp, fontWeight = FontWeight(800), letterSpacing = 0.sp),
                color = c.onHero,
                maxLines = 1,
            )
        }
    }
}

// ---------- 5. 更多（主题色 / 货币 / 仓库 / 版本 / 关于） ----------

@Composable
private fun MoreSection(
    currency: String,
    accent: EcoAccent,
    onPickAccent: (EcoAccent) -> Unit,
    onCurrency: () -> Unit,
    onOpenRepo: () -> Unit,
    onAbout: () -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        EcoSectionHead(title = "更多", aside = "MORE")
        EcoPane {
            Column(Modifier.fillMaxWidth()) {
                ThemeRow(selected = accent, onPick = onPickAccent)
                EcoRowDivider()
                EcoRow(
                    title = "GitHub 仓库",
                    subtitle = GITHUB_REPO_PATH,
                    icon = EcoIcons.Link,
                    onClick = onOpenRepo,
                    showChevron = true,
                )
                EcoRowDivider()
                EcoRow(
                    title = "版本",
                    subtitle = if (BuildConfig.DEBUG) "debug 构建" else "release 构建",
                    trail = "v${BuildConfig.VERSION_NAME}",
                )
                EcoRowDivider()
                EcoRow(
                    title = "关于AeriPocket",
                    icon = EcoIcons.Info,
                    onClick = onAbout,
                    showChevron = true,
                )
            }
        }
    }
}

/** 「更多」弹层：主题色 / 货币 / 仓库 / 版本 / 关于，底排彩虹钮打开。 */
@Composable
private fun MoreSheet(
    currency: String,
    accent: EcoAccent,
    onPickAccent: (EcoAccent) -> Unit,
    onCurrency: () -> Unit,
    onOpenRepo: () -> Unit,
    onAbout: () -> Unit,
    onDismiss: () -> Unit,
) {
    EcoSheet(
        onDismiss = onDismiss,
        title = "更多",
        subtitle = "主题色 / 开源仓库 / 版本",
    ) {
        Column(Modifier.fillMaxWidth()) {
            ThemeRow(selected = accent, onPick = onPickAccent)
            EcoRowDivider()
            EcoRow(
                title = "GitHub 仓库",
                subtitle = GITHUB_REPO_PATH,
                icon = EcoIcons.Link,
                onClick = onOpenRepo,
                showChevron = true,
            )
            EcoRowDivider()
            EcoRow(
                title = "版本",
                subtitle = if (BuildConfig.DEBUG) "debug 构建" else "release 构建",
                trail = "v${BuildConfig.VERSION_NAME}",
            )
            EcoRowDivider()
            EcoRow(
                title = "关于AeriPocket",
                icon = EcoIcons.Info,
                onClick = onAbout,
                showChevron = true,
            )
        }
    }
}

/**
 * 「每月首日」选择：保留项目原来的滚轮交互（上下拖动、惯性吸附、逐项回调），
 * 但改成卡内点击后从底部弹出；视觉上不再把三行滚轮常驻在 102dp 的卡里。
 */
@Composable
private fun MonthStartSheet(
    monthStartDay: Int,
    onPick: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val options = remember { MonthPeriod.startDayOptions() }
    EcoSheet(
        onDismiss = onDismiss,
        title = "每月首日",
        // 滚轮自己处理竖向拖拽；不能让外层 verticalScroll 抢走手势
        scrollable = false,
    ) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.fillMaxWidth().height(132.dp),
                contentAlignment = Alignment.Center,
            ) {
                WheelPicker(
                    items = options,
                    selectedIndex = MonthPeriod.startDayIndex(monthStartDay),
                    onSelected = { onPick(MonthPeriod.startDayAtIndex(it)) },
                    label = "每月首日",
                    itemHeight = 32.dp,
                    rows = 3,
                    expandedRows = 3,
                    trigger = WheelTrigger.DRAG,
                    updateWhileScrolling = true,
                )
            }
            Spacer(Modifier.height(8.dp))
            EcoPrimaryButton("完成", onClick = onDismiss, modifier = Modifier.fillMaxWidth())
        }
    }
}

/** 主题按钮专用弹层：只包含主题色，不再混入「更多」。 */
@Composable
private fun ThemeSheet(
    accent: EcoAccent,
    onPickAccent: (EcoAccent) -> Unit,
    onDismiss: () -> Unit,
) {
    EcoSheet(onDismiss = onDismiss, title = "主题色") {
        ThemeRow(selected = accent, onPick = onPickAccent)
    }
}

/**
 * 主题色按四列排列，选中状态使用对勾，不改变色点占位。
 */
@Composable
private fun ThemeRow(selected: EcoAccent, onPick: (EcoAccent) -> Unit) {
    val c = MaterialTheme.eco
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
        EcoAccent.entries.chunked(4).forEach { rowEntries ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                rowEntries.forEach { entry ->
                    val on = entry == selected
                    // 色点要显示「实际生效」的主色（eco 里做过整体提亮），不能用原始 Family 值
                    val dot = ecoColors(c.isDark, entry).blue
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onPick(entry) }
                            .padding(vertical = 4.dp),
                    ) {
                        Box(
                            Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(dot)
                                .then(
                                    if (on) {
                                        Modifier.border(2.dp, c.blue, CircleShape)
                                    } else {
                                        Modifier.border(1.dp, c.rule, CircleShape)
                                    },
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (on) {
                                Icon(
                                    EcoIcons.Check,
                                    contentDescription = null,
                                    // 浅底主题（白/暖金）用深色勾，深底用白勾
                                    tint = contentColorOn(dot),
                                    modifier = Modifier.size(17.dp),
                                )
                            }
                        }
                        Spacer(Modifier.height(5.dp))
                        Text(
                            entry.label,
                            style = EcoType.Pill.copy(fontSize = 10.5.sp),
                            color = if (on) c.ink else c.muted,
                            maxLines = 1,
                        )
                    }
                }
                // 末行不足 4 个时补占位，保持列宽一致
                repeat(4 - rowEntries.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

// ---------- 6. 弹层：货币 / 打赏 / 关于 ----------

/** 货币符号选择（9 种）：只改显示符号，不做换算。 */

@Composable
private fun AboutDialog(onOpenRepo: () -> Unit, onDismiss: () -> Unit) {
    val c = MaterialTheme.eco
    EcoDialog(onDismiss = onDismiss) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(15.dp))
                        .background(
                            Brush.linearGradient(
                                // 品牌块跟随主题（原来是固定的青绿，任何主题下都是绿的）
                                listOf(c.accentGradA, c.accentGradC),
                                start = Offset.Zero,
                                end = Offset.Infinite,
                            ),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        EcoIcons.Leaf,
                        contentDescription = null,
                        tint = c.onAccent,
                        modifier = Modifier.size(24.dp),
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("AeriPocket", style = EcoType.ModalTitle, color = c.ink, maxLines = 1)
                    Spacer(Modifier.height(1.dp))
                    Text(
                        "基于 ControlECO · GPL-3.0",
                        style = EcoType.PageNote,
                        color = c.muted,
                        maxLines = 1,
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            AboutLine("版本号", "v${BuildConfig.VERSION_NAME}（${BuildConfig.VERSION_CODE}）")
            AboutLine("构建类型", if (BuildConfig.DEBUG) "debug" else "release")
            AboutLine("开源仓库", GITHUB_REPO_PATH)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.Top) {
                Icon(
                    EcoIcons.Shield,
                    contentDescription = null,
                    tint = c.jade,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "只写本机数据库：不联网、不上传，无云端账号与第三方统计。",
                    style = EcoType.PageNote,
                    color = c.muted,
                )
            }
            Spacer(Modifier.height(16.dp))
            EcoPrimaryButton(
                text = "在浏览器中打开仓库",
                onClick = onOpenRepo,
                leading = EcoIcons.External,
                height = 40.dp,
            )
            Spacer(Modifier.height(8.dp))
            EcoGhostButton(text = "关闭", onClick = onDismiss, height = 40.dp)
        }
    }
}

@Composable
private fun AboutLine(label: String, value: String) {
    val c = MaterialTheme.eco
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = EcoType.RowSub, color = c.muted, modifier = Modifier.weight(1f), maxLines = 1)
        Text(value, style = EcoType.RowTrail, color = c.ink, maxLines = 1)
    }
}

// ---------- 公用 ----------

private fun currencyName(symbol: String): String =
    Money.CURRENCIES.firstOrNull { it.first == symbol }?.second ?: ""
