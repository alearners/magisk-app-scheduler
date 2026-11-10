package com.appscheduler.lsposed

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import com.appscheduler.lsposed.store.PendingStore
import java.util.concurrent.Executors

/**
 * 执行定时任务的前台服务。
 *
 * 为什么需要它：解锁 + 启动多 App 的流程可能超过广播接收器约 10 秒的执行窗口，
 * 而「等用户手动解锁」的兜底更是几十秒到半小时。放到前台服务里执行，进程不会被随手回收，
 * 也不需要让 BroadcastReceiver 挂着 PendingResult 干等。
 *
 * [ACTION_WAIT] 是「挂起等待手动解锁」期间的守候服务：它用 registerReceiver 动态注册
 * USER_PRESENT（动态注册不受 Android 8+ 隐式广播限制），同时每 2 秒轮询一次锁屏状态，
 * 双保险保证用户解锁后能把队列跑掉。之前靠清单里静态注册的接收器，真机上从来没收到过广播。
 */
class RunService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        val taskId = intent?.getLongExtra(EXTRA_TASK_ID, 0L) ?: 0L

        // 前台通知起不来（少权限、渠道被禁用、Android 14 的 specialUse 限制）时不能让服务直接崩，
        // 退化成普通后台服务把这一轮跑完，总比丢掉整次任务好
        try {
            startForeground(
                if (action == ACTION_WAIT) WAIT_NOTIFICATION_ID else NOTIFICATION_ID,
                buildNotification(action)
            )
        } catch (t: Throwable) {
            LogStore.append(this, "前台服务通知失败，退化为普通服务执行: $t\n")
        }

        if (action == ACTION_WAIT) {
            startWaitLoop(startId)
            return START_NOT_STICKY
        }

        val delaySec = intent?.getIntExtra(EXTRA_DELAY_SEC, 0) ?: 0
        scheduleWatchdog(startId, delaySec)
        POOL.execute {
            try {
                when (action) {
                    ACTION_LAUNCH -> RunCoordinator.launchPending(this)
                    ACTION_EXPIRE -> RunCoordinator.expirePending(this)
                    ACTION_LOCK -> AutoLock.fire(this)
                    ACTION_LOCK_DELAY -> {
                        // 在服务里原地等到点再锁：进程保持前台优先级，
                        // 不会被厂商省电策略冻结掉那个「5 秒」闹钟
                        sleep(delaySec.coerceAtLeast(2) * 1000L)
                        AutoLock.fire(this)
                    }
                    else -> RunCoordinator.executeTask(this, taskId)
                }
            } catch (t: Throwable) {
                LogStore.append(this, "前台服务执行异常: $t")
            } finally {
                cancelWatchdog()
                stopForegroundRun()
                stopSelf(startId)
            }
        }
        return START_NOT_STICKY
    }

    /**
     * 守候待启动队列：动态注册 USER_PRESENT + 轮询兜底。
     *
     * 退出条件：队列清空 / 全部条目过期 / 硬上限（默认 35 分钟）到时。
     * 硬上限是防御性的——正常情况下队列在用户解锁或超时后就会清空。
     */
    private fun startWaitLoop(startId: Int) {
        // 同一个服务实例可能被重复 start（每个计划挂起都会拉起一次），只留一个守候线程
        if (waitThread?.isAlive == true) {
            return
        }
        val app = applicationContext
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == Intent.ACTION_USER_PRESENT) {
                    userPresent = true
                }
            }
        }
        var registered = false
        try {
            registerReceiver(receiver, IntentFilter(Intent.ACTION_USER_PRESENT))
            registered = true
        } catch (t: Throwable) {
            LogStore.append(app, "动态注册解锁广播失败，改用轮询: $t\n")
        }

        val thread = Thread {
            val hardStopAt = System.currentTimeMillis() + WAIT_HARD_LIMIT_SEC * 1000L
            try {
                while (System.currentTimeMillis() < hardStopAt) {
                    val entries = PendingStore.all(app)
                    if (entries.isEmpty()) {
                        break
                    }
                    val now = System.currentTimeMillis()
                    if (entries.all { now > it.deadline }) {
                        RunCoordinator.expirePending(app)
                        break
                    }
                    // 解锁了就立刻跑；仍在锁屏时把轮询间隔拉长，减少无谓的 dumpsys
                    if (!TaskRunner.isLocked()) {
                        RunCoordinator.launchPending(app)
                        if (PendingStore.all(app).isEmpty()) {
                            break
                        }
                    }
                    sleep(if (userPresent) 500L else WAIT_POLL_MS)
                    userPresent = false
                }
            } catch (t: Throwable) {
                LogStore.append(app, "守候解锁异常: $t")
            } finally {
                if (registered) {
                    try {
                        unregisterReceiver(receiver)
                    } catch (t: Throwable) {
                        // 已经注销过，忽略
                    }
                }
                stopForegroundRun()
                // 用无参 stopSelf：守候期间可能又被 start 过一次，带 startId 的版本会失效
                stopSelf()
            }
        }
        waitThread = thread
        thread.start()
    }

    private fun sleep(millis: Long) {
        try {
            Thread.sleep(millis)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }

    /**
     * 兜底看门狗：随时可能被 su 授权弹窗、系统服务响应慢拖住，
     * 真机上实测过一次执行链卡住约 3 分钟、前台通知一直不消失。
     * 超过 [WATCHDOG_SEC] 秒仍没跑完就强制摘掉前台通知并结束服务，
     * 让系统与用户都不会看到一个永远「正在执行」的通知。
     */
    private fun scheduleWatchdog(startId: Int, extraSec: Int = 0) {
        val handler = Handler(Looper.getMainLooper())
        val limit = (WATCHDOG_SEC + extraSec).toLong()
        val runnable = Runnable {
            LogStore.append(this, "执行超过 $limit 秒仍未结束，强制结束前台服务\n")
            stopForegroundRun()
            stopSelf(startId)
        }
        watchdogHandler = handler
        watchdogRunnable = runnable
        handler.postDelayed(runnable, limit * 1000L)
    }

    private fun cancelWatchdog() {
        watchdogRunnable?.let { watchdogHandler?.removeCallbacks(it) }
        watchdogRunnable = null
        watchdogHandler = null
    }

    private var watchdogHandler: Handler? = null
    private var watchdogRunnable: Runnable? = null

    /** 广播线程写、守候线程读，必须 volatile */
    @Volatile
    private var userPresent = false

    /** 守候线程：同一个服务实例只允许一个 */
    @Volatile
    private var waitThread: Thread? = null

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

    private fun buildNotification(action: String?): Notification {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val waitMode = action == ACTION_WAIT
        val lockMode = action == ACTION_LOCK_DELAY
        val title = APP_NAME
        val text = when {
            waitMode -> "等待你手动解锁后启动…"
            lockMode -> "启动后无操作，即将自动锁屏…"
            else -> "正在执行计划…"
        }
        val icon = if (waitMode) android.R.drawable.ic_lock_idle_lock
        else android.R.drawable.ic_popup_sync
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "$APP_NAME · 执行中", NotificationManager.IMPORTANCE_LOW
            )
            channel.setShowBadge(false)
            nm.createNotificationChannel(channel)
            return Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(icon)
                .setContentTitle(title)
                .setContentText(text)
                .setOngoing(true)
                .build()
        }
        @Suppress("DEPRECATION")
        return Notification.Builder(this)
            .setSmallIcon(icon)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .build()
    }

    companion object {
        const val ACTION_RUN = "com.appscheduler.action.SERVICE_RUN"
        const val ACTION_LAUNCH = "com.appscheduler.action.SERVICE_LAUNCH"
        const val ACTION_EXPIRE = "com.appscheduler.action.SERVICE_EXPIRE"
        const val ACTION_WAIT = "com.appscheduler.action.SERVICE_WAIT"
        const val ACTION_LOCK = "com.appscheduler.action.SERVICE_LOCK"
        const val ACTION_LOCK_DELAY = "com.appscheduler.action.SERVICE_LOCK_DELAY"
        const val EXTRA_TASK_ID = "task_id"
        const val EXTRA_DELAY_SEC = "delay_sec"

        /** 显示名统一在这里改一次 */
        const val APP_NAME = "AppTimer"

        private const val CHANNEL_ID = "scheduler_run"
        private const val NOTIFICATION_ID = 2003
        private const val WAIT_NOTIFICATION_ID = 2004

        /** 单次执行的最长存活时间（秒），超过就强制收尾 */
        private const val WATCHDOG_SEC = 180

        /** 守候服务的硬上限（秒）：正常流程会提前清空队列退出，这里只是防止服务常驻 */
        private const val WAIT_HARD_LIMIT_SEC = 2100

        /** 锁屏期间轮询解锁状态的间隔 */
        private const val WAIT_POLL_MS = 2000L

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

        /**
         * 启动后延时自动锁屏：不让它依赖系统闹钟。
         *
         * 真机（MIUI/HyperOS）复现过：App 在后台被冻结后，+5 秒的精确闹钟会被推迟到几分钟后才送达，
         * 于是「无操作 5 秒就锁屏」实际变成「几分钟后才锁」。放到前台服务里原地等待，进程有前台优先级，
         * 计时精度不受省电策略影响。
         */
        fun startLockDelay(context: Context, delaySec: Int): Boolean {
            val intent = Intent(context.applicationContext, RunService::class.java).apply {
                action = ACTION_LOCK_DELAY
                putExtra(EXTRA_DELAY_SEC, delaySec)
            }
            return try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.applicationContext.startForegroundService(intent)
                } else {
                    context.applicationContext.startService(intent)
                }
                true
            } catch (t: Throwable) {
                LogStore.append(context, "自动锁屏前台服务启动被拒，改用闹钟兜底: $t\n")
                false
            }
        }
    }
}
