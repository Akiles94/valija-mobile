#!/usr/bin/env bash
#
# Build the vendored C (SQLite3MultipleCiphers + phc-winner-argon2) into one static library
# for a single Apple target.
#
#   ./build-apple-native.sh <sdk> <target-triple> <output-dir> <repo-root>
#
# e.g. ./build-apple-native.sh iphoneos          arm64-apple-ios15.0           out/ .
#      ./build-apple-native.sh iphonesimulator   arm64-apple-ios15.0-simulator out/ .
#
# Gradle calls this once per Apple target, but it is deliberately a plain script so the whole
# native build can be reproduced by hand on the borrowed Mac without going through Gradle --
# which is exactly what you want when something fails during a short hardware window.
#
# The compile flags come from gradle/native-defines.txt and gradle/argon2-defines.txt, the same
# two files the Android CMake build reads. Nothing is duplicated here.
set -euo pipefail

SDK="${1:?usage: build-apple-native.sh <sdk> <triple> <outdir> <repo-root>}"
TRIPLE="${2:?missing target triple}"
OUT="${3:?missing output dir}"
ROOT="${4:?missing repo root}"

read_flags() {
    grep -v '^[[:space:]]*#' "$1" | grep -v '^[[:space:]]*$' | tr '\n' ' '
}

SQLITE_FLAGS="$(read_flags "$ROOT/gradle/native-defines.txt")"
ARGON2_FLAGS="$(read_flags "$ROOT/gradle/argon2-defines.txt")"

OBJ="$OUT/obj"
mkdir -p "$OBJ"

compile() {
    local src="$1" out="$2" flags="$3" includes="$4"
    # shellcheck disable=SC2086
    xcrun --sdk "$SDK" clang -target "$TRIPLE" -fPIC -c $flags $includes -o "$out" "$src"
}

echo "==> sqlite3mc ($TRIPLE, sdk=$SDK)"
compile "$ROOT/vendor/sqlite3mc/sqlite3.c" "$OBJ/sqlite3.o" \
    "$SQLITE_FLAGS" "-I$ROOT/vendor/sqlite3mc"

echo "==> argon2 ($TRIPLE, sdk=$SDK)"
for name in argon2 core encoding ref thread; do
    compile "$ROOT/vendor/argon2/src/$name.c" "$OBJ/$name.o" \
        "$ARGON2_FLAGS" "-I$ROOT/vendor/argon2/include -I$ROOT/vendor/argon2/src"
done
compile "$ROOT/vendor/argon2/src/blake2/blake2b.c" "$OBJ/blake2b.o" \
    "$ARGON2_FLAGS" "-I$ROOT/vendor/argon2/include -I$ROOT/vendor/argon2/src"

echo "==> libvalijanative.a"
xcrun libtool -static -o "$OUT/libvalijanative.a" "$OBJ"/*.o
xcrun lipo -info "$OUT/libvalijanative.a" || true
