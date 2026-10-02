package org.perceivers25.warpinator.app

import android.app.Application
import android.content.Intent
import android.util.Log
import androidx.preference.PreferenceManager
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class WarpinatorApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        // Clear old persisted URI permissions, keeping only:
        // - the profile picture URI
        // - the selected download directory URI
        val sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this)
        val picture: String = sharedPrefs.getString("profile", "0")!!
        val downloadDir: String? = sharedPrefs.getString("downloadDir", null)

        for (u in contentResolver.persistedUriPermissions) {
            val uriStr = u.uri.toString()
            if (uriStr == picture || uriStr == downloadDir) {
                Log.v(TAG, "keeping permission for $u")
                continue
            }
            Log.v(TAG, "releasing uri permission $u")
            contentResolver.releasePersistableUriPermission(
                u.uri, Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
    }

    companion object {
        const val TAG: String = "APP"
    }
}
