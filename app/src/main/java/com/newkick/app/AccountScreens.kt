package com.newkick.app

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import kotlinx.coroutines.delay

private const val CHROME_UA_FIX = "; wv"

/** WebView para páginas da Kick: o user-agent padrão ("; wv") é barrado pela proteção anti-bot. */
@SuppressLint("SetJavaScriptEnabled")
private fun kickWebView(
    context: android.content.Context,
    onTitle: (String) -> Unit = {},
    onBlockedHost: (String) -> Unit = {},
): WebView =
    WebView(context).apply {
        setBackgroundColor(0xFF0B0E0F.toInt())
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.userAgentString = settings.userAgentString.replace(CHROME_UA_FIX, "")
        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        webViewClient = object : WebViewClient() {
            // Fica dentro do site da Kick (login, páginas de conta).
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val host = request.url.host.orEmpty()
                if (host.endsWith("kick.com")) return false
                onBlockedHost(host)
                return true
            }

            override fun onPageFinished(view: WebView, url: String?) {
                onTitle(view.title.orEmpty())
            }
        }
    }

/**
 * Login na página oficial da Kick. O usuário digita as credenciais lá (com captcha/2FA, se houver);
 * o app só percebe que a sessão foi criada.
 */
@Composable
fun LoginScreen(onDone: () -> Unit, onCancel: () -> Unit) {
    val context = LocalContext.current
    var error by remember { mutableStateOf<String?>(null) }
    var checking by remember { mutableStateOf(false) }
    var checkTick by remember { mutableIntStateOf(0) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }

    BackHandler {
        val wv = webView
        if (wv != null && wv.canGoBack()) wv.goBack() else onCancel()
    }

    // Confirma o login pelo perfil: só vale se a API aceitar a sessão.
    suspend fun confirm(): Boolean = try {
        KickApi.me()
        true
    } catch (e: Exception) {
        error = if (e is KickSessionExpired) null else e.message
        false
    }

    LaunchedEffect(Unit) {
        var lastToken: String? = null
        while (true) {
            delay(1_500)
            val token = KickSession.token()
            if (token != null && token != lastToken) {
                lastToken = token
                if (confirm()) {
                    android.webkit.CookieManager.getInstance().flush()
                    onDone()
                    return@LaunchedEffect
                }
            }
        }
    }

    LaunchedEffect(checkTick) {
        if (checkTick == 0) return@LaunchedEffect
        checking = true
        error = null
        if (confirm()) {
            android.webkit.CookieManager.getInstance().flush()
            onDone()
        } else if (error == null) {
            error = "Ainda não encontrei sua sessão. Termine o login na página e tente de novo."
        }
        checking = false
    }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onCancel) { Icon(Icons.Default.Close, "Cancelar") }
            Text("Entrar na Kick", fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.weight(1f))
            TextButton(enabled = !checking, onClick = { checkTick++ }) { Text(if (checking) "Verificando..." else "Já entrei", color = KickGreen) }
        }
        Text(
            "Toque no ícone de pessoa (canto superior direito) para entrar com e-mail e senha. Eles são digitados na página oficial da Kick; o app não os vê.",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 16.dp)) }
        notice?.let { Text(it, color = KickGreen, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) }
        AndroidView(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            factory = { ctx ->
                kickWebView(ctx, onBlockedHost = { host ->
                    notice = if (host.contains("google") || host.contains("apple")) {
                        "Google e Apple não permitem login dentro de outros apps. Use e-mail e senha: se sua conta foi criada com o Google, toque em 'Esqueceu sua senha?' e crie uma senha com o mesmo e-mail."
                    } else {
                        null
                    }
                }).also {
                    webView = it
                    it.loadUrl("https://kick.com/")
                }
            },
            onRelease = { it.destroy() },
        )
    }
}

/** Páginas de configuração da conta (perfil, segurança...) na própria Kick. */
@Composable
fun SiteScreen(url: String, title: String, onBack: () -> Unit) {
    var webView by remember { mutableStateOf<WebView?>(null) }
    BackHandler {
        val wv = webView
        if (wv != null && wv.canGoBack()) wv.goBack() else onBack()
    }
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar") }
            Text(title, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        }
        AndroidView(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            factory = { ctx ->
                kickWebView(ctx).also {
                    webView = it
                    it.loadUrl(url)
                }
            },
            onRelease = {
                android.webkit.CookieManager.getInstance().flush()
                it.destroy()
            },
        )
    }
}

private val settingsLinks = listOf(
    "Perfil" to "https://kick.com/settings/profile",
    "Segurança e senha" to "https://kick.com/settings/security",
    "Notificações" to "https://kick.com/settings/notifications",
    "Preferências" to "https://kick.com/settings/preferences",
    "Conexões" to "https://kick.com/settings/connections",
)

/** Aba "Perfil": conta, atalhos para as configurações da Kick e ajustes do app. */
@Composable
fun ProfileTab(
    bottomInset: androidx.compose.ui.unit.Dp,
    scrollSignal: Int,
    onLogin: () -> Unit,
    onLoggedOut: () -> Unit,
    onOpenSite: (url: String, title: String) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenStats: () -> Unit = {},
) {
    val context = LocalContext.current
    val prefs = remember { Prefs(context) }
    val loggedIn = remember { KickSession.hasToken }
    var user by remember { mutableStateOf<UserInfo?>(null) }
    var loading by remember { mutableStateOf(loggedIn) }
    var error by remember { mutableStateOf<String?>(null) }
    var expired by remember { mutableStateOf(false) }
    var notifyFollowed by remember { mutableStateOf(prefs.notifyFollowed) }
    var confirmLogout by remember { mutableStateOf(false) }
    val scroll = rememberScrollState()
    LaunchedEffect(scrollSignal) { if (scrollSignal > 0) scroll.animateScrollTo(0) }

    val notificationPermission = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
    ) {}

    LaunchedEffect(loggedIn) {
        if (!loggedIn) return@LaunchedEffect
        try {
            user = KickApi.me()
            // Uma vez só: pede permissão para avisar quando quem você segue entrar ao vivo.
            if (android.os.Build.VERSION.SDK_INT >= 33 && !prefs.askedNotifications &&
                androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) !=
                android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                prefs.askedNotifications = true
                notificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        } catch (e: KickSessionExpired) {
            expired = true
        } catch (e: Exception) {
            error = e.message ?: "Falha ao carregar a conta."
        } finally {
            loading = false
        }
    }

    Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(scroll)) {
        Text("Perfil", fontWeight = FontWeight.Bold, fontSize = 22.sp, modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp))

        when {
            !loggedIn -> Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Entre na sua conta da Kick para ver quem você segue, seguir canais e conversar no chat.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(onClick = onLogin) { Text("Entrar") }
            }

            loading -> Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                KLoader()
            }

            expired -> Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Sua sessão expirou.")
                Button(onClick = {
                    KickSession.logout()
                    onLogin()
                }) { Text("Entrar de novo") }
            }

            else -> {
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp)) }
                user?.let { u ->
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        AsyncImage(
                            model = u.avatar,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(64.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
                        )
                        Column(Modifier.padding(start = 16.dp)) {
                            Text(u.username, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                            u.email?.let { Text(it, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        }
                    }
                }

                SectionLabel("CONFIGURAÇÕES DA CONTA")
                settingsLinks.forEach { (label, url) ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onOpenSite(url, label) }.padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(label, modifier = Modifier.weight(1f))
                        Text("›", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 20.sp)
                    }
                }
            }
        }

        SectionLabel("NO APP")
        listOf("Histórico de lives" to onOpenHistory, "Tempo assistido" to onOpenStats, "Configurações do app" to onOpenSettings).forEach { (label, action) ->
            Row(
                Modifier.fillMaxWidth().clickable(onClick = action).padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(label, modifier = Modifier.weight(1f))
                Text("›", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 20.sp)
            }
        }
        if (loggedIn) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Avisar quando quem eu sigo entrar ao vivo")
                    Text("Checa a cada ~15 minutos", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = notifyFollowed, onCheckedChange = {
                    notifyFollowed = it
                    prefs.notifyFollowed = it
                })
            }
        }
        SpeedTestRow(Modifier.padding(horizontal = 8.dp))
        Text(
            tr("BetterKick %s • cliente não oficial da Kick").format(Updater.installedVersion(context)),
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp, top = 8.dp),
        )

        if (loggedIn && !expired) {
            TextButton(onClick = { confirmLogout = true }, modifier = Modifier.padding(start = 8.dp, top = 8.dp)) {
                Text("Sair da conta", color = MaterialTheme.colorScheme.error)
            }
        }
        Box(Modifier.size(bottomInset))
    }

    if (confirmLogout) {
        AlertDialog(
            onDismissRequest = { confirmLogout = false },
            title = { Text("Sair da conta?") },
            text = { Text("Você vai precisar entrar de novo para ver quem você segue.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmLogout = false
                    KickSession.logout()
                    prefs.notifiedLive = emptySet()
                    prefs.followedBaseline = false
                    onLoggedOut()
                }) { Text("Sair", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmLogout = false }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}