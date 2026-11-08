package com.appscheduler.lsposed.schedule

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.appscheduler.lsposed.LogStore
import com.appscheduler.lsposed.TaskRunner
import com.appscheduler.lsposed.conditions.ConditionChecker
import com.appscheduler.lsposed.model.TaskItem
import com.appscheduler.lsposed.store.TaskStore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 闹钟触发：检查条件 → 执行计划里的全部 App → 记录次数 → 排下一次。
 */
class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getLongExtra(Scheduler.EXTRA_TASK_ID, 0L)
        val appContext = context.applicationContext
        val pending = goAsync()
        Thread {
            try {
                execute(appContext, taskId)
            } catch (t: Throwable) {
                LogStore.append(appContext, "任务调度异常: $t")
            } finally {
                pending.finish()
            }
        }.start()
    }

    private fun execute(context: Context, taskId: Long) {
        val task = TaskStore.find(context, taskId)
        if (task == null) {
            Scheduler.rescheduleAll(context)
            return
        }
        if (!task.enabled) {
            Scheduler.cancel(context, task.id)
            return
        }

        val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA).format(Date())
        LogStore.append(context, "=== 定时触发 $stamp（${task.title()}） ===")

        val reason = ConditionChecker.unfilledReason(context, task)
        if (reason != null) {
            LogStore.append(context, "启动条件未满足（$reason），本次跳过")
            Scheduler.schedule(context, task)
            return
        }

        val ok = TaskRunner.run(context, task.packages)
        var updated = task.copy(
            lastRunAt = System.currentTimeMillis(),
            lastResult = if (ok) "成功" else "失败"
        )
        if (task.maxRuns > 0) {
            val remaining = (task.remainingRuns - 1).coerceAtLeast(0)
            updated = updated.copy(remainingRuns = remaining)
            if (remaining <= 0) {
                updated = updated.copy(enabled = false)
                LogStore.append(context, "计划「${task.title()}」已执行完 $task.maxRuns 次，自动停用")
            }
        }
        TaskStore.upsert(context, updated)
        if (updated.enabled) {
            Scheduler.schedule(context, updated)
            LogStore.append(context, "下次触发: ${Scheduler.describeNext(updated)}")
        } else {
            Scheduler.cancel(context, updated.id)
        }
    }
}
