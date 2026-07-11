package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Themes
private val ClassicTvColorScheme = darkColorScheme(
    primary = Color(0xFF38BDF8), // Light Sky Blue
    secondary = Color(0xFFF59E0B), // Retro Wood Yellow
    tertiary = Color(0xFFEF4444),
    background = Color(0xFF1C1D24),
    surface = Color(0xFF282A36),
    onPrimary = Color.Black,
    onBackground = Color(0xFFF8FAFC),
    onSurface = Color(0xFFF1F5F9)
)

private val CyberpunkColorScheme = darkColorScheme(
    primary = Color(0xFFE0218A), // Neon pink
    secondary = Color(0xFF00FFFF), // Cyan
    tertiary = Color(0xFFFFFF00), // Yellow
    background = Color(0xFF0F081D),
    surface = Color(0xFF1E1035),
    onPrimary = Color.White,
    onBackground = Color(0xFF00FFFF),
    onSurface = Color(0xFFE2E8F0)
)

private val OledBlackColorScheme = darkColorScheme(
    primary = Color(0xFFFFFFFF), // Pure white
    secondary = Color(0xFFE2E8F0),
    tertiary = Color(0xFFEF4444),
    background = Color(0xFF000000), // Real black
    surface = Color(0xFF121212),
    onPrimary = Color.Black,
    onBackground = Color.White,
    onSurface = Color(0xFFF8FAFC)
)

private val ForestGreenColorScheme = darkColorScheme(
    primary = Color(0xFF4ADE80), // Sage green
    secondary = Color(0xFF22C55E),
    tertiary = Color(0xFFEAB308),
    background = Color(0xFF0C160F),
    surface = Color(0xFF16251B),
    onPrimary = Color.Black,
    onBackground = Color(0xFFECFDF5),
    onSurface = Color(0xFFF0FDF4)
)

private val StandardDarkColorScheme = darkColorScheme(
    primary = Color(0xFF38BDF8),
    secondary = Color(0xFF94A3B8),
    tertiary = Color(0xFF38BDF8),
    background = Color(0xFF0B0F19),
    surface = Color(0xFF1E293B),
    onPrimary = Color.Black,
    onBackground = Color(0xFFE2E8F0),
    onSurface = Color(0xFFF1F5F9)
)

private val StandardLightColorScheme = lightColorScheme(
    primary = Color(0xFF0284C7),
    secondary = Color(0xFF64748B),
    tertiary = Color(0xFF0284C7),
    background = Color(0xFFF8FAFC),
    surface = Color(0xFFFFFFFF),
    onPrimary = Color.White,
    onBackground = Color(0xFF0F172A),
    onSurface = Color(0xFF1E293B)
)

@Composable
fun MyApplicationTheme(
    themeName: String = "Dark",
    content: @Composable () -> Unit
) {
    val colorScheme = when (themeName) {
        "Classic TV" -> ClassicTvColorScheme
        "Cyberpunk" -> CyberpunkColorScheme
        "OLED Black" -> OledBlackColorScheme
        "Forest Green" -> ForestGreenColorScheme
        "Light" -> StandardLightColorScheme
        else -> StandardDarkColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
