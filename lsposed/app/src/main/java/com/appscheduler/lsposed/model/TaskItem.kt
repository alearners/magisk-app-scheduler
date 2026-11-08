package com.appscheduler.lsposed.model

import org.json.JSONArray
import org.json.JSONObject

/**
 * 一个「启动计划」：
 * - 可同时启动多个 App（packages）
 * - 三种定时方式：每天多个时间点 / 每周指定星期 / 按固定间隔循环
 * - 可选启动条件：仅充电、仅 WiFi、仅屏幕熄灭
 * - maxRuns > 0 表示总共只跑 N 次，跑完自动停用
 */
data class TaskItem(
    var id: Long = 0L,
    var name: String = "",
    var packages: MutableList<String> = mutableListOf(),
    var scheduleType: String = TYPE_TIMES,
    var times: MutableList<String> = mutableListOf("08:00"),
    var weekdays: MutableSet<Int> = mutableSetOf(),
    var intervalMinutes: Int = 120,
    var maxRuns: Int = 0,
    var remainingRuns: Int = 0,
    var onlyCharging: Boolean = false,
    var onlyWifi: Boolean = false,
    var onlyScreenOff: Boolean = false,
    var enabled: Boolean = true,
    var lastRunAt: Long = 0L,
    var lastResult: String = ""
) {

    /** 供界面与日志使用的简短标题 */
    fun title(): String {
        if (name.isNotBlank()) return name
        val first = packages.firstOrNull() ?: return "未选择应用"
        return if (packages.size <= 1) first else "$first 等 ${packages.size} 个"
    }

    fun describeSchedule(): String = when (scheduleType) {
        TYPE_WEEKLY -> {
            val days = if (weekdays.isEmpty()) "每天"
            else "每" + weekdays.sorted().joinToString("、") { weekdayName(it) }
            "$days ${times.firstOrNull() ?: "08:00"}"
        }
        TYPE_INTERVAL -> {
            val base = if (intervalMinutes % 60 == 0 && intervalMinutes >= 60)
                "每 ${intervalMinutes / 60} 小时" else "每 $intervalMinutes 分钟"
            if (maxRuns > 0) "$base（共 $maxRuns 次，剩 $remainingRuns 次）" else base
        }
        else -> "每天 " + times.joinToString("、")
    }

    fun describeConditions(): String {
        val list = mutableListOf<String>()
        if (onlyCharging) list.add("充电中")
        if (onlyWifi) list.add("已连 WiFi")
        if (onlyScreenOff) list.add("屏幕熄灭")
        return if (list.isEmpty()) "无限制" else list.joinToString(" 且 ")
    }

    fun toJson(): JSONObject {
        val o = JSONObject()
        o.put("id", id)
        o.put("name", name)
        o.put("packages", JSONArray(packages))
        o.put("scheduleType", scheduleType)
        o.put("times", JSONArray(times))
        o.put("weekdays", JSONArray(weekdays.sorted()))
        o.put("intervalMinutes", intervalMinutes)
        o.put("maxRuns", maxRuns)
        o.put("remainingRuns", remainingRuns)
        o.put("onlyCharging", onlyCharging)
        o.put("onlyWifi", onlyWifi)
        o.put("onlyScreenOff", onlyScreenOff)
        o.put("enabled", enabled)
        o.put("lastRunAt", lastRunAt)
        o.put("lastResult", lastResult)
        return o
    }

    companion object {
        const val TYPE_TIMES = "TIMES"
        const val TYPE_WEEKLY = "WEEKLY"
        const val TYPE_INTERVAL = "INTERVAL"

        fun weekdayName(day: Int): String = when (day) {
            1 -> "周一"
            2 -> "周二"
            3 -> "周三"
            4 -> "周四"
            5 -> "周五"
            6 -> "周六"
            else -> "周日"
        }

        fun fromJson(o: JSONObject): TaskItem {
            val task = TaskItem()
            task.id = o.optLong("id", 0L)
            task.name = o.optString("name", "")
            val pkgs = o.optJSONArray("packages")
            if (pkgs != null) {
                for (i in 0 until pkgs.length()) {
                    val item = pkgs.optString(i, "")
                    if (item.isNotBlank()) task.packages.add(item)
                }
            }
            task.scheduleType = o.optString("scheduleType", TYPE_TIMES)
            val tm = o.optJSONArray("times")
            if (tm != null && tm.length() > 0) {
                task.times.clear()
                for (i in 0 until tm.length()) {
                    val item = tm.optString(i, "")
                    if (item.isNotBlank()) task.times.add(item)
                }
            }
            val wd = o.optJSONArray("weekdays")
            if (wd != null) {
                for (i in 0 until wd.length()) {
                    val day = wd.optInt(i, 0)
                    if (day in 1..7) task.weekdays.add(day)
                }
            }
            task.intervalMinutes = o.optInt("intervalMinutes", 120)
            task.maxRuns = o.optInt("maxRuns", 0)
            task.remainingRuns = o.optInt("remainingRuns", 0)
            task.onlyCharging = o.optBoolean("onlyCharging", false)
            task.onlyWifi = o.optBoolean("onlyWifi", false)
            task.onlyScreenOff = o.optBoolean("onlyScreenOff", false)
            task.enabled = o.optBoolean("enabled", true)
            task.lastRunAt = o.optLong("lastRunAt", 0L)
            task.lastResult = o.optString("lastResult", "")
            return task
        }
    }
}
