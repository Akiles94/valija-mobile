// Directory `use-cases/` names the kind, per valija's CLAUDE.md; the package drops the hyphen
// because Kotlin package identifiers cannot contain one.
package dev.valija.poc.application.usecases

import dev.valija.poc.domain.services.ConformanceVerdict
import dev.valija.poc.domain.services.compareRendered
import dev.valija.poc.domain.values.parseGoldenVaultManifest
import dev.valija.poc.domain.values.parseVaultHeader
import dev.valija.poc.domain.values.saltBytes
import dev.valija.poc.infra.argon2.Argon2idKeyDeriver
import dev.valija.poc.infra.files.FixtureSnapshot
import dev.valija.poc.infra.sqlite.Sqlite3mcDatabase
import dev.valija.poc.infra.sqlite.Sqlite3mcVaultReader
import dev.valija.poc.shared.VaultResult
import dev.valija.poc.shared.getOrThrow

data class ConformanceReport(
    val verdict: ConformanceVerdict,
    val renderedPack: String,
    val derivationMillis: Long,
    val expectationName: String,
)

/**
 * The one screen-level use case, and the file that fixes the order of operations.
 *
 * That order is the security contract of this PoC, so it is written to be read top to bottom:
 *
 *  1. snapshot before anything — the bundled resource is never opened
 *  2. refuse if a journal sidecar sits beside the copy
 *  3. parse the header — salt and KDF parameters come from the vault, never from a default
 *  4. derive the key (timed)
 *  5. assert the derived key equals the published one **before** opening, so a derivation bug
 *     reports itself instead of masquerading as a corrupt database
 *  6. open → cipher → key → verify, in that order
 *  7. read, assemble, render, byte-compare
 *  8. close in a `finally`; the key never leaves this function
 */
class RunGoldenVaultConformance(
    private val bundle: GoldenVaultBundle,
    private val cacheDirectory: String,
    private val nowMillis: () -> Long,
) {

    /** The five bundled resources, already read by the caller (resource IO is platform-side). */
    data class GoldenVaultBundle(
        val vaultDb: ByteArray,
        val vaultJson: String,
        val manifestJson: String,
        val expectedExport: ByteArray,
    ) {
        // ByteArray in a data class: identity equality is not meaningful here and nothing
        // compares bundles, so the generated methods are suppressed rather than left wrong.
        override fun equals(other: Any?) = this === other
        override fun hashCode() = vaultDb.contentHashCode()
    }

    fun run(): VaultResult<ConformanceReport> = runCatching { execute() }
        .fold(
            onSuccess = { VaultResult.Ok(it) },
            onFailure = { throwable ->
                val error = throwable as? dev.valija.poc.domain.VaultError
                    ?: dev.valija.poc.domain.vaultErr(
                        "UNEXPECTED",
                        throwable.message ?: throwable::class.simpleName ?: "unknown failure",
                    )
                VaultResult.Err(error)
            },
        )

    private fun execute(): ConformanceReport {
        val manifest = parseGoldenVaultManifest(manifestJson()).getOrThrow()
        val header = parseVaultHeader(bundle.vaultJson).getOrThrow()

        val snapshot = FixtureSnapshot.materialise(
            cacheDirectory = cacheDirectory,
            vaultDb = bundle.vaultDb,
            vaultJson = bundle.vaultJson.encodeToByteArray(),
        )

        val startedAt = nowMillis()
        val keyHex = Argon2idKeyDeriver().deriveKeyHex(
            passphrase = manifest.passphrase,
            salt = header.saltBytes(),
            memoryKiB = header.kdf.memoryKiB,
            iterations = header.kdf.iterations,
            parallelism = header.kdf.parallelism,
        )
        val derivationMillis = nowMillis() - startedAt

        if (keyHex != manifest.keyHex) {
            throw dev.valija.poc.domain.vaultErr(
                dev.valija.poc.domain.VaultErrorCodes.KEY_MISMATCH,
                "Argon2id produced a key that is not the fixture's published one. " +
                    "The KDF, not the database, is what is wrong.",
            )
        }

        val reader = Sqlite3mcVaultReader(Sqlite3mcDatabase(snapshot.databasePath, keyHex))
        try {
            val rendered = ReadContextPack(reader).execute(
                ReadContextPackInput(
                    projectName = PROJECT,
                    generatedAt = manifest.generatedAt,
                    budgetTokens = null,
                ),
            ).getOrThrow()

            val verdict = compareRendered(
                rendered.markdown.encodeToByteArray(),
                bundle.expectedExport,
            )
            return ConformanceReport(
                verdict = verdict,
                renderedPack = rendered.markdown,
                derivationMillis = derivationMillis,
                expectationName = EXPECTATION,
            )
        } finally {
            reader.close()
        }
    }

    private fun manifestJson() = bundle.manifestJson

    private companion object {
        const val PROJECT = "alpha"
        const val EXPECTATION = "expected-export.md"
    }
}
