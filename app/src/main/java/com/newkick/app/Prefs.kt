package com.newkick.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Preferência de qualidade: MAX trava na melhor, AUTO deixa o ABR decidir, senão a altura em px. */
const val QUALITY_MAX = -1
const val QUALITY_AUTO = 0

data class HistoryEntry(val slug: String, val username: String, val category: String?, val at: Long)

/** Um clipe ou transmissão que você parou no meio. */
data class VodProgress(val url: String, val position: Long, val title: String, val thumbnail: String?, val channel: String, val duration: Long, val at: Long)

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

    /** Idioma do app: "system", "pt", "en" ou "es". */
    var uiLang: String
        get() = sp.getString("ui_lang", "system") ?: "system"
        set(v) = sp.edit().putString("ui_lang", v).apply()

    /** "dark", "light" ou "system". */
    var themeMode: String
        get() = sp.getString("theme_mode", "dark") ?: "dark"
        set(v) = sp.edit().putString("theme_mode", v).apply()

    /** Usa as cores do papel de parede (Material You, Android 12+) nos fundos e superfícies. */
    var dynamicColors: Boolean
        get() = sp.getBoolean("dynamic_colors", false)
        set(v) = sp.edit().putBoolean("dynamic_colors", v).apply()

    /** Vibração curta ao segurar, trocar de aba e confirmar ações. */
    var haptics: Boolean
        get() = sp.getBoolean("haptics", true)
        set(v) = sp.edit().putBoolean("haptics", v).apply()

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

    /** Última vez que olhamos o GitHub atrás de versão nova (ms). */
    var lastUpdateCheck: Long
        get() = sp.getLong("last_update_check", 0L)
        set(v) = sp.edit().putLong("last_update_check", v).apply()

    /** Versão que o usuário já dispensou ("Depois"): não insiste até sair uma mais nova. */
    var skippedUpdate: String
        get() = sp.getString("skipped_update", "") ?: ""
        set(v) = sp.edit().putString("skipped_update", v).apply()

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

    // ---- Retomar clipes e transmissões ----

    /** Onde você parou neste vídeo (ms), ou 0. Guarda os últimos 50. */
    fun vodPosition(url: String): Long {
        val arr = runCatching { JSONArray(sp.getString("vod_positions", "[]")) }.getOrDefault(JSONArray())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            if (o.optString("u") == url) return o.optLong("p")
        }
        return 0L
    }

    /** Guarda onde você parou, com título e miniatura para a seção "Continuar assistindo". */
    fun setVodPosition(url: String, positionMs: Long, title: String = "", thumbnail: String? = null, channel: String = "", durationMs: Long = 0L) {
        val old = runCatching { JSONArray(sp.getString("vod_positions", "[]")) }.getOrDefault(JSONArray())
        val entry = JSONObject().put("u", url).put("p", positionMs).put("t", title).put("th", thumbnail ?: "")
            .put("c", channel).put("d", durationMs).put("at", System.currentTimeMillis())
        val list = mutableListOf(entry)
        for (i in 0 until old.length()) {
            val o = old.optJSONObject(i) ?: continue
            if (o.optString("u") != url) list += o
        }
        sp.edit().putString("vod_positions", JSONArray(list.take(50)).toString()).apply()
    }

    /** Clipes e transmissões que você começou e não terminou, o mais recente primeiro. */
    fun continueWatching(): List<VodProgress> {
        val arr = runCatching { JSONArray(sp.getString("vod_positions", "[]")) }.getOrDefault(JSONArray())
        return (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }
            .filter { it.optString("t").isNotBlank() && it.optLong("p") > 0 }
            .map {
                VodProgress(
                    it.optString("u"), it.optLong("p"), it.optString("t"), it.optString("th").ifBlank { null },
                    it.optString("c"), it.optLong("d"), it.optLong("at"),
                )
            }
            .sortedByDescending { it.at }
    }

    // ---- Listas de canais ----

    /** Suas listas ("LoL", "Música"…), na ordem em que foram criadas: nome → canais. */
    fun channelLists(): Map<String, List<String>> {
        val obj = runCatching { JSONObject(sp.getString("channel_lists", "{}")) }.getOrDefault(JSONObject())
        val out = LinkedHashMap<String, List<String>>()
        val order = runCatching { JSONArray(sp.getString("channel_list_order", "[]")) }.getOrDefault(JSONArray())
        val names = (0 until order.length()).map { order.getString(it) }.filter { obj.has(it) } + obj.keys().asSequence().filter { k ->
            (0 until order.length()).none { order.getString(it) == k }
        }
        for (name in names) {
            val arr = obj.optJSONArray(name) ?: continue
            out[name] = (0 until arr.length()).map { arr.getString(it) }
        }
        return out
    }

    private fun saveChannelLists(lists: Map<String, List<String>>) {
        val obj = JSONObject()
        lists.forEach { (name, slugs) -> obj.put(name, JSONArray(slugs)) }
        sp.edit().putString("channel_lists", obj.toString()).putString("channel_list_order", JSONArray(lists.keys.toList()).toString()).apply()
    }

    fun createChannelList(name: String) {
        val clean = name.trim().take(24)
        if (clean.isEmpty()) return
        val lists = LinkedHashMap(channelLists())
        if (!lists.containsKey(clean)) lists[clean] = emptyList()
        saveChannelLists(lists)
    }

    fun deleteChannelList(name: String) {
        val lists = LinkedHashMap(channelLists())
        lists.remove(name)
        saveChannelLists(lists)
    }

    /** Põe ou tira o canal da lista; devolve true se ficou dentro. */
    fun toggleInChannelList(name: String, slug: String): Boolean {
        val lists = LinkedHashMap(channelLists())
        val current = lists[name].orEmpty()
        val inside = slug !in current
        lists[name] = if (inside) current + slug else current - slug
        saveChannelLists(lists)
        return inside
    }

    // ---- Tempo assistido ----

    /** Soma segundos assistidos de um canal ao dia de hoje (guarda 60 dias). */
    fun addWatchSeconds(slug: String, seconds: Int) {
        val root = runCatching { JSONObject(sp.getString("watch_stats", "{}")) }.getOrDefault(JSONObject())
        val today = java.time.LocalDate.now().toString()
        val day = root.optJSONObject(today) ?: JSONObject().also { root.put(today, it) }
        day.put(slug, day.optLong(slug) + seconds)
        val cutoff = java.time.LocalDate.now().minusDays(60).toString()
        root.keys().asSequence().toList().filter { it < cutoff }.forEach { root.remove(it) }
        sp.edit().putString("watch_stats", root.toString()).apply()
    }

    /** Segundos assistidos por canal nos últimos [days] dias (hoje inclusive). */
    fun watchStats(days: Int): Map<String, Long> {
        val root = runCatching { JSONObject(sp.getString("watch_stats", "{}")) }.getOrDefault(JSONObject())
        val from = java.time.LocalDate.now().minusDays((days - 1).toLong()).toString()
        val out = HashMap<String, Long>()
        for (date in root.keys()) {
            if (date < from) continue
            val day = root.optJSONObject(date) ?: continue
            for (slug in day.keys()) out[slug] = (out[slug] ?: 0L) + day.optLong(slug)
        }
        return out
    }

    fun clearVodPosition(url: String) {
        val old = runCatching { JSONArray(sp.getString("vod_positions", "[]")) }.getOrDefault(JSONArray())
        val list = (0 until old.length()).mapNotNull { old.optJSONObject(it) }.filter { it.optString("u") != url }
        sp.edit().putString("vod_positions", JSONArray(list).toString()).apply()
    }

    /** Velocidade que você usa em clipes e transmissões (lembrada entre vídeos). */
    var vodSpeed: Float
        get() = sp.getFloat("vod_speed", 1f)
        set(v) = sp.edit().putFloat("vod_speed", v).apply()

    // ---- Horário silencioso dos avisos de live ----

    var quietHours: Boolean
        get() = sp.getBoolean("quiet_hours", false)
        set(v) = sp.edit().putBoolean("quiet_hours", v).apply()

    /** Hora (0–23) em que o silêncio começa. */
    var quietStart: Int
        get() = sp.getInt("quiet_start", 23)
        set(v) = sp.edit().putInt("quiet_start", v).apply()

    /** Hora (0–23) em que o silêncio termina. */
    var quietEnd: Int
        get() = sp.getInt("quiet_end", 8)
        set(v) = sp.edit().putInt("quiet_end", v).apply()

    /** Agora está dentro do horário silencioso? (aceita intervalos que viram a meia-noite) */
    fun inQuietHours(hour: Int = java.time.LocalTime.now().hour): Boolean {
        if (!quietHours || quietStart == quietEnd) return false
        return if (quietStart < quietEnd) hour in quietStart until quietEnd else hour >= quietStart || hour < quietEnd
    }

    /** Última versão cuja tela "Novidades" você já viu ("" = nunca). */
    var lastSeenVersion: String
        get() = sp.getString("last_seen_version", "") ?: ""
        set(v) = sp.edit().putString("last_seen_version", v).apply()

    /** Remove uma live do histórico. */
    fun removeHistory(slug: String) {
        val arr = JSONArray()
        history().filter { it.slug != slug }.forEach {
            arr.put(JSONObject().put("slug", it.slug).put("username", it.username).put("category", it.category ?: "").put("at", it.at))
        }
        sp.edit().putString("history", arr.toString()).apply()
    }

    /** Tamanho do texto do chat, em % (85 a 150). */
    var chatZoom: Int
        get() = sp.getInt("chat_zoom", 100)
        set(v) = sp.edit().putInt("chat_zoom", v).apply()

    /** Com bateria abaixo de 20% (sem carregar), o modo automático não passa de 480p. */
    var batterySaver: Boolean
        get() = sp.getBoolean("battery_saver", false)
        set(v) = sp.edit().putBoolean("battery_saver", v).apply()

    // ---- Seções da Home e ordem de Seguindo ----

    fun homeSection(name: String): Boolean = sp.getBoolean("home_$name", true)

    fun setHomeSection(name: String, visible: Boolean) = sp.edit().putBoolean("home_$name", visible).apply()

    /** Seguindo em ordem alfabética (em vez de por espectadores). */
    var followedAlpha: Boolean
        get() = sp.getBoolean("followed_alpha", false)
        set(v) = sp.edit().putBoolean("followed_alpha", v).apply()

    // ---- Economia de dados ----

    /** Em rede móvel (4G/5G), limita o modo automático a 480p. */
    var dataSaver: Boolean
        get() = sp.getBoolean("data_saver", false)
        set(v) = sp.edit().putBoolean("data_saver", v).apply()

    // ---- Backup ----

    /**
     * Tudo que é seu (favoritos, silenciados, histórico, buscas e preferências) em JSON.
     * O login não entra: ele fica nos cookies do app e não sai do aparelho.
     */
    fun exportJson(): String {
        val out = JSONObject()
        for ((key, value) in sp.all) {
            if (key in NOT_BACKED_UP) continue
            when (value) {
                is Set<*> -> out.put(key, JSONArray(value.map { it.toString() }))
                is Boolean, is Int, is Long, is String -> out.put(key, value)
                is Float -> out.put(key, value.toDouble())
            }
        }
        return JSONObject().put("app", "BetterKick").put("format", 1).put("prefs", out).toString(2)
    }

    /** Restaura um backup (sobrescreve as chaves que estiverem no arquivo). Devolve false se não for do BetterKick. */
    fun importJson(json: String): Boolean {
        val root = runCatching { JSONObject(json) }.getOrNull() ?: return false
        if (root.optString("app") != "BetterKick") return false
        val data = root.optJSONObject("prefs") ?: return false
        val editor = sp.edit()
        for (key in data.keys()) {
            if (key in NOT_BACKED_UP) continue
            when (val v = data.get(key)) {
                is JSONArray -> editor.putStringSet(key, (0 until v.length()).map { v.getString(it) }.toSet())
                is Boolean -> editor.putBoolean(key, v)
                is Int -> editor.putInt(key, v)
                is Long -> editor.putLong(key, v)
                is String -> editor.putString(key, v)
            }
        }
        editor.apply()
        return true
    }

    private companion object {
        /** Estado interno que não faz sentido levar para outro aparelho. */
        val NOT_BACKED_UP = setOf(
            "last_update_check", "skipped_update", "followed_baseline", "notified_live",
            "asked_notifications", "bandwidth_bps", "onboarded",
        )
    }
}
