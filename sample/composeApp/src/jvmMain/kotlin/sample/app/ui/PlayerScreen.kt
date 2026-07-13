package sample.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import sample.app.player.PlayerActions
import sample.app.player.PlayerUiState

/**
 * Stateless player UI: a classic Material screen (app bar + a single content
 * column). Renders [state] and forwards user intents to [actions].
 */
@Composable
fun PlayerScreen(state: State<PlayerUiState>, actions: PlayerActions) {
    val ui by state

    Scaffold(
        topBar = { TopAppBar(title = { Text("RodioKt — audio demo") }) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
        ) {
            SourceTabs(selected = ui.source, onSelect = actions::selectSource)
            Spacer(Modifier.height(16.dp))

            SourceInput(
                source = ui.source,
                filePath = ui.filePath,
                streamUrl = ui.streamUrl,
                onFilePathChange = actions::updateFilePath,
                onStreamUrlChange = actions::updateStreamUrl,
                onBrowse = actions::browseFile,
            )
            Spacer(Modifier.height(24.dp))

            StatusRow(event = ui.event, trackTitle = ui.trackTitle)
            Spacer(Modifier.height(12.dp))

            ProgressSection(
                event = ui.event,
                seekable = ui.seekable,
                positionMs = ui.displayPositionMs,
                durationMs = ui.durationMs,
                onSeekPreview = actions::previewSeek,
                onSeekCommit = actions::commitSeek,
            )
            Spacer(Modifier.height(24.dp))

            Transport(
                event = ui.event,
                hasSource = ui.hasSource,
                onPlayPause = actions::playPause,
                onStop = actions::stop,
            )
            Spacer(Modifier.height(24.dp))

            VolumeControl(volume = ui.volume, onChange = actions::setVolume)

            ui.error?.let { message ->
                Spacer(Modifier.height(16.dp))
                Text(
                    message,
                    color = MaterialTheme.colors.error,
                    style = MaterialTheme.typography.caption,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
