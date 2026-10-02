package org.perceivers25.warpinator.core.data

import android.content.SharedPreferences
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import org.perceivers25.warpinator.core.system.PreferenceManager
import javax.inject.Inject

@HiltViewModel
class ThemeViewModel @Inject constructor(
    private val preferenceManager: PreferenceManager,
) : ViewModel() {
    private val _theme = mutableStateOf(preferenceManager.theme)
    val theme: State<String> = _theme
    val themeLightKey = PreferenceManager.VAL_THEME_LIGHT
    val themeDarkKey = PreferenceManager.VAL_THEME_DARK

    private val _dynamicColors = mutableStateOf(preferenceManager.dynamicColors)
    val dynamicColors: State<Boolean> = _dynamicColors

    private val preferenceListener =
        SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            when (key) {
                PreferenceManager.KEY_THEME -> {
                    _theme.value = preferenceManager.theme
                }

                PreferenceManager.KEY_DYNAMIC_COLORS -> {
                    _dynamicColors.value = preferenceManager.dynamicColors
                }
            }
        }

    init {
        preferenceManager.prefs.registerOnSharedPreferenceChangeListener(
            preferenceListener,
        )
    }

    override fun onCleared() {
        super.onCleared()
        preferenceManager.prefs.unregisterOnSharedPreferenceChangeListener(
            preferenceListener,
        )
    }
}