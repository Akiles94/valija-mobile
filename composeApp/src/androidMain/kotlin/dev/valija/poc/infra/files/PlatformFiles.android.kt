package dev.valija.poc.infra.files

import java.io.File

actual object PlatformFiles {
    actual fun write(path: String, bytes: ByteArray) {
        File(path).writeBytes(bytes)
    }

    actual fun exists(path: String): Boolean = File(path).exists()

    actual fun ensureDirectory(path: String) {
        File(path).mkdirs()
    }
}
