package dev.valija.poc.infra.sqlite

import dev.valija.poc.domain.VaultErrorCodes
import dev.valija.poc.domain.vaultErr
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.CPointerVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.cstr
import kotlinx.cinterop.getPointer
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.toKString
import kotlinx.cinterop.value
import sqlite3mc.SQLITE_NULL
import sqlite3mc.SQLITE_OK
import sqlite3mc.SQLITE_OPEN_READONLY
import sqlite3mc.SQLITE_ROW
import sqlite3mc.sqlite3
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
import sqlite3mc.sqlite3_stmt

/**
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
                    // The text is allocated in this memScoped arena, which outlives every step
                    // and the finalize below, so SQLITE_STATIC (a null destructor) is safe and
                    // avoids needing the SQLITE_TRANSIENT macro, which cinterop does not expose.
                    sqlite3_bind_text(stmt, index + 1, arg.cstr.getPointer(this), -1, null)
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
