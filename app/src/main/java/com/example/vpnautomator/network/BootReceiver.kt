package com.example.vpnautomator.network

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.vpnautomator.data.AppPreferences

/**
 * Запускает сервис мониторинга после перезагрузки устройства.
 * Сервис сам определит текущее состояние сети при первом callback'е.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val prefs = AppPreferences(context)
            if (prefs.selectedProfileName != null) {
                NetworkMonitorService.start(context)
            }
        }
    }
}
