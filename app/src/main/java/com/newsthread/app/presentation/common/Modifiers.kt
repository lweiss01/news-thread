package com.newsthread.app.presentation.common

import android.os.Build
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
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
    shape: Shape? = null, // Fallback to theme default
    borderWidth: Dp = 1.dp,
    onClick: () -> Unit
): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val targetShape = shape ?: MaterialTheme.shapes.medium

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1f,
        label = "pulseScale"
    )

    val borderColor = if (isPressed) {
        ProjectTheme.glow.neon
    } else {
        ProjectTheme.glow.subtle
    }

    this
        .scale(scale)
        .border(borderWidth, borderColor, targetShape)
        .clickable(
            interactionSource = interactionSource,
            indication = null,
            role = Role.Button,
            onClick = onClick
        )
}

/**
 * Premium Glassmorphism effect.
 * Translucent, blurred background for premium surfaces.
 */
fun Modifier.glassBackground(
    shape: Shape? = null,
    alpha: Float = 0.85f
): Modifier = composed {
    val targetShape = shape ?: MaterialTheme.shapes.large

    this
        .background(
            color = MaterialTheme.colorScheme.surface.copy(alpha = alpha),
            shape = targetShape
        )
        .then(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Modifier.blur(16.dp)
            } else {
                Modifier
            }
        )
}

/**
 * Branded radial pulse shimmer for image loading.
 */
fun Modifier.radialPulseShimmer(): Modifier = composed {
    this.drawBehind {
        val center = androidx.compose.ui.geometry.Offset(size.width / 2, size.height / 2)
        // Static amber glow placeholder as requested by user (no pulse)
        val safeRadius = (size.width.coerceAtMost(size.height) / 2).coerceAtLeast(0.01f)

        drawCircle(
            brush = androidx.compose.ui.graphics.Brush.radialGradient(
                colors = listOf(
                    com.newsthread.app.presentation.theme.Amber500.copy(alpha = 0.2f),
                    androidx.compose.ui.graphics.Color.Transparent
                ),
                center = center,
                radius = safeRadius
            ),
            radius = safeRadius,
            center = center
        )
    }
}
