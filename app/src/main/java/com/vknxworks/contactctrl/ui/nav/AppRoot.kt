package com.vknxworks.contactctrl.ui.nav

import android.widget.Toast
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Contacts
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.vknxworks.contactctrl.Launch
import com.vknxworks.contactctrl.R
import com.vknxworks.contactctrl.container
import com.vknxworks.contactctrl.ui.common.ContactsPermissionGate
import com.vknxworks.contactctrl.ui.components.CtrlBottomBar
import com.vknxworks.contactctrl.ui.components.LocalBottomClearance
import com.vknxworks.contactctrl.ui.components.TabItem
import com.vknxworks.contactctrl.ui.contacts.AddToScreen
import com.vknxworks.contactctrl.ui.contacts.ContactsScreen
import com.vknxworks.contactctrl.ui.contacts.DetailScreen
import com.vknxworks.contactctrl.ui.contacts.EditScreen
import com.vknxworks.contactctrl.ui.contacts.FavouritesScreen
import com.vknxworks.contactctrl.ui.contacts.ImportScreen
import com.vknxworks.contactctrl.ui.settings.SettingsScreen
import com.vknxworks.contactctrl.ui.settings.YouScreen
import com.vknxworks.contactctrl.ui.theme.CtrlColors

@Composable
fun AppRoot() {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        MainScaffold()
    }
}

private data class TopDest(val route: Any, @StringRes val label: Int, val icon: ImageVector, val selectedIcon: ImageVector)

@Composable
private fun TopDest.item(): TabItem = TabItem(stringResource(label), icon, selectedIcon)

/** Only content tabs: Settings and About open from the profile picture. */
private val topDestinations = listOf(
    TopDest(ContactsRoute, R.string.tab_contacts, Icons.Outlined.Contacts, Icons.Filled.Contacts),
    TopDest(FavouritesRoute, R.string.tab_favourites, Icons.Outlined.StarBorder, Icons.Filled.Star),
)

@Composable
private fun MainScaffold() {
    val context = LocalContext.current
    val c = context.container
    val navController = rememberNavController()
    val navigator = remember(navController) { Navigator(navController) }
    val backStack by navController.currentBackStackEntryAsState()
    val dest = backStack?.destination
    val selectedIndex = topDestinations.indexOfFirst { top -> dest?.hierarchy?.any { it.hasRoute(top.route::class) } == true }

    fun selectTab(i: Int) {
        navController.navigate(topDestinations[i].route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    // What another app asked for: open a contact, add one, import a file.
    val pending by c.pending.collectAsStateWithLifecycle()
    LaunchedEffect(pending) {
        val launch = pending ?: return@LaunchedEffect
        c.pending.value = null
        when (launch) {
            is Launch.View, is Launch.Edit -> {
                val uri = if (launch is Launch.View) launch.uri else (launch as Launch.Edit).uri
                val found = c.contacts.resolve(uri)
                if (found == null) {
                    Toast.makeText(context, context.getString(R.string.contact_not_found), Toast.LENGTH_SHORT).show()
                } else if (launch is Launch.Edit) {
                    navigator.navigate(EditRoute(found.first, found.second))
                } else {
                    navigator.navigate(DetailRoute(found.first, found.second))
                }
            }
            is Launch.Insert -> {
                c.prefill = launch.draft
                navigator.navigate(EditRoute(prefill = true))
            }
            is Launch.InsertOrEdit -> {
                c.prefill = launch.draft
                navigator.navigate(AddToRoute)
            }
            is Launch.ShowOrCreate -> {
                val found = c.contacts.findByPhoneOrEmail(launch.value, launch.isEmail)
                if (found != null) navigator.navigate(DetailRoute(found.first, found.second))
                else {
                    c.prefill = launch.draft
                    navigator.navigate(EditRoute(prefill = true))
                }
            }
            is Launch.Import -> navigator.navigate(ImportRoute(launch.uri.toString()))
        }
    }

    CompositionLocalProvider(LocalNavigator provides navigator) {
        BoxWithConstraints(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            if (maxWidth >= 600.dp) {
                Row(Modifier.fillMaxSize()) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.background,
                        modifier = Modifier.fillMaxHeight().statusBarsPadding(),
                    ) {
                        Spacer(Modifier.weight(1f))
                        topDestinations.forEachIndexed { i, d ->
                            val top = d.item()
                            val selected = i == selectedIndex
                            NavigationRailItem(
                                selected = selected,
                                onClick = { selectTab(i) },
                                icon = { Icon(if (selected) top.selectedIcon else top.icon, contentDescription = top.label) },
                                label = { Text(top.label) },
                                colors = NavigationRailItemDefaults.colors(
                                    selectedIconColor = CtrlColors.OnAccent,
                                    selectedTextColor = CtrlColors.Text,
                                    indicatorColor = CtrlColors.Accent,
                                    unselectedIconColor = CtrlColors.Muted,
                                    unselectedTextColor = CtrlColors.Muted,
                                ),
                            )
                        }
                        Spacer(Modifier.weight(1f))
                    }
                    Box(Modifier.weight(1f).fillMaxHeight().navigationBarsPadding()) { AppNavHost(navController) }
                }
            } else {
                PhoneLayout(navController, showTabs = selectedIndex >= 0, selectedIndex = selectedIndex, onSelect = ::selectTab)
            }
        }
    }
}

@Composable
private fun PhoneLayout(navController: NavHostController, showTabs: Boolean, selectedIndex: Int, onSelect: (Int) -> Unit) {
    val density = LocalDensity.current
    var overlayHeight by remember { mutableStateOf(0.dp) }
    CompositionLocalProvider(LocalBottomClearance provides if (showTabs) overlayHeight else 0.dp) {
        Box(Modifier.fillMaxSize()) {
            AppNavHost(navController)
            AnimatedVisibility(
                visible = showTabs,
                enter = fadeIn() + slideInVertically { it / 2 },
                exit = fadeOut() + slideOutVertically { it / 2 },
                modifier = Modifier.align(Alignment.BottomCenter).onSizeChanged { overlayHeight = with(density) { it.height.toDp() } },
            ) {
                Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 20.dp, end = 20.dp, bottom = 28.dp)) {
                    CtrlBottomBar(items = topDestinations.map { it.item() }, selectedIndex = selectedIndex, onSelect = onSelect)
                }
            }
        }
    }
}

@Composable
private fun AppNavHost(navController: NavHostController) {
    val c = LocalContext.current.container
    NavHost(navController, startDestination = ContactsRoute) {
        composable<ContactsRoute> { Gate { ContactsScreen() } }
        composable<FavouritesRoute> { Gate { FavouritesScreen() } }
        composable<YouRoute> { YouScreen() }
        composable<SettingsRoute> { SettingsScreen() }
        composable<DetailRoute> { val r = it.toRoute<DetailRoute>(); Gate { DetailScreen(r.id, r.key) } }
        composable<EditRoute> { val r = it.toRoute<EditRoute>(); Gate { EditScreen(r.id, r.key, r.prefill) } }
        composable<AddToRoute> { Gate { AddToScreen() } }
        composable<ImportRoute> { val r = it.toRoute<ImportRoute>(); Gate { ImportScreen(r.uri) } }
    }
    LaunchedEffect(Unit) { c.contacts.start() }
}

/** Every contacts screen needs the contacts permission first. */
@Composable
private fun Gate(content: @Composable () -> Unit) {
    val c = LocalContext.current.container
    ContactsPermissionGate(onGranted = { c.contacts.start() }, content = content)
}
