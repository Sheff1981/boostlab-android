package com.boostlab.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.boostlab.app.model.BoostApp
import com.boostlab.app.model.BoostState
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

private val Night = Color(0xFF020817)
private val Navy = Color(0xFF06152B)
private val Panel = Color(0xD9142C48)
private val PanelDeep = Color(0xE90A1830)
private val Cyan = Color(0xFF00E7FF)
private val Blue = Color(0xFF2589FF)
private val Violet = Color(0xFF7B4DFF)
private val Magenta = Color(0xFFFF40E6)
private val Mint = Color(0xFF22E6A8)
private val Muted = Color(0xFF93A8C4)
private val Error = Color(0xFFFF8F9A)
private val NeonGradient = Brush.horizontalGradient(listOf(Cyan, Blue, Violet, Magenta))
private val ScreenGradient = Brush.verticalGradient(
    listOf(
        Color(0xFF031027),
        Color(0xFF020817),
        Color(0xFF030714),
    ),
)

@Composable
fun BoostScreen(
    viewModel: BoostViewModel,
    onRequestVpnPermission: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }

    val filteredApps = remember(viewModel.apps, query) {
        if (query.isBlank()) {
            viewModel.apps
        } else {
            viewModel.apps.filter {
                it.label.contains(query, ignoreCase = true) ||
                    it.packageName.contains(query, ignoreCase = true)
            }
        }
    }

    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Cyan,
            secondary = Violet,
            background = Night,
            surface = PanelDeep,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(ScreenGradient),
        ) {
            Scaffold(
                containerColor = Color.Transparent,
                bottomBar = { BoostBottomBar() },
            ) { scaffoldPadding ->
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(scaffoldPadding),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 14.dp,
                        bottom = 18.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    item {
                        BrandHeader(
                            onSettingsClick = viewModel::toggleAdvancedSettings,
                        )
                    }

                    item {
                        HeroNetworkSection()
                    }

                    item {
                        SelectedGameCard(
                            state = state,
                            onBoostClick = {
                                if (state.isBoosting) {
                                    viewModel.disconnectTunnel()
                                } else {
                                    onRequestVpnPermission()
                                }
                            },
                        )
                    }

                    item {
                        MetricsRow(state)
                    }

                    item {
                        GamePickerHeader(
                            query = query,
                            onQueryChange = { query = it },
                        )
                    }

                    items(filteredApps, key = { it.packageName }) { app ->
                        GameRow(
                            app = app,
                            selected = state.selectedApp?.packageName == app.packageName,
                            onClick = { viewModel.selectApp(app) },
                        )
                    }

                    item {
                        ServerSettingsButton(
                            expanded = state.showAdvancedSettings,
                            onClick = viewModel::toggleAdvancedSettings,
                        )
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
}

@Composable
private fun BrandHeader(
    onSettingsClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BoostLabMark(
            modifier = Modifier.size(52.dp),
        )

        Spacer(Modifier.width(10.dp))

        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = "BOOST",
                    color = Color.White,
                    fontSize = 27.sp,
                    lineHeight = 27.sp,
                    fontWeight = FontWeight.Black,
                    fontStyle = FontStyle.Italic,
                )
                Text(
                    text = "LAB",
                    color = Cyan,
                    fontSize = 27.sp,
                    lineHeight = 27.sp,
                    fontWeight = FontWeight.Black,
                    fontStyle = FontStyle.Italic,
                )
            }
            Text(
                text = "Игровой бустер",
                color = Muted,
                fontSize = 14.sp,
            )
        }

        Box(
            modifier = Modifier
                .size(48.dp)
                .shadow(18.dp, RoundedCornerShape(16.dp))
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0x99102243))
                .border(1.dp, Brush.linearGradient(listOf(Cyan, Violet)), RoundedCornerShape(16.dp))
                .clickable(onClick = onSettingsClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = "Настройки",
                tint = Color.White,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

@Composable
private fun BoostLabMark(
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        drawLine(
            color = Cyan,
            start = Offset(w * 0.18f, h * 0.18f),
            end = Offset(w * 0.72f, h * 0.30f),
            strokeWidth = w * 0.16f,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = Blue,
            start = Offset(w * 0.72f, h * 0.30f),
            end = Offset(w * 0.48f, h * 0.82f),
            strokeWidth = w * 0.16f,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = Cyan,
            start = Offset(w * 0.48f, h * 0.82f),
            end = Offset(w * 0.28f, h * 0.50f),
            strokeWidth = w * 0.14f,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = Violet,
            start = Offset(w * 0.28f, h * 0.50f),
            end = Offset(w * 0.58f, h * 0.50f),
            strokeWidth = w * 0.10f,
            cap = StrokeCap.Round,
        )
    }
}

@Composable
private fun HeroNetworkSection() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(240.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        Color(0xFF06152D),
                        Color(0xFF06102A),
                        Color(0xFF10072A),
                    ),
                ),
            )
            .border(
                1.dp,
                Brush.linearGradient(
                    listOf(Color(0x4434E8FF), Color(0x553C72FF), Color(0x337D48FF)),
                ),
                RoundedCornerShape(28.dp),
            ),
    ) {
        NetworkGlobe(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .size(228.dp)
                .alpha(0.98f),
        )

        Column(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 18.dp, top = 16.dp, bottom = 16.dp)
                .fillMaxWidth(0.55f),
        ) {
            Text(
                text = "БОЛЬШЕ",
                color = Color.White,
                fontSize = 28.sp,
                lineHeight = 29.sp,
                fontWeight = FontWeight.Black,
                fontStyle = FontStyle.Italic,
            )
            Text(
                text = "ПОБЕД",
                color = Color.White,
                fontSize = 28.sp,
                lineHeight = 29.sp,
                fontWeight = FontWeight.Black,
                fontStyle = FontStyle.Italic,
            )
            Text(
                text = "БЕЗ ЛАГОВ",
                color = Cyan,
                fontSize = 28.sp,
                lineHeight = 31.sp,
                fontWeight = FontWeight.Black,
                fontStyle = FontStyle.Italic,
            )

            Spacer(Modifier.height(14.dp))

            HeroFeature(
                icon = Icons.Default.Bolt,
                text = "Стабильный\nпинг",
            )
            HeroFeature(
                icon = Icons.Default.NetworkCheck,
                text = "Умная\nмаршрутизация",
            )
            HeroFeature(
                icon = Icons.Default.Shield,
                text = "Более стабильная\nигра",
            )
        }
    }
}

@Composable
private fun HeroFeature(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 3.dp),
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(Color(0x88204A69))
                .border(1.dp, Color(0x8856E7FF), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Cyan,
                modifier = Modifier.size(17.dp),
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = text,
            color = Color.White,
            fontSize = 12.sp,
            lineHeight = 14.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun NetworkGlobe(
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val center = Offset(size.width * 0.58f, size.height * 0.50f)
        val radius = size.minDimension * 0.36f

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0x66454DFF),
                    Color(0x443700FF),
                    Color.Transparent,
                ),
                center = center,
                radius = radius * 1.45f,
            ),
            radius = radius * 1.45f,
            center = center,
        )

        drawCircle(
            color = Color(0xFF0A2250),
            radius = radius,
            center = center,
        )
        drawCircle(
            color = Color(0xAA24DFFF),
            radius = radius,
            center = center,
            style = Stroke(width = 2.4.dp.toPx()),
        )

        for (fraction in listOf(0.35f, 0.62f, 0.84f)) {
            drawOval(
                color = Color(0x6653A8FF),
                topLeft = Offset(
                    center.x - radius,
                    center.y - radius * fraction,
                ),
                size = Size(radius * 2f, radius * fraction * 2f),
                style = Stroke(width = 1.dp.toPx()),
            )
        }

        for (angle in listOf(-62f, -28f, 18f, 58f)) {
            drawArc(
                color = if (angle < 0) Cyan else Violet,
                startAngle = angle,
                sweepAngle = 150f,
                useCenter = false,
                topLeft = Offset(
                    center.x - radius * 1.18f,
                    center.y - radius * 1.18f,
                ),
                size = Size(radius * 2.36f, radius * 2.36f),
                style = Stroke(
                    width = 2.2.dp.toPx(),
                    cap = StrokeCap.Round,
                ),
            )
        }

        val nodeAngles = listOf(205f, 290f, 42f, 115f)
        nodeAngles.forEachIndexed { index, degrees ->
            val radians = Math.toRadians(degrees.toDouble())
            val node = Offset(
                x = center.x + cos(radians).toFloat() * radius * 0.95f,
                y = center.y + sin(radians).toFloat() * radius * 0.95f,
            )
            val nodeColor = if (index % 2 == 0) Cyan else Magenta
            drawCircle(
                color = Color(0x4411DFFF),
                radius = 15.dp.toPx(),
                center = node,
            )
            drawCircle(
                color = nodeColor,
                radius = 4.5.dp.toPx(),
                center = node,
            )
        }

        drawCircle(
            brush = Brush.radialGradient(
                listOf(Color(0xFF1A8FFF), Color(0xFF0A2857)),
                center = center,
                radius = 34.dp.toPx(),
            ),
            radius = 32.dp.toPx(),
            center = center,
        )
        drawCircle(
            color = Cyan,
            radius = 32.dp.toPx(),
            center = center,
            style = Stroke(width = 1.6.dp.toPx()),
        )
    }

    Box(
        modifier = Modifier
            .padding(top = 24.dp, end = 124.dp),
    ) {
        RegionChip(
            label = "EU",
            value = "12 ms",
            color = Cyan,
        )
    }
}

@Composable
private fun RegionChip(
    label: String,
    value: String,
    color: Color,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xE70B1E39))
            .border(1.dp, color.copy(alpha = 0.85f), RoundedCornerShape(14.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(color),
        )
        Spacer(Modifier.width(6.dp))
        Column {
            Text(
                text = label,
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = value,
                color = Mint,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun SelectedGameCard(
    state: BoostState,
    onBoostClick: () -> Unit,
) {
    val selected = state.selectedApp
    val serverReady =
        state.gatewayHost.isNotBlank() &&
            state.wireGuardServerPublicKey.isNotBlank() &&
            state.clientPublicKey != null
    val boostEnabled =
        !state.isTunnelConnecting &&
            !state.isAutoSelecting &&
            !state.isProbing &&
            !state.isLanDiscovering &&
            (
                state.isBoosting ||
                    (selected != null && serverReady)
                )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(24.dp, RoundedCornerShape(28.dp))
            .clip(RoundedCornerShape(28.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        Color(0xFF0B3258),
                        Color(0xFF112A55),
                        Color(0xFF24113E),
                    ),
                ),
            )
            .border(1.2.dp, NeonGradient, RoundedCornerShape(28.dp)),
    ) {
        NetworkCardGlow()

        Column(Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppIcon(
                    app = selected,
                    modifier = Modifier.size(82.dp),
                )

                Spacer(Modifier.width(14.dp))

                Column(Modifier.weight(1f)) {
                    Text(
                        text = selected?.label ?: "Выбери игру",
                        color = Color.White,
                        fontSize = 21.sp,
                        lineHeight = 24.sp,
                        fontWeight = FontWeight.Black,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )

                    Spacer(Modifier.height(6.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(9.dp)
                                .clip(CircleShape)
                                .background(
                                    when {
                                        state.isBoosting -> Mint
                                        serverReady -> Mint
                                        else -> Color(0xFFFFB04A)
                                    },
                                ),
                        )
                        Spacer(Modifier.width(7.dp))
                        Text(
                            text = when {
                                state.isBoosting -> "Буст активен"
                                state.isAutoSelecting || state.isProbing -> "Подбираем сервер…"
                                serverReady -> "Готов к запуску"
                                else -> "Сервер не настроен"
                            },
                            color = when {
                                state.isBoosting || serverReady -> Mint
                                else -> Color(0xFFFFC16F)
                            },
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                        )
                    }

                    Spacer(Modifier.height(8.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SmallPill(
                            icon = Icons.Default.NetworkCheck,
                            text = if (state.selectedGatewayId != null) "Авто сервер" else "Сервер",
                        )
                        SmallPill(
                            icon = Icons.Default.Public,
                            text = state.selectedGatewayRegion ?: "Авто",
                        )
                    }
                }
            }

            state.tunnelError?.let {
                Text(
                    text = it,
                    color = Error,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }

            Spacer(Modifier.height(14.dp))

            GradientBoostButton(
                text = when {
                    state.isTunnelConnecting -> "Подключаем…"
                    state.isBoosting -> "Отключить"
                    else -> "Буст"
                },
                enabled = boostEnabled,
                active = state.isBoosting,
                onClick = onBoostClick,
            )
        }
    }
}

@Composable
private fun NetworkCardGlow() {
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp),
    ) {
        drawCircle(
            brush = Brush.radialGradient(
                listOf(Color(0x5530B8FF), Color.Transparent),
                center = Offset(size.width * 0.84f, size.height * 0.28f),
                radius = size.width * 0.42f,
            ),
            radius = size.width * 0.42f,
            center = Offset(size.width * 0.84f, size.height * 0.28f),
        )
        drawArc(
            color = Color(0x557A5CFF),
            startAngle = 185f,
            sweepAngle = 120f,
            useCenter = false,
            topLeft = Offset(size.width * 0.50f, size.height * 0.04f),
            size = Size(size.width * 0.46f, size.height * 0.70f),
            style = Stroke(width = 2.dp.toPx()),
        )
    }
}

@Composable
private fun GradientBoostButton(
    text: String,
    enabled: Boolean,
    active: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(28.dp)
    val gradient = if (active) {
        Brush.horizontalGradient(listOf(Violet, Magenta))
    } else {
        Brush.horizontalGradient(listOf(Cyan, Blue, Violet, Magenta))
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(62.dp)
            .alpha(if (enabled) 1f else 0.45f)
            .shadow(20.dp, shape)
            .clip(shape)
            .background(gradient)
            .border(1.dp, Color.White.copy(alpha = 0.75f), shape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.Bolt,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(30.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = text,
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Black,
            )
        }
    }
}

@Composable
private fun MetricsRow(state: BoostState) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        MetricCard(
            icon = Icons.Default.NetworkCheck,
            title = "PING",
            value = state.pingMs?.let { "${it} ms" } ?: "—",
            accent = Cyan,
            modifier = Modifier.weight(1f),
        )
        MetricCard(
            icon = Icons.Default.BarChart,
            title = "JITTER",
            value = state.jitterMs?.let { "${it} ms" } ?: "—",
            accent = Magenta,
            modifier = Modifier.weight(1f),
        )
        MetricCard(
            icon = Icons.Default.Shield,
            title = "LOSS",
            value = state.packetLossPct?.let {
                String.format(Locale.US, "%.1f%%", it)
            } ?: "—",
            accent = Color(0xFFFF53C7),
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun MetricCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(22.dp))
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFF0E2847),
                        Color(0xFF081A34),
                    ),
                ),
            )
            .border(1.dp, accent.copy(alpha = 0.60f), RoundedCornerShape(22.dp))
            .padding(horizontal = 12.dp, vertical = 14.dp),
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(accent.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(20.dp),
            )
        }

        Spacer(Modifier.height(10.dp))

        Text(
            text = title,
            color = Muted,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = value,
            color = Color.White,
            fontSize = 20.sp,
            lineHeight = 22.sp,
            fontWeight = FontWeight.Black,
        )
    }
}

@Composable
private fun GamePickerHeader(
    query: String,
    onQueryChange: (String) -> Unit,
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Выбери игру",
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Black,
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    tint = Muted,
                )
            },
            placeholder = {
                Text(
                    text = "Поиск игры",
                    color = Muted,
                )
            },
            shape = RoundedCornerShape(18.dp),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color(0xA90A1931),
                unfocusedContainerColor = Color(0xA90A1931),
                focusedIndicatorColor = Cyan,
                unfocusedIndicatorColor = Color(0xFF263C61),
                cursorColor = Cyan,
            ),
        )
    }
}

@Composable
private fun GameRow(
    app: BoostApp,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(22.dp)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(if (selected) 18.dp else 0.dp, shape)
            .clip(shape)
            .background(
                Brush.horizontalGradient(
                    if (selected) {
                        listOf(
                            Color(0xFF0D3858),
                            Color(0xFF112A4A),
                            Color(0xFF17143F),
                        )
                    } else {
                        listOf(
                            Color(0xE90A1830),
                            Color(0xE90B1930),
                        )
                    },
                ),
            )
            .border(
                width = if (selected) 1.4.dp else 1.dp,
                brush = if (selected) {
                    NeonGradient
                } else {
                    Brush.horizontalGradient(listOf(Color(0xFF1E3555), Color(0xFF27305C)))
                },
                shape = shape,
            )
            .clickable(onClick = onClick)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(
            app = app,
            modifier = Modifier.size(58.dp),
        )

        Spacer(Modifier.width(12.dp))

        Column(Modifier.weight(1f)) {
            Text(
                text = app.label,
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(6.dp))
            SmallPill(
                icon = Icons.Default.Public,
                text = "Авто",
            )
        }

        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(if (selected) Cyan else Color.Transparent)
                .border(
                    2.dp,
                    if (selected) Cyan else Color(0xFF667A9A),
                    CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Выбрано",
                    tint = Night,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
private fun AppIcon(
    app: BoostApp?,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(18.dp)

    Box(
        modifier = modifier
            .clip(shape)
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFF1B5575), Color(0xFF251855)),
                ),
            )
            .border(1.dp, Color(0x664DEBFF), shape),
        contentAlignment = Alignment.Center,
    ) {
        if (app?.icon != null) {
            Image(
                bitmap = app.icon.asImageBitmap(),
                contentDescription = app.label,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(
                imageVector = Icons.Default.Gamepad,
                contentDescription = null,
                tint = Cyan,
                modifier = Modifier.size(34.dp),
            )
        }
    }
}

@Composable
private fun SmallPill(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(Color(0x88102649))
            .border(1.dp, Color(0xFF24599B), RoundedCornerShape(999.dp))
            .padding(horizontal = 9.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color(0xFFC8D7EA),
            modifier = Modifier.size(14.dp),
        )
        Spacer(Modifier.width(5.dp))
        Text(
            text = text,
            color = Color(0xFFE5EFFA),
            fontSize = 11.sp,
            maxLines = 1,
        )
    }
}

@Composable
private fun ServerSettingsButton(
    expanded: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Color(0xD60A1730))
            .border(1.2.dp, Brush.horizontalGradient(listOf(Cyan, Blue)), RoundedCornerShape(22.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 17.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Default.Dns,
            contentDescription = null,
            tint = Color(0xFFB9CAEA),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = if (expanded) "Скрыть настройки сервера" else "Настройки сервера",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
        )
    }
}

@Composable
private fun AdvancedServerCard(
    state: BoostState,
    viewModel: BoostViewModel,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Color(0xF20A1730))
            .border(1.dp, Color(0xFF244064), RoundedCornerShape(24.dp))
            .padding(16.dp),
    ) {
        Text(
            text = "Сервер",
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Black,
        )
        Text(
            text = "Нужен только при первой настройке или смене узла.",
            color = Muted,
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
        )

        SecondaryAction(
            text = if (state.isLanDiscovering) {
                "Ищем в Wi-Fi…"
            } else {
                "Найти локальный тестовый сервер"
            },
            enabled = !state.isLanDiscovering &&
                !state.isAutoSelecting &&
                !state.isProbing,
            onClick = viewModel::discoverLanGateway,
        )

        Spacer(Modifier.height(10.dp))

        ServerTextField(
            value = state.controlPlaneUrl,
            onValueChange = viewModel::updateControlPlaneUrl,
            label = "Адрес сервиса серверов",
            placeholder = "https://...",
        )

        Spacer(Modifier.height(8.dp))

        SecondaryAction(
            text = if (state.isAutoSelecting) "Ищем лучший сервер…" else "Найти лучший сервер",
            enabled = state.controlPlaneUrl.startsWith("https://") && !state.isAutoSelecting,
            onClick = viewModel::autoSelectGateway,
        )

        Spacer(Modifier.height(10.dp))

        ServerTextField(
            value = state.gatewayHost,
            onValueChange = viewModel::updateGatewayHost,
            label = "Сервер",
        )

        Spacer(Modifier.height(8.dp))

        SecondaryAction(
            text = if (state.isProbing) "Проверяем…" else "Проверить сервер",
            enabled = state.gatewayHost.isNotBlank() &&
                !state.isProbing &&
                !state.isAutoSelecting,
            onClick = viewModel::probeGateway,
        )

        Spacer(Modifier.height(10.dp))

        ServerTextField(
            value = state.wireGuardServerPublicKey,
            onValueChange = viewModel::updateWireGuardServerPublicKey,
            label = "Публичный ключ сервера",
        )
        Spacer(Modifier.height(8.dp))
        ServerTextField(
            value = state.tunnelAddress,
            onValueChange = viewModel::updateTunnelAddress,
            label = "Адрес телефона в туннеле",
        )
        Spacer(Modifier.height(8.dp))
        ServerTextField(
            value = state.dnsServer,
            onValueChange = viewModel::updateDnsServer,
            label = "DNS",
        )

        Spacer(Modifier.height(12.dp))

        Text(
            text = "Публичный ключ телефона",
            color = Muted,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
        )
        SelectionContainer {
            Text(
                text = state.clientPublicKey ?: "Ключ недоступен",
                color = Color.White,
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 5.dp),
            )
        }

        state.identityError?.let {
            Text(
                text = it,
                color = Error,
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

@Composable
private fun ServerTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String = "",
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        label = { Text(label) },
        placeholder = {
            if (placeholder.isNotBlank()) {
                Text(placeholder)
            }
        },
        shape = RoundedCornerShape(16.dp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color(0x88081329),
            unfocusedContainerColor = Color(0x88081329),
            focusedIndicatorColor = Cyan,
            unfocusedIndicatorColor = Color(0xFF2A4265),
            cursorColor = Cyan,
        ),
    )
}

@Composable
private fun SecondaryAction(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else 0.45f)
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF10284A))
            .border(1.dp, Color(0xFF285B91), RoundedCornerShape(16.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 13.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
        )
    }
}

@Composable
private fun BoostBottomBar() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xF5030B1B))
            .border(
                width = 1.dp,
                color = Color(0x331E8BFF),
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            )
            .navigationBarsPadding()
            .padding(horizontal = 22.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BottomNavItem(
            icon = Icons.Default.Gamepad,
            label = "Игры",
            selected = false,
        )
        BottomNavItem(
            icon = Icons.Default.Bolt,
            label = "Буст",
            selected = true,
        )
        BottomNavItem(
            icon = Icons.Default.BarChart,
            label = "Статистика",
            selected = false,
        )
        BottomNavItem(
            icon = Icons.Default.Person,
            label = "Я",
            selected = false,
        )
    }
}

@Composable
private fun BottomNavItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    selected: Boolean,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(68.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (selected) Cyan else Color(0xFF8297BC),
            modifier = Modifier.size(25.dp),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = label,
            color = if (selected) Cyan else Color(0xFF8297BC),
            fontSize = 10.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
        )
    }
}
