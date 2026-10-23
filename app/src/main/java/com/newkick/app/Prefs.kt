package com.newkick.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Preferência de qualidade: MAX trava na melhor, AUTO deixa o ABR decidir, senão a altura em px. */
const val QUALITY_MAX = -1
const val QUALITY_AUTO = 0

data class HistoryEntry(val slug: String, val username: String, val category: String?, val at: Long)

class Prefs(context: Context) {
    private val sp = context.applicationContext.getSharedPreferences("newkick", Context.MODE_PRIVATE)

    // ---- Player ----

    var quality: Int
        get() = sp.getInt("quality", QUALITY_AUTO)
        set(v) = sp.edit().putInt("quality", v).apply()

    /** Qualidade lembrada por canal (vence a padrão). */
    fun channelQuality(slug: String): Int? = if (sp.contains("quality_$slug")) sp.getInt("quality_$slug", QUALITY_AUTO) else null

    fun setChannelQuality(slug: String, value: Int) = sp.edit().putInt("quality_$slug", value).apply()

    /** Distância do ao vivo: maior = mais buffer de segurança, menos travadas. */
    var latencyMs: Int
        get() = sp.getInt("latency_ms", 10_000)
        set(v) = sp.edit().putInt("latency_ms", v).apply()

    var showStats: Boolean
        get() = sp.getBoolean("show_stats", false)
        set(v) = sp.edit().putBoolean("show_stats", v).apply()

    /** Brilho/volume por arrasto em tela cheia. */
    var gestures: Boolean
        get() = sp.getBoolean("gestures", true)
        set(v) = sp.edit().putBoolean("gestures", v).apply()

    var landscapeChat: Boolean
        get() = sp.getBoolean("landscape_chat", false)
        set(v) = sp.edit().putBoolean("landscape_chat", v).apply()

    /** Em paisagem, o chat fica sobre o vídeo (translúcido) em vez de ao lado. */
    var chatOverlay: Boolean
        get() = sp.getBoolean("chat_overlay", false)
        set(v) = sp.edit().putBoolean("chat_overlay", v).apply()

    /** Última banda medida pelo player, usada como estimativa inicial na próxima live. */
    var bandwidthBps: Long
        get() = sp.getLong("bandwidth_bps", 0L)
        set(v) = sp.edit().putLong("bandwidth_bps", v).apply()

    // ---- Aparência ----

    var lang: String
        get() = sp.getString("lang", "pt") ?: "pt"
        set(v) = sp.edit().putString("lang", v).apply()

    /** "dark", "light" ou "system". */
    var themeMode: String
        get() = sp.getString("theme_mode", "dark") ?: "dark"
        set(v) = sp.edit().putString("theme_mode", v).apply()

    var onboarded: Boolean
        get() = sp.getBoolean("onboarded", false)
        set(v) = sp.edit().putBoolean("onboarded", v).apply()

    // ---- Favoritos, silenciados e notificações ----

    fun favorites(): List<String> = sp.getStringSet("favorites", emptySet())!!.sorted()

    fun isFavorite(slug: String) = sp.getStringSet("favorites", emptySet())!!.contains(slug)

    fun toggleFavorite(slug: String): Boolean {
        val set = sp.getStringSet("favorites", emptySet())!!.toMutableSet()
        val added = set.add(slug)
        if (!added) set.remove(slug)
        sp.edit().putStringSet("favorites", set).apply()
        return added
    }

    fun clearFavorites() = sp.edit().remove("favorites").apply()

    /** Canais sem aviso de "entrou ao vivo". */
    fun mutedChannels(): Set<String> = sp.getStringSet("muted", emptySet())!!.toSet()

    fun isMuted(slug: String) = mutedChannels().contains(slug)

    fun toggleMuted(slug: String): Boolean {
        val set = mutedChannels().toMutableSet()
        val nowMuted = set.add(slug)
        if (!nowMuted) set.remove(slug)
        sp.edit().putStringSet("muted", set).apply()
        return nowMuted
    }

    /** Favoritos que já foram avisados como "ao vivo" (evita notificar de novo a cada checagem). */
    var notifiedLive: Set<String>
        get() = sp.getStringSet("notified_live", emptySet())!!.toSet()
        set(v) = sp.edit().putStringSet("notified_live", v).apply()

    /** Avisar quando canais seguidos na conta da Kick entrarem ao vivo. */
    var notifyFollowed: Boolean
        get() = sp.getBoolean("notify_followed", true)
        set(v) = sp.edit().putBoolean("notify_followed", v).apply()

    /** Avisar quando favoritos locais (estrela) entrarem ao vivo. */
    var notifyFavorites: Boolean
        get() = sp.getBoolean("notify_favorites", true)
        set(v) = sp.edit().putBoolean("notify_favorites", v).apply()

    /** A primeira checagem após o login só registra quem já está ao vivo, sem notificar. */
    var followedBaseline: Boolean
        get() = sp.getBoolean("followed_baseline", false)
        set(v) = sp.edit().putBoolean("followed_baseline", v).apply()

    var askedNotifications: Boolean
        get() = sp.getBoolean("asked_notifications", false)
        set(v) = sp.edit().putBoolean("asked_notifications", v).apply()

    // ---- Recentes, histórico e buscas ----

    /** Últimos canais abertos, o mais recente primeiro. */
    fun recents(): List<String> = (sp.getString("recents", "") ?: "").split(',').filter { it.isNotBlank() }

    fun addRecent(slug: String) {
        val list = (listOf(slug) + recents().filter { it != slug }).take(8)
        sp.edit().putString("recents", list.joinToString(",")).apply()
    }

    fun history(): List<HistoryEntry> {
        val raw = sp.getString("history", null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                HistoryEntry(
                    slug = o.optString("slug"),
                    username = o.optString("username"),
                    category = o.optString("category").takeIf { it.isNotEmpty() },
                    at = o.optLong("at"),
                )
            }
        }.getOrDefault(emptyList())
    }

    /** Guarda no histórico (até 60 itens, sem repetir canal seguido: o mais recente sobe). */
    fun addHistory(slug: String, username: String, category: String?) {
        val list = (listOf(HistoryEntry(slug, username, category, System.currentTimeMillis())) + history().filter { it.slug != slug }).take(60)
        val arr = JSONArray()
        list.forEach {
            arr.put(JSONObject().put("slug", it.slug).put("username", it.username).put("category", it.category ?: "").put("at", it.at))
        }
        sp.edit().putString("history", arr.toString()).apply()
    }

    fun clearHistory() = sp.edit().remove("history").remove("recents").apply()

    fun searchHistory(): List<String> = (sp.getString("search_history", "") ?: "").split('\n').filter { it.isNotBlank() }

    fun addSearch(query: String) {
        val q = query.trim()
        if (q.length < 2) return
        val list = (listOf(q) + searchHistory().filter { !it.equals(q, ignoreCase = true) }).take(8)
        sp.edit().putString("search_history", list.joinToString("\n")).apply()
    }

    fun clearSearchHistory() = sp.edit().remove("search_history").apply()
}
