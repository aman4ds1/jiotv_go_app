package com.skylake.skytv.jgorunner.ui.screens

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import kotlinx.coroutines.delay
import java.util.Locale

/*
 * "Video & Audio" panel opened from the gear button in the player
 * (or the ≡ Menu key on the Fire TV remote).
 *
 * Video quality: Auto, or a cap such as "720p" (plays the best stream
 * up to that height). Audio language: every audio track the channel
 * offers. Both choices are saved and re-applied on every channel, so
 * zapping keeps your quality and language.
 */

private const val AV_PREFS = "player_av"
private const val KEY_MAX_HEIGHT = "max_height"   // 0 = Auto
private const val KEY_AUDIO_LANG = "audio_lang"   // null = channel default

private val Gold = Color(0xFFFFD700)

/** Call once after the player is created. */
fun applySavedAvPrefs(context: Context, player: Player) {
    val p = context.getSharedPreferences(AV_PREFS, Context.MODE_PRIVATE)
    val maxHeight = p.getInt(KEY_MAX_HEIGHT, 0)
    val lang = p.getString(KEY_AUDIO_LANG, null)
    player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
        .setMaxVideoSize(Int.MAX_VALUE, if (maxHeight > 0) maxHeight else Int.MAX_VALUE)
        .setPreferredAudioLanguage(lang)
        .build()
}

private data class AudioOption(
    val group: Tracks.Group,
    val index: Int,
    val label: String,
    val language: String?,
    val selected: Boolean
)

private fun languageName(code: String?): String? {
    if (code.isNullOrBlank() || code == "und" || code == C.LANGUAGE_UNDETERMINED) return null
    val name = Locale.forLanguageTag(code).getDisplayLanguage(Locale.ENGLISH)
    return name.takeIf { it.isNotBlank() && it != code }
        ?: code.uppercase()
}

private fun channelsLabel(count: Int): String? = when (count) {
    1 -> "Mono"
    2 -> "Stereo"
    6 -> "5.1"
    8 -> "7.1"
    else -> null
}

@Composable
fun AvSettingsDialog(player: Player, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(AV_PREFS, Context.MODE_PRIVATE) }

    var tracks by remember { mutableStateOf(player.currentTracks) }
    var playingHeight by remember { mutableIntStateOf(player.videoSize.height) }
    var maxHeight by remember { mutableIntStateOf(prefs.getInt(KEY_MAX_HEIGHT, 0)) }
    var audioPref by remember { mutableStateOf(prefs.getString(KEY_AUDIO_LANG, null)) }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onTracksChanged(newTracks: Tracks) { tracks = newTracks }
            override fun onVideoSizeChanged(videoSize: VideoSize) { playingHeight = videoSize.height }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    val heights = remember(tracks) {
        tracks.groups
            .filter { it.type == C.TRACK_TYPE_VIDEO }
            .flatMap { g -> (0 until g.length).filter { g.isTrackSupported(it) }.map { g.getTrackFormat(it).height } }
            .filter { it > 0 }
            .distinct()
            .sortedDescending()
    }

    val audioOptions = remember(tracks) {
        var n = 0
        tracks.groups
            .filter { it.type == C.TRACK_TYPE_AUDIO }
            .flatMap { g ->
                (0 until g.length).filter { g.isTrackSupported(it) }.map { i ->
                    val f = g.getTrackFormat(i)
                    n++
                    val base = f.label?.takeIf { it.isNotBlank() }
                        ?: languageName(f.language)
                        ?: "Audio $n"
                    val extra = channelsLabel(f.channelCount)
                    AudioOption(
                        group = g,
                        index = i,
                        label = if (extra != null) "$base · $extra" else base,
                        language = f.language,
                        selected = g.isTrackSelected(i)
                    )
                }
            }
            .distinctBy { it.label }
    }

    fun chooseVideo(height: Int) {
        maxHeight = height
        prefs.edit().putInt(KEY_MAX_HEIGHT, height).apply()
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .clearOverridesOfType(C.TRACK_TYPE_VIDEO)
            .setMaxVideoSize(Int.MAX_VALUE, if (height > 0) height else Int.MAX_VALUE)
            .build()
    }

    fun chooseAudio(option: AudioOption?) {
        val builder = player.trackSelectionParameters.buildUpon()
        if (option == null) {
            prefs.edit().remove(KEY_AUDIO_LANG).apply()
            builder.clearOverridesOfType(C.TRACK_TYPE_AUDIO).setPreferredAudioLanguage(null)
        } else {
            prefs.edit().putString(KEY_AUDIO_LANG, option.language).apply()
            builder
                .setOverrideForType(TrackSelectionOverride(option.group.mediaTrackGroup, option.index))
                .setPreferredAudioLanguage(option.language)
        }
        player.trackSelectionParameters = builder.build()
    }

    val firstItem = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(100)
        runCatching { firstItem.requestFocus() }
    }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .widthIn(min = 380.dp, max = 480.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xF2161616))
                .padding(24.dp)
        ) {
            Text("Video & Audio", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Spacer(Modifier.height(12.dp))

            LazyColumn(
                modifier = Modifier.heightIn(max = 460.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // ---------- Video quality ----------
                item { SectionTitle("Video quality") }
                item {
                    OptionRow(
                        title = "Auto",
                        subtitle = if (playingHeight > 0) "Now playing ${playingHeight}p" else null,
                        selected = maxHeight == 0 || maxHeight !in heights,
                        modifier = Modifier.focusRequester(firstItem),
                        onClick = { chooseVideo(0) }
                    )
                }
                if (heights.size > 1) {
                    heights.forEach { h ->
                        item(key = "v$h") {
                            OptionRow(
                                title = "${h}p",
                                subtitle = when {
                                    h >= 1080 -> "Full HD"
                                    h >= 720 -> "HD"
                                    h <= 360 -> "Data saver"
                                    else -> null
                                },
                                selected = maxHeight == h,
                                onClick = { chooseVideo(h) }
                            )
                        }
                    }
                } else {
                    item { InfoText(if (heights.isEmpty()) "Loading qualities…" else "This channel has only one video quality.") }
                }

                // ---------- Audio language ----------
                item { Column { Spacer(Modifier.height(10.dp)); SectionTitle("Audio language") } }
                when {
                    audioOptions.isEmpty() -> item { InfoText("Loading audio tracks…") }
                    audioOptions.size == 1 -> {
                        item {
                            OptionRow(title = audioOptions[0].label, selected = true, onClick = {})
                        }
                        item { InfoText("Only one audio language is available on this channel.") }
                    }
                    else -> {
                        item {
                            OptionRow(
                                title = "Channel default",
                                selected = audioPref == null,
                                onClick = { chooseAudio(null); audioPref = null }
                            )
                        }
                        audioOptions.forEach { opt ->
                            item(key = "a${opt.label}") {
                                OptionRow(
                                    title = opt.label,
                                    selected = audioPref != null && opt.selected,
                                    onClick = { chooseAudio(opt); audioPref = opt.language ?: opt.label }
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            Text(
                "Your choices are kept when you change channel. Press Back to close.",
                fontSize = 13.sp,
                color = Color(0xFF9E9E9E)
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text.uppercase(),
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp,
        color = Gold,
        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
    )
}

@Composable
private fun InfoText(text: String) {
    Text(text, fontSize = 14.sp, color = Color(0xFFBDBDBD), modifier = Modifier.padding(4.dp))
}

@Composable
private fun OptionRow(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null
) {
    var focused by remember { mutableStateOf(false) }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (focused) Color(0x33FFFFFF) else Color.Transparent)
            .border(if (focused) 2.dp else 0.dp, if (focused) Gold else Color.Transparent, RoundedCornerShape(12.dp))
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // radio dot
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .border(2.dp, if (selected) Gold else Color(0xFF9E9E9E), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (selected) Box(Modifier.size(10.dp).clip(CircleShape).background(Gold))
        }
        Spacer(Modifier.width(14.dp))
        Column {
            Text(
                title,
                fontSize = 18.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                color = Color.White
            )
            if (subtitle != null) Text(subtitle, fontSize = 13.sp, color = Color(0xFFBDBDBD))
        }
    }
}
