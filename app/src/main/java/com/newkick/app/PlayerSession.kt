package com.newkick.app

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * A live em reprodução. Vive acima da navegação: o player cheio e o mini player mostram o mesmo
 * ExoPlayer, então a live continua tocando enquanto você explora o app.
 */
object PlayerSession {
    var channel by mutableStateOf<ChannelInfo?>(null)
        private set
    var playing by mutableStateOf(false)
        private set
    var buffering by mutableStateOf(true)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set

    /** Só áudio (escolha do usuário): desliga o vídeo para economizar dados e bateria. */
    var audioOnly by mutableStateOf(false)
        private set

    private var holder: PlayerHolder? = null
    private var appContext: Context? = null
    private var latencyMs = 0
    private var failures = 0
    private var inForeground = true
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var statsJob: kotlinx.coroutines.Job? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val player: ExoPlayer? get() = holder?.player
    internal val meter get() = holder?.meter

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            playing = isPlaying
        }

        override fun onPlaybackStateChanged(state: Int) {
            buffering = state == Player.STATE_BUFFERING
            if (state == Player.STATE_READY) {
                failures = 0
                errorMessage = null
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            val p = player ?: return
            if (error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
                p.seekToDefaultPosition()
                p.prepare()
                return
            }
            if (++failures <= 3) {
                reload()
            } else {
                errorMessage = tr("Erro de reprodução: %s").format(error.errorCodeName)
            }
        }
    }

    fun cachedChannel(slug: String): ChannelInfo? = channel?.takeIf { it.slug.equals(slug, ignoreCase = true) }

    /** O token do playback_url expira: pede um novo e recarrega a live no mesmo player. */
    private fun reload() {
        val p = player ?: return
        val slug = channel?.slug ?: return
        scope.launch {
            val fresh = runCatching { KickApi.channel(slug) }.getOrNull()
            if (fresh != null && fresh.isLive && fresh.playbackUrl.isNotEmpty()) {
                channel = fresh
                p.setMediaItem(buildMediaItem(fresh.playbackUrl, latencyMs, fresh))
                p.prepare()
                p.play()
                errorMessage = null
            } else if (fresh != null && !fresh.isLive) {
                errorMessage = "A live terminou."
            }
        }
    }

    /** Tenta de novo (botão do usuário ou volta da internet). */
    fun retryNow() {
        failures = 0
        reload()
    }

    /** Devolve o player da sessão para esse canal, criando um novo (e liberando o antigo) se preciso. */
    internal fun obtain(context: Context, channel: ChannelInfo, latencyMs: Int, lockQuality: Boolean, initialBps: Long): PlayerHolder {
        appContext = context.applicationContext
        val current = holder
        if (current != null && this.channel?.slug == channel.slug && this.latencyMs == latencyMs) {
            this.channel = channel
            return current
        }
        release()
        val created = createPlayer(context.applicationContext, channel.playbackUrl, latencyMs, lockQuality, initialBps, channel)
        created.player.addListener(listener)
        holder = created
        this.channel = channel
        this.latencyMs = latencyMs
        playing = created.player.isPlaying
        buffering = true
        errorMessage = null
        failures = 0
        audioOnly = false
        muted = false
        applyVideo()
        registerNetworkWatcher(context.applicationContext)
        // Tempo assistido: soma 30 s a cada meio minuto em que a live está tocando (também em segundo plano).
        statsJob?.cancel()
        statsJob = scope.launch {
            while (true) {
                kotlinx.coroutines.delay(30_000)
                val c = this@PlayerSession.channel
                if (c != null && playing) appContext?.let { Prefs(it).addWatchSeconds(c.slug, 30) }
            }
        }
        // Serviço de mídia: áudio em segundo plano + notificação (o app está em primeiro plano agora).
        runCatching { appContext?.startService(Intent(appContext, PlaybackService::class.java)) }
        return created
    }

    /** Quando a internet volta e a live estava caída, retoma sozinha. */
    private fun registerNetworkWatcher(context: Context) {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return
        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                scope.launch {
                    val p = player ?: return@launch
                    if (errorMessage != null || p.playbackState == Player.STATE_IDLE || p.playerError != null) retryNow()
                }
            }
        }
        runCatching { cm.registerDefaultNetworkCallback(cb) }.onSuccess { networkCallback = cb }
    }

    /** Mudo (botão do mini player): o vídeo segue tocando, sem som. */
    var muted by mutableStateOf(false)
        private set

    fun toggleMute() {
        muted = !muted
        player?.volume = if (muted) 0f else 1f
    }

    fun togglePlay() {
        val p = player ?: return
        if (p.isPlaying) p.pause() else {
            p.seekToDefaultPosition()
            p.play()
        }
    }

    /** App em primeiro plano ou não: fora da tela só o áudio segue tocando. */
    fun setForeground(foreground: Boolean) {
        inForeground = foreground
        applyVideo()
    }

    fun setVideoEnabled(enabled: Boolean) = setForeground(enabled)

    fun updateAudioOnly(value: Boolean) {
        audioOnly = value
        applyVideo()
        // Religar/desligar o vídeo recarrega as faixas: volta para o ao vivo em vez de ficar atrasado.
        player?.seekToDefaultPosition()
    }

    private fun applyVideo() {
        val p = player ?: return
        val enabled = inForeground && !audioOnly
        p.trackSelectionParameters = p.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, !enabled)
            .build()
    }

    fun release() {
        statsJob?.cancel()
        statsJob = null
        appContext?.let { ctx ->
            networkCallback?.let { cb -> runCatching { ctx.getSystemService(ConnectivityManager::class.java)?.unregisterNetworkCallback(cb) } }
            runCatching { ctx.stopService(Intent(ctx, PlaybackService::class.java)) }
        }
        networkCallback = null
        holder?.let { h ->
            val estimate = h.meter.bitrateEstimate
            if (estimate > 0) appContext?.let { Prefs(it).bandwidthBps = estimate }
            h.player.removeListener(listener)
            h.player.release()
        }
        holder = null
        channel = null
        playing = false
        buffering = true
        errorMessage = null
        audioOnly = false
        muted = false
    }
}
