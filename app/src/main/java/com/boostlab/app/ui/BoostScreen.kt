package com.boostlab.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import com.boostlab.app.model.PlanTier
import java.util.Locale

private val BgTop = Color(0xFF071426)
private val BgBottom = Color(0xFF0B2038)
private val Cyan = Color(0xFF28E7F0)
private val Purple = Color(0xFF7A5CFF)
private val CardBg = Color(0xFF17314F)

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
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    horizontal = 18.dp,
                    vertical = 20.dp,
                ),
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
                            text = "Per-app encrypted route optimizer",
                            color = Color(0xFF9FB4C9),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }

                item {
                    PlanCard(state)
                }

                if (state.adsEnabled && !state.isBoosting) {
                    item {
                        AdPlaceholderCard()
                    }
                }

                item {
                    RouteCard(
                        state = state,
                        viewModel = viewModel,
                    )
                }

                item {
                    TunnelCard(
                        state = state,
                        viewModel = viewModel,
                        onRequestVpnPermission = onRequestVpnPermission,
                    )
                }

                item {
                    Text(
                        "Приложения",
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
            }
        }
    }
}


@Composable
private fun PlanCard(
    state: com.boostlab.app.model.BoostState,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF102842)),
        shape = RoundedCornerShape(18.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = if (state.planTier == PlanTier.PREMIUM) {
                        "BOOSTLAB Premium"
                    } else {
                        "BOOSTLAB Free"
                    },
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = if (state.planTier == PlanTier.PREMIUM) {
                        "Без рекламы · приоритетные маршруты"
                    } else {
                        "Бесплатный доступ · реклама оплачивает инфраструктуру"
                    },
                    color = Color(0xFF9FB4C9),
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            Text(
                text = if (state.planTier == PlanTier.PREMIUM) "PREMIUM" else "FREE",
                color = Cyan,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun AdPlaceholderCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0D243B)),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
        ) {
            Text(
                text = "Реклама",
                color = Color(0xFF8EA7BD),
                style = MaterialTheme.typography.labelSmall,
            )
            Text(
                text = "Рекламный блок будет включён только в бесплатной релизной версии.",
                color = Color.White,
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                text = "Во время активного буста реклама скрыта.",
                color = Color(0xFF9FB4C9),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun RouteCard(
    state: com.boostlab.app.model.BoostState,
    viewModel: BoostViewModel,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        shape = RoundedCornerShape(22.dp),
    ) {
        Column(Modifier.padding(18.dp)) {
            Text(
                "Stage 4 · выбор маршрута",
                color = Cyan,
                fontWeight = FontWeight.Bold,
            )

            Spacer(Modifier.height(10.dp))

            OutlinedTextField(
                value = state.controlPlaneUrl,
                onValueChange = viewModel::updateControlPlaneUrl,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Control API (HTTPS)") },
                placeholder = { Text("https://control.example.com") },
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
                        "Выбрать лучший автоматически"
                    },
                )
            }

            if (state.discoveredNodes > 0) {
                Text(
                    text = "Найдено серверов: ${state.discoveredNodes}",
                    color = Color(0xFF9FB4C9),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }

            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = state.gatewayHost,
                onValueChange = viewModel::updateGatewayHost,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Gateway IP / host") },
                supportingText = { Text("Probe UDP ${state.gatewayPort}") },
            )

            Button(
                onClick = viewModel::probeGateway,
                enabled = state.gatewayHost.isNotBlank() &&
                    !state.isProbing &&
                    !state.isAutoSelecting,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (state.isProbing) "Проверяем…" else "Проверить маршрут")
            }

            Spacer(Modifier.height(8.dp))
            Text(state.serverLabel, color = Color.White)

            state.probeError?.let {
                Text(
                    text = it,
                    color = Color(0xFFFFA8A8),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            Spacer(Modifier.height(12.dp))

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
        }
    }
}

@Composable
private fun TunnelCard(
    state: com.boostlab.app.model.BoostState,
    viewModel: BoostViewModel,
    onRequestVpnPermission: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        shape = RoundedCornerShape(22.dp),
    ) {
        Column(Modifier.padding(18.dp)) {
            Text(
                "WireGuard · выбранное приложение",
                color = Cyan,
                fontWeight = FontWeight.Bold,
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = state.selectedApp?.let { "Приложение: ${it.label}" }
                    ?: "Сначала выбери приложение ниже",
                color = Color.White,
            )

            Spacer(Modifier.height(10.dp))
            Text(
                "Публичный ключ этого телефона",
                color = Color(0xFF9FB4C9),
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
                    color = Color(0xFFFFA8A8),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            Spacer(Modifier.height(10.dp))

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
                supportingText = { Text("Например 10.77.0.2/32") },
            )

            OutlinedTextField(
                value = state.dnsServer,
                onValueChange = viewModel::updateDnsServer,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("DNS через туннель") },
            )

            Text(
                text = "WireGuard UDP ${state.wireGuardPort}",
                color = Color(0xFF9FB4C9),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 6.dp),
            )

            state.tunnelError?.let {
                Text(
                    text = it,
                    color = Color(0xFFFFA8A8),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }

            Spacer(Modifier.height(12.dp))

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
                                    state.gatewayHost.isNotBlank() &&
                                    state.wireGuardServerPublicKey.isNotBlank() &&
                                    state.clientPublicKey != null
                                )
                        ),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (state.isBoosting) Purple else Cyan,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                androidx.compose.material3.Icon(
                    Icons.Default.Bolt,
                    contentDescription = null,
                )
                Text(
                    when {
                        state.isTunnelConnecting -> " Подключаем…"
                        state.isBoosting -> " Отключить буст"
                        else -> " Подключить буст"
                    },
                )
            }

            Text(
                text = if (state.isBoosting) {
                    "WireGuard поднят. Через него направляется только выбранное приложение."
                } else {
                    "Туннель включится только после настройки реального WireGuard peer на gateway."
                },
                color = Color(0xFF9FB4C9),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 8.dp),
            )
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
        colors = CardDefaults.cardColors(containerColor = Color(0xFF102842)),
        shape = RoundedCornerShape(14.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                title,
                color = Color(0xFF8EA7BD),
                style = MaterialTheme.typography.labelSmall,
            )
            Text(value, color = Color.White, fontWeight = FontWeight.Bold)
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
                    app.label,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    app.packageName,
                    color = Color(0xFF94A9BE),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Text(if (selected) "Выбрано" else "Выбрать", color = Cyan)
        }
    }
}
