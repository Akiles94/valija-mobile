package dev.valija.poc.shared

import dev.valija.poc.domain.VaultError

/**
 * The repo-wide result contract — the Kotlin analogue of valija's
 * `src/shared/domain/result.ts`, and one of the two standing exceptions to the
 * "no bare files at a layer's root" rule.
 *
 * Parsing and use cases return this rather than throwing, so a caller cannot forget
 * that reading a vault can fail.
 */
sealed interface VaultResult<out T> {
    data class Ok<out T>(val value: T) : VaultResult<T>
    data class Err(val error: VaultError) : VaultResult<Nothing>
}

fun <T> ok(value: T): VaultResult<T> = VaultResult.Ok(value)

fun err(error: VaultError): VaultResult<Nothing> = VaultResult.Err(error)

/** Unwrap, or throw the carried error. For tests and for the outermost UI edge only. */
fun <T> VaultResult<T>.getOrThrow(): T = when (this) {
    is VaultResult.Ok -> value
    is VaultResult.Err -> throw error
}
