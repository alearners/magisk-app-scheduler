package com.appscheduler.lsposed.ui

import android.content.Context
import java.util.Calendar
import kotlin.math.pow
import kotlin.math.roundToInt

/** 主题模式：跟随系统、固定浅色、固定深色 */
enum class ThemeMode(val storage: String, val label: String) {
    SYSTEM("system", "跟随系统"),
    LIGHT("light", "浅色"),
    DARK("dark", "深色");

    companion object {
        fun from(value: String?): ThemeMode =
            entries.firstOrNull { it.storage == value } ?: SYSTEM
    }
}

/** 背景类型：无、内置渐变、自选图片 */
enum class BackgroundStyle(val storage: String, val label: String) {
    NONE("none", "无背景"),
    MIST("mist", "晨雾"),
    AURORA("aurora", "极光"),
    DUSK("dusk", "暮色"),
    FOREST("forest", "山林"),
    INK("ink", "石墨"),
    IMAGE("image", "自选图片");

    companion object {
        fun from(value: String?): BackgroundStyle =
            entries.firstOrNull { it.storage == value } ?: NONE
    }
}

/** 一组可直接选用的按钮色；名称只用于界面，不写进配置 */
data class AccentPreset(val name: String, val argb: Int)

object ThemePalette {
    val presets = listOf(
        AccentPreset("青蓝", 0xFF1FA0CC.toInt()),
        AccentPreset("松绿", 0xFF2E7D5B.toInt()),
        AccentPreset("琥珀", 0xFFB26A00.toInt()),
        AccentPreset("绯红", 0xFFB3261E.toInt()),
        AccentPreset("靛蓝", 0xFF4F5D95.toInt()),
        AccentPreset("紫藤", 0xFF7A5C9E.toInt()),
        AccentPreset("石墨", 0xFF4B5563.toInt()),
        AccentPreset("暖沙", 0xFF9A6B48.toInt())
    )

    const val DEFAULT = 0xFF1FA0CC.toInt()
    const val DEFAULT_NIGHT = 0xFF5C7CFA.toInt()
}

/**
 * 主题设置。全部字段只保存本机；自选图片仅保存内容 URI，不复制文件。
 * 默认值保持原有青蓝配色，升级后不会无故改变界面。
 */
data class ThemeSettings(
    val mode: ThemeMode = ThemeMode.SYSTEM,
    val accentColor: Int = ThemePalette.DEFAULT,
    val backgroundStyle: BackgroundStyle = BackgroundStyle.NONE,
    val backgroundImageUri: String? = null,
    val backgroundAlpha: Float = 1f,
    val backgroundScrim: Float = 0.18f,
    val buttonAlpha: Float = 1f,
    val timeAccentEnabled: Boolean = false,
    val dayAccent: Int = ThemePalette.DEFAULT,
    val nightAccent: Int = ThemePalette.DEFAULT_NIGHT,
    val dayStart: String = "07:00",
    val nightStart: String = "19:00"
) {
    fun darkTheme(systemDark: Boolean): Boolean = when (mode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    /** 当前应采用的按钮色；未启用按时段切换时，始终为 accentColor */
    fun effectiveAccent(nowMinutes: Int): Int {
        if (!timeAccentEnabled) return accentColor
        return if (ThemeMath.isDaytime(nowMinutes, dayStart, nightStart)) dayAccent else nightAccent
    }

    fun phaseLabel(nowMinutes: Int): String {
        if (!timeAccentEnabled) return "固定配色"
        return if (ThemeMath.isDaytime(nowMinutes, dayStart, nightStart)) "日间配色" else "夜间配色"
    }

    /** 用于界面摘要的一行说明 */
    fun summary(nowMinutes: Int): String {
        val background = if (backgroundStyle == BackgroundStyle.IMAGE) "自选图片" else backgroundStyle.label
        return "${mode.label} · ${phaseLabel(nowMinutes)} · 背景：$background · 按钮 ${(buttonAlpha * 100).roundToInt()}%"
    }

    /** 配置备份不含图片 URI：换机后 URI 无效，导入时退回无背景 */
    fun forTransfer(): ThemeSettings =
        if (backgroundStyle == BackgroundStyle.IMAGE) {
            copy(backgroundStyle = BackgroundStyle.NONE, backgroundImageUri = null)
        } else {
            copy(backgroundImageUri = null)
        }

    companion object {
        private const val PREFS = "app_theme"
        private const val KEY_MODE = "mode"
        private const val KEY_ACCENT = "accent"
        private const val KEY_BACKGROUND = "background"
        private const val KEY_BACKGROUND_URI = "background_uri"
        private const val KEY_BACKGROUND_ALPHA = "background_alpha"
        private const val KEY_BACKGROUND_SCRIM = "background_scrim"
        private const val KEY_BUTTON_ALPHA = "button_alpha"
        private const val KEY_TIME_ENABLED = "time_enabled"
        private const val KEY_DAY_ACCENT = "day_accent"
        private const val KEY_NIGHT_ACCENT = "night_accent"
        private const val KEY_DAY_START = "day_start"
        private const val KEY_NIGHT_START = "night_start"

        fun load(context: Context): ThemeSettings {
            val sp = context.applicationContext
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val uri = sp.getString(KEY_BACKGROUND_URI, null)?.takeIf { it.isNotBlank() }
            val style = BackgroundStyle.from(sp.getString(KEY_BACKGROUND, null))
            return ThemeSettings(
                mode = ThemeMode.from(sp.getString(KEY_MODE, null)),
                accentColor = ThemeMath.opaque(sp.getInt(KEY_ACCENT, ThemePalette.DEFAULT)),
                backgroundStyle = if (style == BackgroundStyle.IMAGE && uri == null) {
                    BackgroundStyle.NONE
                } else {
                    style
                },
                backgroundImageUri = uri,
                backgroundAlpha = sp.getFloat(KEY_BACKGROUND_ALPHA, 1f).coerceIn(0f, 1f),
                backgroundScrim = sp.getFloat(KEY_BACKGROUND_SCRIM, 0.18f).coerceIn(0f, 0.8f),
                buttonAlpha = sp.getFloat(KEY_BUTTON_ALPHA, 1f).coerceIn(0.2f, 1f),
                timeAccentEnabled = sp.getBoolean(KEY_TIME_ENABLED, false),
                dayAccent = ThemeMath.opaque(sp.getInt(KEY_DAY_ACCENT, ThemePalette.DEFAULT)),
                nightAccent = ThemeMath.opaque(sp.getInt(KEY_NIGHT_ACCENT, ThemePalette.DEFAULT_NIGHT)),
                dayStart = sp.getString(KEY_DAY_START, "07:00") ?: "07:00",
                nightStart = sp.getString(KEY_NIGHT_START, "19:00") ?: "19:00"
            )
        }

        fun save(context: Context, settings: ThemeSettings) {
            val sp = context.applicationContext
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            sp.edit()
                .putString(KEY_MODE, settings.mode.storage)
                .putInt(KEY_ACCENT, ThemeMath.opaque(settings.accentColor))
                .putString(KEY_BACKGROUND, settings.backgroundStyle.storage)
                .putString(KEY_BACKGROUND_URI, settings.backgroundImageUri)
                .putFloat(KEY_BACKGROUND_ALPHA, settings.backgroundAlpha.coerceIn(0f, 1f))
                .putFloat(KEY_BACKGROUND_SCRIM, settings.backgroundScrim.coerceIn(0f, 0.8f))
                .putFloat(KEY_BUTTON_ALPHA, settings.buttonAlpha.coerceIn(0.2f, 1f))
                .putBoolean(KEY_TIME_ENABLED, settings.timeAccentEnabled)
                .putInt(KEY_DAY_ACCENT, ThemeMath.opaque(settings.dayAccent))
                .putInt(KEY_NIGHT_ACCENT, ThemeMath.opaque(settings.nightAccent))
                .putString(KEY_DAY_START, normalizeTime(settings.dayStart, "07:00"))
                .putString(KEY_NIGHT_START, normalizeTime(settings.nightStart, "19:00"))
                .apply()
        }

        private fun normalizeTime(value: String, fallback: String): String =
            if (ThemeMath.parseTimeToMinutes(value) != null) value else fallback

        fun saveImageUri(context: Context, uri: String?) {
            val sp = context.applicationContext
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            sp.edit().putString(KEY_BACKGROUND_URI, uri).apply()
        }

        fun reset(context: Context) {
            context.applicationContext
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().clear().apply()
        }
    }
}

/** 主题相关的纯计算：不依赖 Android，便于单元测试 */
object ThemeMath {

    fun opaque(argb: Int): Int = argb or (0xFF shl 24)

    fun isDaytime(nowMinutes: Int, dayStart: String, nightStart: String): Boolean {
        val start = parseTimeToMinutes(dayStart) ?: 7 * 60
        val end = parseTimeToMinutes(nightStart) ?: 19 * 60
        val now = nowMinutes.coerceIn(0, 1439)
        if (start == end) return true
        return if (start < end) {
            now in start until end
        } else {
            now >= start || now < end
        }
    }

    fun parseTimeToMinutes(text: String): Int? {
        val match = Regex("^(\\d{1,2}):(\\d{2})$").matchEntire(text.trim()) ?: return null
        val hour = match.groupValues[1].toIntOrNull() ?: return null
        val minute = match.groupValues[2].toIntOrNull() ?: return null
        if (hour !in 0..23 || minute !in 0..59) return null
        return hour * 60 + minute
    }

    fun formatTime(minutes: Int): String {
        val safe = minutes.coerceIn(0, 1439)
        return "%02d:%02d".format(safe / 60, safe % 60)
    }

    /** 将带透明度的前景色合成到不透明底色上，得到实际显示颜色 */
    fun blend(foreground: Int, background: Int, alpha: Float): Int {
        val a = alpha.coerceIn(0f, 1f)
        val fgR = (foreground shr 16) and 0xFF
        val fgG = (foreground shr 8) and 0xFF
        val fgB = foreground and 0xFF
        val bgR = (background shr 16) and 0xFF
        val bgG = (background shr 8) and 0xFF
        val bgB = background and 0xFF
        return opaque(
            (((fgR * a + bgR * (1f - a)).roundToInt()) shl 16) or
                (((fgG * a + bgG * (1f - a)).roundToInt()) shl 8) or
                ((fgB * a + bgB * (1f - a)).roundToInt())
        )
    }

    private fun linear(channel: Int): Double {
        val c = channel.coerceIn(0, 255) / 255.0
        return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
    }

    fun luminance(argb: Int): Double {
        val r = linear((argb shr 16) and 0xFF)
        val g = linear((argb shr 8) and 0xFF)
        val b = linear(argb and 0xFF)
        return 0.2126 * r + 0.7152 * g + 0.0722 * b
    }

    fun contrastRatio(first: Int, second: Int): Double {
        val a = luminance(first)
        val b = luminance(second)
        val light = if (a > b) a else b
        val dark = if (a > b) b else a
        return (light + 0.05) / (dark + 0.05)
    }

    /** 在黑白之间选择对比度更高的一种作为按钮文字色 */
    fun contrastOn(argb: Int, fallbackDark: Int = 0xFF171C1F.toInt()): Int {
        val white = 0xFFFFFFFF.toInt()
        val black = opaque(fallbackDark)
        return if (contrastRatio(white, argb) >= contrastRatio(black, argb)) white else black
    }

    /** 半透明按钮色合成到实际底色后，再决定文字色，避免低透明度时文字看不清 */
    fun contrastOnComposite(accent: Int, surface: Int, alpha: Float): Int =
        contrastOn(blend(accent, surface, alpha))
}

/** 当前本地时间的「时:分」折算为当日分钟数 */
fun currentMinuteOfDay(): Int {
    val calendar = Calendar.getInstance()
    return calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)
}
