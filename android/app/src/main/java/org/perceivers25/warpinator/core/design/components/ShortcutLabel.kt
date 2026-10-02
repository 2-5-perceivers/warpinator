package org.perceivers25.warpinator.core.design.components

import android.content.res.Configuration
import android.view.KeyCharacterMap
import android.view.KeyEvent
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.TextStyle

@Composable
fun rememberHasPhysicalKeyboard(): Boolean {
    val configuration = LocalConfiguration.current
    return remember {
        configuration.keyboard == Configuration.KEYBOARD_QWERTY
    }
}

private fun getKeyLabel(keyCode: Int): String {
    val deviceId = KeyCharacterMap.VIRTUAL_KEYBOARD
    val map = KeyCharacterMap.load(deviceId)
    val char = map.get(keyCode, 0)
    return if (char != 0) char.toChar().uppercaseChar().toString() else ""
}

private fun buildString(
    keyCode: Int,
    ctrl: Boolean = false,
    shift: Boolean = false,
    alt: Boolean = false,
): String {
    return buildString {
        if (ctrl) append("Ctrl+")
        if (shift) append("Shift+")
        if (alt) append("Alt+")
        when (keyCode) {
            KeyEvent.KEYCODE_DEL -> append("Del")
            KeyEvent.KEYCODE_ENTER -> append("Enter")
            KeyEvent.KEYCODE_F1 -> append("F1")
            else -> append(getKeyLabel(keyCode))
        }
    }
}

@Composable
fun rememberShortcutLabelText(
    keyCode: Int,
    ctrl: Boolean = false,
    shift: Boolean = false,
    alt: Boolean = false,
    text: String,
): String {
    val hasKeyboard = rememberHasPhysicalKeyboard()
    if (!hasKeyboard) return text

    return "$text (${buildString(keyCode, ctrl, shift, alt)})"
}

@Composable
fun ShortcutLabel(
    keyCode: Int,
    ctrl: Boolean = false,
    shift: Boolean = false,
    alt: Boolean = false,
    style: TextStyle = MaterialTheme.typography.labelSmall,
    color: Color = LocalContentColor.current.copy(alpha = 0.6f),
) {

    val hasKeyboard = rememberHasPhysicalKeyboard()
    if (!hasKeyboard) return

    val label = buildString(keyCode, ctrl, shift, alt)

    Text(
        text = label,
        style = style,
        color = color,
    )
}