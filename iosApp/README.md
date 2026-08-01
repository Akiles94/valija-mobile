# iosApp — the Xcode host

`iOSApp.swift` and `Info.plist` are the entire iOS shell: a SwiftUI `App` that hosts the shared
Compose UI (`MainViewControllerKt.MainViewController()`) and nothing else.

**The `.xcodeproj` is not committed.** It is generated boilerplate that would be the largest and
least reviewable file in this repository, and Xcode rewrites it on almost every interaction. For
this PoC it is created once, on the Mac, at the start of the device session:

1. Xcode → File → New → Project → iOS → App
   - Product Name: `iosApp`, Interface: SwiftUI, Language: Swift
   - Save into this directory, replacing the generated `ContentView.swift`/`Info.plist` with the
     two files already here.
2. Build Phases → add a "Run Script" phase **before** "Compile Sources":
   ```
   cd "$SRCROOT/.."
   ./gradlew :composeApp:embedAndSignAppleFrameworkForXcode
   ```
3. Build Settings → `Framework Search Paths` →
   `$(SRCROOT)/../composeApp/build/xcode-frameworks/$(CONFIGURATION)/$(SDK_NAME)`
4. Signing & Capabilities → select a personal team (a free Apple ID is enough for a 7-day
   development profile on your own device — no paid account, and nothing is submitted anywhere).

The exact commands used for the recorded device run are in `valija`'s
`advances/MOBILE/poc.md`, which is the authoritative runbook.
