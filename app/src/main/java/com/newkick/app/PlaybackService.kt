package com.newkick.app

import android.app.PendingIntent
import android.content.Intent
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

/**
 * Mantém a live tocando (só áudio) com o app em segundo plano ou a tela apagada, e mostra os
 * controles de mídia na notificação e na tela de bloqueio.
 */
@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        // Nome do canal de notificação e ícone próprios (em vez do padrão do Media3).
        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this)
                .setChannelName(R.string.notification_channel_playback)
                .build()
                .also { it.setSmallIcon(R.drawable.ic_kick_k) },
        )
        val player = PlayerSession.player
        if (player == null) {
            stopSelf()
            return
        }
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        session = MediaSession.Builder(this, player).setSessionActivity(open).build()
        // Sem controlador externo conectado, a sessão precisa ser registrada para gerar a notificação.
        addSession(session!!)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    // Fechou o app pelos recentes: encerra a live em vez de gastar bateria em segundo plano.
    override fun onTaskRemoved(rootIntent: Intent?) {
        PlayerSession.release()
        stopSelf()
    }

    override fun onDestroy() {
        session?.release()
        session = null
        super.onDestroy()
    }
}