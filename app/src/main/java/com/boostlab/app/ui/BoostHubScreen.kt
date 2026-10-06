package com.boostlab.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.boostlab.app.model.BoostApp
import com.boostlab.app.model.BoostState
import com.boostlab.app.model.CatalogGame
import com.boostlab.app.model.GameCatalogTag
import java.util.Locale

private val HubNight = Color(0xFF080B12)
private val HubPanel = Color(0xFF121722)
private val HubPanel2 = Color(0xFF171D2A)
private val HubCyan = Color(0xFF54F3F4)
private val HubViolet = Color(0xFF9C36F5)
private val HubMuted = Color(0xFF8993A8)
private val HubMint = Color(0xFF40E3AF)
private val HubError = Color(0xFFFF8A96)

private enum class HubTab { GAMES, BOOST, STATS, SQUAD, PROFILE }
private enum class CatalogTab { HOT, NEW, ALL }

@Composable
fun BoostHubScreen(
    viewModel: BoostViewModel,
    onRequestVpnPermission: () -> Unit,
    onRequestAudioPermission: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    var tabName by rememberSaveable { mutableStateOf(HubTab.BOOST.name) }
    val tab = runCatching { HubTab.valueOf(tabName) }.getOrDefault(HubTab.BOOST)

    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = HubCyan,
            secondary = HubViolet,
            background = HubNight,
            surface = HubPanel,
        ),
    ) {
        Scaffold(
            containerColor = HubNight,
            bottomBar = {
                NavigationBar(containerColor = Color(0xFF0B0F18)) {
                    HubTab.entries.forEach { item ->
                        NavigationBarItem(
                            selected = tab == item,
                            onClick = { tabName = item.name },
                            icon = {
                                Icon(
                                    when (item) {
                                        HubTab.GAMES -> Icons.Default.Gamepad
                                        HubTab.BOOST -> Icons.Default.Bolt
                                        HubTab.STATS -> Icons.Default.BarChart
                                        HubTab.SQUAD -> Icons.Default.Group
                                        HubTab.PROFILE -> Icons.Default.Person
                                    },
                                    contentDescription = null,
                                )
                            },
                            label = {
                                Text(
                                    when (item) {
                                        HubTab.GAMES -> "Игры"
                                        HubTab.BOOST -> "Буст"
                                        HubTab.STATS -> "Статы"
                                        HubTab.SQUAD -> "Отряд"
                                        HubTab.PROFILE -> "Я"
                                    },
                                    fontSize = 10.sp,
                                )
                            },
                        )
                    }
                }
            },
        ) { padding ->
            when (tab) {
                HubTab.GAMES -> GamesPage(viewModel, state, padding)
                HubTab.BOOST -> BoostPage(viewModel, state, padding, onRequestVpnPermission)
                HubTab.STATS -> StatsPage(viewModel, state, padding)
                HubTab.SQUAD -> SquadPage(viewModel, state, padding, onRequestAudioPermission)
                HubTab.PROFILE -> ProfilePage(viewModel, state, padding)
            }
        }
    }
}

@Composable
private fun PageHeader(title: String, subtitle: String? = null) {
    Column {
        Text(title, color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Black)
        if (subtitle != null) Text(subtitle, color = HubMuted, fontSize = 12.sp)
    }
}

@Composable
private fun GamesPage(viewModel: BoostViewModel, state: BoostState, padding: PaddingValues) {
    var catalogTabName by rememberSaveable { mutableStateOf(CatalogTab.HOT.name) }
    var query by rememberSaveable { mutableStateOf("") }
    var showInstalled by rememberSaveable { mutableStateOf(false) }
    val catalogTab = runCatching { CatalogTab.valueOf(catalogTabName) }.getOrDefault(CatalogTab.HOT)
    val filtered = viewModel.catalogGames.filter {
        val tagOk = when (catalogTab) {
            CatalogTab.HOT -> GameCatalogTag.HOT in it.tags
            CatalogTab.NEW -> GameCatalogTag.NEW in it.tags
            CatalogTab.ALL -> true
        }
        tagOk && (query.isBlank() || it.title.contains(query, ignoreCase = true))
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { PageHeader("Игры", "Каталог: ${viewModel.catalogGames.size} · установлено: ${viewModel.apps.size}") }
        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = { Icon(Icons.Default.Search, null) },
                placeholder = { Text("Поиск игры") },
                singleLine = true,
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CatalogTab.entries.forEach { c ->
                    SelectChip(
                        text = when (c) {
                            CatalogTab.HOT -> "Hot"
                            CatalogTab.NEW -> "New"
                            CatalogTab.ALL -> "All"
                        },
                        selected = c == catalogTab,
                        onClick = { catalogTabName = c.name },
                    )
                }
            }
        }
        item {
            OutlinedButton(onClick = { showInstalled = !showInstalled }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Add, null)
                Spacer(Modifier.width(8.dp))
                Text(if (showInstalled) "Скрыть приложения телефона" else "Добавить игру с телефона")
            }
        }
        if (showInstalled) {
            items(
                viewModel.apps.filter { query.isBlank() || it.label.contains(query, true) },
                key = { "local-" + it.packageName },
            ) { app ->
                InstalledAppRow(app, app.packageName in state.pinnedPackages) {
                    viewModel.togglePinnedApp(app)
                }
            }
        }
        items(filtered, key = { "catalog-" + it.title }) { game ->
            val installed = findInstalled(game, viewModel.apps)
            CatalogRow(
                game = game,
                installed = installed,
                onAction = {
                    if (installed != null) viewModel.selectApp(installed)
                    else viewModel.openStoreSearch(game.title)
                },
            )
        }
    }
}

@Composable
private fun BoostPage(
    viewModel: BoostViewModel,
    state: BoostState,
    padding: PaddingValues,
    onRequestVpnPermission: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var confirmDisconnect by remember { mutableStateOf(false) }
    val pinned = viewModel.apps.filter { it.packageName in state.pinnedPackages }
    val visible = (if (pinned.isNotEmpty()) pinned else viewModel.apps)
        .filter { query.isBlank() || it.label.contains(query, true) }

    if (confirmDisconnect) {
        AlertDialog(
            onDismissRequest = { confirmDisconnect = false },
            title = { Text("Остановить Network Boost?") },
            text = { Text("VPN-маршрут выбранной игры будет отключён.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDisconnect = false
                    viewModel.disconnectTunnel()
                }) { Text("Остановить") }
            },
            dismissButton = { TextButton(onClick = { confirmDisconnect = false }) { Text("Отмена") } },
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { PageHeader("Локальный Буст", "Выбери игру → BOOST → запуск") }
        item { NetworkHero(state) }
        item {
            Panel {
                Text("Режим ускорения", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SelectChip("Smart", state.boostMode == "SMART") { viewModel.setBoostMode("SMART") }
                    SelectChip("Low Ping", state.boostMode == "LOW_PING") { viewModel.setBoostMode("LOW_PING") }
                    SelectChip("Stable", state.boostMode == "STABLE") { viewModel.setBoostMode("STABLE") }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "Регион: ${state.preferredRegion} · узел: ${state.selectedGatewayRegion ?: "авто"}",
                    color = HubMuted,
                    fontSize = 11.sp,
                )
            }
        }
        item {
            state.selectedApp?.let { SelectedAppCard(it, state, viewModel::cycleGameLaunchMode) }
                ?: InfoCard("Игра не выбрана", "Нажми на игру ниже, чтобы подготовить запуск.")
        }
        item {
            PrimaryButton(
                text = if (state.isGameLaunching) "Запускаем…" else "BOOST · ЗАПУСТИТЬ",
                enabled = state.selectedApp != null && !state.isGameLaunching,
                onClick = viewModel::boostAndLaunchGame,
            )
        }
        item {
            Panel {
                Text("Network Boost", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text("VPN-маршрут отдельно от локального запуска.", color = HubMuted, fontSize = 12.sp)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            if (state.isBoosting) {
                                if (state.confirmStop) confirmDisconnect = true else viewModel.disconnectTunnel()
                            } else onRequestVpnPermission()
                        },
                        enabled = !state.isTunnelConnecting,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(
                            when {
                                state.isTunnelConnecting -> "Подключаем…"
                                state.isBoosting -> "Отключить"
                                else -> "Включить VPN"
                            },
                        )
                    }
                    OutlinedButton(
                        onClick = viewModel::autoSelectGateway,
                        enabled = !state.isBoosting && !state.isTunnelConnecting && !state.isAutoSelecting,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(if (state.isAutoSelecting) "Ищем…" else "Авто-сервер")
                    }
                }
                if (state.isBoosting) {
                    Text("Сервер зафиксирован до отключения Network Boost.", color = HubMint, fontSize = 11.sp)
                }
                state.tunnelError?.let { Text(it, color = HubError, fontSize = 11.sp) }
            }
        }
        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = { Icon(Icons.Default.Search, null) },
                placeholder = { Text("Поиск в моих играх") },
                singleLine = true,
            )
        }
        items(visible.take(30), key = { it.packageName }) { app ->
            LocalBoostRow(
                app = app,
                selected = state.selectedApp?.packageName == app.packageName,
                onClick = { viewModel.selectApp(app) },
            )
        }
    }
}

@Composable
private fun StatsPage(viewModel: BoostViewModel, state: BoostState, padding: PaddingValues) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { PageHeader("Статистика", "Boost Report · реальные измерения") }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MetricBox("PING", if (state.showPing) state.pingMs?.let { "$it ms" } ?: "—" else "скрыт", Modifier.weight(1f))
                MetricBox("JITTER", state.jitterMs?.let { "$it ms" } ?: "—", Modifier.weight(1f))
                MetricBox("LOSS", state.packetLossPct?.let { String.format(Locale.US, "%.1f%%", it) } ?: "—", Modifier.weight(1f))
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MetricBox("RAM", state.availableMemoryPercent?.let { "$it%" } ?: "—", Modifier.weight(1f))
                MetricBox("НАГРЕВ", thermalLabel(state.thermalStatus), Modifier.weight(1f))
                MetricBox("СЕТЬ", state.networkTransport ?: "—", Modifier.weight(1f))
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MetricBox("VPN ↓", trafficLabel(state.tunnelRxBytes), Modifier.weight(1f))
                MetricBox("VPN ↑", trafficLabel(state.tunnelTxBytes), Modifier.weight(1f))
                MetricBox("ROUTE", state.routeHealth, Modifier.weight(1f))
            }
        }
        item {
            InfoCard(
                when {
                    state.gameTrafficVerified -> "Игровой трафик подтверждён"
                    state.isBoosting -> "Handshake есть · ждём трафик игры"
                    else -> "Проверка игрового трафика выключена"
                },
                "RX/TX — фактические байты WireGuard выбранной игры. Ping/Jitter/Loss — измерения gateway, а не обещанный FPS.",
            )
        }
        item {
            Panel {
                Text("Boost Report", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Spacer(Modifier.height(8.dp))
                Text("Сессий: ${state.boostSessionCount}", color = HubMuted)
                Text("Общее время: ${formatDuration(state.totalBoostSeconds)}", color = HubMuted)
                Text("Последняя сессия: ${formatDuration(state.lastBoostSeconds)}", color = HubMuted)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MetricBox("LAST PING", state.lastBoostPingMs?.let { "$it ms" } ?: "—", Modifier.weight(1f))
                    MetricBox("LAST JITTER", state.lastBoostJitterMs?.let { "$it ms" } ?: "—", Modifier.weight(1f))
                    MetricBox(
                        "LAST LOSS",
                        state.lastBoostPacketLossPct?.let { String.format(Locale.US, "%.1f%%", it) } ?: "—",
                        Modifier.weight(1f),
                    )
                }
            }
        }
        item {
            Panel {
                Text(if (state.isBoosting) "Network Boost активен" else "Network Boost выключен", color = Color.White, fontWeight = FontWeight.Bold)
                Text(state.serverLabel, color = HubMuted, fontSize = 12.sp)
                Text("Режим: ${state.boostMode} · регион: ${state.preferredRegion}", color = HubMuted, fontSize = 11.sp)
                Text(
                    "Маршрут: ${state.routeHealth} · ошибок проверки подряд: ${state.routeProbeFailures}",
                    color = if (state.routeHealth == "DEGRADED") HubError else HubMuted,
                    fontSize = 11.sp,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = viewModel::probeGateway,
                    enabled = state.gatewayHost.isNotBlank() && !state.isProbing,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (state.isProbing) "Проверяем ping…" else "Тест ping / jitter / loss")
                }
            }
        }
        item {
            InfoCard("Профиль запуска: ${state.gameLaunchMode.title}", state.gameLaunchMode.description)
        }
    }
}

@Composable
private fun SquadPage(
    viewModel: BoostViewModel,
    state: BoostState,
    padding: PaddingValues,
    onRequestAudioPermission: () -> Unit,
) {
    var joinCode by rememberSaveable { mutableStateOf("") }
    var friend by rememberSaveable { mutableStateOf("") }
    var message by rememberSaveable { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { PageHeader("Отряд", "Реальный чат через BOOSTLAB Control API") }
        item {
            InfoCard(
                "Твой ID: ${state.localUserId.ifBlank { "создаётся…" }}",
                "Текстовый чат и online-presence работают через наш сервер. Voice signaling уже поддерживается сервером; медиаканал WebRTC — следующий слой.",
            )
        }
        item {
            Panel {
                Text("Отряды", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                if (state.squadCode == null) {
                    PrimaryButton("Создать новый отряд", true, viewModel::createSquad)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = joinCode,
                        onValueChange = { joinCode = it },
                        label = { Text("Код приглашения") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { viewModel.joinSquad(joinCode) },
                        enabled = joinCode.trim().length >= 4,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Присоединиться") }
                } else {
                    Text("Код отряда: ${state.squadCode}", color = HubCyan, fontWeight = FontWeight.Bold)
                    Text(
                        "Онлайн: ${state.squadOnlineUsers.size}",
                        color = if (state.squadOnlineUsers.isNotEmpty()) HubMint else HubMuted,
                        fontSize = 11.sp,
                    )
                    if (state.squadOnlineUsers.isNotEmpty()) {
                        Text(
                            state.squadOnlineUsers.joinToString(" · "),
                            color = HubMuted,
                            fontSize = 10.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = viewModel::shareSquadInvite, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.Share, null)
                            Spacer(Modifier.width(6.dp))
                            Text("Пригласить")
                        }
                        OutlinedButton(onClick = viewModel::refreshSquadNow, modifier = Modifier.weight(1f)) {
                            Text("Обновить")
                        }
                        OutlinedButton(onClick = viewModel::leaveSquad, modifier = Modifier.weight(1f)) {
                            Text("Выйти")
                        }
                    }
                }
            }
        }

        if (state.squadCode != null) {
            item {
                Panel {
                    Text("Голосовой чат", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "P2P WebRTC · signaling через BOOSTLAB Control",
                        color = HubMuted,
                        fontSize = 11.sp,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Статус: ${voiceStateLabel(state.voiceCallState)}",
                        color = when (state.voiceCallState) {
                            "CONNECTED" -> HubMint
                            "FAILED" -> HubError
                            else -> HubCyan
                        },
                        fontWeight = FontWeight.Bold,
                    )
                    state.voicePeerId?.let {
                        Text("Собеседник: $it", color = HubMuted, fontSize = 11.sp)
                    }
                    state.voiceError?.let {
                        Spacer(Modifier.height(4.dp))
                        Text(it, color = HubError, fontSize = 11.sp)
                    }
                    Spacer(Modifier.height(10.dp))

                    when (state.voiceCallState) {
                        "RINGING" -> {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = onRequestAudioPermission,
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(containerColor = HubMint),
                                ) {
                                    Text("Ответить", color = HubNight, fontWeight = FontWeight.Bold)
                                }
                                OutlinedButton(
                                    onClick = viewModel::rejectVoiceCall,
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Text("Отклонить")
                                }
                            }
                        }

                        "CALLING", "CONNECTING", "RECONNECTING", "CONNECTED" -> {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = viewModel::toggleVoiceMute,
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Text(if (state.voiceMuted) "Включить микрофон" else "Выключить микрофон")
                                }
                                Button(
                                    onClick = viewModel::hangupVoiceCall,
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(containerColor = HubError),
                                ) {
                                    Text("Завершить", color = HubNight, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        else -> {
                            PrimaryButton(
                                text = "Позвонить участнику",
                                enabled = state.controlPlaneUrl.startsWith("https://") &&
                                    state.squadOnlineUsers.any { it != state.localUserId },
                                onClick = onRequestAudioPermission,
                            )
                        }
                    }
                }
            }

        if (state.squadCode != null) {
            item {
                Panel {
                    Text("Чат отряда", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text(
                        if (state.controlPlaneUrl.startsWith("https://")) {
                            "Синхронизация через BOOSTLAB Control"
                        } else {
                            "Укажи Control API HTTPS в разделе «Я», чтобы включить сетевой чат."
                        },
                        color = HubMuted,
                        fontSize = 11.sp,
                    )
                    state.squadSyncError?.let {
                        Spacer(Modifier.height(6.dp))
                        Text(it, color = HubError, fontSize = 11.sp)
                    }
                    Spacer(Modifier.height(10.dp))

                    if (state.squadMessages.isEmpty()) {
                        Text("Сообщений пока нет.", color = HubMuted, fontSize = 12.sp)
                    } else {
                        state.squadMessages.takeLast(20).forEach { chat ->
                            val mine = chat.sender == state.localUserId
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                horizontalAlignment = if (mine) Alignment.End else Alignment.Start,
                            ) {
                                Text(
                                    if (mine) "Ты" else chat.sender,
                                    color = if (mine) HubCyan else HubMint,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(chat.text, color = Color.White, fontSize = 13.sp)
                            }
                        }
                    }

                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = message,
                        onValueChange = { message = it.take(1000) },
                        label = { Text("Сообщение") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    PrimaryButton(
                        text = "Отправить",
                        enabled = message.isNotBlank() && state.controlPlaneUrl.startsWith("https://"),
                        onClick = {
                            viewModel.sendSquadMessage(message)
                            message = ""
                        },
                    )
                }
            }
        }

        item {
            Panel {
                Text("Друзья", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = friend,
                    onValueChange = { friend = it },
                    label = { Text("Имя / ID друга") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = {
                        viewModel.addFriend(friend)
                        friend = ""
                    },
                    enabled = friend.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.Add, null)
                    Spacer(Modifier.width(6.dp))
                    Text("Добавить друга")
                }
                state.friends.forEach {
                    Text("• $it", color = Color.White, modifier = Modifier.padding(top = 8.dp))
                }
            }
        }
    }
}

@Composable
private fun ProfilePage(viewModel: BoostViewModel, state: BoostState, padding: PaddingValues) {
    var customDnsInput by rememberSaveable { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { PageHeader("Я", "Настройки BOOSTLAB · без VIP и платных ограничений") }
        item { InfoCard("Аккаунт", "Локальный профиль · ${state.localUserId} · все функции бесплатны") }

        item {
            Panel {
                Text("События", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                if (state.appEvents.isEmpty()) {
                    Text("Пока нет событий.", color = HubMuted, fontSize = 11.sp)
                } else {
                    state.appEvents.takeLast(5).reversed().forEach {
                        Text(it, color = HubMuted, fontSize = 10.sp, modifier = Modifier.padding(top = 6.dp))
                    }
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = viewModel::clearAppEvents) { Text("Очистить") }
                }
            }
        }

        item {
            Panel {
                Text("Network Boost", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Spacer(Modifier.height(8.dp))
                Text("Режим", color = HubMuted, fontSize = 11.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SelectChip("Smart", state.boostMode == "SMART") { viewModel.setBoostMode("SMART") }
                    SelectChip("Low Ping", state.boostMode == "LOW_PING") { viewModel.setBoostMode("LOW_PING") }
                    SelectChip("Stable", state.boostMode == "STABLE") { viewModel.setBoostMode("STABLE") }
                }
                Spacer(Modifier.height(10.dp))
                Text("Предпочтительный регион", color = HubMuted, fontSize = 11.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(
                        "AUTO" to "Авто",
                        "EUROPE" to "EU",
                        "ASIA" to "Asia",
                        "US" to "US",
                    ).forEach { (value, label) ->
                        SelectChip(label, state.preferredRegion == value) { viewModel.setPreferredRegion(value) }
                    }
                }
            }
        }

        item { SettingSwitch("Показывать ping", state.showPing, viewModel::setShowPing) }
        item { SettingSwitch("Автовыбор лучшего узла", state.autoSelectBestNode, viewModel::setAutoSelectBestNode) }
        item { SettingSwitch("Запустить игру после Network Boost", state.autoLaunchAfterNetworkBoost, viewModel::setAutoLaunchAfterNetworkBoost) }
        item { SettingSwitch("Подтвердить остановку", state.confirmStop, viewModel::setConfirmStop) }

        item {
            Panel {
                Text("DNS", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text("Основной: ${state.dnsServer}", color = HubMuted)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("1.1.1.1", "8.8.8.8", "9.9.9.9").forEach { dns ->
                        SelectChip(dns, !state.customDnsEnabled && state.dnsServer == dns) {
                            viewModel.setCustomDnsEnabled(false)
                            viewModel.selectDns(dns)
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Пользовательский DNS", color = Color.White, modifier = Modifier.weight(1f))
                    Switch(checked = state.customDnsEnabled, onCheckedChange = viewModel::setCustomDnsEnabled)
                }
                if (state.customDnsEnabled) {
                    Text("Можно добавить до 4 DNS-серверов.", color = HubMuted, fontSize = 11.sp)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = customDnsInput,
                        onValueChange = { customDnsInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("DNS, например 1.0.0.1") },
                        singleLine = true,
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = {
                            viewModel.addCustomDns(customDnsInput)
                            customDnsInput = ""
                        },
                        enabled = customDnsInput.isNotBlank() && state.customDnsServers.size < 4,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Добавить DNS") }
                    state.customDnsServers.forEach { dns ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(dns, color = HubCyan, modifier = Modifier.weight(1f))
                            TextButton(onClick = { viewModel.removeCustomDns(dns) }) { Text("Удалить") }
                        }
                    }
                }
            }
        }

        item { SettingSwitch("Включить отладочный лог", state.debugLogging, viewModel::setDebugLogging) }
        item {
            Panel {
                Text("Диагностика и Feedback", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text("Записей: ${state.diagnosticLogEntries.size}", color = HubMuted)
                state.diagnosticLogEntries.takeLast(3).forEach {
                    Text(it, color = HubMuted, fontSize = 10.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = viewModel::shareFeedbackReport, modifier = Modifier.fillMaxWidth()) {
                    Text("Сообщить о проблеме")
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = viewModel::exportDiagnosticLog, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.UploadFile, null)
                        Spacer(Modifier.width(6.dp))
                        Text("Экспорт")
                    }
                    OutlinedButton(onClick = viewModel::clearDiagnosticLog, modifier = Modifier.weight(1f)) {
                        Text("Очистить")
                    }
                }
            }
        }

        item {
            Panel {
                Text("Серверы / Nodes", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text(state.serverLabel, color = HubMuted, fontSize = 12.sp)
                Text(
                    "Выбрано: ${state.selectedGatewayRegion ?: "—"} · ${state.selectedGatewayId ?: "—"}",
                    color = HubMuted,
                    fontSize = 11.sp,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = state.controlPlaneUrl,
                    onValueChange = viewModel::updateControlPlaneUrl,
                    label = { Text("Control API HTTPS") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = state.gatewayHost,
                    onValueChange = viewModel::updateGatewayHost,
                    label = { Text("Gateway host") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = state.wireGuardServerPublicKey,
                    onValueChange = viewModel::updateWireGuardServerPublicKey,
                    label = { Text("WireGuard public key") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = state.tunnelAddress,
                    onValueChange = viewModel::updateTunnelAddress,
                    label = { Text("Tunnel address") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = viewModel::autoSelectGateway, modifier = Modifier.weight(1f)) {
                        Text("Автовыбор")
                    }
                    OutlinedButton(onClick = viewModel::probeGateway, modifier = Modifier.weight(1f)) {
                        Text("Ping")
                    }
                    OutlinedButton(onClick = viewModel::discoverLanGateway, modifier = Modifier.weight(1f)) {
                        Text("LAN")
                    }
                }
            }
        }

        item {
            Panel {
                Text("О приложении", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text(
                    "Игры, Network Boost, Nodes, DNS, Ping Test, Boost Report, Logs, Search, Feedback, Squad и профиль доступны без VIP.",
                    color = HubMuted,
                    fontSize = 11.sp,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = viewModel::shareApp, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Share, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Поделиться BOOSTLAB")
                }
            }
        }
    }
}

@Composable
private fun NetworkHero(state: BoostState) {
    Panel {
        Text(if (state.isBoosting) "NETWORK BOOST ON" else "LOCAL BOOST", color = if (state.isBoosting) HubMint else HubCyan, fontWeight = FontWeight.Black, fontSize = 13.sp)
        Spacer(Modifier.height(4.dp))
        Text(state.selectedApp?.label ?: "Выбери приложение", color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.Black)
        Text(state.serverLabel, color = HubMuted, fontSize = 12.sp)
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetricBox("PING", if (state.showPing) state.pingMs?.let { "$it ms" } ?: "—" else "скрыт", Modifier.weight(1f))
            MetricBox("JITTER", state.jitterMs?.let { "$it ms" } ?: "—", Modifier.weight(1f))
            MetricBox("LOSS", state.packetLossPct?.let { String.format(Locale.US, "%.1f%%", it) } ?: "—", Modifier.weight(1f))
        }
    }
}

@Composable
private fun SelectedAppCard(app: BoostApp, state: BoostState, onProfile: () -> Unit) {
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppIcon(app, Modifier.size(64.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(app.label, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text(state.gameBoostMessage, color = HubMuted, fontSize = 11.sp)
            }
        }
        Spacer(Modifier.height(10.dp))
        OutlinedButton(onClick = onProfile, modifier = Modifier.fillMaxWidth()) {
            Text("Профиль: ${state.gameLaunchMode.title} · ${state.gameLaunchMode.description}")
        }
        state.gameLaunchError?.let { Text(it, color = HubError, fontSize = 11.sp) }
    }
}

@Composable
private fun InstalledAppRow(app: BoostApp, pinned: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(HubPanel).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(app, Modifier.size(50.dp))
        Spacer(Modifier.width(10.dp))
        Text(app.label, color = Color.White, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        OutlinedButton(onClick = onToggle) {
            Icon(if (pinned) Icons.Default.Check else Icons.Default.Add, null)
            Spacer(Modifier.width(4.dp))
            Text(if (pinned) "Добавлено" else "Добавить")
        }
    }
}

@Composable
private fun CatalogRow(game: CatalogGame, installed: BoostApp?, onAction: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier.size(52.dp).clip(RoundedCornerShape(14.dp))
                .background(Brush.linearGradient(listOf(Color(0xFF14314A), Color(0xFF1A1639)))),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Default.Gamepad, null, tint = HubCyan) }
        Spacer(Modifier.width(12.dp))
        Text(game.title, color = Color.White, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Button(
            onClick = onAction,
            colors = ButtonDefaults.buttonColors(containerColor = if (installed != null) HubCyan else Color(0xFF10324A)),
        ) {
            Icon(if (installed != null) Icons.Default.Bolt else Icons.Default.Download, null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text(if (installed != null) "Буст" else "Скачать", color = if (installed != null) HubNight else HubCyan)
        }
    }
}

@Composable
private fun LocalBoostRow(app: BoostApp, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
            .background(if (selected) Color(0xFF132B3A) else HubPanel)
            .border(1.dp, if (selected) HubCyan else Color(0xFF202838), RoundedCornerShape(20.dp))
            .clickable(onClick = onClick).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(app, Modifier.size(54.dp))
        Spacer(Modifier.width(10.dp))
        Text(app.label, color = Color.White, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Button(onClick = onClick, colors = ButtonDefaults.buttonColors(containerColor = HubCyan)) {
            Text("Буст", color = HubNight)
        }
    }
}

@Composable
private fun AppIcon(app: BoostApp, modifier: Modifier) {
    Box(modifier = modifier.clip(RoundedCornerShape(14.dp)).background(HubPanel2), contentAlignment = Alignment.Center) {
        if (app.icon != null) Image(app.icon.asImageBitmap(), app.label, Modifier.fillMaxSize())
        else Icon(Icons.Default.Gamepad, null, tint = HubCyan)
    }
}

@Composable
private fun SettingSwitch(title: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(HubPanel).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.Settings, null, tint = HubCyan)
        Spacer(Modifier.width(10.dp))
        Text(title, color = Color.White, modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}

@Composable
private fun InfoCard(title: String, body: String) {
    Panel {
        Text(title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text(body, color = HubMuted, fontSize = 12.sp)
    }
}

@Composable
private fun Panel(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp))
            .background(HubPanel)
            .border(1.dp, Color(0xFF242D3E), RoundedCornerShape(22.dp))
            .padding(16.dp),
        content = content,
    )
}

@Composable
private fun PrimaryButton(text: String, enabled: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().height(54.dp),
        colors = ButtonDefaults.buttonColors(containerColor = HubCyan, disabledContainerColor = Color(0xFF30404C)),
    ) {
        Icon(Icons.Default.Bolt, null, tint = HubNight)
        Spacer(Modifier.width(7.dp))
        Text(text, color = HubNight, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun SelectChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier.clip(RoundedCornerShape(12.dp))
            .background(if (selected) Color(0xFF173D46) else Color.Transparent)
            .border(1.dp, if (selected) HubCyan else Color(0xFF293243), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(text, color = if (selected) HubCyan else HubMuted, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun MetricBox(title: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.clip(RoundedCornerShape(14.dp)).background(HubPanel2).padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, color = HubMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
        Text(value, color = HubCyan, fontSize = 13.sp, fontWeight = FontWeight.Black, maxLines = 1)
    }
}

private fun findInstalled(game: CatalogGame, apps: List<BoostApp>): BoostApp? {
    val wanted = normalize(game.title)
    return apps.firstOrNull {
        val actual = normalize(it.label)
        actual == wanted ||
            (wanted.length >= 6 && actual.contains(wanted)) ||
            (actual.length >= 6 && wanted.contains(actual))
    }
}

private fun normalize(value: String): String =
    value.lowercase(Locale.ROOT).replace(Regex("[^a-zа-я0-9]+"), "")

private fun trafficLabel(bytes: Long): String = when {
    bytes >= 1_048_576L -> String.format(Locale.US, "%.1f MB", bytes / 1_048_576.0)
    bytes >= 1_024L -> String.format(Locale.US, "%.1f KB", bytes / 1_024.0)
    else -> bytes.toString() + " B"
}

private fun thermalLabel(status: Int?): String = when (status) {
    null -> "—"
    0 -> "OK"
    1 -> "LIGHT"
    2 -> "WARM"
    3 -> "HOT"
    4 -> "SEVERE"
    else -> "CRIT"
}


private fun formatDuration(seconds: Long): String {
    val safe = seconds.coerceAtLeast(0L)
    val hours = safe / 3600L
    val minutes = (safe % 3600L) / 60L
    val secs = safe % 60L
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, secs)
    } else {
        String.format(Locale.US, "%d:%02d", minutes, secs)
    }
}


private fun voiceStateLabel(state: String): String = when (state) {
    "RINGING" -> "входящий звонок"
    "CALLING" -> "вызываем"
    "CONNECTING" -> "соединяем"
    "RECONNECTING" -> "восстанавливаем связь"
    "CONNECTED" -> "соединено"
    "FAILED" -> "ошибка"
    else -> "готов"
}
