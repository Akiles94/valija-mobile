package dev.valija.poc.infra.jni

/**
 * The whole JNI surface of this PoC, in one place.
 *
 * A binding is neither a port nor a use case, so it gets its own kind-named folder rather than
 * being dropped next to the adapters it serves (CLAUDE.md). Keeping every `external` here means
 * the C symbol names have exactly one Kotlin counterpart to stay in sync with, and the adapters
 * above stay readable Kotlin.
 *
 * Symbols map to `androidMain/cpp/valija_native.c` as
 * `Java_dev_valija_poc_infra_jni_ValijaNative_<name>`.
 */
internal object ValijaNative {

    init {
        System.loadLibrary("valija_native")
    }

    /** @return an opaque `sqlite3*`, already keyed and verified. Throws `VaultError` on failure. */
    external fun nativeOpen(path: String, keyHex: String): Long

    external fun nativeSelectAll(
        handle: Long,
        sql: String,
        args: Array<String>,
    ): Array<Array<String?>>

    external fun nativeClose(handle: Long)

    /** Passphrase arrives as UTF-8 bytes so the encoding is decided in Kotlin, not in C. */
    external fun nativeArgon2idRaw(
        passphrase: ByteArray,
        salt: ByteArray,
        memoryKiB: Int,
        iterations: Int,
        parallelism: Int,
        hashLength: Int,
    ): ByteArray
}
