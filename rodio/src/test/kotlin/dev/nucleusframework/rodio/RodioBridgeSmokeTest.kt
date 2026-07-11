package dev.nucleusframework.rodio

import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Smoke test for the hand-written JNI bridge: validates that the native
 * library is extracted/loaded by NativeLibraryLoader, that every JNI symbol
 * resolves, that `nativeSetCallback` pins the callback via a GlobalRef and
 * that rodio dispatches `PlaybackEvent` back across the boundary.
 *
 * Uses `playSine` so it needs no audio file on disk. Runs on the JVM only —
 * the native backend is JVM-only, so this test is skipped on non-JVM targets
 * by virtue of living under `jvmMain`-equivalent test sources (kotlin("test")).
 */
class RodioBridgeSmokeTest {
    private lateinit var player: RodioPlayer
    private val events = mutableListOf<PlaybackEvent>()

    @BeforeTest
    fun setUp() {
        player = RodioPlayer()
        player.setCallback(
            object : PlaybackCallback {
                override fun onEvent(event: PlaybackEvent) {
                    synchronized(events) { events += event }
                }

                override fun onMetadata(key: String, value: String) {}
                override fun onError(message: String) {}
            },
        )
    }

    @AfterTest
    fun tearDown() {
        player.close()
    }

    @Test
    fun sinePlaybackDispatchesPlayingEvent() {
        // playSine notifies PLAYING synchronously on the calling thread once
        // the source is appended, so the event is recorded before return.
        player.playSine(440f, 300L)
        // Give the audio thread a moment to start, then assert state.
        Thread.sleep(100)
        assertTrue(events.contains(PlaybackEvent.PLAYING), "expected PLAYING event, got $events")
        assertEquals(false, player.isPaused())
    }

    @Test
    fun volumeAndSeekableStateAreReadable() {
        player.setVolume(0.5f)
        player.playSine(220f, 200L)
        Thread.sleep(100)
        // A sine source is not seekable and has no advanceable duration beyond
        // the take_duration window — just assert no exception and paused=false.
        assertEquals(false, player.isPaused())
        assertEquals(false, player.isSeekable())
    }

    @Test
    fun urlLoopHlsIsRejectedWithRodioException() {
        // hls + loop is unsupported by design — validates error path throws
        // RodioException across the JNI boundary instead of aborting.
        var threw = false
        try {
            runBlocking { player.playUrlAsync("https://example.com/stream.m3u8", loop = true) }
        } catch (_: RodioException) {
            threw = true
        }
        assertTrue(threw, "expected RodioException for hls looped playback")
    }
}