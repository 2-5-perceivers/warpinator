package org.perceivers25.warpinator.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.input.key.KeyEvent
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.AndroidEntryPoint
import org.perceivers25.warpinator.core.data.ThemeViewModel
import org.perceivers25.warpinator.core.design.theme.WarpinatorTheme
import org.perceivers25.warpinator.core.service.MainService
import org.perceivers25.warpinator.core.utils.KeyShortcutDispatcher
import org.perceivers25.warpinator.core.utils.LocalKeyShortcutDispatcher
import org.perceivers25.warpinator.core.utils.Utils

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    val keyShortcutDispatcher = KeyShortcutDispatcher()

    override fun onKeyDown(
        keyCode: Int,
        event: android.view.KeyEvent?,
    ): Boolean {
        if (event != null && keyShortcutDispatcher.dispatch(KeyEvent(event))) return true
        return super.onKeyDown(keyCode, event)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    3,
                )
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.CINNAMON_BUN) {
            if (checkSelfPermission(Manifest.permission.ACCESS_LOCAL_NETWORK) != PackageManager.PERMISSION_GRANTED) {

                requestPermissions(
                    arrayOf(Manifest.permission.ACCESS_LOCAL_NETWORK),
                    4,
                )
            }
        }

        if (!Utils.isMyServiceRunning(this, MainService::class.java)) {
            startService(Intent(this, MainService::class.java))
        }

        setContent {
            val navController = rememberNavController()

            val themeViewModel: ThemeViewModel = hiltViewModel()
            val theme by themeViewModel.theme
            val useDynamicColors by themeViewModel.dynamicColors

            val isDark = when (theme) {
                themeViewModel.themeLightKey -> false
                themeViewModel.themeDarkKey -> true
                else -> isSystemInDarkTheme()
            }

            DisposableEffect(isDark) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(
                        Color.TRANSPARENT,
                        Color.TRANSPARENT,
                    ) { isDark },
                    navigationBarStyle = SystemBarStyle.auto(
                        Color.TRANSPARENT,
                        Color.TRANSPARENT,
                    ) { isDark },
                )
                onDispose {}
            }

            WarpinatorTheme(
                darkTheme = isDark,
                dynamicColor = useDynamicColors,
            ) {
                CompositionLocalProvider(
                    LocalKeyShortcutDispatcher provides keyShortcutDispatcher,
                ) {
                    WarpinatorApp(navController)
                }
            }
        }
    }
}
