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
                text = "Локальный буст",
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
                    Text("Stage 1", color = Cyan, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = state.selectedApp?.let { "Выбрано: ${it.label}" }
                            ?: "Выбери приложение для ускорения",
                        color = Color.White,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = state.serverLabel,
                        color = Color(0xFF9FB4C9),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(Modifier.height(14.dp))
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
                        Text(if (state.isBoosting) " Остановить" else " Буст")
                    }
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
