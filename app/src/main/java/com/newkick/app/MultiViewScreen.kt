package com.newkick.app

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView

/** Segunda live escolhida no menu de segurar ("Assistir lado a lado"); a primeira fica esperando aqui. */
object MultiViewState {
    var pending by mutableStateOf<ChannelTarget?>(null)
}

private sealed interface TileState {
    data object Loading : TileState
    data class Ready(val channel: ChannelInfo, val holder: PlayerHolder) : TileState
    data class Failed(val reason: String) : TileState
}

/**
 * Duas lives ao mesmo tempo (lado a lado em paisagem, uma sobre a outra em retrato). Só uma tem
 * som por vez (toque na outra para trocar) e cada uma é limitada a 480p para não pesar.
 */
@OptIn(UnstableApi::class)
@Composable
fun MultiViewScreen(slugA: String, slugB: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val activity = context.findActivity()
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val prefs = remember { Prefs(context) }
    var audioIndex by remember { mutableIntStateOf(0) }
    val tiles = remember { listOf(mutableStateOf<TileState>(TileState.Loading), mutableStateOf<TileState>(TileState.Loading)) }
    val slugs = listOf(slugA, slugB)

    // Uma live em segundo plano seria um terceiro áudio.
    LaunchedEffect(Unit) { PlayerSession.release() }

    slugs.forEachIndexed { i, slug ->
        LaunchedEffect(slug) {
            tiles[i].value = try {
                val channel = KickApi.channel(slug)
                if (!channel.isLive || channel.playbackUrl.isEmpty()) {
                    TileState.Failed(tr("%s está offline").format(channel.username))
                } else {
                    val holder = createPlayer(
                        context.applicationContext, channel.playbackUrl, prefs.latencyMs,
                        lockQuality = false, initialBps = prefs.bandwidthBps, channel = channel,
                        capHeight = 480, audioFocus = false,
                    )
                    TileState.Ready(channel, holder)
                }
            } catch (e: Exception) {
                TileState.Failed(tr("Não consegui abrir %s").format(slug))
            }
        }
    }

    // Só um com som.
    val readyTiles = tiles.map { it.value }
    LaunchedEffect(audioIndex, readyTiles) {
        readyTiles.forEachIndexed { i, t -> if (t is TileState.Ready) t.holder.player.volume = if (i == audioIndex) 1f else 0f }
    }

    // Fora da tela, pausa os dois (não há serviço de áudio para multi-view).
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            val players = tiles.mapNotNull { (it.value as? TileState.Ready)?.holder?.player }
            if (event == Lifecycle.Event.ON_STOP) players.forEach { it.pause() }
            if (event == Lifecycle.Event.ON_START) players.forEach { it.play() }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    DisposableEffect(Unit) {
        onDispose {
            tiles.forEach { (it.value as? TileState.Ready)?.holder?.player?.release() }
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }
    BackHandler(onBack = onBack)

    Column(Modifier.fillMaxSize().background(Color.Black).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar", tint = Color.White) }
            Text("Lado a lado", color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            IconButton(onClick = {
                activity?.requestedOrientation =
                    if (landscape) ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED else ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            }) { Text(if (landscape) "↙" else "↗", color = Color.White, fontSize = 20.sp) }
        }
        val tile: @Composable (Int, Modifier) -> Unit = { i, modifier ->
            Tile(tiles[i].value, hasAudio = audioIndex == i, modifier = modifier) { audioIndex = i }
        }
        if (landscape) {
            Row(Modifier.fillMaxSize().padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                tile(0, Modifier.weight(1f).fillMaxSize())
                tile(1, Modifier.weight(1f).fillMaxSize())
            }
        } else {
            Column(Modifier.fillMaxSize().padding(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                tile(0, Modifier.weight(1f).fillMaxWidth())
                tile(1, Modifier.weight(1f).fillMaxWidth())
            }
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun Tile(state: TileState, hasAudio: Boolean, modifier: Modifier, onSelectAudio: () -> Unit) {
    Box(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF101416))
            .border(2.dp, if (hasAudio) KickGreen else Color.Transparent, RoundedCornerShape(12.dp))
            .clickable(onClick = onSelectAudio),
        contentAlignment = Alignment.Center,
    ) {
        when (state) {
            TileState.Loading -> KLoader(size = 48.dp)
            is TileState.Failed -> Text(state.reason, color = Color.White, fontSize = 13.sp, modifier = Modifier.padding(16.dp))
            is TileState.Ready -> {
                var buffering by remember { mutableStateOf(true) }
                DisposableEffect(state.holder) {
                    val listener = object : Player.Listener {
                        override fun onPlaybackStateChanged(playbackState: Int) {
                            buffering = playbackState == Player.STATE_BUFFERING
                        }
                    }
                    state.holder.player.addListener(listener)
                    onDispose { state.holder.player.removeListener(listener) }
                }
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                            useController = false
                            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                            setShutterBackgroundColor(android.graphics.Color.BLACK)
                            player = state.holder.player
                        }
                    },
                    onRelease = { it.player = null },
                )
                if (buffering) KLoader(size = 36.dp)
                Row(
                    Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        state.channel.username,
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(
                        if (hasAudio) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
                        contentDescription = if (hasAudio) "Com som" else "Sem som",
                        tint = if (hasAudio) KickGreen else Color.White,
                    )
                }
            }
        }
    }
}
