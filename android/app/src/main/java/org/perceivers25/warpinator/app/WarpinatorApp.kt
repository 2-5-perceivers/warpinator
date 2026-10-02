package org.perceivers25.warpinator.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.*
import androidx.navigation3.ui.NavDisplay
import kotlinx.serialization.Serializable
import org.perceivers25.warpinator.feature.about.AboutScreen
import org.perceivers25.warpinator.feature.home.HomeScreen
import org.perceivers25.warpinator.feature.settings.SettingsScreen

@Serializable
data object HomeRoute : NavKey

@Serializable
data class SettingsRoute(val pickDir: Boolean = false) : NavKey

@Serializable
data object AboutRoute : NavKey

class Navigator(val backStack: NavBackStack<NavKey>) {
    fun navigate(key: NavKey) {
        backStack.add(key)
    }

    fun goBack(): Boolean {
        if (backStack.size > 1) {
            backStack.removeLastOrNull()
            return true
        }
        return false
    }

    fun popToRoot() {
        while (backStack.size > 1) {
            backStack.removeLastOrNull()
        }
    }

    val currentKey: NavKey?
        get() = backStack.lastOrNull()
}

val LocalNavigator = staticCompositionLocalOf<Navigator?> {
    null
}

@Composable
fun WarpinatorApp(
    needsDirSetup: Boolean = false,
) {
    val backStack = rememberNavBackStack(HomeRoute)
    val navigator = remember(backStack) { Navigator(backStack) }
    var remoteTarget by remember { mutableStateOf<Pair<String, Boolean>?>(null) }

    LaunchedEffect(needsDirSetup) {
        if (needsDirSetup && navigator.currentKey !is SettingsRoute) {
            navigator.navigate(SettingsRoute(pickDir = true))
        }
    }

    Surface(color = MaterialTheme.colorScheme.surface) {
        CompositionLocalProvider(LocalNavigator provides navigator) {
            Box(Modifier.fillMaxSize()) {
                WarpinatorIntentHandler(
                    onOpenRemote = { uuid, openMessages ->
                        remoteTarget = Pair(uuid, openMessages)
                        if (navigator.currentKey != HomeRoute) {
                            navigator.popToRoot()
                        }
                    },
                )

                NavDisplay(
                    backStack = backStack,
                    onBack = { navigator.goBack() },
                    entryDecorators = listOf(
                        rememberSaveableStateHolderNavEntryDecorator(),
                        rememberViewModelStoreNavEntryDecorator(),
                    ),
                    entryProvider = entryProvider {
                        entry<HomeRoute> {
                            HomeScreen(
                                remoteTarget = remoteTarget,
                                onRemoteTargetConsumed = {
                                    remoteTarget = null
                                },
                            )
                        }

                        entry<SettingsRoute> { route ->
                            SettingsScreen(launchDirPicker = route.pickDir)
                        }

                        entry<AboutRoute> {
                            AboutScreen()
                        }
                    },
                )
            }
        }
    }
}
