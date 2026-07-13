package sample.app.util

import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.openFilePicker
import io.github.vinceglb.filekit.path

private val AUDIO_EXTENSIONS = setOf("mp3", "wav", "flac", "ogg", "oga", "m4a", "aac", "opus")

/** Opens FileKit's native Open dialog filtered to audio files; returns the path or null. */
suspend fun pickAudioFile(): String? =
    FileKit.openFilePicker(type = FileKitType.File(AUDIO_EXTENSIONS))?.path
