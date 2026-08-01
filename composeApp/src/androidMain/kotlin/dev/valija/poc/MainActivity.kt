package dev.valija.poc

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent

/**
 * The whole Android shell. It hands the app its cache directory and a clock, and does nothing
 * else — no permissions, no services, no lifecycle work.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            App(
                cacheDirectory = cacheDir.absolutePath,
                nowMillis = { System.currentTimeMillis() },
            )
        }
    }
}
