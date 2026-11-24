package com.appscheduler.lsposed.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext

/**
 * 配色参考 Sesame-AG 的 Material3 主题：青蓝主色、浅蓝容器、淡蓝灰背景。
 *
 * 传入 [settings] 时改用用户选择的按钮色、浅色 / 深色模式；未传入时保留原有
 * 动态取色行为，供截图测试与旧调用方使用。按钮透明度会合成到实际底色上，
 * 再据此选择文字色，避免按钮变淡后文字不可读。
 */
private val LightScheme = lightColorScheme(
    primary = Color(0xFF1FA0CC),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFBBEAFF),
    onPrimaryContainer = Color(0xFF004D62),
    secondary = Color(0xFF4C616B),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFCFE6F1),
    onSecondaryContainer = Color(0xFF354A53),
    tertiary = Color(0xFF5C5B7E),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFE2DFFF),
    onTertiaryContainer = Color(0xFF444465),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF93000A),
    background = Color(0xFFF5FAFD),
    onBackground = Color(0xFF171C1F),
    surface = Color(0xFFF5FAFD),
    onSurface = Color(0xFF171C1F),
    surfaceVariant = Color(0xFFDCE4E8),
    onSurfaceVariant = Color(0xFF40484C),
    outline = Color(0xFF70787D),
    outlineVariant = Color(0xFFC0C8CC)
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFF87D3EE),
    onPrimary = Color(0xFF003543),
    primaryContainer = Color(0xFF004D62),
    onPrimaryContainer = Color(0xFFBBEAFF),
    secondary = Color(0xFFB3CBD5),
    onSecondary = Color(0xFF1E333C),
    secondaryContainer = Color(0xFF354A53),
    onSecondaryContainer = Color(0xFFCFE6F1),
    tertiary = Color(0xFFC5C3EB),
    onTertiary = Color(0xFF2D2D4D),
    tertiaryContainer = Color(0xFF444465),
    onTertiaryContainer = Color(0xFFE2DFFF),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF0E1417),
    onBackground = Color(0xFFDEE3E6),
    surface = Color(0xFF0E1417),
    onSurface = Color(0xFFDEE3E6),
    surfaceVariant = Color(0xFF40484C),
    onSurfaceVariant = Color(0xFFC0C8CC),
    outline = Color(0xFF8A9296),
    outlineVariant = Color(0xFF40484C)
)

@Composable
fun AppSchedulerTheme(
    settings: ThemeSettings? = null,
    nowMinutes: Int = currentMinuteOfDay(),
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colorScheme = if (settings != null) {
        val effectiveDark = settings.darkTheme(darkTheme)
        val base = if (effectiveDark) DarkScheme else LightScheme
        val accent = settings.effectiveAccent(nowMinutes)
        val alpha = settings.buttonAlpha.coerceIn(0.2f, 1f)
        val surface = base.surface
        val surfaceArgb = surface.toArgb()
        val primaryContainer = Color(
            ThemeMath.blend(accent, surfaceArgb, if (effectiveDark) 0.32f else 0.18f)
        )
        base.copy(
            primary = Color(accent).copy(alpha = alpha),
            onPrimary = Color(ThemeMath.contrastOnComposite(accent, surfaceArgb, alpha)),
            primaryContainer = primaryContainer,
            onPrimaryContainer = Color(ThemeMath.contrastOn(primaryContainer.toArgb()))
        )
    } else {
        when {
            dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
                if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)

            darkTheme -> DarkScheme
            else -> LightScheme
        }
    }
    MaterialTheme(colorScheme = colorScheme, content = content)
}

/** 当前主题下按钮色在实际底色上的对比度，用于主题页提示 */
fun buttonContrastRatio(settings: ThemeSettings, nowMinutes: Int, darkTheme: Boolean): Double {
    val surface = (if (settings.darkTheme(darkTheme)) DarkScheme.surface else LightScheme.surface).toArgb()
    val accent = settings.effectiveAccent(nowMinutes)
    return ThemeMath.contrastRatio(
        ThemeMath.blend(accent, surface, settings.buttonAlpha.coerceIn(0.2f, 1f)),
        surface
    )
}
