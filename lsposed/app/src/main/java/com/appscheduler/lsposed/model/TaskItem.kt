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
    /** 解锁后启动该计划里的应用时，是否先强制停止：true 冷启动，false 直接启动 / 切回已运行的进程 */
    var forceColdStart: Boolean = true,
    /** 用什么命令把应用拉起来，见 LAUNCH_* 常量；每个计划单独选择 */
    var launchMode: String = LAUNCH_COMPONENT,
    var lastRunAt: Long = 0L,
    var lastResult: String = ""
) {

    /** 启动方式的短名称，给列表和日志用 */
    fun describeLaunch(): String = when (launchMode) {
        LAUNCH_WAIT -> "等待界面就绪后打开"
        LAUNCH_MONKEY -> "模拟点击打开"
        LAUNCH_DESKTOP -> "返回桌面后打开"
        else -> "直接打开"
    }

    /** 供界面与日志使用的简短标题 */
    fun title(): String {
        if (name.isNotBlank()) return name
        val first = packages.firstOrNull() ?: return "尚未选择应用"
        return if (packages.size <= 1) first else "「$first」等 ${packages.size} 个应用"
    }

    fun describeSchedule(): String {
        if (scheduleType == TYPE_INTERVAL) {
            val base = if (intervalMinutes % 60 == 0 && intervalMinutes >= 60)
                "每 ${intervalMinutes / 60} 小时执行一次" else "每 $intervalMinutes 分钟执行一次"
            return if (maxRuns > 0) "$base（共 $maxRuns 次，剩余 $remainingRuns 次）" else base
        }
        if (times.isEmpty()) {
            return "尚未设定时间"
        }
        if (scheduleType == TYPE_WEEKLY) {
            val days = if (weekdays.isEmpty()) "每天"
            else weekdays.sorted().joinToString("、") { weekdayName(it) }
            return "$days ${times.first()}"
        }
        return "每天 " + times.joinToString("、")
    }

    fun describeConditions(): String {
        val list = mutableListOf<String>()
        if (onlyCharging) list.add("充电中")
        if (onlyWifi) list.add("已连接 WiFi")
        if (onlyScreenOff) list.add("屏幕已熄灭")
        return if (list.isEmpty()) "无附加条件，按时执行" else list.joinToString("，且")
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
        o.put("forceColdStart", forceColdStart)
        o.put("launchMode", launchMode)
        o.put("lastRunAt", lastRunAt)
        o.put("lastResult", lastResult)
        return o
    }

    companion object {
        const val TYPE_TIMES = "TIMES"
        const val TYPE_WEEKLY = "WEEKLY"
        const val TYPE_INTERVAL = "INTERVAL"

        /** 默认保持原有行为：先强制停止再启动，保证是冷启动 */
        const val DEFAULT_FORCE_COLD_START = true

        // ---- 启动方式 ----
        /** am start -n <组件>：直接打开解析出的启动页，不注入点击，也不改系统设置 */
        const val LAUNCH_COMPONENT = "COMPONENT"
        /** am start -W -n <组件>：同上，但等界面真的出现再继续，多花 1–3 秒 */
        const val LAUNCH_WAIT = "WAIT"
        /** monkey：模拟一次点击启动，兼容性最好，但会注入触摸/导航事件 */
        const val LAUNCH_MONKEY = "MONKEY"
        /** 先回桌面（input keyevent 3）再 am start：模拟从桌面图标点开 */
        const val LAUNCH_DESKTOP = "DESKTOP"

        const val DEFAULT_LAUNCH_MODE = LAUNCH_COMPONENT

        private val LAUNCH_MODES =
            setOf(LAUNCH_COMPONENT, LAUNCH_WAIT, LAUNCH_MONKEY, LAUNCH_DESKTOP)

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
            if (tm != null) {
                // 空数组也要照实还原：用户把时间点全删掉时，不应悄悄退回默认 08:00
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
            // 旧配置没有这个字段：默认保持原有行为「先强制停止再启动」
            task.forceColdStart = o.optBoolean("forceColdStart", DEFAULT_FORCE_COLD_START)
            // 旧配置没有这个字段，或写进了未知值：一律按默认的「指定组件启动」
            val mode = o.optString("launchMode", DEFAULT_LAUNCH_MODE)
            task.launchMode = if (mode in LAUNCH_MODES) mode else DEFAULT_LAUNCH_MODE
            task.lastRunAt = o.optLong("lastRunAt", 0L)
            task.lastResult = o.optString("lastResult", "")
            return task
        }
    }
}
