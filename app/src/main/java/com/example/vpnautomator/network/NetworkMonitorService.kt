package com.example.vpnautomator.network

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.vpnautomator.MainActivity
import com.example.vpnautomator.data.AppPreferences
import com.example.vpnautomator.vpn.OpenVpnController

/**
 * Foreground Service, отслеживающий изменения сети и управляющий VPN.
 *
 * Логика:
 * 1. Wi-Fi -> Wi-Fi (смена SSID): применяем правило для нового SSID.
 * 2. Wi-Fi -> мобильная сеть: применяем настройку cellularConnectVpn.
 * 3. Мобильная сеть -> Wi-Fi: применяем правило для SSID.
 * 4. Любая сеть -> нет сети: принудительно отключаем VPN.
 */
class NetworkMonitorService : Service() {

    private lateinit var connectivityManager: ConnectivityManager
    private lateinit var prefs: AppPreferences
    private lateinit var vpnController: OpenVpnController

    private var lastWifiSsid: String? = null
    private var lastNetworkType: NetworkType = NetworkType.NONE

    private enum class NetworkType { WIFI, CELLULAR, NONE }

    override fun onCreate() {
        super.onCreate()
        connectivityManager = getSystemService(ConnectivityManager::class.java)
        prefs = AppPreferences(this)
        vpnController = OpenVpnController(this)

        startForeground(NOTIFICATION_ID, buildNotification())
        registerNetworkCallback()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    private fun registerNetworkCallback() {
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        connectivityManager.registerNetworkCallback(
            request,
            object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) { evaluateNetworkState() }
                override fun onLost(network: Network) { evaluateNetworkState() }
                override fun onCapabilitiesChanged(
                    network: Network,
                    caps: NetworkCapabilities
                ) { evaluateNetworkState() }
            }
        )
    }

    private fun evaluateNetworkState() {
        val activeNetwork = connectivityManager.activeNetwork
        val caps = activeNetwork?.let { connectivityManager.getNetworkCapabilities(it) }

        val hasWifi = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        val hasCellular = caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true

        val currentType = when {
            hasWifi -> NetworkType.WIFI
            hasCellular -> NetworkType.CELLULAR
            else -> NetworkType.NONE
        }

        val currentSsid = if (hasWifi) getCurrentSsid(activeNetwork) else null

        if (currentType == NetworkType.NONE) {
            if (lastNetworkType != NetworkType.NONE) {
                vpnController.disconnect()
                prefs.vpnWasActive = false
            }
            lastNetworkType = NetworkType.NONE
            lastWifiSsid = null
            return
        }

        if (currentType == NetworkType.WIFI && currentSsid != null) {
            if (lastWifiSsid != currentSsid || lastNetworkType != NetworkType.WIFI) {
                applyWifiRule(currentSsid)
            }
        }

        if (currentType == NetworkType.CELLULAR && lastNetworkType != NetworkType.CELLULAR) {
            applyCellularAction()
        }

        lastNetworkType = currentType
        lastWifiSsid = currentSsid
    }

    private fun applyWifiRule(ssid: String) {
        val rule = prefs.wifiRules.firstOrNull { it.ssid == ssid } ?: return
        val profile = prefs.selectedProfileName ?: return

        if (rule.connectVpn) {
            vpnController.connect(profile)
            prefs.vpnWasActive = true
        } else {
            vpnController.disconnect()
            prefs.vpnWasActive = false
        }
    }

    private fun applyCellularAction() {
        val profile = prefs.selectedProfileName ?: return
        if (prefs.cellularConnectVpn) {
            vpnController.connect(profile)
            prefs.vpnWasActive = true
        } else {
            vpnController.disconnect()
            prefs.vpnWasActive = false
        }
    }

    @Suppress("DEPRECATION")
    private fun getCurrentSsid(network: Network?): String? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val caps = connectivityManager.getNetworkCapabilities(network) ?: return null
                val info = caps.transportInfo
                if (info is WifiInfo) info.ssid?.trim('"') else null
            } else {
                val wm = applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
                wm.connectionInfo?.ssid?.trim('"')
            }
        } catch (_: SecurityException) { null }
    }

    private fun buildNotification(): Notification {
        val channelId = "vpn_monitor_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "VPN Monitor",
                NotificationManager.IMPORTANCE_MIN
            ).apply { description = "Фоновый мониторинг сети" }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }

        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("VPN Automator")
            .setContentText("Отслеживание сети активно")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
    }

    companion object {
        private const val NOTIFICATION_ID = 1001

        fun start(context: Context) {
            val intent = Intent(context, NetworkMonitorService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }
}
