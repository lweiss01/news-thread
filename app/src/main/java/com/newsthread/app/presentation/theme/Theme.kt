package com.newsthread.app.presentation.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// --- Extension Systems (Pulse Effects) ---

data class NewsGlow(
    val neon: Brush,
    val subtle: Brush
)

val LocalGlow = staticCompositionLocalOf<NewsGlow> { error("No Glow provided") }

data class NewsBiasColors(
    val gradient: Brush,
    val unrated: Color
)

val LocalBias = staticCompositionLocalOf<NewsBiasColors> { error("No Bias provided") }


// --- Material Theme Mapping ---

private val DarkColorScheme = darkColorScheme(
    primary = Cyan500,
    onPrimary = NewsOnPrimary,
    primaryContainer = Slate800,
    onPrimaryContainer = Cyan500,
    secondary = Violet500,
    onSecondary = Color.White,
    background = NewsBackground, // Slate950
    onBackground = NewsOnSurface, // Slate300
    surface = NewsSurface, // Slate900
    onSurface = NewsOnSurface,
    surfaceVariant = Slate800,
    onSurfaceVariant = NewsOnSurfaceVariant,
    outline = NewsOutline,
    error = Color(0xFFEF4444),
    onError = Color.White
)

private val LightColorScheme = lightColorScheme(
    primary = Cyan500, // Keep Cyan as brand identity
    onPrimary = Color.White,
    primaryContainer = Slate100,
    onPrimaryContainer = Slate900,
    secondary = Violet500,
    onSecondary = Color.White,
    background = Slate50, // Soft White
    onBackground = Slate900,
    surface = Color.White,
    onSurface = Slate900,
    surfaceVariant = Slate100,
    onSurfaceVariant = Slate600,
    outline = Slate200,
    error = Color(0xFFDC2626),
    onError = Color.White
)


@Composable
fun NewsThreadTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // We disable dynamic color for now to enforce our Brand Identity (Cyan/Slate)
    // dynamicColor: Boolean = true, 
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }
    
    // Define Extensions based on Theme
    val glow = if (darkTheme) {
        NewsGlow(
            neon = Brush.verticalGradient(listOf(Cyan500.copy(alpha=0.6f), Color.Transparent)),
            subtle = Brush.verticalGradient(listOf(Slate700.copy(alpha=0.3f), Color.Transparent))
        )
    } else {
        NewsGlow(
            neon = Brush.verticalGradient(listOf(Cyan500.copy(alpha=0.4f), Color.Transparent)),
            subtle = Brush.verticalGradient(listOf(Slate200.copy(alpha=0.5f), Color.Transparent))
        )
    }

    val bias = NewsBiasColors(
        gradient = Brush.horizontalGradient(listOf(BiasLeft, BiasCenter, BiasRight)),
        unrated = BiasUnrated
    )

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb() // Match background for seamless look
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    CompositionLocalProvider(
        LocalSpacing provides NewsSpacing(),
        LocalGlow provides glow,
        LocalBias provides bias
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}

// Accessor for easier usage
object ProjectTheme {
    val spacing: NewsSpacing
        @Composable
        get() = LocalSpacing.current
        
    val glow: NewsGlow
        @Composable
        get() = LocalGlow.current
        
    val bias: NewsBiasColors
        @Composable
        get() = LocalBias.current
}
