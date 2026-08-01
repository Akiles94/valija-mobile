package dev.valija.poc.application.ports

/**
 * Argon2id key derivation, behind a port so the domain never sees the vendored C.
 *
 * Parameters are passed in from the vault's own header (`docs/vault-format.md` §4) rather than
 * defaulted here — a reader that assumes 64 MiB / t=3 / p=1 would derive the wrong key for any
 * vault written with different settings, and would do it silently.
 */
interface KeyDeriver {
    /**
     * @return the derived 32-byte key as 64 lowercase hex characters — the form
     *   `PRAGMA key = "x'…'"` expects (`docs/vault-format.md` §5).
     */
    fun deriveKeyHex(
        passphrase: String,
        salt: ByteArray,
        memoryKiB: Int,
        iterations: Int,
        parallelism: Int,
    ): String
}
