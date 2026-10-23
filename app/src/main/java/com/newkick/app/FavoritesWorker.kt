package com.newkick.app

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/** Avisa quando um canal favorito entra ao vivo. Roda a cada ~15 min com rede. */
class FavoritesWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        KickApi.init(applicationContext)
        Strings.init(applicationContext)
        val prefs = Prefs(applicationContext)
        val muted = prefs.mutedChannels()
        val favorites = if (prefs.notifyFavorites) prefs.favorites() else emptyList()
        val state = prefs.notifiedLive.toMutableSet()

        // Canais seguidos na conta (se houver login): a lista já traz quem está ao vivo.
        val followed = if (prefs.notifyFollowed && KickSession.hasToken) {
            runCatching { KickApi.followed() }.getOrNull()
        } else {
            null
        }
        val followedSlugs = followed.orEmpty().map { it.slug }.toSet()
        if (followed != null) state.retainAll(favorites.toSet() + followedSlugs) // fetch falhou: mantém o estado

        val baseline = followed != null && !prefs.followedBaseline
        for (f in followed.orEmpty()) {
            if (f.isLive) {
                // Na primeira checagem só registra quem já estava ao vivo, sem encher de avisos.
                if (state.add(f.slug) && !baseline && f.slug !in muted) notify(f.slug, f.username, f.title.ifBlank { f.category.orEmpty() })
            } else {
                state.remove(f.slug)
            }
        }
        if (baseline) prefs.followedBaseline = true
        Log.d("FavoritesWorker", "seguidos=${followed?.size} ao vivo=${followed.orEmpty().count { it.isLive }} baseline=$baseline estado=${state.size}")

        // Favoritos locais que não são seguidos na conta.
        for (slug in favorites) {
            if (slug in followedSlugs) continue
            val channel = runCatching { KickApi.channel(slug) }.getOrNull() ?: continue // erro de rede: mantém o estado
            if (channel.isLive) {
                if (state.add(slug) && slug !in muted) notify(slug, channel.username, channel.title.ifBlank { channel.category.orEmpty() })
            } else {
                state.remove(slug)
            }
        }
        prefs.notifiedLive = state
        LiveWidget.updateAll(applicationContext)
        return Result.success()
    }

    private fun notify(slug: String, username: String, text: String) {
        val context = applicationContext
        // Horário silencioso: não avisa (a live que começou nesse período não gera aviso depois).
        if (Prefs(context).inQuietHours()) return
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return

        val manager = context.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, tr("Favoritos ao vivo"), NotificationManager.IMPORTANCE_DEFAULT),
            )
        }
        val open = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = Uri.parse("https://kick.com/$slug")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(
            context, slug.hashCode(), open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val mute = PendingIntent.getBroadcast(
            context,
            slug.hashCode(),
            Intent(context, LiveActionReceiver::class.java).setAction(LiveActionReceiver.ACTION_MUTE).putExtra(LiveActionReceiver.EXTRA_SLUG, slug),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_kick_k)
            .setContentTitle(tr("%s está ao vivo").format(username))
            .setContentText(text)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setGroup(GROUP)
            .addAction(0, tr("Assistir"), pending)
            .addAction(0, tr("Silenciar"), mute)
            .build()
        // Com várias lives avisadas, o Android junta tudo numa pilha só.
        val summary = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_kick_k)
            .setContentTitle(tr("Lives ao vivo"))
            .setGroup(GROUP)
            .setGroupSummary(true)
            .setAutoCancel(true)
            .build()
        val manager2 = NotificationManagerCompat.from(context)
        manager2.notify(slug.hashCode(), notification)
        manager2.notify(SUMMARY_ID, summary)
    }

    companion object {
        private const val CHANNEL_ID = "live"
        private const val GROUP = "live-group"
        private const val SUMMARY_ID = 7001
        private const val WORK_NAME = "favorites-live"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<FavoritesWorker>(15, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        /** Verifica agora (usado para testes e quando o usuário favorita um canal). */
        fun checkNow(context: Context) {
            WorkManager.getInstance(context).enqueue(OneTimeWorkRequestBuilder<FavoritesWorker>().build())
        }
    }
}
