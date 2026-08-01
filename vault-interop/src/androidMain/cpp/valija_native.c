/*
 * valija_native.c — the Android JNI bridge to the vendored SQLite3MultipleCiphers
 * amalgamation and the vendored phc-winner-argon2 reference implementation.
 *
 * Deliberately narrow and boring. It contains:
 *   - no PRAGMA journal_mode, no PRAGMA wal_checkpoint  (docs/vault-format.md §11)
 *   - no DDL, no INSERT, no UPDATE, no DELETE           (read-only, permanently: M4 D-J)
 *   - no logging of any kind                            (no key, no content, ever)
 *   - no network anything
 *
 * The whole query runs inside one JNI call rather than exposing prepare/step/column across the
 * boundary. That is a deliberate narrowing of the interface plan.md step 31 sketched: the
 * statement handle never escapes C, so it cannot be leaked by a Kotlin-side early return, and
 * a 9-row read costs 1 boundary crossing instead of ~100.
 */

#include <jni.h>
#include <stdlib.h>
#include <stdint.h>
#include <string.h>
#include <stdio.h>

#include "sqlite3.h"
#include "argon2.h"

#define VAULT_ERROR_CLASS "dev/valija/poc/domain/VaultError"

/* Raise a Kotlin VaultError(code, message). It extends RuntimeException but has no
 * single-String constructor, so ThrowNew is not usable — construct then Throw. */
static void throw_vault(JNIEnv *env, const char *code, const char *message) {
    jclass cls = (*env)->FindClass(env, VAULT_ERROR_CLASS);
    if (cls == NULL) {
        return; /* NoClassDefFoundError is already pending */
    }
    jmethodID ctor = (*env)->GetMethodID(
        env, cls, "<init>", "(Ljava/lang/String;Ljava/lang/String;)V");
    if (ctor == NULL) {
        return;
    }
    jstring j_code = (*env)->NewStringUTF(env, code);
    jstring j_message = (*env)->NewStringUTF(env, message);
    jobject error = (*env)->NewObject(env, cls, ctor, j_code, j_message);
    if (error != NULL) {
        (*env)->Throw(env, (jthrowable) error);
    }
}

static void throw_sqlite(JNIEnv *env, const char *code, sqlite3 *db, const char *context) {
    char buffer[512];
    const char *detail = (db != NULL) ? sqlite3_errmsg(db) : "no database handle";
    snprintf(buffer, sizeof(buffer), "%s: %s", context, detail);
    throw_vault(env, code, buffer);
}

/*
 * Open read-only, select the SQLCipher scheme, apply the raw key, then prove the key works.
 *
 * Order matters and is fixed by docs/vault-format.md §5: cipher first, key second, and a read
 * of sqlite_master third — sqlite3_open_v2 itself does not touch the encrypted pages, so a
 * wrong key surfaces only on the first real read, as SQLITE_NOTADB.
 */
JNIEXPORT jlong JNICALL
Java_dev_valija_poc_infra_jni_ValijaNative_nativeOpen(
    JNIEnv *env, jobject thiz, jstring j_path, jstring j_key_hex) {
    (void) thiz;

    const char *path = (*env)->GetStringUTFChars(env, j_path, NULL);
    const char *key_hex = (*env)->GetStringUTFChars(env, j_key_hex, NULL);
    if (path == NULL || key_hex == NULL) {
        return 0;
    }

    if (strlen(key_hex) != 64) {
        (*env)->ReleaseStringUTFChars(env, j_path, path);
        (*env)->ReleaseStringUTFChars(env, j_key_hex, key_hex);
        throw_vault(env, "KEY_MISMATCH", "Raw key must be exactly 64 hex characters.");
        return 0;
    }

    sqlite3 *db = NULL;
    int rc = sqlite3_open_v2(path, &db, SQLITE_OPEN_READONLY, NULL);
    if (rc != SQLITE_OK) {
        throw_sqlite(env, "INVALID_HEADER", db, "sqlite3_open_v2 failed");
        if (db != NULL) sqlite3_close(db);
        (*env)->ReleaseStringUTFChars(env, j_path, path);
        (*env)->ReleaseStringUTFChars(env, j_key_hex, key_hex);
        return 0;
    }

    rc = sqlite3_exec(db, "PRAGMA cipher='sqlcipher'", NULL, NULL, NULL);
    if (rc != SQLITE_OK) {
        throw_sqlite(env, "INVALID_HEADER", db, "PRAGMA cipher='sqlcipher' failed");
        sqlite3_close(db);
        (*env)->ReleaseStringUTFChars(env, j_path, path);
        (*env)->ReleaseStringUTFChars(env, j_key_hex, key_hex);
        return 0;
    }

    /* PRAGMA key = "x'<64 hex>'" — the raw-key convention (§5). The buffer holds key material,
     * so it is zeroed before this function returns on every path. */
    char key_pragma[128];
    snprintf(key_pragma, sizeof(key_pragma), "PRAGMA key=\"x'%s'\"", key_hex);
    rc = sqlite3_exec(db, key_pragma, NULL, NULL, NULL);
    memset(key_pragma, 0, sizeof(key_pragma));

    (*env)->ReleaseStringUTFChars(env, j_path, path);
    (*env)->ReleaseStringUTFChars(env, j_key_hex, key_hex);

    if (rc != SQLITE_OK) {
        throw_sqlite(env, "WRONG_PASSPHRASE", db, "PRAGMA key failed");
        sqlite3_close(db);
        return 0;
    }

    /* The first read of an encrypted page is what actually validates the key. */
    rc = sqlite3_exec(db, "SELECT count(*) FROM sqlite_master", NULL, NULL, NULL);
    if (rc != SQLITE_OK) {
        throw_vault(
            env, "WRONG_PASSPHRASE",
            "The database refused the derived key (SQLITE_NOTADB on the first read). "
            "That means a wrong passphrase or wrong cipher parameters, not a corrupt file.");
        sqlite3_close(db);
        return 0;
    }

    return (jlong) (intptr_t) db;
}

/*
 * Run one query, return every row as a String[] of column values.
 *
 * Rows are collected into a growing C array of local references; each cell's local ref is
 * released as soon as it is stored, so the JNI local reference table cannot overflow on a
 * large result set.
 */
JNIEXPORT jobjectArray JNICALL
Java_dev_valija_poc_infra_jni_ValijaNative_nativeSelectAll(
    JNIEnv *env, jobject thiz, jlong handle, jstring j_sql, jobjectArray j_args) {
    (void) thiz;

    sqlite3 *db = (sqlite3 *) (intptr_t) handle;
    if (db == NULL) {
        throw_vault(env, "INVALID_HEADER", "Database is closed.");
        return NULL;
    }

    const char *sql = (*env)->GetStringUTFChars(env, j_sql, NULL);
    if (sql == NULL) {
        return NULL;
    }

    sqlite3_stmt *stmt = NULL;
    int rc = sqlite3_prepare_v2(db, sql, -1, &stmt, NULL);
    (*env)->ReleaseStringUTFChars(env, j_sql, sql);
    if (rc != SQLITE_OK) {
        throw_sqlite(env, "INVALID_HEADER", db, "sqlite3_prepare_v2 failed");
        return NULL;
    }

    jsize arg_count = (j_args == NULL) ? 0 : (*env)->GetArrayLength(env, j_args);
    for (jsize i = 0; i < arg_count; i++) {
        jstring j_arg = (jstring) (*env)->GetObjectArrayElement(env, j_args, i);
        const char *arg = (*env)->GetStringUTFChars(env, j_arg, NULL);
        /* SQLITE_TRANSIENT: sqlite copies the text, so the JNI buffer can be released now. */
        sqlite3_bind_text(stmt, (int) i + 1, arg, -1, SQLITE_TRANSIENT);
        (*env)->ReleaseStringUTFChars(env, j_arg, arg);
        (*env)->DeleteLocalRef(env, j_arg);
    }

    jclass string_class = (*env)->FindClass(env, "java/lang/String");
    jobjectArray *rows = NULL;
    size_t row_count = 0;
    size_t capacity = 0;

    while ((rc = sqlite3_step(stmt)) == SQLITE_ROW) {
        int columns = sqlite3_column_count(stmt);
        jobjectArray row = (*env)->NewObjectArray(env, columns, string_class, NULL);
        if (row == NULL) {
            break;
        }
        for (int c = 0; c < columns; c++) {
            if (sqlite3_column_type(stmt, c) == SQLITE_NULL) {
                continue; /* leave the slot as Java null */
            }
            const unsigned char *text = sqlite3_column_text(stmt, c);
            jstring value = (*env)->NewStringUTF(env, (const char *) text);
            (*env)->SetObjectArrayElement(env, row, c, value);
            (*env)->DeleteLocalRef(env, value);
        }

        if (row_count == capacity) {
            size_t next = (capacity == 0) ? 32 : capacity * 2;
            jobjectArray *grown = realloc(rows, next * sizeof(jobjectArray));
            if (grown == NULL) {
                free(rows);
                sqlite3_finalize(stmt);
                throw_vault(env, "INVALID_HEADER", "Out of memory collecting rows.");
                return NULL;
            }
            rows = grown;
            capacity = next;
        }
        rows[row_count++] = row;
    }

    int step_rc = rc;
    sqlite3_finalize(stmt);

    if (step_rc != SQLITE_DONE) {
        free(rows);
        throw_sqlite(env, "WRONG_PASSPHRASE", db, "sqlite3_step failed");
        return NULL;
    }

    /* The outer array's element type is String[], not String. */
    jclass row_class = (*env)->FindClass(env, "[Ljava/lang/String;");
    jobjectArray result = (*env)->NewObjectArray(env, (jsize) row_count, row_class, NULL);
    if (result == NULL) {
        free(rows);
        return NULL; /* OutOfMemoryError already pending */
    }
    for (size_t i = 0; i < row_count; i++) {
        (*env)->SetObjectArrayElement(env, result, (jsize) i, rows[i]);
        (*env)->DeleteLocalRef(env, rows[i]);
    }
    free(rows);
    return result;
}

JNIEXPORT void JNICALL
Java_dev_valija_poc_infra_jni_ValijaNative_nativeClose(
    JNIEnv *env, jobject thiz, jlong handle) {
    (void) env;
    (void) thiz;
    sqlite3 *db = (sqlite3 *) (intptr_t) handle;
    if (db != NULL) {
        sqlite3_close(db);
    }
}

/*
 * Argon2id, raw output. Parameters arrive from the vault's own header — nothing is defaulted
 * here. The passphrase arrives as UTF-8 bytes rather than a jstring so the encoding is fixed
 * on the Kotlin side and cannot drift.
 */
JNIEXPORT jbyteArray JNICALL
Java_dev_valija_poc_infra_jni_ValijaNative_nativeArgon2idRaw(
    JNIEnv *env, jobject thiz, jbyteArray j_passphrase, jbyteArray j_salt,
    jint memory_kib, jint iterations, jint parallelism, jint hash_length) {
    (void) thiz;

    jsize pass_len = (*env)->GetArrayLength(env, j_passphrase);
    jsize salt_len = (*env)->GetArrayLength(env, j_salt);

    jbyte *passphrase = (*env)->GetByteArrayElements(env, j_passphrase, NULL);
    jbyte *salt = (*env)->GetByteArrayElements(env, j_salt, NULL);
    if (passphrase == NULL || salt == NULL) {
        return NULL;
    }

    uint8_t *hash = calloc((size_t) hash_length, 1);
    if (hash == NULL) {
        (*env)->ReleaseByteArrayElements(env, j_passphrase, passphrase, JNI_ABORT);
        (*env)->ReleaseByteArrayElements(env, j_salt, salt, JNI_ABORT);
        throw_vault(env, "KEY_MISMATCH", "Out of memory deriving the key.");
        return NULL;
    }

    int rc = argon2id_hash_raw(
        (uint32_t) iterations, (uint32_t) memory_kib, (uint32_t) parallelism,
        passphrase, (size_t) pass_len,
        salt, (size_t) salt_len,
        hash, (size_t) hash_length);

    /* JNI_ABORT: nothing was modified, and it avoids copying key material back. */
    (*env)->ReleaseByteArrayElements(env, j_passphrase, passphrase, JNI_ABORT);
    (*env)->ReleaseByteArrayElements(env, j_salt, salt, JNI_ABORT);

    if (rc != ARGON2_OK) {
        memset(hash, 0, (size_t) hash_length);
        free(hash);
        throw_vault(env, "KEY_MISMATCH", argon2_error_message(rc));
        return NULL;
    }

    jbyteArray result = (*env)->NewByteArray(env, hash_length);
    if (result != NULL) {
        (*env)->SetByteArrayRegion(env, result, 0, hash_length, (const jbyte *) hash);
    }
    memset(hash, 0, (size_t) hash_length);
    free(hash);
    return result;
}
