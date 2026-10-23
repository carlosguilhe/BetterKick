package com.newkick.app

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.haze
import dev.chrisbanes.haze.hazeChild
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay

enum class MainTab(val label: String, val icon: ImageVector) {
    HOME("Home", Icons.Outlined.Home),
    CATEGORIES("Categorias", Icons.Outlined.SportsEsports),
    FOLLOWING("Seguindo", Icons.Outlined.FavoriteBorder),
    FEATURED("Destaques", Icons.Outlined.LocalFireDepartment),
    PROFILE("Perfil", Icons.Outlined.Person),
}

private class FollowedState(
    val thumbs: Map<String, String?>,
    val list: List<FollowedChannel>,
    val loading: Boolean,
    val expired: Boolean,
    val error: String?,
)

@Composable
fun MainShell(
    tab: MainTab,
    onTab: (MainTab) -> Unit,
    onOpen: (String) -> Unit,
    onOpenChannel: (String) -> Unit,
    onLogin: () -> Unit,
    onOpenSite: (url: String, title: String) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenHistory: () -> Unit,
    onLoggedOut: () -> Unit,
) {
    val context = LocalContext.current
    val prefs = remember { Prefs(context) }
    val hazeState = remember { HazeState() }
    val loggedIn = remember(tab) { KickSession.hasToken }
    val online = rememberIsOnline()

    var lang by remember { mutableStateOf(prefs.lang) }
    var category by remember { mutableStateOf<Category?>(null) }
    var refreshTick by remember { mutableIntStateOf(0) }
    var favTick by remember { mutableIntStateOf(0) }
    var scrollSignals by remember { mutableStateOf(mapOf<MainTab, Int>()) }
    var sheetTarget by remember { mutableStateOf<ChannelTarget?>(null) }

    var followed by remember { mutableStateOf<List<FollowedChannel>>(emptyList()) }
    var followedLoading by remember { mutableStateOf(loggedIn) }
    var followedExpired by remember { mutableStateOf(false) }
    var followedError by remember { mutableStateOf<String?>(null) }
    var thumbs by remember { mutableStateOf<Map<String, String?>>(emptyMap()) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000)
            favTick++
        }
    }

    // Quem você segue: compartilhado entre Home (carrossel) e a aba Seguindo.
    LaunchedEffect(loggedIn, favTick, refreshTick) {
        if (!loggedIn) {
            followed = emptyList()
            followedLoading = false
            return@LaunchedEffect
        }
        try {
            followed = KickApi.followed().sortedWith(compareByDescending<FollowedChannel> { it.isLive }.thenByDescending { it.viewers })
            followedExpired = false
            followedError = null
        } catch (e: KickSessionExpired) {
            followedExpired = true
        } catch (e: Exception) {
            if (followed.isEmpty()) followedError = e.message ?: "Falha ao carregar."
        } finally {
            followedLoading = false
        }
    }

    // Miniaturas das lives que você segue (só busca quando a aba Seguindo está aberta).
    LaunchedEffect(followed, tab, favTick) {
        if (tab != MainTab.FOLLOWING) return@LaunchedEffect
        val live = followed.filter { it.isLive }
        if (live.isEmpty()) return@LaunchedEffect
        thumbs = thumbs + coroutineScope {
            live.map { ch -> async { ch.slug to runCatching { KickApi.liveThumbnail(ch.slug) }.getOrNull() } }.awaitAll()
        }.toMap()
    }

    val setLang: (String) -> Unit = {
        lang = it
        prefs.lang = it
    }
    val openCategory: (Category) -> Unit = {
        category = it
        onTab(MainTab.CATEGORIES)
    }
    val reselect: (MainTab) -> Unit = { t -> scrollSignals = scrollSignals + (t to ((scrollSignals[t] ?: 0) + 1)) }
    val followedState = FollowedState(thumbs, followed, followedLoading, followedExpired, followedError)
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 100.dp
    val longPress: (ChannelTarget) -> Unit = { sheetTarget = it }

    Box(Modifier.fillMaxSize()) {
        // Todo o conteúdo é a "fonte" do desfoque da barra.
        Box(Modifier.fillMaxSize().haze(hazeState)) {
            Crossfade(targetState = tab, animationSpec = tween(220), label = "tabs") { current ->
                when (current) {
                    MainTab.HOME -> HomeTab(lang, loggedIn, followedState, bottomInset, refreshTick, scrollSignals[MainTab.HOME] ?: 0, { refreshTick++ }, onOpen, openCategory, { onTab(MainTab.FEATURED) }, longPress)
                    MainTab.CATEGORIES -> CategoriesTab(category, { category = it }, lang, setLang, bottomInset, refreshTick, scrollSignals[MainTab.CATEGORIES] ?: 0, { refreshTick++ }, onOpen, longPress)
                    MainTab.FOLLOWING -> FollowingTab(followedState, loggedIn, bottomInset, scrollSignals[MainTab.FOLLOWING] ?: 0, { refreshTick++ }, onOpen, onLogin, longPress)
                    MainTab.FEATURED -> FeaturedTab(lang, setLang, bottomInset, refreshTick, scrollSignals[MainTab.FEATURED] ?: 0, { refreshTick++ }, onOpen, longPress)
                    MainTab.PROFILE -> ProfileTab(bottomInset, scrollSignals[MainTab.PROFILE] ?: 0, onLogin, onLoggedOut, onOpenSite, onOpenSettings, onOpenHistory)
                }
            }
        }

        // Avisos: sem internet / a Kick pode ter mudado algo.
        Column(
            Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(end = 20.dp, bottom = 92.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (!online) StatusPill("Sem conexão: mostrando o que já foi carregado", Color(0xE6444B50))
            else if (KickApi.failureStreak >= 3) StatusPill("A Kick pode ter mudado algo no servidor", Color(0xE6B3541E))
        }

        FloatingNavBar(tab, onTab, reselect, hazeState, Modifier.align(Alignment.BottomCenter))

        sheetTarget?.let { target ->
            ChannelActionsSheet(
                target = target,
                loggedIn = loggedIn,
                followedSlugs = followed.map { it.slug }.toSet(),
                onDismiss = { sheetTarget = null },
                onOpen = onOpen,
                onOpenChannel = onOpenChannel,
                onFollowChanged = { refreshTick++ },
            )
        }
    }
}

/**
 * Barra flutuante com fundo translúcido e desfoque. Um círculo verde desliza (mola) até a aba
 * selecionada e o ícone dá um pulinho. Tocar na aba já selecionada volta a lista ao topo.
 */
@Composable
private fun FloatingNavBar(selected: MainTab, onSelect: (MainTab) -> Unit, onReselect: (MainTab) -> Unit, hazeState: HazeState, modifier: Modifier) {
    val tabs = MainTab.entries
    val haptic = LocalHapticFeedback.current
    val shape = RoundedCornerShape(32.dp)
    val circle = 50.dp
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val barBackground = if (dark) Color(0xFF0B0E0F) else Color(0xFFF4F6F7)
    val barTint = if (dark) Color(0x66141A1D) else Color(0x66FFFFFF)
    val idleIcon = if (dark) Color(0xFFEDEDED) else Color(0xFF263033)
    val borderColor = if (dark) Color(0x33FFFFFF) else Color(0x33000000)

    BoxWithConstraints(
        modifier = modifier
            .navigationBarsPadding()
            .padding(start = 20.dp, end = 20.dp, bottom = 14.dp)
            .fillMaxWidth()
            .height(66.dp)
            .clip(shape)
            .hazeChild(
                state = hazeState,
                shape = shape,
                style = HazeStyle(
                    backgroundColor = barBackground,
                    tint = HazeTint(barTint),
                    blurRadius = 26.dp,
                    noiseFactor = 0.05f,
                ),
            )
            .border(1.dp, borderColor, shape),
    ) {
        val itemWidth = maxWidth / tabs.size
        val index = tabs.indexOf(selected)
        val circleX by animateDpAsState(
            targetValue = itemWidth * index + (itemWidth - circle) / 2,
            animationSpec = spring(dampingRatio = 0.55f, stiffness = 380f),
            label = "circleX",
        )
        Box(
            Modifier
                .offset(x = circleX)
                .align(Alignment.CenterStart)
                .size(circle)
                .clip(CircleShape)
                .background(KickGreen),
        )
        Row(Modifier.fillMaxSize()) {
            tabs.forEach { t ->
                val isSelected = t == selected
                val tint by animateColorAsState(if (isSelected) Color(0xFF0B0E0F) else idleIcon, label = "tint")
                val scale by animateFloatAsState(
                    targetValue = if (isSelected) 1.18f else 1f,
                    animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium),
                    label = "scale",
                )
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                            if (isSelected) {
                                onReselect(t)
                            } else {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onSelect(t)
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(t.icon, contentDescription = t.label, tint = tint, modifier = Modifier.size(26.dp).scale(scale))
                }
            }
        }
    }
}

// ───────────────────────────── Abas ─────────────────────────────

@Composable
private fun TabHeader(title: @Composable () -> Unit, onRefresh: (() -> Unit)? = null, leading: @Composable () -> Unit = {}) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading()
        title()
        Box(Modifier.weight(1f))
        if (onRefresh != null) IconButton(onClick = onRefresh) { Icon(Icons.Default.Refresh, contentDescription = "Atualizar") }
    }
}

private enum class SearchFilter(val label: String) { ALL("Tudo"), CHANNELS("Canais"), CATEGORIES("Categorias") }

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HomeTab(
    lang: String,
    loggedIn: Boolean,
    followed: FollowedState,
    bottomInset: Dp,
    refreshTick: Int,
    scrollSignal: Int,
    onRefresh: () -> Unit,
    onOpen: (String) -> Unit,
    onCategory: (Category) -> Unit,
    onSeeAll: () -> Unit,
    onLongPress: (ChannelTarget) -> Unit,
) {
    val context = LocalContext.current
    val prefs = remember { Prefs(context) }
    val favorites = remember { prefs.favorites() }
    val recents = remember { prefs.recents() }
    val listState = rememberLazyListState()
    LaunchedEffect(scrollSignal) { if (scrollSignal > 0) listState.animateScrollToItem(0) }

    var query by rememberSaveable { mutableStateOf("") }
    var searchFocused by remember { mutableStateOf(false) }
    var searchFilter by remember { mutableStateOf(SearchFilter.ALL) }
    var searchHistory by remember { mutableStateOf(prefs.searchHistory()) }
    var suggestions by remember { mutableStateOf<SearchResult?>(null) }
    var categories by remember { mutableStateOf<List<Category>>(emptyList()) }
    var lives by remember { mutableStateOf<List<LiveItem>>(emptyList()) }
    var recommended by remember { mutableStateOf<List<LiveItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(lang, refreshTick) {
        loading = true
        error = null
        try {
            categories = KickApi.topCategories()
            lives = KickApi.lives(lang.takeIf { it != "all" }, null, limit = 11).items
        } catch (e: Exception) {
            error = e.message ?: "Falha ao carregar."
        } finally {
            loading = false
        }
    }

    // Recomendados: categorias que você mais assistiu (pelo histórico), sem repetir o que já está na tela.
    LaunchedEffect(categories, refreshTick, lang) {
        val top = prefs.history().mapNotNull { it.category }.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.take(2).map { it.key }
        val ids = top.mapNotNull { name -> categories.firstOrNull { it.name.equals(name, ignoreCase = true) }?.id }
        if (ids.isEmpty()) {
            recommended = emptyList()
            return@LaunchedEffect
        }
        recommended = runCatching {
            ids.flatMap { KickApi.lives(lang.takeIf { it != "all" }, it, limit = 4).items }.distinctBy { it.slug }.take(6)
        }.getOrDefault(emptyList())
    }

    LaunchedEffect(query) {
        if (query.trim().length < 2) {
            suggestions = null
            return@LaunchedEffect
        }
        delay(350)
        suggestions = try { KickApi.search(query) } catch (e: Exception) { null }
    }

    BackHandler(enabled = query.isNotEmpty()) { query = "" }

    val openTyped = {
        val slug = query.trim().substringAfterLast('/').trim()
        if (slug.isNotEmpty()) {
            prefs.addSearch(slug)
            onOpen(slug)
        }
    }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        TabHeader(title = { BrandTitle() }, onRefresh = onRefresh)
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).onFocusChanged { searchFocused = it.isFocused },
            placeholder = { Text("Buscar canal ou categoria") },
            singleLine = true,
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { query = "" }) { Icon(Icons.Default.Close, contentDescription = "Limpar") }
                } else {
                    IconButton(onClick = openTyped) { Icon(Icons.Default.Search, contentDescription = "Abrir") }
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { openTyped() }),
            shape = RoundedCornerShape(12.dp),
        )

        val s = suggestions
        if (query.isEmpty() && searchFocused && searchHistory.isNotEmpty()) {
            // Buscas recentes
            LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = bottomInset)) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Buscas recentes", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        TextButton(onClick = {
                            prefs.clearSearchHistory()
                            searchHistory = emptyList()
                        }) { Text("Limpar", color = KickGreen) }
                    }
                }
                items(searchHistory, key = { it }) { q ->
                    Text(q, modifier = Modifier.fillMaxWidth().clickable { query = q }.padding(vertical = 12.dp))
                }
            }
            return@Column
        }
        if (query.trim().length >= 2 && s != null) {
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SearchFilter.entries.forEach { f -> FilterChip(selected = searchFilter == f, onClick = { searchFilter = f }, label = { Text(f.label) }) }
            }
            LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = bottomInset)) {
                if (s.channels.isEmpty() && s.categories.isEmpty()) {
                    item { Text("Nada encontrado.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                if (searchFilter != SearchFilter.CATEGORIES) {
                    items(s.channels.take(10), key = { "c_" + it.slug }) { ch ->
                        Column(
                            Modifier.fillMaxWidth().combinedClickable(
                                onClick = {
                                    prefs.addSearch(query)
                                    onOpen(ch.slug)
                                },
                                onLongClick = { onLongPress(ChannelTarget(ch.slug, ch.username)) },
                            ).padding(vertical = 10.dp),
                        ) {
                            Text(ch.username, fontWeight = FontWeight.Bold)
                            Text("${formatViewers(ch.followers)} seguidores", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                if (searchFilter != SearchFilter.CHANNELS) {
                    items(s.categories.take(6), key = { "k_" + it.slug }) { cat ->
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                prefs.addSearch(query)
                                query = ""
                                onCategory(cat)
                            }.padding(vertical = 10.dp),
                        ) {
                            Text("Categoria: ", color = KickGreen, fontSize = 13.sp)
                            Text(cat.name, fontSize = 13.sp)
                        }
                    }
                }
            }
            return@Column
        }

        when {
            loading && lives.isEmpty() -> SkeletonList(bottomInset, modifier = Modifier.fillMaxSize())

            error != null && lives.isEmpty() -> Column(
                Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(error!!, color = MaterialTheme.colorScheme.error)
                Button(onClick = onRefresh, modifier = Modifier.padding(top = 12.dp)) { Text("Tentar de novo") }
            }

            else -> PullRefresh(onRefresh = onRefresh) {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(top = 4.dp, bottom = bottomInset),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val hot = lives.take(5)
                    val rest = lives.drop(5)
                    if (hot.isNotEmpty()) {
                        item { SectionTitle("Em alta agora") }
                        item { FeaturedCarousel(hot, onOpen) { onLongPress(ChannelTarget(it.slug, it.username)) } }
                    }
                    val liveFollowed = followed.list.filter { it.isLive }
                    if (loggedIn && liveFollowed.isNotEmpty()) {
                        item { SectionTitle("Seguindo ao vivo") }
                        item {
                            LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                items(liveFollowed, key = { it.slug }) { ch -> LiveAvatar(ch) { onOpen(ch.slug) } }
                            }
                        }
                    }
                    if (recommended.isNotEmpty()) {
                        item { SectionTitle("Recomendado para você") }
                        item {
                            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                items(recommended, key = { "r_" + it.slug }) { live ->
                                    Box(Modifier.size(width = 260.dp, height = 190.dp)) {
                                        LiveCard(live, onLongClick = { onLongPress(ChannelTarget(live.slug, live.username)) }) { onOpen(live.slug) }
                                    }
                                }
                            }
                        }
                    }
                    val chips = (favorites + recents).distinct().take(12)
                    if (chips.isNotEmpty()) {
                        item { SectionTitle("Favoritos e recentes") }
                        item {
                            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(chips, key = { it }) { slug -> AssistChip(onClick = { onOpen(slug) }, label = { Text(slug) }) }
                            }
                        }
                    }
                    if (categories.isNotEmpty()) {
                        item { SectionTitle("Categorias populares") }
                        item {
                            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                items(categories.take(12), key = { it.slug }) { cat ->
                                    CategoryCard(cat, Modifier.size(width = 118.dp, height = 200.dp)) { onCategory(cat) }
                                }
                            }
                        }
                    }
                    if (rest.isNotEmpty()) {
                        item { SectionTitle("Mais lives") }
                        items(rest, key = { "m_" + it.slug }) { live ->
                            Box(Modifier.padding(horizontal = 16.dp, vertical = 6.dp).animateItem()) {
                                LiveCard(live, onLongClick = { onLongPress(ChannelTarget(live.slug, live.username)) }) { onOpen(live.slug) }
                            }
                        }
                    }
                    item {
                        TextButton(onClick = onSeeAll, modifier = Modifier.padding(start = 8.dp)) {
                            Text("Ver todos os destaques", color = KickGreen)
                        }
                    }
                }
            }
        }
    }
}

/** Carrossel de destaques com "snap": cada cartão ocupa quase a largura da tela. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FeaturedCarousel(items: List<LiveItem>, onOpen: (String) -> Unit, onLongPress: (LiveItem) -> Unit) {
    val state = rememberLazyListState()
    LazyRow(
        state = state,
        flingBehavior = rememberSnapFlingBehavior(state),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(items, key = { "h_" + it.slug }) { item ->
            Box(
                Modifier
                    .fillParentMaxWidth(0.86f)
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(16.dp))
                    .combinedClickable(onClick = { onOpen(item.slug) }, onLongClick = { onLongPress(item) }),
            ) {
                AsyncImage(
                    model = item.thumbnail,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant),
                )
                Box(
                    Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC000000)), startY = 150f)),
                )
                LiveBadge(item.viewers, Modifier.align(Alignment.TopStart).padding(10.dp))
                Column(Modifier.align(Alignment.BottomStart).padding(12.dp)) {
                    Text(item.title.ifBlank { item.username }, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(
                        listOfNotNull(item.username, item.category?.takeIf { it.isNotBlank() }).joinToString(" • "),
                        color = Color(0xFFDDDDDD),
                        fontSize = 12.sp,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, fontWeight = FontWeight.Bold, fontSize = 17.sp, modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 2.dp))
}

@Composable
private fun CategoriesTab(
    category: Category?,
    onCategory: (Category?) -> Unit,
    lang: String,
    onLang: (String) -> Unit,
    bottomInset: Dp,
    refreshTick: Int,
    scrollSignal: Int,
    onRefresh: () -> Unit,
    onOpen: (String) -> Unit,
    onLongPress: (ChannelTarget) -> Unit,
) {
    var categories by remember { mutableStateOf<List<Category>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    val gridState = rememberLazyGridState()
    LaunchedEffect(scrollSignal) { if (scrollSignal > 0) gridState.animateScrollToItem(0) }

    BackHandler(enabled = category != null) { onCategory(null) }

    LaunchedEffect(refreshTick) {
        loading = categories.isEmpty()
        error = null
        try {
            categories = KickApi.topCategories()
        } catch (e: Exception) {
            error = e.message ?: "Falha ao carregar."
        } finally {
            loading = false
        }
    }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        if (category != null) {
            TabHeader(
                title = { Text(category.name, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1) },
                onRefresh = onRefresh,
                leading = { IconButton(onClick = { onCategory(null) }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar") } },
            )
            LanguageChips(lang, onLang)
            PullRefresh(onRefresh = onRefresh, modifier = Modifier.weight(1f)) {
                LivesList(
                    lang, category.id, refreshTick, bottomInset, onOpen, Modifier.fillMaxSize(),
                    scrollSignal = scrollSignal,
                    onLongPress = { onLongPress(ChannelTarget(it.slug, it.username)) },
                )
            }
        } else {
            TabHeader(title = { Text("Categorias", fontSize = 22.sp, fontWeight = FontWeight.Bold) }, onRefresh = onRefresh)
            when {
                loading -> SkeletonList(bottomInset, modifier = Modifier.fillMaxSize())
                error != null && categories.isEmpty() -> Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(error!!, color = MaterialTheme.colorScheme.error)
                    Button(onClick = onRefresh, modifier = Modifier.padding(top = 12.dp)) { Text("Tentar de novo") }
                }
                else -> LazyVerticalGrid(
                    state = gridState,
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = bottomInset),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    gridItems(categories, key = { it.slug }) { cat -> CategoryCard(cat, Modifier.fillMaxWidth().animateItem()) { onCategory(cat) } }
                }
            }
        }
    }
}

@Composable
private fun FollowingTab(
    state: FollowedState,
    loggedIn: Boolean,
    bottomInset: Dp,
    scrollSignal: Int,
    onRefresh: () -> Unit,
    onOpen: (String) -> Unit,
    onLogin: () -> Unit,
    onLongPress: (ChannelTarget) -> Unit,
) {
    val gridState = rememberLazyGridState()
    LaunchedEffect(scrollSignal) { if (scrollSignal > 0) gridState.animateScrollToItem(0) }
    var search by remember { mutableStateOf("") }
    var mode by remember { mutableIntStateOf(0) } // 0 = todos, 1 = ao vivo, 2 = offline

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        val live = state.list.count { it.isLive }
        TabHeader(
            title = {
                Text(if (loggedIn && live > 0) "Seguindo ($live ao vivo)" else "Seguindo", fontSize = 22.sp, fontWeight = FontWeight.Bold)
            },
            onRefresh = if (loggedIn) onRefresh else null,
        )
        when {
            !loggedIn -> LoginPrompt("Entre na sua conta da Kick para ver os canais que você segue e quem está ao vivo.", onLogin)
            state.expired -> LoginPrompt("Sua sessão expirou. Entre de novo.") {
                KickSession.logout()
                onLogin()
            }
            state.loading && state.list.isEmpty() -> SkeletonList(bottomInset, modifier = Modifier.fillMaxSize())
            state.error != null && state.list.isEmpty() -> Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Text(state.error, color = MaterialTheme.colorScheme.error)
                Button(onClick = onRefresh, modifier = Modifier.padding(top = 12.dp)) { Text("Tentar de novo") }
            }
            else -> {
                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    placeholder = { Text("Filtrar quem eu sigo") },
                    singleLine = true,
                    trailingIcon = { if (search.isNotEmpty()) IconButton(onClick = { search = "" }) { Icon(Icons.Default.Close, "Limpar") } },
                    shape = RoundedCornerShape(12.dp),
                )
                Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Todos", "Ao vivo", "Offline").forEachIndexed { i, label ->
                        FilterChip(selected = mode == i, onClick = { mode = i }, label = { Text(label) })
                    }
                }
                val filtered = state.list.filter { ch ->
                    (search.isBlank() || ch.username.contains(search, ignoreCase = true) || ch.slug.contains(search, ignoreCase = true)) &&
                        when (mode) { 1 -> ch.isLive; 2 -> !ch.isLive; else -> true }
                }
                PullRefresh(onRefresh = onRefresh) {
                    val liveList = filtered.filter { it.isLive }
                    val offline = filtered.filter { !it.isLive }
                    LazyVerticalGrid(
                        state = gridState,
                        columns = GridCells.Fixed(2),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = bottomInset),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        if (filtered.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
                            Text(if (state.list.isEmpty()) "Você ainda não segue ninguém." else "Ninguém encontrado.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (liveList.isNotEmpty()) item(span = { GridItemSpan(maxLineSpan) }) { Text("Ao vivo agora", fontWeight = FontWeight.Bold, fontSize = 17.sp) }
                        gridItems(liveList, key = { "l_" + it.slug }) { ch ->
                            FollowedPreviewCard(
                                ch, state.thumbs[ch.slug],
                                modifier = Modifier.animateItem(),
                                onLongClick = { onLongPress(ChannelTarget(ch.slug, ch.username)) },
                            ) { onOpen(ch.slug) }
                        }
                        if (offline.isNotEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
                            Text("Offline", fontWeight = FontWeight.Bold, fontSize = 17.sp, modifier = Modifier.padding(top = 8.dp))
                        }
                        gridItems(offline, key = { "o_" + it.slug }, span = { GridItemSpan(maxLineSpan) }) { ch ->
                            Box(Modifier.animateItem()) {
                                FollowedRow(ch, onLongClick = { onLongPress(ChannelTarget(ch.slug, ch.username)) }) { onOpen(ch.slug) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LoginPrompt(message: String, onLogin: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Outlined.Person, contentDescription = null, tint = KickGreen, modifier = Modifier.size(56.dp))
        Text(message, modifier = Modifier.padding(top = 16.dp, bottom = 16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Button(onClick = onLogin) { Text("Entrar") }
    }
}

@Composable
private fun FeaturedTab(
    lang: String,
    onLang: (String) -> Unit,
    bottomInset: Dp,
    refreshTick: Int,
    scrollSignal: Int,
    onRefresh: () -> Unit,
    onOpen: (String) -> Unit,
    onLongPress: (ChannelTarget) -> Unit,
) {
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        TabHeader(title = { Text("Destaques", fontSize = 22.sp, fontWeight = FontWeight.Bold) }, onRefresh = onRefresh)
        LanguageChips(lang, onLang)
        PullRefresh(onRefresh = onRefresh, modifier = Modifier.weight(1f)) {
            LivesList(
                lang, null, refreshTick, bottomInset, onOpen, Modifier.fillMaxSize(),
                scrollSignal = scrollSignal,
                onLongPress = { onLongPress(ChannelTarget(it.slug, it.username)) },
            )
        }
    }
}

/** Puxar para atualizar (o indicador some sozinho depois de um instante). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PullRefresh(onRefresh: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    var refreshing by remember { mutableStateOf(false) }
    LaunchedEffect(refreshing) {
        if (refreshing) {
            delay(1_000)
            refreshing = false
        }
    }
    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = {
            refreshing = true
            onRefresh()
        },
        modifier = modifier,
    ) { content() }
}
