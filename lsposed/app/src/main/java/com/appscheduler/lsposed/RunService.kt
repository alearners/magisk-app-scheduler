package com.appscheduler.lsposed

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import java.util.concurrent.Executors

/**
 * 执行定时任务的前台服务。
 *
 * 为什么需要它：解锁 + 启动多 App 的流程可能超过广播接收器约 10 秒的执行窗口，
 * 而「等用户手动解锁」的兜底更是几十秒到半小时。放到前台服务里执行，进程不会被随手回收，
 * 也不需要让 BroadcastReceiver 挂着 PendingResult 干等。
 */
class RunService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 前台通知起不来（少权限、渠道被禁用、Android 14 的 specialUse 限制）时不能让服务直接崩，
        // 退化成普通后台服务把这一轮跑完，总比丢掉整次任务好
        try {
            startForeground(NOTIFICATION_ID, buildNotification())
        } catch (t: Throwable) {
            LogStore.append(this, "前台服务通知失败，退化为普通服务执行: $t\n")
        }
        val action = intent?.action
        val taskId = intent?.getLongExtra(EXTRA_TASK_ID, 0L) ?: 0L
        POOL.execute {
            try {
                when (action) {
                    ACTION_LAUNCH -> RunCoordinator.launchPending(this)
                    ACTION_EXPIRE -> RunCoordinator.expirePending(this)
                    else -> RunCoordinator.executeTask(this, taskId)
                }
            } catch (t: Throwable) {
                LogStore.append(this, "前台服务执行异常: $t")
            } finally {
                stopForegroundRun()
                stopSelf(startId)
            }
        }
        return START_NOT_STICKY
    }

    private fun stopForegroundRun() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
        } catch (t: Throwable) {
            // 停前台失败不影响后续
        }
    }

    private fun buildNotification(): Notification {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "定时启动 App · 执行中", NotificationManager.IMPORTANCE_LOW
            )
            channel.setShowBadge(false)
            nm.createNotificationChannel(channel)
            return Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_popup_sync)
                .setContentTitle("定时启动 App")
                .setContentText("正在执行计划…")
                .setOngoing(true)
                .build()
        }
        @Suppress("DEPRECATION")
        return Notification.Builder(this)
            .setSmallIcon(android.R.drawable.ic_popup_sync)
            .setContentTitle("定时启动 App")
            .setContentText("正在执行计划…")
            .setOngoing(true)
            .build()
    }

    companion object {
        const val ACTION_RUN = "com.appscheduler.action.SERVICE_RUN"
        const val ACTION_LAUNCH = "com.appscheduler.action.SERVICE_LAUNCH"
        const val ACTION_EXPIRE = "com.appscheduler.action.SERVICE_EXPIRE"
        const val EXTRA_TASK_ID = "task_id"

        private const val CHANNEL_ID = "scheduler_run"
        private const val NOTIFICATION_ID = 2003

        /** 串行执行，避免多个计划同时抢 su 与屏幕 */
        private val POOL = Executors.newSingleThreadExecutor()

        /**
         * 尝试通过前台服务执行。
         *
         * @return false 表示系统拒绝后台启动前台服务（Android 12+ 的启动限制），调用方应退化为接收器线程执行
         */
        fun start(context: Context, action: String, taskId: Long): Boolean {
            val intent = Intent(context.applicationContext, RunService::class.java).apply {
                this.action = action
                putExtra(EXTRA_TASK_ID, taskId)
            }
            return try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.applicationContext.startForegroundService(intent)
                } else {
                    context.applicationContext.startService(intent)
                }
                true
            } catch (t: Throwable) {
                LogStore.append(context, "前台服务启动被拒，退化到接收器线程执行: $t\n")
                false
            }
        }
    }
}
