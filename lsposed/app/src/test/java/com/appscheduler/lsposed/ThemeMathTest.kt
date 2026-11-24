package com.appscheduler.lsposed

import com.appscheduler.lsposed.ui.BackgroundStyle
import com.appscheduler.lsposed.ui.ThemeMath
import com.appscheduler.lsposed.ui.ThemeSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeMathTest {

    @Test
    fun parseTime_acceptsValidAndRejectsInvalid() {
        assertEquals(7 * 60, ThemeMath.parseTimeToMinutes("07:00"))
        assertEquals(23 * 60 + 59, ThemeMath.parseTimeToMinutes("23:59"))
        assertNull(ThemeMath.parseTimeToMinutes("24:00"))
        assertNull(ThemeMath.parseTimeToMinutes("7:0"))
        assertNull(ThemeMath.parseTimeToMinutes(""))
    }

    @Test
    fun daytime_normalRange() {
        assertFalse(ThemeMath.isDaytime(6 * 60 + 59, "07:00", "19:00"))
        assertTrue(ThemeMath.isDaytime(7 * 60, "07:00", "19:00"))
        assertTrue(ThemeMath.isDaytime(18 * 60 + 59, "07:00", "19:00"))
        assertFalse(ThemeMath.isDaytime(19 * 60, "07:00", "19:00"))
    }

    @Test
    fun daytime_crossMidnightRange() {
        assertTrue(ThemeMath.isDaytime(22 * 60, "21:00", "06:00"))
        assertTrue(ThemeMath.isDaytime(2 * 60, "21:00", "06:00"))
        assertFalse(ThemeMath.isDaytime(12 * 60, "21:00", "06:00"))
    }

    @Test
    fun daytime_equalBoundsTreatsAllDayAsDaytime() {
        assertTrue(ThemeMath.isDaytime(0, "08:00", "08:00"))
        assertTrue(ThemeMath.isDaytime(12 * 60, "08:00", "08:00"))
    }

    @Test
    fun effectiveAccent_switchesBySchedule() {
        val settings = ThemeSettings(
            timeAccentEnabled = true,
            dayAccent = 0xFF1FA0CC.toInt(),
            nightAccent = 0xFF5C7CFA.toInt(),
            dayStart = "07:00",
            nightStart = "19:00"
        )
        assertEquals(0xFF1FA0CC.toInt(), settings.effectiveAccent(8 * 60))
        assertEquals(0xFF5C7CFA.toInt(), settings.effectiveAccent(20 * 60))
        assertEquals("日间配色", settings.phaseLabel(8 * 60))
        assertEquals("夜间配色", settings.phaseLabel(20 * 60))
    }

    @Test
    fun fixedAccentIgnoresSchedule() {
        val settings = ThemeSettings(
            accentColor = 0xFFB3261E.toInt(),
            timeAccentEnabled = false
        )
        assertEquals(0xFFB3261E.toInt(), settings.effectiveAccent(23 * 60))
        assertEquals("固定配色", settings.phaseLabel(23 * 60))
    }

    @Test
    fun blendHalfwayBetweenColors() {
        val blended = ThemeMath.blend(0xFF000000.toInt(), 0xFFFFFFFF.toInt(), 0.5f)
        assertEquals(0xFF808080.toInt(), blended)
    }

    @Test
    fun contrastOnPicksReadableText() {
        val accent = 0xFF1FA0CC.toInt()
        val onAccent = ThemeMath.contrastOn(accent)
        assertTrue(ThemeMath.contrastRatio(onAccent, accent) >= 4.5)
        assertEquals(0xFF171C1F.toInt(), ThemeMath.contrastOn(0xFFFFFFFF.toInt()))
    }

    @Test
    fun transferDropsImageButKeepsVisualSettings() {
        val settings = ThemeSettings(
            backgroundStyle = BackgroundStyle.IMAGE,
            backgroundImageUri = "content://demo/1",
            buttonAlpha = 0.6f
        )
        val transfer = settings.forTransfer()
        assertEquals(BackgroundStyle.NONE, transfer.backgroundStyle)
        assertNull(transfer.backgroundImageUri)
        assertEquals(0.6f, transfer.buttonAlpha)
    }
}
