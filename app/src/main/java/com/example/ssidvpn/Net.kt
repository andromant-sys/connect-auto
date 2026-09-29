package com.example.ssidvpn

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager

object Net {

    /** Точное имя текущей сети Wi-Fi или null, если оно недоступно. */
    @Suppress("DEPRECATION")
    fun currentSsid(ctx: Context): String? {
        return try {
            val wm = ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val raw = wm.connectionInfo?.ssid ?: return null
            val s = raw.removeSurrounding("\"")
            if (s.isEmpty() || s == WifiManager.UNKNOWN_SSID) null else s
        } catch (e: Exception) {
            null
        }
    }

    private class Snapshot(val wifi: Boolean, val mobile: Boolean, val vpn: Boolean)

    @Suppress("DEPRECATION")
    private fun snapshot(ctx: Context): Snapshot {
        val cm = ctx.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        var wifi = false
        var mobile = false
        var vpn = false
        for (n in cm.allNetworks) {
            val c = cm.getNetworkCapabilities(n) ?: continue
            if (c.hasTransport(NetworkCapabilities.TRANSPORT_VPN) ||
                !c.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
            ) {
                vpn = true
                continue
            }
            if (!c.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) continue
            if (c.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) wifi = true
            else if (c.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) mobile = true
        }
        return Snapshot(wifi, mobile, vpn)
    }

    fun hasVpn(ctx: Context) = snapshot(ctx).vpn

    /**
     * Что должно быть с VPN прямо сейчас: true - подключён, false - отключён.
     * Принудительный режим имеет приоритет над правилами.
     * В авто-режиме Wi-Fi имеет приоритет над мобильной сетью. Нет сети - отключить.
     */
    fun desired(ctx: Context): Boolean {
        when (Prefs.mode(ctx)) {
            1 -> return true
            2 -> return false
        }
        val s = snapshot(ctx)
        return when {
            s.wifi -> {
                val ssid = currentSsid(ctx)
                if (ssid == null) true else Prefs.rules(ctx)[ssid] ?: true
            }
            s.mobile -> Prefs.mobileConnect(ctx)
            else -> false
        }
    }
}
