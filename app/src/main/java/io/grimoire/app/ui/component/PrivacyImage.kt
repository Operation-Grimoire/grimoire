package io.grimoire.app.ui.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.HideImage
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

private const val FADE_DURATION_MS = 250
private const val TAP_REVEAL_THRESHOLD_MS = 200L
private const val DEFAULT_PLACEHOLDER_ASPECT = 3f / 2f

/** Hidden behind a placeholder until pressed; short tap calls [onTapReveal], long press fades the image in only while held. Image bytes aren't requested until first interaction. */
@Composable
fun PrivacyImage(
    model: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    onTapReveal: () -> Unit,
) {
    var triggered by remember(model) { mutableStateOf(false) }
    var pressed by remember(model) { mutableStateOf(false) }
    var loadedAspect by remember(model) { mutableFloatStateOf(DEFAULT_PLACEHOLDER_ASPECT) }

    val alpha by animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = tween(durationMillis = FADE_DURATION_MS),
        label = "privacyImageAlpha",
    )

    Box(
        modifier = modifier
            .aspectRatio(loadedAspect)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .pointerInput(model) {
                detectTapGestures(
                    onPress = {
                        triggered = true
                        pressed = true
                        val startMs = System.currentTimeMillis()
                        val released = tryAwaitRelease()
                        pressed = false
                        if (released && System.currentTimeMillis() - startMs < TAP_REVEAL_THRESHOLD_MS) {
                            onTapReveal()
                        }
                    },
                )
            },
    ) {
        if (triggered) {
            AsyncImage(
                model = model,
                contentDescription = contentDescription,
                contentScale = ContentScale.FillWidth,
                onSuccess = { state ->
                    val size = state.painter.intrinsicSize
                    if (size.width > 0f && size.height > 0f) {
                        loadedAspect = size.width / size.height
                    }
                },
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(alpha),
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .alpha(1f - alpha),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = Icons.Outlined.HideImage,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(32.dp),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Tap to reveal · hold to peek",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
