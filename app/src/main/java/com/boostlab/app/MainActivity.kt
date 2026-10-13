package com.boostlab.app

import android.content.Intent
import android.os.Bundle
import android.net.VpnService
import androidx.activity.result.contract.ActivityResultContracts
import com.wireguard.android.backend.GoBackend
import com.wireguard.android.backend.Tunnel
import com.wireguard.config.Config
import java.io.ByteArrayInputStream
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private val night = Color(0xFF10121D)
private val panel = Color(0xFF202330)
private val cyan = Color(0xFF55EAE6)

class MainActivity : ComponentActivity() {
    private val backend by lazy { GoBackend(applicationContext) }
    private var importedConfig: Config? = null
    private var updateStatus: (String) -> Unit = {}
    private val tunnel = object : Tunnel {
        override fun getName(): String = "boostlab"
        override fun onStateChange(newState: Tunnel.State) {
            runOnUiThread { updateStatus("Туннель: $newState") }
        }
    }
    private val importConfig = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            try {
                val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: error("Не удалось открыть конфигурацию")
                importedConfig = Config.parse(ByteArrayInputStream(bytes))
                bytes.fill(0)
                updateStatus("Конфигурация загружена. Нажми BOOST")
            } catch (e: Exception) {
                updateStatus("Ошибка конфигурации: ${e.message}")
            }
        }
    }
    private val vpnPermission = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == RESULT_OK) connect()
        else updateStatus("Разрешение VPN не предоставлено")
    }
    private fun connect() {
        val config = importedConfig ?: return
        updateStatus("Подключение WireGuard…")
        Thread {
            try {
                backend.setState(tunnel, Tunnel.State.UP, config)
                runOnUiThread { updateStatus("Туннель поднят; handshake пока не проверен") }
            } catch (e: Exception) {
                runOnUiThread { updateStatus("Ошибка VPN: ${e.message}") }
            }
        }.start()
    }
    private fun disconnect() {
        updateStatus("Отключение WireGuard…")
        Thread {
            try {
                backend.setState(tunnel, Tunnel.State.DOWN, null)
                runOnUiThread { updateStatus("VPN отключён") }
            } catch (e: Exception) {
                runOnUiThread { updateStatus("Ошибка отключения: ${e.message}") }
            }
        }.start()
    }
    private fun requestConnect() {
        if (importedConfig == null) {
            importConfig.launch("*/*")
            return
        }
        val intent = VpnService.prepare(this)
        if (intent != null) vpnPermission.launch(intent) else connect()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val games = packageManager.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0
        ).mapNotNull { entry ->
            val pkg = entry.activityInfo.packageName
            if (pkg == packageName) null else pkg to entry.loadLabel(packageManager).toString()
        }.distinctBy { it.first }.sortedBy { it.second.lowercase() }

        setContent {
            var selected by remember { mutableStateOf<String?>(null) }
            var status by remember { mutableStateOf("VPN не настроен") }
            SideEffect { updateStatus = { status = it } }
            MaterialTheme(colorScheme = darkColorScheme(primary = cyan, background = night, surface = panel)) {
                Column(Modifier.fillMaxSize().background(night).padding(18.dp)) {
                    Text("BOOSTLAB", style = MaterialTheme.typography.headlineLarge, color = Color.White, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text("Выбери игру и подключись через VPN", color = Color.LightGray)
                    Spacer(Modifier.height(20.dp))
                    Card(colors = CardDefaults.cardColors(containerColor = panel), shape = RoundedCornerShape(18.dp)) {
                        Column(Modifier.fillMaxWidth().padding(18.dp)) {
                            Text("Сеть", color = Color.LightGray)
                            Text(status, color = cyan, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(12.dp))
                            Button(
                                enabled = selected != null,
                                onClick = { requestConnect() },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("BOOST") }
                            OutlinedButton(
                                onClick = { disconnect() },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("Отключить VPN") }
                            Text("Тестовый режим: импорт WireGuard-конфигурации. Запуск игры до проверки handshake заблокирован.", color = Color.LightGray)
                        }
                    }
                    Spacer(Modifier.height(18.dp))
                    Text("Приложения", color = Color.White, style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(8.dp))
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(games) { (pkg, name) ->
                            Card(
                                onClick = { selected = pkg },
                                colors = CardDefaults.cardColors(containerColor = if (selected == pkg) Color(0xFF244A54) else panel),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(name, modifier = Modifier.weight(1f), color = Color.White)
                                    Text(if (selected == pkg) "Выбрано" else "Выбрать", color = cyan)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
