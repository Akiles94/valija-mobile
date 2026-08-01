package dev.valija.poc

import dev.valija.poc.domain.entities.ContextItem
import dev.valija.poc.domain.services.ConformanceVerdict
import dev.valija.poc.domain.services.DEFAULT_BUDGET_TOKENS
import dev.valija.poc.domain.services.PackSection
import dev.valija.poc.domain.services.assembleContextPack
import dev.valija.poc.domain.services.compareRendered
import dev.valija.poc.domain.services.estimateTokens
import dev.valija.poc.domain.values.ItemType
import dev.valija.poc.domain.values.parseGoldenVaultManifest
import dev.valija.poc.domain.values.parseItemType
import dev.valija.poc.domain.values.parseVaultHeader
import dev.valija.poc.domain.values.saltBytes
import dev.valija.poc.delivery.renderContextPackMarkdown
import dev.valija.poc.shared.VaultResult
import dev.valija.poc.shared.getOrThrow
import java.io.File
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * The drift gate. Runs on the JVM in seconds — no SQLite, no vendored C, no device, no
 * emulator — and proves the single most valuable thing this PoC can prove: that a **second,
 * independent implementation** of valija's pack algorithm and renderer produces byte-identical
 * output from the same inputs.
 *
 * Everything the device runs add on top of this is the adapter, the interop and the process.
 * If this test is red, no device run is worth taking.
 */
class GoldenVaultConformanceTest {

    // --- the fixture, read straight from vendor/golden-vault/ ------------------------------

    private val fixtures = File(
        requireNotNull(System.getProperty("valija.fixtures")) {
            "System property 'valija.fixtures' is not set; see vault-core/build.gradle.kts"
        },
    )

    private fun fixtureText(name: String) = File(fixtures, name).readText(Charsets.UTF_8)
    private fun fixtureBytes(name: String) = File(fixtures, name).readBytes()

    private val manifest = parseGoldenVaultManifest(fixtureText("manifest.json")).getOrThrow()

    /** Only what the domain needs; `seed.json` is a test input, not part of the vault format. */
    @Serializable
    private data class SeedProject(val id: String, val name: String)

    @Serializable
    private data class SeedItem(
        val id: String,
        val projectId: String,
        val type: String,
        val content: String,
        val tags: List<String>,
        val pinned: Boolean,
        val archived: Boolean,
        val createdAt: String,
        val updatedAt: String,
    )

    @Serializable
    private data class Seed(val projects: List<SeedProject>, val items: List<SeedItem>)

    private val seed: Seed = seedJson.decodeFromString(fixtureText("seed.json"))

    private companion object {
        val seedJson = Json { ignoreUnknownKeys = true }
    }

    /**
     * Exactly what `SELECT * FROM context_items WHERE project_id = ? AND archived = 0
     * ORDER BY created_at DESC` returns (`docs/vault-format.md` §7) — reproduced in Kotlin so
     * this test needs no database.
     */
    private fun activeItemsFor(projectName: String): List<ContextItem> {
        val project = seed.projects.first { it.name == projectName }
        return seed.items
            .filter { it.projectId == project.id && !it.archived }
            .sortedByDescending { it.createdAt }
            .map {
                ContextItem(
                    id = it.id,
                    projectId = it.projectId,
                    type = requireNotNull(parseItemType(it.type)) { "unknown type ${it.type}" },
                    content = it.content,
                    tags = it.tags,
                    pinned = it.pinned,
                    archived = it.archived,
                    createdAt = it.createdAt,
                    updatedAt = it.updatedAt,
                )
            }
    }

    private fun renderAlpha(budgetTokens: Int?): String =
        renderContextPackMarkdown(
            assembleContextPack(
                projectName = "alpha",
                items = activeItemsFor("alpha"),
                generatedAt = manifest.generatedAt,
                budgetTokens = budgetTokens,
            ),
        )

    private fun assertByteIdentical(rendered: String, expectationName: String) {
        val actual = rendered.toByteArray(Charsets.UTF_8)
        val expected = fixtureBytes(expectationName)
        when (val verdict = compareRendered(actual, expected)) {
            is ConformanceVerdict.Pass -> Unit
            is ConformanceVerdict.Fail -> {
                val at = verdict.firstDifferenceIndex
                val window = { bytes: ByteArray ->
                    String(
                        bytes.copyOfRange(
                            maxOf(0, at - 40),
                            minOf(bytes.size, at + 40),
                        ),
                        Charsets.UTF_8,
                    )
                }
                throw AssertionError(
                    """
                    Rendered pack differs from $expectationName.
                      first difference at byte $at
                      rendered ${verdict.byteCount} bytes, expected ${verdict.expectedByteCount}
                      ...rendered: ${window(actual)}
                      ...expected: ${window(expected)}
                    """.trimIndent(),
                )
            }
        }
    }

    // --- the byte comparisons -------------------------------------------------------------

    @Test
    fun `the unbudgeted pack for alpha is byte-identical to expected-export`() {
        assertByteIdentical(renderAlpha(budgetTokens = null), "expected-export.md")
    }

    @Test
    fun `the budgeted pack for alpha is byte-identical to expected-pack`() {
        // The only thing in this advance that exercises the budgeting rules at all, and so the
        // only thing that can prove or disprove the M4 review's W5 (plan.md D-9).
        assertByteIdentical(renderAlpha(manifest.packBudgetTokens), "expected-pack.md")
    }

    // --- the token estimate, the trap that separates Kotlin from Swift --------------------

    @Test
    fun `estimateTokens matches the documented values`() {
        assertEquals(2, estimateTokens("abcde"), "the pinned constant in docs/vault-format.md §8")
        assertEquals(0, estimateTokens(""))
        assertEquals(1, estimateTokens("a"))
        assertEquals(1, estimateTokens("abcd"))
    }

    @Test
    fun `estimateTokens counts UTF-16 code units, not grapheme clusters`() {
        // "café ☕" — all BMP, so UTF-16 units and graphemes agree. This is the cheap check:
        // it catches a broken *encoding*, not a broken counting *semantic*.
        assertEquals(6, "café ☕".length)
        assertEquals(2, estimateTokens("café ☕"))

        // U+1D11E MUSICAL SYMBOL G CLEF is astral: one grapheme, two UTF-16 code units.
        // This is the assertion that actually separates the two semantics.
        val gClef = "𝄞"
        assertEquals(2, gClef.length, "Kotlin's String.length must be UTF-16 code units")

        // Four of them: 8 UTF-16 units -> 2 tokens; 4 graphemes would give 1. A Swift
        // implementation using String.count lands on 1 here and fails.
        assertEquals(2, estimateTokens(gClef.repeat(4)))
    }

    // --- what the pack must and must not contain ------------------------------------------

    @Test
    fun `the pack excludes archived and imported items but still counts imported`() {
        val items = activeItemsFor("alpha")
        assertEquals(9, items.size, "10 items minus the 1 archived one")
        assertTrue(items.none { it.archived }, "archived items are excluded by the query itself")
        assertTrue(items.any { it.type == ItemType.IMPORTED }, "the imported item is still read")

        val pack = assembleContextPack("alpha", items, manifest.generatedAt, budgetTokens = null)
        assertEquals(9, pack.totalCount, "the '> N items in vault' line counts imported items")

        val placed = pack.sections.flatMap { it.items }
        assertTrue(placed.none { it.type == ItemType.IMPORTED }, "imported is never in a section")
        assertTrue(placed.none { it.archived })
        assertEquals(placed.size, placed.map { it.id }.toSet().size, "no item appears twice")

        val body = renderContextPackMarkdown(pack)
        assertTrue(
            "This item is archived and must never appear" !in body,
            "the archived item's content leaked into the rendered pack",
        )
        assertTrue(
            "Imported conversation snippet" !in body,
            "the imported item's content leaked into the rendered pack",
        )
    }

    @Test
    fun `only the newest handoff appears, and older handoffs are dropped entirely`() {
        val pack = assembleContextPack(
            "alpha",
            activeItemsFor("alpha"),
            manifest.generatedAt,
            budgetTokens = null,
        )
        val handoffs = pack.sections.flatMap { it.items }.filter { it.type == ItemType.HANDOFF }
        assertEquals(1, handoffs.size, "'latest' means exactly one")
        assertEquals("item-a06", handoffs.single().id, "the newest handoff, not the older a05")
    }

    @Test
    fun `the newest pinned item survives a budget smaller than itself`() {
        val pack = assembleContextPack(
            "alpha",
            activeItemsFor("alpha"),
            manifest.generatedAt,
            budgetTokens = manifest.packBudgetTokens,
        )
        val pinned = pack.sections.filterIsInstance<PackSection.Pinned>().single()
        assertEquals(listOf("item-a08"), pinned.items.map { it.id })
        assertTrue(
            pack.estimatedTokens > manifest.packBudgetTokens,
            "the single kept pinned item is itself over budget — that is the rule being proven",
        )
    }

    @Test
    fun `the default budget constant is the documented one`() {
        assertEquals(4000, DEFAULT_BUDGET_TOKENS)
    }

    // --- the header parsers ----------------------------------------------------------------

    @Test
    fun `the fixture's own header parses and carries the published salt`() {
        val header = parseVaultHeader(fixtureText("vault.json")).getOrThrow()
        assertEquals(1, header.schemaVersion)
        assertEquals("argon2id", header.kdf.algorithm)
        assertEquals(65536, header.kdf.memoryKiB)
        assertEquals(3, header.kdf.iterations)
        assertEquals(1, header.kdf.parallelism)
        assertEquals(manifest.saltBase64, header.saltBase64)
        assertEquals(16, header.saltBytes().size, "a 16-byte salt, per docs/vault-format.md §4")
    }

    @Test
    fun `a header from the future is refused, never migrated`() {
        val fromTheFuture = fixtureText("vault.json")
            .replace("\"schemaVersion\": 1", "\"schemaVersion\": 99")
        val result = parseVaultHeader(fromTheFuture)
        assertIs<VaultResult.Err>(result)
        assertEquals("SCHEMA_TOO_NEW", result.error.code)
    }
}
