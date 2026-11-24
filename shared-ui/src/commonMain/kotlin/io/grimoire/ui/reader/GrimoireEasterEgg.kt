package io.grimoire.ui.reader

import io.grimoire.ui.resources.*
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import kotlin.math.PI
import kotlin.math.sin

// Whole-word, case-insensitive — allows trailing "s" so "grimoires" is also styled. The
// match runs on the *parsed* AnnotatedString (post-HTML), so HTML attribute values like
// href="grimoire" don't leak into the rendered text and can't false-match.
val GRIMOIRE_REGEX = "\\bgrimoires?\\b".toRegex(RegexOption.IGNORE_CASE)
internal const val GRIMOIRE_ANNOTATION_TAG = "grimoire_tag"

// Wave runs in deep-green → emerald → deep-green, modulated per character so the highlight
// reads like a pulse travelling along the word.
private val GRIMOIRE_DARK = Color(0xFF0A3A0A)
private val GRIMOIRE_LIGHT = Color(0xFF7CFC4D)
// >1 puts more than one crest in the word so even short matches (~8 chars) show
// visible banding rather than a single fade.
private const val GRIMOIRE_WAVE_FREQUENCY = 1.5f

// All paragraphs sample the same 18-step, 1.8-second wave.
fun grimoireWavePhase(elapsedMillis: Long): Float =
    ((elapsedMillis / 100) % 18).toFloat() / 18f

fun grimoireWaveColor(phase: Float, charPos: Float): Color {
    val arg = (GRIMOIRE_WAVE_FREQUENCY * charPos - phase).mod(1f)
    val w = (sin(arg * 2f * PI.toFloat()) + 1f) / 2f
    return lerp(GRIMOIRE_DARK, GRIMOIRE_LIGHT, w)
}

@Composable
fun GrimoireEasterEggDialog(
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.reader_grimoire_highlight)) },
        text = {
            Column {
                Text(
                    text = stringResource(Res.string.reader_grimoire_highlight_description),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(Res.string.reader_enabled),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Switch(checked = enabled, onCheckedChange = onToggle)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_close)) }
        },
    )
}
