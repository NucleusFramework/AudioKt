package dev.nucleusframework.rodio

import dev.nucleusframework.rodio.internal.NativePlaybackCallbackRaw
import dev.nucleusframework.rodio.internal.NativeRodioBridge
import dev.nucleusframework.rodio.internal.NativeRodioBridge.nativeClear
import dev.nucleusframework.rodio.internal.NativeRodioBridge.nativeClearCallback
import dev.nucleusframework.rodio.internal.NativeRodioBridge.nativeCreatePlayer
import dev.nucleusframework.rodio.internal.NativeRodioBridge.nativeCreatePlayerWithBufferSizeFrames
import dev.nucleusframework.rodio.internal.NativeRodioBridge.nativeDestroyPlayer
import dev.nucleusframework.rodio.internal.NativeRodioBridge.nativeGetDurationMs
import dev.nucleusframework.rodio.internal.NativeRodioBridge.nativeGetPositionMs
import dev.nucleusframework.rodio.internal.NativeRodioBridge.nativeIsEmpty
import dev.nucleusframework.rodio.internal.NativeRodioBridge.nativeIsPaused
import dev.nucleusframework.rodio.internal.NativeRodioBridge.nativeIsSeekable
import dev.nucleusframework.rodio.internal.NativeRodioBridge.nativePause
import dev.nucleusframework.rodio.internal.NativeRodioBridge.nativePlay
import dev.nucleusframework.rodio.internal.NativeRodioBridge.nativePlayFile
import dev.nucleusframework.rodio.internal.NativeRodioBridge.nativePlayRadio
import dev.nucleusframework.rodio.internal.NativeRodioBridge.nativePlaySine
import dev.nucleusframework.rodio.internal.NativeRodioBridge.nativePlayUrl
import dev.nucleusframework.rodio.internal.NativeRodioBridge.nativeSeekPositionMs
import dev.nucleusframework.rodio.internal.NativeRodioBridge.nativeSetCallback
import dev.nucleusframework.rodio.internal.NativeRodioBridge.nativeSetVolume
import dev.nucleusframework.rodio.internal.NativeRodioBridge.nativeStop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Audio player backed by the Rust `rodio` crate over hand-written JNI.
 *
 * Decoding and streaming run on rodio's internal threads; the public methods
 * here are thin synchronous wrappers. The `*Async` variants hop off the
 * caller thread onto [Dispatchers.Default] — useful for the blocking
 * network/file reads in `playUrl`/`playRadio`/`playFile`.
 *
 * Remember to call [close] to release the native output stream.
 */
class RodioPlayer(
    /** Optional output buffer size in frames. Larger values raise latency but reduce underruns. */
    bufferSizeFrames: Int? = null,
) {
    private companion object {
        val playbackDispatcher = Dispatchers.Default
    }

    private var handle: Long = when {
        bufferSizeFrames == null -> nativeCreatePlayer()
        bufferSizeFrames <= 0 -> throw IllegalArgumentException("bufferSizeFrames must be > 0")
        else -> nativeCreatePlayerWithBufferSizeFrames(bufferSizeFrames)
    }
    private var closed = false

    private fun requireHandle(): Long {
        check(!closed) { "RodioPlayer is closed" }
        return handle
    }

    fun playFile(path: String, loop: Boolean) {
        nativePlayFile(requireHandle(), path, loop)
    }

    suspend fun playFileAsync(path: String, loop: Boolean) {
        withContext(playbackDispatcher) { playFile(path, loop) }
    }

    fun playUrl(url: String, loop: Boolean = false, callback: PlaybackCallback? = null) {
        if (callback != null) setCallback(callback)
        nativePlayUrl(requireHandle(), url, loop)
    }

    suspend fun playUrlAsync(url: String, loop: Boolean = false, callback: PlaybackCallback? = null) {
        withContext(playbackDispatcher) { playUrl(url, loop, callback) }
    }

    fun playRadio(url: String, callback: PlaybackCallback? = null) {
        if (callback != null) setCallback(callback)
        nativePlayRadio(requireHandle(), url)
    }

    suspend fun playRadioAsync(url: String, callback: PlaybackCallback? = null) {
        withContext(playbackDispatcher) { playRadio(url, callback) }
    }

    fun playSine(frequencyHz: Float, durationMs: Long) {
        require(durationMs > 0) { "durationMs must be > 0" }
        nativePlaySine(requireHandle(), frequencyHz, durationMs)
    }

    fun play() {
        nativePlay(requireHandle())
    }

    fun pause() {
        nativePause(requireHandle())
    }

    fun stop() {
        nativeStop(requireHandle())
    }

    fun clear() {
        nativeClear(requireHandle())
    }

    fun getPositionMs(): Long = nativeGetPositionMs(requireHandle())

    fun getDurationMs(): Long? {
        val ms = nativeGetDurationMs(requireHandle())
        return if (ms < 0) null else ms
    }

    fun seekToMs(positionMs: Long) {
        require(positionMs >= 0) { "positionMs must be >= 0" }
        nativeSeekPositionMs(requireHandle(), positionMs)
    }

    fun isSeekable(): Boolean = nativeIsSeekable(requireHandle())

    fun setVolume(volume: Float) {
        nativeSetVolume(requireHandle(), volume)
    }

    fun setCallback(callback: PlaybackCallback?) {
        if (callback == null) {
            nativeClearCallback(requireHandle())
        } else {
            nativeSetCallback(requireHandle(), callback.toRaw())
        }
    }

    fun clearCallback() {
        nativeClearCallback(requireHandle())
    }

    fun isPaused(): Boolean = nativeIsPaused(requireHandle())

    fun isEmpty(): Boolean = nativeIsEmpty(requireHandle())

    fun close() {
        if (closed) return
        nativeDestroyPlayer(handle)
        closed = true
    }
}

/** Adapts the public [PlaybackCallback] (enum-typed) to the raw JNI interface. */
private fun PlaybackCallback.toRaw(): NativePlaybackCallbackRaw =
    object : NativePlaybackCallbackRaw {
        override fun onEvent(event: Int) {
            this@toRaw.onEvent(PlaybackEvent.fromInt(event))
        }

        override fun onMetadata(key: String, value: String) {
            this@toRaw.onMetadata(key, value)
        }

        override fun onError(message: String) {
            this@toRaw.onError(message)
        }
    }