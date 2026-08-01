package dev.valija.poc.domain.services

import dev.valija.poc.domain.entities.ContextItem
import dev.valija.poc.domain.values.ItemType

/** `docs/vault-format.md` §8. Pinned by the conformance test, not just documented. */
const val DEFAULT_BUDGET_TOKENS: Int = 4000

/** U+2014 EM DASH, used only in the preamble's *cost* string — never in rendered output. */
private const val EM_DASH = "—"

/**
 * Cheap token estimate: ~4 characters per token, rounded up.
 *
 * **`String.length` here means UTF-16 code units**, because that is what JavaScript's
 * `String.length` is and this must agree with valija's TypeScript to the character. Kotlin's
 * `String.length` is also UTF-16 code units, so this port is exact — but Swift's
 * `String.count` counts *grapheme clusters* and would silently disagree on any astral
 * character. The conformance test pins this with `"𝄞"` (one grapheme, two UTF-16 units).
 *
 * Integer arithmetic, no floating point: `(n + 3) / 4` is `ceil(n / 4)` for non-negative `n`.
 */
fun estimateTokens(text: String): Int = (text.length + 3) / 4

/** What one item costs against the budget: its body plus the metadata that travels with it. */
fun estimateItemTokens(item: ContextItem): Int =
    estimateTokens(
        "${item.type.wireName} ${item.createdDate} ${item.tags.joinToString(" ")}\n\n${item.content}",
    )

/** Types that get their own section, in the order they appear in the pack (§8). */
val SECTION_TYPE_ORDER: List<ItemType> = listOf(
    ItemType.DECISION,
    ItemType.PREFERENCE,
    ItemType.PROGRESS,
    ItemType.FACT,
)

sealed interface PackSection {
    val items: List<ContextItem>

    data class Pinned(override val items: List<ContextItem>) : PackSection
    data class LatestHandoff(override val items: List<ContextItem>) : PackSection
    data class ByType(val type: ItemType, override val items: List<ContextItem>) : PackSection
}

data class ContextPack(
    val projectName: String,
    /** The raw ISO string, passed through verbatim to the renderer (D-7). */
    val generatedAt: String,
    val sections: List<PackSection>,
    val includedCount: Int,
    val totalCount: Int,
    val estimatedTokens: Int,
)

/** Mutable working state while the pack is assembled. Mirrors the TypeScript's `Draft`. */
private class Draft(val budget: Long) {
    val sections = mutableListOf<PackSection>()
    val included = mutableSetOf<String>()
    var usedTokens: Int = 0
}

private fun estimatePreambleTokens(
    projectName: String,
    itemCount: Int,
    generatedAt: String,
): Int = estimateTokens(
    "Context pack: $projectName $EM_DASH $itemCount items, generated $generatedAt",
)

/**
 * Choose what fits in the budget, newest first, and in what order it reads:
 * pinned, then the latest handoff, then a section per type. No item repeats.
 *
 * @param items newest first, archived already excluded — the repository contract (§7).
 * @param budgetTokens `null` means unbudgeted (the `valija export` path).
 */
fun assembleContextPack(
    projectName: String,
    items: List<ContextItem>,
    generatedAt: String,
    budgetTokens: Int? = null,
): ContextPack {
    val draft = Draft(budget = budgetTokens?.toLong() ?: Long.MAX_VALUE)
    draft.usedTokens = estimatePreambleTokens(projectName, items.size, generatedAt)

    addPinned(draft, items)
    addLatestHandoff(draft, items)
    addTypeSections(draft, items)

    return ContextPack(
        projectName = projectName,
        generatedAt = generatedAt,
        sections = draft.sections.toList(),
        includedCount = draft.included.size,
        totalCount = items.size,
        estimatedTokens = draft.usedTokens,
    )
}

/**
 * Pinned items, newest first.
 *
 * Label rule 1 of 3 (`docs/vault-format.md` §8, corrected by this advance): `"Pinned"` is
 * charged **unconditionally, before the loop** — even if no pinned item ends up fitting. And
 * the newest pinned item is kept **even when it alone exceeds the whole budget**; only from
 * the second item onward is the budget consulted.
 */
private fun addPinned(draft: Draft, items: List<ContextItem>) {
    val pinned = items.filter { it.pinned }
    if (pinned.isEmpty()) return

    draft.usedTokens += estimateTokens("Pinned")
    val kept = mutableListOf<ContextItem>()
    for (item in pinned) {
        val cost = estimateItemTokens(item)
        if (kept.isNotEmpty() && draft.usedTokens + cost > draft.budget) break
        kept.add(item)
        draft.usedTokens += cost
        draft.included.add(item.id)
    }
    draft.sections.add(PackSection.Pinned(kept.toList()))
}

/**
 * The latest handoff, if any and if it fits.
 *
 * Selection rule (M4 review W6): the newest `handoff` **not already placed in the Pinned
 * section** — not simply "the newest handoff". A pinned handoff that the budget pushed out of
 * the Pinned section is still eligible here.
 *
 * Label rule 2 of 3: the item's cost and `"Latest handoff"` are tested against the budget
 * **together**; the label is charged only if the pair fits.
 */
private fun addLatestHandoff(draft: Draft, items: List<ContextItem>) {
    val handoff = items.firstOrNull {
        it.type == ItemType.HANDOFF && it.id !in draft.included
    } ?: return

    val cost = estimateItemTokens(handoff) + estimateTokens("Latest handoff")
    if (draft.usedTokens + cost > draft.budget) return
    draft.usedTokens += cost
    draft.sections.add(PackSection.LatestHandoff(listOf(handoff)))
    draft.included.add(handoff.id)
}

/**
 * Recent items by type in section order, newest first, until the budget is reached.
 *
 * Label rule 3 of 3: the label is `estimateTokens(type.wireName)` — the **lowercase wire name**
 * (`"decision"`), *not* the rendered plural title (`"Decisions"`) — and it is folded into the
 * **first candidate's** budget test, so it is charged only if that first item fits.
 */
private fun addTypeSections(draft: Draft, items: List<ContextItem>) {
    for (type in SECTION_TYPE_ORDER) {
        val candidates = items.filter { it.type == type && it.id !in draft.included }
        val kept = mutableListOf<ContextItem>()
        for (item in candidates) {
            val labelCost = if (kept.isEmpty()) estimateTokens(type.wireName) else 0
            val cost = estimateItemTokens(item) + labelCost
            if (draft.usedTokens + cost > draft.budget) break
            kept.add(item)
            draft.usedTokens += cost
            draft.included.add(item.id)
        }
        if (kept.isNotEmpty()) draft.sections.add(PackSection.ByType(type, kept.toList()))
    }
}
