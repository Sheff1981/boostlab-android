package com.boostlab.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.boostlab.app.model.BoostApp
import com.boostlab.app.model.BoostState
import java.util.Locale

private val BgTop = Color(0xFF071426)
private val BgBottom = Color(0xFF0B2038)
private val Cyan = Color(0xFF28E7F0)
private val Purple = Color(0xFF7A5CFF)
private val CardBg = Color(0xFF17314F)
private val Muted = Color(0xFF9FB4C9)
private val Error = Color(0xFFFFA8A8)

@Composable
fun BoostScreen(
    viewModel: BoostViewModel,
    onRequestVpnPermission: () -> Unit,
) {
    val state by viewModel.state.collectAsState()

    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Cyan,
            secondary = Purple,
            background = BgTop,
            surface = CardBg,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(BgTop, BgBottom))),
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    Column {
                        Text(
                            text = "BOOSTLAB",
                            color = Color.White,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "Игровой бустер",
                            color = Muted,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }

                item {
                    MainBoostCard(
                        state = state,
                        viewModel = viewModel,
                        onRequestVpnPermission = onRequestVpnPermission,
                    )
                }

                item {
                    QualityCard(state)
                }

                item {
                    Text(
                        text = "Выбери игру",
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                    )
                }

                items(viewModel.apps, key = { it.packageName }) { app ->
                    AppRow(
                        app = app,
                        selected = state.selectedApp?.packageName == app.packageName,
                        onClick = { viewModel.selectApp(app) },
                    )
                }

                item {
                    OutlinedButton(
                        onClick = viewModel::toggleAdvancedSettings,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            if (state.showAdvancedSettings) {
                                "Скрыть настройки сервера"
                            } else {
                                "Настройки сервера"
                            },
                        )
                    }
                }

                if (state.showAdvancedSettings) {
                    item {
                        AdvancedServerCard(
                            state = state,
                            viewModel = viewModel,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MainBoostCard(
    state: BoostState,
    viewModel: BoostViewModel,
    onRequestVpnPermission: () -> Unit,
) {
    val serverReady =
        state.gatewayHost.isNotBlank() &&
            state.wireGuardServerPublicKey.isNotBlank() &&
            state.clientPublicKey != null

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        shape = RoundedCornerShape(22.dp),
    ) {
        Column(Modifier.padding(18.dp)) {
            Text(
                text = state.selectedApp?.label ?: "Игра не выбрана",
                color = Color.White,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )

            Spacer(Modifier.height(6.dp))

            Text(
                text = when {
                    state.isBoosting -> "Буст активен"
                    !serverReady -> "Сервер нужно настроить один раз"
                    else -> "Готов к запуску"
                },
                color = when {
                    state.isBoosting -> Cyan
                    serverReady -> Color.White
                    else -> Muted
                },
            )

            if (!serverReady) {
                Text(
                    text = "Открой «Настройки сервера» ниже. После настройки они больше не понадобятся.",
                    color = Muted,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }

            state.tunnelError?.let {
                Text(
                    text = it,
                    color = Error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            Spacer(Modifier.height(14.dp))

            Button(
                onClick = {
                    if (state.isBoosting) {
                        viewModel.disconnectTunnel()
                    } else {
                        onRequestVpnPermission()
                    }
                },
                enabled = !state.isTunnelConnecting &&
                    (
                        state.isBoosting ||
                            (
                                state.selectedApp != null &&
                                    serverReady
                                )
                        ),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (state.isBoosting) Purple else Cyan,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                androidx.compose.material3.Icon(
                    imageVector = Icons.Default.Bolt,
                    contentDescription = null,
                )
                Text(
                    when {
                        state.isTunnelConnecting -> " Подключаем…"
                        state.isBoosting -> " Отключить"
                        else -> " Буст"
                    },
                )
            }
        }
    }
}

@Composable
private fun QualityCard(state: BoostState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF102842)),
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = state.serverLabel,
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
            )

            Spacer(Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MetricCard(
                    title = "PING",
                    value = state.pingMs?.let { "${it} ms" } ?: "—",
                    modifier = Modifier.weight(1f),
                )
                MetricCard(
                    title = "JITTER",
                    value = state.jitterMs?.let { "${it} ms" } ?: "—",
                    modifier = Modifier.weight(1f),
                )
                MetricCard(
                    title = "LOSS",
                    value = state.packetLossPct?.let {
                        String.format(Locale.US, "%.1f%%", it)
                    } ?: "—",
                    modifier = Modifier.weight(1f),
                )
            }

            state.probeError?.let {
                Text(
                    text = it,
                    color = Error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun AdvancedServerCard(
    state: BoostState,
    viewModel: BoostViewModel,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        shape = RoundedCornerShape(22.dp),
    ) {
        Column(Modifier.padding(18.dp)) {
            Text(
                text = "Настройка сервера",
                color = Cyan,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "Этот раздел нужен только при первой настройке или смене сервера.",
                color = Muted,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp),
            )

            Spacer(Modifier.height(14.dp))

            Button(
                onClick = viewModel::discoverLanGateway,
                enabled = !state.isLanDiscovering &&
                    !state.isAutoSelecting &&
                    !state.isProbing,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    if (state.isLanDiscovering) {
                        "Ищем в Wi-Fi…"
                    } else {
                        "Найти локальный тестовый сервер"
                    },
                )
            }

            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = state.controlPlaneUrl,
                onValueChange = viewModel::updateControlPlaneUrl,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Адрес сервиса серверов") },
                placeholder = { Text("https://...") },
            )

            Button(
                onClick = viewModel::autoSelectGateway,
                enabled = state.controlPlaneUrl.startsWith("https://") &&
                    !state.isAutoSelecting,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    if (state.isAutoSelecting) {
                        "Ищем лучший сервер…"
                    } else {
                        "Найти лучший сервер"
                    },
                )
            }

            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = state.gatewayHost,
                onValueChange = viewModel::updateGatewayHost,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Сервер") },
            )

            Button(
                onClick = viewModel::probeGateway,
                enabled = state.gatewayHost.isNotBlank() &&
                    !state.isProbing &&
                    !state.isAutoSelecting,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (state.isProbing) "Проверяем…" else "Проверить сервер")
            }

            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = state.wireGuardServerPublicKey,
                onValueChange = viewModel::updateWireGuardServerPublicKey,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Публичный ключ сервера") },
            )

            OutlinedTextField(
                value = state.tunnelAddress,
                onValueChange = viewModel::updateTunnelAddress,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Адрес телефона в туннеле") },
            )

            OutlinedTextField(
                value = state.dnsServer,
                onValueChange = viewModel::updateDnsServer,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("DNS") },
            )

            Spacer(Modifier.height(12.dp))

            Text(
                text = "Публичный ключ телефона",
                color = Muted,
                style = MaterialTheme.typography.labelMedium,
            )
            SelectionContainer {
                Text(
                    text = state.clientPublicKey ?: "Ключ недоступен",
                    color = Color.White,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            state.identityError?.let {
                Text(
                    text = it,
                    color = Error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun MetricCard(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0C2138)),
        shape = RoundedCornerShape(14.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = title,
                color = Color(0xFF8EA7BD),
                style = MaterialTheme.typography.labelSmall,
            )
            Text(
                text = value,
                color = Color.White,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun AppRow(
    app: BoostApp,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) Color(0xFF214C68) else CardBg,
        ),
        shape = RoundedCornerShape(18.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = app.label,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = app.packageName,
                    color = Color(0xFF94A9BE),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Text(
                text = if (selected) "Выбрано" else "Выбрать",
                color = Cyan,
            )
        }
    }
}
