package dev.valija.poc.infra.sqlite

/**
 * The one `expect`/`actual` seam over the vendored SQLite3MultipleCiphers amalgamation.
 *
 * Deliberately tiny — open, read rows as strings, close. Materialising a handful of rows as
 * strings costs nothing at this size and buys the thing that matters: **the SQL and the row
 * mapping are written once, in common code** ([Sqlite3mcVaultReader]), so the two platforms
 * cannot silently read differently. Everything platform-specific is confined to how the
 * bytes get in and out — JNI on Android, Kotlin/Native cinterop on iOS — which is exactly the
 * difference G3 exists to test.
 *
 * Read-only by construction: there is no `execute`, no `insert`, no way to run a pragma from
 * outside this class. The implementations open with `SQLITE_OPEN_READONLY` and never run
 * `journal_mode` or `wal_checkpoint` (`docs/vault-format.md` §11).
 */
expect class Sqlite3mcDatabase(path: String, keyHex: String) {

    /**
     * Run a query and return every row, every column as a nullable string.
     *
     * @param args bound as text via parameter placeholders — never string-interpolated.
     */
    fun selectAll(sql: String, args: List<String>): List<List<String?>>

    fun close()
}
