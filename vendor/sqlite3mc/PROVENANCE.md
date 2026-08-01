# Provenance — `vendor/sqlite3mc/`

**What this is:** the SQLite3MultipleCiphers amalgamation, the same C source
`better-sqlite3-multiple-ciphers` compiles into valija's desktop native addon. This PoC vendors
the byte-identical files so both ends of the vault format run the same cipher implementation,
per `docs/vault-format.md` and `advances/M4/spike.md`'s Option 2 finding.

## Versions

- **SQLite3MultipleCiphers:** `v2.3.5` (tag, `github.com/utelle/SQLite3MultipleCiphers`)
- **SQLite core:** `3.53.2` (`VERSION="3530200"` in the source npm package's build script)
- **Source npm package:** `better-sqlite3-multiple-ciphers@12.11.1`
- **Copied from:** `valija/node_modules/better-sqlite3-multiple-ciphers/deps/sqlite3/`, verbatim,
  byte-for-byte — no edits.

## SHA-256 of the copied files

```
670d8d053176b53a68073b168f8e68fb72db67bdf964a0eb130338e9391198b9  sqlite3.c
8270c30673c9dccb08f0516ae63b64f898a09bc92b76d850cd912b0c1461dbe5  sqlite3.h
a3ca6e430c8e97edf8cbd66867ac178ab179a41d85c04cad48889a8b84806dcd  sqlite3ext.h
```

## Compile flags

Must match **exactly** what `better-sqlite3-multiple-ciphers`'s `node-gyp` build uses for the
desktop native addon (`valija/node_modules/better-sqlite3-multiple-ciphers/deps/defines.gypi` and
`deps/sqlite3.gyp`). The single source of truth for this PoC is `gradle/native-defines.txt`, read
by both the Android CMake build and the iOS `Exec` task — a define that differs between platforms
would silently build a different cipher configuration and re-open the compatibility question
`advances/M4/spike.md` already closed.

```
-std=c99 -w -O3 -DNDEBUG
-DHAVE_INT16_T=1 -DHAVE_INT32_T=1 -DHAVE_INT8_T=1 -DHAVE_STDINT_H=1
-DHAVE_UINT16_T=1 -DHAVE_UINT32_T=1 -DHAVE_UINT8_T=1 -DHAVE_USLEEP=1
-DSQLITE_DEFAULT_CACHE_SIZE=-16000 -DSQLITE_DEFAULT_FOREIGN_KEYS=1
-DSQLITE_DEFAULT_MEMSTATUS=0 -DSQLITE_DEFAULT_WAL_SYNCHRONOUS=1 -DSQLITE_DQS=0
-DSQLITE_ENABLE_COLUMN_METADATA -DSQLITE_ENABLE_DBSTAT_VTAB -DSQLITE_ENABLE_DESERIALIZE
-DSQLITE_ENABLE_FTS3 -DSQLITE_ENABLE_FTS3_PARENTHESIS -DSQLITE_ENABLE_FTS4
-DSQLITE_ENABLE_FTS5 -DSQLITE_ENABLE_GEOPOLY -DSQLITE_ENABLE_JSON1
-DSQLITE_ENABLE_MATH_FUNCTIONS -DSQLITE_ENABLE_PERCENTILE -DSQLITE_ENABLE_RTREE
-DSQLITE_ENABLE_STAT4 -DSQLITE_ENABLE_UPDATE_DELETE_LIMIT
-DSQLITE_LIKE_DOESNT_MATCH_BLOBS -DSQLITE_OMIT_DEPRECATED
-DSQLITE_OMIT_PROGRESS_CALLBACK -DSQLITE_OMIT_SHARED_CACHE -DSQLITE_OMIT_TCL_VARIABLE
-DSQLITE_SOUNDEX -DSQLITE_THREADSAFE=2 -DSQLITE_TRACE_SIZE_LIMIT=32
-DSQLITE_USER_AUTHENTICATION=0 -DSQLITE_USE_URI=0
```

`SQLITE_THREADSAFE=2` is multi-thread mode: a single connection must never be shared between
threads. This PoC confines all database work to one dispatcher (see `composeApp`'s
`RunGoldenVaultConformance`).

## Licence — verified, not assumed

The vendored `sqlite3.c`'s own header block reads:

```
Name:        sqlite3mc.c
Author:      Ulrich Telle
Copyright:   (c) 2006-2025 Ulrich Telle
License:     MIT
```

The upstream `LICENSE` file at tag `v2.3.5` was fetched directly
(`raw.githubusercontent.com/utelle/SQLite3MultipleCiphers/v2.3.5/LICENSE`) and copied verbatim to
`vendor/sqlite3mc/LICENSE` — MIT requires the full licence text and copyright notice to travel with
the source, which is why the text is reproduced rather than paraphrased or assumed from the header
comment alone. The embedded SQLite core itself is public domain and needs no notice.

Full attribution appears in `THIRD-PARTY-NOTICES.md` at the repo root.
