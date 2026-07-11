package dev.nucleusframework.rodio.internal

import dev.nucleusframework.core.runtime.NativeLibraryLoader

private const val LIBRARY_NAME = "nucleus_rodio"

/**
 * JNI-friendly callback interface: rodio calls back with a raw `Int` event
 * ordinal (see [dev.nucleusframework.rodio.PlaybackEvent]) and `String`
 * payloads. The native side pins a single instance per player via a JNI
 * `GlobalRef` and invokes it from non-JVM threads after attaching them.
 */
internal interface NativePlaybackCallbackRaw {
    fun onEvent(event: Int)

    fun onMetadata(key: String, value: String)

    fun onError(message: String)
}

/**
 * Direct JNI bridge over the Rust `rodio` crate. Symbols live in the native
 * library `nucleus_rodio` under `dev.nucleusframework.rodio.internal.NativeRodioBridge`.
 *
 * Duration getters encode `None` as `-1L` (see `nativeGetDurationMs`).
 */
@Suppress("TooManyFunctions")
internal object NativeRodioBridge {
    private val loaded = NativeLibraryLoader.load(LIBRARY_NAME, NativeRodioBridge::class.java)

    val isLoaded: Boolean get() = loaded

    @JvmStatic external fun nativeCreatePlayer(): Long

    @JvmStatic external fun nativeCreatePlayerWithBufferSizeFrames(bufferSizeFrames: Int): Long

    @JvmStatic external fun nativeDestroyPlayer(id: Long)

    @JvmStatic external fun nativeSetCallback(id: Long, callback: NativePlaybackCallbackRaw)

    @JvmStatic external fun nativeClearCallback(id: Long)

    @JvmStatic external fun nativePlayFile(id: Long, path: String, loop: Boolean)

    @JvmStatic external fun nativePlayUrl(id: Long, url: String, loop: Boolean)

    @JvmStatic external fun nativePlayRadio(id: Long, url: String)

    @JvmStatic external fun nativePlaySine(id: Long, frequencyHz: Float, durationMs: Long)

    @JvmStatic external fun nativePlay(id: Long)

    @JvmStatic external fun nativePause(id: Long)

    @JvmStatic external fun nativeStop(id: Long)

    @JvmStatic external fun nativeClear(id: Long)

    @JvmStatic external fun nativeIsPaused(id: Long): Boolean

    @JvmStatic external fun nativeIsEmpty(id: Long): Boolean

    @JvmStatic external fun nativeGetPositionMs(id: Long): Long

    @JvmStatic external fun nativeSeekPositionMs(id: Long, positionMs: Long)

    @JvmStatic external fun nativeGetDurationMs(id: Long): Long

    @JvmStatic external fun nativeIsSeekable(id: Long): Boolean

    @JvmStatic external fun nativeSetVolume(id: Long, volume: Float)

    @JvmStatic external fun nativeHttpSetAllowInvalidCerts(allow: Boolean)

    @JvmStatic external fun nativeHttpAddRootCertPem(pem: String)

    @JvmStatic external fun nativeHttpClearRootCerts()
}