package com.newkick.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat

/** Botão "Silenciar" do aviso de live: para de avisar desse canal e fecha o aviso. */
class LiveActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_MUTE) return
        val slug = intent.getStringExtra(EXTRA_SLUG) ?: return
        val prefs = Prefs(context)
        if (!prefs.isMuted(slug)) prefs.toggleMuted(slug)
        NotificationManagerCompat.from(context).cancel(slug.hashCode())
    }

    companion object {
        const val ACTION_MUTE = "com.newkick.app.MUTE_LIVE"
        const val EXTRA_SLUG = "slug"
    }
}
