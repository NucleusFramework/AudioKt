package sample.app.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import dev.nucleusframework.rodio.PlaybackCallback
import dev.nucleusframework.rodio.PlaybackEvent
import dev.nucleusframework.rodio.RodioPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import sample.app.util.pickAudioFile

/**
 * State holder that owns the native [RodioPlayer] and exposes a single
 * observable [PlayerUiState] plus [PlayerActions] intents. All state mutation
 * is marshalled onto [scope] (the composition's Main dispatcher) so writes stay
 * on one thread — rodio invokes the callback from its own decoder threads.
 */
class PlayerController(private val scope: CoroutineScope) : PlayerActions {
    private val player = RodioPlayer()

    private val _state = mutableStateOf(PlayerUiState())
    val state: State<PlayerUiState> get() = _state

    private var current: PlayerUiState
        get() = _state.value
        set(value) {
            _state.value = value
        }

    private val callback = object : PlaybackCallback {
        override fun onEvent(event: PlaybackEvent) = update {
            copy(event = event, trackTitle = if (event == PlaybackEvent.STOPPED) null else trackTitle)
        }

        override fun onMetadata(key: String, value: String) {
            if (key.equals("StreamTitle", ignoreCase = true) && value.isNotBlank()) {
                update { copy(trackTitle = value) }
            }
        }

        override fun onError(message: String) = update {
            copy(error = message, event = PlaybackEvent.STOPPED)
        }
    }

    init {
        player.setCallback(callback)
        scope.launch {
            while (isActive) {
                current = current.copy(
                    positionMs = player.getPositionMs(),
                    durationMs = player.getDurationMs(),
                    seekable = runCatching { player.isSeekable() }.getOrDefault(false),
                )
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    override fun selectSource(source: Source) = update { copy(source = source) }

    override fun updateFilePath(path: String) = update { copy(filePath = path) }

    override fun updateStreamUrl(url: String) = update { copy(streamUrl = url) }

    override fun browseFile() {
        scope.launch { pickAudioFile()?.let { updateFilePath(it) } }
    }

    override fun playPause() {
        when (current.event) {
            PlaybackEvent.PLAYING -> player.pause()
            PlaybackEvent.PAUSED -> player.play()
            else -> startPlayback()
        }
    }

    override fun stop() = player.stop()

    override fun previewSeek(positionMs: Long?) = update { copy(seekPreviewMs = positionMs) }

    override fun commitSeek() {
        val target = current.displayPositionMs
        scope.launch { runCatching { player.seekToMs(target) } }
        previewSeek(null)
    }

    override fun setVolume(volume: Float) {
        update { copy(volume = volume) }
        runCatching { player.setVolume(volume.coerceIn(0f, 1f)) }
    }

    fun close() {
        player.clearCallback()
        player.close()
    }

    private fun startPlayback() {
        val snapshot = current
        update { copy(error = null) }
        scope.launch {
            val result = when (snapshot.source) {
                Source.File -> runCatching { player.playFileAsync(snapshot.filePath, loop = false) }
                Source.Stream -> runCatching { player.playUrlAsync(snapshot.streamUrl, loop = false) }
            }
            result.onFailure { update { copy(error = it.message) } }
        }
    }

    /** Update state on the Main dispatcher; safe to call from any thread. */
    private fun update(reducer: PlayerUiState.() -> PlayerUiState) {
        scope.launch { current = current.reducer() }
    }

    private companion object {
        const val POLL_INTERVAL_MS = 200L
    }
}

/** Remembers a [PlayerController] bound to the composition and releases it on dispose. */
@Composable
fun rememberPlayerController(): PlayerController {
    val scope = rememberCoroutineScope()
    val controller = remember { PlayerController(scope) }
    DisposableEffect(controller) {
        onDispose { controller.close() }
    }
    return controller
}
