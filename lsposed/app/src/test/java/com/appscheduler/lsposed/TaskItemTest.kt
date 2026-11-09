package com.appscheduler.lsposed

import com.appscheduler.lsposed.model.TaskItem
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 计划数据的纯 JVM 回归测试：
 * 这些是「存了又读」的边界，出问题会让用户的计划在重启后悄悄变形，
 * 所以用真实 org.json 走一遍序列化往返，而不是只靠界面肉眼看。
 */
class TaskItemTest {

    @Test
    fun emptyTimesArrayStaysEmptyAfterRoundTrip() {
        val task = TaskItem(
            id = 7L,
            name = "工作日",
            packages = mutableListOf("com.example.one"),
            times = mutableListOf()
        )
        val restored = TaskItem.fromJson(JSONObject(task.toJson().toString()))
        assertTrue("空时间点不应退回默认 08:00", restored.times.isEmpty())
        assertEquals("未设定时间点", restored.describeSchedule())
    }

    @Test
    fun remainingRunsAndEnabledSurviveRoundTrip() {
        val task = TaskItem(
            id = 3L,
            name = "间隔跑",
            packages = mutableListOf("com.example.one", "com.example.two"),
            scheduleType = TaskItem.TYPE_INTERVAL,
            intervalMinutes = 90,
            maxRuns = 5,
            remainingRuns = 3
        )
        val restored = TaskItem.fromJson(JSONObject(task.toJson().toString()))
        assertEquals(3, restored.remainingRuns)
        assertEquals(5, restored.maxRuns)
        assertTrue(restored.enabled)
        assertEquals("每 90 分钟（共 5 次，剩 3 次）", restored.describeSchedule())
    }

    @Test
    fun finishedPlanKeepsDisabledState() {
        val task = TaskItem(id = 11L, maxRuns = 2, remainingRuns = 0, enabled = false)
        val restored = TaskItem.fromJson(JSONObject(task.toJson().toString()))
        assertFalse("已跑完的计划重启后不能自己变回启用", restored.enabled)
        assertEquals(0, restored.remainingRuns)
    }

    @Test
    fun missingEnabledFieldDefaultsToEnabled() {
        val json = JSONObject().apply {
            put("id", 1L)
            put("name", "老数据")
            put("packages", JSONArray(listOf("com.example.one")))
            put("times", JSONArray(listOf("07:30")))
        }
        val restored = TaskItem.fromJson(json)
        assertTrue(restored.enabled)
        assertEquals(listOf("07:30"), restored.times)
    }

    @Test
    fun weeklyScheduleDescribesSelectedDays() {
        val task = TaskItem(
            scheduleType = TaskItem.TYPE_WEEKLY,
            times = mutableListOf("21:30"),
            weekdays = mutableSetOf(3, 1)
        )
        assertEquals("每周一、周三 21:30", task.describeSchedule())
    }

    @Test
    fun weekdayNumberOutsideRangeIsIgnored() {
        val json = JSONObject().apply {
            put("times", JSONArray(listOf("08:00")))
            put("weekdays", JSONArray(listOf(0, 1, 8)))
        }
        val restored = TaskItem.fromJson(json)
        assertEquals(setOf(1), restored.weekdays.toSet())
    }

    @Test
    fun blankPackageEntriesAreDropped() {
        val json = JSONObject().apply {
            put("packages", JSONArray(listOf("com.example.one", "", "  ")))
        }
        val restored = TaskItem.fromJson(json)
        assertEquals(listOf("com.example.one"), restored.packages)
    }
}
