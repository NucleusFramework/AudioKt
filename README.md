# RodioKt

RodioKt is a Kotlin/JVM audio playback library backed by the Rust [`rodio`](https://github.com/RustAudio/rodio) crate over hand-written JNI. It offers a compact API to play local files, HTTP(S)/HLS streams, internet radio (ICY), and to quickly generate a sine tone for testing.

Native binaries (Linux, macOS, Windows) ship inside the artifact and are extracted at runtime — no manual Rust build is required to consume the library.

## Highlights ✨
- Play local files, direct URLs, HLS streams, and internet radio (with ICY metadata).
- Callbacks to track state (`Connecting`, `Playing`, `Paused`, `Stopped`), receive metadata, and surface errors.
- Volume control, position/duration retrieval, and seeking when the source is seekable.
- `suspend` helpers so playback can start off the caller thread.
- Tone generator (`playSine`) to verify audio output quickly.

## Requirements ⚙️
- JDK 17 (the project targets Java 17 toolchains).
- To **consume** the published artifact: nothing else — the native libraries are bundled.
- To **build from source**: a Rust toolchain (rustup + cargo) for your host platform.

## Installation 📦
### From Maven Central
```kotlin
dependencies {
    implementation("dev.nucleusframework:nucleus.rodio:<version>")
}
```

### From source
- Build the library: `./gradlew :rodio:build` (compiles the Rust JNI bridge for the host platform)
- Publish to your local Maven for integration tests: `./gradlew :rodio:publishToMavenLocal`
- Run the Compose Desktop sample: `./gradlew :sample:composeApp:run`

## Quick start 🚀
```kotlin
import dev.nucleusframework.rodio.RodioPlayer
import dev.nucleusframework.rodio.PlaybackCallback
import dev.nucleusframework.rodio.PlaybackEvent

// Increase bufferSizeFrames to reduce underruns at the cost of a bit more latency.
val player = RodioPlayer(bufferSizeFrames = 2048)

val callback = object : PlaybackCallback {
    override fun onEvent(event: PlaybackEvent) {
        println("State: $event")
    }

    override fun onMetadata(key: String, value: String) {
        println("Metadata: $key = $value")
    }

    override fun onError(message: String) {
        println("Playback error: $message")
    }
}

player.setCallback(callback)
player.playUrl("https://example.com/stream.mp3", loop = false)

// ...
player.close()
```

## Core API 🧭
- Playback
  - `playFile(path: String, loop: Boolean)`
  - `playUrl(url: String, loop: Boolean = false, callback: PlaybackCallback? = null)` (auto-detects HLS)
  - `playRadio(url: String, callback: PlaybackCallback? = null)` (radio streams + ICY metadata)
  - `playSine(frequencyHz: Float, durationMs: Long)`
  - `suspend` variants: `playFileAsync`, `playUrlAsync`, `playRadioAsync` (run on `Dispatchers.Default`)
- Control
  - `play()`, `pause()`, `stop()`, `clear()` (clears the queue and resets the sink)
  - `setVolume(volume: Float)` (0.0 to 1.0 recommended)
  - `getPositionMs()`, `getDurationMs()` (returns `null` when the duration is unknown)
  - `seekToMs(positionMs: Long)` + `isSeekable()` to check if seeking is supported
  - `isPaused()`, `isEmpty()` to inspect current state
- Callbacks
  - `setCallback(callback: PlaybackCallback?)` / `clearCallback()`
  - `PlaybackCallback.onMetadata` is invoked for ICY metadata (radio) and some HTTP responses.

> ⚠️ Callbacks run on rodio's internal decoder/audio/stream threads. Keep them non-blocking and thread-safe; marshal UI work to your own dispatcher.

Always close the player when you are done: `player.close()`.

## Focused examples 🎯
### Play a local file
```kotlin
val player = RodioPlayer()
player.playFile("/path/to/my_file.ogg", loop = false)
```

### Play radio with metadata
```kotlin
player.setCallback(object : PlaybackCallback {
    override fun onEvent(event: PlaybackEvent) { println("State $event") }
    override fun onMetadata(key: String, value: String) { println("$key -> $value") }
    override fun onError(message: String) { println("Error: $message") }
})

player.playRadio("https://my.radio.example/stream")
```

### Non-blocking with coroutines
```kotlin
scope.launch {
    player.playUrlAsync("https://example.com/playlist.m3u8")
}
```

## HTTP/TLS customization 🛡️
Allow self-signed certificates or add extra roots if needed:
```kotlin
import dev.nucleusframework.rodio.RodioHttp

RodioHttp.setAllowInvalidCerts(true)              // Debug only
RodioHttp.addRootCertPem(customPemString)         // Add a trusted root
RodioHttp.clearRootCerts()                        // Restore defaults
```
These options are process-wide and apply to the next HTTP(S) request used by the player.

## Develop and test 🧪
- Build the library only: `./gradlew :rodio:build`
- Run the JVM tests: `./gradlew :rodio:test`
- Manual verification with the demo app: `./gradlew :sample:composeApp:run`

## Limitations and notes ⚠️
- Duration may be unknown for some live streams; `getDurationMs()` can return `null`.
- HLS is supported, but encrypted, byte-range, or init-segment-based streams are not.
- Looping (`loop = true`) is not available for HLS.
- One `RodioPlayer` per output device is recommended; reuse it and close it cleanly with `close()`.

## License 📄
Released under the [MIT License](LICENSE).
