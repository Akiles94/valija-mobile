package dev.valija.poc

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.valija.poc.application.usecases.ConformanceReport
import dev.valija.poc.application.usecases.RunGoldenVaultConformance
import dev.valija.poc.domain.services.ConformanceVerdict
import dev.valija.poc.domain.services.describe
import dev.valija.poc.shared.VaultResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.ExperimentalResourceApi

/**
 * The entire UI: one button, one verdict, one rendered pack.
 *
 * Shared across iOS and Android as one Compose Multiplatform codebase (P-8). The interop
 * boundary this PoC exists to test lives *beneath* the port, not here — this screen calls a use
 * case and never sees SQL, a key, or a file path.
 */
@OptIn(ExperimentalResourceApi::class)
@Composable
fun App(cacheDirectory: String, nowMillis: () -> Long) {
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            var state by remember { mutableStateOf<ScreenState>(ScreenState.Idle) }
            val scope = rememberCoroutineScope()

            Column(
                modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "valija — vault format proof of concept",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = "Opens the published golden-vault fixture (public test data, not a " +
                        "real vault), derives its key with Argon2id, renders project 'alpha' " +
                        "and byte-compares the result on this device.",
                    style = MaterialTheme.typography.bodySmall,
                )

                Button(
                    onClick = {
                        state = ScreenState.Running
                        scope.launch {
                            val bundle = loadGoldenVaultBundle()
                            // SQLITE_THREADSAFE=2 means one connection, one thread: all vault
                            // work stays on this single background dispatcher.
                            val result = withContext(Dispatchers.Default) {
                                RunGoldenVaultConformance(bundle, cacheDirectory, nowMillis).run()
                            }
                            state = when (result) {
                                is VaultResult.Ok -> ScreenState.Done(result.value)
                                is VaultResult.Err ->
                                    ScreenState.Failed("${result.error.code}: ${result.error.message}")
                            }
                        }
                    },
                    enabled = state !is ScreenState.Running,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Open the golden vault")
                }

                when (val current = state) {
                    ScreenState.Idle -> Unit
                    ScreenState.Running -> CircularProgressIndicator()

                    is ScreenState.Failed -> Text(
                        text = current.message,
                        color = FAIL_RED,
                        fontWeight = FontWeight.Bold,
                    )

                    is ScreenState.Done -> {
                        val report = current.report
                        Text(
                            text = report.verdict.describe(report.expectationName),
                            color = if (report.verdict is ConformanceVerdict.Pass) PASS_GREEN else FAIL_RED,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "Argon2id 64 MiB / t=3 / p=1 — ${report.derivationMillis} ms",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            text = report.renderedPack,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                        )
                    }
                }
            }
        }
    }
}

private val PASS_GREEN = Color(0xFF1B7F3B)
private val FAIL_RED = Color(0xFFB3261E)

private sealed interface ScreenState {
    data object Idle : ScreenState
    data object Running : ScreenState
    data class Done(val report: ConformanceReport) : ScreenState
    data class Failed(val message: String) : ScreenState
}
