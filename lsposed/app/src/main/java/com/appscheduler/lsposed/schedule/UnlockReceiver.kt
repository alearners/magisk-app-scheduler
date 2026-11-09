package com.appscheduler.lsposed.schedule

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.appscheduler.lsposed.AutoLock
import com.appscheduler.lsposed.LogStore
import com.appscheduler.lsposed.RunCoordinator
import com.appscheduler.lsposed.RunService
import com.appscheduler.lsposed.store.PendingStore

/**
 * 监听「等待解锁超时」闹钟、「启动后自动锁屏」闹钟，以及系统「用户已解锁」广播。
 *
 * 注意 USER_PRESENT：它属于 Android 8 起的隐式广播限制清单，清单里静态注册的接收器收不到，
 * 真机上实测「用户手动解锁后队列原样残留」就是这个原因。所以这里保留 ACTION_USER_PRESENT 分支
 * （由 RunService 在前台服务里用 registerReceiver 动态注册，动态注册不受该限制），
 * 同时待启动队列还会用轮询兜底，不再把整条链路押在这个广播上。
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

            AutoLock.ACTION_AUTO_LOCK -> {
                if (RunService.start(appContext, RunService.ACTION_LOCK, 0L)) {
                    return
                }
                val pending = goAsync()
                Thread {
                    try {
                        AutoLock.fire(appContext)
                    } catch (t: Throwable) {
                        LogStore.append(appContext, "自动锁屏异常: $t")
                    } finally {
                        pending.finish()
                    }
                }.start()
            }
        }
    }
}
