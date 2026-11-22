package com.vknxworks.contactctrl

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vknxworks.contactctrl.ui.lock.AppLockGate
import com.vknxworks.contactctrl.ui.lock.SensitiveWindowEffect
import com.vknxworks.contactctrl.ui.lock.lockCovering
import com.vknxworks.contactctrl.ui.nav.AppRoot
import com.vknxworks.contactctrl.ui.theme.ContactCtrlTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) take(intent)
        secureBeforeFirstFrame()
        setContent { CtrlWindow(this) { AppRoot() } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        take(intent)
    }

    private fun take(intent: Intent?) {
        // A .vcf file handed over by ImportActivity (after Android checked the sender could read it).
        val expected = container.pendingImport
        val shared = intent?.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.uri
        if (expected != null && shared == expected) {
            container.pendingImport = null
            container.pending.value = Launch.Import(expected)
            return
        }
        Launch.from(intent)?.let { container.pending.value = it }
    }
}

/**
 * Keeps the window out of screenshots and screen recordings from its very first frame when the app opens locked
 * (or "Hide in screenshots" is already known to be on). [CtrlWindow] keeps the flag up to date after that, with
 * the same rule. Call before setContent.
 */
fun ComponentActivity.secureBeforeFirstFrame() {
    val c = container
    // Until the saved settings are read, "Hide in screenshots" counts as on (it may be).
    if (!c.settings.loaded.value || c.settings.settings.value.hideScreenshots || lockCovering(c.vault)) {
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }
}

/**
 * The Ctrl window: follows the phone's dark theme, applies "Hide in screenshots" / "Hide in recent apps",
 * and shows nothing of the app while the app lock is locked.
 */
@Composable
fun CtrlWindow(activity: ComponentActivity, content: @Composable () -> Unit) {
    val c = activity.container
    val dark = isSystemInDarkTheme()
    DisposableEffect(dark) {
        val bars = if (dark) SystemBarStyle.dark(DARK_BAR) else SystemBarStyle.light(LIGHT_BAR, DARK_BAR)
        activity.enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
        onDispose { }
    }
    // Read before the settings: once this is true, the settings read below are the saved ones.
    val loaded by c.settings.loaded.collectAsStateWithLifecycle()
    val settings by c.settings.settings.collectAsStateWithLifecycle()
    // "Hide in screenshots": FLAG_SECURE (also blanks the Recents preview). Always on while a lock or PIN screen
    // shows, and until the saved settings are known.
    val secure = !loaded || settings.hideScreenshots || lockCovering(c.vault)
    // "Hide in recent apps": a blank Recents card. Always with the app lock on.
    val blankRecents = settings.hideRecents || c.vault.enabled
    LaunchedEffect(secure, blankRecents) {
        if (secure) activity.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        else activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        activity.setRecentsScreenshotEnabled(!blankRecents)
    }
    // While a lock or PIN screen covers the app: no other apps' overlays on top, and hidden from apps that
    // misuse accessibility (real screen readers still work). The rest of the app is unaffected.
    SensitiveWindowEffect(enabled = lockCovering(c.vault), sensitiveData = true)
    ContactCtrlTheme(dark) {
        AppLockGate { content() }
    }
}

/** The Ctrl / Signal backgrounds behind the status and navigation bars: dark #08080A, light #F5F5F1. */
private val DARK_BAR = android.graphics.Color.rgb(8, 8, 10)
private val LIGHT_BAR = android.graphics.Color.rgb(245, 245, 241)
