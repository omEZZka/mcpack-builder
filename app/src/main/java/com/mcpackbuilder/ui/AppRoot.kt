package com.mcpackbuilder.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Store
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.*
import com.mcpackbuilder.AppContainer
import com.mcpackbuilder.ui.builder.BuilderScreen
import com.mcpackbuilder.ui.catalog.CatalogScreen
import com.mcpackbuilder.ui.library.LibraryScreen
import com.mcpackbuilder.ui.theme.LocalStrings
import com.mcpackbuilder.ui.theme.ThemeScreen
import kotlinx.coroutines.launch

private object Routes {
    const val CATALOG = "catalog"
    const val BUILDER = "builder"
    const val LIBRARY = "library"
    const val SETTINGS = "settings"
}

@Composable
fun AppRoot(container: AppContainer) {
    val s = LocalStrings.current
    val nav = rememberNavController()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val showMessage: (String) -> Unit = { scope.launch { snackbar.showSnackbar(it) } }
    val tabs = listOf(
        Triple(Routes.CATALOG, s.navCatalog, Icons.Default.Store),
        Triple(Routes.BUILDER, s.navBuilder, Icons.Default.Build),
        Triple(Routes.LIBRARY, s.navLibrary, Icons.Default.Folder),
        Triple(Routes.SETTINGS, s.navSettings, Icons.Default.Settings),
    )
    val entry by nav.currentBackStackEntryAsState()
    val current = entry?.destination?.route

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            NavigationBar {
                tabs.forEach { (route, label, icon) ->
                    NavigationBarItem(
                        selected = current == route,
                        onClick = { nav.navigate(route) { popUpTo(nav.graph.findStartDestination().id) { saveState = true }; launchSingleTop = true; restoreState = true } },
                        icon = { Icon(icon, contentDescription = label) },
                        label = { Text(label) }
                    )
                }
            }
        }
    ) { padding ->
        NavHost(nav, startDestination = Routes.CATALOG, modifier = Modifier.padding(padding)) {
            composable(Routes.CATALOG) { CatalogScreen(container, showMessage) { nav.navigate(Routes.BUILDER) { popUpTo(nav.graph.findStartDestination().id) { saveState = true }; launchSingleTop = true; restoreState = true } } }
            composable(Routes.BUILDER) { BuilderScreen(container, showMessage) }
            composable(Routes.LIBRARY) { LibraryScreen(container, showMessage) }
            composable(Routes.SETTINGS) { ThemeScreen(container.theme) }
        }
    }
}
