package com.boostlab.app

import android.content.Intent
import android.os.Bundle
import android.net.VpnService
import androidx.activity.result.contract.ActivityResultContracts
import com.wireguard.android.backend.GoBackend
import com.wireguard.android.backend.Tunnel
import com.wireguard.config.Config
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
            updateStatus("Автоматическая настройка VPN пока недоступна. Импорт файла не требуется.")
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
            val sessionPrefs = remember { getSharedPreferences("boostlab_account", MODE_PRIVATE) }
            var sessionToken by remember { mutableStateOf(sessionPrefs.getString("session_token", null)) }
            var sessionEmail by remember { mutableStateOf(sessionPrefs.getString("email", "") ?: "") }
            var sessionAccountId by remember { mutableStateOf(sessionPrefs.getString("account_id", "") ?: "") }
            var accountScreen by remember { mutableStateOf(false) }
            var accountEmail by remember { mutableStateOf("") }
            var accountPassword by remember { mutableStateOf("") }
            var accountMessage by remember { mutableStateOf("") }
            var isRegistering by remember { mutableStateOf(true) }
            var accountBusy by remember { mutableStateOf(false) }
            SideEffect { updateStatus = { status = it } }
            MaterialTheme(colorScheme = darkColorScheme(primary = cyan, background = night, surface = panel)) {
                Column(Modifier.fillMaxSize().background(night).padding(18.dp)) {
                    Text("BOOSTLAB", style = MaterialTheme.typography.headlineLarge, color = Color.White, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text("Выбери игру и подключись через VPN", color = Color.LightGray)
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(onClick = { accountScreen = !accountScreen }, modifier = Modifier.fillMaxWidth()) {
                        Text(if (accountScreen) "Вернуться к играм" else "Аккаунт / Регистрация")
                    }
                    if (accountScreen) {
                        if (sessionToken != null) {
                            Text("Личный кабинет", color = Color.White, style = MaterialTheme.typography.titleLarge)
                            Text("Email: $sessionEmail", color = Color.White)
                            Text("ID: $sessionAccountId", color = Color.LightGray)
                            Button(onClick = {
                                val token = sessionToken ?: return@Button
                                accountBusy = true
                                Thread {
                                    try {
                                        AccountApi.authenticated("/v1/accounts/logout", token, "POST")
                                        runOnUiThread {
                                            sessionPrefs.edit().clear().apply()
                                            sessionToken = null
                                            sessionEmail = ""
                                            sessionAccountId = ""
                                            accountMessage = "Вы вышли из аккаунта"
                                            accountBusy = false
                                        }
                                    } catch (e: Exception) {
                                        runOnUiThread { accountMessage = "Не удалось завершить сессию: ${e.message}"; accountBusy = false }
                                    }
                                }.start()
                            }, enabled = !accountBusy) { Text("Выйти") }
                            Text(accountMessage, color = cyan)
                        } else {
                        Text(if (isRegistering) "Создать аккаунт" else "Войти", color = Color.White,
                            style = MaterialTheme.typography.titleLarge)
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(value = accountEmail, onValueChange = { accountEmail = it },
                            label = { Text("Email") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(value = accountPassword, onValueChange = { accountPassword = it },
                            label = { Text("Пароль") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                            visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation())
                        Text("От 8 символов: заглавная и строчная буква, цифра", color = Color.LightGray)
                        Button(enabled = !accountBusy && accountEmail.isNotBlank() && accountPassword.isNotBlank(),
                            onClick = {
                                accountBusy = true
                                accountMessage = "Подключение к серверу…"
                                val path = if (isRegistering) "/v1/accounts/register" else "/v1/accounts/login"
                                Thread {
                                    try {
                                        val result = AccountApi.request(path, accountEmail.trim(), accountPassword)
                                        runOnUiThread {
                                            accountMessage = if (isRegistering) "Аккаунт создан. Теперь войди."
                                                else "Вход выполнен. ID: " + result.getString("account_id").take(8)
                                            if (!isRegistering) {
                                                val token = result.getString("session_token")
                                                val accountId = result.getString("account_id")
                                                sessionPrefs.edit().putString("session_token", token)
                                                    .putString("account_id", accountId)
                                                    .putString("email", accountEmail.trim()).apply()
                                                sessionToken = token
                                                sessionAccountId = accountId
                                                sessionEmail = accountEmail.trim()
                                            }
                                            accountPassword = ""
                                            if (isRegistering) isRegistering = false
                                            accountBusy = false
                                        }
                                    } catch (e: Exception) {
                                        runOnUiThread {
                                            accountMessage = "Ошибка подключения или регистрации: " + (e.message ?: "Неизвестная ошибка")
                                            accountBusy = false
                                        }
                                    }
                                }.start()
                            }, modifier = Modifier.fillMaxWidth()) {
                            Text(if (isRegistering) "Зарегистрироваться" else "Войти")
                        }
                        TextButton(onClick = { isRegistering = !isRegistering; accountMessage = "" }) {
                            Text(if (isRegistering) "Уже есть аккаунт? Войти" else "Нет аккаунта? Зарегистрироваться")
                        }
                        Text(accountMessage, color = cyan)
                        }
                    } else {
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
                            Text("Тестовая версия: автоматическое VPN-подключение пока недоступно. Запуск игры до проверки соединения заблокирован.", color = Color.LightGray)
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
}
