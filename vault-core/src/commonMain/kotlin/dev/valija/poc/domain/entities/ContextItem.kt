package dev.valija.poc.domain.entities

import dev.valija.poc.domain.values.ItemType

/**
 * One stored context item.
 *
 * **Timestamps are the raw stored ISO strings, never a date type** (plan.md D-7). Ordering is
 * the database's (`ORDER BY created_at DESC`), and the only formatting anyone needs is the
 * first ten characters. Parsing them into a date type and re-serialising is how a second
 * implementation silently produces `2026-07-26T12:00:00Z` where valija wrote
 * `2026-07-26T12:00:00.000Z` — a one-character difference that fails a byte comparison for a
 * reason that looks like nothing.
 */
data class ContextItem(
    val id: String,
    val projectId: String,
    val type: ItemType,
    val content: String,
    val tags: List<String>,
    val pinned: Boolean,
    val archived: Boolean,
    val createdAt: String,
    val updatedAt: String,
) {
    /** `YYYY-MM-DD` — what both the token estimate and the renderer use (§8, §9). */
    val createdDate: String get() = createdAt.take(10)
}
