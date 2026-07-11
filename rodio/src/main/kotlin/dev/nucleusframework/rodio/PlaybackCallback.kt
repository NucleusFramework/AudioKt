package dev.nucleusframework.rodio

/**
 * Receives playback notifications from the native rodio backend.
 *
 * Implementations are invoked from rodio's internal decoder/audio thread
 * (and, for ICY metadata, the stream read thread) — they must be thread-safe
 * and must not block. Marshal UI work to the appropriate dispatcher.
 */
interface PlaybackCallback {
    fun onEvent(event: PlaybackEvent)

    fun onMetadata(key: String, value: String)

    fun onError(message: String)
}