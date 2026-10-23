package com.newkick.app

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.RemoteViews
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Widget da tela inicial: quem você segue que está ao vivo agora (até 4). Toque abre a live. */
class LiveWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                refresh(context, manager, ids)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private val ROWS = listOf(
            Triple(R.id.row1, R.id.name1, R.id.viewers1),
            Triple(R.id.row2, R.id.name2, R.id.viewers2),
            Triple(R.id.row3, R.id.name3, R.id.viewers3),
            Triple(R.id.row4, R.id.name4, R.id.viewers4),
        )

        /** Atualiza todos os widgets (chamado a cada checagem de avisos e pelo Android de tempos em tempos). */
        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, LiveWidget::class.java))
            if (ids.isEmpty()) return
            CoroutineScope(Dispatchers.IO).launch { refresh(context, manager, ids) }
        }

        private suspend fun refresh(context: Context, manager: AppWidgetManager, ids: IntArray) {
            KickApi.init(context)
            val live = if (KickSession.hasToken) {
                runCatching { KickApi.followed() }.getOrNull()?.filter { it.isLive }?.sortedByDescending { it.viewers }
            } else {
                null
            }
            val openApp = PendingIntent.getActivity(
                context, 0, Intent(context, MainActivity::class.java).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            for (id in ids) {
                val views = RemoteViews(context.packageName, R.layout.widget_following)
                views.setOnClickPendingIntent(R.id.widget_header, openApp)
                views.setOnClickPendingIntent(R.id.widget_empty, openApp)
                val message = when {
                    !KickSession.hasToken -> context.getString(R.string.widget_login)
                    live == null -> context.getString(R.string.widget_error)
                    live.isEmpty() -> context.getString(R.string.widget_none)
                    else -> null
                }
                views.setTextViewText(R.id.widget_empty, message.orEmpty())
                views.setViewVisibility(R.id.widget_empty, if (message == null) android.view.View.GONE else android.view.View.VISIBLE)
                ROWS.forEachIndexed { index, (row, name, viewers) ->
                    val item = live?.getOrNull(index)
                    if (item == null) {
                        views.setViewVisibility(row, android.view.View.GONE)
                    } else {
                        views.setViewVisibility(row, android.view.View.VISIBLE)
                        views.setTextViewText(name, "● ${item.username}")
                        views.setTextViewText(viewers, formatViewers(item.viewers))
                        val open = Intent(context, MainActivity::class.java).apply {
                            action = Intent.ACTION_VIEW
                            data = Uri.parse("https://kick.com/${item.slug}")
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                        }
                        views.setOnClickPendingIntent(
                            row,
                            PendingIntent.getActivity(context, 100 + index + item.slug.hashCode(), open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT),
                        )
                    }
                }
                manager.updateAppWidget(id, views)
            }
        }
    }
}
