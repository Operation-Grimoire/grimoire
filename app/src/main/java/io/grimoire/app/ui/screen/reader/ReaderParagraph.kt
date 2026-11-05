package io.grimoire.app.ui.screen.reader

import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

internal class GrimoireWaveClock {
    var matchingParagraphs by mutableIntStateOf(0)
    val phase = mutableStateOf(0f)
}

@Composable
internal fun GrimoireWaveTicker(wave: GrimoireWaveClock, enabled: Boolean) {
    val active = enabled && wave.matchingParagraphs > 0
    if (active) {
        LaunchedEffect(wave) {
            while (true) {
                wave.phase.value = grimoireWavePhase(SystemClock.uptimeMillis())
                delay(100)
            }
        }
    }
}

@Composable
internal fun ReaderParagraph(
    formattedText: String?,
    plainText: String,
    highlighted: Boolean,
    textStyle: TextStyle,
    foreground: Color,
    paragraphSpacing: Int,
    effectActive: Boolean,
    wave: GrimoireWaveClock,
    onWordTap: () -> Unit,
    onOtherTap: () -> Unit,
) {
    // Sources that opt into rich formatting populate
    // `formattedText` with the constrained-HTML subset that
    // AnnotatedString.fromHtml understands (<i>, <b>, <u>,
    // <a>, <br>, &nbsp;). Otherwise fall back to plain text.
    // Cached per page so we don't re-parse HTML on every
    // recomposition (scroll, TTS highlight, etc.).
    val rendered = remember(formattedText, plainText) {
        formattedText?.let { AnnotatedString.fromHtml(it) }
            ?: AnnotatedString(plainText.trim())
    }
    val grimoireMatches = remember(rendered) {
        GRIMOIRE_REGEX.findAll(rendered.text).map { it.range }.toList()
    }
    val hasMatches = grimoireMatches.isNotEmpty()
    if (hasMatches) {
        DisposableEffect(wave) {
            wave.matchingParagraphs++
            onDispose { wave.matchingParagraphs-- }
        }
    }
    val displayText = if (effectActive && hasMatches) {
        buildAnnotatedString {
            append(rendered)
            grimoireMatches.forEach { r ->
                val len = (r.last - r.first + 1).coerceAtLeast(1)
                // One SpanStyle per character so each letter can carry a
                // different phase of the wave.
                for (i in 0 until len) {
                    val pos = i.toFloat() / len
                    val color = grimoireWaveColor(wave.phase.value, pos)
                    addStyle(
                        SpanStyle(
                            color = color,
                            fontWeight = FontWeight.Bold,
                        ),
                        r.first + i,
                        r.first + i + 1,
                    )
                }
                addStringAnnotation(
                    GRIMOIRE_ANNOTATION_TAG,
                    rendered.text.substring(r),
                    r.first,
                    r.last + 1,
                )
            }
        }
    } else rendered
    var layoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
    val tapModifier = if (effectActive && hasMatches) {
        Modifier.pointerInput(grimoireMatches) {
            detectTapGestures { pos ->
                val lr = layoutResult
                val onWord = lr != null && grimoireMatches.any { range ->
                    val offset = lr.getOffsetForPosition(pos)
                    offset in range.first..range.last
                }
                if (onWord) onWordTap()
                else onOtherTap()
            }
        }
    } else Modifier
    Text(
        text = displayText,
        style = textStyle,
        onTextLayout = { layoutResult = it },
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (highlighted) {
                    Modifier.background(foreground.copy(alpha = 0.10f))
                } else {
                    Modifier
                },
            )
            .then(tapModifier)
            .padding(bottom = paragraphSpacing.dp),
    )
}
