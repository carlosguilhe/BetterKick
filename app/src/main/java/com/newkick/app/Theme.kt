package com.newkick.app

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

/** Escolha de tema do usuário: "dark", "light" ou "system". */
object ThemeState {
    var mode by mutableStateOf("dark")
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
    CompositionLocalProvider(LocalIsDark provides dark) {
        MaterialTheme(colorScheme = if (dark) DarkColors else LightColors, content = content)
    }
}
