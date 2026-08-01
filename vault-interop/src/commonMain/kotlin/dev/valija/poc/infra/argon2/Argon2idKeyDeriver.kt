package dev.valija.poc.infra.argon2

import dev.valija.poc.application.ports.KeyDeriver

/**
 * Argon2id over the vendored `phc-winner-argon2` reference implementation — the same library
 * valija's desktop binds, so this is the identical algorithm, not a reimplementation.
 *
 * `ref.c` is the compiled backend on both platforms: `opt.c` is x86 SSE2-only, so on arm64 the
 * reference path is upstream's only path. The measured timing is therefore not an artificially
 * slow one.
 */
expect class Argon2idKeyDeriver() : KeyDeriver

/** Shared hex encoding, so the two platforms cannot disagree about the key's text form. */
internal fun ByteArray.toKeyHex(): String {
    val digits = "0123456789abcdef"
    val out = StringBuilder(size * 2)
    for (b in this) {
        // Mask the sign bit: a naive Byte.toString(16) yields "-1f" for high bytes and would
        // produce a key that is wrong in a way that looks exactly like a corrupt vault.
        val v = b.toInt() and 0xFF
        out.append(digits[v ushr 4])
        out.append(digits[v and 0x0F])
    }
    return out.toString()
}
