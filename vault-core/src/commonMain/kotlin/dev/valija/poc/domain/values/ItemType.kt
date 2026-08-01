package dev.valija.poc.domain.values

/**
 * Every type that may be **stored**, matching the `CHECK` constraint on `context_items.type`
 * (`docs/vault-format.md` §6). `IMPORTED` is storable but never user-creatable, and never
 * appears in a pack (§8).
 *
 * [wireName] is the value in the database and the value the renderer prints
 * (`### decision · …`) — never the enum constant's name.
 */
enum class ItemType(val wireName: String) {
    DECISION("decision"),
    PROGRESS("progress"),
    PREFERENCE("preference"),
    FACT("fact"),
    HANDOFF("handoff"),
    IMPORTED("imported"),
}

fun parseItemType(raw: String): ItemType? = ItemType.entries.firstOrNull { it.wireName == raw }
