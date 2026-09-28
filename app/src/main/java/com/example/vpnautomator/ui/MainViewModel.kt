package com.example.vpnautomator.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import com.example.vpnautomator.data.AppPreferences
import com.example.vpnautomator.data.WifiRule
import com.example.vpnautomator.network.NetworkMonitorService
import com.example.vpnautomator.vpn.OpenVpnController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class UiState(
    val profileName: String? = null,
    val wifiRules: List<WifiRule> = emptyList(),
    val cellularConnectVpn: Boolean = false,
    val isServiceRunning: Boolean = false
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = AppPreferences(application)
    private val vpnController = OpenVpnController(application)

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        _uiState.value = UiState(
            profileName = prefs.selectedProfileName,
            wifiRules = prefs.wifiRules,
            cellularConnectVpn = prefs.cellularConnectVpn
        )
    }

    fun onProfileSelected(uri: Uri) {
        vpnController.importProfile(uri)
        val fileName = uri.lastPathSegment?.substringAfterLast('/') ?: "profile"
        prefs.selectedProfileName = fileName
        _uiState.value = _uiState.value.copy(profileName = fileName)
    }

    fun setProfileName(name: String) {
        prefs.selectedProfileName = name
        _uiState.value = _uiState.value.copy(profileName = name)
    }

    fun setWifiRule(ssid: String, connectVpn: Boolean) {
        val rules = prefs.wifiRules.toMutableList()
        val idx = rules.indexOfFirst { it.ssid == ssid }
        if (idx >= 0) rules[idx] = WifiRule(ssid, connectVpn)
        else rules.add(WifiRule(ssid, connectVpn))
        prefs.wifiRules = rules
        _uiState.value = _uiState.value.copy(wifiRules = rules)
    }

    fun removeWifiRule(ssid: String) {
        val rules = prefs.wifiRules.filterNot { it.ssid == ssid }
        prefs.wifiRules = rules
        _uiState.value = _uiState.value.copy(wifiRules = rules)
    }

    fun setCellularConnectVpn(connect: Boolean) {
        prefs.cellularConnectVpn = connect
        _uiState.value = _uiState.value.copy(cellularConnectVpn = connect)
    }

    fun startService() {
        NetworkMonitorService.start(getApplication())
        _uiState.value = _uiState.value.copy(isServiceRunning = true)
    }
}
