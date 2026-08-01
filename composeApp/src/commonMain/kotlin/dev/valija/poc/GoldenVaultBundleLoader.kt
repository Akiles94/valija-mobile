package dev.valija.poc

import dev.valija.poc.application.usecases.RunGoldenVaultConformance
import dev.valija.poc.resources.Res
import org.jetbrains.compose.resources.ExperimentalResourceApi

/**
 * Reads the four bundled fixture files out of the app bundle.
 *
 * Resource IO is the one thing Compose Multiplatform already abstracts per platform, so it
 * needs no `expect`/`actual` of ours. `seed.json` and `expected-pack.md` are deliberately not
 * bundled — they are JVM-test inputs, and an app should carry only what it actually opens.
 */
@OptIn(ExperimentalResourceApi::class)
suspend fun loadGoldenVaultBundle(): RunGoldenVaultConformance.GoldenVaultBundle =
    RunGoldenVaultConformance.GoldenVaultBundle(
        vaultDb = Res.readBytes("files/golden-vault/vault.db"),
        vaultJson = Res.readBytes("files/golden-vault/vault.json").decodeToString(),
        manifestJson = Res.readBytes("files/golden-vault/manifest.json").decodeToString(),
        expectedExport = Res.readBytes("files/golden-vault/expected-export.md"),
    )
