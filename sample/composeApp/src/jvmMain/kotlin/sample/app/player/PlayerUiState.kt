package sample.app.player

import dev.nucleusframework.rodio.PlaybackEvent

/** Which input the user is loading audio from. */
enum class Source(val label: String) {
    File("File"),
    Stream("Stream"),
}

/** Immutable snapshot of everything the player UI renders. */
data class PlayerUiState(
    val source: Source = Source.File,
    val filePath: String = "",
    val streamUrl: String = "https://broadcast.adpronet.com/radio/6060/radio.mp3",
    val event: PlaybackEvent = PlaybackEvent.STOPPED,
    val trackTitle: String? = null,
    val error: String? = null,
    val positionMs: Long = 0L,
    val durationMs: Long? = null,
    val seekPreviewMs: Long? = null,
    val seekable: Boolean = false,
    val volume: Float = 1f,
) {
    val hasSource: Boolean
        get() = when (source) {
            Source.File -> filePath.isNotBlank()
            Source.Stream -> streamUrl.isNotBlank()
        }

    /** Position to display: the in-flight drag value while seeking, else the real position. */
    val displayPositionMs: Long
        get() = seekPreviewMs ?: positionMs
}

/** Intents the UI can dispatch. Implemented by [PlayerController]. */
interface PlayerActions {
    fun selectSource(source: Source)

    fun updateFilePath(path: String)

    fun updateStreamUrl(url: String)

    fun browseFile()

    fun playPause()

    fun stop()

    fun previewSeek(positionMs: Long?)

    fun commitSeek()

    fun setVolume(volume: Float)
}
