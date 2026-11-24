package io.grimoire.app.ui.screen.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.grimoire.app.R
import io.grimoire.app.data.tts.TtsEngineType
import io.grimoire.ui.component.ActionLinkCard
import io.grimoire.ui.component.sheet.SheetSectionLabel
import io.grimoire.ui.component.sheet.SheetSwitchRow
import io.grimoire.ui.component.sheet.SingleChoiceSegmented
import io.grimoire.ui.component.sheet.StepperRow
import io.grimoire.ui.icon.AppIcons
import io.grimoire.ui.icon.KeyboardArrowRight
import io.grimoire.ui.icon.RecordVoiceOver

@Composable
internal fun ReaderTtsSettings(
    enabled: Boolean,
    engine: TtsEngineType,
    speechRate: Int,
    pitch: Int,
    autoAdvance: Boolean,
    onEnabled: (Boolean) -> Unit,
    onEngine: (TtsEngineType) -> Unit,
    onSpeechRate: (Int) -> Unit,
    onPitch: (Int) -> Unit,
    onAutoAdvance: (Boolean) -> Unit,
    onOpenFullSettings: () -> Unit,
) {
    Column(
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(top = 4.dp, bottom = 16.dp),
    ) {
        SheetSwitchRow(
            title = stringResource(R.string.reader_enable_tts),
            hint = stringResource(R.string.reader_enable_tts_hint),
            checked = enabled,
            onCheckedChange = onEnabled,
        )

        HorizontalDivider(
            modifier = Modifier.padding(vertical = 4.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
        )

        SheetSectionLabel(stringResource(R.string.reader_speech_engine), enabled = enabled)
        Spacer(Modifier.height(6.dp))
        SingleChoiceSegmented(
            options = TtsEngineType.entries,
            selected = engine,
            onSelect = onEngine,
            enabled = enabled,
            label = { value ->
                when (value) {
                    TtsEngineType.DEVICE -> stringResource(R.string.reader_on_device)
                    TtsEngineType.ELEVENLABS -> "ElevenLabs"
                }
            },
        )

        Spacer(Modifier.height(8.dp))
        Column(
            modifier = Modifier.padding(start = 16.dp, end = 4.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            StepperRow(
                label = stringResource(R.string.reader_speech_rate),
                value = "%.2f×".format(speechRate / 100f),
                onDecrement = { onSpeechRate(speechRate - 5) },
                onIncrement = { onSpeechRate(speechRate + 5) },
                decrementEnabled = speechRate > 25,
                incrementEnabled = speechRate < 300,
                enabled = enabled,
            )

            StepperRow(
                label = stringResource(R.string.reader_pitch),
                value = "%.2f×".format(pitch / 100f),
                onDecrement = { onPitch(pitch - 5) },
                onIncrement = { onPitch(pitch + 5) },
                decrementEnabled = pitch > 50,
                incrementEnabled = pitch < 200,
                enabled = enabled && engine == TtsEngineType.DEVICE,
            )
        }

        Spacer(Modifier.height(4.dp))
        SheetSwitchRow(
            title = stringResource(R.string.reader_auto_advance),
            checked = autoAdvance,
            onCheckedChange = onAutoAdvance,
            enabled = enabled,
        )

        Spacer(Modifier.height(8.dp))
        ActionLinkCard(
            title = stringResource(R.string.reader_manage_voices),
            leadingIcon = AppIcons.RecordVoiceOver,
            trailingIcon = AppIcons.KeyboardArrowRight,
            onClick = onOpenFullSettings,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
    }
}
