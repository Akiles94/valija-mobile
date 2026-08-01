package dev.valija.poc.domain.entities

/**
 * A project. It has no content of its own — everything a pack shows comes from its items
 * (`docs/vault-format.md` §7).
 */
data class Project(
    val id: String,
    val name: String,
    val createdAt: String,
    val updatedAt: String,
)
