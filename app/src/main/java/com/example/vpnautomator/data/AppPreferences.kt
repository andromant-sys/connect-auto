package com.example.vpnautomator.data

import android.content.Context
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject

/**
 * Хранилище состояния приложения в SharedPreferences.
 * Переживает перезагрузку и убийство процесса.
 */
class AppPreferences(context: Context) {

    private val prefs = context.getSharedPreferences("vpn_automator", Context.MODE_PRIVATE)

    var selectedProfileName: String?
        get() = prefs.getString(KEY_PROFILE, null)
        set(value) = prefs.edit { putString(KEY_PROFILE, value) }

    /** Действие при переходе на мобильную сеть. true = подключать VPN. */
    var cellularConnectVpn: Boolean
        get() = prefs.getBoolean(KEY_CELLULAR_CONNECT, false)
        set(value) = prefs.edit { putBoolean(KEY_CELLULAR_CONNECT, value) }

    /** Флаг «VPN был активен» — восстанавливается после перезагрузки. */
    var vpnWasActive: Boolean
        get() = prefs.getBoolean(KEY_VPN_ACTIVE, false)
        set(value) = prefs.edit { putBoolean(KEY_VPN_ACTIVE, value) }

    /** Список правил для Wi-Fi сетей. */
    var wifiRules: List<WifiRule>
        get() {
            val raw = prefs.getString(KEY_WIFI_RULES, null) ?: return emptyList()
            return try {
                val arr = JSONArray(raw)
                (0 until arr.length()).map { i ->
                    val obj = arr.getJSONObject(i)
                    WifiRule(
                        ssid = obj.getString("ssid"),
                        connectVpn = obj.getBoolean("connectVpn")
                    )
                }
            } catch (_: Exception) { emptyList() }
        }
        set(value) {
            val arr = JSONArray()
            value.forEach { rule ->
                arr.put(JSONObject().apply {
                    put("ssid", rule.ssid)
                    put("connectVpn", rule.connectVpn)
                })
            }
            prefs.edit { putString(KEY_WIFI_RULES, arr.toString()) }
        }

    companion object {
        private const val KEY_PROFILE = "selected_profile"
        private const val KEY_CELLULAR_CONNECT = "cellular_connect_vpn"
        private const val KEY_VPN_ACTIVE = "vpn_was_active"
        private const val KEY_WIFI_RULES = "wifi_rules"
    }
}
