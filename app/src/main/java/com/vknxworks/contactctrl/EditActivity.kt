package com.vknxworks.contactctrl

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.vknxworks.contactctrl.ui.common.ContactsPermissionGate
import com.vknxworks.contactctrl.ui.contacts.AddToScreen
import com.vknxworks.contactctrl.ui.contacts.EditScreen
import com.vknxworks.contactctrl.ui.nav.AddToRoute
import com.vknxworks.contactctrl.ui.nav.EditRoute
import com.vknxworks.contactctrl.ui.nav.LocalNavigator
import com.vknxworks.contactctrl.ui.nav.Navigator
import com.vknxworks.contactctrl.ui.theme.CtrlColors

/**
 * "Add to contacts" and "Edit contact" for other apps. It returns the saved contact to the app that asked,
 * then closes, so you end up back where you were.
 */
class EditActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val launch = Launch.from(intent)
        if (launch == null || launch !is Launch.Insert && launch !is Launch.InsertOrEdit && launch !is Launch.Edit) {
            finish()
            return
        }
        setContent {
            CtrlWindow(this) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    ContactsPermissionGate(onGranted = { container.contacts.start() }) {
                        EditFlow(launch, firstStart = savedInstanceState == null)
                    }
                }
            }
        }
    }

    /** Closes with the saved contact (or nothing, when cancelled). */
    fun done(saved: Uri?) {
        if (saved != null) {
            setResult(RESULT_OK, Intent().setData(saved).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
        } else {
            setResult(RESULT_CANCELED)
        }
        finish()
    }
}

@Composable
private fun EditFlow(launch: Launch, firstStart: Boolean) {
    val context = LocalContext.current
    val c = context.container
    val activity = context as EditActivity
    val navController = rememberNavController()
    val navigator = remember(navController) { EditNavigator(navController) { activity.done(null) } }
    var start by remember { mutableStateOf<Any?>(null) }

    LaunchedEffect(Unit) {
        c.contacts.start()
        start = when (launch) {
            is Launch.Insert -> { if (firstStart) c.prefill = launch.draft; EditRoute(prefill = true) }
            is Launch.InsertOrEdit -> { if (firstStart) c.prefill = launch.draft; AddToRoute }
            is Launch.Edit -> {
                val found = c.contacts.resolve(launch.uri)
                if (found == null) {
                    Toast.makeText(context, context.getString(R.string.contact_not_found), Toast.LENGTH_SHORT).show()
                    activity.done(null)
                    null
                } else EditRoute(found.first, found.second)
            }
            else -> null
        }
    }

    val s = start
    if (s == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = CtrlColors.AccentText) }
        return
    }
    CompositionLocalProvider(LocalNavigator provides navigator) {
        NavHost(navController, startDestination = s) {
            composable<EditRoute> { val r = it.toRoute<EditRoute>(); EditScreen(r.id, r.key, r.prefill) }
            composable<AddToRoute> { AddToScreen() }
        }
    }
}

/** Back on the first screen closes the activity instead of leaving an empty screen. */
private class EditNavigator(nav: androidx.navigation.NavHostController, private val onExit: () -> Unit) : Navigator(nav) {
    override fun back() {
        if (nav.previousBackStackEntry == null) onExit() else nav.popBackStack()
    }
}
