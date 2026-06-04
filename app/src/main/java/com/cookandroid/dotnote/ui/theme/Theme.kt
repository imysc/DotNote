package com.cookandroid.dotnote.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// 베이지 노트 테마: 따뜻한 종이 위에 잉크로 쓴 듯한 라이트 컬러 스킴
private val NoteColorScheme = lightColorScheme(
    primary = NoteBrown,
    onPrimary = OnNoteBrown,
    primaryContainer = NoteBrownContainer,
    onPrimaryContainer = OnNoteBrownContainer,

    secondary = NoteOlive,
    onSecondary = OnNoteOlive,
    secondaryContainer = NoteOliveContainer,
    onSecondaryContainer = OnNoteOliveContainer,

    tertiary = NoteTerracotta,

    background = NoteBackground,
    onBackground = OnNoteBackground,

    surface = NoteSurface,
    onSurface = OnNoteSurface,
    surfaceVariant = NoteSurfaceVariant,
    onSurfaceVariant = OnNoteSurfaceVariant,

    outline = NoteOutline,
    outlineVariant = NoteOutlineVariant,

    error = NoteError,
    onError = OnNoteError,
)

@Composable
fun DotNoteTheme(
    content: @Composable () -> Unit
) {
    val colorScheme = NoteColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            // 상태바를 배경색과 통일하여 자연스러운 몰입감 제공
            window.statusBarColor = colorScheme.background.toArgb()
            // 밝은 테마이므로 상태바 아이콘을 어둡게 설정
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = true
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}