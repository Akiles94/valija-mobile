package dev.valija.poc.domain.services

/**
 * The verdict the screen shows and the device test asserts on.
 *
 * [Fail] carries the exact byte offset so a mismatch is a lead, not a mystery.
 */
sealed interface ConformanceVerdict {
    data class Pass(val byteCount: Int) : ConformanceVerdict

    data class Fail(
        val byteCount: Int,
        val expectedByteCount: Int,
        val firstDifferenceIndex: Int,
    ) : ConformanceVerdict
}

/**
 * Compare rendered output against the committed expectation, **over UTF-8 bytes**.
 *
 * Not a `String` comparison, not normalised, not whitespace-insensitive, not a snapshot. The
 * whole point of this PoC is that a second implementation produces the same bytes — comparing
 * anything softer would let a `\r\n`, a normalised `·`, or a re-encoded `café ☕` pass.
 */
fun compareRendered(actual: ByteArray, expected: ByteArray): ConformanceVerdict {
    val shared = minOf(actual.size, expected.size)
    for (i in 0 until shared) {
        if (actual[i] != expected[i]) {
            return ConformanceVerdict.Fail(actual.size, expected.size, i)
        }
    }
    if (actual.size != expected.size) {
        return ConformanceVerdict.Fail(actual.size, expected.size, shared)
    }
    return ConformanceVerdict.Pass(actual.size)
}

/** A one-line, screen-ready summary. The exact text that appears in the committed screenshots. */
fun ConformanceVerdict.describe(expectationName: String): String = when (this) {
    is ConformanceVerdict.Pass ->
        "CONFORMANCE: PASS — $byteCount bytes, byte-identical to $expectationName"
    is ConformanceVerdict.Fail ->
        "CONFORMANCE: FAIL — first difference at byte $firstDifferenceIndex " +
            "($byteCount bytes rendered, $expectedByteCount expected)"
}
