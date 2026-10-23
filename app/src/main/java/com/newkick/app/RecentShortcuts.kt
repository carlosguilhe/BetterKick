package com.newkick.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat

/** Atalhos do ícone do app (segurar o ícone) com os últimos canais que você assistiu. */
object RecentShortcuts {
    private const val STATIC_COUNT = 3

    fun update(context: Context) {
        runCatching {
            val room = (ShortcutManagerCompat.getMaxShortcutCountPerActivity(context) - STATIC_COUNT).coerceIn(0, 3)
            if (room == 0) return
            val recent = Prefs(context).history().distinctBy { it.slug }.take(room)
            val shortcuts = recent.map { entry ->
                val open = Intent(context, MainActivity::class.java).apply {
                    action = Intent.ACTION_VIEW
                    data = Uri.parse("https://kick.com/${entry.slug}")
                }
                ShortcutInfoCompat.Builder(context, "recent_${entry.slug}")
                    .setShortLabel(entry.username)
                    .setLongLabel(entry.username)
                    .setIcon(IconCompat.createWithResource(context, R.mipmap.ic_launcher))
                    .setIntent(open)
                    .build()
            }
            ShortcutManagerCompat.setDynamicShortcuts(context, shortcuts)
        }
    }
}
