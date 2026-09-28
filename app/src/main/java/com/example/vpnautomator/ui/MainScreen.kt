package com.example.vpnautomator.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.vpnautomator.data.WifiRule

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: MainViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { viewModel.onProfileSelected(it) } }

    Scaffold(
        topBar = { TopAppBar(title = { Text("VPN Automator") }) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("OpenVPN профиль", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        state.profileName ?: "Не выбран",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = {
                        filePicker.launch(arrayOf("application/x-openvpn-profile", "*/*"))
                    }) { Text("Загрузить .ovpn") }
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Мобильная сеть", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Подключать VPN при переходе на мобильную сеть")
                        Switch(
                            checked = state.cellularConnectVpn,
                            onCheckedChange = { viewModel.setCellularConnectVpn(it) }
                        )
                    }
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Правила Wi-Fi", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    state.wifiRules.forEach { rule ->
                        WifiRuleRow(
                            rule = rule,
                            onToggle = { viewModel.setWifiRule(rule.ssid, it) },
                            onDelete = { viewModel.removeWifiRule(rule.ssid) }
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    AddWifiRuleField(
                        onAdd = { ssid, connect -> viewModel.setWifiRule(ssid, connect) }
                    )
                }
            }

            Button(
                onClick = { viewModel.startService() },
                modifier = Modifier.fillMaxWidth(),
                enabled = state.profileName != null
            ) {
                Text(if (state.isServiceRunning) "Сервис запущен" else "Запустить мониторинг")
            }
        }
    }
}

@Composable
private fun WifiRuleRow(
    rule: WifiRule,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(Modifier.weight(1f)) {
            Text(rule.ssid, fontWeight = FontWeight.Medium)
            Text(
                if (rule.connectVpn) "Подключать VPN" else "Отключать VPN",
                style = MaterialTheme.typography.bodySmall
            )
        }
        Switch(checked = rule.connectVpn, onCheckedChange = onToggle)
        IconButton(onClick = onDelete) {
            Icon(Icons.Default.Delete, contentDescription = "Удалить")
        }
    }
}

@Composable
private fun AddWifiRuleField(onAdd: (String, Boolean) -> Unit) {
    var ssid by remember { mutableStateOf("") }
    var connect by remember { mutableStateOf(true) }

    Column {
        OutlinedTextField(
            value = ssid,
            onValueChange = { ssid = it },
            label = { Text("SSID сети") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Подключать VPN")
            Switch(checked = connect, onCheckedChange = { connect = it })
        }
        Button(
            onClick = {
                if (ssid.isNotBlank()) {
                    onAdd(ssid.trim(), connect)
                    ssid = ""
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Добавить правило") }
    }
}
