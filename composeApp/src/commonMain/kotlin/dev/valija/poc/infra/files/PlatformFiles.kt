package dev.valija.poc.infra.files

/**
 * The only filesystem the app touches, and the only `expect` in this module.
 *
 * Kept to three operations on purpose: the app copies one bundled resource into a sandbox
 * directory it was handed, and checks for journal sidecars. It cannot delete, cannot enumerate
 * the user's storage, and cannot reach outside the path it is given.
 */
expect object PlatformFiles {
    fun write(path: String, bytes: ByteArray)
    fun exists(path: String): Boolean
    fun ensureDirectory(path: String)
}
