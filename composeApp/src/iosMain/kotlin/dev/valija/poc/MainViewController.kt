package dev.valija.poc

import androidx.compose.ui.window.ComposeUIViewController
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSDate
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask
import platform.Foundation.timeIntervalSince1970

/**
 * The whole iOS shell — the mirror of [MainActivity]. It resolves the app's own Caches
 * directory, hands it to the shared Compose UI, and does nothing else.
 */
fun MainViewController() = ComposeUIViewController {
    App(
        cacheDirectory = cachesDirectory(),
        nowMillis = { (NSDate().timeIntervalSince1970 * 1000).toLong() },
    )
}

private fun cachesDirectory(): String =
    NSSearchPathForDirectoriesInDomains(
        directory = NSCachesDirectory,
        domainMask = NSUserDomainMask,
        expandTilde = true,
    ).first() as String
