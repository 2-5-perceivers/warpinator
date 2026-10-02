package org.perceivers25.warpinator.app

import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import org.perceivers25.warpinator.feature.about.AboutScreen
import org.perceivers25.warpinator.feature.home.HomeScreen
import org.perceivers25.warpinator.feature.settings.SettingsScreen

val LocalNavController = staticCompositionLocalOf<NavController?> {
    null
}

@Composable
fun WarpinatorApp(
    navController: NavHostController,
    needsDirSetup: Boolean = false,
) {
    var remoteTarget by remember { mutableStateOf<Pair<String, Boolean>?>(null) }

    LaunchedEffect(needsDirSetup) {
        if (needsDirSetup) {
            navController.navigate("settings?pickDir=true") {
                launchSingleTop = true
            }
        }
    }

    Surface(color = MaterialTheme.colorScheme.surface) {
        CompositionLocalProvider(LocalNavController provides navController) {
            Box(Modifier.fillMaxSize()) {
                WarpinatorIntentHandler(
                    onOpenRemote = { uuid, openMessages ->
                        remoteTarget = Pair(uuid, openMessages)

                        // If we are currently on Settings or About, pop back to Home
                        if (navController.currentDestination?.route != "home") {
                            navController.navigate("home") {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    },
                )

                NavHost(
                    navController = navController, startDestination = "home",

                    enterTransition = {
                        slideInHorizontally(initialOffsetX = { it })
                    },
                    exitTransition = {
                        scaleOut(targetScale = 0.9f)
                    },
                    popEnterTransition = {
                        scaleIn(initialScale = 0.9f)
                    },
                    popExitTransition = {
                        slideOutHorizontally(targetOffsetX = { it })
                    },
                ) {
                    composable("home") {
                        HomeScreen(
                            remoteTarget = remoteTarget,
                            onRemoteTargetConsumed = { remoteTarget = null },
                        )
                    }

                    composable(
                        route = "settings?pickDir={pickDir}",
                        arguments = listOf(
                            navArgument("pickDir") {
                                type = NavType.BoolType
                                defaultValue = false
                            },
                        ),
                    ) { backStackEntry ->
                        val launchPicker =
                            backStackEntry.arguments?.getBoolean("pickDir")
                                ?: false
                        SettingsScreen(launchDirPicker = launchPicker)
                    }

                    composable("about") {
                        AboutScreen()
                    }
                }
            }
        }
    }
}
