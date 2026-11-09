package com.appscheduler.lsposed.schedule

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.appscheduler.lsposed.LogStore
import com.appscheduler.lsposed.RunCoordinator
import com.appscheduler.lsposed.RunService
import com.appscheduler.lsposed.store.PendingStore

/**
 * 监听系统「用户已解锁」广播（USER_PRESENT）与「等待解锁超时」闹钟。
 *
 * 自动解锁失败后不再由广播接收器原地轮询，改由这里接管：用户一解锁，
 * 就把待启动的计划送去前台服务执行；超时则清理并通知。
 */
class UnlockReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val appContext = context.applicationContext
        when (intent.action) {
            Intent.ACTION_USER_PRESENT -> {
                if (PendingStore.all(appContext).isEmpty()) {
                    return
                }
                if (RunService.start(appContext, RunService.ACTION_LAUNCH, 0L)) {
                    return
                }
                val pending = goAsync()
                Thread {
                    try {
                        RunCoordinator.launchPending(appContext)
                    } catch (t: Throwable) {
                        LogStore.append(appContext, "解锁后启动异常: $t")
                    } finally {
                        pending.finish()
                    }
                }.start()
            }

            RunCoordinator.ACTION_MANUAL_TIMEOUT -> {
                if (RunService.start(appContext, RunService.ACTION_EXPIRE, 0L)) {
                    return
                }
                val pending = goAsync()
                Thread {
                    try {
                        RunCoordinator.expirePending(appContext)
                    } catch (t: Throwable) {
                        LogStore.append(appContext, "解锁超时处理异常: $t")
                    } finally {
                        pending.finish()
                    }
                }.start()
            }
        }
    }
}
