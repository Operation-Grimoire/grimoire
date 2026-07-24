package io.grimoire.app.ui.tour

import io.grimoire.app.ui.icon.*
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.animateIntOffsetAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import io.grimoire.app.R
import kotlin.math.roundToInt

private const val MAX_W_DP = 320
private const val MARGIN_DP = 16
private const val GAP_DP = 12
private const val ARROW_W_DP = 18
private const val ARROW_H_DP = 9

/**
 * The single, persistent tour balloon. It eases (offset) and morphs (size +
 * content) from one step to the next rather than being recreated, giving the
 * step-to-step transition. Positions itself just below the target (or above
 * when the target sits in the lower half), points an arrow at it, and centres
 * itself when the step has no target.
 */
@Composable
internal fun TourBalloon(
    step: TourStep,
    index: Int,
    stepCount: Int,
    target: Rect?,
    container: IntSize,
    onBack: () -> Unit,
    onNext: () -> Unit,
    onSkip: () -> Unit,
    onAction: (TourActionId) -> Unit,
) {
    val density = LocalDensity.current
    var size by remember { mutableStateOf(IntSize.Zero) }

    val marginPx = with(density) { MARGIN_DP.dp.roundToPx() }
    val gapPx = with(density) { GAP_DP.dp.roundToPx() }
    val arrowHPx = with(density) { ARROW_H_DP.dp.roundToPx() }
    val arrowInsetPx = with(density) { 20.dp.toPx() }

    // Below the target by default; above it when the target sits low enough that
    // a below-balloon would run off the bottom.
    val below = target == null || target.center.y < container.height / 2f

    val rawX = if (target == null) {
        (container.width - size.width) / 2f
    } else {
        target.center.x - size.width / 2f
    }
    val x = rawX.coerceIn(
        marginPx.toFloat(),
        (container.width - size.width - marginPx).coerceAtLeast(marginPx).toFloat(),
    )
    val y = when {
        target == null -> (container.height - size.height) / 2f
        below -> target.bottom + gapPx + arrowHPx
        else -> target.top - size.height - gapPx - arrowHPx
    }.coerceIn(
        marginPx.toFloat(),
        (container.height - size.height - marginPx).coerceAtLeast(marginPx).toFloat(),
    )

    val offset by animateIntOffsetAsState(
        targetValue = IntOffset(x.roundToInt(), y.roundToInt()),
        animationSpec = spring(stiffness = 380f),
        label = "tourBalloonOffset",
    )

    // Arrow centre relative to the balloon's left edge, kept inside the body.
    val arrowCenterX = target?.let {
        (it.center.x - x).coerceIn(arrowInsetPx, (size.width - arrowInsetPx).coerceAtLeast(arrowInsetPx))
    }

    Column(
        modifier = Modifier
            .offset { offset }
            .onSizeChanged { size = it }
            .widthIn(max = MAX_W_DP.dp),
    ) {
        if (target != null && below) BalloonArrow(pointsUp = true, centerX = arrowCenterX)
        Surface(
            color = MaterialTheme.colorScheme.inverseSurface,
            contentColor = MaterialTheme.colorScheme.inverseOnSurface,
            shape = MaterialTheme.shapes.large,
            shadowElevation = 6.dp,
        ) {
            AnimatedContent(
                targetState = index,
                transitionSpec = {
                    fadeIn(tween(180)) togetherWith fadeOut(tween(120)) using
                        SizeTransform(clip = false) { _, _ -> spring(stiffness = 320f) }
                },
                label = "tourBalloonContent",
            ) { i ->
                BalloonContent(
                    step = step,
                    index = i,
                    stepCount = stepCount,
                    onBack = onBack,
                    onNext = onNext,
                    onSkip = onSkip,
                    onAction = onAction,
                )
            }
        }
        if (target != null && !below) BalloonArrow(pointsUp = false, centerX = arrowCenterX)
    }
}

@Composable
private fun BalloonContent(
    step: TourStep,
    index: Int,
    stepCount: Int,
    onBack: () -> Unit,
    onNext: () -> Unit,
    onSkip: () -> Unit,
    onAction: (TourActionId) -> Unit,
) {
    val last = index >= stepCount - 1
    Column(
        modifier = Modifier.padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        when (step.art) {
            TourArt.Welcome -> Icon(AppIcons.AutoStories, contentDescription = null)
            TourArt.Done -> Icon(AppIcons.Celebration, contentDescription = null)
            TourArt.None -> Unit
        }
        Text(stringResource(step.titleRes), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(step.bodyRes), style = MaterialTheme.typography.bodyMedium)

        step.actions.forEach { action ->
            OutlinedButton(onClick = { onAction(action) }) { Text(stringResource(action.labelRes)) }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.tour_progress, index + 1, stepCount), style = MaterialTheme.typography.labelMedium)
            if (!last) {
                Spacer(Modifier.size(8.dp))
                TextButton(onClick = onSkip) { Text(stringResource(R.string.action_skip)) }
            }
            Spacer(Modifier.weight(1f))
            if (index > 0) TextButton(onClick = onBack) { Text(stringResource(R.string.action_back)) }
            Button(onClick = onNext) {
                Text(stringResource(if (last) R.string.action_done else R.string.action_next))
            }
        }
    }
}

@Composable
private fun BalloonArrow(pointsUp: Boolean, centerX: Float?) {
    val color = MaterialTheme.colorScheme.inverseSurface
    val startPad = with(LocalDensity.current) {
        ((centerX ?: 0f) - ARROW_W_DP.dp.toPx() / 2f).coerceAtLeast(0f).toDp()
    }
    Canvas(
        modifier = Modifier
            .padding(start = startPad)
            .size(ARROW_W_DP.dp, ARROW_H_DP.dp),
    ) {
        val path = Path().apply {
            if (pointsUp) {
                moveTo(size.width / 2f, 0f)
                lineTo(0f, size.height)
                lineTo(size.width, size.height)
            } else {
                moveTo(0f, 0f)
                lineTo(size.width, 0f)
                lineTo(size.width / 2f, size.height)
            }
            close()
        }
        drawPath(path, color)
    }
}
