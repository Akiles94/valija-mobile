package dev.valija.poc.infra.files

import dev.valija.poc.domain.VaultErrorCodes
import dev.valija.poc.domain.vaultErr

/**
 * Copy the bundled vault out of the app bundle into a sandbox directory, and hand back the
 * copy's path.
 *
 * **The bundled resource is never opened in place — not read-only, not once.** That makes
 * `docs/vault-format.md` §11's "a reader never mutates the file" structural rather than
 * promised: there is no code path in this app that can reach the original bytes with a database
 * handle. It costs one copy of a 61 KB file.
 */
object FixtureSnapshot {

    private const val DIRECTORY = "golden-vault-snapshot"
    private val SIDECAR_SUFFIXES = listOf("-wal", "-shm", "-journal")

    /**
     * @param cacheDirectory the platform's own cache directory, passed in from the entry point
     *   rather than discovered here — the app never goes looking for a place to write.
     */
    fun materialise(cacheDirectory: String, vaultDb: ByteArray, vaultJson: ByteArray): Snapshot {
        val directory = "$cacheDirectory/$DIRECTORY"
        PlatformFiles.ensureDirectory(directory)

        val databasePath = "$directory/vault.db"
        PlatformFiles.write(databasePath, vaultDb)
        PlatformFiles.write("$directory/vault.json", vaultJson)

        // A sidecar beside the file means someone left a WAL behind; refuse rather than open it
        // and risk reading a half-checkpointed view. It can never happen from a fresh bundle
        // copy — the check exists so the refusal is real, reviewable code rather than a claim.
        refuseIfJournalSidecarPresent(databasePath)

        return Snapshot(databasePath = databasePath, directory = directory)
    }

    fun refuseIfJournalSidecarPresent(databasePath: String) {
        val found = SIDECAR_SUFFIXES.filter { PlatformFiles.exists(databasePath + it) }
        if (found.isNotEmpty()) {
            throw vaultErr(
                VaultErrorCodes.JOURNAL_SIDECAR_PRESENT,
                "Refusing to open: journal sidecar(s) present (${found.joinToString()}). " +
                    "A reader never folds a WAL — that would mutate a file the owner is syncing.",
            )
        }
    }

    data class Snapshot(val databasePath: String, val directory: String)
}
