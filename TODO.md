# TODO / Known issues

Audited inventory of defects and gaps in `nucleus_rodio`, ordered by severity.
Static review only — none of the items below were reproduced on a running
build yet, so each one carries a "confirm" step where the diagnosis is not
purely structural.

Severity key: **P0** blocks correctness in normal use · **P1** robustness or
tuning defect · **P2** polish / hygiene.

---

## P0 — correctness

### 1. `OutputStream` stored in a `thread_local`, `Sink` in a global map

`rodio/src/main/native/src/state.rs:68` keeps the `OutputStream` in a
`thread_local!` `HashMap` while the matching `PlayerState` (holding the `Sink`)
lives in the process-wide `PLAYERS: OnceLock<Mutex<HashMap<..>>>`. The two
halves of one player therefore have different lifetimes, tied to different
threads.

Two failure modes, both reachable through the public API as documented:

- **Silent audio loss.** `register()` inserts the stream into the *creating*
  thread's map. If that thread exits, the `OutputStream` is dropped while the
  `Sink` stays alive in the global registry — every subsequent call succeeds and
  plays to nothing. Constructing a `RodioPlayer` inside
  `withContext(Dispatchers.IO)` is the obvious trap: IO threads are reclaimed
  after ~60 s idle.
- **Output-device leak.** `unregister(id)`
  (`state.rs:113`) removes from the *calling* thread's `STREAMS`. Calling
  `close()` from the UI thread for a player created on `Dispatchers.Default`
  drops the `Sink` but never the `OutputStream`, leaking the device until the
  creating thread dies. The API actively encourages this split:
  `playUrlAsync`/`playRadioAsync`/`playFileAsync` run on `Dispatchers.Default`
  while `close()` is typically called from the UI thread.

The `thread_local` exists because `cpal::Stream` (inside rodio's
`OutputStream`) is `!Send`. That is a real constraint, but the fix is to own
the streams on a **dedicated audio thread** driven by a command channel — every
create/destroy is a message to that thread, so `!Send` is satisfied by
construction and lifetimes stop depending on which caller thread happened to
run. Working around `!Send` with thread-local storage moves the problem instead
of solving it.

- [ ] **P0** Introduce an audio owner thread in `state.rs`: spawn it lazily,
      have it own `HashMap<u64, OutputStream>`, and route `register` /
      `unregister` through a `std::sync::mpsc` command channel with a reply
      channel for the generated id. Drop the `thread_local!`.
- [ ] **P0** Confirm both modes first (cheap): create a player on a short-lived
      `thread::spawn`, let it exit, then `playFile` → expect silence; and create
      on `Dispatchers.Default`, `close()` from main, then check the OS device
      list / open handle count for the leaked stream.
- [ ] **P0** Regression test: create + close a player from two *different*
      threads and assert the native registry and the stream map are both empty
      afterwards (needs a debug-only `nativeDebugPlayerCount` export).

---

## P1 — robustness and tuning

### 2. `panic = "abort"` takes the whole application down

`rodio/src/main/native/Cargo.toml` sets `panic = "abort"` in **both** the
`release` and `dev` profiles. Any panic below the JNI boundary — most
plausibly symphonia on a malformed or truncated audio file — aborts the JVM
process. For a library embedded in a Compose Desktop app that is the wrong
trade: a bad input file must surface as a catchable `RodioException`, not kill
the host app.

- [ ] **P1** Switch both profiles to `panic = "unwind"`.
- [ ] **P1** Wrap every `extern "system"` export body in
      `std::panic::catch_unwind` and translate `Err` into
      `RodioError::Internal` → `throw()`. Note the existing `throw()` contract
      (`lib.rs:66`): the export must still return a safe default value.
- [ ] **P1** Install a panic hook that routes the payload to `notify_error` when
      the panic happens on a rodio-internal thread rather than inside an export.

### 3. `opt-level = "z"` on the decode hot path

`Cargo.toml`'s release profile optimises for **size** (`opt-level = "z"`).
Audio decoding (MP3/AAC/FLAC via symphonia) and resampling are the hot path;
`-Oz` is likely to cost real CPU and is a plausible contributor to underruns
that would otherwise be blamed on `bufferSizeFrames`.

- [ ] **P1** Set `opt-level = 3`, keep `lto = "fat"` / `codegen-units = 1` /
      `strip = "debuginfo"`. Record the before/after `.so` size and CPU usage
      while decoding a long FLAC so the trade is documented, not guessed.

### 4. `RodioPlayer.handle` / `closed` are not synchronised

`rodio/src/main/kotlin/.../RodioPlayer.kt:47-56` — `requireHandle()` reads
`closed` then `handle` with no `@Volatile` and no lock, while `close()` writes
them from a potentially different thread. A `close()` racing a `playUrl()`
passes the `check(!closed)` and then calls into a destroyed handle. Today that
degrades to `RodioException("player N not found")` because the native registry
is a `Mutex<HashMap>` lookup — the safety is incidental, not designed.

- [ ] **P1** Mark `closed` `@Volatile`, make `handle` a `val`, and serialise
      `close()` under a private lock so it is idempotent under concurrency.
- [ ] **P1** Implement `AutoCloseable` so callers get `use { }`, and add a
      `java.lang.ref.Cleaner` as a backstop for a leaked player (the native
      output stream is a scarce OS resource; today forgetting `close()` leaks it
      for the process lifetime).

### 5. `notify_error` / `notify_metadata` never clear pending exceptions

`lib.rs:84-107` — `notify_event` correctly does `exception_check` /
`exception_clear` after `call_method` (`lib.rs:79-81`), but `notify_error` and
`notify_metadata` do not. An exception thrown from a user's `onError` or
`onMetadata` implementation stays pending on that thread and surfaces at an
arbitrary later JNI call, with a misleading stack.

- [ ] **P1** Factor the check/clear into a helper and use it at all three call
      sites.

### 6. `attach_current_thread_permanently` is never undone

`lib.rs:72,86,98` attach rodio's decoder/stream threads permanently and never
detach. If rodio spawns a fresh decoder thread per track, JVM thread
attachments accumulate for the process lifetime.

- [ ] **P1** Confirm whether rodio reuses one decoder thread or spawns per
      source. If it spawns, switch to a scoped attach (`attach_current_thread`,
      detached on guard drop) — or keep the permanent attach only for the
      long-lived audio thread introduced by item 1.

---

## P2 — API surface

### 7. Callback interface instead of a Flow

`PlaybackCallback` forces every consumer to marshal to their own dispatcher,
and the docs correctly warn that it fires on rodio's internal threads. For the
primary consumer — a Compose Desktop app — a `StateFlow<PlaybackState>` plus a
`Flow<Metadata>` would be the idiomatic surface, with the callback interface
kept as the lower-level escape hatch.

- [ ] **P2** Expose `val state: StateFlow<PlaybackState>` and
      `val metadata: Flow<Pair<String, String>>` on `RodioPlayer`, backed
      internally by the existing raw callback.

### 8. No output-device control

`SDL`-style device enumeration is absent: no way to list output devices, pick
one, or react to a device change (unplugging headphones is currently
unobservable and untested). `OutputStreamBuilder::from_default_device()` is the
only path (`state.rs:41`).

- [ ] **P2** Add `nativeListOutputDevices` / device selection at construction
      time, and surface a `PlaybackEvent.DEVICE_CHANGED` (or an error) when the
      default device disappears mid-playback.

### 9. Missing playback features

- [ ] **P2** No queue/playlist API — the rodio `Sink` has a queue but only
      `clear()` is exposed.
- [ ] **P2** No fade-in/out or crossfade helpers.

---

## P2 — build and hygiene

### 10. `onlyIf { !checkFile.exists() }` contradicts the declared inputs

`rodio/build.gradle.kts:43-79` — the three `buildNative*` tasks declare
`inputs.dir("src/main/native/src")` **and** an `onlyIf` that skips when the
output binary already exists. Editing `lib.rs` therefore marks the task
out-of-date and then skips it, silently shipping a stale binary. The existence
guard makes the input tracking dead code.

- [ ] **P2** Drop the `!checkFile.exists()` clause and keep only the OS-family
      guard; Gradle's up-to-date check already handles the "nothing changed"
      case. Verify by touching `lib.rs` and confirming a rebuild.

### 11. CI never runs the tests

`.github/workflows/pre-merge.yaml:44` runs `:rodio:compileKotlin` only. Neither
`RodioReachabilityMetadataTest` (which guards the GraalVM metadata against
drift — the exact class of bug fixed in `fe179ff`) nor `RodioBridgeSmokeTest`
executes on CI.

- [ ] **P2** Run `./gradlew :rodio:test` in pre-merge.
- [ ] **P2** Add Detekt + KtLint to match the Nucleus convention, and wire them
      into pre-merge.

### 12. Naming is inconsistent across the project

The repository is `AudioKt`, `settings.gradle.kts` declares
`rootProject.name = "RodioKt"`, the artifact is `dev.nucleusframework:nucleus.rodio`,
and the POM `url`/`scm` in `rodio/build.gradle.kts` point at
`github.com/NucleusFramework/RodioKt` — a URL that does not match the actual
remote.

- [ ] **P2** Pick one name. At minimum fix the POM `url`/`scm` so the published
      metadata resolves.

### 13. `RodioHttp.setAllowInvalidCerts` is a process-wide TLS override

`RodioHttp.kt:11` is documented as debug-only, but nothing enforces it and it
applies to every request in the process. Nucleus already ships `native-ssl` for
OS trust-store integration; the reqwest side uses
`rustls-tls-native-roots` + `rustls-tls-webpki-roots`, so the relationship
between the two should be documented rather than left implicit.

- [ ] **P2** Gate `setAllowInvalidCerts` behind an explicit
      `@RodioDangerousApi` opt-in annotation, and document how the rodio trust
      roots relate to `native-ssl`.
