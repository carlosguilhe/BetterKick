package com.newkick.app

import android.content.Context
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

data class ChannelInfo(
    val slug: String,
    val username: String,
    val avatar: String?,
    val playbackUrl: String,
    val isLive: Boolean,
    val title: String,
    val viewers: Int,
    val category: String?,
    val thumbnail: String? = null,
    /** Início da transmissão atual (epoch ms), se conhecido. */
    val startedAtMs: Long? = null,
)

data class LiveItem(
    val slug: String,
    val username: String,
    val title: String,
    val viewers: Int,
    val category: String?,
    val thumbnail: String?,
    val avatar: String? = null,
)

data class SearchChannel(val slug: String, val username: String, val followers: Int)

data class Category(val id: Int, val slug: String, val name: String, val viewers: Int, val banner: String?)

data class UserInfo(val username: String, val email: String?, val avatar: String?)

data class FollowedChannel(
    val slug: String,
    val username: String,
    val avatar: String?,
    val isLive: Boolean,
    val viewers: Int,
    val title: String,
    val category: String?,
)

data class SearchResult(val channels: List<SearchChannel>, val categories: List<Category>)

data class ChannelProfile(
    val slug: String,
    val username: String,
    val avatar: String?,
    val followers: Int,
    val bio: String?,
    val isLive: Boolean,
    val verified: Boolean,
)

data class ClipItem(
    val id: String,
    val title: String,
    val url: String,
    val thumbnail: String?,
    val views: Int,
    val durationSec: Int,
    val creator: String?,
    val createdAt: String?,
)

data class VodItem(
    val id: String,
    val title: String,
    val url: String,
    val thumbnail: String?,
    val views: Int,
    val durationMs: Long,
    val category: String?,
    val createdAt: String?,
    val isLive: Boolean,
)

/** Cliente mínimo para os endpoints públicos (não oficiais) do site da Kick. */
object KickApi {
    private const val UA =
        "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/126.0.0.0 Mobile Safari/537.36"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private var cacheDir: File? = null
    private const val CACHE_MAX_AGE_MS = 24L * 60 * 60 * 1000

    /**
     * Falhas seguidas que não são de rede (HTTP 4xx/5xx inesperado ou resposta em formato
     * diferente). Três ou mais indicam que a Kick pode ter mudado algo no servidor.
     */
    var failureStreak by mutableIntStateOf(0)
        private set

    fun init(context: Context) {
        if (cacheDir == null) cacheDir = File(context.applicationContext.cacheDir, "api").apply { mkdirs() }
    }

    fun clearCache() {
        runCatching { cacheDir?.listFiles()?.forEach { it.delete() } }
    }

    private fun cacheFile(url: String): File? = cacheDir?.let { File(it, Integer.toHexString(url.hashCode()) + "_" + url.length) }

    private fun writeCache(url: String, body: String) {
        runCatching { cacheFile(url)?.writeText(body) }
    }

    /** Última resposta guardada (se for de menos de 24 h): permite abrir o app sem internet. */
    private fun readCache(url: String): String? = runCatching {
        cacheFile(url)?.takeIf { it.exists() && System.currentTimeMillis() - it.lastModified() < CACHE_MAX_AGE_MS }?.readText()
    }.getOrNull()

    private fun fetch(url: String, auth: Boolean): String {
        val builder = Request.Builder()
            .url(url)
            .header("User-Agent", UA)
            .header("Accept", "application/json")
            .header("Referer", "https://kick.com/")
        if (auth) {
            // Igual ao site: Bearer com o cookie de sessão + cookies da conta.
            val token = KickSession.token() ?: throw KickSessionExpired()
            builder.header("Authorization", "Bearer $token").header("x-app-platform", "web")
            KickSession.cookieHeader()?.let { builder.header("Cookie", it) }
            KickSession.xsrf()?.let { builder.header("x-xsrf-token", it) }
        }
        client.newCall(builder.build()).execute().use { response ->
            when {
                response.code == 401 && auth -> throw KickSessionExpired()
                response.code == 404 -> throw KickException("Canal não encontrado.", 404)
                response.code == 403 -> throw KickException("A Kick bloqueou a requisição (403).", 403)
                !response.isSuccessful -> throw KickException(tr("Erro HTTP %d.").format(response.code), response.code)
            }
            return response.body?.string().orEmpty()
        }
    }

    private suspend fun getBody(url: String, auth: Boolean = false): String = withContext(Dispatchers.IO) {
        try {
            val body = fetch(url, auth)
            if (!auth) writeCache(url, body)
            failureStreak = 0
            body
        } catch (e: KickSessionExpired) {
            throw e
        } catch (e: KickException) {
            if (e.code != 404) failureStreak++
            throw e
        } catch (e: IOException) {
            // Sem rede: tenta a última resposta guardada.
            if (!auth) readCache(url)?.let { return@withContext it }
            throw e
        }
    }

    private fun parseObject(body: String): JSONObject = try {
        JSONObject(body)
    } catch (e: JSONException) {
        failureStreak++
        throw KickException("Resposta inesperada da Kick.")
    }

    private fun parseArray(body: String): JSONArray = try {
        JSONArray(body)
    } catch (e: JSONException) {
        failureStreak++
        throw KickException("Resposta inesperada da Kick.")
    }

    private suspend fun getJson(url: String, auth: Boolean = false) = parseObject(getBody(url, auth))

    private fun JSONObject.firstString(vararg keys: String): String? =
        keys.firstNotNullOfOrNull { k -> optString(k).takeIf { it.isNotEmpty() && it != "null" } }

    private fun parseTime(value: String?): Long? {
        if (value.isNullOrBlank()) return null
        return runCatching { java.time.Instant.parse(value).toEpochMilli() }.getOrNull()
            ?: runCatching {
                java.time.LocalDateTime.parse(value.replace(' ', 'T')).toInstant(java.time.ZoneOffset.UTC).toEpochMilli()
            }.getOrNull()
    }

    suspend fun channel(slug: String): ChannelInfo {
        val clean = slug.trim().lowercase().replace('_', '-')
        val json = getJson("https://kick.com/api/v2/channels/$clean")
        val live = json.optJSONObject("livestream")
        return ChannelInfo(
            slug = json.optString("slug", clean),
            username = json.optJSONObject("user")?.optString("username").orEmpty().ifEmpty { clean },
            avatar = json.optJSONObject("user")?.optString("profile_pic")?.takeIf { it.isNotEmpty() && it != "null" },
            playbackUrl = json.optString("playback_url"),
            isLive = live?.optBoolean("is_live", false) == true,
            title = live?.optString("session_title").orEmpty(),
            viewers = live?.optInt("viewer_count", 0) ?: 0,
            category = live?.optJSONArray("categories")?.optJSONObject(0)?.optString("name"),
            thumbnail = live?.optJSONObject("thumbnail")?.let { t -> t.optString("src").ifEmpty { t.optString("url") } }?.takeIf { it.isNotEmpty() && it != "null" },
            startedAtMs = parseTime(live?.firstString("created_at", "start_time")),
        )
    }

    /** Dados da página do canal: seguidores, bio e se está ao vivo. */
    suspend fun channelProfile(slug: String): ChannelProfile {
        val clean = slug.trim().lowercase().replace('_', '-')
        val json = getJson("https://kick.com/api/v2/channels/$clean")
        val user = json.optJSONObject("user")
        return ChannelProfile(
            slug = json.optString("slug", clean),
            username = user?.optString("username").orEmpty().ifEmpty { clean },
            avatar = user?.firstString("profile_pic", "profilepic"),
            followers = json.optInt("followers_count", 0),
            bio = user?.firstString("bio"),
            isLive = json.optJSONObject("livestream")?.optBoolean("is_live", false) == true,
            verified = json.opt("verified") != null && json.opt("verified") != JSONObject.NULL,
        )
    }

    /** Miniatura pública da live atual de um canal (null se estiver offline). */
    suspend fun liveThumbnail(slug: String): String? {
        val data = getJson("https://kick.com/api/v2/channels/${slug.trim().lowercase()}/livestream").optJSONObject("data") ?: return null
        val t = data.optJSONObject("thumbnail") ?: return null
        return t.optString("src").ifEmpty { t.optString("url") }.takeIf { it.isNotEmpty() && it != "null" }
    }

    /** Clipes mais vistos do canal; `cursor` é o valor devolvido pela página anterior. */
    suspend fun clips(slug: String, cursor: String? = null): Pair<List<ClipItem>, String?> {
        val c = java.net.URLEncoder.encode(cursor ?: "0", "UTF-8")
        val json = getJson("https://kick.com/api/v2/channels/${slug.trim().lowercase()}/clips?sort=view&time=all&cursor=$c")
        val arr = json.optJSONArray("clips")
        val items = (0 until (arr?.length() ?: 0)).mapNotNull { i ->
            val o = arr?.optJSONObject(i) ?: return@mapNotNull null
            val url = o.firstString("clip_url", "video_url") ?: return@mapNotNull null
            ClipItem(
                id = o.optString("id"),
                title = o.optString("title"),
                url = url,
                thumbnail = o.firstString("thumbnail_url"),
                views = o.optInt("views", o.optInt("view_count", 0)),
                durationSec = o.optInt("duration", 0),
                creator = o.optJSONObject("creator")?.firstString("username"),
                createdAt = o.firstString("created_at"),
            )
        }
        val next = json.opt("nextCursor")?.takeIf { it != JSONObject.NULL }?.toString()?.takeIf { it.isNotBlank() && it != "null" }
        return items to next
    }

    /** Transmissões anteriores (VODs) do canal. */
    suspend fun videos(slug: String): List<VodItem> {
        val arr = parseArray(getBody("https://kick.com/api/v2/channels/${slug.trim().lowercase()}/videos"))
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val url = o.firstString("source") ?: return@mapNotNull null
            val video = o.optJSONObject("video")
            VodItem(
                id = video?.firstString("uuid") ?: o.optString("id"),
                title = o.firstString("session_title") ?: "Transmissão",
                url = url,
                thumbnail = o.optJSONObject("thumbnail")?.firstString("src"),
                views = o.optInt("views", video?.optInt("views", 0) ?: 0),
                durationMs = o.optLong("duration", 0L),
                category = o.optJSONArray("categories")?.optJSONObject(0)?.firstString("name"),
                createdAt = o.firstString("created_at"),
                isLive = o.optBoolean("is_live", false),
            )
        }
    }

    data class LivePage(val items: List<LiveItem>, val next: String?)

    /** Lives ordenadas por espectadores; filtra por idioma e/ou categoria e pagina por cursor. */
    suspend fun lives(lang: String?, categoryId: Int?, after: String? = null, limit: Int = 20): LivePage {
        val url = buildString {
            append("https://web.kick.com/api/v1/livestreams?sort=viewer_count_desc&limit=$limit")
            if (lang != null) append("&language=$lang")
            if (categoryId != null) append("&category_id=$categoryId")
            if (after != null) append("&after=$after")
        }
        val data = getJson(url).optJSONObject("data") ?: return LivePage(emptyList(), null)
        val arr = data.optJSONArray("livestreams")
        val items = (0 until (arr?.length() ?: 0)).mapNotNull { i ->
            val o = arr?.optJSONObject(i) ?: return@mapNotNull null
            val channel = o.optJSONObject("channel") ?: return@mapNotNull null
            LiveItem(
                slug = channel.optString("slug"),
                username = channel.optString("username").ifEmpty { channel.optString("slug") },
                title = o.optString("title"),
                viewers = o.optInt("viewer_count", 0),
                category = o.optJSONObject("category")?.optString("name"),
                thumbnail = o.optJSONObject("thumbnail")?.optString("src")?.takeIf { it.isNotEmpty() },
                avatar = channel.optString("profile_pic").takeIf { it.isNotEmpty() && it != "null" },
            )
        }
        val next = data.optJSONObject("pagination")?.optString("next_cursor")?.takeIf { it.isNotEmpty() && it != "null" }
        return LivePage(items, next)
    }

    suspend fun topCategories(): List<Category> {
        val arr = parseArray(getBody("https://kick.com/api/v1/categories/top"))
        return (0 until arr.length()).mapNotNull { i -> arr.optJSONObject(i)?.let(::parseCategory) }
    }

    private fun parseCategory(o: JSONObject) = Category(
        id = o.optInt("id", 0),
        slug = o.optString("slug"),
        name = o.optString("name"),
        viewers = o.optInt("viewers", 0),
        banner = o.optJSONObject("banner")?.optString("src")?.takeIf { it.isNotEmpty() },
    )

    suspend fun search(query: String): SearchResult {
        val q = java.net.URLEncoder.encode(query.trim(), "UTF-8")
        val json = getJson("https://kick.com/api/search?searched_word=$q")
        val channels = json.optJSONArray("channels")?.let { arr ->
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                SearchChannel(
                    slug = o.optString("slug"),
                    username = o.optJSONObject("user")?.optString("username").orEmpty().ifEmpty { o.optString("slug") },
                    followers = o.optInt("followers_count", o.optInt("followersCount", 0)),
                )
            }
        }.orEmpty()
        val categories = json.optJSONArray("categories")?.let { arr ->
            (0 until arr.length()).mapNotNull { i -> arr.optJSONObject(i)?.let(::parseCategory) }
        }.orEmpty()
        return SearchResult(channels, categories)
    }

    // ---- Conta (precisa de login) ----

    suspend fun me(): UserInfo {
        val json = getJson("https://kick.com/api/v1/user", auth = true)
        Log.d("NewKickApi", "user keys=${json.keys().asSequence().toList()}")
        val username = json.firstString("username", "streamer_name")
            ?: json.optJSONObject("user")?.firstString("username")
            ?: throw KickException("Perfil inválido.")
        return UserInfo(
            username = username,
            email = json.firstString("email"),
            avatar = json.firstString("profilepic", "profile_pic", "profile_picture")
                ?: json.optJSONObject("streamer_channel")?.optJSONObject("user")?.firstString("profilepic", "profile_pic"),
        )
    }

    private fun parseFollowed(json: JSONObject): List<FollowedChannel> {
        val arr = json.optJSONArray("channels") ?: json.optJSONArray("data") ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val channel = o.optJSONObject("channel")
            val slug = o.firstString("channel_slug", "slug") ?: channel?.firstString("slug") ?: return@mapNotNull null
            FollowedChannel(
                slug = slug,
                username = o.firstString("user_username", "username")
                    ?: channel?.optJSONObject("user")?.firstString("username")
                    ?: slug,
                avatar = o.firstString("profile_picture", "profile_pic", "profilepic")
                    ?: channel?.optJSONObject("user")?.firstString("profilepic", "profile_pic"),
                isLive = o.optBoolean("is_live", o.optBoolean("isLive", false)),
                viewers = o.optInt("viewer_count", o.optInt("viewers", 0)),
                title = o.firstString("session_title", "title") ?: "",
                category = o.firstString("category_name") ?: o.optJSONObject("category")?.firstString("name"),
            )
        }
    }

    /** Canais que o usuário segue (até ~100), com status ao vivo. */
    suspend fun followed(): List<FollowedChannel> {
        val all = mutableListOf<FollowedChannel>()
        var cursor: String? = "0"
        var pages = 0
        while (cursor != null && pages < 5) {
            val json = getJson("https://kick.com/api/v2/channels/followed?cursor=$cursor", auth = true)
            all += parseFollowed(json)
            cursor = (json.firstString("nextCursor", "next_cursor", "cursor"))?.takeIf { it != cursor && it != "0" }
            pages++
        }
        return all.distinctBy { it.slug }
    }

    suspend fun isFollowing(slug: String): Boolean {
        val json = getJson("https://kick.com/api/v2/channels/${slug.trim().lowercase()}/me", auth = true)
        val since = json.opt("following_since")
        return since != null && since != JSONObject.NULL && since.toString().isNotEmpty() && since.toString() != "null"
    }
}

open class KickException(message: String, val code: Int = 0) : Exception(message)

/** Sem login ou sessão expirada. */
class KickSessionExpired : KickException("Sessão expirada. Entre de novo.", 401)
