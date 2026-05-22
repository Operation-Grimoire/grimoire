package io.grimoire.app.ui.screen.settings.tts

import android.content.Intent
import android.speech.tts.TextToSpeech
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import io.grimoire.app.data.tts.TtsEngineType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TtsVoicePickerScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TtsSettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.voiceState.collectAsState()
    val selectedId by viewModel.selectedVoiceId.collectAsState()
    val engine by viewModel.engine.collectAsState()
    val context = LocalContext.current

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBarWithTitle(
                title = viewModel.language ?: "Voice",
                onNavigateBack = onNavigateBack,
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (val s = state) {
                is VoiceListState.Loading -> CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                )

                is VoiceListState.NeedsApiKey -> CenteredMessage(
                    text = "Add an ElevenLabs API key in Text-to-speech settings to " +
                        "choose a cloud voice.",
                )

                is VoiceListState.Error -> Column(
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        s.message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    TextButton(onClick = viewModel::loadVoices) { Text("Retry") }
                }

                is VoiceListState.Loaded -> LazyColumn(Modifier.fillMaxSize()) {
                    item {
                        VoiceRow(
                            title = "System default",
                            subtitle = "Let the engine pick a voice for this language",
                            selected = selectedId == null,
                            onClick = { viewModel.selectVoice(null) },
                        )
                        HorizontalDivider()
                    }
                    items(s.voices, key = { it.id }) { voice ->
                        VoiceRow(
                            title = voice.displayName,
                            subtitle = voice.detail,
                            selected = selectedId == voice.id,
                            onClick = { viewModel.selectVoice(voice.id) },
                        )
                    }
                    if (engine == TtsEngineType.DEVICE) {
                        item {
                            HorizontalDivider()
                            ListItem(
                                headlineContent = { Text("Install more voices…") },
                                supportingContent = {
                                    Text("Download additional voice data for the device engine")
                                },
                                modifier = Modifier.clickable {
                                    runCatching {
                                        context.startActivity(
                                            Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA)
                                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                        )
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TopAppBarWithTitle(title: String, onNavigateBack: () -> Unit) {
    androidx.compose.material3.TopAppBar(
        navigationIcon = {
            IconButton(onClick = onNavigateBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
        },
        title = { Text(title) },
    )
}

@Composable
private fun VoiceRow(
    title: String,
    subtitle: String?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = if (subtitle != null) {
            { Text(subtitle) }
        } else {
            null
        },
        leadingContent = { RadioButton(selected = selected, onClick = null) },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@Composable
private fun CenteredMessage(text: String) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
