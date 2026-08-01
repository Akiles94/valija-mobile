cmake_minimum_required(VERSION 3.22.1)
project(valija_native C)

# cpp/ -> androidMain -> src -> vault-interop -> repo root
get_filename_component(REPO_ROOT "${CMAKE_CURRENT_SOURCE_DIR}/../../../.." ABSOLUTE)
set(VENDOR "${REPO_ROOT}/vendor")

# Read the compile flags from their single source of truth, so the Android build and the iOS
# build cannot drift. A define that differs between platforms would silently produce a
# different cipher configuration -- see vendor/sqlite3mc/PROVENANCE.md.
function(read_defines OUT_VAR PATH)
    file(STRINGS "${PATH}" RAW)
    set(ACC "")
    foreach(LINE ${RAW})
        string(STRIP "${LINE}" LINE)
        if(NOT LINE STREQUAL "" AND NOT LINE MATCHES "^#")
            set(ACC "${ACC} ${LINE}")
        endif()
    endforeach()
    set(${OUT_VAR} "${ACC}" PARENT_SCOPE)
endfunction()

read_defines(SQLITE_FLAGS "${REPO_ROOT}/gradle/native-defines.txt")
read_defines(ARGON2_FLAGS "${REPO_ROOT}/gradle/argon2-defines.txt")

set(ARGON2_SOURCES
    "${VENDOR}/argon2/src/argon2.c"
    "${VENDOR}/argon2/src/core.c"
    "${VENDOR}/argon2/src/encoding.c"
    # ref.c, not opt.c: opt.c is x86 SSE2-only, so on arm64 the reference implementation is
    # upstream's only path. See vendor/argon2/PROVENANCE.md.
    "${VENDOR}/argon2/src/ref.c"
    "${VENDOR}/argon2/src/thread.c"
    "${VENDOR}/argon2/src/blake2/blake2b.c"
)

add_library(valija_native SHARED
    "${VENDOR}/sqlite3mc/sqlite3.c"
    ${ARGON2_SOURCES}
    valija_native.c
)

set_source_files_properties("${VENDOR}/sqlite3mc/sqlite3.c"
    PROPERTIES COMPILE_FLAGS "${SQLITE_FLAGS}")
set_source_files_properties(${ARGON2_SOURCES}
    PROPERTIES COMPILE_FLAGS "${ARGON2_FLAGS}")

target_include_directories(valija_native PRIVATE
    "${VENDOR}/sqlite3mc"
    "${VENDOR}/argon2/include"
    "${VENDOR}/argon2/src"
)

# Android's libc provides dl/pthread/math in libc itself; only the log library would be extra,
# and this bridge deliberately logs nothing.
target_link_libraries(valija_native)
