package com.skylake.skytv.jgorunner.ui.tvhome

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import com.skylake.skytv.jgorunner.activities.ChannelInfo
import com.skylake.skytv.jgorunner.services.player.ExoPlayJet
import com.skylake.skytv.jgorunner.ui.screens.AppStartTracker
import com.skylake.skytv.jgorunner.ui.tvhome.components.ShimmerChannelGrid
import kotlinx.coroutines.delay

/*
 * Rows_Layout — remote-first TV home.
 *
 *  ┌ Info header: logo, channel name, what's on now (EPG) ┐
 *  ├ Language pills: All · Hindi · English · Tamil …     ┤
 *  ├ Recently watched  ▸ ▸ ▸ ▸                            ┤
 *  ├ Favourites        ▸ ▸ ▸ ▸                            ┤
 *  ├ News              ▸ ▸ ▸ ▸                            ┤
 *  └ Entertainment …   ▸ ▸ ▸ ▸                            ┘
 *
 * Left/Right moves inside a row, Up/Down moves between rows and
 * each row remembers its last focused tile. OK plays; long-press
 * OK toggles favourite. In the player, Up/Down zaps through the
 * row you launched from (e.g. only News channels).
 */

private val Gold = Color(0xFFFFD700)

private val CATEGORY_ORDER = listOf(
    12 to "News", 5 to "Entertainment", 6 to "Movies", 8 to "Sports",
    7 to "Kids", 13 to "Music", 10 to "Infotainment", 9 to "Lifestyle",
    15 to "Devotional", 16 to "Business", 17 to "Educational",
    18 to "Shopping", 19 to "JioDarshan"
)

private val LANGUAGES: List<Pair<String, Int?>> = listOf(
    "All" to null, "Hindi" to 1, "English" to 6, "Marathi" to 2,
    "Bengali" to 5, "Tamil" to 8, "Telugu" to 11, "Malayalam" to 7,
    "Kannada" to 13, "Gujarati" to 9, "Punjabi" to 3, "Odia" to 10,
    "Bhojpuri" to 12, "Urdu" to 4, "Assamese" to 14, "Nepali" to 15,
    "Other" to 18
)

private data class ChannelRow(val title: String, val channels: List<Channel>)

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun Rows_Layout(context: Context, reloadTrigger: Int) {
    val vm: TvViewModel = viewModel()

    val allChannels by vm.allChannels.collectAsState()
    val isLoading by vm.isLoading.collectAsState()
    val isError by vm.isError.collectAsState()
    val favorites by vm.favoriteChannels.collectAsState()
    val recents by vm.recentChannels.collectAsState()
    val epg by vm.epgData.collectAsState()
    val isEpgLoading by vm.isEpgLoading.collectAsState()
    val baseUrl = vm.basefinURL

    // Remember the chosen language across app restarts
    val prefs = remember { context.getSharedPreferences("rows_layout", Context.MODE_PRIVATE) }
    var languageId by rememberSaveable {
        mutableStateOf(prefs.getInt("language_id", -1).takeIf { it >= 0 })
    }

    LaunchedEffect(reloadTrigger) {
        if (reloadTrigger > 0) vm.loadChannels(forceRefresh = true)
    }

    val favoriteIds = remember(favorites) { favorites.map { it.channel_id }.toSet() }

    val rows = remember(allChannels, favorites, recents, languageId) {
        val byLanguage = allChannels.filter { languageId == null || it.channelLanguageId == languageId }
        val known = CATEGORY_ORDER.map { it.first }.toSet()
        buildList {
            if (recents.isNotEmpty()) add(ChannelRow("Recently watched", recents))
            if (favorites.isNotEmpty()) add(ChannelRow("Favourites", favorites))
            CATEGORY_ORDER.forEach { (id, name) ->
                val list = byLanguage.filter { it.channelCategoryId == id }
                if (list.isNotEmpty()) add(ChannelRow(name, list))
            }
            val other = byLanguage.filter { it.channelCategoryId !in known }
            if (other.isNotEmpty()) add(ChannelRow("Other", other))
        }
    }

    // Focused channel drives the info header; EPG fetch is debounced so
    // scrolling quickly along a row doesn't fire a request per tile.
    var focusedChannel by remember { mutableStateOf<Channel?>(null) }
    var epgChannelId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(focusedChannel?.channel_id) {
        val id = focusedChannel?.channel_id ?: return@LaunchedEffect
        delay(400)
        epgChannelId = id
        vm.loadEpg(id)
    }
    val epgForFocused = epg.takeIf {
        !isEpgLoading && epgChannelId != null && epgChannelId == focusedChannel?.channel_id
    }

    fun play(channel: Channel, rowChannels: List<Channel>, index: Int) {
        val intent = Intent(context, ExoPlayJet::class.java).apply {
            putExtra("video_url", channel.channel_url)
            putExtra("key_url", channel.key_url)
            putExtra("zone", "TV")
            putParcelableArrayListExtra(
                "channel_list_data",
                ArrayList(rowChannels.map { ch ->
                    // Plain URL = adaptive stream with every quality, so the
                    // player's Video & Audio menu can offer all of them.
                    ChannelInfo(
                        ch.channel_url,
                        ch.key_url,
                        "$baseUrl/jtvimage/${ch.logoUrl}",
                        ch.channel_name
                    )
                })
            )
            putExtra("current_channel_index", index)
            putExtra("logo_url", "$baseUrl/jtvimage/${channel.logoUrl}")
            putExtra("ch_name", channel.channel_name)
            addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        vm.saveToRecents(channel)
    }

    // Keep the existing "start TV automatically" setting working:
    // resume the last watched channel, else the first channel.
    LaunchedEffect(rows) {
        if (rows.isNotEmpty() &&
            vm.preferenceManager.myPrefs.startTvAutomatically &&
            !AppStartTracker.shouldPlayChannel
        ) {
            val row = rows.first()
            delay(500)
            play(row.channels.first(), row.channels, 0)
            AppStartTracker.shouldPlayChannel = true
        }
    }

    val firstTile = remember { FocusRequester() }
    var initialFocusDone by remember { mutableStateOf(false) }
    LaunchedEffect(rows.isNotEmpty()) {
        if (rows.isNotEmpty() && !initialFocusDone) {
            delay(150)
            runCatching { firstTile.requestFocus() }
            initialFocusDone = true
        }
    }

    when {
        isLoading && allChannels.isEmpty() -> ShimmerChannelGrid()

        isError && allChannels.isEmpty() -> ErrorState { vm.loadChannels(forceRefresh = true) }

        else -> Column(Modifier.fillMaxSize()) {
            InfoHeader(focusedChannel, epgForFocused, baseUrl)

            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRestorer(),
                contentPadding = PaddingValues(horizontal = 32.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(LANGUAGES, key = { it.first }) { (name, id) ->
                    LanguagePill(
                        label = name,
                        selected = languageId == id,
                        onClick = {
                            languageId = id
                            prefs.edit().putInt("language_id", id ?: -1).apply()
                        }
                    )
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 48.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                itemsIndexed(rows, key = { _, row -> row.title }) { rowIndex, row ->
                    Column {
                        Text(
                            text = "${row.title}  ·  ${row.channels.size}",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.padding(start = 32.dp, bottom = 8.dp)
                        )
                        LazyRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRestorer(),
                            contentPadding = PaddingValues(horizontal = 32.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            itemsIndexed(
                                row.channels,
                                key = { _, ch -> "${row.title}-${ch.channel_id}" }
                            ) { index, channel ->
                                ChannelTile(
                                    channel = channel,
                                    baseUrl = baseUrl,
                                    isFavorite = channel.channel_id in favoriteIds,
                                    modifier = if (rowIndex == 0 && index == 0)
                                        Modifier.focusRequester(firstTile) else Modifier,
                                    onFocused = { focusedChannel = channel },
                                    onClick = { play(channel, row.channels, index) },
                                    onLongClick = {
                                        vm.toggleFavorite(channel)
                                        val added = channel.channel_id !in favoriteIds
                                        Toast.makeText(
                                            context,
                                            if (added) "Added ${channel.channel_name} to Favourites"
                                            else "Removed ${channel.channel_name} from Favourites",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
private fun InfoHeader(channel: Channel?, epg: EpgProgram?, baseUrl: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(130.dp)
            .padding(horizontal = 32.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (channel == null) {
            Text(
                "Pick a channel",
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            return@Row
        }
        Box(
            modifier = Modifier
                .size(width = 150.dp, height = 100.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            GlideImage(
                model = "$baseUrl/jtvimage/${channel.logoUrl}",
                contentDescription = channel.channel_name,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().padding(10.dp)
            )
        }
        Spacer(Modifier.width(24.dp))
        Column(Modifier.weight(1f)) {
            Text(
                channel.channel_name,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onBackground
            )
            val now = epg
            if (now != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "Now: ${now.showname}",
                    fontSize = 18.sp,
                    color = Gold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    now.description,
                    fontSize = 14.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun LanguagePill(label: String, selected: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.1f else 1f, label = "pillScale")
    Box(
        modifier = Modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(50))
            .background(
                if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceVariant
            )
            .border(
                BorderStroke(if (focused) 3.dp else 0.dp, if (focused) Gold else Color.Transparent),
                RoundedCornerShape(50)
            )
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 10.dp)
    ) {
        Text(
            label,
            fontSize = 16.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalGlideComposeApi::class)
@Composable
private fun ChannelTile(
    channel: Channel,
    baseUrl: String,
    isFavorite: Boolean,
    modifier: Modifier,
    onFocused: () -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.12f else 1f, label = "tileScale")

    Column(
        modifier = modifier
            .width(168.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocused()
            }
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(100.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(
                    if (focused) Color.White else MaterialTheme.colorScheme.surfaceVariant
                )
                .border(
                    BorderStroke(if (focused) 3.dp else 0.dp, if (focused) Gold else Color.Transparent),
                    RoundedCornerShape(14.dp)
                )
        ) {
            GlideImage(
                model = "$baseUrl/jtvimage/${channel.logoUrl}",
                contentDescription = channel.channel_name,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().padding(14.dp)
            )
            if (isFavorite) {
                Icon(
                    Icons.Filled.Star,
                    contentDescription = "Favourite",
                    tint = Gold,
                    modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).size(18.dp)
                )
            }
            if (channel.isHD) {
                Text(
                    "HD",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xCC000000))
                        .padding(horizontal = 5.dp, vertical = 1.dp)
                )
            }
        }
        Text(
            channel.channel_name,
            fontSize = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontWeight = if (focused) FontWeight.Bold else FontWeight.Normal,
            color = if (focused) Gold else MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(top = 6.dp)
        )
    }
}

@Composable
private fun ErrorState(onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Filled.CloudOff, null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(64.dp)
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "Can't reach the JioTV Go server",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            "Make sure the server is running and you're logged in.",
            fontSize = 15.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onRetry) { Text("Retry", fontSize = 16.sp) }
    }
}
