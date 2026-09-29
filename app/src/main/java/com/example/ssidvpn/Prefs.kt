package com.example.ssidvpn

import android.content.Context
import org.json.JSONObject

/** Настройки: SSID -> true (подключать VPN) / false (не подключать). */
object Prefs {
    private fun sp(c: Context) =
        c.applicationContext.getSharedPreferences("cfg", Context.MODE_PRIVATE)

    fun rules(c: Context): Map<String, Boolean> {
        val out = LinkedHashMap<String, Boolean>()
        try {
            val o = JSONObject(sp(c).getString("rules", "{}") ?: "{}")
            for (k in o.keys()) out[k] = o.getBoolean(k)
        } catch (_: Exception) {
        }
        return out
    }

    private fun save(c: Context, m: Map<String, Boolean>) {
        val o = JSONObject()
        for ((k, v) in m) o.put(k, v)
        sp(c).edit().putString("rules", o.toString()).apply()
    }

    fun setRule(c: Context, ssid: String, connect: Boolean) {
        val m = LinkedHashMap(rules(c))
        m[ssid] = connect
        save(c, m)
    }

    fun removeRule(c: Context, ssid: String) {
        val m = LinkedHashMap(rules(c))
        m.remove(ssid)
        save(c, m)
    }

    fun mobileConnect(c: Context) = sp(c).getBoolean("mobile", true)
    fun setMobileConnect(c: Context, v: Boolean) = sp(c).edit().putBoolean("mobile", v).apply()

    /** Режим: 0 - авто (по правилам), 1 - принудительно включён, 2 - принудительно выключен. */
    fun mode(c: Context) = sp(c).getInt("mode", 0)
    fun setMode(c: Context, v: Int) = sp(c).edit().putInt("mode", v).apply()

    fun profileUuid(c: Context): String? = sp(c).getString("uuid", null)
    fun profileName(c: Context): String? = sp(c).getString("pname", null)
    fun setProfile(c: Context, uuid: String, name: String) =
        sp(c).edit().putString("uuid", uuid).putString("pname", name).apply()

    /** Последнее применённое решение: -1 неизвестно, 0 отключить, 1 подключить. */
    fun last(c: Context) = sp(c).getInt("last", -1)
    fun setLast(c: Context, v: Int) = sp(c).edit().putInt("last", v).apply()
}
