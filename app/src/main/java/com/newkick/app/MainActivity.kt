package com.newkick.app

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle

class MainActivity : ComponentActivity() {
    /** Canal pedido por um link kick.com/<canal>; consumido pela UI. */
    private val linkedSlug = mutableStateOf<String?>(null)

    /** Aba pedida por um atalho do ícone (nome do MainTab). */
    private val linkedTab = mutableStateOf<String?>(null)
    private var pipActive = false

    override fun onStart() {
        super.onStart()
        PlayerSession.setVideoEnabled(true)
    }

    override fun onStop() {
        super.onStop()
        // PiP fechado pelo X: a live para (o Android chama onStop sem avisar a saída do PiP).
        if (pipActive) {
            pipActive = false
            PlayerSession.release()
            return
        }
        // Fora da tela (e sem PiP): só áudio.
        if (!isInPictureInPictureMode) PlayerSession.setVideoEnabled(false)
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: android.content.res.Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        if (isInPictureInPictureMode) {
            pipActive = true
        } else if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            // Voltou para o app (tocou na janela do PiP): não foi fechado.
            pipActive = false
        } else {
            // Saiu do PiP sem voltar à tela: foi fechado pelo X.
            pipActive = false
            PlayerSession.release()
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (PlayerState.pipAllowed) enterPip(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        KickApi.init(this)
        CrashLogger.install(this)
        ThemeState.mode = Prefs(this).themeMode
        linkedSlug.value = slugFrom(intent)
        linkedTab.value = tabFrom(intent)
        FavoritesWorker.schedule(this)
        if (intent?.action == ACTION_CHECK_FAVORITES) FavoritesWorker.checkNow(this)
        setContent {
            NewKickTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    App(linkedSlug, linkedTab)
                }
            }
        }
    }

    // singleTask: reabrir o app (ou um link) com o PiP ativo reaproveita esta instância.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.action == ACTION_CHECK_FAVORITES) FavoritesWorker.checkNow(this)
        slugFrom(intent)?.let { linkedSlug.value = it }
        tabFrom(intent)?.let { linkedTab.value = it }
    }

    private companion object {
        const val ACTION_CHECK_FAVORITES = "com.newkick.app.CHECK_FAVORITES"
        const val ACTION_OPEN_TAB = "com.newkick.app.OPEN_TAB"
    }

    private fun slugFrom(intent: Intent?): String? =
        intent?.data?.takeIf { it.host?.endsWith("kick.com") == true }?.pathSegments?.firstOrNull()

    private fun tabFrom(intent: Intent?): String? =
        if (intent?.action == ACTION_OPEN_TAB) intent.getStringExtra("tab")?.takeIf { t -> MainTab.entries.any { it.name == t } } else null
}

/** Telas de vídeo ficam sempre no tema escuro, mesmo com o app em tema claro. */
@Composable
private fun DarkScreen(content: @Composable () -> Unit) {
    NewKickTheme(forceDark = true) {
        Box(Modifier.fillMaxSize().background(Color(0xFF0B0E0F))) { content() }
    }
}

@Composable
private fun App(linkedSlug: MutableState<String?>, linkedTab: MutableState<String?>) {
    val context = LocalContext.current
    val prefs = remember { Prefs(context) }

    // Rotas: "home" (abas), "player:<canal>", "channel:<canal>", "vod:<url>|<título>", "login",
    // "site:<título>|<url>", "settings", "history", "onboarding".
    var route by rememberSaveable {
        mutableStateOf(
            when {
                linkedSlug.value != null -> "player:${linkedSlug.value}"
                !prefs.onboarded -> "onboarding"
                else -> "home"
            },
        )
    }
    var vodBack by rememberSaveable { mutableStateOf("home") }
    var tabName by rememberSaveable { mutableStateOf(MainTab.HOME.name) }
    val tab = MainTab.valueOf(tabName)
    var crashReport by remember { mutableStateOf(CrashLogger.pending(context)) }

    // Versão nova no GitHub: confere a cada 6 h, sem travar a abertura do app.
    var update by remember { mutableStateOf<Updater.Release?>(null) }
    LaunchedEffect(Unit) {
        if (System.currentTimeMillis() - prefs.lastUpdateCheck > Updater.CHECK_EVERY_MS) {
            val found = runCatching { Updater.check(context) }.getOrNull()
            if (found != null && found.version != prefs.skippedUpdate) update = found
        }
    }

    LaunchedEffect(linkedTab.value) {
        linkedTab.value?.let {
            tabName = it
            route = "home"
            linkedTab.value = null
        }
    }
    LaunchedEffect(linkedSlug.value) {
        linkedSlug.value?.let {
            route = "player:$it"
            linkedSlug.value = null
        }
    }

    val onPlayer = route.startsWith("player:")
    val onVod = route.startsWith("vod:")
    val navInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    // Ícones da barra de status: claros no escuro, escuros no claro (vídeo é sempre escuro).
    val dark = LocalIsDark.current || onPlayer || onVod
    val view = LocalView.current
    SideEffect {
        (view.context as? Activity)?.window?.let { w ->
            val controller = WindowCompat.getInsetsController(w, view)
            controller.isAppearanceLightStatusBars = !dark
            controller.isAppearanceLightNavigationBars = !dark
        }
    }

    Box(Modifier.fillMaxSize()) {
        when {
            onPlayer -> DarkScreen {
                PlayerScreen(
                    slug = route.removePrefix("player:"),
                    // Voltar: a live segue tocando no mini player.
                    onMinimize = { route = "home" },
                    onClose = {
                        PlayerSession.release()
                        route = "home"
                    },
                    // Canal offline: mostra a página dele (clipes e transmissões anteriores).
                    onOffline = { route = "channel:$it" },
                )
            }
            onVod -> DarkScreen {
                val (url, title) = route.removePrefix("vod:").split("|", limit = 2).let { it[0] to it.getOrElse(1) { "" } }
                VodPlayerScreen(url = url, title = title, onBack = { route = vodBack })
            }
            route.startsWith("channel:") -> {
                val slug = route.removePrefix("channel:")
                ChannelScreen(
                    slug = slug,
                    onBack = { route = "home" },
                    onWatchLive = { route = "player:$slug" },
                    onPlay = { url, title ->
                        vodBack = "channel:$slug"
                        route = "vod:$url|$title"
                    },
                )
            }
            route == "settings" -> SettingsScreen(onBack = {
                tabName = MainTab.PROFILE.name
                route = "home"
            })
            route == "history" -> HistoryScreen(
                onBack = {
                    tabName = MainTab.PROFILE.name
                    route = "home"
                },
                onOpen = { route = "player:$it" },
            )
            route == "onboarding" -> OnboardingScreen(onDone = {
                prefs.onboarded = true
                route = "home"
            })
            route == "login" -> LoginScreen(
                onDone = {
                    tabName = MainTab.PROFILE.name
                    route = "home"
                },
                onCancel = { route = "home" },
            )
            route.startsWith("site:") -> {
                val (title, url) = route.removePrefix("site:").split("|", limit = 2)
                SiteScreen(url = url, title = title, onBack = { route = "home" })
            }
            else -> MainShell(
                tab = tab,
                onTab = { tabName = it.name },
                onOpen = { route = "player:$it" },
                onOpenChannel = { route = "channel:$it" },
                onLogin = { route = "login" },
                onOpenSite = { url, title -> route = "site:$title|$url" },
                onOpenSettings = { route = "settings" },
                onOpenHistory = { route = "history" },
                onLoggedOut = { tabName = MainTab.HOME.name },
            )
        }
        // Só aparece na tela inicial, para não interromper uma live.
        update?.takeIf { route == "home" }?.let { release ->
            UpdateDialog(release, onLater = {
                prefs.skippedUpdate = release.version
                update = null
            })
        }
        crashReport?.let { report ->
            AlertDialog(
                onDismissRequest = {},
                title = { Text("O app fechou inesperadamente") },
                text = { Text("Da última vez algo deu errado. Quer compartilhar o relatório para eu corrigir?") },
                confirmButton = {
                    TextButton(onClick = {
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, report)
                        }
                        context.startActivity(Intent.createChooser(send, "Compartilhar relatório"))
                        CrashLogger.clear(context)
                        crashReport = null
                    }) { Text("Compartilhar") }
                },
                dismissButton = {
                    TextButton(onClick = {
                        CrashLogger.clear(context)
                        crashReport = null
                    }) { Text("Descartar") }
                },
            )
        }
        // Mini player: aparece acima das telas (menos no player cheio) enquanto há uma live ativa.
        if (!onPlayer && !onVod && PlayerSession.channel != null) {
            // Acima da barra flutuante, que só existe na tela inicial.
            val inset = if (route == "home") navInset + 92.dp else navInset + 20.dp
            MiniPlayerOverlay(bottomInset = inset, onExpand = { route = "player:$it" })
        }
    }
}

fun Context.findActivity(): ComponentActivity? {
    var ctx: Context = this
    while (ctx is ContextWrapper) {
        if (ctx is ComponentActivity) return ctx
        ctx = ctx.baseContext
    }
    return null
}
