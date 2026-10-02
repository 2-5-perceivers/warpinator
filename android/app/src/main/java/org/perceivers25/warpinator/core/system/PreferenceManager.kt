package org.perceivers25.warpinator.core.system

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import dagger.hilt.android.qualifiers.ApplicationContext
import org.perceivers25.warpinator.core.model.preferences.*
import javax.inject.Inject
import javax.inject.Singleton

enum class AutoAcceptValue {
    Nobody, Favourites, Everyone
}

@Singleton
class PreferenceManager @Inject constructor(
    @param:ApplicationContext val context: Context,
) {
    val prefs: SharedPreferences =
        PreferenceManager.getDefaultSharedPreferences(context)

    var favourites: Set<SavedFavourite> = setOf()
    var recentRemotes: List<RecentRemote> = listOf()

    // Simple Properties (Read-only accessors for sync access)
    var loadedPreferences = false
        private set

    val debugLog: Boolean get() = prefs.getBoolean(KEY_DEBUG_LOG, false)
    val autoStop: Boolean get() = prefs.getBoolean(KEY_AUTO_STOP, true)
    val serviceUuid: String? get() = prefs.getString(KEY_UUID, null)
    val displayName: String
        get() = prefs.getString(KEY_DISPLAY_NAME, DEFAULT_DISPLAY_NAME)
            ?: DEFAULT_DISPLAY_NAME
    val port: Int
        get() = prefs.getString(KEY_PORT, DEFAULT_PORT)?.toIntOrNull() ?: 42000
    val authPort: Int
        get() = prefs.getString(KEY_AUTH_PORT, DEFAULT_AUTH_PORT)?.toIntOrNull()
            ?: 42001
    val networkInterface: String
        get() = prefs.getString(
            KEY_NETWORK_INTERFACE,
            "AUTO",
        ) ?: "AUTO"
    val groupCode: String
        get() = prefs.getString(KEY_GROUP_CODE, DEFAULT_GROUP_CODE)
            ?: DEFAULT_GROUP_CODE
    val autoAccept: AutoAcceptValue
        get() = AutoAcceptValue.entries.getOrNull(
            prefs.getInt(
                KEY_AUTO_ACCEPT,
                0,
            ),
        ) ?: AutoAcceptValue.Nobody
    val useCompression: Boolean
        get() = prefs.getBoolean(
            KEY_USE_COMPRESSION,
            false,
        )
    val notifyIncoming: Boolean
        get() = prefs.getBoolean(
            KEY_NOTIFY_INCOMING,
            true,
        )
    val downloadDirUri: String? get() = prefs.getString(KEY_DOWNLOAD_DIR, null)
    val profilePicture: String?
        get() = prefs.getString(
            KEY_PROFILE_PICTURE,
            DEFAULT_PROFILE_PICTURE,
        )
    val theme: String
        get() = prefs.getString(KEY_THEME, VAL_THEME_DEFAULT)
            ?: VAL_THEME_DEFAULT
    val dynamicColors: Boolean
        get() = prefs.getBoolean(
            KEY_DYNAMIC_COLORS,
            true,
        )

    init {
        loadSettings()
    }

    fun loadSettings() {
        // Ensure defaults exist
        if (!prefs.contains(KEY_PROFILE_PICTURE)) {
            prefs.edit {
                putString(
                    KEY_PROFILE_PICTURE,
                    DEFAULT_PROFILE_PICTURE,
                )
            }
        }

        // Load Complex Data
        loadFavorites()
        loadRecentRemotes()

        loadedPreferences = true
    }

    // --- Favorites Logic ---

    private fun loadFavorites() {
        val json = prefs.getString(KEY_FAVORITES, null) ?: return
        favourites = savedFavouritesFromJson(json)
    }

    fun toggleFavorite(uuid: String): Boolean {
        var newStatus = false
        val current = favourites
        val newItem = SavedFavourite(uuid)
        val newSet = if (current.contains(newItem)) {
            newStatus = false

            current - newItem
        } else {
            newStatus = true

            current + newItem
        }
        saveFavorites(newSet)

        return newStatus
    }

    private fun saveFavorites(set: Set<SavedFavourite>) {
        favourites = set
        val json = set.toJson()
        prefs.edit {
            putString(KEY_FAVORITES, json)
        }
    }

    // --- Recent Remotes Logic ---

    private fun loadRecentRemotes() {
        val json = prefs.getString(KEY_RECENT_REMOTES, null) ?: return
        recentRemotes = recentRemotesFromJson(json)
    }

    fun addRecentRemote(host: String, hostname: String) {
        val newItem = RecentRemote(host, hostname)
        val current = recentRemotes.toMutableList()

        // Remove existing if present (to move to top)
        current.removeAll { it.host == host }
        current.add(0, newItem)

        if (current.size > 10) {
            current.removeAt(current.lastIndex)
        }

        saveRecentRemotes(current)
    }

    private fun saveRecentRemotes(list: List<RecentRemote>) {
        recentRemotes = list
        val json = list.toJson()


        prefs.edit {
            putString(KEY_RECENT_REMOTES, json)
        }
    }

    // --- Setters ---

    fun saveServiceUuid(uuid: String) = prefs.edit { putString(KEY_UUID, uuid) }

    fun setDisplayName(value: String) =
        prefs.edit { putString(KEY_DISPLAY_NAME, value) }

    fun setGroupCode(value: String) =
        prefs.edit { putString(KEY_GROUP_CODE, value) }

    fun setServerPort(value: String) = prefs.edit { putString(KEY_PORT, value) }

    fun setAuthPort(value: String) =
        prefs.edit { putString(KEY_AUTH_PORT, value) }

    fun setNetworkInterface(value: String) =
        prefs.edit { putString(KEY_NETWORK_INTERFACE, value) }

    fun setNotifyIncoming(value: Boolean) =
        prefs.edit { putBoolean(KEY_NOTIFY_INCOMING, value) }

    fun setAutoAccept(value: AutoAcceptValue) =
        prefs.edit { putInt(KEY_AUTO_ACCEPT, value.ordinal) }

    fun setUseCompression(value: Boolean) =
        prefs.edit { putBoolean(KEY_USE_COMPRESSION, value) }

    fun setAutoStop(value: Boolean) =
        prefs.edit { putBoolean(KEY_AUTO_STOP, value) }

    fun setDebugLog(value: Boolean) =
        prefs.edit { putBoolean(KEY_DEBUG_LOG, value) }

    fun setDirectory(uri: Uri) =
        prefs.edit { putString(KEY_DOWNLOAD_DIR, uri.toString()) }

    fun resetDirectory() = prefs.edit { remove(KEY_DOWNLOAD_DIR) }

    fun setProfilePictureKey(key: String) =
        prefs.edit { putString(KEY_PROFILE_PICTURE, key) }

    fun setTheme(value: ThemeOptions) {
        prefs.edit { putString(KEY_THEME, value.key) }
    }

    fun setDynamicColors(value: Boolean) =
        prefs.edit { putBoolean(KEY_DYNAMIC_COLORS, value) }

    companion object {
        const val KEY_UUID = "uuid"
        const val KEY_DISPLAY_NAME = "displayName"
        const val KEY_PROFILE_PICTURE = "profile"
        const val KEY_DOWNLOAD_DIR = "downloadDir"
        const val KEY_NOTIFY_INCOMING = "notifyIncoming"
        const val KEY_AUTO_ACCEPT = "autoAccept"
        const val KEY_USE_COMPRESSION = "useCompression"
        const val KEY_AUTO_STOP = "autoStop"
        const val KEY_DEBUG_LOG = "debugLog"
        const val KEY_GROUP_CODE = "groupCode"
        const val KEY_PORT = "port"
        const val KEY_AUTH_PORT = "authPort"
        const val KEY_NETWORK_INTERFACE = "networkInterface"
        const val KEY_THEME = "theme_setting"
        const val KEY_DYNAMIC_COLORS = "dynamicColors"

        const val KEY_FAVORITES = "favorites"
        const val KEY_RECENT_REMOTES = "recentRemotes"

        // Defaults
        const val DEFAULT_DISPLAY_NAME = "Android"
        const val DEFAULT_PORT = "42000"
        const val DEFAULT_AUTH_PORT = "42001"
        const val DEFAULT_GROUP_CODE = "Warpinator"
        const val DEFAULT_PROFILE_PICTURE = "0"
        const val DEFAULT_NETWORK_INTERFACE = "auto"

        // Theme Values
        const val VAL_THEME_DEFAULT = "sysDefault"
        const val VAL_THEME_LIGHT = "lightTheme"
        const val VAL_THEME_DARK = "darkTheme"

        // File/Folder Names
        const val DIR_NAME_WARPINATOR = "Warpinator"
        const val FILE_PROFILE_PIC = "profilePic.png"
    }
}