package dev.valija.poc.application.ports

import dev.valija.poc.domain.entities.ContextItem
import dev.valija.poc.domain.entities.Project

/**
 * The one port between the domain and any database.
 *
 * Everything below this interface — SQLite, the vendored SQLite3MultipleCiphers amalgamation,
 * JNI, cinterop — is invisible above it. That boundary is what lets the entire pack algorithm
 * be tested on the JVM in seconds, with the device runs left to prove only the adapter.
 */
interface VaultReader {
    /** `SELECT value FROM meta WHERE key = 'schema_version'`. Refused if not the supported one. */
    fun readSchemaVersion(): String

    fun findProjectByName(name: String): Project?

    /**
     * Non-archived items for a project, **newest first**.
     *
     * The name says `Active` rather than `All` so no caller can forget that `archived = 0` is
     * applied in the query itself, never as a later filter (`docs/vault-format.md` §7).
     */
    fun findActiveItems(projectId: String): List<ContextItem>

    fun close()
}
