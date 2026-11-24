package com.appscheduler.lsposed.permissions

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import com.appscheduler.lsposed.LogStore
import com.appscheduler.lsposed.TaskRunner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/** 单项权限的判定结果 */
enum class PermStatus(val label: String) {
    OK("已就绪"),
    VERIFIED("已确认"),
    MISSING("尚未授予"),
    UNKNOWN("待确认");

    /** 算不算「不用再管」：已就绪与用户已确认的，都不再计入待处理 */
    val ready: Boolean get() = this == OK || this == VERIFIED
}

/** 点「申请/去设置」时具体做什么 */
enum class PermAction {
    NOTIFICATION,           // 弹系统运行时授权框
    NOTIFICATION_SETTINGS,  // 跳到本应用的通知设置
    EXACT_ALARM,            // 跳到「闹钟与提醒」授权页
    BATTERY,                // root 加白，失败再跳系统页
    ROOT,                   // 触发一次 su，让 Magisk 弹授权
    LSPOSED,                // 打开 LSPosed 管理器
    APP_DETAILS,            // 打开本应用详情页
    PACKAGE_CHECK,          // 由用户主动点一次「读应用列表」（某些系统这时才会弹授权框）
    CONFIRM_LSPOSED,        // 用户自己去 LSPosed 里确认过作用域，回来点「我已确认」
    CONFIRM_VENDOR          // 用户自己去应用详情里开过自启动/后台弹出界面，回来点「我已确认」
}

data class PermRow(
    val key: String,
    val title: String,
    val detail: String,
    val status: PermStatus,
    val actionLabel: String? = null,
    val action: PermAction? = null,
    /** 第二个按钮：用于「去设置」之后回来点「我已确认」这类手动确认 */
    val secondActionLabel: String? = null,
    val secondAction: PermAction? = null,
    /** 已就绪时的补充信息，如 «uid=0»、«已放行» */
    val extra: String? = null
)

/**
 * 权限体检：只做判定，不主动弹任何系统框。
 * 真正「申请」由界面根据 [PermAction] 触发，避免每次进页面都打扰用户。
 *
 * 注意：这里**不能**在检测阶段去查已安装应用列表。HyperOS / MIUI 上第一次查询包列表会弹
 * 「取得已安装的应用程式资讯」授权框，与「进页面只读状态、不弹框」的约定冲突。
 * 所以「读取应用列表」这一项只读缓存结果，要查也得由用户点「现在检查」触发。
 */
object PermissionAudit {

    private const val AUDIT_PREFS = "apptimer_audit"
    private const val KEY_ROOT_VERIFIED = "root_verified"
    private const val KEY_PKG_COUNT = "pkg_count"
    private const val KEY_PKG_CHECKED_AT = "pkg_checked_at"
    /** 两项「需手动确认」的权限：用户自己在系统里确认过之后记一笔，之后显示「已验证」 */
    private const val KEY_LSPOSED_CONFIRMED = "lsposed_confirmed"
    private const val KEY_HOOK_LOADED = "hook_loaded"
    private const val KEY_HOOK_PID = "hook_pid"
    private const val KEY_HOOK_CHECKED_AT = "hook_checked_at"
    private const val KEY_VENDOR_CONFIRMED = "vendor_confirmed"

    /** 认为是「读得到应用列表」的最小数量，低于它基本可以判定被限制了 */
    private const val PKG_COUNT_OK = 10

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(AUDIT_PREFS, Context.MODE_PRIVATE)

    suspend fun collect(context: Context): List<PermRow> = withContext(Dispatchers.IO) {
        val rows = mutableListOf<PermRow>()
        rows += notificationRow(context)
        rows += exactAlarmRow(context)
        rows += batteryRow(context)
        rows += rootRow(context)
        rows += packageQueryRow(context)
        rows += lsposedRow(context)
        rows += vendorRow(context)
        rows
    }

    // ---------------- 通知 ----------------

    private fun notificationRow(context: Context): PermRow {
        val runtimeGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        val enabled = try {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.areNotificationsEnabled()
        } catch (t: Throwable) {
            true
        }
        return when {
            !runtimeGranted -> PermRow(
                key = "notification",
                title = "通知权限",
                detail = "「待你手动解锁」「本次执行未成功」等提醒均依赖此项。Android 13 及以上的系统会在弹窗中征求许可。",
                status = PermStatus.MISSING,
                actionLabel = "去申请",
                action = PermAction.NOTIFICATION
            )

            !enabled -> PermRow(
                key = "notification",
                title = "通知权限",
                detail = "系统已关闭本应用的通知，运行提醒与失败提醒均无法送达。",
                status = PermStatus.MISSING,
                actionLabel = "前往通知设置",
                action = PermAction.NOTIFICATION_SETTINGS
            )

            else -> PermRow(
                key = "notification",
                title = "通知权限",
                detail = "运行提醒与失败提醒均可正常送达。",
                status = PermStatus.OK,
                extra = "已获允许"
            )
        }
    }

    // ---------------- 精确闹钟 ----------------

    private fun exactAlarmRow(context: Context): PermRow {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return PermRow(
                key = "exact_alarm",
                title = "精确闹钟",
                detail = "Android 11 及以下无需单独授予，系统可按既定时间触发。",
                status = PermStatus.OK,
                extra = "该系统无需额外授权"
            )
        }
        val granted = try {
            val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            am.canScheduleExactAlarms()
        } catch (t: Throwable) {
            true
        }
        return PermRow(
            key = "exact_alarm",
            title = "精确闹钟",
            detail = "若未授予，系统仅能按大致时间触发，可能延后数分钟。",
            status = if (granted) PermStatus.OK else PermStatus.MISSING,
            actionLabel = if (granted) null else "前往授予",
            action = if (granted) null else PermAction.EXACT_ALARM,
            extra = if (granted) "已获允许准时唤醒" else null
        )
    }

    // ---------------- 省电白名单 ----------------

    private fun batteryRow(context: Context): PermRow {
        val ignored = try {
            val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            pm.isIgnoringBatteryOptimizations(context.packageName)
        } catch (t: Throwable) {
            true
        }
        return PermRow(
            key = "battery",
            title = "省电白名单",
            detail = "系统启用省电限制后，定时可能被延后；情况严重时，本应用会被列入后台受限名单而不再触发。",
            status = if (ignored) PermStatus.OK else PermStatus.MISSING,
            actionLabel = if (ignored) null else "使用 root 加入白名单",
            action = if (ignored) null else PermAction.BATTERY,
            extra = if (ignored) "已不受省电限制" else null
        )
    }

    // ---------------- root ----------------

    private fun rootRow(context: Context): PermRow {
        val p = prefs(context)
        val verified = p.getBoolean(KEY_ROOT_VERIFIED, false)
        val suPresent = suBinaryPresent()
        return when {
            verified -> PermRow(
                key = "root",
                title = "root 权限",
                detail = "解锁（上滑、输入密码）、强制停止与启动应用、加入省电白名单，均依赖此项。",
                status = PermStatus.OK,
                extra = "已验证，确实取得了 root"
            )

            suPresent -> PermRow(
                key = "root",
                title = "root 权限",
                detail = "设备上存在 su，但尚未完成验证。请点击「申请 root 授权」，Magisk 或 KernelSU 将弹出一次授权确认。",
                status = PermStatus.UNKNOWN,
                actionLabel = "申请 root 授权",
                action = PermAction.ROOT
            )

            else -> PermRow(
                key = "root",
                title = "root 权限",
                detail = "未找到 su。本应用必须运行在已取得 root 的设备上（Magisk 或 KernelSU），否则无法使用。",
                status = PermStatus.MISSING
            )
        }
    }

    /** 只查 su 是否存在，不执行需要 root 的命令，因此不会弹授权框 */
    fun suBinaryPresent(): Boolean {
        val paths = listOf(
            "/system/bin/su", "/system/xbin/su", "/sbin/su",
            "/su/bin/su", "/debug_ramdisk/su", "/data/adb/ksu/bin/su", "/data/adb/magisk/su"
        )
        if (paths.any { File(it).exists() }) {
            return true
        }
        return try {
            val p = ProcessBuilder("/system/bin/sh", "-c", "command -v su || which su")
                .redirectErrorStream(true)
                .start()
            val out = p.inputStream.bufferedReader().use { it.readText() }
            p.waitFor(3, TimeUnit.SECONDS)
            out.trim().isNotEmpty()
        } catch (t: Throwable) {
            false
        }
    }

    /** 真正验证一次 root（会弹授权框），成功后记住结果 */
    suspend fun verifyRoot(context: Context): Boolean = withContext(Dispatchers.IO) {
        val result = TaskRunner.su("id")
        val ok = !result.timeout && result.code == 0 && result.out.contains("uid=0")
        val p = prefs(context)
        p.edit().putBoolean(KEY_ROOT_VERIFIED, ok).apply()
        ok
    }

    // ---------------- 应用列表 ----------------

    /**
     * 只读缓存，不查 PackageManager：查询包列表本身就会触发系统授权框。
     * 缓存由「选择应用」页真正取过一次列表时写入（见 [recordPackageCount]）。
     */
    private fun packageQueryRow(context: Context): PermRow {
        val count = prefs(context).getInt(KEY_PKG_COUNT, -1)
        return when {
            count >= PKG_COUNT_OK -> PermRow(
                key = "packages",
                title = "读取应用列表",
                detail = "「应用选择」需要通过此项列出已安装的应用；当前读取正常。",
                status = PermStatus.OK,
                extra = "当前可见 $count 个应用"
            )

            count >= 0 -> PermRow(
                key = "packages",
                title = "读取应用列表",
                detail = "当前仅可见 $count 个应用，基本可以判定受到系统限制。请前往应用详情，允许「读取应用列表 / 取得已安装应用资讯」。",
                status = PermStatus.MISSING,
                actionLabel = "打开应用详情",
                action = PermAction.APP_DETAILS
            )

            else -> PermRow(
                key = "packages",
                title = "读取应用列表",
                detail = "尚未统计应用列表。请点击「现在核验」执行一次；澎湃、MIUI 等系统会同时征求「取得已安装的应用资讯」许可，允许后「应用选择」才能完整显示。",
                status = PermStatus.UNKNOWN,
                actionLabel = "现在核验",
                action = PermAction.PACKAGE_CHECK
            )
        }
    }

    /** 由用户主动触发的一次应用列表读取；返回读到的数量，同时写进缓存 */
    suspend fun checkPackages(context: Context): Int = withContext(Dispatchers.IO) {
        val count = readLauncherCount(context)
        recordPackageCount(context, count)
        count
    }

    /** 选择应用页取过一次列表后回写，让权限页的状态与真实能力一致 */
    fun recordPackageCount(context: Context, count: Int) {
        prefs(context).edit()
            .putInt(KEY_PKG_COUNT, count)
            .putLong(KEY_PKG_CHECKED_AT, System.currentTimeMillis())
            .apply()
    }

    private fun readLauncherCount(context: Context): Int = try {
        val intent = android.content.Intent(android.content.Intent.ACTION_MAIN)
            .addCategory(android.content.Intent.CATEGORY_LAUNCHER)
        context.packageManager.queryIntentActivities(intent, 0)
            .mapNotNull { it.activityInfo?.packageName }
            .filter { it != context.packageName }
            .distinct()
            .size
    } catch (t: Throwable) {
        0
    }

    // ---------------- LSPosed ----------------

    private suspend fun lsposedRow(context: Context): PermRow {
        val confirmed = prefs(context).getBoolean(KEY_LSPOSED_CONFIRMED, false)
        // 真检测：本模块有没有被 LSPosed 载入到系统界面进程里
        val probe = probeSystemUiHook(context)
        if (probe != null) {
            LogStore.appendDebug(
                context,
                "握手检测：loaded=${probe.loaded} pid=${probe.pid} hookVc=${probe.vc}"
            )
            prefs(context).edit()
                .putBoolean(KEY_HOOK_LOADED, probe.loaded)
                .putString(KEY_HOOK_PID, probe.pid)
                .putLong(KEY_HOOK_CHECKED_AT, System.currentTimeMillis())
                .apply()
        }

        if (probe?.loaded == true) {
            // 系统界面里那版钩子和本次安装的钩子版本不一致：钩子代码只在系统界面启动时载入，需要它重新启动一次
            val stale = probe.vc != null && probe.vc != TaskRunner.HOOK_VERSION_CODE
            return PermRow(
                key = "lsposed",
                title = "LSPosed 模块",
                detail = if (stale)
                    "系统界面（进程 ${probe.pid}）已作出回应，说明模块确已在其中运行；但其加载的仍为旧版本 ${probe.vc}，本次安装的版本为 ${TaskRunner.HOOK_VERSION_CODE}。系统界面仅在启动时读取一次模块代码，重启系统界面即可完成更新，无需重启设备。"
                else
                    "系统界面（进程 ${probe.pid}）已作出回应，模块确已在其中运行，收起锁屏的链路畅通。",
                status = if (stale) PermStatus.UNKNOWN else PermStatus.OK,
                actionLabel = "前往管理器查看",
                action = PermAction.LSPOSED,
                extra = if (stale) "当前模块版本偏旧 · ${probe.at}" else "刚刚核验，状态正常 · ${probe.at}"
            )
        }

        if (probe != null) {
            return PermRow(
                key = "lsposed",
                title = "LSPosed 模块",
                detail = "已找到系统界面（进程 ${probe.pid ?: "未读到"}），但未获回应。请在 LSPosed 中启用本模块，将作用域设为「系统界面」，并重启系统界面（无需重启设备），返回后点击「重新核验」。",
                status = PermStatus.UNKNOWN,
                actionLabel = "前往 LSPosed 管理器",
                action = PermAction.LSPOSED,
                extra = "本次核验时间 ${probe.at}"
            )
        }

        return if (confirmed) {
            PermRow(
                key = "lsposed",
                title = "LSPosed 模块",
                detail = "你已确认模块与作用域。root 验证完成后，此处可自行核验，无需再次手动确认。",
                status = PermStatus.VERIFIED,
                actionLabel = "前往管理器查看",
                action = PermAction.LSPOSED
            )
        } else {
            PermRow(
                key = "lsposed",
                title = "LSPosed 模块",
                detail = "请在 LSPosed 中启用本应用，并将作用域设为「系统界面」。root 验证完成后，此处将直接向系统界面查询模块是否在位，无需手动确认。",
                status = PermStatus.UNKNOWN,
                actionLabel = "前往 LSPosed 管理器",
                action = PermAction.LSPOSED,
                secondActionLabel = "本人确认其已生效",
                secondAction = PermAction.CONFIRM_LSPOSED
            )
        }
    }

    /** 实时探测结果：本模块是否已经被载入系统界面进程；vc 是系统界面里那版钩子的版本号 */
    private data class HookProbe(val loaded: Boolean, val pid: String?, val at: String, val vc: Int? = null)

    /**
     * 验证模块是否真的在系统界面里生效，按可信度依次尝试：
     * 1) 握手：模块在 SystemUI 注册了 PING 接收器，收到就回一行 logcat，app 用 root 读回；
     * 2) maps：LSPosed 把模块代码载进目标进程时会映射本应用的文件；
     * 3) LSPosed 自己的注入日志。
     * 返回 null 表示现在查不了（root 还没验证，不能在这时候弹授权框）。
     */
    private suspend fun probeSystemUiHook(context: Context): HookProbe? {
        if (!prefs(context).getBoolean(KEY_ROOT_VERIFIED, false)) {
            return null
        }
        val pidRes = TaskRunner.su("pidof ${TaskRunner.SYSTEMUI_PKG}")
        if (pidRes.timeout) {
            return null
        }
        val pids = pidRes.out.trim().split(Regex("\\s+"))
            .filter { it.isNotEmpty() && it.all { c -> c.isDigit() } }
        val now = SimpleDateFormat("MM-dd HH:mm", Locale.CHINA).format(Date())
        if (pids.isEmpty()) {
            return HookProbe(false, null, now)
        }
        val firstPid = pids.first()

        // 1) 握手：发一个带随机串的广播，只有收到同串的 pong 才算真正生效
        val token = "t" + System.currentTimeMillis().toString(36)
        val ping = TaskRunner.su(
            "am broadcast -a ${TaskRunner.ACTION_PING} -p ${TaskRunner.SYSTEMUI_PKG} " +
                "--es ${TaskRunner.EXTRA_TOKEN} $token >/dev/null 2>&1; " +
                "sleep 2; logcat -d -s AppTimer:I | tail -n 8"
        )
        if (!ping.timeout && ping.out.contains("token=$token")) {
            val pid = Regex("pong pid=(\\d+)").find(ping.out)?.groupValues?.get(1) ?: firstPid
            val line = ping.out.lineSequence().firstOrNull { it.contains("token=$token") }.orEmpty()
            val vc = Regex("vc=(\\d+)").find(line)?.groupValues?.get(1)?.toIntOrNull()
            return HookProbe(true, pid, now, vc)
        }

        // 2) maps：模块的 apk / dex 被映射进目标进程
        val pkg = context.packageName
        for (pid in pids) {
            val hit = TaskRunner.su("grep -c '$pkg' /proc/$pid/maps 2>/dev/null")
            if (!hit.timeout && (hit.out.trim().toIntOrNull() ?: 0) > 0) {
                return HookProbe(true, pid, now)
            }
        }

        // 3) LSPosed 注入日志（进程刚起来时才有，只做兜底）
        val log = TaskRunner.su("logcat -d -s LSPosed:* | grep 'AppTimer' | tail -n 1")
        if (!log.timeout && log.out.contains("AppTimer")) {
            return HookProbe(true, firstPid, now)
        }
        return HookProbe(false, firstPid, now)
    }

    // ---------------- 厂商后台限制 ----------------

    private fun vendorRow(context: Context): PermRow {
        val confirmed = prefs(context).getBoolean(KEY_VENDOR_CONFIRMED, false)
        return if (confirmed) {
            PermRow(
                key = "vendor",
                title = "自启动 / 后台弹出界面",
                detail = "你已在应用详情中开启「自启动」与「后台弹出界面」。锁屏时能否成功启动，仍以运行日志为准。",
                status = PermStatus.VERIFIED,
                actionLabel = "前往应用详情查看",
                action = PermAction.APP_DETAILS
            )
        } else {
            PermRow(
                key = "vendor",
                title = "自启动 / 后台弹出界面",
                detail = "澎湃、MIUI 等系统默认不允许应用在后台弹出界面。请前往应用详情，开启「自启动」与「后台弹出界面」，否则锁屏状态下可能无法启动。",
                status = PermStatus.UNKNOWN,
                actionLabel = "打开应用详情",
                action = PermAction.APP_DETAILS,
                secondActionLabel = "本人已完成设置",
                secondAction = PermAction.CONFIRM_VENDOR
            )
        }
    }

    /** 用户在系统里确认过之后调用，记下时间点；下次进权限页就显示「已验证」 */
    fun markConfirmed(context: Context, action: PermAction) {
        val key = when (action) {
            PermAction.CONFIRM_LSPOSED -> KEY_LSPOSED_CONFIRMED
            PermAction.CONFIRM_VENDOR -> KEY_VENDOR_CONFIRMED
            else -> return
        }
        prefs(context).edit().putBoolean(key, true).apply()
    }

    /** 撤销确认：把「已验证」改回「需手动确认」，方便重新核对 */
    fun clearConfirmed(context: Context, action: PermAction) {
        val key = when (action) {
            PermAction.CONFIRM_LSPOSED -> KEY_LSPOSED_CONFIRMED
            PermAction.CONFIRM_VENDOR -> KEY_VENDOR_CONFIRMED
            else -> return
        }
        prefs(context).edit().putBoolean(key, false).apply()
    }
}
