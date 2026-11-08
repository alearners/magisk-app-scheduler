package com.appscheduler.lsposed.schedule

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.appscheduler.lsposed.Config
import com.appscheduler.lsposed.model.TaskItem
import com.appscheduler.lsposed.store.TaskStore
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * 每个计划独立排一个闹钟：
 * - TIMES：每天多个时间点，取最近一个
 * - WEEKLY：指定星期 + 一个时间点
 * - INTERVAL：按间隔循环，可设置总次数
 */
object Scheduler {

    const val ACTION_RUN_TASK = "com.appscheduler.action.RUN_TASK"
    const val EXTRA_TASK_ID = "task_id"

    fun pendingIntent(context: Context, taskId: Long): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = ACTION_RUN_TASK
            putExtra(EXTRA_TASK_ID, taskId)
        }
        val requestCode = 3000 + (taskId % 900L).toInt()
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun cancel(context: Context, taskId: Long) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        am.cancel(pendingIntent(context, taskId))
    }

    fun rescheduleAll(context: Context) {
        TaskStore.all(context).forEach { schedule(context, it) }
    }

    /** 已启用则排下一次，否则撤销闹钟。 */
    fun schedule(context: Context, task: TaskItem) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val masterEnabled = Config.get(context).enabled()
        if (!masterEnabled || !task.enabled || task.packages.isEmpty()) {
            am.cancel(pendingIntent(context, task.id))
            return
        }
        val next = nextTrigger(task, System.currentTimeMillis())
        if (next == null) {
            am.cancel(pendingIntent(context, task.id))
            return
        }
        val pi = pendingIntent(context, task.id)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pi)
            } else {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pi)
            }
        } catch (e: SecurityException) {
            am.set(AlarmManager.RTC_WAKEUP, next, pi)
        }
    }

    /**
     * @return 下一次触发时间戳（毫秒），无有效配置返回 null。
     */
    fun nextTrigger(task: TaskItem, from: Long): Long? = when (task.scheduleType) {
        TaskItem.TYPE_INTERVAL -> nextInterval(task, from)
        TaskItem.TYPE_WEEKLY -> nextByClock(from, task.times.take(1), task.weekdays)
        else -> nextByClock(from, task.times, emptySet())
    }

    private fun nextInterval(task: TaskItem, from: Long): Long? {
        val step = task.intervalMinutes.coerceAtLeast(1) * 60_000L
        if (task.lastRunAt <= 0L) {
            return from + step
        }
        var next = task.lastRunAt + step
        var guard = 0
        while (next <= from && guard < 10000) {
            next += step
            guard++
        }
        return next
    }

    private fun nextByClock(from: Long, times: List<String>, days: Set<Int>): Long? {
        val valid = times.mapNotNull { parseHm(it) }
        if (valid.isEmpty()) {
            return null
        }
        for (offset in 0..7) {
            val base = Calendar.getInstance()
            base.timeInMillis = from
            base.add(Calendar.DAY_OF_YEAR, offset)
            val dayOfWeek = isoWeekday(base)
            if (days.isNotEmpty() && !days.contains(dayOfWeek)) {
                continue
            }
            for ((hour, minute) in valid) {
                val cal = Calendar.getInstance()
                cal.timeInMillis = from
                cal.add(Calendar.DAY_OF_YEAR, offset)
                cal.set(Calendar.HOUR_OF_DAY, hour)
                cal.set(Calendar.MINUTE, minute)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                val t = cal.timeInMillis
                if (t > from + 3000L) {
                    return t
                }
            }
        }
        return null
    }

    /** Calendar 的星期：1=周日，这里换算成 1=周一 … 7=周日 */
    private fun isoWeekday(cal: Calendar): Int {
        val value = cal.get(Calendar.DAY_OF_WEEK)
        return if (value == Calendar.SUNDAY) 7 else value - 1
    }

    fun parseHm(text: String): Pair<Int, Int>? {
        val item = text.trim()
        val sep = item.indexOf(':')
        if (sep <= 0 || sep >= item.length - 1) {
            return null
        }
        return try {
            val hour = item.substring(0, sep).trim().toInt()
            val minute = item.substring(sep + 1).trim().toInt()
            if (hour !in 0..23 || minute !in 0..59) null else hour to minute
        } catch (e: NumberFormatException) {
            null
        }
    }

    fun describeNext(task: TaskItem, from: Long = System.currentTimeMillis()): String {
        val next = nextTrigger(task, from) ?: return "无有效时间点"
        return SimpleDateFormat("MM-dd HH:mm", Locale.CHINA).format(Date(next))
    }

    /** 所有启用计划里最近的一次触发 */
    fun soonest(context: Context): Pair<TaskItem, Long>? {
        var best: Pair<TaskItem, Long>? = null
        TaskStore.all(context).forEach { task ->
            if (!task.enabled || task.packages.isEmpty()) return@forEach
            val next = nextTrigger(task, System.currentTimeMillis()) ?: return@forEach
            val current = best
            if (current == null || next < current.second) {
                best = task to next
            }
        }
        return best
    }
}
