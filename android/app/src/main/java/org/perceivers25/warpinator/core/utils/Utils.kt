package org.perceivers25.warpinator.core.utils

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Uri
import android.net.wifi.WifiManager
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.provider.Settings
import android.text.TextUtils
import android.util.Log
import androidx.core.net.toUri
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.SocketException
import java.net.URLDecoder
import java.util.Collections
import java.util.Random

object Utils {
    private const val TAG = "Utils"

    fun getDeviceName(context: Context): String {
        var name: String? = null
        try {
            name = Settings.Global.getString(
                context.contentResolver, Settings.Global.DEVICE_NAME,
            )
            if (name == null) name = Settings.Secure.getString(
                context.contentResolver, "bluetooth_name",
            )
        } catch (_: Exception) {
        }
        if (name == null) {
            Log.v(
                TAG, "Could not get device name - using default",
            )
            name = "Android Phone"
        }
        return name
    }

    @get:Throws(SocketException::class)
    val activeIface: NetworkInterface?
        get() {
            val nis: MutableList<NetworkInterface> =
                Collections.list(NetworkInterface.getNetworkInterfaces())
            // prioritize wlan(...) interfaces and deprioritize tun(...) which can be leftover from VPN apps
            Collections.sort(
                nis,
                Comparator sort@{ i1: NetworkInterface?, i2: NetworkInterface? ->
                    val i1Name = i1!!.displayName
                    val i2Name = i2!!.displayName
                    if (i1Name.contains("wlan") || i2Name.contains("tun")) {
                        return@sort -1
                    } else if (i1Name.contains("tun") || i2Name.contains("wlan")) {
                        return@sort 1
                    }
                    0
                },
            )

            for (ni in nis) {
                if ((!ni.isLoopback) && ni.isUp) {
                    val name = ni.displayName
                    if (name.contains("dummy") || name.contains("rmnet") || name.contains(
                            "ifb",
                        )
                    ) continue
                    if (getIPForIface(ni) == null)  // Skip ifaces with no IPv4 address
                        continue
                    Log.d(
                        TAG, "Selected interface: " + ni.displayName,
                    )
                    return ni
                }
            }
            return null
        }

    val wifiInterface: String
        get() {
            var iface: String? = null
            try {
                val m = Class.forName("android.os.SystemProperties")
                    .getMethod("get", String::class.java)
                iface = m.invoke(null, "wifi.interface") as String?
            } catch (ignored: Throwable) {
            }
            if (iface == null || iface.isEmpty()) iface = "wlan0"
            return iface
        }

    val networkInterfaces: Array<String?>?
        get() {
            val nisNames = ArrayList<String?>()
            try {
                val nis = NetworkInterface.getNetworkInterfaces()
                while (nis.hasMoreElements()) {
                    val ni = nis.nextElement()
                    if (ni.isUp) {
                        nisNames.add(ni.displayName)
                    }
                }
            } catch (e: SocketException) {
                Log.e(
                    TAG, "Could not get network interfaces", e,
                )
                return null
            }
            return nisNames.toTypedArray<String?>()
        }

    fun dumpInterfaces(): String? {
        val nis: Array<String?> =
            networkInterfaces ?: return "Failed to get network interfaces"
        return TextUtils.join("\n", nis)
    }

    @Throws(SocketException::class)
    fun getIPForIfaceName(ifaceName: String?): IPInfo? {
        val nis = NetworkInterface.getNetworkInterfaces()
        var ni: NetworkInterface
        while (nis.hasMoreElements()) {
            ni = nis.nextElement()
            if (ni.displayName == ifaceName) {
                return getIPForIface(ni)
            }
        }
        return null
    }

    fun getIPForIface(ni: NetworkInterface): IPInfo? {
        for (ia in ni.interfaceAddresses) {
            // filter for ipv4/ipv6
            if (ia.address.address.size == 4) {
                // 4 for ipv4, 16 for ipv6
                return IPInfo(
                    (ia.address as Inet4Address?)!!,
                    ia.networkPrefixLength.toInt(),
                )
            }
        }
        return null
    }

    @SuppressLint("Range")
    fun getNameFromUri(ctx: Context, uri: Uri): String? {
        var result: String? = null
        try {
            if ("content" == uri.scheme) {
                val cursor =
                    ctx.contentResolver.query(uri, null, null, null, null)
                if (cursor != null && cursor.moveToFirst()) {
                    result =
                        cursor.getString(cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME))
                    cursor.close()
                }
            }
            if (result == null) {
                val parts: Array<String?> = URLDecoder.decode(uri.toString())
                    .split("/".toRegex())
                    .dropLastWhile { it.isEmpty() }
                    .toTypedArray()
                return parts[parts.size - 1]
            }
        } catch (_: Exception) {
            return null
        }
        return result
    }

    fun getChildUri(treeUri: Uri?, path: String?): Uri {
        val rootID = DocumentsContract.getTreeDocumentId(treeUri)
        val docID = "$rootID/$path"
        return DocumentsContract.buildDocumentUriUsingTree(treeUri, docID)
    }

    fun isMyServiceRunning(ctx: Context, serviceClass: Class<*>): Boolean {
        val manager =
            checkNotNull(ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager)
        for (service in manager.getRunningServices(Int.MAX_VALUE)) {
            if (serviceClass.name == service.service.className) {
                return true
            }
        }
        return false
    }

    fun isConnectedToWiFiOrEthernet(ctx: Context): Boolean {
        val connManager =
            checkNotNull(ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager)
        val wifi = connManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI)
        val ethernet =
            connManager.getNetworkInfo(ConnectivityManager.TYPE_ETHERNET)
        return (wifi != null && wifi.isConnected) || (ethernet != null && ethernet.isConnected)
    }

    fun isHotspotOn(ctx: Context): Boolean {
        val manager = checkNotNull(
            ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager,
        )
        try {
            val method = manager.javaClass.getDeclaredMethod("isWifiApEnabled")
            method.isAccessible =
                true // in the case of visibility change in future APIs
            return (method.invoke(manager) as Boolean?)!!
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get hotspot state", e)
        }

        return false
    }

    @JvmStatic
    fun generateServiceName(context: Context): String {
        return getDeviceName(context).uppercase()
            .replace(" ", "") + "-" + getRandomHexString(6)
    }

    fun getRandomHexString(len: Int): String {
        val buf = CharArray(len)
        val random = Random()
        for (idx in buf.indices) buf[idx] =
            HEX_ARRAY[random.nextInt(HEX_ARRAY.size)]
        return String(buf)
    }

    fun openUrl(context: Context, url: String?) {
        val intent = Intent(Intent.ACTION_VIEW, url?.toUri())
        context.startActivity(intent)
    }

    // FOR DEBUG PURPOSES
    private val HEX_ARRAY = "0123456789ABCDEF".toCharArray()

    class IPInfo internal constructor(
        @JvmField var address: Inet4Address, @JvmField var prefixLength: Int,
    )
}
