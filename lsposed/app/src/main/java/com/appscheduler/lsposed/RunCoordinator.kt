package com.appscheduler.lsposed

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.appscheduler.lsposed.conditions.ConditionChecker
import com.appscheduler.lsposed.model.TaskItem
import com.appscheduler.lsposed.schedule.Scheduler
import com.appscheduler.lsposed.schedule.UnlockReceiver
import com.appscheduler.lsposed.store.PendingLaunch
import com.appscheduler.lsposed.store.PendingStore
import com.appscheduler.lsposed.store.TaskStore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 定时任务的执行编排。被 RunService 的前台服务线程调用，也可以在接收器线程里跑（退化路径）。
 *
 * 关键点：自动解锁失败时**不再原地等待**——把「解锁后要启动的包」写进 PendingStore，
 * 由系统 USER_PRESENT 广播触发 {[UnlockReceiver]} 再拉起启动流程，另排一个超时闹钟负责收尾。
 * 这样任何广播接收器都不会被占用几十秒到半小时。
 */
object RunCoordinator {

    const val ACTION_MANUAL_TIMEOUT = "com.appscheduler.action.MANUAL_UNLOCK_TIMEOUT"
    private const val TIMEOUT_REQUEST_CODE = 4100

    /** 定时触发：条件检查 → 校验 → 解锁 → 启动 → 记账 → 排下一次 */
    fun executeTask(context: Context, taskId: Long) {
        val task = TaskStore.find(context, taskId)
        if (task == null) {
            Scheduler.rescheduleAll(context)
            return
        }
        if (!task.enabled) {
            Scheduler.cancel(context, task.id)
            return
        }
        if (!Config.get(context).enabled()) {
            // 总开关关掉后本不该再有闹钟；这里再挡一次，避免残留闹钟把 App 拉起来
            Scheduler.cancel(context, task.id)
            LogStore.append(context, "调度总开关已关闭，本次不执行「${task.title()}」\n")
            return
        }

        val log = StringBuilder()
        log.append("=== 定时触发 ${TaskRunner.stamp()}（${task.title()}） ===\n")

        if (!TaskRunner.suAvailable()) {
            log.append("未获得 root（su 不可用），本次跳过\n")
            LogStore.append(context, log.toString())
            TaskRunner.notifyFailure(context, log.toString())
            // 不能因为这一次拿不到 root 就让计划彻底停摆，仍然排下一次
            Scheduler.schedule(context, task)
            return
        }

        val reason = ConditionChecker.unfilledReason(context, task)
        if (reason != null) {
            log.append("启动条件未满足（$reason），本次跳过\n")
            LogStore.append(context, log.toString())
            Scheduler.schedule(context, task)
            return
        }

        val runnable = TaskRunner.validate(task.packages, log)
        if (runnable.isEmpty()) {
            log.append("没有任何可启动的包，放弃本次任务\n")
            LogStore.append(context, log.toString())
            TaskRunner.notifyFailure(context, log.toString())
            applyResult(context, task, false)
            return
        }

        val config = Config.get(context)
        if (TaskRunner.isLocked()) {
            log.append("锁屏状态: true\n")
            if (!TaskRunner.unlock(context, config, log)) {
                if (config.manualFallback()) {
                    val waitSec = config.manualWait()
                    val now = System.currentTimeMillis()
                    PendingStore.add(
                        context,
                        PendingLaunch(task.id, task.title(), runnable, now + waitSec * 1000L)
                    )
                    log.append("已排入待启动队列：等待你手动解锁后自动启动，最长 ")
                        .append(waitSec).append(" 秒\n")
                    LogStore.append(context, log.toString())
                    TaskRunner.notifyManualUnlock(context, waitSec)
                    scheduleTimeout(context, waitSec)
                    // 挂起等待用户解锁的同时，把计划的下一次触发排好；
                    // 否则这次挂起就等于把整个计划停掉了（超时或解锁失败后不再有后续闹钟）
                    Scheduler.schedule(context, task)
                    return
                }
                log.append("未开启手动解锁兜底，放弃本次任务\n")
                LogStore.append(context, log.toString())
                TaskRunner.notifyFailure(context, log.toString())
                applyResult(context, task, false)
                return
            }
        } else {
            log.append("锁屏状态: false，直接启动\n")
        }

        val ok = TaskRunner.launch(runnable, log)
        LogStore.append(context, log.toString())
        if (!ok) {
            TaskRunner.notifyFailure(context, log.toString())
        }
        applyResult(context, task, ok)
    }

    /** 检测到用户解锁：把待启动的计划真正跑一遍 */
    fun launchPending(context: Context) {
        val entries = PendingStore.all(context)
        if (entries.isEmpty()) {
            cancelTimeout(context)
            return
        }
        val now = System.currentTimeMillis()
        for (entry in entries) {
            if (now > entry.deadline) {
                expireEntry(context, entry)
                continue
            }
            if (TaskRunner.isLocked()) {
                // 解锁事件刚到、锁屏还没完全收起，留给下一次 USER_PRESENT
                continue
            }
            val task = TaskStore.find(context, entry.taskId)
            if (task == null) {
                PendingStore.remove(context, entry.taskId)
                LogStore.append(context, "待启动计划「${entry.title}」已不存在，已清理\n")
                continue
            }
            if (!task.enabled || !Config.get(context).enabled()) {
                // 排队期间用户关掉了这个计划或调度总开关，就不要在解锁后偷偷启动
                PendingStore.remove(context, entry.taskId)
                LogStore.append(context, "待启动计划「${entry.title}」已被关闭，已取消\n")
                continue
            }
            val log = StringBuilder()
            log.append("=== 检测到手动解锁 ${TaskRunner.stamp()}（${entry.title}） ===\n")
            val runnable = TaskRunner.validate(entry.packages, log)
            if (runnable.isEmpty()) {
                log.append("没有任何可启动的包，放弃本次任务\n")
                LogStore.append(context, log.toString())
                TaskRunner.notifyFailure(context, log.toString())
                PendingStore.remove(context, entry.taskId)
                applyResult(context, task, false)
                continue
            }
            val ok = TaskRunner.launch(runnable, log)
            LogStore.append(context, log.toString())
            if (!ok) {
                TaskRunner.notifyFailure(context, log.toString())
            }
            PendingStore.remove(context, entry.taskId)
            applyResult(context, task, ok)
        }
        // 可能还有条目没处理完（例如锁屏尚未完全收起），这时不能把超时闹钟取消掉
        refreshTimeout(context)
    }

    /** 超时闹钟到点：清理过期条目并通知，不再等待 */
    fun expirePending(context: Context) {
        val now = System.currentTimeMillis()
        for (entry in PendingStore.all(context)) {
            if (now > entry.deadline) {
                expireEntry(context, entry)
            }
        }
        refreshTimeout(context)
    }

    /** 丢弃一条待启动记录：记超时、按失败记账并排下一次，避免计划从此不再触发 */
    private fun expireEntry(context: Context, entry: PendingLaunch) {
        logPendingTimeout(context, entry)
        PendingStore.remove(context, entry.taskId)
        val task = TaskStore.find(context, entry.taskId)
        if (task != null) {
            applyResult(context, task, false)
        }
    }

    /** 队列空了就撤销超时闹钟，还有存活条目就按最早的截止时间重排一次 */
    private fun refreshTimeout(context: Context) {
        val alive = PendingStore.all(context)
        if (alive.isEmpty()) {
            cancelTimeout(context)
            return
        }
        val remaining = ((alive.minOf { it.deadline } - System.currentTimeMillis()) / 1000L)
            .toInt().coerceAtLeast(10)
        scheduleTimeout(context, remaining)
    }

    /** 界面上的「立即执行」：同步跑完并把结果写日志 */
    fun runNow(context: Context, packages: List<String>): Boolean {
        val log = StringBuilder()
        log.append("=== 手动执行 ${TaskRunner.stamp()} ===\n")
        if (!TaskRunner.suAvailable()) {
            log.append("未获得 root（su 不可用），无法执行\n")
            LogStore.append(context, log.toString())
            TaskRunner.notifyFailure(context, log.toString())
            return false
        }
        val runnable = TaskRunner.validate(packages, log)
        if (runnable.isEmpty()) {
            log.append("没有任何可启动的包，放弃本次任务\n")
            LogStore.append(context, log.toString())
            TaskRunner.notifyFailure(context, log.toString())
            return false
        }
        val config = Config.get(context)
        if (TaskRunner.isLocked() && !TaskRunner.unlock(context, config, log)) {
            log.append("自动解锁失败，放弃本次手动执行\n")
            LogStore.append(context, log.toString())
            TaskRunner.notifyFailure(context, log.toString())
            return false
        }
        val ok = TaskRunner.launch(runnable, log)
        LogStore.append(context, log.toString())
        if (!ok) {
            TaskRunner.notifyFailure(context, log.toString())
        }
        return ok
    }

    /** 执行完一轮后记账：剩余次数、自动停用、排下一次 */
    private fun applyResult(context: Context, task: TaskItem, ok: Boolean) {
        var updated = task.copy(
            lastRunAt = System.currentTimeMillis(),
            lastResult = if (ok) "成功" else "失败"
        )
        val lines = StringBuilder()
        if (task.maxRuns > 0) {
            val remaining = (task.remainingRuns - 1).coerceAtLeast(0)
            updated = updated.copy(remainingRuns = remaining)
            if (remaining <= 0) {
                updated = updated.copy(enabled = false)
                lines.append("计划「${task.title()}」已执行完 ${task.maxRuns} 次，自动停用\n")
            }
        }
        TaskStore.upsert(context, updated)
        if (updated.enabled) {
            Scheduler.schedule(context, updated)
            lines.append("下次触发: ${Scheduler.describeNext(updated)}\n")
        } else {
            Scheduler.cancel(context, updated.id)
        }
        if (lines.isNotEmpty()) {
            LogStore.append(context, lines.toString())
        }
    }

    private fun logPendingTimeout(context: Context, entry: PendingLaunch) {
        val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA)
            .format(Date(entry.deadline))
        val text = "计划「${entry.title}」等待手动解锁超时（截止 $stamp），放弃本次任务\n"
        LogStore.append(context, text)
        TaskRunner.notifyFailure(context, text)
    }

    private fun timeoutIntent(context: Context): PendingIntent {
        val intent = Intent(context, UnlockReceiver::class.java).apply {
            action = ACTION_MANUAL_TIMEOUT
        }
        return PendingIntent.getBroadcast(
            context,
            TIMEOUT_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun scheduleTimeout(context: Context, afterSec: Int) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val at = System.currentTimeMillis() + afterSec * 1000L
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, timeoutIntent(context))
            } else {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, timeoutIntent(context))
            }
        } catch (e: SecurityException) {
            try {
                am.set(AlarmManager.RTC_WAKEUP, at, timeoutIntent(context))
            } catch (t: Throwable) {
                LogStore.append(context, "排超时闹钟失败: $t\n")
            }
        }
    }

    private fun cancelTimeout(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        try {
            am.cancel(timeoutIntent(context))
        } catch (t: Throwable) {
            // 取消失败不影响功能
        }
    }
}
