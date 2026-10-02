package org.perceivers25.warpinator.feature.settings.state

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Environment
import androidx.core.graphics.scale
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.application
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.perceivers25.warpinator.core.model.preferences.ThemeOptions
import org.perceivers25.warpinator.core.model.ui.UiMessage
import org.perceivers25.warpinator.core.system.AutoAcceptValue
import org.perceivers25.warpinator.core.system.PreferenceManager
import org.perceivers25.warpinator.core.utils.Utils
import org.perceivers25.warpinator.feature.settings.messages.FailedToSaveProfilePicture
import org.perceivers25.warpinator.feature.settings.messages.NeedsRestartMessage
import org.perceivers25.warpinator.feature.settings.messages.PortOutOfBounds
import java.io.File
import javax.inject.Inject

/**
 * Represents the immutable UI state for the Settings screen.
 *
 * This state object aggregates all user-configurable preferences, including identity,
 * transfer rules, application behavior, network configuration, and visual themes.
 * It is exposed via a [kotlinx.coroutines.flow.StateFlow] in the [SettingsViewModel].
 *
 */
data class SettingsUiState(
    // Identity
    val displayName: String = PreferenceManager.DEFAULT_DISPLAY_NAME,
    val profilePictureKey: String = PreferenceManager.DEFAULT_PROFILE_PICTURE,
    val profileImageSignature: Long = 0,

    // Transfer
    val downloadDir: String = "",
    val downloadDirSummary: String = "",
    val canResetDir: Boolean = false,
    val notifyIncoming: Boolean = true,
    val autoAccept: AutoAcceptValue = AutoAcceptValue.Nobody,
    val useCompression: Boolean = false,

    // App Behavior / Boot
    val startOnBoot: Boolean = false,
    val autoStop: Boolean = true,
    val debugLog: Boolean = false,

    // Network
    val groupCode: String = PreferenceManager.DEFAULT_GROUP_CODE,
    val port: String = PreferenceManager.DEFAULT_PORT,
    val authPort: String = PreferenceManager.DEFAULT_AUTH_PORT,
    val networkInterface: String = PreferenceManager.DEFAULT_NETWORK_INTERFACE,
    val interfaceEntries: List<Pair<String, String>> = emptyList(),

    // Aspect
    val themeMode: ThemeOptions = ThemeOptions.SYSTEM_DEFAULT,
    val dynamicColors: Boolean = true,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    application: Application,
    private val preferenceManager: PreferenceManager,
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState = _uiState.asStateFlow()

    private val _uiMessages = Channel<UiMessage>(Channel.BUFFERED)
    val uiMessages = _uiMessages.receiveAsFlow()

    private val preferenceChangeListener =
        SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            loadSettings()
        }

    init {
        preferenceManager.prefs.registerOnSharedPreferenceChangeListener(
            preferenceChangeListener,
        )
        loadSettings()
        loadInterfaces()
    }

    override fun onCleared() {
        preferenceManager.prefs.unregisterOnSharedPreferenceChangeListener(
            preferenceChangeListener,
        )
        super.onCleared()
    }

    private fun loadSettings() {
        val savedDownloadPath = preferenceManager.downloadDirUri
        val isCustomDir = savedDownloadPath?.startsWith("content") ?: false

        // Logic for default directory summary
        val downloadPathSummary = if (isCustomDir) {
            savedDownloadPath.toUri().path ?: savedDownloadPath
        } else if (savedDownloadPath == null) {
            "Tap to set"
        } else {
            File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                PreferenceManager.DIR_NAME_WARPINATOR,
            ).absolutePath
        }

        _uiState.update { state ->
            state.copy(
                // Identity
                displayName = preferenceManager.displayName,
                profilePictureKey = preferenceManager.profilePicture
                    ?: PreferenceManager.DEFAULT_PROFILE_PICTURE,

                // Transfer
                downloadDir = savedDownloadPath ?: "",
                downloadDirSummary = downloadPathSummary,
                canResetDir = isCustomDir,
                notifyIncoming = preferenceManager.notifyIncoming,
                autoAccept = preferenceManager.autoAccept,
                useCompression = preferenceManager.useCompression,

                // Boot / App Behavior
                autoStop = preferenceManager.autoStop,
                debugLog = preferenceManager.debugLog,

                // Network
                groupCode = preferenceManager.groupCode,
                port = preferenceManager.port.toString(),
                authPort = preferenceManager.authPort.toString(),
                networkInterface = preferenceManager.networkInterface,

                // Aspect
                themeMode = ThemeOptions.fromKey(preferenceManager.theme),
                dynamicColors = preferenceManager.dynamicColors,
            )
        }
    }

    fun setDisplayName(value: String) {
        preferenceManager.setDisplayName(value)
    }

    fun setGroupCode(value: String) {
        preferenceManager.setGroupCode(value)
        viewModelScope.launch { _uiMessages.send(NeedsRestartMessage()) }
    }

    fun setServerPort(value: String) {
        setPortInternal(value) { preferenceManager.setServerPort(it) }
    }

    fun setAuthPort(value: String) {
        setPortInternal(value) { preferenceManager.setAuthPort(it) }
    }

    private fun setPortInternal(value: String, saveAction: (String) -> Unit) {
        val parsedPort = value.toIntOrNull()
        if (parsedPort == null || parsedPort !in 1024..65535) {
            viewModelScope.launch {
                _uiMessages.send(PortOutOfBounds())
            }
            return
        }
        saveAction(value)
        viewModelScope.launch { _uiMessages.send(NeedsRestartMessage()) }
    }

    fun setNetworkInterface(value: String) {
        preferenceManager.setNetworkInterface(value)
    }

    fun setNotifyIncoming(value: Boolean) {
        preferenceManager.setNotifyIncoming(value)
    }

    fun setAutoAccept(value: AutoAcceptValue) {
        preferenceManager.setAutoAccept(value)
    }

    fun setUseCompression(value: Boolean) {
        preferenceManager.setUseCompression(value)
    }

    fun setAutoStop(value: Boolean) {
        preferenceManager.setAutoStop(value)
    }

    fun setDebugLog(value: Boolean) {
        preferenceManager.setDebugLog(value)
        viewModelScope.launch { _uiMessages.send(NeedsRestartMessage()) }
    }

    fun setDirectory(uri: Uri) {
        preferenceManager.setDirectory(uri)
    }

    fun resetDirectory() {
        preferenceManager.resetDirectory()
    }

    fun setProfilePicture(key: String) {
        _uiState.update { it.copy(profileImageSignature = 0) }
        preferenceManager.setProfilePictureKey(key)
    }

    fun updateTheme(value: ThemeOptions) {
        preferenceManager.setTheme(value)
    }

    fun setUseDynamicColors(value: Boolean) {
        preferenceManager.setDynamicColors(value)
    }

    private fun loadInterfaces() {
        viewModelScope.launch(Dispatchers.IO) {
            val networkInterfaceNames = Utils.networkInterfaces
                ?: arrayOf("Failed to get network interfaces")
            val interfaceDropdownEntries = mutableListOf<Pair<String, String>>()

            interfaceDropdownEntries.add("Auto" to PreferenceManager.DEFAULT_NETWORK_INTERFACE)

            for (interfaceName in networkInterfaceNames) {
                if (interfaceName == null) continue
                var interfaceDisplayLabel = interfaceName
                try {
                    val ip = Utils.getIPForIfaceName(interfaceName)
                    if (ip != null) {
                        interfaceDisplayLabel += " (${ip.address.hostAddress} /${ip.prefixLength})"
                    }
                } catch (_: Exception) { /* Ignored */
                }
                interfaceDropdownEntries.add(interfaceDisplayLabel to interfaceName)
            }

            _uiState.update { it.copy(interfaceEntries = interfaceDropdownEntries) }
        }
    }

    fun handleCustomProfilePicture(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                application.contentResolver.openInputStream(uri)
                    ?.use { inputStream ->
                        val originalBitmap =
                            BitmapFactory.decodeStream(inputStream)
                                ?: return@use

                        val maxDimension = 512
                        val (outW, outH) = if (originalBitmap.width > originalBitmap.height) {
                            maxDimension to (originalBitmap.height * maxDimension) / originalBitmap.width
                        } else {
                            (originalBitmap.width * maxDimension) / originalBitmap.height to maxDimension
                        }

                        // Create scaled bitmap
                        val scaledBitmap = originalBitmap.scale(outW, outH)

                        // Save to internal storage
                        application.openFileOutput(
                            PreferenceManager.FILE_PROFILE_PIC,
                            Context.MODE_PRIVATE,
                        ).use { os ->
                            scaledBitmap.compress(
                                Bitmap.CompressFormat.PNG,
                                100,
                                os,
                            )
                        }

                        _uiState.update {
                            it.copy(
                                // Update timestamp to force reload
                                profileImageSignature = System.currentTimeMillis(),
                            )
                        }
                    }
            } catch (e: Exception) {
                e.printStackTrace()
                viewModelScope.launch {
                    _uiMessages.send(
                        FailedToSaveProfilePicture(e),
                    )
                }
            }
        }
    }
}