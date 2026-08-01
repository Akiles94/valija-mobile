package dev.valija.poc.domain

/**
 * This module's single error type — the Kotlin analogue of valija's per-context
 * `errors.ts`, and a well-known per-module file (CLAUDE.md's standing exception).
 *
 * It extends [RuntimeException] so an infra adapter deep inside a C interop call can throw it,
 * while application-layer use cases still hand callers a `VaultResult` rather than throwing.
 */
class VaultError(
    val code: String,
    override val message: String,
) : RuntimeException(message)

fun vaultErr(code: String, message: String): VaultError = VaultError(code, message)

/** Codes this PoC can produce. Kept together so the whole failure surface is one screenful. */
object VaultErrorCodes {
    /** The database refused the derived key — a wrong passphrase, not a corrupt file. */
    const val WRONG_PASSPHRASE = "WRONG_PASSPHRASE"

    /** `meta.schema_version` is newer than this reader understands. Never migrate (M4 D-J). */
    const val SCHEMA_TOO_NEW = "SCHEMA_TOO_NEW"

    /** A `-wal`, `-shm` or `-journal` sidecar sits beside the file (`docs/vault-format.md` §11). */
    const val JOURNAL_SIDECAR_PRESENT = "JOURNAL_SIDECAR_PRESENT"

    /** No project by that name. */
    const val PROJECT_NOT_FOUND = "PROJECT_NOT_FOUND"

    /** Argon2id produced a key that is not the fixture's published one. */
    const val KEY_MISMATCH = "KEY_MISMATCH"

    /** The plaintext header could not be parsed, or carries an unsupported schema. */
    const val INVALID_HEADER = "INVALID_HEADER"
}
