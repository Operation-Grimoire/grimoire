package io.grimoire.app.ui.screen.settings.tts

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import io.grimoire.app.data.tts.TtsEngineType
import io.grimoire.app.ui.screen.reader.StepperRow
import io.grimoire.app.ui.screen.settings.common.SettingsSectionHeader

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TtsSettingsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToVoice: (language: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TtsSettingsViewModel = hiltViewModel(),
) {
    val engine by viewModel.engine.collectAsState()
    val speechRate by viewModel.speechRate.collectAsState()
    val pitch by viewModel.pitch.collectAsState()
    val autoAdvance by viewModel.autoAdvance.collectAsState()
    val apiKey by viewModel.apiKey.collectAsState()
    val languages by viewModel.languages.collectAsState()
    val deviceVoices by viewModel.deviceVoiceMap.collectAsState()
    val cloudVoices by viewModel.cloudVoiceMap.collectAsState()

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                title = { Text("Text-to-speech") },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.padding(padding)) {
            item { SettingsSectionHeader("Speech engine") }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = engine == TtsEngineType.DEVICE,
                        onClick = { viewModel.setEngine(TtsEngineType.DEVICE) },
                        label = { Text("On-device") },
                    )
                    FilterChip(
                        selected = engine == TtsEngineType.ELEVENLABS,
                        onClick = { viewModel.setEngine(TtsEngineType.ELEVENLABS) },
                        label = { Text("ElevenLabs") },
                    )
                }
            }
            item {
                Text(
                    text = when (engine) {
                        TtsEngineType.DEVICE ->
                            "Reads aloud with the device's built-in voices. Works offline."
                        TtsEngineType.ELEVENLABS ->
                            "Streams natural cloud voices from ElevenLabs. Needs a network " +
                                "connection and is billed per character on your account."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }

            if (engine == TtsEngineType.ELEVENLABS) {
                item { SettingsSectionHeader("ElevenLabs account") }
                item {
                    var draft by remember { mutableStateOf(apiKey) }
                    LaunchedEffect(apiKey) { if (apiKey != draft) draft = apiKey }
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { draft = it; viewModel.setApiKey(it) },
                        label = { Text("API key") },
                        singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            imeAction = ImeAction.Done,
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
            }

            item { SettingsSectionHeader("Playback") }
            item {
                ListItem(
                    headlineContent = {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            StepperRow(
                                label = "Speech rate",
                                value = "%.2f×".format(speechRate / 100f),
                                onDecrement = { viewModel.setSpeechRate(speechRate - 5) },
                                onIncrement = { viewModel.setSpeechRate(speechRate + 5) },
                                decrementEnabled = speechRate > 25,
                                incrementEnabled = speechRate < 300,
                            )
                            StepperRow(
                                label = "Pitch (on-device only)",
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
                    headlineContent = { Text("Auto-advance to next chapter") },
                    supportingContent = {
                        Text("Keep reading into the next chapter when one finishes")
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

            item { SettingsSectionHeader("Voices") }
            item {
                Text(
                    text = "Pick which voice reads each language. Novels are matched by " +
                        "their content language.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            items(languages, key = { it }) { language ->
                val voices = if (engine == TtsEngineType.ELEVENLABS) cloudVoices else deviceVoices
                ListItem(
                    headlineContent = { Text(language) },
                    supportingContent = { Text(viewModel.voiceSummary(language, voices)) },
                    trailingContent = {
                        Icon(Icons.AutoMirrored.Filled.ArrowForwardIos, contentDescription = null)
                    },
                    modifier = Modifier.clickable { onNavigateToVoice(language) },
                )
            }
        }
    }
}
