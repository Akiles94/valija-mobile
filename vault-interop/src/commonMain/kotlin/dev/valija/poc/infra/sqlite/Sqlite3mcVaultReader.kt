package dev.valija.poc.infra.sqlite

import dev.valija.poc.application.ports.VaultReader
import dev.valija.poc.domain.VaultErrorCodes
import dev.valija.poc.domain.entities.ContextItem
import dev.valija.poc.domain.entities.Project
import dev.valija.poc.domain.values.parseItemType
import dev.valija.poc.domain.vaultErr
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonPrimitive

/**
 * The [VaultReader] adapter over the vendored amalgamation — **shared by both platforms**.
 *
 * The three statements below are the whole read surface of this PoC, quoted from
 * `docs/vault-format.md` §6–§7. They live here, in common code, rather than in each platform's
 * `actual`, so Android and iOS provably issue the same SQL.
 */
class Sqlite3mcVaultReader(private val db: Sqlite3mcDatabase) : VaultReader {

    override fun readSchemaVersion(): String {
        val rows = db.selectAll(SQL_SCHEMA_VERSION, emptyList())
        return rows.firstOrNull()?.firstOrNull()
            ?: throw vaultErr(
                VaultErrorCodes.INVALID_HEADER,
                "meta has no schema_version row; this is not a valija vault.",
            )
    }

    override fun findProjectByName(name: String): Project? {
        val row = db.selectAll(SQL_PROJECT_BY_NAME, listOf(name)).firstOrNull() ?: return null
        return Project(
            id = row.required(0, "projects.id"),
            name = row.required(1, "projects.name"),
            createdAt = row.required(2, "projects.created_at"),
            updatedAt = row.required(3, "projects.updated_at"),
        )
    }

    override fun findActiveItems(projectId: String): List<ContextItem> =
        db.selectAll(SQL_ACTIVE_ITEMS, listOf(projectId)).map { row ->
            val rawType = row.required(2, "context_items.type")
            ContextItem(
                id = row.required(0, "context_items.id"),
                projectId = row.required(1, "context_items.project_id"),
                type = parseItemType(rawType) ?: throw vaultErr(
                    VaultErrorCodes.SCHEMA_TOO_NEW,
                    "Unknown item type '$rawType'. This vault was written by a newer valija; " +
                        "update the app — a reader never guesses and never migrates.",
                ),
                content = row.required(3, "context_items.content"),
                tags = parseTags(row.getOrNull(4)),
                // 0/1 integers, not SQLite booleans — SQLite has none (§6).
                pinned = row.getOrNull(5) == "1",
                archived = row.getOrNull(6) == "1",
                createdAt = row.required(7, "context_items.created_at"),
                updatedAt = row.required(8, "context_items.updated_at"),
            )
        }

    override fun close() = db.close()

    /** `tags` is a JSON array stored as text, e.g. `'["security","storage"]'` (§6). */
    private fun parseTags(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            (json.parseToJsonElement(raw) as JsonArray).map { it.jsonPrimitive.content }
        } catch (t: Throwable) {
            throw vaultErr(
                VaultErrorCodes.INVALID_HEADER,
                "context_items.tags is not a JSON array of strings: ${t.message}",
            )
        }
    }

    private fun List<String?>.required(index: Int, column: String): String =
        getOrNull(index) ?: throw vaultErr(
            VaultErrorCodes.INVALID_HEADER,
            "$column is NULL, but the schema declares it NOT NULL.",
        )

    private companion object {
        val json = Json { ignoreUnknownKeys = true }

        const val SQL_SCHEMA_VERSION =
            "SELECT value FROM meta WHERE key = 'schema_version'"

        const val SQL_PROJECT_BY_NAME =
            "SELECT id, name, created_at, updated_at FROM projects WHERE name = ?"

        /**
         * `archived = 0` is applied **in this query**, never as a later filter — archived items
         * must never reach pack assembly at all (§7). `ORDER BY created_at DESC` has no
         * tie-break; the fixture gives every item a distinct timestamp.
         */
        const val SQL_ACTIVE_ITEMS =
            "SELECT id, project_id, type, content, tags, pinned, archived, created_at, updated_at " +
                "FROM context_items WHERE project_id = ? AND archived = 0 ORDER BY created_at DESC"
    }
}
