package com.mcpackbuilder.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.mcpackbuilder.AppContainer
import com.mcpackbuilder.ui.builder.BuilderScreen
import com.mcpackbuilder.ui.catalog.CatalogScreen
import com.mcpackbuilder.ui.library.LibraryScreen
import kotlinx.coroutines.launch

private object Routes {
    const val CATALOG = "catalog"
    const val BUILDER = "builder"
    const val LIBRARY = "library"
}

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab(Routes.CATALOG, "Каталог", Icons.Default.Search),
    Tab(Routes.BUILDER, "Сборка", Icons.Default.Build),
    Tab(Routes.LIBRARY, "Мои сборки", Icons.Default.Folder)
)

@Composable
fun AppRoot(container: AppContainer) {
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val selectedMods by container.selection.mods.collectAsStateWithLifecycle()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: Routes.CATALOG

    val showMessage: (String) -> Unit = { message ->
        scope.launch { snackbarHostState.showSnackbar(message) }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            NavigationBar {
                tabs.forEach { tab ->
                    NavigationBarItem(
                        selected = currentRoute == tab.route,
                        onClick = {
                            if (currentRoute == tab.route) return@NavigationBarItem
                            navController.navigate(tab.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = {
                            if (tab.route == Routes.BUILDER && selectedMods.isNotEmpty()) {
                                BadgedBox(badge = { Badge { Text(selectedMods.size.toString()) } }) {
                                    Icon(tab.icon, contentDescription = tab.label)
                                }
                            } else {
                                Icon(tab.icon, contentDescription = tab.label)
                            }
                        },
                        label = { Text(tab.label) }
                    )
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.CATALOG,
            modifier = Modifier.padding(padding)
        ) {
            composable(Routes.CATALOG) {
                CatalogScreen(container, showMessage)
            }
            composable(Routes.BUILDER) {
                BuilderScreen(container, showMessage)
            }
            composable(Routes.LIBRARY) {
                LibraryScreen(container, showMessage)
            }
        }
    }
}
