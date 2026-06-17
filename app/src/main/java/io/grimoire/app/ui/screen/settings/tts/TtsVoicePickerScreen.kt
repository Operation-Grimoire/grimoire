package io.grimoire.app.ui.screen.settings.tts

import android.content.Intent
import android.widget.Toast
import io.grimoire.app.ui.component.PlainTooltipIconButton
import android.speech.tts.TextToSpeech
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import io.grimoire.app.data.tts.TtsEngineType
import io.grimoire.app.data.tts.TtsPreviewManager

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
    val preview by viewModel.previewState.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(preview.error) {
        preview.error?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.clearPreviewError()
        }
    }

    // Stop any audition when the picker leaves the composition.
    DisposableEffect(Unit) {
        onDispose { viewModel.stopPreview() }
    }

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
                    if (engine == TtsEngineType.ELEVENLABS) {
                        item {
                            Text(
                                text = "The ElevenLabs free plan is limited (~10,000 " +
                                    "characters/month) and many voices — even some Default " +
                                    "ones — require a paid plan. If a voice won't play, try " +
                                    "another, or use the On-device engine for unlimited " +
                                    "offline reading.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            )
                            HorizontalDivider()
                        }
                    }
                    item {
                        VoiceRow(
                            title = "System default",
                            subtitle = "Let the engine pick a voice for this language",
                            selected = selectedId == null,
                            previewState = previewStateFor(preview, viewModel.previewKey(null)),
                            onClick = { viewModel.selectVoice(null) },
                            onPreview = { viewModel.previewVoice(null) },
                        )
                        HorizontalDivider()
                    }
                    items(s.voices, key = { it.id }) { voice ->
                        VoiceRow(
                            title = voice.displayName,
                            subtitle = voice.detail,
                            selected = selectedId == voice.id,
                            previewState = previewStateFor(preview, viewModel.previewKey(voice.id)),
                            onClick = { viewModel.selectVoice(voice.id) },
                            onPreview = { viewModel.previewVoice(voice.id) },
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
            PlainTooltipIconButton(onClick = onNavigateBack, tooltip = "Back") {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
        },
        title = { Text(title) },
    )
}

/** Lifecycle of the per-row preview button. */
private enum class PreviewButtonState { IDLE, LOADING, PLAYING }

/** Maps the shared [TtsPreviewManager.State] onto a single row identified by [key]. */
private fun previewStateFor(state: TtsPreviewManager.State, key: String): PreviewButtonState =
    if (state.key != key) {
        PreviewButtonState.IDLE
    } else when (state.phase) {
        TtsPreviewManager.Phase.LOADING -> PreviewButtonState.LOADING
        TtsPreviewManager.Phase.PLAYING -> PreviewButtonState.PLAYING
    }

@Composable
private fun VoiceRow(
    title: String,
    subtitle: String?,
    selected: Boolean,
    previewState: PreviewButtonState,
    onClick: () -> Unit,
    onPreview: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = if (subtitle != null) {
            { Text(subtitle) }
        } else {
            null
        },
        leadingContent = { RadioButton(selected = selected, onClick = null) },
        trailingContent = { PreviewButton(previewState, onPreview) },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@Composable
private fun PreviewButton(state: PreviewButtonState, onClick: () -> Unit) {
    when (state) {
        PreviewButtonState.LOADING -> IconButton(onClick = onClick) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
        }

        PreviewButtonState.PLAYING -> IconButton(onClick = onClick) {
            Icon(Icons.Filled.Stop, contentDescription = "Stop preview")
        }

        PreviewButtonState.IDLE -> IconButton(onClick = onClick) {
            Icon(Icons.Filled.PlayArrow, contentDescription = "Preview voice")
        }
    }
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
