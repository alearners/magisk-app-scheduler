package com.appscheduler.lsposed.schedule

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.appscheduler.lsposed.LogStore
import com.appscheduler.lsposed.RunCoordinator
import com.appscheduler.lsposed.RunService

/**
 * 闹钟触发入口：优先交给前台服务，进程有前台优先级、不会被广播窗口限制。
 * 前台服务启动被系统拒绝时，退化为接收器线程执行；此时解锁失败也只登记待启动，不会长等。
 */
class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getLongExtra(Scheduler.EXTRA_TASK_ID, 0L)
        val appContext = context.applicationContext

        if (RunService.start(appContext, RunService.ACTION_RUN, taskId)) {
            return
        }

        val pending = goAsync()
        Thread {
            try {
                RunCoordinator.executeTask(appContext, taskId)
            } catch (t: Throwable) {
                LogStore.append(appContext, "任务调度异常: $t")
            } finally {
                pending.finish()
            }
        }.start()
    }
}
