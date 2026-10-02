package org.perceivers25.warpinator.feature.home.components

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.view.KeyEvent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import org.perceivers25.warpinator.R
import org.perceivers25.warpinator.app.AboutRoute
import org.perceivers25.warpinator.app.LocalNavigator
import org.perceivers25.warpinator.app.SettingsRoute
import org.perceivers25.warpinator.core.design.components.MenuAction
import org.perceivers25.warpinator.core.design.components.MenuGroup
import org.perceivers25.warpinator.core.design.components.MenuGroupsPopup
import org.perceivers25.warpinator.core.design.components.TooltipIconButton
import org.perceivers25.warpinator.core.service.MainService
import org.perceivers25.warpinator.feature.home.panes.CONNECTION_ISSUES_HELP_URL

@OptIn(
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalMaterial3Api::class,
)
@Composable
fun HomeMenu(
    initiallyExpanded: Boolean = false,
    onManualConnectionClick: () -> Unit,
    onRestart: () -> Unit,
    onSaveLog: (uri: Uri) -> Unit,
) {
    val uriHandler = LocalUriHandler.current

    val saveLocationPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(
            mimeType = "text/plain",
        ),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        onSaveLog(uri)
    }

    var menuOpen by rememberSaveable { mutableStateOf(initiallyExpanded) }
    val groupInteractionSource = remember { MutableInteractionSource() }

    val navigator = LocalNavigator.current
    val context = LocalContext.current

    val menuGroups = listOf(
        MenuGroup(
            listOf(
                MenuAction(
                    stringResource(R.string.manual_connection_label),
                    trailingIcon = Icons.Rounded.AddLink,
                    onClick = onManualConnectionClick,
                    shortcutKeyCode = KeyEvent.KEYCODE_K,
                    shortcutKeyCtrl = true,
                ),
                MenuAction(
                    "Restart",
                    trailingIcon = Icons.Rounded.RestartAlt,
                    onClick = onRestart,
                    shortcutKeyCode = KeyEvent.KEYCODE_R,
                    shortcutKeyCtrl = true,
                    shortcutKeyAlt = true,
                ),
                MenuAction(
                    stringResource(R.string.connection_issues_label),
                    trailingIcon = Icons.AutoMirrored.Rounded.MenuBook,
                    onClick = {
                        uriHandler.openUri(CONNECTION_ISSUES_HELP_URL)
                    },
                    shortcutKeyCode = KeyEvent.KEYCODE_F1,
                ),
            ),
        ),
        MenuGroup(
            listOf(
                MenuAction(
                    stringResource(R.string.settings_title),
                    trailingIcon = Icons.Rounded.Settings,
                    onClick = { navigator?.navigate(SettingsRoute()) },
                ),
                MenuAction(
                    stringResource(R.string.save_log_label),
                    trailingIcon = Icons.Rounded.Archive,
                    onClick = {
                        saveLocationPicker.launch("warpinator-log.txt")
                    },
                ),
                MenuAction(
                    stringResource(R.string.about_title),
                    trailingIcon = Icons.Outlined.Info,
                    onClick = { navigator?.navigate(AboutRoute) },
                ),
            ),
        ),
        MenuGroup(
            listOf(
                MenuAction(
                    stringResource(R.string.quit_label),
                    trailingIcon = Icons.Rounded.Close,
                    onClick = {
                        (context as? Activity)?.finish()
                        val intent = Intent(context, MainService::class.java)
                        context.stopService(intent)
                    },
                ),
            ),
        ),
    )

    Box(
        modifier = Modifier.wrapContentSize(Alignment.TopEnd),
    ) {
        TooltipIconButton(
            description = stringResource(R.string.open_menu_label),
            icon = Icons.Rounded.MoreVert,
            onClick = { menuOpen = true },
        )
        MenuGroupsPopup(
            menuGroups = menuGroups,
            groupInteractionSource = groupInteractionSource,
            menuOpen = menuOpen,
            onDismiss = { menuOpen = false },
        )
    }
}

@Preview(showBackground = true)
@Composable
fun HomeMenuPreview() {
    Scaffold { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues)) {
            HomeMenu(
                true,
                onManualConnectionClick = {},
                onRestart = {},
                onSaveLog = {},
            )
        }
    }
}