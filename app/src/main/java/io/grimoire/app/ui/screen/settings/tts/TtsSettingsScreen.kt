package io.grimoire.app.ui.screen.settings.tts

import io.grimoire.app.ui.icon.*
import androidx.compose.foundation.clickable
import io.grimoire.app.ui.component.PlainTooltipIconButton
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import io.grimoire.app.data.tts.TtsEngineType
import io.grimoire.app.ui.screen.reader.StepperRow
import io.grimoire.app.ui.screen.settings.common.SettingsSectionHeader
import io.grimoire.app.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TtsSettingsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToVoice: (language: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TtsSettingsViewModel = hiltViewModel(),
) {
    val enabled by viewModel.enabled.collectAsState()
    val engine by viewModel.engine.collectAsState()
    val speechRate by viewModel.speechRate.collectAsState()
    val pitch by viewModel.pitch.collectAsState()
    val autoAdvance by viewModel.autoAdvance.collectAsState()
    val apiKey by viewModel.apiKey.collectAsState()
    val languages by viewModel.languages.collectAsState()
    val deviceVoices by viewModel.deviceVoiceMap.collectAsState()
    val cloudVoices by viewModel.cloudVoiceMap.collectAsState()
    val usageState by viewModel.usageState.collectAsState()

    LaunchedEffect(engine) {
        if (engine == TtsEngineType.ELEVENLABS) viewModel.loadUsage()
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    PlainTooltipIconButton(onClick = onNavigateBack, tooltip = stringResource(R.string.action_back)) {
                        Icon(AppIcons.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                title = { Text(stringResource(R.string.settings_tts_title)) },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.padding(padding)) {
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.tts_enable)) },
                    supportingContent = {
                        Text(stringResource(R.string.tts_enable_summary))
                    },
                    trailingContent = {
                        Switch(
                            checked = enabled,
                            onCheckedChange = { viewModel.setEnabled(it) },
                        )
                    },
                    modifier = Modifier.clickable { viewModel.setEnabled(!enabled) },
                )
            }
            item { SettingsSectionHeader(stringResource(R.string.tts_speech_engine)) }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = engine == TtsEngineType.DEVICE,
                        onClick = { viewModel.setEngine(TtsEngineType.DEVICE) },
                        label = { Text(stringResource(R.string.tts_on_device)) },
                    )
                    FilterChip(
                        selected = engine == TtsEngineType.ELEVENLABS,
                        onClick = { viewModel.setEngine(TtsEngineType.ELEVENLABS) },
                        label = { Text(stringResource(R.string.tts_elevenlabs)) },
                    )
                }
            }
            item {
                Text(
                    text = when (engine) {
                        TtsEngineType.DEVICE -> stringResource(R.string.tts_device_summary)
                        TtsEngineType.ELEVENLABS -> stringResource(R.string.tts_elevenlabs_summary)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }

            if (engine == TtsEngineType.ELEVENLABS) {
                item { SettingsSectionHeader(stringResource(R.string.tts_elevenlabs_account)) }
                item {
                    var draft by remember { mutableStateOf(apiKey) }
                    LaunchedEffect(apiKey) { if (apiKey != draft) draft = apiKey }
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { draft = it; viewModel.setApiKey(it) },
                        label = { Text(stringResource(R.string.tts_api_key)) },
                        singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            imeAction = ImeAction.Done,
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
                item {
                    ElevenLabsUsageCard(state = usageState, onRefresh = viewModel::loadUsage)
                }
            }

            item { SettingsSectionHeader(stringResource(R.string.tts_playback)) }
            item {
                ListItem(
                    headlineContent = {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            StepperRow(
                                label = stringResource(R.string.tts_speech_rate),
                                value = "%.2f×".format(speechRate / 100f),
                                onDecrement = { viewModel.setSpeechRate(speechRate - 5) },
                                onIncrement = { viewModel.setSpeechRate(speechRate + 5) },
                                decrementEnabled = speechRate > 25,
                                incrementEnabled = speechRate < 300,
                            )
                            StepperRow(
                                label = stringResource(R.string.tts_pitch),
                                value = "%.2f×".format(pitch / 100f),
                                onDecrement = { viewModel.setPitch(pitch - 5) },
                                onIncrement = { viewModel.setPitch(pitch + 5) },
                                decrementEnabled = pitch > 50,
                                incrementEnabled = pitch < 200,
                            )
                        }
                    },
                )
            }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.tts_auto_advance)) },
                    supportingContent = {
                        Text(stringResource(R.string.tts_auto_advance_summary))
                    },
                    trailingContent = {
                        Switch(
                            checked = autoAdvance,
                            onCheckedChange = { viewModel.setAutoAdvance(it) },
                        )
                    },
                    modifier = Modifier.clickable { viewModel.setAutoAdvance(!autoAdvance) },
                )
            }

            item { SettingsSectionHeader(stringResource(R.string.tts_voices)) }
            item {
                Text(
                    text = stringResource(R.string.tts_voices_summary),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            items(languages, key = { it }) { language ->
                val voices = if (engine == TtsEngineType.ELEVENLABS) cloudVoices else deviceVoices
                ListItem(
                    headlineContent = { Text(language) },
                    supportingContent = {
                        Text(
                            stringResource(
                                if (viewModel.hasCustomVoice(language, voices)) R.string.tts_custom_voice
                                else R.string.tts_system_default,
                            ),
                        )
                    },
                    trailingContent = {
                        Icon(AppIcons.ArrowForwardIos, contentDescription = null)
                    },
                    modifier = Modifier.clickable { onNavigateToVoice(language) },
                )
            }
        }
    }
}

@Composable
private fun ElevenLabsUsageCard(state: UsageState, onRefresh: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.tts_credit_usage),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            PlainTooltipIconButton(onClick = onRefresh, tooltip = stringResource(R.string.tts_refresh_usage)) {
                Icon(AppIcons.Refresh, contentDescription = stringResource(R.string.tts_refresh_usage))
            }
        }
        when (state) {
            is UsageState.Idle -> Text(
                text = stringResource(R.string.tts_usage_api_key_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            is UsageState.Loading -> Text(
                text = stringResource(R.string.tts_loading),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            is UsageState.Error -> Text(
                text = state.message.ifBlank { stringResource(R.string.tts_load_usage_failed) },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
            is UsageState.Loaded -> {
                val usage = state.usage
                LinearProgressIndicator(
                    progress = { usage.fraction },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = stringResource(
                        R.string.tts_usage_summary,
                        usage.characterCount,
                        usage.characterLimit,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                )
                usage.nextResetUnixMs?.let { resetMs ->
                    val date = java.text.DateFormat
                        .getDateInstance(java.text.DateFormat.MEDIUM)
                        .format(java.util.Date(resetMs))
                    Text(
                        text = stringResource(R.string.tts_quota_reset, date),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
