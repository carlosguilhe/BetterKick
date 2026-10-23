package com.newkick.app

import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Mini player: canto inferior esquerdo, arrastável. Toque para voltar ao player cheio; arraste
 * para fora da tela para fechar (a live para de tocar).
 */
@OptIn(UnstableApi::class)
@Composable
fun MiniPlayerOverlay(bottomInset: Dp, onExpand: (slug: String) -> Unit) {
    val channel = PlayerSession.channel ?: return
    val player = PlayerSession.player ?: return
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val width = 196.dp
        val height = width * 9 / 16
        val screenW = with(density) { maxWidth.toPx() }
        val screenH = with(density) { maxHeight.toPx() }
        val w = with(density) { width.toPx() }
        val h = with(density) { height.toPx() }
        val margin = with(density) { 14.dp.toPx() }
        val homeY = screenH - h - with(density) { bottomInset.toPx() }
        val topLimit = with(density) { 40.dp.toPx() }

        val offset = remember { Animatable(Offset(margin, homeY), Offset.VectorConverter) }
        val scale = remember { Animatable(0.4f) }
        val alpha = remember { Animatable(0f) }
        var dismissing by remember { mutableStateOf(false) }

        // Entrada: cresce do canto inferior esquerdo.
        LaunchedEffect(Unit) {
            launch { scale.animateTo(1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium)) }
            alpha.animateTo(1f, tween(180))
        }
        // Se a barra de navegação mudar de altura/posição, mantém no fundo.
        LaunchedEffect(homeY) {
            if (offset.value.y > homeY) offset.snapTo(offset.value.copy(y = homeY))
        }

        Box(
            Modifier
                .offset { IntOffset(offset.value.x.roundToInt(), offset.value.y.roundToInt()) }
                .graphicsLayer {
                    scaleX = scale.value
                    scaleY = scale.value
                    this.alpha = alpha.value
                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 1f)
                }
                .size(width, height)
                .shadow(10.dp, RoundedCornerShape(12.dp))
                .clip(RoundedCornerShape(12.dp))
                .background(Color.Black)
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragEnd = {
                            scope.launch {
                                val cx = offset.value.x + w / 2
                                val cy = offset.value.y + h / 2
                                // Passou da borda da tela (centro fora): fecha deslizando para fora.
                                val out = when {
                                    cx < 0f -> Offset(-w * 1.5f, offset.value.y)
                                    cx > screenW -> Offset(screenW + w * 0.5f, offset.value.y)
                                    cy > screenH -> Offset(offset.value.x, screenH + h)
                                    cy < 0f -> Offset(offset.value.x, -h * 1.5f)
                                    else -> null
                                }
                                if (out != null) {
                                    dismissing = true
                                    launch { alpha.animateTo(0f, tween(200)) }
                                    offset.animateTo(out, tween(220))
                                    PlayerSession.release()
                                } else {
                                    // Gruda no canto esquerdo ou direito mais próximo.
                                    val targetX = if (cx < screenW / 2) margin else screenW - w - margin
                                    val targetY = offset.value.y.coerceIn(topLimit, homeY)
                                    offset.animateTo(Offset(targetX, targetY), spring(Spring.DampingRatioLowBouncy, Spring.StiffnessMediumLow))
                                }
                            }
                        },
                    ) { change, drag ->
                        change.consume()
                        if (!dismissing) scope.launch { offset.snapTo(offset.value + Offset(drag.x, drag.y)) }
                    }
                }
                .clickable { onExpand(channel.slug) },
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                        useController = false
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                        setShutterBackgroundColor(android.graphics.Color.BLACK)
                    }
                },
                update = { it.player = player },
                onRelease = { it.player = null },
            )
            Box(
                Modifier.fillMaxWidth().height(34.dp).align(Alignment.BottomStart)
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC000000)))),
            )
            Row(
                Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(start = 8.dp, end = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    channel.username,
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Box(
                    Modifier.size(26.dp).clip(RoundedCornerShape(13.dp)).clickable { PlayerSession.togglePlay() },
                    contentAlignment = Alignment.Center,
                ) {
                    if (PlayerSession.playing) {
                        Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(3.dp)) {
                            repeat(2) { Box(Modifier.size(width = 3.dp, height = 12.dp).clip(RoundedCornerShape(1.dp)).background(Color.White)) }
                        }
                    } else {
                        Icon(Icons.Default.PlayArrow, "Tocar", tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                }
            }
            Box(
                Modifier.align(Alignment.TopEnd).padding(4.dp).size(22.dp).clip(RoundedCornerShape(11.dp))
                    .background(Color(0x99000000)).clickable { PlayerSession.release() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.Close, "Fechar mini player", tint = Color.White, modifier = Modifier.size(14.dp))
            }
            if (PlayerSession.buffering && PlayerSession.errorMessage == null) {
                KLoader(Modifier.align(Alignment.Center), size = 24.dp)
            }
        }
    }
}
