package app.tileshell.helper

import android.bluetooth.BluetoothManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.provider.Settings
import android.telephony.TelephonyManager

/**
 * The app's own reads of the four radios (phase 04 build task 1 c checks they return the true states). The app alone
 * can flip none of them (R4, B7); it reads them itself, so the helper has no read verb. Null when Android refuses the
 * read on this device.
 */
object RadioStates {
    fun read(context: Context, radio: Radio): Boolean? = runCatching {
        when (radio) {
            Radio.WIFI -> context.getSystemService(WifiManager::class.java).isWifiEnabled
            Radio.BLUETOOTH -> context.getSystemService(BluetoothManager::class.java)?.adapter?.isEnabled
            Radio.MOBILE_DATA -> context.getSystemService(TelephonyManager::class.java).isDataEnabled
            Radio.AIRPLANE -> Settings.Global.getInt(context.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) != 0
        }
    }.getOrNull()

    /** Whether the phone is connected to a Wi-Fi network now (Wireless debugging needs one). */
    fun wifiConnected(context: Context): Boolean = runCatching {
        val cm = context.getSystemService(ConnectivityManager::class.java)
        cm.allNetworks.any { cm.getNetworkCapabilities(it)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true }
    }.getOrDefault(false)
}
