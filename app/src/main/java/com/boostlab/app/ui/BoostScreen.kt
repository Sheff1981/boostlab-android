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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(BgTop, BgBottom))),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp, vertical = 20.dp),
        ) {
            Text(
                text = "BOOSTLAB",
                color = Color.White,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "Маршрут для выбранного приложения",
                color = Color(0xFF9FB4C9),
                style = MaterialTheme.typography.bodyMedium,
            )

            Spacer(Modifier.height(18.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = CardBg),
                shape = RoundedCornerShape(22.dp),
            ) {
                Column(Modifier.padding(18.dp)) {
                    Text("Stage 2 · измерение маршрута", color = Cyan, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(10.dp))

                    OutlinedTextField(
                        value = state.gatewayHost,
                        onValueChange = viewModel::updateGatewayHost,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("IP или имя сервера") },
                        placeholder = { Text("например 203.0.113.10") },
                        supportingText = {
                            Text("UDP-порт ${state.gatewayPort}")
                        },
                    )

                    Button(
                        onClick = viewModel::probeGateway,
                        enabled = state.gatewayHost.isNotBlank() && !state.isProbing,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(if (state.isProbing) "Проверяем…" else "Проверить сервер")
                    }

                    Spacer(Modifier.height(10.dp))
                    Text(state.serverLabel, color = Color.White)

                    state.probeError?.let {
                        Spacer(Modifier.height(4.dp))
                        Text(it, color = Color(0xFFFFA8A8), style = MaterialTheme.typography.bodySmall)
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

                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = state.selectedApp?.let { "Выбрано: ${it.label}" }
                            ?: "Выбери приложение ниже",
                        color = Color.White,
                    )
                    Spacer(Modifier.height(10.dp))

                    Button(
                        onClick = {
                            if (state.isBoosting) {
                                viewModel.stopBooster()
                            } else {
                                onRequestVpnPermission()
                            }
                        },
                        enabled = state.selectedApp != null,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (state.isBoosting) Purple else Cyan,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        androidx.compose.material3.Icon(
                            Icons.Default.Bolt,
                            contentDescription = null,
                        )
                        Text(if (state.isBoosting) " Остановить" else " Подготовить VPN")
                    }

                    Text(
                        text = "Трафик пока не перенаправляется: сначала проверяем реальный gateway.",
                        color = Color(0xFF9FB4C9),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }

            Spacer(Modifier.height(18.dp))
            Text("Приложения", color = Color.White, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))

            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
            Text(title, color = Color(0xFF8EA7BD), style = MaterialTheme.typography.labelSmall)
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
