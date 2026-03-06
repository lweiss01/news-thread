package com.newsthread.app.presentation.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

data class NewsIconSizes(
    val small: Dp = 18.dp,
    val standard: Dp = 24.dp,
    val large: Dp = 32.dp,
    val hero: Dp = 64.dp
)

val LocalIconSizes = staticCompositionLocalOf { NewsIconSizes() }
