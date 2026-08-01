package dev.valija.poc.delivery

import dev.valija.poc.domain.entities.ContextItem
import dev.valija.poc.domain.services.ContextPack
import dev.valija.poc.domain.services.PackSection
import dev.valija.poc.domain.values.ItemType

/**
 * U+00B7 MIDDLE DOT — the separator between type, date and tags. Not a hyphen, not a bullet,
 * not a pipe. Written as an escape so no editor, terminal or `.gitattributes` slip can turn it
 * into something that looks identical and compares differently.
 */
private const val MIDDLE_DOT = "·"

/** Section headings. The domain orders sections; naming them is presentation. */
private val TYPE_LABELS: Map<ItemType, String> = mapOf(
    ItemType.DECISION to "Decisions",
    ItemType.PREFERENCE to "Preferences",
    ItemType.PROGRESS to "Progress",
    ItemType.FACT to "Facts",
    ItemType.HANDOFF to "Handoffs",
)

private fun sectionTitle(section: PackSection): String = when (section) {
    is PackSection.Pinned -> "Pinned"
    is PackSection.LatestHandoff -> "Latest handoff"
    // IMPORTED never reaches a by-type section (§8), so the fallback is unreachable by design.
    is PackSection.ByType -> TYPE_LABELS[section.type] ?: section.type.wireName
}

private fun renderItem(item: ContextItem): String {
    val tags = if (item.tags.isEmpty()) {
        ""
    } else {
        " $MIDDLE_DOT " + item.tags.joinToString(" ") { "#$it" }
    }
    return "### ${item.type.wireName} $MIDDLE_DOT ${item.createdDate}$tags\n\n${item.content}\n"
}

/**
 * Render an assembled pack as the markdown handed to a human or an LLM.
 *
 * **The concatenation rule is load-bearing** and was under-specified in
 * `docs/vault-format.md` §9 until this advance corrected it: the header already ends with a
 * newline, each section part *starts* with one, each item part *ends* with one, and the parts
 * are joined with a further `"\n"`. That join is what produces the two blank lines before every
 * `##` heading that follows an item, and exactly one blank line elsewhere. Building this with
 * `appendLine` per element, or joining with `""`, produces output that looks right in a diff
 * viewer and fails a byte comparison.
 */
fun renderContextPackMarkdown(pack: ContextPack): String {
    val header = "# Context pack: ${pack.projectName}\n\n" +
        "> ${pack.totalCount} items in vault $MIDDLE_DOT generated ${pack.generatedAt}\n"
    val parts = pack.sections.flatMap { section ->
        listOf("\n## ${sectionTitle(section)}\n") + section.items.map(::renderItem)
    }
    return header + parts.joinToString("\n")
}
