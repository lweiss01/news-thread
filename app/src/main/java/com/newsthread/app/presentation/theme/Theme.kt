package com.newsthread.app.presentation.theme

import android.app.Activity
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
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// ---------------------------------------------------------------------------
// Glow Extension
// Neon glow now uses Amber rather than Cyan, anchoring the brand feel.
// ---------------------------------------------------------------------------
data class NewsGlow(
    val neon: Brush,   // Amber-tinted vertical glow for hero elements
    val subtle: Brush  // Slate-tinted subtle glow for cards
)

val LocalGlow = staticCompositionLocalOf<NewsGlow> { error("No Glow provided") }

// ---------------------------------------------------------------------------
// Bias Extension
// Carries spectrum gradient + perspective label colors.
// These values are identical in light and dark — intentional, see Color.kt.
// ---------------------------------------------------------------------------
data class NewsBiasColors(
    val gradient: Brush,
    val unrated: Color,
    // Perspective section label colors, matched to spectrum endpoints.
    // Use these wherever you render "Left Perspective" / "Right Perspective" labels
    // so that the label color always agrees with the bar.
    val leftLabel: Color,
    val rightLabel: Color,
    // Discrete scale colors for heatmaps and charts (-2 to +2)
    val pointColors: Map<Int, Color>
)

val LocalBias = staticCompositionLocalOf<NewsBiasColors> { error("No Bias provided") }


// ---------------------------------------------------------------------------
// Material Color Schemes
// ---------------------------------------------------------------------------

private val DarkColorScheme = darkColorScheme(
    // Brand — Amber
    primary            = Amber500,
    onPrimary          = NewsOnPrimary,       // Slate900 — dark text on amber
    primaryContainer   = Slate800,            // Amber-tinted container not needed in dark
    onPrimaryContainer = Amber300,            // Amber300 for text inside containers

    // Secondary — Neutral slate (Violet is reserved for spectrum only)
    secondary          = Slate400,
    onSecondary        = Slate900,
    secondaryContainer = Slate700,
    onSecondaryContainer = Slate300,

    // Surfaces
    background         = NewsBackground,      // Slate950
    onBackground       = NewsOnSurface,       // Slate300
    surface            = NewsSurface,         // Slate900
    onSurface          = NewsOnSurface,       // Slate300
    surfaceVariant     = Slate800,
    onSurfaceVariant   = NewsOnSurfaceVariant, // Slate400

    // Chrome
    outline            = NewsOutline,         // Slate700
    error              = Color(0xFFEF4444),
    onError            = Color.White
)

private val LightColorScheme = lightColorScheme(
    // Brand — Amber
    primary            = Amber500,
    onPrimary          = Slate900,            // Dark text on amber button
    primaryContainer   = Amber200,            // Soft amber container fill
    onPrimaryContainer = Slate900,            // Dark text inside amber containers

    // Secondary — Neutral slate
    secondary          = Slate500,
    onSecondary        = Color.White,
    secondaryContainer = Slate100,
    onSecondaryContainer = Slate700,

    // Surfaces
    background         = Slate50,            // #F8FAFC near-white
    onBackground       = Slate900,
    surface            = Color.White,
    onSurface          = Slate900,
    surfaceVariant     = Slate100,
    onSurfaceVariant   = Slate600,

    // Chrome
    outline            = Slate200,
    error              = Color(0xFFDC2626),
    onError            = Color.White
)


// ---------------------------------------------------------------------------
// Theme Composable
// ---------------------------------------------------------------------------

@Composable
fun NewsThreadTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color disabled — Amber brand identity must be enforced.
    // dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    // Glow: Amber-tinted in both modes, adjusted for surface brightness
    val glow = if (darkTheme) {
        NewsGlow(
            neon   = Brush.verticalGradient(listOf(Amber500.copy(alpha = 0.5f), Color.Transparent)),
            subtle = Brush.verticalGradient(listOf(Slate700.copy(alpha = 0.3f), Color.Transparent))
        )
    } else {
        NewsGlow(
            neon   = Brush.verticalGradient(listOf(Amber400.copy(alpha = 0.35f), Color.Transparent)),
            subtle = Brush.verticalGradient(listOf(Slate200.copy(alpha = 0.5f), Color.Transparent))
        )
    }

    // Bias: Same spectrum in both modes — users never re-learn the bar
    val bias = NewsBiasColors(
        gradient   = Brush.horizontalGradient(listOf(BiasLeft, BiasCenter, BiasRight)),
        unrated    = BiasUnrated,
        leftLabel  = PerspectiveLeft,   // #60A5FA
        rightLabel = PerspectiveRight,   // #F87171
        pointColors = mapOf(
            -2 to Color(0xFF1D4ED8), // Far Left (Deep Blue)
            -1 to BiasLeft,          // Left (Soft Blue)
            0  to BiasCenter,        // Center (Violet)
            1  to BiasRight,         // Right (Soft Red)
            2  to Color(0xFFB91C1C)  // Far Right (Deep Red)
        )
    )

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    CompositionLocalProvider(
        LocalSpacing provides NewsSpacing(),
        LocalElevations provides NewsElevations(),
        LocalGlow    provides glow,
        LocalBias    provides bias
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography  = Typography,
            shapes      = Shapes,
            content     = content
        )
    }
}


// ---------------------------------------------------------------------------
// ProjectTheme Accessor
// Usage example:
//   val bias = ProjectTheme.bias
//   Text("Left Perspective", color = bias.leftLabel)
//   val linkColor = if (darkTheme) Amber300 else Amber600
// ---------------------------------------------------------------------------
object ProjectTheme {
    val spacing: NewsSpacing
        @Composable get() = LocalSpacing.current

    val elevation: NewsElevations
        @Composable get() = LocalElevations.current

    val glow: NewsGlow
        @Composable get() = LocalGlow.current

    val bias: NewsBiasColors
        @Composable get() = LocalBias.current
}
