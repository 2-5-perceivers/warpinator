package org.perceivers25.warpinator.core.system

import android.annotation.SuppressLint
import android.content.Context
import android.os.PowerManager
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.decrementAndFetch
import kotlin.concurrent.atomics.incrementAndFetch

@Singleton
class WarpinatorPowerManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : org.perceivers25.warpinator.PowerManager {
    private var wakeLock: PowerManager.WakeLock? = null

    @OptIn(ExperimentalAtomicApi::class)
    private var lockCount: AtomicInt = AtomicInt(0)

    init {
        val powerManager =
            context.getSystemService(Context.POWER_SERVICE) as PowerManager
        // PARTIAL_WAKE_LOCK ensures the CPU runs, but screen can turn off.
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "$TAG::WakeLock",
        ).apply {
            // Important: Reference counting allows multiple transfers to "hold" the lock.
            // The CPU stays awake until the lock is released as many times as it was acquired.
            setReferenceCounted(true)
        }
    }

    // Suppress the timeout as a transfer might take longer than that, and I trust the lock guards in the native code to work
    @SuppressLint("WakelockTimeout")
    @OptIn(ExperimentalAtomicApi::class)
    override fun acquireWakeLock() {
        try {
            // 10 minute timeout as a safety net per acquisition in case of logic bugs
            wakeLock?.acquire()
            val lc = lockCount.incrementAndFetch()
            Log.d(TAG, "WakeLock acquired. Held count: $lc")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to acquire WakeLock", e)
        }
    }

    @OptIn(ExperimentalAtomicApi::class)
    override fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
                val lc = lockCount.decrementAndFetch()
                Log.d(TAG, "WakeLock released. Held count: $lc")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to release WakeLock", e)
        }
    }

    companion object {
        private const val TAG = "WarpinatorPowerManager"
    }
}