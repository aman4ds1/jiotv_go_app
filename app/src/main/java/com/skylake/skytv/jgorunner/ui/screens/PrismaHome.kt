package com.skylake.skytv.jgorunner.ui.screens

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.skylake.skytv.jgorunner.data.SkySharedPref
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * PrismA TV start screen. Same parameters as the original HomeScreen,
 * so MainActivity needs no changes. "TV" (onDebugButtonClick) is now
 * the big Watch TV tile; Web TV, IPTV and Omni TV live under More.
 */
@Suppress("UNUSED_PARAMETER")
@Composable
fun HomeScreen(
    title: String,
    titleShouldGlow: Boolean,
    isServerRunning: Boolean,
    publicJTVServerURL: String,
    outputText: String,
    onRunServerButtonClick: () -> Unit,
    onStopServerButtonClick: () -> Unit,
    onRunIPTVButtonClick: () -> Unit,
    onWebTVButtonClick: () -> Unit,
    onDebugButtonClick: () -> Unit,
    onOmniTvButtonClick: () -> Unit,
    onExitButtonClick: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { SkySharedPref.getInstance(context) }
    val port = prefs.myPrefs.jtvGoServerPort
    val version = prefs.myPrefs.jtvGoBinaryVersion ?: ""

    var showMore by remember { mutableStateOf(false) }
    var showLogs by remember { mutableStateOf(false) }
    var clock by remember { mutableStateOf(now()) }
    LaunchedEffect(Unit) { while (true) { delay(15_000); clock = now() } }

    val watchFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { delay(200); runCatching { watchFocus.requestFocus() } }

    val statusColor = if (isServerRunning) Prisma.Green else Prisma.Red

    Box(
        Modifier
            .fillMaxSize()
            .background(Prisma.Bg)
            .background(
                Brush.radialGradient(
                    listOf(statusColor.copy(alpha = 0.10f), Color.Transparent),
                    radius = 1400f
                )
            )
            .padding(horizontal = 48.dp, vertical = 28.dp)
    ) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(22.dp)) {

            // Header
            Row(verticalAlignment = Alignment.CenterVertically) {
                PrismaGem(40.dp)
                Spacer(Modifier.width(14.dp))
                PrismaWordmark(26.sp)
                Spacer(Modifier.weight(1f))
                Text(clock, color = Prisma.Muted, fontSize = 18.sp)
            }

            // Status card
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(28.dp))
                    .background(Prisma.Card)
                    .padding(horizontal = 36.dp, vertical = 28.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PulsingDot(statusColor, pulsing = isServerRunning)
                        Spacer(Modifier.width(12.dp))
                        Text(
                            if (isServerRunning) "SERVER RUNNING" else "SERVER STOPPED",
                            color = statusColor, fontSize = 15.sp,
                            fontWeight = FontWeight.Bold, letterSpacing = 2.sp
                        )
                    }
                    Text(
                        if (isServerRunning) "Ready to watch" else "Server is off",
                        color = Prisma.Ink, fontSize = 40.sp, fontWeight = FontWeight.Bold
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                        Meta("Address", "localhost:$port")
                        if (version.isNotBlank()) Meta("Server", version)
                    }
                }
                PrismaFocusBox(
                    onClick = { if (isServerRunning) onStopServerButtonClick() else onRunServerButtonClick() },
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Row(
                        Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.PowerSettingsNew, null, tint = Prisma.Silver, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(
                            if (isServerRunning) "Stop server" else "Start server",
                            color = Prisma.Silver, fontSize = 18.sp, fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            // Actions
            Row(
                Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                PrismaFocusBox(
                    onClick = {
                        if (isServerRunning) onDebugButtonClick() else onRunServerButtonClick()
                    },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .focusRequester(watchFocus),
                    shape = RoundedCornerShape(28.dp),
                    background = Prisma.SilverFill,
                    restingEdge = Color(0x40FFFFFF),
                    focusScale = 1.02f
                ) {
                    Row(
                        Modifier
                            .fillMaxSize()
                            .padding(horizontal = 36.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.LiveTv, null, tint = Prisma.Bg, modifier = Modifier.size(72.dp))
                        Spacer(Modifier.width(28.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                if (isServerRunning) "Watch TV" else "Start and watch",
                                color = Prisma.Bg, fontSize = 40.sp, fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                if (isServerRunning) "Open your channels" else "Starts the server first",
                                color = Color(0xFF3F444C), fontSize = 17.sp
                            )
                        }
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = Prisma.Bg, modifier = Modifier.size(36.dp))
                    }
                }
                PrismaFocusBox(
                    onClick = { showMore = true },
                    modifier = Modifier
                        .width(180.dp)
                        .fillMaxHeight(),
                    shape = RoundedCornerShape(28.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Filled.MoreHoriz, null, tint = Prisma.Silver, modifier = Modifier.size(40.dp))
                        Spacer(Modifier.height(8.dp))
                        Text("More", color = Prisma.Silver, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            // Footer
            Row(verticalAlignment = Alignment.CenterVertically) {
                PrismaFocusBox(onClick = { showLogs = !showLogs }, shape = RoundedCornerShape(14.dp)) {
                    Row(
                        Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.AutoMirrored.Filled.List, null, tint = Prisma.Muted, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(if (showLogs) "Hide activity" else "Show activity", color = Prisma.Muted, fontSize = 15.sp)
                    }
                }
                Spacer(Modifier.weight(1f))
                PrismaFocusBox(
                    onClick = onExitButtonClick,
                    shape = RoundedCornerShape(14.dp),
                    background = Brush.linearGradient(listOf(Color.Transparent, Color.Transparent)),
                    restingEdge = Color.Transparent
                ) {
                    Text("Exit", color = Prisma.Muted, fontSize = 15.sp, modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp))
                }
            }
        }

        if (showLogs) {
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(bottom = 64.dp)
                    .width(620.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color(0xEB08080C))
                    .padding(18.dp)
            ) {
                Text(
                    outputText.lines().takeLast(10).joinToString("\n").ifBlank { "No activity yet." },
                    color = Color(0xFFA8B0BA), fontSize = 13.sp, fontFamily = FontFamily.Monospace, lineHeight = 20.sp
                )
            }
        }
    }

    if (showMore) {
        val first = remember { FocusRequester() }
        LaunchedEffect(Unit) { delay(150); runCatching { first.requestFocus() } }
        Dialog(onDismissRequest = { showMore = false }) {
            Column(
                Modifier
                    .width(500.dp)
                    .clip(RoundedCornerShape(26.dp))
                    .background(Prisma.CardSolid)
                    .padding(26.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("More ways to watch", color = Prisma.Ink, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.weight(1f))
                    PrismaFocusBox(onClick = { showMore = false }, shape = CircleShape) {
                        Icon(Icons.Filled.Close, "Close", tint = Prisma.Ink, modifier = Modifier.padding(10.dp).size(20.dp))
                    }
                }
                MoreItem(Icons.Filled.Public, "Web TV", "The server’s own page in a browser", Modifier.focusRequester(first)) {
                    showMore = false; onWebTVButtonClick()
                }
                MoreItem(Icons.Filled.PlaylistPlay, "IPTV app", "Send channels to another player app") {
                    showMore = false; onRunIPTVButtonClick()
                }
                MoreItem(Icons.Filled.AutoAwesome, "Omni TV", "Advanced: combine several sources") {
                    showMore = false; onOmniTvButtonClick()
                }
            }
        }
    }
}

@Composable
private fun MoreItem(icon: ImageVector, title: String, sub: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    PrismaFocusBox(onClick = onClick, modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, tint = Prisma.Silver, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(16.dp))
            Column {
                Text(title, color = Prisma.Ink, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text(sub, color = Prisma.Muted, fontSize = 14.sp)
            }
        }
    }
}

@Composable
private fun Meta(label: String, value: String) {
    Row {
        Text("$label ", color = Prisma.Muted, fontSize = 16.sp)
        Text(value, color = Prisma.Silver, fontSize = 16.sp)
    }
}

@Composable
private fun PulsingDot(color: Color, pulsing: Boolean) {
    val t = rememberInfiniteTransition(label = "ping")
    val s by t.animateFloat(1f, 2.6f, infiniteRepeatable(tween(1600), RepeatMode.Restart), label = "s")
    val a by t.animateFloat(0.6f, 0f, infiniteRepeatable(tween(1600), RepeatMode.Restart), label = "a")
    Box(Modifier.size(14.dp), contentAlignment = Alignment.Center) {
        if (pulsing) {
            Box(
                Modifier
                    .size(14.dp)
                    .graphicsLayer { scaleX = s; scaleY = s; alpha = a }
                    .clip(CircleShape)
                    .background(color)
            )
        }
        Box(Modifier.size(14.dp).clip(CircleShape).background(color))
    }
}

private fun now(): String = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date())
