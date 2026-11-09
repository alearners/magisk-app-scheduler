package com.appscheduler.lsposed

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import com.appscheduler.lsposed.model.TaskItem
import com.appscheduler.lsposed.schedule.Scheduler
import com.appscheduler.lsposed.store.TaskStore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 「应用内部计时器」模式下的计时服务。
 *
 * 和系统闹钟的区别：
 * - 系统闹钟由 AlarmManager 负责，进程/App 被杀掉也能到点唤醒，但会被省电策略推迟，且部分 ROM 限制精确闹钟；
 * - 内部计时器由一个常驻前台服务自己数时间，到点判定完全由本进程负责，不受闹钟延迟影响，
 *   但代价是必须常驻一个前台服务（通知栏会一直有一条「内部计时运行中」）。
 *
 * 两种模式在界面上二选一，默认仍然是系统闹钟。
 */
class TimerService : Service() {

    private var loop: Thread? = null
    private var lastBanner = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val config = Config.get(applicationContext)
        if (!config.enabled() || !config.internalTimer()) {
            // 总开关关了或已切回系统闹钟：不需要这个常驻服务
            stopSelf()
            return START_NOT_STICKY
        }
        try {
            startForeground(NOTIFICATION_ID, buildNotification(nextText()))
        } catch (t: Throwable) {
            LogStore.append(this, "内部计时服务前台通知失败: $t\n")
        }
        if (loop == null) {
            loop = Thread({ runLoop() }, "apptimer-timer").apply { isDaemon = false; start() }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        loop = null
        super.onDestroy()
    }

    private fun runLoop() {
        val app = applicationContext
        val due = mutableMapOf<Long, Long>()
        try {
            while (true) {
                val config = Config.get(app)
                if (!config.enabled() || !config.internalTimer()) {
                    break
                }
                val tasks = TaskStore.all(app).filter { it.enabled && it.packages.isNotEmpty() }
                val ids = tasks.map { it.id }.toSet()
                due.keys.retainAll(ids)

                val now = System.currentTimeMillis()
                for (task in tasks) {
                    val at = due[task.id] ?: Scheduler.nextTrigger(task, now) ?: continue
                    due[task.id] = at
                    if (at <= now) {
                        // 到点了：按计划跑一遍，跑完把这次的触发点换成下一次
                        try {
                            RunCoordinator.executeTask(app, task.id)
                        } catch (t: Throwable) {
                            LogStore.append(app, "内部计时执行异常: $t")
                        }
                        due[task.id] = Scheduler.nextTrigger(task, System.currentTimeMillis() + 1000L)
                            ?: Long.MAX_VALUE
                    }
                }

                // 每分钟把「下次执行」写进通知，用户能直接看到内部计时器还活着
                if (System.currentTimeMillis() - lastBanner > 60_000L) {
                    lastBanner = System.currentTimeMillis()
                    updateNotification()
                }
                Thread.sleep(TICK_MS)
            }
        } catch (t: Throwable) {
            LogStore.append(app, "内部计时服务异常: $t")
        } finally {
            stopForegroundRun()
            stopSelf()
        }
    }

    private fun updateNotification() {
        try {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.notify(NOTIFICATION_ID, buildNotification(nextText()))
        } catch (t: Throwable) {
            // 通知更新失败不影响计时
        }
    }

    private fun nextText(): String {
        val soonest: Pair<TaskItem, Long>? = Scheduler.soonest(this)
        val stamp = soonest?.let {
            SimpleDateFormat("MM-dd HH:mm", Locale.CHINA).format(Date(it.second)) + " · " + it.first.title()
        } ?: "暂无启用中的计划"
        return "下次执行：$stamp"
    }

    private fun buildNotification(text: String): Notification {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "${RunService.APP_NAME} · 内部计时", NotificationManager.IMPORTANCE_LOW
            )
            channel.setShowBadge(false)
            nm.createNotificationChannel(channel)
            return Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_menu_recent_history)
                .setContentTitle(RunService.APP_NAME)
                .setContentText(text)
                .setContentIntent(open)
                .setOngoing(true)
                .build()
        }
        @Suppress("DEPRECATION")
        return Notification.Builder(this)
            .setSmallIcon(android.R.drawable.ic_menu_recent_history)
            .setContentTitle(RunService.APP_NAME)
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(true)
            .build()
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

    companion object {
        private const val CHANNEL_ID = "apptimer_timer"
        private const val NOTIFICATION_ID = 2005

        /** 计时精度：1 秒足够，内部计时不需要更细 */
        private const val TICK_MS = 1000L

        fun start(context: Context) {
            val app = context.applicationContext
            val intent = Intent(app, TimerService::class.java)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    app.startForegroundService(intent)
                } else {
                    app.startService(intent)
                }
            } catch (t: Throwable) {
                LogStore.append(app, "内部计时服务启动被拒: $t\n")
            }
        }

        fun stop(context: Context) {
            val app = context.applicationContext
            try {
                app.stopService(Intent(app, TimerService::class.java))
            } catch (t: Throwable) {
                // 停服务失败不影响后续
            }
        }
    }
}
