package dev.valija.poc.domain.values

import dev.valija.poc.domain.VaultErrorCodes
import dev.valija.poc.domain.vaultErr
import dev.valija.poc.shared.VaultResult
import dev.valija.poc.shared.err
import dev.valija.poc.shared.ok
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** The only header schema version this reader understands (`docs/vault-format.md` §3). */
const val SUPPORTED_HEADER_SCHEMA_VERSION: Int = 1

/** The only database schema version this reader understands (`docs/vault-format.md` §6). */
const val SUPPORTED_SCHEMA_VERSION: String = "3"

@Serializable
data class KdfParams(
    val algorithm: String,
    @SerialName("memoryKiB") val memoryKiB: Int,
    val iterations: Int,
    val parallelism: Int,
)

/**
 * `vault.json` — the plaintext header sitting beside the encrypted database.
 *
 * The KDF parameters and salt come from **this file**, never from a compiled-in default
 * (`docs/vault-format.md` §4). A reader that hard-codes 64 MiB / t=3 / p=1 works against
 * today's fixture and silently fails against a vault written with different parameters.
 */
@Serializable
data class VaultHeader(
    val vaultId: String,
    val schemaVersion: Int,
    val kdf: KdfParams,
    val saltBase64: String,
    val createdAt: String,
)

private val headerJson = Json { ignoreUnknownKeys = true }

/**
 * Parse-don't-validate at the file boundary: a malformed header must be a message on screen,
 * never a crash, and never a partially-trusted object.
 */
fun parseVaultHeader(json: String): VaultResult<VaultHeader> {
    val header = try {
        headerJson.decodeFromString<VaultHeader>(json)
    } catch (t: Throwable) {
        return err(vaultErr(VaultErrorCodes.INVALID_HEADER, "vault.json is not valid: ${t.message}"))
    }

    if (header.schemaVersion != SUPPORTED_HEADER_SCHEMA_VERSION) {
        return err(
            vaultErr(
                VaultErrorCodes.SCHEMA_TOO_NEW,
                "vault.json schemaVersion is ${header.schemaVersion}; this reader understands " +
                    "$SUPPORTED_HEADER_SCHEMA_VERSION. Update the app — a reader never migrates.",
            ),
        )
    }
    if (header.kdf.algorithm != "argon2id") {
        return err(
            vaultErr(
                VaultErrorCodes.INVALID_HEADER,
                "Unsupported KDF '${header.kdf.algorithm}'; expected argon2id.",
            ),
        )
    }
    return ok(header)
}

/** The raw salt bytes Argon2id is fed. */
@OptIn(ExperimentalEncodingApi::class)
fun VaultHeader.saltBytes(): ByteArray = Base64.decode(saltBase64)
