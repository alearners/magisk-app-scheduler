package com.appscheduler.lsposed

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.appscheduler.lsposed.schedule.UnlockReceiver

/**
 * 启动完 App 之后的「无操作自动锁屏」。
 *
 * 语义：启动成功后排一个 N 秒的闹钟，到点时如果
 * 1) 屏幕还亮着没被用户自己锁掉，且
 * 2) 前台还是本次启动的那个 App（用户没有切走去做别的事），
 * 就认为是「用户没有操作」，执行熄屏上锁。
 * 用户一旦切到别的界面或自己按了电源键，这一步就自动跳过，不会误锁。
 */
object AutoLock {

    const val ACTION_AUTO_LOCK = "com.appscheduler.action.AUTO_LOCK"
    private const val REQUEST_CODE = 4200
    /** 闹钟只能带一个 PendingIntent，本次要保护哪些包存在 prefs 里 */
    private const val KEY_PKGS = "auto_lock_pkgs"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(Config.PREFS, Context.MODE_PRIVATE)

    private fun savePkgs(context: Context, packages: List<String>) {
        prefs(context).edit().putString(KEY_PKGS, packages.joinToString(",")).apply()
    }

    private fun loadPkgs(context: Context): List<String> =
        prefs(context).getString(KEY_PKGS, "").orEmpty()
            .split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }

    private fun intent(context: Context): PendingIntent {
        val i = Intent(context, UnlockReceiver::class.java).apply { action = ACTION_AUTO_LOCK }
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /**
     * 启动成功后调用：记录本次要保护的包，返回「是否需要等一下再锁」。
     *
     * 真正的等待交给 [holdAndFire] 在调用方所在的线程（前台服务里）完成——
     * 真机实测：任务跑完后进程会在 1~2 秒内被厂商省电策略冻结，
     * 此时再 rop 一个闹钟或另起服务都会被推迟到几分钟后，5 秒就变成几分钟。
     */
    fun arm(context: Context, delaySec: Int, packages: List<String>): Boolean {
        if (delaySec <= 0 || packages.isEmpty()) {
            return false
        }
        if (!Config.get(context).autoLock()) {
            return false
        }
        savePkgs(context, packages)
        return true
    }

    /**
     * 原地等 N 秒再判断要不要锁屏。
     *
     * 必须由仍在执行中的前台服务线程调用：只要服务还是前台状态，
     * 进程不会被冻结，计时精度就和用户手点一样准。
     */
    fun holdAndFire(context: Context) {
        val delay = Config.get(context).autoLockDelay()
        try {
            Thread.sleep(delay.coerceAtLeast(2) * 1000L)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
        }
        fire(context)
    }

    fun cancel(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        try {
            am.cancel(intent(context))
        } catch (t: Throwable) {
            // 取消失败不影响功能
        }
    }

    /**
     * 闹钟到点：确认用户确实没接管，再熄屏上锁。
     *
     * 这里重新读一次配置：排闹钟到到点之间用户可能在界面上把开关关掉了。
     */
    fun fire(context: Context) {
        val config = Config.get(context)
        if (!config.autoLock()) {
            return
        }
        val packages = loadPkgs(context)
        if (packages.isEmpty()) {
            return
        }
        if (TaskRunner.isLocked()) {
            // 用户已经自己锁屏了，不用再补一刀
            return
        }
        val fg = TaskRunner.foregroundPackage()
        if (fg.isNotEmpty() && packages.none { it == fg }) {
            LogStore.append(
                context,
                "自动锁屏：前台已是「$fg」，判定为用户已接管，跳过\n"
            )
            return
        }
        val log = StringBuilder()
        if (TaskRunner.lockScreen(log)) {
            LogStore.append(
                context,
                "启动后 ${config.autoLockDelay()} 秒无操作，已自动熄屏上锁\n"
            )
        } else {
            LogStore.append(context, log.toString())
        }
    }
}
