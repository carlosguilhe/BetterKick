package com.newkick.app

import android.content.Context
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import kotlinx.coroutines.launch

// ───────────────────────────── Configurações ─────────────────────────────

@Composable
private fun SettingsSection(text: String) {
    Text(
        text,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 4.dp),
    )
}

@Composable
private fun <T> ChoiceRow(title: String, choices: List<Pair<String, T>>, selected: T, onSelect: (T) -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
        Text(title)
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            choices.forEach { (label, value) ->
                FilterChip(selected = selected == value, onClick = { onSelect(value) }, label = { Text(label) })
            }
        }
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String? = null, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    val haptics = rememberHaptics()
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title)
            subtitle?.let { Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        Switch(checked = checked, onCheckedChange = { haptics.tick(); onChange(it) }, enabled = enabled)
    }
}

@Composable
private fun ActionRow(title: String, onClick: () -> Unit) {
    Text(title, modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp))
}

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { Prefs(context) }
    BackHandler(onBack = onBack)

    var quality by remember { mutableIntStateOf(prefs.quality) }
    var latency by remember { mutableIntStateOf(prefs.latencyMs) }
    var gestures by remember { mutableStateOf(prefs.gestures) }
    var overlay by remember { mutableStateOf(prefs.chatOverlay) }
    var stats by remember { mutableStateOf(prefs.showStats) }
    var lang by remember { mutableStateOf(prefs.lang) }
    var notifyFav by remember { mutableStateOf(prefs.notifyFavorites) }
    var notifyFollowed by remember { mutableStateOf(prefs.notifyFollowed) }
    var crash by remember { mutableStateOf<String?>(null) }
    var update by remember { mutableStateOf<Updater.Release?>(null) }
    var checking by remember { mutableStateOf(false) }
    var vibration by remember { mutableStateOf(prefs.haptics) }
    var dataSaver by remember { mutableStateOf(prefs.dataSaver) }
    var uiLang by remember { mutableStateOf(prefs.uiLang) }
    val haptics = rememberHaptics()

    // Backup: um arquivo JSON com favoritos, histórico e preferências (o login não entra).
    val exportLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri != null) {
            val ok = runCatching {
                context.contentResolver.openOutputStream(uri)?.use { it.write(prefs.exportJson().toByteArray()) } != null
            }.getOrDefault(false)
            Toast.makeText(context, if (ok) "Backup salvo." else "Não consegui salvar o backup.", Toast.LENGTH_SHORT).show()
        }
    }
    val importLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            val text = runCatching { context.contentResolver.openInputStream(uri)?.use { String(it.readBytes()) } }.getOrNull()
            if (text != null && prefs.importJson(text)) {
                // Aplica na hora o que aparece nesta tela.
                ThemeState.mode = prefs.themeMode
                ThemeState.dynamic = prefs.dynamicColors
                quality = prefs.quality
                latency = prefs.latencyMs
                gestures = prefs.gestures
                overlay = prefs.chatOverlay
                stats = prefs.showStats
                lang = prefs.lang
                notifyFav = prefs.notifyFavorites
                notifyFollowed = prefs.notifyFollowed
                vibration = prefs.haptics
                dataSaver = prefs.dataSaver
                Toast.makeText(context, "Dados restaurados.", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Esse arquivo não é um backup do BetterKick.", Toast.LENGTH_SHORT).show()
            }
        }
    }
    val scope = rememberCoroutineScope()
    val loggedIn = remember { KickSession.hasToken }

    fun toast(message: String) = Toast.makeText(context, message, Toast.LENGTH_SHORT).show()

    Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState())) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar") }
            Text("Configurações", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        }

        SettingsSection("REPRODUÇÃO")
        ChoiceRow("Qualidade padrão", listOf("Automática" to QUALITY_AUTO, "Máxima" to QUALITY_MAX), quality) {
            quality = it
            prefs.quality = it
        }
        ChoiceRow("Distância do ao vivo", listOf("Baixa (4 s)" to 4_000, "Equilibrada (10 s)" to 10_000, "Estável (20 s)" to 20_000), latency) {
            latency = it
            prefs.latencyMs = it
        }
        SwitchRow("Economia de dados", "No 4G/5G, o modo automático não passa de 480p", dataSaver) {
            dataSaver = it
            prefs.dataSaver = it
        }
        SwitchRow("Gestos de brilho e volume", "Arraste na tela cheia", gestures) {
            gestures = it
            prefs.gestures = it
        }
        SwitchRow("Chat sobre o vídeo", "Em paisagem, translúcido em vez de ao lado", overlay) {
            overlay = it
            prefs.chatOverlay = it
        }
        SwitchRow("Estatísticas de reprodução", "Mostra buffer, rede e quadros perdidos", stats) {
            stats = it
            prefs.showStats = it
        }

        SettingsSection("APARÊNCIA")
        ChoiceRow("Tema", listOf("Escuro" to "dark", "Claro" to "light", "Sistema" to "system"), ThemeState.mode) {
            haptics.tick()
            ThemeState.mode = it
            prefs.themeMode = it
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            SwitchRow("Cores do sistema", "Fundos com as cores do papel de parede (Material You)", ThemeState.dynamic) {
                haptics.tick()
                ThemeState.dynamic = it
                prefs.dynamicColors = it
            }
        }
        SwitchRow("Vibração", "Toque leve ao segurar, trocar de aba e confirmar", vibration) {
            vibration = it
            prefs.haptics = it
            haptics.tick()
        }
        ChoiceRow(
            "Idioma do app",
            listOf("Sistema" to "system", "Português" to "pt", "English" to "en", "Español" to "es"),
            uiLang,
        ) {
            haptics.tick()
            uiLang = it
            prefs.uiLang = it
            Strings.apply(it)
        }
        ChoiceRow("Idioma das listas", listOf("PT" to "pt", "EN" to "en", "ES" to "es", "Todos" to "all"), lang) {
            lang = it
            prefs.lang = it
        }

        SettingsSection("AVISOS")
        SwitchRow("Favoritos (estrela) ao vivo", "Avisa quando um favorito entrar ao vivo", notifyFav) {
            notifyFav = it
            prefs.notifyFavorites = it
        }
        SwitchRow("Quem eu sigo ao vivo", if (loggedIn) "Canais que você segue na Kick" else "Entre na conta para usar", notifyFollowed, enabled = loggedIn) {
            notifyFollowed = it
            prefs.notifyFollowed = it
        }

        SettingsSection("DADOS")
        ActionRow("Salvar backup dos meus dados") { exportLauncher.launch("BetterKick-backup.json") }
        ActionRow("Restaurar backup") { importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }
        ActionRow("Limpar histórico de lives") {
            prefs.clearHistory()
            toast("Histórico limpo.")
        }
        ActionRow("Limpar buscas recentes") {
            prefs.clearSearchHistory()
            toast("Buscas limpas.")
        }
        ActionRow("Remover todos os favoritos") {
            prefs.clearFavorites()
            toast("Favoritos removidos.")
        }
        ActionRow("Limpar cache de listas") {
            KickApi.clearCache()
            toast("Cache limpo.")
        }

        SettingsSection("ATUALIZAÇÕES")
        ActionRow(if (checking) "Procurando atualização…" else "Procurar atualização") {
            if (!checking) scope.launch {
                checking = true
                val found = runCatching { Updater.check(context) }
                checking = false
                val release = found.getOrNull()
                when {
                    found.isFailure -> toast("Não consegui consultar o GitHub.")
                    release == null -> toast("Você está na versão mais recente.")
                    else -> update = release
                }
            }
        }

        SettingsSection("SUPORTE")
        ActionRow("Relatório da última falha") {
            val report = CrashLogger.pending(context)
            if (report == null) toast("Nenhuma falha registrada.") else crash = report
        }
        Text(
            tr("BetterKick %s • cliente não oficial da Kick\nUsa endereços não oficiais do site, que podem mudar sem aviso.").format(Updater.installedVersion(context)),
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(16.dp),
        )
        Spacer(Modifier.height(24.dp).navigationBarsPadding())
    }

    update?.let { release -> UpdateDialog(release, onLater = { update = null }) }

    crash?.let { report ->
        AlertDialog(
            onDismissRequest = { crash = null },
            title = { Text("Última falha") },
            text = { Text(report.take(900), fontSize = 11.sp) },
            confirmButton = {
                TextButton(onClick = {
                    val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(android.content.Intent.EXTRA_TEXT, report)
                    }
                    context.startActivity(android.content.Intent.createChooser(send, "Compartilhar relatório"))
                    crash = null
                }) { Text("Compartilhar") }
            },
            dismissButton = {
                TextButton(onClick = {
                    CrashLogger.clear(context)
                    crash = null
                }) { Text("Apagar") }
            },
        )
    }
}

// ───────────────────────────── Histórico ─────────────────────────────

private fun formatAgo(thenMs: Long): String {
    val minutes = ((System.currentTimeMillis() - thenMs) / 60_000).coerceAtLeast(0)
    return when {
        minutes < 1 -> "agora há pouco"
        minutes < 60 -> tr("há %d min").format(minutes)
        minutes < 60 * 24 -> tr("há %d h").format(minutes / 60)
        else -> tr("há %d d").format(minutes / (60 * 24))
    }
}

@Composable
fun HistoryScreen(onBack: () -> Unit, onOpen: (String) -> Unit) {
    val context = LocalContext.current
    val prefs = remember { Prefs(context) }
    var entries by remember { mutableStateOf(prefs.history()) }
    BackHandler(onBack = onBack)

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar") }
            Text("Histórico de lives", fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.weight(1f))
            if (entries.isNotEmpty()) TextButton(onClick = {
                prefs.clearHistory()
                entries = emptyList()
            }) { Text("Limpar", color = KickGreen) }
        }
        if (entries.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text("Nada por aqui ainda. As lives que você assistir aparecem nesta lista.", color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            }
        } else {
            LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
                items(entries, key = { it.slug }) { e ->
                    Column(Modifier.fillMaxWidth().clickable { onOpen(e.slug) }.padding(vertical = 12.dp)) {
                        Text(e.username.ifBlank { e.slug }, fontWeight = FontWeight.Bold)
                        Text(
                            listOfNotNull(e.category, formatAgo(e.at)).joinToString(" • "),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

// ───────────────────────────── Boas-vindas ─────────────────────────────

private data class OnboardingPage(val title: String, val text: String)

@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    val pages = remember {
        listOf(
            OnboardingPage("Bem-vindo ao BetterKick", "Um jeito melhor de assistir às lives da Kick no celular: qualidade estável, mini player e áudio em segundo plano."),
            OnboardingPage("Assista do seu jeito", "Voltar vira mini player. Segure e arraste o vídeo para baixo, arraste para fora para fechar, e use picture-in-picture ou só áudio."),
            OnboardingPage("Entre e acompanhe quem você segue", "Faça login pela página oficial da Kick para ver quem está ao vivo e receber avisos. O app nunca vê sua senha."),
        )
    }
    val pager = rememberPagerState { pages.size }
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onDone) { Text("Pular", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        HorizontalPager(state = pager, modifier = Modifier.weight(1f)) { index ->
            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Image(
                    painter = painterResource(if (LocalIsDark.current) R.drawable.ic_logo_bk else R.drawable.ic_logo_bk_dark),
                    contentDescription = null,
                    modifier = Modifier.size(width = 150.dp, height = 97.dp),
                )
                Text(pages[index].title, fontWeight = FontWeight.Bold, fontSize = 22.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 32.dp))
                Text(pages[index].text, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 12.dp))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 16.dp)) {
            repeat(pages.size) { i ->
                Box(
                    Modifier.size(if (pager.currentPage == i) 22.dp else 8.dp, 8.dp).clip(CircleShape)
                        .background(if (pager.currentPage == i) KickGreen else MaterialTheme.colorScheme.surfaceVariant),
                )
            }
        }
        Button(
            onClick = {
                if (pager.currentPage == pages.lastIndex) onDone() else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(if (pager.currentPage == pages.lastIndex) "Começar" else "Continuar") }
    }
}

// ───────────────────────────── Página do canal ─────────────────────────────

private fun formatDuration(totalSeconds: Long): String {
    val s = totalSeconds.coerceAtLeast(0)
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
}

private fun formatDate(iso: String?): String? {
    if (iso == null || iso.length < 10) return null
    val d = iso.substring(0, 10).split("-")
    return if (d.size == 3) "${d[2]}/${d[1]}/${d[0]}" else null
}

@Composable
private fun MediaCard(thumbnail: String?, title: String, subtitle: String, duration: String?, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick)) {
        Box {
            AsyncImage(
                model = thumbnail,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
            )
            duration?.let {
                Text(
                    it,
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp).clip(RoundedCornerShape(6.dp)).background(Color(0xCC000000)).padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }
        Text(title, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
        Text(subtitle, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Página do canal: perfil, e abas de clipes e transmissões anteriores (VODs). */
@Composable
fun ChannelScreen(slug: String, onBack: () -> Unit, onWatchLive: () -> Unit, onPlay: (url: String, title: String) -> Unit) {
    BackHandler(onBack = onBack)
    var profile by remember { mutableStateOf<ChannelProfile?>(null) }
    var tab by remember { mutableIntStateOf(0) } // 0 = clipes, 1 = transmissões
    var clips by remember { mutableStateOf<List<ClipItem>>(emptyList()) }
    var clipCursor by remember { mutableStateOf<String?>(null) }
    var vods by remember { mutableStateOf<List<VodItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var loadingMore by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(slug) {
        loading = true
        error = null
        try {
            profile = KickApi.channelProfile(slug)
            val (c, next) = KickApi.clips(slug)
            clips = c
            clipCursor = next
            vods = runCatching { KickApi.videos(slug) }.getOrDefault(emptyList()).filter { !it.isLive }
        } catch (e: Exception) {
            error = e.message ?: "Falha ao carregar o canal."
        } finally {
            loading = false
        }
    }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar") }
            Text(profile?.username ?: slug, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        }
        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { KLoader(size = 64.dp) }
            error != null -> Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Text(error!!, color = MaterialTheme.colorScheme.error)
            }
            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(150.dp),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                profile?.let { p ->
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                AsyncImage(
                                    model = p.avatar,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.size(72.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
                                )
                                Column(Modifier.padding(start = 14.dp)) {
                                    Text(p.username, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                                    Text(tr("%s seguidores").format(formatViewers(p.followers)), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                                }
                            }
                            p.bio?.let { Text(it, fontSize = 13.sp, maxLines = 4, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 10.dp)) }
                            if (p.isLive) Button(onClick = onWatchLive, modifier = Modifier.padding(top = 12.dp)) { Text("Assistir ao vivo agora") }
                            else Text("Offline no momento", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, modifier = Modifier.padding(top = 10.dp))
                            Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(selected = tab == 0, onClick = { tab = 0 }, label = { Text(tr("Clipes (%s)").format("${clips.size}${if (clipCursor != null) "+" else ""}")) })
                                FilterChip(selected = tab == 1, onClick = { tab = 1 }, label = { Text(tr("Transmissões (%d)").format(vods.size)) })
                            }
                        }
                    }
                }
                if (tab == 0) {
                    if (clips.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) { Text("Sem clipes.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    gridItems(clips, key = { it.id }) { c ->
                        MediaCard(
                            c.thumbnail, c.title.ifBlank { "Clipe" },
                            listOfNotNull(c.creator?.let { tr("por %s").format(it) }, "${formatViewers(c.views)} views").joinToString(" • "),
                            formatDuration(c.durationSec.toLong()),
                        ) { onPlay(c.url, c.title.ifBlank { tr("Clipe de %s").format(profile?.username ?: slug) }) }
                    }
                    if (clipCursor != null) item(span = { GridItemSpan(maxLineSpan) }) {
                        TextButton(
                            enabled = !loadingMore,
                            onClick = {
                                loadingMore = true
                                scope.launch {
                                    runCatching { KickApi.clips(slug, clipCursor) }.onSuccess { (more, next) ->
                                        clips = (clips + more).distinctBy { it.id }
                                        clipCursor = next
                                    }
                                    loadingMore = false
                                }
                            },
                        ) { Text(if (loadingMore) "Carregando..." else "Carregar mais clipes", color = KickGreen) }
                    }
                } else {
                    if (vods.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) { Text("Sem transmissões anteriores salvas.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    gridItems(vods, key = { it.id }) { v ->
                        MediaCard(
                            v.thumbnail, v.title,
                            listOfNotNull(v.category, formatDate(v.createdAt), "${formatViewers(v.views)} views").joinToString(" • "),
                            v.durationMs.takeIf { it > 0 }?.let { formatDuration(it / 1000) },
                        ) { onPlay(v.url, v.title) }
                    }
                }
            }
        }
    }
}

// ───────────────────────────── Player de clipes e VODs ─────────────────────────────

/** Player de vídeo gravado (clipe ou VOD): com barra de progresso e tela cheia. */
@OptIn(UnstableApi::class)
@Composable
fun VodPlayerScreen(url: String, title: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val activity = context.findActivity()
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val view = LocalView.current

    // Uma live em segundo plano seria um segundo áudio ao mesmo tempo.
    LaunchedEffect(Unit) { PlayerSession.release() }

    val prefs = remember { Prefs(context) }
    val resumeAt = remember(url) { prefs.vodPosition(url) }
    val player = remember {
        ExoPlayer.Builder(context)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(), true)
            .setHandleAudioBecomingNoisy(true)
            .build()
            .apply {
                // Continua de onde você parou (se já tinha assistido um pedaço).
                setMediaItem(MediaItem.fromUri(url), resumeAt)
                playWhenReady = true
                prepare()
            }
    }
    var speed by remember { mutableStateOf(1f) }
    var speedMenu by remember { mutableStateOf(false) }
    // Pulo de 10 s por duplo toque: true = para frente. O contador reinicia a animação a cada toque.
    var seekFlash by remember { mutableStateOf<Pair<Boolean, Int>?>(null) }
    var seekCount by remember { mutableIntStateOf(0) }
    LaunchedEffect(seekFlash) {
        if (seekFlash != null) {
            kotlinx.coroutines.delay(650)
            seekFlash = null
        }
    }
    LaunchedEffect(resumeAt) {
        if (resumeAt > 0) Toast.makeText(context, tr("Continuando de %s").format(formatDuration(resumeAt / 1000)), Toast.LENGTH_SHORT).show()
    }
    fun savePosition() {
        val duration = player.duration
        val position = player.currentPosition
        if (duration == C.TIME_UNSET || duration <= 0) return
        // Quase no fim (ou começo): não precisa lembrar.
        if (position < 15_000 || position > duration - 20_000) prefs.clearVodPosition(url) else prefs.setVodPosition(url, position)
    }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(5_000)
            savePosition()
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            savePosition()
            player.release()
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }
    DisposableEffect(landscape) {
        val controller = activity?.window?.let { WindowCompat.getInsetsController(it, view) }
        if (landscape) {
            controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller?.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            controller?.show(WindowInsetsCompat.Type.systemBars())
        }
        onDispose { controller?.show(WindowInsetsCompat.Type.systemBars()) }
    }
    BackHandler {
        if (landscape) activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED else onBack()
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            modifier = Modifier.fillMaxSize().then(if (landscape) Modifier else Modifier.statusBarsPadding()),
            factory = { ctx ->
                SeekPlayerView(ctx) { forward ->
                    val delta = if (forward) 10_000L else -10_000L
                    player.seekTo((player.currentPosition + delta).coerceIn(0L, player.duration.takeIf { it != C.TIME_UNSET } ?: Long.MAX_VALUE))
                    seekCount++
                    seekFlash = forward to seekCount
                }.apply {
                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    this.player = player
                    useController = true
                    keepScreenOn = true
                    setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                    setFullscreenButtonClickListener { fullscreen ->
                        activity?.requestedOrientation =
                            if (fullscreen) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                    }
                }
            },
            onRelease = { it.player = null },
        )
        Row(
            Modifier.align(Alignment.TopStart).then(if (landscape) Modifier else Modifier.statusBarsPadding()).fillMaxWidth().padding(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { if (landscape) activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED else onBack() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar", tint = Color.White)
            }
            Text(title, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Box {
                TextButton(onClick = { speedMenu = true }) {
                    Text(if (speed == 1f) "1x" else "${speed.toString().removeSuffix(".0")}x", color = Color.White, fontWeight = FontWeight.Bold)
                }
                androidx.compose.material3.DropdownMenu(expanded = speedMenu, onDismissRequest = { speedMenu = false }) {
                    listOf(0.75f, 1f, 1.25f, 1.5f, 2f).forEach { s ->
                        androidx.compose.material3.DropdownMenuItem(
                            text = { Text(if (s == speed) "✓ ${s}x" else "${s}x") },
                            onClick = {
                                speed = s
                                player.setPlaybackSpeed(s)
                                speedMenu = false
                            },
                        )
                    }
                }
            }
        }
        // Feedback do duplo toque: "+10 s" à direita, "−10 s" à esquerda.
        seekFlash?.let { (forward, _) ->
            Text(
                if (forward) "+10 s" else "−10 s",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                modifier = Modifier
                    .align(if (forward) Alignment.CenterEnd else Alignment.CenterStart)
                    .padding(horizontal = 48.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0x99000000))
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            )
        }
    }
}

/** PlayerView que entende duplo toque (metade esquerda volta 10 s, direita avança 10 s). */
@OptIn(UnstableApi::class)
private class SeekPlayerView(context: Context, private val onSeek: (forward: Boolean) -> Unit) : PlayerView(context) {
    private val detector = android.view.GestureDetector(
        context,
        object : android.view.GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: android.view.MotionEvent) = true

            // Toque simples continua mostrando/escondendo os controles.
            override fun onSingleTapConfirmed(e: android.view.MotionEvent): Boolean = performClick()

            override fun onDoubleTap(e: android.view.MotionEvent): Boolean {
                onSeek(e.x > width / 2f)
                return true
            }
        },
    )

    override fun onTouchEvent(event: android.view.MotionEvent): Boolean = detector.onTouchEvent(event) || super.onTouchEvent(event)
}
