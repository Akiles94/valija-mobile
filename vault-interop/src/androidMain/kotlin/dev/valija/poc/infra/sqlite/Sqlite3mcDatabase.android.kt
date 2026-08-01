package dev.valija.poc.infra.sqlite

import dev.valija.poc.infra.jni.ValijaNative

/**
 * Android's half of the seam: JNI into the vendored amalgamation.
 *
 * The `sqlite3*` lives as an opaque handle and never leaves this class. Statement lifetime is
 * entirely inside C — [ValijaNative.nativeSelectAll] prepares, binds, steps and finalises in a
 * single call — so no Kotlin-side early return can leak one.
 */
actual class Sqlite3mcDatabase actual constructor(path: String, keyHex: String) {

    private var handle: Long = ValijaNative.nativeOpen(path, keyHex)

    actual fun selectAll(sql: String, args: List<String>): List<List<String?>> {
        check(handle != 0L) { "This vault database is already closed." }
        return ValijaNative.nativeSelectAll(handle, sql, args.toTypedArray())
            .map { row -> row.toList() }
    }

    actual fun close() {
        if (handle != 0L) {
            ValijaNative.nativeClose(handle)
            handle = 0L
        }
    }
}
