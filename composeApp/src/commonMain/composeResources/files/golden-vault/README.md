# Golden vault fixture

A **published test vault** used to keep `docs/vault-format.md` honest and to give a second
(non-Node) implementation something real to open. Every value here is public test data —
**never point a real vault at these values, and never treat this passphrase or key as a
secret.** The vault contains no real user content.

## Files

- `manifest.json` — the published parameter set: passphrase, the raw key it derives to,
  salt, KDF params, the probed SQLCipher parameters, and a few fixed ids used to build the
  one lineage bump the vault carries.
- `seed.json` — the exact plaintext rows the vault contains (two projects, twelve items),
  so a second implementation can see what it should be reading without decrypting anything.
- `vault.json` / `vault.db` — the built fixture: the plaintext header and the encrypted
  SQLCipher database, produced from `manifest.json` + `seed.json` through valija's real
  write path (`src/testing/golden-vault.ts`'s `buildGoldenVault`).
- `expected-pack.md` — the rendered context pack for project `alpha` at the tight budget
  recorded in `manifest.json` (`packBudgetTokens`), exercising the "newest pinned item
  included even over budget" rule.
- `expected-export.md` — the same pack, unbudgeted (the `valija export` path), exercising
  the full section order.
- `expected-search.json` — expected results for a fixed set of search queries, exercising
  quote-escaping, project scoping, the imported/archived rules, and limit truncation.

## Regenerating

Run the conformance test with the regeneration flag set:

```
VALIJA_WRITE_GOLDEN_VAULT=1 npx vitest run src/delivery/vault-format-conformance.test.ts
```

This rebuilds `vault.json`/`vault.db` from `manifest.json` + `seed.json`, re-derives the key,
re-probes the cipher parameters, re-renders both expected packs and the search results, then
**fails the run on purpose** — regeneration must never look like a passing test, so a stray
env var can never silently rewrite the fixture in CI. Review the diff, then commit it.

Note: the ciphertext itself (`vault.db`) is never byte-stable across regenerations — it
carries random IVs — only the *rendered* expectations are compared byte-for-byte. See
`docs/vault-format.md` §14 for what a change to the pack algorithm or renderer requires.
