# Context pack: alpha

> 9 items in vault · generated 2026-07-26T12:00:00.000Z

## Pinned

### decision · 2026-01-01 · #pinned #long

This is the deliberately long pinned note used to prove the over-budget-pinned rule: the newest pinned item in a project must always be included in a budgeted context pack, even when its own size alone exceeds the configured token budget, and every older pinned item must then be cut starting with the oldest one first. The fixture's tight pack budget is only one hundred fifty estimated tokens, which corresponds to roughly six hundred characters once the per-item metadata prefix (its type, its creation date, and its tags) is accounted for alongside this body text, so this paragraph is written to comfortably exceed that threshold on its own before any other section of the pack is even considered for inclusion, giving the conformance test a wide, dependency-proof margin against small changes to the token-estimate formula.

### fact · 2026-01-01 · #pinned

Pinned note: keep the fixture's passphrase published and clearly documented as public test data, never real.


## Latest handoff

### handoff · 2026-01-01 · #handoff

Handoff: the golden vault fixture is ready for the conformance test; next step is the cipher-parameter probe.


## Decisions

### decision · 2026-01-01 · #security #storage

We chose SQLCipher for at-rest encryption; it lets the same vault.db file live inside a synced folder without ever exposing plaintext to the sync client.


## Preferences

### preference · 2026-01-01

Prefer terse commit messages over verbose ones.


## Progress

### progress · 2026-01-01 · #cli

Implemented the pack renderer.

Next: wire the CLI export command.

Note: café ☕ still needs unicode testing.


## Facts

### fact · 2026-01-01

The vault's schema is currently at version 3 — search scoping is verified per project.
