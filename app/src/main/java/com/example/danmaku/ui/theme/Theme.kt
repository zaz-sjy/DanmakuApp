package com.example.danmaku.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * 只用浅色一套配色，背景强制纯白（不跟随系统深色模式）。
 */
private val DanmakuLightColors = lightColorScheme(
    primary = Color(0xFF1E6DF2),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE3EDFF),
    onPrimaryContainer = Color(0xFF0A2B62),
    secondaryContainer = Color(0xFFEDF1F7),
    onSecondaryContainer = Color(0xFF2A3140),
    background = Color.White,
    onBackground = Color(0xFF111111),
    surface = Color.White,
    onSurface = Color(0xFF111111),
    surfaceVariant = Color(0xFFF2F4F8),
    onSurfaceVariant = Color(0xFF44474E),
    outline = Color(0xFFC7CBD4),
    outlineVariant = Color(0xFFE2E5EC)
)

@Composable
fun DanmakuTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DanmakuLightColors,
        content = content
    )
}
