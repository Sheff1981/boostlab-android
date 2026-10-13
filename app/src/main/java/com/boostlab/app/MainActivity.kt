package com.boostlab.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
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
            var search by remember { mutableStateOf("") }
            val visibleGames = remember(search) { games.filter { it.second.contains(search.trim(), ignoreCase = true) || it.first.contains(search.trim(), ignoreCase = true) } }
            var status by remember { mutableStateOf("VPN не настроен") }
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
                                enabled = false, // Fail closed until WireGuard and backend provisioning are implemented.
                                onClick = { status = "VPN недоступен" },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("BOOST") }
                            Text("Подключение появится после настройки сервера", color = Color.LightGray)
                        }
                    }
                    Spacer(Modifier.height(18.dp))
                    Text("Приложения (${games.size})", color = Color.White, style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(value = search, onValueChange = { search = it }, label = { Text("Поиск игры") }, singleLine = true, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    if (selected != null) {
                        Button(onClick = { packageManager.getLaunchIntentForPackage(selected!!)?.let { startActivity(it) } ?: run { status = "Не удалось открыть приложение" } }, modifier = Modifier.fillMaxWidth()) { Text("Открыть выбранную игру без BOOST") }
                    }
                    Spacer(Modifier.height(8.dp))
                    if (visibleGames.isEmpty()) Text("Приложения не найдены", color = Color.LightGray)
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(visibleGames) { (pkg, name) ->
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
