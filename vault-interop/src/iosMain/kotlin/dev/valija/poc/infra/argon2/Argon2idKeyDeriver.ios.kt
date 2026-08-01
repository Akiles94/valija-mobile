package dev.valija.poc.infra.argon2

import argon2.argon2_error_message
import argon2.argon2id_hash_raw
import dev.valija.poc.application.ports.KeyDeriver
import dev.valija.poc.domain.VaultErrorCodes
import dev.valija.poc.domain.vaultErr
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.toKString
import kotlinx.cinterop.usePinned

/** iOS's half of the seam: cinterop into the vendored `phc-winner-argon2`. */
@OptIn(ExperimentalForeignApi::class)
actual class Argon2idKeyDeriver actual constructor() : KeyDeriver {

    override fun deriveKeyHex(
        passphrase: String,
        salt: ByteArray,
        memoryKiB: Int,
        iterations: Int,
        parallelism: Int,
    ): String {
        val passphraseBytes = passphrase.encodeToByteArray()
        val out = ByteArray(KEY_LENGTH_BYTES)

        val rc = passphraseBytes.usePinned { pinnedPassphrase ->
            salt.usePinned { pinnedSalt ->
                out.usePinned { pinnedOut ->
                    argon2id_hash_raw(
                        iterations.convert(),
                        memoryKiB.convert(),
                        parallelism.convert(),
                        pinnedPassphrase.addressOf(0),
                        passphraseBytes.size.convert(),
                        pinnedSalt.addressOf(0),
                        salt.size.convert(),
                        pinnedOut.addressOf(0),
                        out.size.convert(),
                    )
                }
            }
        }

        passphraseBytes.fill(0)

        // ARGON2_OK is 0. Compared as a plain int rather than against the generated enum
        // constant, whose Kotlin type depends on how cinterop chose to map the C enum.
        if (rc != 0) {
            out.fill(0)
            throw vaultErr(
                VaultErrorCodes.KEY_MISMATCH,
                argon2_error_message(rc)?.toKString() ?: "Argon2id failed with code $rc",
            )
        }

        val hex = out.toKeyHex()
        out.fill(0)
        return hex
    }

    private companion object {
        const val KEY_LENGTH_BYTES = 32
    }
}
