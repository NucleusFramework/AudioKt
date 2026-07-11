package dev.nucleusframework.rodio

/**
 * Playback state reported via [PlaybackCallback.onEvent].
 *
 * Ordinals are pinned (0..3) because they cross the JNI boundary as a raw
 * `jint` — see `PlaybackEvent` in `nucleus_rodio` (`lib.rs`). Do not reorder.
 */
enum class PlaybackEvent {
    CONNECTING,
    PLAYING,
    PAUSED,
    STOPPED,
    ;

    internal companion object {
        fun fromInt(value: Int): PlaybackEvent = entries.getOrElse(value) { STOPPED }
    }
}