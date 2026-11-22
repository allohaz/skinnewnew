package com.skintracker.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

/** Цвета приложения. Читаются из composable, поэтому при смене темы экран перерисовывается сам. */
object C {
    var dark by mutableStateOf(true)

    val bg: Color get() = if (dark) Color(0xFF0E1116) else Color(0xFFF3F5F9)
    val card: Color get() = if (dark) Color(0xFF1B212B) else Color(0xFFFFFFFF)
    val card2: Color get() = if (dark) Color(0xFF252C38) else Color(0xFFE8ECF3)
    val text: Color get() = if (dark) Color(0xFFE8ECF3) else Color(0xFF161B24)
    val muted: Color get() = if (dark) Color(0xFF8C97A8) else Color(0xFF677184)
    val accent: Color get() = if (dark) Color(0xFF7C9CFF) else Color(0xFF3B5BDB)
    val up: Color get() = if (dark) Color(0xFF3DDC84) else Color(0xFF168A4E)
    val down: Color get() = if (dark) Color(0xFFFF6B6B) else Color(0xFFD13C3C)
    val sell: Color get() = if (dark) Color(0xFFFFB02E) else Color(0xFFC27500)
    val buy: Color get() = if (dark) Color(0xFF38D0F0) else Color(0xFF0A86A6)
    val flip: Color get() = if (dark) Color(0xFFB58CFF) else Color(0xFF7447D4)
}

/** mode: 0 — как в телефоне, 1 — тёмная, 2 — светлая. */
@Composable
fun SkinTheme(mode: Int = 0, content: @Composable () -> Unit) {
    val system = isSystemInDarkTheme()
    val dark = when (mode) {
        1 -> true
        2 -> false
        else -> system
    }
    C.dark = dark
    val scheme = if (dark) {
        darkColorScheme(
            primary = C.accent, onPrimary = C.bg,
            background = C.bg, onBackground = C.text,
            surface = C.bg, onSurface = C.text,
            surfaceVariant = C.card, onSurfaceVariant = C.muted,
            secondaryContainer = C.card2, onSecondaryContainer = C.text,
            primaryContainer = C.card2, onPrimaryContainer = C.text
        )
    } else {
        lightColorScheme(
            primary = C.accent, onPrimary = C.bg,
            background = C.bg, onBackground = C.text,
            surface = C.bg, onSurface = C.text,
            surfaceVariant = C.card, onSurfaceVariant = C.muted,
            secondaryContainer = C.card2, onSecondaryContainer = C.text,
            primaryContainer = C.card2, onPrimaryContainer = C.text
        )
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
