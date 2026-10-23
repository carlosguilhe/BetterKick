package com.newkick.app

import android.Manifest
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import android.media.AudioManager
import android.provider.Settings
import android.view.WindowManager
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import coil.compose.AsyncImage
import android.content.Intent
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.PictureInPictureAlt
import android.app.PictureInPictureParams
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.core.content.ContextCompat
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Build
import android.util.Log
import android.util.Rational
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.PictureInPictureModeChangedInfo
import androidx.core.util.Consumer
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.trackselection.AdaptiveTrackSelection
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.upstream.DefaultBandwidthMeter
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay

/** Permite que a MainActivity entre em picture-in-picture ao apertar Home enquanto a live toca. */
object PlayerState {
    @Volatile
    var pipAllowed = false
}

const val ACTION_PIP_TOGGLE = "com.newkick.app.PIP_TOGGLE"

/** Janela do PiP com 16:9 e um botão de pausar/tocar. */
fun pipParams(context: Context, playing: Boolean): PictureInPictureParams {
    val builder = PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9))
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val label = if (playing) "Pausar" else "Tocar"
        val toggle = android.app.PendingIntent.getBroadcast(
            context,
            0,
            android.content.Intent(ACTION_PIP_TOGGLE).setPackage(context.packageName),
            android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val icon = android.graphics.drawable.Icon.createWithResource(context, if (playing) R.drawable.ic_pip_pause else R.drawable.ic_pip_play)
        builder.setActions(listOf(android.app.RemoteAction(icon, label, label, toggle)))
    }
    return builder.build()
}

fun enterPip(activity: android.app.Activity) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        activity.enterPictureInPictureMode(pipParams(activity, PlayerSession.playing))
    }
}

private val latencyChoices = listOf("Baixa (4 s)" to 4_000, "Equilibrada (10 s)" to 10_000, "Estável (20 s)" to 20_000)

private data class QualityOption(
    val label: String,
    val height: Int,
    val bitrate: Int,
    val group: Tracks.Group,
    val trackIndex: Int,
)

internal class PlayerHolder(val player: ExoPlayer, val meter: DefaultBandwidthMeter)

private sealed interface LoadState {
    data object Loading : LoadState
    data class Ready(val channel: ChannelInfo) : LoadState
    data class Failed(val message: String) : LoadState
}

@Composable
fun PlayerScreen(slug: String, onClose: () -> Unit, onMinimize: () -> Unit, onOffline: (String) -> Unit = {}) {
    val context = LocalContext.current
    val activity = context.findActivity()
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    // Voltando do mini player, a live já está tocando: abre direto, sem recarregar.
    var state by remember { mutableStateOf<LoadState>(PlayerSession.cachedChannel(slug)?.let { LoadState.Ready(it) } ?: LoadState.Loading) }
    var reloadKey by remember { mutableIntStateOf(0) }
    var inPip by remember { mutableStateOf(activity?.isInPictureInPictureMode == true) }

    LaunchedEffect(slug, reloadKey) {
        // Outra live: para a que estava tocando em segundo plano.
        if (PlayerSession.cachedChannel(slug) == null) PlayerSession.release()
        val cached = PlayerSession.cachedChannel(slug)
        if (cached != null && reloadKey == 0) {
            state = LoadState.Ready(cached)
            return@LaunchedEffect
        }
        state = LoadState.Loading
        state = try {
            LoadState.Ready(KickApi.channel(slug)).also {
                val prefs = Prefs(context)
                prefs.addRecent(it.channel.slug)
                if (it.channel.isLive) prefs.addHistory(it.channel.slug, it.channel.username, it.channel.category)
            }
        } catch (e: Exception) {
            LoadState.Failed(e.message ?: "Falha ao carregar o canal.")
        }
    }

    DisposableEffect(activity) {
        val listener = Consumer<PictureInPictureModeChangedInfo> { inPip = it.isInPictureInPictureMode }
        activity?.addOnPictureInPictureModeChangedListener(listener)
        onDispose { activity?.removeOnPictureInPictureModeChangedListener(listener) }
    }

    // Tela cheia = paisagem sem barras do sistema.
    val view = LocalView.current
    DisposableEffect(landscape) {
        val window = activity?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        if (landscape) {
            controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller?.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            controller?.show(WindowInsetsCompat.Type.systemBars())
        }
        onDispose { controller?.show(WindowInsetsCompat.Type.systemBars()) }
    }
    DisposableEffect(Unit) {
        onDispose { activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
    }

    BackHandler {
        if (landscape) activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        else onMinimize()
    }

    val toggleFullscreen = {
        activity?.requestedOrientation =
            if (landscape) ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            else ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
    }

    when (val s = state) {
        LoadState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            KLoader(size = 80.dp)
        }
        is LoadState.Failed -> Column(
            Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(s.message, color = MaterialTheme.colorScheme.error)
            Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { reloadKey++ }) { Text("Tentar de novo") }
                Button(onClick = onClose) { Text("Voltar") }
            }
        }
        is LoadState.Ready -> if (!s.channel.isLive || s.channel.playbackUrl.isEmpty()) {
            // Offline: abre a página do canal (clipes e transmissões anteriores).
            LaunchedEffect(s.channel.slug) { onOffline(s.channel.slug) }
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { KLoader(size = 80.dp) }
        } else {
            PlayerContent(
                channel = s.channel,
                landscape = landscape,
                inPip = inPip,
                onBack = { if (landscape) toggleFullscreen() else onMinimize() },
                onMinimize = onMinimize,
                onToggleFullscreen = toggleFullscreen,
                onEnterPip = { activity?.let(::enterPip) },
            )
        }
    }
}

@OptIn(UnstableApi::class)
internal fun createPlayer(
    context: Context,
    url: String,
    latencyMs: Int,
    lockQuality: Boolean,
    initialBps: Long,
    channel: ChannelInfo? = null,
    /** Altura máxima do vídeo (0 = sem limite). Usado no multi-view para pesar menos. */
    capHeight: Int = 0,
    /** false quando há mais de um player (multi-view): senão um tira o foco de áudio do outro. */
    audioFocus: Boolean = true,
): PlayerHolder {
    val meter = DefaultBandwidthMeter.Builder(context).apply {
        if (initialBps > 0) setInitialBitrateEstimate(initialBps)
    }.build()
    // Sem limites de viewport/bitrate. Numa live o buffer só tem alguns segundos (limitado pela
    // latência), então os limiares do modo automático são proporcionais a ela; valores fixos
    // altos fariam a qualidade nunca subir.
    val increaseMs = (latencyMs * 35 / 100).coerceIn(2_000, 8_000)
    val decreaseMs = (latencyMs * 60 / 100).coerceIn(3_000, 10_000)
    // Economia de dados: em rede móvel, o modo automático não passa de 480p (escolher uma qualidade à mão ainda vale).
    val saveData = Prefs(context).dataSaver &&
        context.getSystemService(android.net.ConnectivityManager::class.java)?.isActiveNetworkMetered == true
    val cap = if (capHeight > 0) capHeight else if (saveData) 480 else 0
    val trackSelector = DefaultTrackSelector(context, AdaptiveTrackSelection.Factory(increaseMs, decreaseMs, increaseMs, 0.75f)).apply {
        parameters = buildUponParameters()
            .setViewportSize(Int.MAX_VALUE, Int.MAX_VALUE, false)
            .setMaxVideoSize(if (cap > 0) cap * 16 / 9 else Int.MAX_VALUE, if (cap > 0) cap else Int.MAX_VALUE)
            .setMaxVideoBitrate(Int.MAX_VALUE)
            .setForceHighestSupportedBitrate(lockQuality)
            .setExceedVideoConstraintsIfNecessary(true)
            .build()
    }
    // Retoma só com 6 s de buffer: menos pausa-e-volta, pausas mais raras e mais longas.
    val loadControl = DefaultLoadControl.Builder()
        .setBufferDurationsMs(30_000, 60_000, 2_500, 6_000)
        .build()
    val player = ExoPlayer.Builder(context, DefaultRenderersFactory(context).setEnableDecoderFallback(true))
        .setTrackSelector(trackSelector)
        .setLoadControl(loadControl)
        .setBandwidthMeter(meter)
        .setAudioAttributes(
            AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(),
            audioFocus,
        )
        .setHandleAudioBecomingNoisy(true)
        .build()
        .apply {
            setMediaItem(buildMediaItem(url, latencyMs, channel))
            playWhenReady = true
            prepare()
        }
    return PlayerHolder(player, meter)
}

internal fun buildMediaItem(url: String, latencyMs: Int, channel: ChannelInfo? = null): MediaItem =
    MediaItem.Builder()
        .setUri(url)
        // Título/artista aparecem na notificação de mídia e na tela de bloqueio.
        .setMediaMetadata(
            androidx.media3.common.MediaMetadata.Builder()
                .setTitle(channel?.title?.ifBlank { null } ?: channel?.username)
                .setArtist(channel?.username)
                .setArtworkUri(channel?.avatar?.let { android.net.Uri.parse(it) })
                .build(),
        )
        .setMimeType(MimeTypes.APPLICATION_M3U8)
        .setLiveConfiguration(
            MediaItem.LiveConfiguration.Builder()
                .setTargetOffsetMs(latencyMs.toLong())
                .setMinPlaybackSpeed(1f)
                // Só acelera para alcançar o ao vivo no modo de baixa latência.
                .setMaxPlaybackSpeed(if (latencyMs <= 4_000) 1.05f else 1f)
                .build(),
        )
        .build()

@OptIn(UnstableApi::class)
@Composable
private fun PlayerContent(
    channel: ChannelInfo,
    landscape: Boolean,
    inPip: Boolean,
    onBack: () -> Unit,
    onToggleFullscreen: () -> Unit,
    onEnterPip: () -> Unit,
    onMinimize: () -> Unit,
) {
    val context = LocalContext.current
    val prefs = remember { Prefs(context) }
    var qualityPref by remember(channel.slug) { mutableIntStateOf(prefs.channelQuality(channel.slug) ?: prefs.quality) }
    var latencyMs by remember { mutableIntStateOf(prefs.latencyMs) }
    var showStats by remember { mutableStateOf(prefs.showStats) }
    var favorite by remember { mutableStateOf(prefs.isFavorite(channel.slug)) }
    var landscapeChat by remember { mutableStateOf(prefs.landscapeChat) }

    // Seguir na conta da Kick (só com login).
    val loggedIn = remember { KickSession.hasToken }
    var following by remember { mutableStateOf<Boolean?>(null) }
    var followBusy by remember { mutableStateOf(false) }
    val followScope = rememberCoroutineScope()
    var sleepMinutes by remember { mutableIntStateOf(0) }
    LaunchedEffect(channel.slug) {
        if (loggedIn) following = runCatching { KickApi.isFollowing(channel.slug) }.getOrNull()
    }

    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    val haptics = rememberHaptics()
    val toggleFavorite = {
        haptics.confirm()
        favorite = prefs.toggleFavorite(channel.slug)
        if (favorite) {
            // Já está ao vivo agora: não precisa avisar de novo.
            prefs.notifiedLive = prefs.notifiedLive + channel.slug
            if (Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    var options by remember { mutableStateOf<List<QualityOption>>(emptyList()) }
    var playing by remember { mutableStateOf(false) }
    var buffering by remember { mutableStateOf(true) }
    var playbackInfo by remember { mutableStateOf("") }
    var statsText by remember { mutableStateOf("") }
    val errorMessage = PlayerSession.errorMessage
    var controlsVisible by remember { mutableStateOf(true) }
    var interactionTick by remember { mutableIntStateOf(0) }
    var menuOpen by remember { mutableStateOf(false) }
    var droppedFrames by remember { mutableIntStateOf(0) }
    var rebuffers by remember { mutableIntStateOf(0) }
    var recentStalls by remember { mutableIntStateOf(0) }
    var liveOffsetMs by remember { mutableLongStateOf(0L) }
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var chatOverlay by remember { mutableStateOf(prefs.chatOverlay) }
    var bannerDismissed by remember { mutableStateOf(false) }
    var decoderName by remember { mutableStateOf("") }
    val stallTimes = remember { mutableStateListOf<Long>() }

    val holder = remember(channel.playbackUrl, latencyMs) {
        PlayerSession.obtain(context, channel, latencyMs, qualityPref != QUALITY_AUTO, prefs.bandwidthBps)
    }
    val player = holder.player

    fun applyQuality(pref: Int, opts: List<QualityOption>) {
        if (opts.isEmpty()) return
        val builder = player.trackSelectionParameters.buildUpon().clearOverridesOfType(C.TRACK_TYPE_VIDEO)
        if (pref == QUALITY_AUTO) {
            builder.setForceHighestSupportedBitrate(false)
        } else {
            val target = if (pref == QUALITY_MAX) opts.first()
            else opts.firstOrNull { it.height <= pref } ?: opts.last()
            builder.setForceHighestSupportedBitrate(true)
                .setOverrideForType(TrackSelectionOverride(target.group.mediaTrackGroup, target.trackIndex))
        }
        player.trackSelectionParameters = builder.build()
    }

    fun setQuality(value: Int) {
        qualityPref = value
        prefs.setChannelQuality(channel.slug, value)
        bannerDismissed = false
        applyQuality(value, options)
    }

    DisposableEffect(player) {
        var failures = 0
        var applied = false
        var lastState = Player.STATE_IDLE
        val listener = object : Player.Listener {
            override fun onTracksChanged(tracks: Tracks) {
                val found = tracks.groups
                    .filter { it.type == C.TRACK_TYPE_VIDEO }
                    .flatMap { group ->
                        (0 until group.length)
                            .filter { group.isTrackSupported(it) }
                            .map { i ->
                                val f = group.getTrackFormat(i)
                                val fps = Math.round(f.frameRate).takeIf { it > 0 } ?: 30
                                QualityOption(
                                    label = "${f.height}p${if (fps >= 50) fps.toString() else ""}",
                                    height = f.height,
                                    bitrate = f.bitrate,
                                    group = group,
                                    trackIndex = i,
                                )
                            }
                    }
                    .sortedWith(compareByDescending<QualityOption> { it.height }.thenByDescending { it.bitrate })
                if (found.isNotEmpty()) {
                    options = found
                    if (!applied) {
                        applied = true
                        applyQuality(qualityPref, found)
                    }
                }
            }

            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_BUFFERING && lastState == Player.STATE_READY) {
                    rebuffers++
                    stallTimes.add(System.currentTimeMillis())
                }
                lastState = state
                buffering = state == Player.STATE_BUFFERING
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                playing = isPlaying
            }
        }
        val analytics = object : AnalyticsListener {
            override fun onDroppedVideoFrames(eventTime: AnalyticsListener.EventTime, dropped: Int, elapsedMs: Long) {
                droppedFrames += dropped
            }

            override fun onVideoDecoderInitialized(
                eventTime: AnalyticsListener.EventTime,
                name: String,
                initializedTimestampMs: Long,
                initializationDurationMs: Long,
            ) {
                decoderName = name
            }
        }
        // A sessão pode já estar tocando (voltando do mini player): sincroniza o estado atual.
        playing = player.isPlaying
        buffering = player.playbackState == Player.STATE_BUFFERING
        player.addListener(listener)
        player.addAnalyticsListener(analytics)
        listener.onTracksChanged(player.currentTracks)
        onDispose {
            player.removeListener(listener)
            player.removeAnalyticsListener(analytics)
            val estimate = holder.meter.bitrateEstimate
            if (estimate > 0) prefs.bandwidthBps = estimate
            // Não libera o player: ele continua na sessão (mini player).
        }
    }

    // Timer para dormir: pausa a live quando o tempo acaba.
    LaunchedEffect(sleepMinutes) {
        if (sleepMinutes > 0) {
            delay(sleepMinutes * 60_000L)
            player.pause()
            Toast.makeText(context, "Timer encerrado: live pausada.", Toast.LENGTH_LONG).show()
            sleepMinutes = 0
        }
    }

    // Permite PiP ao apertar Home enquanto está tocando.
    DisposableEffect(playing) {
        PlayerState.pipAllowed = playing
        onDispose { PlayerState.pipAllowed = false }
    }

    // Estatísticas, contagem de travadas recentes e informação da resolução atual.
    LaunchedEffect(player) {
        while (true) {
            val now = System.currentTimeMillis()
            nowMs = now
            liveOffsetMs = player.currentLiveOffset.takeIf { it != C.TIME_UNSET } ?: 0L
            stallTimes.removeAll { now - it > 60_000 }
            recentStalls = stallTimes.size

            player.videoFormat?.let { f ->
                val fps = if (f.frameRate >= 50f) Math.round(f.frameRate).toString() else ""
                val mbps = if (f.bitrate > 0) " • %.1f Mbps".format(f.bitrate / 1_000_000.0) else ""
                playbackInfo = "${f.height}p$fps$mbps"
            }
            if (showStats) {
                val live = player.currentLiveOffset.takeIf { it != C.TIME_UNSET } ?: -1L
                statsText = "%s • buffer %.1fs • latência %.1fs • rede %.1f Mbps\nquadros perdidos %d • travadas %d • %s".format(
                    playbackInfo, player.totalBufferedDuration / 1000.0, live / 1000.0, holder.meter.bitrateEstimate / 1e6,
                    droppedFrames, rebuffers, decoderName,
                )
                Log.d("NewKickStats", statsText.replace('\n', ' '))
            }
            delay(1_000)
        }
    }

    // Esconde os controles sozinho depois de alguns segundos tocando.
    LaunchedEffect(controlsVisible, interactionTick, playing, menuOpen) {
        if (controlsVisible && playing && !menuOpen) {
            delay(3_500)
            controlsVisible = false
        }
    }

    // Sugestão quando a qualidade travada não cabe na conexão.
    val suggestion: QualityOption? = run {
        if (qualityPref == QUALITY_AUTO || recentStalls < 2 || bannerDismissed || options.size < 2) return@run null
        val current = if (qualityPref == QUALITY_MAX) options.first().height else qualityPref
        val bw = holder.meter.bitrateEstimate
        val pick = options.firstOrNull { it.bitrate <= bw * 0.75 } ?: options.last()
        pick.takeIf { it.height < current }
    }

    val activity = remember { context.findActivity() }
    val haptic = LocalHapticFeedback.current
    var hud by remember { mutableStateOf<String?>(null) }
    var hudVersion by remember { mutableIntStateOf(0) }
    LaunchedEffect(hudVersion) {
        if (hudVersion > 0) {
            delay(900)
            hud = null
        }
    }
    DisposableEffect(Unit) {
        // Devolve o brilho ao valor do sistema ao sair do player.
        onDispose {
            activity?.window?.let { w -> w.attributes = w.attributes.also { it.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE } }
        }
    }
    var brightness by remember {
        mutableStateOf(
            activity?.window?.attributes?.screenBrightness?.takeIf { it >= 0f }
                ?: (Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, 128) / 255f),
        )
    }
    val audio = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    var volume by remember { mutableStateOf(audio.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)) }
    val adjustBrightness: (Float) -> Unit = { delta ->
        brightness = (brightness + delta).coerceIn(0.02f, 1f)
        activity?.window?.let { w -> w.attributes = w.attributes.also { it.screenBrightness = brightness } }
        hud = tr("Brilho %d%%").format((brightness * 100).toInt())
        hudVersion++
    }
    val adjustVolume: (Float) -> Unit = { delta ->
        volume = (volume + delta).coerceIn(0f, 1f)
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        audio.setStreamVolume(AudioManager.STREAM_MUSIC, Math.round(volume * max), 0)
        hud = tr("Volume %d%%").format((volume * 100).toInt())
        hudVersion++
    }
    val dragY = remember { Animatable(0f) }
    val dragScope = rememberCoroutineScope()
    val finishDrag: () -> Unit = {
        dragScope.launch {
            if (dragY.value > 220f) {
                dragY.animateTo(1200f, tween(240))
                onMinimize()
            } else {
                dragY.animateTo(0f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium))
            }
        }
    }
    val toggleChat: () -> Unit = {
        landscapeChat = !landscapeChat
        prefs.landscapeChat = landscapeChat
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    val playerBox: @Composable (Modifier) -> Unit = { modifier ->
        Box(
            modifier = modifier
                .background(Color.Black)
                .pointerInput(landscape) {
                    detectTapGestures(
                        onTap = {
                            controlsVisible = !controlsVisible
                            interactionTick++
                        },
                        // Paisagem: abre/fecha o chat. Retrato: tela cheia.
                        onDoubleTap = { if (landscape) toggleChat() else onToggleFullscreen() },
                    )
                }
                .pointerInput(landscape, inPip) {
                    // Retrato: segurar e arrastar para baixo transforma o player em mini player.
                    if (landscape || inPip) return@pointerInput
                    detectDragGesturesAfterLongPress(
                        onDragStart = { haptic.performHapticFeedback(HapticFeedbackType.LongPress) },
                        onDragEnd = { finishDrag() },
                        onDragCancel = { finishDrag() },
                    ) { change, amount ->
                        change.consume()
                        dragScope.launch { dragY.snapTo((dragY.value + amount.y).coerceAtLeast(0f)) }
                    }
                }
                .pointerInput(landscape) {
                    // Brilho/volume por gesto: só em tela cheia.
                    if (!landscape || !prefs.gestures) return@pointerInput
                    var leftSide = true
                    detectVerticalDragGestures(
                        onDragStart = { leftSide = it.x < size.width / 2f },
                    ) { _, drag ->
                        val delta = -drag / size.height
                        if (leftSide) adjustBrightness(delta) else adjustVolume(delta)
                    }
                },
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                        )
                        useController = false
                        keepScreenOn = true
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                        setShutterBackgroundColor(android.graphics.Color.BLACK)
                    }
                },
                update = { it.player = player },
            )

            if (buffering && errorMessage == null) {
                KLoader(Modifier.align(Alignment.Center), size = 40.dp)
            }
            if (PlayerSession.audioOnly) {
                Box(Modifier.fillMaxSize().background(Color(0xFF0B0E0F)), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        AsyncImage(
                            model = channel.avatar,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(64.dp).clip(CircleShape).background(Color(0xFF1D2326)),
                        )
                        Text("Só áudio", color = Color.White, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
                    }
                }
            }
            errorMessage?.let {
                Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(it, color = Color(0xFFFF8A80), fontSize = 13.sp)
                    TextButton(onClick = { PlayerSession.retryNow() }) { Text("Tentar de novo", color = KickGreen) }
                }
            }
            hud?.let {
                Text(
                    it,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 20.dp)
                        .clip(RoundedCornerShape(20.dp)).background(Color(0xB3000000)).padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }

            if (!inPip) {
                if (showStats) {
                    Text(
                        statsText,
                        color = KickGreen,
                        fontSize = 10.sp,
                        lineHeight = 13.sp,
                        modifier = Modifier.align(Alignment.BottomStart).padding(start = 8.dp, bottom = 48.dp)
                            .background(Color(0x99000000)).padding(4.dp),
                    )
                }

                suggestion?.let { s ->
                    Row(
                        modifier = Modifier.align(Alignment.TopCenter).padding(top = 56.dp, start = 12.dp, end = 12.dp)
                            .clip(RoundedCornerShape(10.dp)).background(Color(0xE6202528)).padding(start = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Conexão lenta para essa qualidade.", color = Color.White, fontSize = 12.sp, modifier = Modifier.weight(1f, fill = false))
                        TextButton(onClick = { setQuality(s.height) }) { Text(tr("Usar %s").format(s.label), color = KickGreen, fontSize = 12.sp) }
                        TextButton(onClick = { bannerDismissed = true }) { Text("Ignorar", color = Color.LightGray, fontSize = 12.sp) }
                    }
                }

                AnimatedVisibility(
                    visible = controlsVisible,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    Box(Modifier.fillMaxSize()) {
                        Box(
                            Modifier.fillMaxWidth().height(96.dp).align(Alignment.TopStart)
                                .background(Brush.verticalGradient(listOf(Color(0xB3000000), Color.Transparent))),
                        )
                        Box(
                            Modifier.fillMaxWidth().height(110.dp).align(Alignment.BottomStart)
                                .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xB3000000)))),
                        )
                        Row(
                            modifier = Modifier.align(Alignment.TopStart).fillMaxWidth().padding(4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            IconButton(onClick = onBack) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar", tint = Color.White)
                            }
                            Text(
                                channel.username,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(onClick = {
                                toggleFavorite()
                                interactionTick++
                            }) {
                                Icon(
                                    Icons.Default.Star,
                                    "Favorito",
                                    tint = if (favorite) KickGreen else Color(0x99FFFFFF),
                                )
                            }
                            IconButton(onClick = onEnterPip) {
                                Icon(Icons.Outlined.PictureInPictureAlt, "Picture-in-picture", tint = Color.White)
                            }
                            Box {
                                IconButton(onClick = { menuOpen = true; interactionTick++ }) {
                                    Icon(Icons.Default.Settings, "Configurações", tint = Color.White)
                                }
                                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                    fun mark(selected: Boolean, label: String) = (if (selected) "✓ " else "    ") + label

                                    DropdownMenuItem(text = { Text("QUALIDADE", fontSize = 11.sp, color = Color.Gray) }, onClick = {}, enabled = false)
                                    val qualityChoices = buildList {
                                        add(Triple("Automática (estável)", QUALITY_AUTO, ""))
                                        add(Triple("Máxima (travada)", QUALITY_MAX, ""))
                                        options.forEach { add(Triple(it.label, it.height, " • %.1f Mbps".format(it.bitrate / 1e6))) }
                                    }
                                    qualityChoices.forEach { (label, value, extra) ->
                                        val selected = qualityPref == value
                                        DropdownMenuItem(
                                            text = { Text(mark(selected, label + extra), color = if (selected) KickGreen else Color.Unspecified) },
                                            onClick = { menuOpen = false; setQuality(value) },
                                        )
                                    }
                                    DropdownMenuItem(text = { Text("LATÊNCIA", fontSize = 11.sp, color = Color.Gray) }, onClick = {}, enabled = false)
                                    latencyChoices.forEach { (label, value) ->
                                        val selected = latencyMs == value
                                        DropdownMenuItem(
                                            text = { Text(mark(selected, label), color = if (selected) KickGreen else Color.Unspecified) },
                                            onClick = {
                                                menuOpen = false
                                                latencyMs = value
                                                prefs.latencyMs = value
                                            },
                                        )
                                    }
                                    DropdownMenuItem(text = { Text("TIMER PARA DORMIR", fontSize = 11.sp, color = Color.Gray) }, onClick = {}, enabled = false)
                                    listOf(0 to "Desligado", 15 to "15 min", 30 to "30 min", 60 to "60 min").forEach { (minutes, label) ->
                                        val selected = sleepMinutes == minutes
                                        DropdownMenuItem(
                                            text = { Text(mark(selected, label), color = if (selected) KickGreen else Color.Unspecified) },
                                            onClick = {
                                                menuOpen = false
                                                sleepMinutes = minutes
                                            },
                                        )
                                    }
                                    DropdownMenuItem(text = { Text("EXIBIÇÃO", fontSize = 11.sp, color = Color.Gray) }, onClick = {}, enabled = false)
                                    DropdownMenuItem(
                                        text = { Text(mark(PlayerSession.audioOnly, "Só áudio"), color = if (PlayerSession.audioOnly) KickGreen else Color.Unspecified) },
                                        onClick = {
                                            menuOpen = false
                                            PlayerSession.updateAudioOnly(!PlayerSession.audioOnly)
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = { Text(mark(chatOverlay, "Chat sobre o vídeo (paisagem)"), color = if (chatOverlay) KickGreen else Color.Unspecified) },
                                        onClick = {
                                            menuOpen = false
                                            chatOverlay = !chatOverlay
                                            prefs.chatOverlay = chatOverlay
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = { Text(mark(showStats, "Estatísticas"), color = if (showStats) KickGreen else Color.Unspecified) },
                                        onClick = {
                                            menuOpen = false
                                            showStats = !showStats
                                            prefs.showStats = showStats
                                        },
                                    )
                                }
                            }
                        }

                        IconButton(
                            onClick = {
                                if (player.isPlaying) player.pause() else {
                                    player.seekToDefaultPosition()
                                    player.play()
                                }
                                interactionTick++
                            },
                            modifier = Modifier.align(Alignment.Center).size(68.dp).clip(CircleShape).background(Color(0x66000000)),
                        ) {
                            if (playing) PauseGlyph() else Icon(
                                Icons.Default.PlayArrow, "Tocar", tint = Color.White, modifier = Modifier.size(48.dp),
                            )
                        }

                        Row(
                            modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            val behindLive = liveOffsetMs > latencyMs + 4_000
                            Text(
                                if (behindLive) "VOLTAR AO VIVO" else "AO VIVO",
                                color = if (behindLive) Color.White else Color(0xFF0B0E0F),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (behindLive) Color(0xFF59626A) else KickGreen)
                                    .clickable { player.seekToDefaultPosition() }
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                            Text(playbackInfo, color = Color.White, fontSize = 12.sp, modifier = Modifier.weight(1f))
                            if (landscape) {
                                IconButton(
                                    onClick = {
                                        landscapeChat = !landscapeChat
                                        prefs.landscapeChat = landscapeChat
                                    },
                                    modifier = Modifier.size(40.dp),
                                ) {
                                    Icon(
                                        if (landscapeChat) Icons.Filled.ChatBubble else Icons.Outlined.ChatBubbleOutline,
                                        if (landscapeChat) "Ocultar chat" else "Mostrar chat",
                                        tint = if (landscapeChat) KickGreen else Color.White,
                                    )
                                }
                            }
                            IconButton(onClick = onToggleFullscreen, modifier = Modifier.size(40.dp)) {
                                Icon(
                                    if (landscape) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen,
                                    if (landscape) "Sair da tela cheia" else "Tela cheia",
                                    tint = Color.White,
                                    modifier = Modifier.size(28.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (inPip) {
        playerBox(Modifier.fillMaxSize())
    } else if (landscape) {
        val chatWidth by animateDpAsState(
            targetValue = if (landscapeChat) 320.dp else 0.dp,
            animationSpec = tween(360, easing = FastOutSlowInEasing),
            label = "chatWidth",
        )
        if (chatOverlay) {
            // Chat translúcido sobre o vídeo: o vídeo não muda de tamanho.
            val overlayOffset by animateDpAsState(
                targetValue = if (landscapeChat) 0.dp else 300.dp,
                animationSpec = tween(360, easing = FastOutSlowInEasing),
                label = "overlayOffset",
            )
            Box(Modifier.fillMaxSize()) {
                playerBox(Modifier.fillMaxSize())
                Box(
                    Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(300.dp)
                        .offset(x = overlayOffset)
                        .graphicsLayer { alpha = 0.8f }
                        .clipToBounds(),
                ) {
                    ChatPanel(channel.slug, Modifier.requiredWidth(300.dp).fillMaxHeight())
                }
            }
        } else {
            Row(Modifier.fillMaxSize()) {
                playerBox(Modifier.weight(1f).fillMaxHeight())
                // O chat continua carregado (largura 0 quando fechado) e só desliza para dentro.
                Box(Modifier.width(chatWidth).fillMaxHeight().clipToBounds()) {
                    ChatPanel(channel.slug, Modifier.requiredWidth(320.dp).fillMaxHeight())
                }
            }
        }
    } else {
        Column(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val p = (dragY.value / 900f).coerceIn(0f, 1f)
                    val sc = 1f - 0.5f * p
                    translationY = dragY.value
                    scaleX = sc
                    scaleY = sc
                    alpha = 1f - 0.6f * p
                    transformOrigin = TransformOrigin(0.08f, 1f)
                }
                .statusBarsPadding(),
        ) {
            playerBox(Modifier.fillMaxWidth().aspectRatio(16f / 9f))
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, top = 12.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(
                    model = channel.avatar,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.padding(end = 12.dp).size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
                )
                Column(Modifier.weight(1f)) {
                    Text(channel.title, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(
                        listOfNotNull(
                            channel.username,
                            tr("%s assistindo").format(formatViewers(channel.viewers)),
                            channel.category?.takeIf { it.isNotBlank() },
                            channel.startedAtMs?.let { tr("ao vivo há %s").format(formatElapsed(nowMs - it)) },
                        ).joinToString(" • "),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                if (loggedIn) {
                    Text(
                        when (following) {
                            true -> "Seguindo"
                            false -> "Seguir"
                            null -> "..."
                        },
                        color = if (following == true) KickGreen else Color(0xFF0B0E0F),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (following == true) Color(0x3353FC19) else KickGreen)
                            .border(1.5.dp, KickGreen, RoundedCornerShape(8.dp))
                            .clickable(enabled = following != null && !followBusy) {
                                val want = following != true
                                followBusy = true
                                followScope.launch {
                                    val status = ChatWeb.setFollow(channel.slug, want)
                                    if (status in 200..299) {
                                        following = want
                                    } else {
                                        val reason = when (status) {
                                            ChatWeb.STATUS_NO_PAGE -> "Aguarde o chat carregar e tente de novo."
                                            ChatWeb.STATUS_NO_SESSION -> "Entre na sua conta primeiro."
                                            else -> tr(if (want) "Não foi possível seguir (código %d)." else "Não foi possível deixar de seguir (código %d).").format(status)
                                        }
                                        Toast.makeText(context, reason, Toast.LENGTH_LONG).show()
                                    }
                                    followBusy = false
                                }
                            }
                            .padding(horizontal = 12.dp, vertical = 7.dp),
                    )
                }
                IconButton(onClick = {
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, tr("%s está ao vivo na Kick: %s").format(channel.username, "https://kick.com/${channel.slug}"))
                    }
                    context.startActivity(Intent.createChooser(send, "Compartilhar canal"))
                }) {
                    Icon(Icons.Default.Share, "Compartilhar", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { toggleFavorite() }) {
                    Icon(Icons.Default.Star, "Favorito", tint = if (favorite) KickGreen else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            ChatPanel(channel.slug, Modifier.weight(1f).fillMaxWidth().padding(top = 6.dp))
        }
    }
}

/** Chat oficial da Kick (página popout) dentro de um WebView: emotes, badges e login funcionam. */
@Composable
private fun ChatPanel(slug: String, modifier: Modifier) {
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            android.webkit.WebView(ctx).apply {
                setBackgroundColor(0xFF0B0E0F.toInt())
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                // O user-agent padrão do WebView ("; wv") é barrado pela proteção anti-bot da Kick.
                settings.userAgentString = settings.userAgentString.replace("; wv", "")
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                webViewClient = object : android.webkit.WebViewClient() {
                    // Mantém a navegação dentro do chat da Kick.
                    override fun shouldOverrideUrlLoading(
                        view: android.webkit.WebView,
                        request: android.webkit.WebResourceRequest,
                    ) = !(request.url.host?.endsWith("kick.com") ?: false)
                }
                addJavascriptInterface(ChatWeb.Bridge(), "NewKick")
                ChatWeb.webView = this
                loadUrl("https://kick.com/popout/$slug/chat")
            }
        },
        onRelease = {
            if (ChatWeb.webView === it) ChatWeb.webView = null
            it.destroy()
        },
    )
}

@Composable
private fun PauseGlyph() {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(2) {
            Box(Modifier.width(8.dp).height(34.dp).clip(RoundedCornerShape(2.dp)).background(Color.White))
        }
    }
}

/** "42 min" ou "2h 05min" para o tempo de transmissão. */
private fun formatElapsed(ms: Long): String {
    val minutes = (ms / 60_000).coerceAtLeast(0)
    return if (minutes < 60) "$minutes min" else "${minutes / 60}h %02dmin".format(minutes % 60)
}