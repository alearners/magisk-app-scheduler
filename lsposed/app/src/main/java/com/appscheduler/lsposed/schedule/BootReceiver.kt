package com.appscheduler.lsposed.schedule

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.appscheduler.lsposed.LogStore

/**
 * 开机后重排所有计划的闹钟（AlarmManager 的定时在重启后会丢）。
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_LOCKED_BOOT_COMPLETED) {
            return
        }
        Scheduler.rescheduleAll(context)
        LogStore.append(context, "开机完成，已重排全部计划")
    }
}
