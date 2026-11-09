package com.appscheduler.lsposed.schedule

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.appscheduler.lsposed.Config
import com.appscheduler.lsposed.LogStore
import com.appscheduler.lsposed.RunCoordinator
import com.appscheduler.lsposed.store.PendingStore

/**
 * 开机后重排所有计划的闹钟（AlarmManager 的定时在重启后会丢）。
 *
 * 内部计时器模式：系统闹钟本来就没在用，直接把常驻的 TimerService 拉起来，
 * 由它按计划自己数时间。
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_LOCKED_BOOT_COMPLETED) {
            return
        }
        Scheduler.applyMode(context)
        val mode = if (Config.get(context).internalTimer()) "应用内部计时器" else "系统闹钟"
        LogStore.append(context, "开机完成，已按「$mode」重排全部计划\n")

        // 重启会清掉闹钟，包括「等待手动解锁」的超时闹钟；这里补一次清理 + 重排，
        // 免得重启前挂起的条目一直停在待启动队列里
        if (PendingStore.all(context).isEmpty()) {
            return
        }
        val pending = goAsync()
        Thread {
            try {
                RunCoordinator.expirePending(context.applicationContext)
            } catch (t: Throwable) {
                LogStore.append(context, "开机清理待启动队列异常: $t")
            } finally {
                pending.finish()
            }
        }.start()
    }
}
