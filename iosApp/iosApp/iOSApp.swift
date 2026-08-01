import SwiftUI
import ComposeApp

// The only Swift in this repository, and it does nothing but host.
//
// P-8 chose Compose Multiplatform for the UI on BOTH platforms, so there is no SwiftUI screen
// here to keep in sync with an Android one. The Kotlin<->C interop boundary this PoC exists to
// test lives beneath the vault-reading port, not at this layer.
@main
struct iOSApp: App {
    var body: some Scene {
        WindowGroup {
            ComposeView().ignoresSafeArea(.all)
        }
    }
}

struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }
    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}
