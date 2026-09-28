package com.example.vpnautomator.vpn

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Обёртка над API OpenVPN for Android (de.blinkt.openvpn).
 *
 * Для работы с фонового сервиса на Android 10+ приложению нужно
 * разрешение SYSTEM_ALERT_WINDOW («Отображение поверх других приложений»),
 * иначе система заблокирует запуск активностей OpenVPN из фона.
 */
class OpenVpnController(private val context: Context) {

    fun connect(profileName: String) {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            component = ComponentName(
                "de.blinkt.openvpn",
                "de.blinkt.openvpn.api.ConnectVPN"
            )
            putExtra("de.blinkt.openvpn.api.profileName", profileName)
        }
        try {
            context.startActivity(intent)
        } catch (_: Exception) {
            openPlayStore()
        }
    }

    fun disconnect() {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            component = ComponentName(
                "de.blinkt.openvpn",
                "de.blinkt.openvpn.api.DisconnectVPN"
            )
        }
        try {
            context.startActivity(intent)
        } catch (_: Exception) { }
    }

    fun importProfile(uri: Uri) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/x-openvpn-profile")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            context.startActivity(intent)
        } catch (_: Exception) { }
    }

    private fun openPlayStore() {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("market://details?id=de.blinkt.openvpn")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
        } catch (_: Exception) {
            val web = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse("https://play.google.com/store/apps/details?id=de.blinkt.openvpn")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try { context.startActivity(web) } catch (_: Exception) { }
        }
    }
}
