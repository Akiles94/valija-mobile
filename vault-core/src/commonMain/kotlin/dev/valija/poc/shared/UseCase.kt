package dev.valija.poc.shared

/**
 * The per-repo use-case contract, the Kotlin analogue of valija's
 * `src/shared/application/use-case.ts`. The other standing exception to the
 * "no bare files at a layer's root" rule.
 */
interface UseCase<In, Out> {
    fun execute(input: In): VaultResult<Out>
}
