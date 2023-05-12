package com.chatwaifu.mobile.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

var globalDarkTheme: Boolean = false

@Composable
fun ChatWaifu_MobileTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit,
) {
    var showTheme = darkTheme
    if (darkTheme != globalDarkTheme) {
        showTheme = globalDarkTheme
    }
    val colors = if (showTheme) {
        ChatWaifuDarkTheme
    } else {
        ChatWaifuLightTheme
    }

    MaterialTheme(
        colorScheme = colors,
        content = content
    )
}


private val ChatWaifuDarkTheme = darkColorScheme(
    primary = Color(0xFF0D47A1),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF1E88E5),
    onPrimaryContainer = Color(0xFFFFFFFF),
    inversePrimary = Color(0xFF0D47A1),
    secondary = Color(0xFFFF9800),
    onSecondary = Color(0xFF212121),
    secondaryContainer = Color(0xFFFFCC80),
    onSecondaryContainer = Color(0xFF212121),
    tertiary = Color(0xFF757575),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFA4A4A4),
    onTertiaryContainer = Color(0xFFFFFFFF),
    background = Color(0xFF212121),
    onBackground = Color(0xFFFFFFFF),
    surface = Color(0xFF2C2C2C),
    onSurface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFF424242),
    onSurfaceVariant = Color(0xFF9E9E9E),
    inverseSurface = Color(0xFFF5F5F5),
    inverseOnSurface = Color(0xFF212121),
    error = Color(0xFFF44336),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFCDD2),
    onErrorContainer = Color(0xFF212121),
    outline = Color(0xFFBDBDBD),
    outlineVariant = Color(0xFF757575),
    scrim = Color(0x99000000),
)

private val ChatWaifuLightTheme = lightColorScheme(
    primary = Color(0xFF2196F3),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF64B5F6),
    onPrimaryContainer = Color(0xFF212121),
    inversePrimary = Color(0xFF2196F3),
    secondary = Color(0xFFFFC0CB),
    onSecondary = Color(0xFF212121),
    secondaryContainer = Color(0xFFFFE1E6),
    onSecondaryContainer = Color(0xFF212121),
    tertiary = Color(0xFF757575),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFA4A4A4),
    onTertiaryContainer = Color(0xFFFFFFFF),
//    background = Color(0xBBFFE1E6),
    background = Color(0xFFF5F5F5),
    onBackground = Color(0xFF212121),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF212121),
    surfaceVariant = Color(0xFFF5F5F5),
    onSurfaceVariant = Color(0xFF757575),
    inverseSurface = Color(0xFF424242),
    inverseOnSurface = Color(0xFFFFFFFF),
    error = Color(0xFFF44336),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFCDD2),
    onErrorContainer = Color(0xFF212121),
    outline = Color(0xFFBDBDBD),
    outlineVariant = Color(0xFF757575),
    scrim = Color(0x99000000)
)
