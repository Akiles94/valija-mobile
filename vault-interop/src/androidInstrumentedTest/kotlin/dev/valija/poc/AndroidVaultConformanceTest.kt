package dev.valija.poc

import androidx.test.platform.app.InstrumentationRegistry
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
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The on-device, machine-checkable half of P-5: this returns a real process exit code from
 * `./gradlew :vault-interop:connectedAndroidTest`, on real hardware, through the real JNI
 * bridge and the real vendored amalgamation.
 *
 * The on-screen verdict in the app is what a human reads; this is what fails a build when it
 * stops being true.
 */
class AndroidVaultConformanceTest {

    private val context = InstrumentationRegistry.getInstrumentation().context
    private val cacheDir: File = InstrumentationRegistry.getInstrumentation()
        .targetContext.cacheDir

    private fun asset(name: String): ByteArray =
        context.assets.open(name).use { it.readBytes() }

    private fun assetText(name: String): String = asset(name).toString(Charsets.UTF_8)

    /** Never open the bundled resource in place — copy to the sandbox first (§11). */
    private fun snapshot(): File {
        val dir = File(cacheDir, "valija-conformance").apply { mkdirs() }
        val copy = File(dir, "vault.db")
        copy.writeBytes(asset("vault.db"))
        return copy
    }

    @Test
    fun derivesThePublishedKeyAndRendersAByteIdenticalPack() {
        val manifest = parseGoldenVaultManifest(assetText("manifest.json")).getOrThrow()
        val header = parseVaultHeader(assetText("vault.json")).getOrThrow()

        val startedAt = System.nanoTime()
        val keyHex = Argon2idKeyDeriver().deriveKeyHex(
            passphrase = manifest.passphrase,
            salt = header.saltBytes(),
            memoryKiB = header.kdf.memoryKiB,
            iterations = header.kdf.iterations,
            parallelism = header.kdf.parallelism,
        )
        val derivationMillis = (System.nanoTime() - startedAt) / 1_000_000
        println("ARGON2ID: ${header.kdf.memoryKiB / 1024} MiB / t=${header.kdf.iterations} / " +
            "p=${header.kdf.parallelism} -> $derivationMillis ms " +
            "(${android.os.Build.MODEL}, ${android.os.Build.SUPPORTED_ABIS.firstOrNull()})")

        // Assert the key BEFORE opening, so a derivation bug reports itself rather than
        // masquerading as a corrupt database.
        assertEquals(manifest.keyHex, keyHex, "derived key must equal the published one")

        val copy = snapshot()
        val reader = Sqlite3mcVaultReader(Sqlite3mcDatabase(copy.absolutePath, keyHex))
        try {
            assertEquals("3", reader.readSchemaVersion())

            val rendered = ReadContextPack(reader).execute(
                ReadContextPackInput("alpha", manifest.generatedAt, budgetTokens = null),
            ).getOrThrow()

            val verdict = compareRendered(
                rendered.markdown.toByteArray(Charsets.UTF_8),
                asset("expected-export.md"),
            )
            println(verdict.describe("expected-export.md"))
            assertTrue(verdict is ConformanceVerdict.Pass, verdict.describe("expected-export.md"))
            assertEquals(9, rendered.pack.totalCount)
        } finally {
            reader.close()
        }

        val sidecars = copy.parentFile!!.listFiles().orEmpty().map { it.name }.filter {
            it.endsWith("-wal") || it.endsWith("-shm") || it.endsWith("-journal")
        }
        assertEquals(emptyList(), sidecars, "a read must never produce a journal sidecar")
    }

    @Test
    fun refusesAWrongKeyAsAWrongPassphrase() {
        val copy = snapshot()
        val error = runCatching { Sqlite3mcDatabase(copy.absolutePath, "0".repeat(64)) }
            .exceptionOrNull()
        assertTrue(error is dev.valija.poc.domain.VaultError, "expected VaultError, got $error")
        assertEquals("WRONG_PASSPHRASE", error.code)
    }
}
