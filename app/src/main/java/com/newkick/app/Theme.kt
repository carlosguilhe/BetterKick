package com.newkick.app

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

/** Escolha de tema do usuário: "dark", "light" ou "system". */
object ThemeState {
    var mode by mutableStateOf("dark")

    /** Cores do sistema (Material You) nos fundos; o verde da Kick continua como destaque. */
    var dynamic by mutableStateOf(false)
}

val LocalIsDark = compositionLocalOf { true }

/** Verde da Kick: vivo no tema escuro, um tom mais fechado no claro (para o texto continuar legível). */
val KickGreen: Color
    @Composable
    @ReadOnlyComposable
    get() = if (LocalIsDark.current) Color(0xFF53FC18) else Color(0xFF2DAA0A)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF53FC18),
    onPrimary = Color(0xFF0B0E0F),
    secondaryContainer = Color(0xFF173010),
    onSecondaryContainer = Color(0xFFBFFF9E),
    background = Color(0xFF0B0E0F),
    onBackground = Color(0xFFEDEDED),
    surface = Color(0xFF14181A),
    onSurface = Color(0xFFEDEDED),
    surfaceVariant = Color(0xFF1D2326),
    onSurfaceVariant = Color(0xFFA7B0B5),
    error = Color(0xFFFF6B6B),
)

/** Preto puro: economiza bateria em telas OLED. */
private val AmoledColors = DarkColors.copy(
    background = Color.Black,
    surface = Color(0xFF0A0C0D),
    surfaceVariant = Color(0xFF15191B),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF2DAA0A),
    onPrimary = Color.White,
    secondaryContainer = Color(0xFFD7F3C8),
    onSecondaryContainer = Color(0xFF1B4D0A),
    background = Color(0xFFF6F8F7),
    onBackground = Color(0xFF111517),
    surface = Color.White,
    onSurface = Color(0xFF111517),
    surfaceVariant = Color(0xFFE6EBEA),
    onSurfaceVariant = Color(0xFF58636B),
    error = Color(0xFFC62828),
)

@Composable
fun NewKickTheme(forceDark: Boolean = false, content: @Composable () -> Unit) {
    val system = isSystemInDarkTheme()
    val dark = forceDark || when (ThemeState.mode) {
        "light" -> false
        "system" -> system
        else -> true
    }
    val context = LocalContext.current
    val scheme = when {
        !forceDark && ThemeState.dynamic && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val base = if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            base.copy(
                primary = if (dark) Color(0xFF53FC18) else Color(0xFF2DAA0A),
                onPrimary = if (dark) Color(0xFF0B0E0F) else Color.White,
                error = if (dark) Color(0xFFFF6B6B) else Color(0xFFC62828),
            )
        }
        dark && !forceDark && ThemeState.mode == "black" -> AmoledColors
        dark -> DarkColors
        else -> LightColors
    }
    CompositionLocalProvider(LocalIsDark provides dark) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
