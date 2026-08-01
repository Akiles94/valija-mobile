# valija-mobile

**This is a proof of concept. It is explicitly non-authoritative scaffolding, not the mobile app.**

It exists to answer one question with real evidence rather than inference: can a minimal Kotlin
Multiplatform + Compose Multiplatform app open valija's encrypted vault format and reproduce, on a
physical iPhone and a physical Android phone, the same rendered output the desktop CLI produces?

The full spec, plan, results, and evidence live in the main `valija` repository, not here:

- Spec: [`valija/advances/MOBILE/refined.md`](https://github.com/akiles94/valija/blob/main/advances/MOBILE/refined.md)
- Plan: [`valija/advances/MOBILE/plan.md`](https://github.com/akiles94/valija/blob/main/advances/MOBILE/plan.md)
- Results, once run: [`valija/advances/MOBILE/poc.md`](https://github.com/akiles94/valija/blob/main/advances/MOBILE/poc.md)
- The vault format this app implements against: [`valija/docs/vault-format.md`](https://github.com/akiles94/valija/blob/main/docs/vault-format.md)

## What this app does

Opens a bundled, published test vault (`vendor/golden-vault/` — the passphrase and key are
intentionally public, see `vendor/golden-vault/README.md`), derives the key with a vendored
reference Argon2id implementation, renders one project's context pack with a second (Kotlin)
implementation of the same algorithm valija's desktop uses, and byte-compares the result on
screen. That's the whole app: one button, one screen, no navigation, no writes, no network.

## What this app does not do

No real vault, no document picker, no biometrics, no keychain/keystore, no clipboard, no share
sheet, no distribution. See `valija/advances/MOBILE/refined.md` §6 for the full non-goals list —
this repository does not repeat it.

## Structure

Three Gradle modules, layered so the vendored C is reachable only through one port:

- `vault-core/` — pure Kotlin domain (pack assembly, rendering, byte-comparison). No SQLite, no
  C, no platform code. Its tests run on the JVM alone.
- `vault-interop/` — the only module that knows C exists: the `expect`/`actual` SQLite3MC and
  Argon2id bindings (JNI/NDK on Android, Kotlin/Native cinterop on iOS).
- `composeApp/` — the shared Compose Multiplatform screen and both platform shells.

Third-party C sources live in `vendor/`, each with its own `PROVENANCE.md` (version, SHA-256,
licence). Full licence texts are in `THIRD-PARTY-NOTICES.md`.

## Building

Everything JVM-only builds and tests with a local JDK + Gradle, no SDK required:

```
./gradlew :vault-core:jvmTest
```

The Android and iOS targets need an Android SDK/NDK and Xcode respectively; this project's own
CI (`.github/workflows/ci.yml`) builds and runs both on real emulators/simulators. Physical-device
verification is recorded in `valija`'s `advances/MOBILE/poc.md`, not here.
