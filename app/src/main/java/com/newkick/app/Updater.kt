package com.newkick.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/** Atualização pelo GitHub: olha a última Release do repositório, baixa o APK e abre o instalador. */
object Updater {
    private const val REPO = "carlosguilhe/BetterKick"
    const val CHECK_EVERY_MS = 6L * 60 * 60 * 1000

    data class Release(val version: String, val notes: String, val apkUrl: String, val sizeBytes: Long)

    /** 0..1 enquanto baixa; null quando parado. */
    var progress by mutableStateOf<Float?>(null)
        private set
    var message by mutableStateOf<String?>(null)
        private set

    val busy: Boolean get() = progress != null

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    fun installedVersion(context: Context): String =
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "?"

    /** "1.10" > "1.9": compara número a número. */
    fun isNewer(remote: String, local: String): Boolean {
        fun parts(v: String) = v.trim().removePrefix("v").split('.', '-').map { it.filter(Char::isDigit).toIntOrNull() ?: 0 }
        val a = parts(remote)
        val b = parts(local)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    /** A Release mais nova que a instalada, ou null se já está em dia. Lança em erro de rede. */
    suspend fun check(context: Context): Release? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("https://api.github.com/repos/$REPO/releases/latest")
            .header("Accept", "application/vnd.github+json")
            .build()
        http.newCall(request).execute().use { response ->
            Prefs(context).lastUpdateCheck = System.currentTimeMillis()
            if (response.code == 404) return@use null // ainda não há Release publicada
            if (!response.isSuccessful) error("GitHub respondeu ${response.code}")
            val json = JSONObject(response.body?.string().orEmpty())
            val version = json.optString("tag_name").removePrefix("v")
            val assets = json.optJSONArray("assets") ?: return@use null
            var apk: JSONObject? = null
            for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                if (a.optString("name").endsWith(".apk")) {
                    apk = a
                    break
                }
            }
            if (apk == null || version.isEmpty() || !isNewer(version, installedVersion(context))) return@use null
            Release(version, json.optString("body"), apk.getString("browser_download_url"), apk.optLong("size"))
        }
    }

    /** Notas da Release da versão instalada ("v1.7"), ou null (sem rede, sem Release). */
    suspend fun notesFor(version: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url("https://api.github.com/repos/$REPO/releases/tags/v$version")
                .header("Accept", "application/vnd.github+json")
                .build()
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                JSONObject(response.body?.string().orEmpty()).optString("body").takeIf { it.isNotBlank() }
            }
        }.getOrNull()
    }

    /** Tira a marcação do Markdown para mostrar as notas como texto simples. */
    fun plainNotes(markdown: String): String = markdown
        .lineSequence()
        .map { it.trim() }
        .filterNot { it.startsWith("Atualiza por cima") || it.startsWith("# BetterKick") }
        .map { line ->
            val clean = line.replace("**", "").replace(Regex("\\[([^\\]]+)]\\([^)]*\\)"), "$1")
            when {
                clean.startsWith("## ") -> clean.removePrefix("## ").uppercase() // seção
                clean.startsWith("### ") -> clean.removePrefix("### ")
                clean.startsWith("- ") -> "• " + clean.removePrefix("- ")
                else -> clean
            }
        }
        .joinToString("\n")
        .replace(Regex("\n{3,}"), "\n\n")
        .trim()

    /** Baixa o APK e abre o instalador do Android (que confere se a assinatura é a mesma do app). */
    suspend fun downloadAndInstall(context: Context, release: Release) {
        if (busy) return
        val app = context.applicationContext
        if (!app.packageManager.canRequestPackageInstalls()) {
            message = "Permita instalar apps do BetterKick e toque em Atualizar de novo."
            app.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${app.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            return
        }
        progress = 0f
        message = null
        try {
            val file = withContext(Dispatchers.IO) { download(app, release) }
            install(app, file)
        } catch (e: Exception) {
            message = "Não consegui baixar a atualização. Tente de novo."
        } finally {
            progress = null
        }
    }

    private fun download(context: Context, release: Release): File {
        val dir = File(context.cacheDir, "updates").apply {
            mkdirs()
            listFiles()?.forEach { it.delete() }
        }
        val part = File(dir, "BetterKick-${release.version}.part")
        val target = File(dir, "BetterKick-${release.version}.apk")
        http.newCall(Request.Builder().url(release.apkUrl).build()).execute().use { response ->
            if (!response.isSuccessful) error("download ${response.code}")
            val body = response.body ?: error("sem corpo")
            val total = body.contentLength().takeIf { it > 0 } ?: release.sizeBytes
            part.outputStream().use { out ->
                body.byteStream().use { input ->
                    val buffer = ByteArray(64 * 1024)
                    var done = 0L
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        out.write(buffer, 0, n)
                        done += n
                        if (total > 0) progress = (done.toFloat() / total).coerceIn(0f, 1f)
                    }
                }
            }
        }
        if (release.sizeBytes > 0 && part.length() != release.sizeBytes) error("arquivo incompleto")
        // Confere que é mesmo o BetterKick antes de entregar ao instalador.
        val info = context.packageManager.getPackageArchiveInfo(part.path, 0)
        if (info?.packageName != context.packageName) error("pacote inesperado")
        part.renameTo(target)
        return target
    }

    private fun install(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", file)
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}

/** "Novidades da versão X": as notas da Release, em texto simples e com rolagem. */
@Composable
fun WhatsNewDialog(notes: String, onDone: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text(tr("Novidades da versão %s").format(Updater.installedVersion(context))) },
        text = {
            Column(Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState())) {
                androidx.compose.material3.Text(notes, fontSize = 13.sp)
            }
        },
        confirmButton = { TextButton(onClick = onDone) { Text("Entendi") } },
    )
}

/** Aviso de versão nova: baixa e instala ao tocar em Atualizar. */
@Composable
fun UpdateDialog(release: Updater.Release, onLater: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    AlertDialog(
        onDismissRequest = { if (!Updater.busy) onLater() },
        title = { Text(tr("Nova versão %s").format(release.version)) },
        text = {
            Column {
                Text(
                    release.notes.trim().ifEmpty { "Melhorias e correções." }.take(600),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Updater.progress?.let {
                    LinearProgressIndicator(progress = { it }, modifier = Modifier.fillMaxWidth().padding(top = 14.dp))
                }
                Updater.message?.let { Text(it, fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp)) }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !Updater.busy,
                onClick = { scope.launch { Updater.downloadAndInstall(context, release) } },
            ) { Text(if (Updater.busy) "Baixando…" else "Atualizar") }
        },
        dismissButton = { TextButton(enabled = !Updater.busy, onClick = onLater) { Text("Depois") } },
    )
}
