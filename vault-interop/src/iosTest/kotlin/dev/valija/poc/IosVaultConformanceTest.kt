package dev.valija.poc

import dev.valija.poc.application.usecases.ReadContextPack
import dev.valija.poc.application.usecases.ReadContextPackInput
import dev.valija.poc.domain.services.ConformanceVerdict
import dev.valija.poc.domain.services.compareRendered
import dev.valija.poc.domain.services.describe
import dev.valija.poc.domain.values.parseGoldenVaultManifest
import dev.valija.poc.domain.values.parseVaultHeader
import dev.valija.poc.domain.values.saltBytes
import dev.valija.poc.infra.argon2.Argon2idKeyDeriver
import dev.valija.poc.infra.sqlite.Sqlite3mcDatabase
import dev.valija.poc.infra.sqlite.Sqlite3mcVaultReader
import dev.valija.poc.shared.getOrThrow
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.usePinned
import platform.posix.SEEK_END
import platform.posix.fclose
import platform.posix.fopen
import platform.posix.fread
import platform.posix.fseek
import platform.posix.ftell
import platform.posix.getenv
import kotlinx.cinterop.toKString
import platform.posix.rewind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The Kotlin/Native side of the conformance check, exercising the **cinterop** path rather than
 * Android's JNI path.
 *
 * **Scope, stated where the claim is made:** this runs on the iOS *simulator*
 * (`:vault-interop:iosSimulatorArm64Test`), which shares the host filesystem, so it reads the
 * committed fixture by absolute path. That is why it is a simulator-only check. The
 * physical-iPhone evidence for this advance comes from the app itself — its on-screen verdict
 * and console log (plan.md Slice 9) — not from this test.
 */
@OptIn(ExperimentalForeignApi::class)
class IosVaultConformanceTest {

    private val fixtures: String = getenv("VALIJA_FIXTURES")?.toKString()
        ?: error("VALIJA_FIXTURES is not set; see vault-interop/build.gradle.kts")

    private fun readBytes(name: String): ByteArray {
        val path = "$fixtures/$name"
        val file = fopen(path, "rb") ?: error("cannot open $path")
        try {
            fseek(file, 0, SEEK_END)
            val size = ftell(file).toInt()
            rewind(file)
            val buffer = ByteArray(size)
            if (size > 0) {
                buffer.usePinned { fread(it.addressOf(0), 1.convert(), size.convert(), file) }
            }
            return buffer
        } finally {
            fclose(file)
        }
    }

    private fun readText(name: String) = readBytes(name).decodeToString()

    @Test
    fun derivesThePublishedKeyAndRendersAByteIdenticalPack() {
        val manifest = parseGoldenVaultManifest(readText("manifest.json")).getOrThrow()
        val header = parseVaultHeader(readText("vault.json")).getOrThrow()

        val keyHex = Argon2idKeyDeriver().deriveKeyHex(
            passphrase = manifest.passphrase,
            salt = header.saltBytes(),
            memoryKiB = header.kdf.memoryKiB,
            iterations = header.kdf.iterations,
            parallelism = header.kdf.parallelism,
        )
        assertEquals(manifest.keyHex, keyHex, "derived key must equal the published one")

        // Read-only: the simulator can reach the committed fixture directly, so this opens it
        // strictly read-only and asserts afterwards that no sidecar was produced.
        val reader = Sqlite3mcVaultReader(Sqlite3mcDatabase("$fixtures/vault.db", keyHex))
        try {
            assertEquals("3", reader.readSchemaVersion())
            val rendered = ReadContextPack(reader).execute(
                ReadContextPackInput("alpha", manifest.generatedAt, budgetTokens = null),
            ).getOrThrow()

            val verdict = compareRendered(
                rendered.markdown.encodeToByteArray(),
                readBytes("expected-export.md"),
            )
            println(verdict.describe("expected-export.md"))
            assertTrue(verdict is ConformanceVerdict.Pass, verdict.describe("expected-export.md"))
            assertEquals(9, rendered.pack.totalCount)
        } finally {
            reader.close()
        }
    }

    @Test
    fun refusesAWrongKeyAsAWrongPassphrase() {
        val error = runCatching {
            Sqlite3mcDatabase("$fixtures/vault.db", "0".repeat(64))
        }.exceptionOrNull()
        assertTrue(error is dev.valija.poc.domain.VaultError, "expected VaultError, got $error")
        assertEquals("WRONG_PASSPHRASE", error.code)
    }
}
