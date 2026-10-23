package com.newkick.app

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.launch

/** "all" = sem filtro de idioma. */
private val languages = listOf("pt" to "PT", "en" to "EN", "es" to "ES", "all" to "Todos")

fun formatViewers(n: Int): String = when {
    n >= 1_000_000 -> "%.1fM".format(n / 1_000_000.0)
    n >= 1_000 -> "%.1fK".format(n / 1_000.0)
    else -> n.toString()
}

/** Fonte pixel, no mesmo estilo do K da logo. */
val PixelFont = FontFamily(Font(R.font.press_start_2p))

@Composable
fun BrandTitle(modifier: Modifier = Modifier, size: Int = 18) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Text("Better", fontFamily = PixelFont, fontSize = size.sp)
        Text("Kick", fontFamily = PixelFont, fontSize = size.sp, color = KickGreen)
    }
}
@Composable
fun LanguageChips(lang: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        languages.forEach { (code, name) ->
            FilterChip(selected = lang == code, onClick = { onChange(code) }, label = { Text(name) })
        }
    }
}

/** Lista de lives por espectadores (destaques ou de uma categoria), com "Carregar mais". */
@Composable
fun LivesList(
    lang: String,
    categoryId: Int?,
    refreshKey: Int,
    bottomInset: Dp,
    onOpen: (String) -> Unit,
    modifier: Modifier = Modifier,
    scrollSignal: Int = 0,
    onLongPress: ((LiveItem) -> Unit)? = null,
) {
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    LaunchedEffect(scrollSignal) {
        if (scrollSignal > 0) listState.animateScrollToItem(0)
    }
    var items by remember(lang, categoryId) { mutableStateOf<List<LiveItem>>(emptyList()) }
    var next by remember(lang, categoryId) { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var loadingMore by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var retry by remember { mutableStateOf(0) }

    LaunchedEffect(lang, categoryId, refreshKey, retry) {
        loading = true
        error = null
        try {
            val page = KickApi.lives(lang.takeIf { it != "all" }, categoryId)
            items = page.items
            next = page.next
        } catch (e: Exception) {
            error = e.message ?: "Falha ao carregar."
        } finally {
            loading = false
        }
    }

    when {
        loading && items.isEmpty() -> SkeletonList(bottomInset, modifier = modifier)

        error != null && items.isEmpty() -> Column(
            modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(error!!, color = MaterialTheme.colorScheme.error)
            Button(onClick = { retry++ }, modifier = Modifier.padding(top = 12.dp)) { Text("Tentar de novo") }
        }

        else -> LazyColumn(
            state = listState,
            modifier = modifier,
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = bottomInset),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            items(items, key = { it.slug }) { item ->
                Box(Modifier.animateItem()) {
                    LiveCard(item, onLongClick = onLongPress?.let { cb -> { cb(item) } }) { onOpen(item.slug) }
                }
            }
            if (items.isEmpty()) item { Text("Nenhuma live agora.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            if (next != null) item {
                TextButton(
                    enabled = !loadingMore,
                    onClick = {
                        loadingMore = true
                        scope.launch {
                            try {
                                val page = KickApi.lives(lang.takeIf { it != "all" }, categoryId, next)
                                items = (items + page.items).distinctBy { it.slug }
                                next = page.next
                            } catch (_: Exception) {
                            } finally {
                                loadingMore = false
                            }
                        }
                    },
                ) { Text(if (loadingMore) "Carregando..." else "Carregar mais", color = KickGreen) }
            }
        }
    }
}

@Composable
fun SpeedTestRow(modifier: Modifier = Modifier) {
    var speed by remember { mutableStateOf<String?>(null) }
    var testing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TextButton(
            enabled = !testing,
            onClick = {
                testing = true
                speed = "Medindo..."
                scope.launch {
                    speed = try {
                        val r = SpeedTest.run()
                        tr("%.0f Mbps • ideal para %s").format(r.best, r.recommendation)
                    } catch (e: Exception) {
                        tr("Falha no teste: %s").format(e.message)
                    }
                    testing = false
                }
            },
        ) { Text("Testar velocidade", color = KickGreen) }
        speed?.let { Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
fun CategoryCard(cat: Category, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(modifier = modifier.clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick)) {
        AsyncImage(
            model = cat.banner,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(3f / 4f)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        )
        Text(cat.name, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
        Text(tr("%s assistindo").format(formatViewers(cat.viewers)), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Abre a página do canal (clipes e transmissões anteriores). Fornecido pelo App; padrão não faz nada. */
val LocalOpenChannel = androidx.compose.runtime.staticCompositionLocalOf<(String) -> Unit> { {} }

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LiveCard(item: LiveItem, onLongClick: (() -> Unit)? = null, onClick: () -> Unit) {
    val openChannel = LocalOpenChannel.current
    Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).combinedClickable(onClick = onClick, onLongClick = onLongClick, role = androidx.compose.ui.semantics.Role.Button, onLongClickLabel = tr("Mais ações"))) {
        Box {
            AsyncImage(
                model = item.thumbnail,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            )
            LiveBadge(item.viewers, Modifier.align(Alignment.TopStart).padding(10.dp))
        }
        Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.Top) {
            AsyncImage(
                model = item.avatar,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { openChannel(item.slug) },
            )
            Column(Modifier.padding(start = 12.dp).weight(1f)) {
                Text(
                    item.title.ifBlank { item.username },
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    listOfNotNull(item.username, item.category?.takeIf { it.isNotBlank() }).joinToString(" • "),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}

@Composable
fun LiveBadge(viewers: Int, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.clip(RoundedCornerShape(8.dp)).background(Color(0xCC000000)).padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(Color(0xFFFF4D4D)))
        Text(formatViewers(viewers), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 5.dp))
    }
}

/** Preview de uma live que você segue (miniatura grande, avatar, nome e categoria). */
@Composable
@OptIn(ExperimentalFoundationApi::class)
fun FollowedPreviewCard(ch: FollowedChannel, thumbnail: String?, modifier: Modifier = Modifier, onLongClick: (() -> Unit)? = null, onClick: () -> Unit) {
    val openChannel = LocalOpenChannel.current
    Column(modifier = modifier.clip(RoundedCornerShape(14.dp)).combinedClickable(onClick = onClick, onLongClick = onLongClick, role = androidx.compose.ui.semantics.Role.Button, onLongClickLabel = tr("Mais ações"))) {
        Box {
            AsyncImage(
                model = thumbnail ?: ch.avatar,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            )
            LiveBadge(ch.viewers, Modifier.align(Alignment.TopStart).padding(8.dp))
        }
        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = ch.avatar,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(30.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { openChannel(ch.slug) },
            )
            Column(Modifier.padding(start = 8.dp)) {
                Text(ch.username, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    ch.category?.takeIf { it.isNotBlank() } ?: ch.title,
                    color = KickGreen,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
@Composable
@OptIn(ExperimentalFoundationApi::class)
fun FollowedRow(ch: FollowedChannel, onLongClick: (() -> Unit)? = null, onClick: () -> Unit) {
    val openChannel = LocalOpenChannel.current
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).combinedClickable(onClick = onClick, onLongClick = onLongClick, role = androidx.compose.ui.semantics.Role.Button, onLongClickLabel = tr("Mais ações")).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = ch.avatar,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable { openChannel(ch.slug) },
        )
        Column(Modifier.padding(start = 12.dp).weight(1f)) {
            Text(ch.username, fontWeight = FontWeight.Bold)
            if (ch.isLive) {
                Text(
                    listOfNotNull("● AO VIVO", formatViewers(ch.viewers), ch.category?.takeIf { it.isNotBlank() }).joinToString(" • "),
                    color = KickGreen,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (ch.title.isNotBlank()) Text(ch.title, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            } else {
                Text("Offline", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Avatar com anel verde para o carrossel de "Seguindo ao vivo" da Home. */
@Composable
fun LiveAvatar(ch: FollowedChannel, onClick: () -> Unit) {
    Column(
        Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(68.dp).clip(CircleShape).background(KickGreen), contentAlignment = Alignment.Center) {
            AsyncImage(
                model = ch.avatar,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(62.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
            )
        }
        Text(ch.username, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp).size(width = 68.dp, height = 16.dp))
    }
}

/** Logo da BetterKick (B branco + K verde) pulsando no lugar do indicador de progresso; `size` é a altura. */
@Composable
fun KLoader(modifier: Modifier = Modifier, size: Dp = 64.dp) {
    val transition = rememberInfiniteTransition(label = "k-loader")
    val scale by transition.animateFloat(
        initialValue = 0.86f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(tween(650, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "scale",
    )
    val alpha by transition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(650, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "alpha",
    )
    Image(
        painter = painterResource(if (LocalIsDark.current) R.drawable.ic_logo_bk else R.drawable.ic_logo_bk_dark),
        contentDescription = "Carregando",
        modifier = modifier
            .size(width = size * 1.545f, height = size)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                this.alpha = alpha
            },
    )
}

fun Modifier.shimmer(): Modifier = composed {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val x by transition.animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(tween(1300, easing = LinearEasing)),
        label = "x",
    )
    background(
        Brush.linearGradient(
            colors = listOf(Color(0xFF161B1D), Color(0xFF262D31), Color(0xFF161B1D)),
            start = Offset(x * 500f, 0f),
            end = Offset(x * 500f + 500f, 120f),
        ),
    )
}

@Composable
fun SkeletonCard(modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(14.dp)).shimmer())
        Row(Modifier.padding(top = 10.dp)) {
            Box(Modifier.size(40.dp).clip(CircleShape).shimmer())
            Column(Modifier.padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.fillMaxWidth(0.8f).height(14.dp).clip(RoundedCornerShape(4.dp)).shimmer())
                Box(Modifier.fillMaxWidth(0.5f).height(11.dp).clip(RoundedCornerShape(4.dp)).shimmer())
            }
        }
    }
}

@Composable
fun SkeletonList(bottomInset: Dp, count: Int = 3, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxSize().padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = bottomInset),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        repeat(count) { SkeletonCard() }
    }
}
/** `true` enquanto o aparelho tem uma rede com internet (atualiza sozinho). */
@Composable
fun rememberIsOnline(): Boolean {
    val context = androidx.compose.ui.platform.LocalContext.current
    val cm = remember { context.getSystemService(android.net.ConnectivityManager::class.java) }
    var online by remember {
        mutableStateOf(
            cm?.activeNetwork?.let { cm.getNetworkCapabilities(it)?.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET) } == true,
        )
    }
    androidx.compose.runtime.DisposableEffect(cm) {
        val callback = object : android.net.ConnectivityManager.NetworkCallback() {
            // Rede padrão com internet validada; se ela cai e não há outra, onLost marca offline.
            override fun onCapabilitiesChanged(network: android.net.Network, caps: android.net.NetworkCapabilities) {
                online = caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                    caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            }

            override fun onLost(network: android.net.Network) {
                online = false
            }
        }
        runCatching { cm?.registerDefaultNetworkCallback(callback) }
        onDispose { runCatching { cm?.unregisterNetworkCallback(callback) } }
    }
    return online
}

/**
 * Barra de progresso verde: mostra quanto já passou (verde), quanto já carregou (cinza claro) e o que falta.
 * Toque ou arraste para ir para outro ponto; o pulo só acontece ao soltar. [dragLabel] é o texto que aparece
 * sobre a barra enquanto você arrasta (recebe a posição em ms).
 */
@Composable
fun GreenSeekBar(
    position: Long,
    buffered: Long,
    duration: Long,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
    dragLabel: ((Long) -> String)? = null,
) {
    val green = KickGreen
    var dragFraction by remember { mutableStateOf<Float?>(null) }
    val safeDuration = duration.coerceAtLeast(1L)
    val playedFraction = dragFraction ?: (position.toFloat() / safeDuration).coerceIn(0f, 1f)
    val bufferedFraction = (buffered.toFloat() / safeDuration).coerceIn(0f, 1f)
    Box(modifier.height(28.dp)) {
        androidx.compose.foundation.Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(duration) {
                    detectTapGestures { offset ->
                        onSeek(((offset.x / size.width).coerceIn(0f, 1f) * safeDuration).toLong())
                    }
                }
                .pointerInput(duration) {
                    detectHorizontalDragGestures(
                        onDragStart = { dragFraction = (it.x / size.width).coerceIn(0f, 1f) },
                        onHorizontalDrag = { change, _ ->
                            change.consume()
                            dragFraction = (change.position.x / size.width).coerceIn(0f, 1f)
                        },
                        onDragEnd = {
                            dragFraction?.let { onSeek((it * safeDuration).toLong()) }
                            dragFraction = null
                        },
                        onDragCancel = { dragFraction = null },
                    )
                },
        ) {
            val track = 4.dp.toPx()
            val y = size.height / 2
            val round = androidx.compose.ui.geometry.CornerRadius(track / 2, track / 2)
            fun bar(fraction: Float, color: Color) = drawRoundRect(
                color = color,
                topLeft = androidx.compose.ui.geometry.Offset(0f, y - track / 2),
                size = androidx.compose.ui.geometry.Size(size.width * fraction, track),
                cornerRadius = round,
            )
            bar(1f, Color(0x4DFFFFFF))
            bar(bufferedFraction, Color(0x80FFFFFF))
            bar(playedFraction, green)
            drawCircle(green, radius = (if (dragFraction != null) 9.dp else 6.dp).toPx(), center = androidx.compose.ui.geometry.Offset(size.width * playedFraction, y))
        }
        val dragging = dragFraction
        if (dragging != null && dragLabel != null) {
            Text(
                dragLabel((dragging * safeDuration).toLong()),
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = (-26).dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xCC000000))
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            )
        }
    }
}

/** Vibração curta (respeita a opção "Vibração" das Configurações). */
class Haptics(private val view: android.view.View, private val prefs: Prefs) {
    /** Toque leve: troca de aba, opção ligada ou desligada. */
    fun tick() {
        if (prefs.haptics) view.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
    }

    /** Segurar: abre um menu. */
    fun press() {
        if (prefs.haptics) view.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
    }

    /** Ação concluída (seguir, favoritar). */
    fun confirm() {
        if (!prefs.haptics) return
        view.performHapticFeedback(
            if (android.os.Build.VERSION.SDK_INT >= 30) android.view.HapticFeedbackConstants.CONFIRM
            else android.view.HapticFeedbackConstants.VIRTUAL_KEY,
        )
    }
}

@Composable
fun rememberHaptics(): Haptics {
    val view = androidx.compose.ui.platform.LocalView.current
    val context = androidx.compose.ui.platform.LocalContext.current
    return remember(view) { Haptics(view, Prefs(context)) }
}

/** Aviso flutuante (offline / API da Kick instável). */
@Composable
fun StatusPill(text: String, color: Color, modifier: Modifier = Modifier) {
    Text(
        text,
        color = Color.White,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier.clip(RoundedCornerShape(16.dp)).background(color).padding(horizontal = 12.dp, vertical = 6.dp),
    )
}