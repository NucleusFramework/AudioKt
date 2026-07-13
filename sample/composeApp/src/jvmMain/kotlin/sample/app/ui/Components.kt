package sample.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.Button
import androidx.compose.material.LinearProgressIndicator
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedButton
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Slider
import androidx.compose.material.Tab
import androidx.compose.material.TabRow
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.nucleusframework.rodio.PlaybackEvent
import sample.app.player.Source
import sample.app.util.formatTime

@Composable
fun SourceTabs(selected: Source, onSelect: (Source) -> Unit) {
    TabRow(selectedTabIndex = selected.ordinal) {
        Source.entries.forEach { option ->
            Tab(
                selected = option == selected,
                onClick = { onSelect(option) },
                text = { Text(option.label) },
            )
        }
    }
}

@Composable
fun SourceInput(
    source: Source,
    filePath: String,
    streamUrl: String,
    onFilePathChange: (String) -> Unit,
    onStreamUrlChange: (String) -> Unit,
    onBrowse: () -> Unit,
) {
    when (source) {
        Source.File -> Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = filePath,
                onValueChange = onFilePathChange,
                singleLine = true,
                label = { Text("Audio file path") },
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(12.dp))
            OutlinedButton(onClick = onBrowse) { Text("Browse") }
        }

        Source.Stream -> OutlinedTextField(
            value = streamUrl,
            onValueChange = onStreamUrlChange,
            singleLine = true,
            label = { Text("Stream URL (http/https)") },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
fun StatusRow(event: PlaybackEvent, trackTitle: String?) {
    val label = when (event) {
        PlaybackEvent.CONNECTING -> "Connecting…"
        PlaybackEvent.PLAYING -> "Playing"
        PlaybackEvent.PAUSED -> "Paused"
        PlaybackEvent.STOPPED -> "Stopped"
    }
    Column {
        Text("Status: $label", style = MaterialTheme.typography.body2, fontWeight = FontWeight.Medium)
        if (trackTitle != null) {
            Text(
                trackTitle,
                style = MaterialTheme.typography.caption,
                color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f),
            )
        }
    }
}

@Composable
fun ProgressSection(
    event: PlaybackEvent,
    seekable: Boolean,
    positionMs: Long,
    durationMs: Long?,
    onSeekPreview: (Long) -> Unit,
    onSeekCommit: () -> Unit,
) {
    val active = event != PlaybackEvent.STOPPED
    val total = durationMs?.takeIf { it > 0 }

    when {
        active && seekable && total != null -> {
            Slider(
                value = positionMs.coerceIn(0, total).toFloat(),
                onValueChange = { onSeekPreview(it.toLong()) },
                onValueChangeFinished = onSeekCommit,
                valueRange = 0f..total.toFloat(),
                modifier = Modifier.fillMaxWidth(),
            )
            TimeRow(positionMs, total)
        }

        active -> {
            if (total != null) {
                LinearProgressIndicator(
                    progress = positionMs.coerceAtMost(total).toFloat() / total.toFloat(),
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            TimeRow(positionMs, total)
        }

        else -> Text(
            "Load a file or stream to begin",
            style = MaterialTheme.typography.body2,
            color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f),
        )
    }
}

@Composable
private fun TimeRow(positionMs: Long, totalMs: Long?) {
    Spacer(Modifier.height(6.dp))
    val faint = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(formatTime(positionMs), style = MaterialTheme.typography.caption, color = faint)
        Text(totalMs?.let { formatTime(it) } ?: "--:--", style = MaterialTheme.typography.caption, color = faint)
    }
}

@Composable
fun Transport(
    event: PlaybackEvent,
    hasSource: Boolean,
    onPlayPause: () -> Unit,
    onStop: () -> Unit,
) {
    val isPlaying = event == PlaybackEvent.PLAYING
    val isConnecting = event == PlaybackEvent.CONNECTING
    val playEnabled = (hasSource || event == PlaybackEvent.PAUSED) && !isConnecting

    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Button(onClick = onPlayPause, enabled = playEnabled) {
            Text(if (isPlaying) "Pause" else "Play")
        }
        OutlinedButton(onClick = onStop, enabled = event != PlaybackEvent.STOPPED) {
            Text("Stop")
        }
    }
}

@Composable
fun VolumeControl(volume: Float, onChange: (Float) -> Unit) {
    Column {
        Text("Volume: ${(volume * 100).toInt()}%", style = MaterialTheme.typography.body2)
        Slider(
            value = volume,
            onValueChange = onChange,
            valueRange = 0f..1f,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
