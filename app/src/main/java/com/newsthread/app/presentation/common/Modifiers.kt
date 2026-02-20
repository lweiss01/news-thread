package com.newsthread.app.presentation.common

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.newsthread.app.presentation.theme.ProjectTheme

/**
 * Applies the "NewsThread Pulse" effect to a component.
 * - Reactive glow on press
 * - Subtle scale change
 * - Theme-aware border
 */
fun Modifier.pulseEffect(
    shape: Shape = RoundedCornerShape(12.dp),
    borderWidth: Dp = 1.dp,
    onClick: () -> Unit
): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1f,
        label = "pulseScale"
    )

    val borderColor = if (isPressed) {
        ProjectTheme.glow.neon // Glow active
    } else {
        ProjectTheme.glow.subtle // Subtle border idle
    }
    
    // We handle the glow brush/color separately. 
    // Since border takes a Brush or Color, we need a small helper or just use the Neon color for direct highlight.
    // Ideally glow.neon is a Brush, so:
    
    this
        .scale(scale)
        .border(borderWidth, borderColor, shape)
        .clickable(
            interactionSource = interactionSource,
            indication = null, // Disable ripple, use pulse instead
            onClick = onClick
        )
}
