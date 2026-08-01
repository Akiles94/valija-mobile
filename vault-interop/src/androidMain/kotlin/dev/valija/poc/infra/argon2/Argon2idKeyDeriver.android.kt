package dev.valija.poc.infra.argon2

import dev.valija.poc.application.ports.KeyDeriver
import dev.valija.poc.infra.jni.ValijaNative

/** Android's half of the seam: JNI into the vendored `phc-winner-argon2`. */
actual class Argon2idKeyDeriver actual constructor() : KeyDeriver {

    override fun deriveKeyHex(
        passphrase: String,
        salt: ByteArray,
        memoryKiB: Int,
        iterations: Int,
        parallelism: Int,
    ): String {
        val passphraseBytes = passphrase.encodeToByteArray()
        val raw = ValijaNative.nativeArgon2idRaw(
            passphrase = passphraseBytes,
            salt = salt,
            memoryKiB = memoryKiB,
            iterations = iterations,
            parallelism = parallelism,
            hashLength = KEY_LENGTH_BYTES,
        )
        val hex = raw.toKeyHex()
        // The hex string is what the caller needs; the raw key does not outlive this call.
        raw.fill(0)
        passphraseBytes.fill(0)
        return hex
    }

    private companion object {
        /** 32 bytes — a 256-bit raw key, 64 hex characters (`docs/vault-format.md` §4). */
        const val KEY_LENGTH_BYTES = 32
    }
}
