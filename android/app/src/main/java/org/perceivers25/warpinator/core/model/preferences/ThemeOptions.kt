package org.perceivers25.warpinator.core.model.preferences

import androidx.annotation.StringRes
import org.perceivers25.warpinator.R
import org.perceivers25.warpinator.core.system.PreferenceManager

/**
 * Represents the available application theme modes.
 */
enum class ThemeOptions(val key: String, @param:StringRes val label: Int) {
    SYSTEM_DEFAULT(
        PreferenceManager.VAL_THEME_DEFAULT,
        R.string.system_default_theme,
    ),
    LIGHT_THEME(
        PreferenceManager.VAL_THEME_LIGHT,
        R.string.light_theme,
    ),
    DARK_THEME(PreferenceManager.VAL_THEME_DARK, R.string.dark_theme);

    companion object {
        fun fromKey(key: String?): ThemeOptions {
            return entries.find { it.key == key } ?: SYSTEM_DEFAULT
        }
    }
}