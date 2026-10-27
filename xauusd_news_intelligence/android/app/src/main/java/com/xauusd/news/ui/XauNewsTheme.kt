package com.xauusd.news.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val GoldNavy = Color(0xFF101820)
private val GoldSurface = Color(0xFF1B2732)
private val GoldAccent = Color(0xFFE5B85C)
private val GoldTeal = Color(0xFF62C7B4)

@Composable
fun XauNewsTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = GoldAccent,
            onPrimary = GoldNavy,
            secondary = GoldTeal,
            background = GoldNavy,
            surface = GoldSurface,
            onBackground = Color(0xFFF3F5F6),
            onSurface = Color(0xFFF3F5F6),
            error = Color(0xFFFF8A80),
        ),
        content = content,
    )
}

