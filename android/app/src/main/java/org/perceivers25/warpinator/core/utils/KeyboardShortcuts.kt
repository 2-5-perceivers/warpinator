package org.perceivers25.warpinator.core.utils

import androidx.compose.runtime.*
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.platform.LocalInspectionMode

typealias KeyShortcut = (KeyEvent) -> Boolean

class KeyShortcutDispatcher {
    private val handlers = mutableStateListOf<KeyShortcut>()

    fun register(handler: KeyShortcut) {
        handlers.add(handler)
    }

    fun unregister(handler: KeyShortcut) {
        handlers.remove(handler)
    }

    fun dispatch(event: KeyEvent): Boolean {
        // last registered wins (innermost screen takes priority)
        return handlers.reversed().any { it(event) }
    }
}

val LocalKeyShortcutDispatcher =
    staticCompositionLocalOf<KeyShortcutDispatcher> {
        error("No KeyShortcutDispatcher provided")
    }

@Composable
fun KeyboardShortcuts(
    enabled: Boolean = true,
    handler: (KeyEvent) -> Boolean,
) {
    if (LocalInspectionMode.current) return

    val dispatcher = LocalKeyShortcutDispatcher.current
    val currentHandler by rememberUpdatedState(handler)
    val currentEnabled by rememberUpdatedState(enabled)

    DisposableEffect(dispatcher) {
        val wrappedHandler: KeyShortcut = { event ->
            if (currentEnabled) currentHandler(event) else false
        }
        dispatcher.register(wrappedHandler)
        onDispose { dispatcher.unregister(wrappedHandler) }
    }
}