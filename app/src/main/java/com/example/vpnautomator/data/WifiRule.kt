package com.example.vpnautomator.data

/**
 * Правило для конкретной Wi-Fi сети.
 * @param ssid точное имя сети
 * @param connectVpn true = подключать VPN, false = отключать
 */
data class WifiRule(
    val ssid: String,
    val connectVpn: Boolean
)
