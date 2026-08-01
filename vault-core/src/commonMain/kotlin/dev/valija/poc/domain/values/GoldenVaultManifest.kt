package dev.valija.poc.domain.values

import dev.valija.poc.domain.VaultErrorCodes
import dev.valija.poc.domain.vaultErr
import dev.valija.poc.shared.VaultResult
import dev.valija.poc.shared.err
import dev.valija.poc.shared.ok
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * `manifest.json` — the golden fixture's **published** parameter set.
 *
 * Every value here is public test data by design (see the fixture's own `README.md`): the
 * passphrase and the key it derives to are printed in the repository precisely so a second
 * implementation can check itself. Nothing in this class is a secret, and nothing in this class
 * describes a real vault.
 */
@Serializable
data class GoldenVaultManifest(
    val fixtureVersion: Int,
    val vaultId: String,
    val passphrase: String,
    val keyHex: String,
    val saltBase64: String,
    val kdf: KdfParams,
    val schemaVersion: Int,
    val generatedAt: String,
    val packBudgetTokens: Int,
)

private val manifestJson = Json { ignoreUnknownKeys = true }

fun parseGoldenVaultManifest(json: String): VaultResult<GoldenVaultManifest> {
    val manifest = try {
        manifestJson.decodeFromString<GoldenVaultManifest>(json)
    } catch (t: Throwable) {
        return err(
            vaultErr(VaultErrorCodes.INVALID_HEADER, "manifest.json is not valid: ${t.message}"),
        )
    }
    if (manifest.keyHex.length != 64) {
        return err(
            vaultErr(
                VaultErrorCodes.INVALID_HEADER,
                "manifest.keyHex must be 64 hex characters (a 32-byte raw key); " +
                    "got ${manifest.keyHex.length}.",
            ),
        )
    }
    return ok(manifest)
}
