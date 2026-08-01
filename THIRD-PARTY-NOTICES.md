# Third-party notices

This repository vendors two third-party C libraries under `vendor/`. Both licences are
reproduced here in full, satisfying their attribution requirements; per-file provenance,
versions, and SHA-256 hashes live next to each library in `vendor/<name>/PROVENANCE.md`.

---

## SQLite3MultipleCiphers (`vendor/sqlite3mc/`)

Copyright (c) 2006-2025 Ulrich Telle. Licensed under the MIT License.

```
MIT License

Copyright (c) 2019-2026 Ulrich Telle

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

The amalgamation also embeds the SQLite core itself, which is public domain and requires no
notice (https://www.sqlite.org/copyright.html).

---

## phc-winner-argon2 (`vendor/argon2/`)

Copyright 2015 Daniel Dinu, Dmitry Khovratovich, Jean-Philippe Aumasson, and Samuel Neves.
Dual-licensed under CC0-1.0 or Apache-2.0, at the user's option. This project elects **Apache-2.0**,
matching the rest of the repository.

```
Copyright 2015
Daniel Dinu, Dmitry Khovratovich, Jean-Philippe Aumasson, and Samuel Neves

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```

The full text of both license options is also reproduced verbatim in `vendor/argon2/LICENSE`.

---

## Everything else

The rest of this repository — the Gradle build, the Kotlin sources, the Compose UI — is
`akiles94/valija-mobile`'s own code, licensed Apache-2.0 (see `LICENSE`), matching `valija`.
