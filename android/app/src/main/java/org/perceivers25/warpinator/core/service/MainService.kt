package org.perceivers25.warpinator.core.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.*
import android.net.ConnectivityManager.NetworkCallback
import android.net.wifi.WifiManager
import android.net.wifi.WifiManager.MulticastLock
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import org.perceivers25.warpinator.core.data.ServiceState
import org.perceivers25.warpinator.core.data.WarpinatorRepository
import org.perceivers25.warpinator.core.notification.WarpinatorNotificationManager
import org.perceivers25.warpinator.core.utils.Utils
import java.io.File
import java.util.Timer
import java.util.TimerTask
import javax.inject.Inject

@AndroidEntryPoint
class MainService : LifecycleService() {
    @Inject
    lateinit var repository: WarpinatorRepository

    @Inject
    lateinit var notificationManager: WarpinatorNotificationManager

    var runningTransfers: Int = 0
    var notificationMgr: NotificationManagerCompat? = null

    private var timer: Timer? = null
    private var logcatProcess: Process? = null
    private var lock: MulticastLock? = null
    private var connMgr: ConnectivityManager? = null
    private var networkCallback: NetworkCallback? = null
    private var apStateChangeReceiver: BroadcastReceiver? = null
    private var autoStopTask: TimerTask? = null

    override fun onBind(intent: Intent): IBinder {
        super.onBind(intent)
        return MainServiceBinder(this)
    }

    override fun onUnbind(intent: Intent?): Boolean {
        return false
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        super.onStartCommand(intent, flags, startId)
        notificationMgr = NotificationManagerCompat.from(this)

        // Start logging
        if (repository.prefs.debugLog) {
            logcatProcess = launchLogcat()
        }

        // Acquire multicast lock for mDNS
        acquireMulticastLock()

        // Server needs to load interface setting before this
        // repository.currentIPInfo = Utils.iPAddress(this, server.get().networkInterface) // TODO: man fix this
        Log.d(TAG, Utils.dumpInterfaces() ?: "No interfaces")

        // Sometimes fails. Maybe takes too long to get here?
        startForeground(
            WarpinatorNotificationManager.FOREGROUND_SERVICE_ID,
            notificationManager.createForegroundNotification(),
        )
        Log.v(TAG, "Entered foreground")

        repository.start()

        listenOnNetworkChanges()

        // Consume active transfers
        // lifecycleScope.launch {
        //     repository.remoteListState.collect { remotes ->
        //         val isTransferring = notificationManager.updateProgressNotification(remotes)
        //
        //         // Update local tracking for auto-stop logic
        //         runningTransfers = if (isTransferring) 1 else 0
        //     }
        // }

        // Notify the tile service that MainService just started
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            TileMainService.requestListeningState(this)
        }

        repository.updateServiceState(ServiceState.Ok)

        return START_STICKY
    }

    override fun onDestroy() {
        stopServer()
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        Log.d(TAG, "Task removed")
        if (runningTransfers == 0) // && autostop enabled
            autoStop()
        super.onTaskRemoved(rootIntent)
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        super.onTimeout(startId, fgsType)
        Log.e(
            TAG,
            "Service has run out of time and must be stopped (Android 15+)",
        )
        stopSelf()
    }

    private fun stopServer() {
        repository.updateServiceState(ServiceState.Stopped)


        repository.clearRemotes()

        notificationManager.cancelAll()

        lifecycleScope.launch {
            // TODO: stop server
        }

        if (connMgr != null && networkCallback != null) {
            try {
                connMgr?.unregisterNetworkCallback(networkCallback!!)
            } catch (e: Exception) {
                Log.e(TAG, "Error unregistering callback", e)
            }
        }

        if (apStateChangeReceiver != null) {
            try {
                unregisterReceiver(apStateChangeReceiver)
            } catch (e: Exception) {
                Log.e(TAG, "Error unregistering receiver", e)
            }
        }

        timer?.cancel()

        try {
            lock?.release()
        } catch (_: Exception) {
        }

        logcatProcess?.destroy()
    }

    private fun autoStop() {
        if (!isAutoStopEnabled) return
        Log.i(TAG, "Autostopping")
        stopSelf()
        autoStopTask = null
    }

    private fun launchLogcat(): Process? {
        val output = File(getExternalFilesDir(null), "latest.log")
        var process: Process? = null
        val cmd = "logcat -f ${output.absolutePath}\n"
        try {
            output.delete() // Delete original file
            process = Runtime.getRuntime().exec(cmd)
            Log.d(TAG, "---- Logcat started ----")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start logging to file", e)
        }
        return process
    }

    private fun listenOnNetworkChanges() {
        connMgr = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager

        val nr = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .addTransportType(NetworkCapabilities.TRANSPORT_ETHERNET)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED)
            .build()

        networkCallback = object : NetworkCallback() {
            override fun onAvailable(network: Network) {
                Log.d(TAG, "New network")
                repository.updateNetworkState { it.copy(isConnected = true) }
                onNetworkChanged()
            }

            override fun onLost(network: Network) {
                Log.d(TAG, "Network lost")
                repository.updateNetworkState { it.copy(isConnected = true) }
                onNetworkLost()
            }

            override fun onLinkPropertiesChanged(
                network: Network,
                linkProperties: LinkProperties,
            ) {
                Log.d(TAG, "Link properties changed")
                onNetworkChanged()
            }
        }

        apStateChangeReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val apState =
                    intent.getIntExtra(WifiManager.EXTRA_WIFI_STATE, 0)
                if (apState % 10 == WifiManager.WIFI_STATE_ENABLED) {
                    Log.d(TAG, "AP was enabled")
                    repository.updateNetworkState { it.copy(isHotspot = true) }
                    onNetworkChanged()
                } else if (apState % 10 == WifiManager.WIFI_STATE_DISABLED) {
                    Log.d(TAG, "AP was disabled")
                    repository.updateNetworkState { it.copy(isHotspot = true) }
                    onNetworkLost()
                }
            }
        }

        // Manually get state, some devices don't fire broadcast when registered
        repository.updateNetworkState { it.copy(isHotspot = isHotspotOn) }
        registerReceiver(
            apStateChangeReceiver,
            IntentFilter("android.net.wifi.WIFI_AP_STATE_CHANGED"),
        )

        networkCallback?.let {
            connMgr?.registerNetworkCallback(nr, it)
        }
    }

    private val isHotspotOn: Boolean
        get() {
            val manager =
                applicationContext.getSystemService(WIFI_SERVICE) as? WifiManager
                    ?: return false
            try {
                val method =
                    manager.javaClass.getDeclaredMethod("isWifiApEnabled")
                method.isAccessible = true
                return method.invoke(manager) as? Boolean ?: false
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get hotspot state", e)
            }
            return false
        }

    fun gotNetwork() = repository.networkState.value.isOnline

    private fun onNetworkLost() {
        // if (!gotNetwork()) repository.currentIPInfo =
        //     null // Rebind even if we reconnected to the same net
    }

    private fun onNetworkChanged() {
        // val newInfo = Utils.iPAddress(this, server.get().networkInterface)
        // if (newInfo == null) {
        //     Log.w(TAG, "Network changed, but we do not have an IP")
        //     repository.currentIPInfo = null
        //     return
        // }
        //
        // val newIP = newInfo.address
        // val oldIP = repository.currentIPInfo?.address
        //
        // if (newIP != oldIP) {
        //     Log.d(TAG, ":: Restarting. New IP: $newIP")
        //     repository.updateServiceState(ServiceState.NetworkChangeRestart)
        //     repository.currentIPInfo = newInfo
        //
        //
        //     // Restart server
        //     lifecycleScope.launch {
        //         server.get().stop()
        //
        //         if (authenticator.certException == null) {
        //             server.start()
        //         } else {
        //             Log.w(TAG, "No cert. Server not started.")
        //         }
        //     }
        // }
    }

    private fun acquireMulticastLock() {
        val wifi =
            applicationContext.getSystemService(WIFI_SERVICE) as? WifiManager
        if (wifi != null) {
            lock = wifi.createMulticastLock("WarpMDNSLock")
            lock?.setReferenceCounted(true)
            lock?.acquire()
            Log.d(TAG, "Multicast lock acquired")
        }
    }

    private val isAutoStopEnabled: Boolean
        get() = repository.prefs.autoStop

    companion object {
        private const val TAG = "SERVICE"

        const val ACTION_STOP: String = "StopSvc"
    }
}

