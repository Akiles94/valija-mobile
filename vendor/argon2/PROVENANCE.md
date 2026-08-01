# Provenance — `vendor/argon2/`

**What this is:** the `phc-winner-argon2` reference C implementation — the same source the npm
`argon2` package (`ranisalt/node-argon2`) binds, which is what valija's desktop already uses to
derive the vault key. Vendoring it here means the PoC exercises the identical Argon2id algorithm
on device, not a reimplementation.

## Source

Copied from `valija/node_modules/argon2/argon2/` (npm package `argon2@0.44.0`), which itself
vendors `phc-winner-argon2`'s reference sources verbatim. Algorithm version: `ARGON2_VERSION_13`
(Argon2 v1.3, per `include/argon2.h`) — the current, non-deprecated version and the one valija's
desktop `argon2` binding produces.

**Files copied** (byte-for-byte, no edits): `include/argon2.h`; `src/argon2.c`, `src/core.c`,
`src/core.h`, `src/encoding.c`, `src/encoding.h`, `src/thread.c`, `src/thread.h`, `src/ref.c`;
`src/blake2/blake2.h`, `src/blake2/blake2-impl.h`, `src/blake2/blamka-round-ref.h`,
`src/blake2/blamka-round-opt.h`, `src/blake2/blake2b.c`; `LICENSE`.

**`ref.c`, not `opt.c`, is compiled.** `opt.c` is x86 SSE2-specific; on arm64 (both the Android and
iOS device targets) the reference implementation is upstream's only path, so this is not a
slow-path choice made for convenience — it is the only choice. `poc.md` states this explicitly so
the Argon2id timing (G5) is not read as artificially pessimistic.

Compiled with `-DARGON2_NO_THREADS` — the vault's Argon2id parameters use `parallelism: 1`
(`docs/vault-format.md` §4), so the pthread dependency is unneeded and removing it simplifies the
iOS link.

## SHA-256 of the copied files

```
72a93deebc5fd76bec0c6d300a2d92b500fb354b26babbddbc6d8b88681e663d  src/argon2.c
0f53eb2370f8971f04fcf043b8083bf81d1530c48b2ead71c0b6c4e22a01aeec  src/core.c
c9665623cb3d306f63b6a3effd87bcfab971c28253c77e111445e42c1523235e  src/core.h
e20124ec0f780f88527e79cf00825e839624ed1f469c7e231596bf070f8c7b14  src/encoding.c
42f283d12ec445cfb423ac2f1f5a5d1a7f152ce2b9363f6ec3e1d5b61cd4ee6d  src/encoding.h
4ec47b080c22f4ee416b9dbcfae70ee6007fcfba427f870d7b84395846fa1bc7  src/ref.c
1402085e2f54b021ea31b0d301f4b689a8b300832e2d11901502e62835185e3e  src/thread.c
9eab7f9ff356862a00a3075478dd0059344953ed79daab8b29185be2010efe78  src/thread.h
3c2de197dd23179f78c57deac7b73a6049778bf651c5db57e508b2cfce5e7559  src/blake2/blake2.h
8ac91f1f57d94235f8de0069b7809be1deb365c3b37adb8e30423cd364aff09a  src/blake2/blake2-impl.h
bcfdcf785218cf897f05b144e80b659e611188fee3887d533bdcb7a6aa4c336b  src/blake2/blamka-round-ref.h
ba5a9c1fef492cc6ce8d5b5217d6e7cd7983de10f16762f84e284d0ed9972e3b  src/blake2/blamka-round-opt.h
52519ccbc1e48f489ff444091f630d05dadcc8f1ee61c5b7361e3a9618299c9a  src/blake2/blake2b.c
df20cb726a2fe6bccc736b81ea0d86219766a0d17f1794c19e048a34830ca1cc  include/argon2.h
```

## Licence

Dual-licensed, at the user's option — reproduced verbatim in `vendor/argon2/LICENSE`:

```
Copyright 2015
Daniel Dinu, Dmitry Khovratovich, Jean-Philippe Aumasson, and Samuel Neves

You may use this work under the terms of a Creative Commons CC0 1.0
License/Waiver or the Apache Public License 2.0, at your option.
```

This PoC elects the **Apache-2.0** option, matching the rest of the repo. Full attribution appears
in `THIRD-PARTY-NOTICES.md` at the repo root.
