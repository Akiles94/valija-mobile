package dev.valija.poc.infra.sqlite

import dev.valija.poc.domain.VaultErrorCodes
import dev.valija.poc.domain.vaultErr
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CFunction
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.CPointerVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.toCPointer
import kotlinx.cinterop.toKString
import kotlinx.cinterop.value
import cnames.structs.sqlite3
import cnames.structs.sqlite3_stmt
import sqlite3mc.SQLITE_NULL
import sqlite3mc.SQLITE_OK
import sqlite3mc.SQLITE_OPEN_READONLY
import sqlite3mc.SQLITE_ROW
import sqlite3mc.sqlite3_bind_text
import sqlite3mc.sqlite3_close
import sqlite3mc.sqlite3_column_count
import sqlite3mc.sqlite3_column_text
import sqlite3mc.sqlite3_column_type
import sqlite3mc.sqlite3_errmsg
import sqlite3mc.sqlite3_exec
import sqlite3mc.sqlite3_finalize
import sqlite3mc.sqlite3_open_v2
import sqlite3mc.sqlite3_prepare_v2
import sqlite3mc.sqlite3_step

/**
 * `sqlite3` and `sqlite3_stmt` import from `cnames.structs`, not from the `sqlite3mc` package
 * this file's other imports use. SQLite deliberately only forward-declares them
 * (`typedef struct sqlite3 sqlite3;`, no body) so callers can never see or depend on the layout —
 * and because cinterop never sees a full definition either, it cannot attribute the type to the
 * `.def` file's own package the way it does for functions and constants. It places every such
 * opaque struct in the shared `cnames.structs.<Name>` namespace instead, confirmed by the real
 * compiler error this file failed with before this comment existed.
 *
 * iOS's half of the seam: Kotlin/Native cinterop straight into the vendored amalgamation.
 *
 * Note the asymmetry with Android, which is the point of G3: there is **no hand-written C on
 * this side**. Android goes Kotlin → JNI (C we wrote) → sqlite3; iOS goes Kotlin → cinterop →
 * sqlite3. Two genuinely different mechanisms over one shared adapter, so proving one does not
 * prove the other.
 */
@OptIn(ExperimentalForeignApi::class)
actual class Sqlite3mcDatabase actual constructor(path: String, keyHex: String) {

    private var handle: CPointer<sqlite3>? = null

    private companion object {
        /**
         * The Kotlin/Native form of SQLite's `SQLITE_TRANSIENT` macro
         * (`(sqlite3_destructor_type)-1`). It is a cast expression in the C header, not a plain
         * constant, so cinterop cannot extract it — this reconstructs the same bit pattern by
         * hand, the documented approach for this exact, well-known SQLite/Kotlin-Native gap.
         */
        val SQLITE_TRANSIENT: CPointer<CFunction<(COpaquePointer?) -> Unit>>? =
            (-1L).toCPointer()
    }

    init {
        if (keyHex.length != 64) {
            throw vaultErr(VaultErrorCodes.KEY_MISMATCH, "Raw key must be exactly 64 hex characters.")
        }

        val opened = memScoped {
            val out = alloc<CPointerVar<sqlite3>>()
            val rc = sqlite3_open_v2(path, out.ptr, SQLITE_OPEN_READONLY, null)
            if (rc != SQLITE_OK) {
                val db = out.value
                val detail = db?.let { sqlite3_errmsg(it)?.toKString() } ?: "rc=$rc"
                if (db != null) sqlite3_close(db)
                throw vaultErr(VaultErrorCodes.INVALID_HEADER, "sqlite3_open_v2 failed: $detail")
            }
            out.value ?: throw vaultErr(VaultErrorCodes.INVALID_HEADER, "sqlite3_open_v2 gave no handle")
        }
        handle = opened

        // Order is fixed by docs/vault-format.md §5: cipher, then key, then a real read. Opening
        // never touches an encrypted page, so a wrong key surfaces only on that third step.
        exec(opened, "PRAGMA cipher='sqlcipher'", VaultErrorCodes.INVALID_HEADER)
        exec(opened, "PRAGMA key=\"x'$keyHex'\"", VaultErrorCodes.WRONG_PASSPHRASE)
        exec(
            opened,
            "SELECT count(*) FROM sqlite_master",
            VaultErrorCodes.WRONG_PASSPHRASE,
            "The database refused the derived key (SQLITE_NOTADB on the first read). " +
                "That means a wrong passphrase or wrong cipher parameters, not a corrupt file.",
        )
    }

    actual fun selectAll(sql: String, args: List<String>): List<List<String?>> {
        val db = handle ?: throw vaultErr(VaultErrorCodes.INVALID_HEADER, "This vault database is already closed.")
        val rows = mutableListOf<List<String?>>()

        memScoped {
            val stmtVar = alloc<CPointerVar<sqlite3_stmt>>()
            if (sqlite3_prepare_v2(db, sql, -1, stmtVar.ptr, null) != SQLITE_OK) {
                throw vaultErr(
                    VaultErrorCodes.INVALID_HEADER,
                    "sqlite3_prepare_v2 failed: ${sqlite3_errmsg(db)?.toKString()}",
                )
            }
            val stmt = stmtVar.value ?: throw vaultErr(VaultErrorCodes.INVALID_HEADER, "no statement")

            try {
                args.forEachIndexed { index, arg ->
                    // cinterop maps the C `const char*` parameter to Kotlin String directly
                    // (confirmed by the compiler: passing a raw CPointer here is a type
                    // mismatch, not just unidiomatic) -- so there is no buffer of ours to manage
                    // at all. What still matters is the *destructor* argument: SQLITE_TRANSIENT,
                    // not SQLITE_STATIC (null) -- passing null tells SQLite the pointer behind
                    // the string outlives this call, which is not guaranteed for a string
                    // marshalled at the FFI boundary. SQLITE_TRANSIENT makes SQLite copy the
                    // bytes before returning, which is correct regardless of how long the
                    // marshalled buffer actually lives.
                    sqlite3_bind_text(stmt, index + 1, arg, -1, SQLITE_TRANSIENT)
                }

                while (sqlite3_step(stmt) == SQLITE_ROW) {
                    val columns = sqlite3_column_count(stmt)
                    rows += (0 until columns).map { column ->
                        if (sqlite3_column_type(stmt, column) == SQLITE_NULL) {
                            null
                        } else {
                            sqlite3_column_text(stmt, column)?.reinterpret<ByteVar>()?.toKString()
                        }
                    }
                }
            } finally {
                sqlite3_finalize(stmt)
            }
        }
        return rows
    }

    actual fun close() {
        handle?.let { sqlite3_close(it) }
        handle = null
    }

    private fun exec(db: CPointer<sqlite3>, sql: String, code: String, message: String? = null) {
        if (sqlite3_exec(db, sql, null, null, null) != SQLITE_OK) {
            throw vaultErr(code, message ?: "Failed: ${sqlite3_errmsg(db)?.toKString()}")
        }
    }
}
