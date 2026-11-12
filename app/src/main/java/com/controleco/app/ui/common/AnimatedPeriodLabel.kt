package com.controleco.app.ui.common

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import com.controleco.app.data.PeriodMode
import com.controleco.app.ui.eco.LocalAnimationsEnabled
import java.time.YearMonth

/** A shared, directional date change for both manual selection and continuous ledger scrolling. */
@Composable
internal fun AnimatedPeriodLabel(
    month: YearMonth, mode: PeriodMode, style: TextStyle, color: Color,
    modifier: Modifier = Modifier, label: (YearMonth, PeriodMode) -> String = ::periodLabel,
) {
    if (!LocalAnimationsEnabled.current) {
        Text(label(month, mode), modifier = modifier, style = style, color = color, maxLines = 1)
        return
    }
    AnimatedContent(targetState = month to mode, modifier = modifier, contentAlignment = Alignment.Center,
        transitionSpec = {
            val direction = if (targetState.first < initialState.first) -1 else 1
            ((slideInVertically(tween(200)) { it * direction } + fadeIn(tween(160))) togetherWith
                (slideOutVertically(tween(200)) { -it * direction } + fadeOut(tween(120))))
                .using(SizeTransform(clip = true))
        }, label = "periodChange") { (value, periodMode) ->
        Text(label(value, periodMode), style = style, color = color, maxLines = 1)
    }
}
