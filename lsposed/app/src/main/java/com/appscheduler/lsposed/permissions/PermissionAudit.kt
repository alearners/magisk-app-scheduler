package com.appscheduler.lsposed.permissions

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import com.appscheduler.lsposed.TaskRunner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

/** 单项权限的判定结果 */
enum class PermStatus(val label: String) {
    OK("已就绪"),
    MISSING("未授予"),
    UNKNOWN("需手动确认")
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
    PACKAGE_CHECK           // 由用户主动点一次「读应用列表」（某些系统这时才会弹授权框）
}

data class PermRow(
    val key: String,
    val title: String,
    val detail: String,
    val status: PermStatus,
    val actionLabel: String? = null,
    val action: PermAction? = null,
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
    private const val KEY_ROOT_UID = "root_uid"
    private const val KEY_PKG_COUNT = "pkg_count"
    private const val KEY_PKG_CHECKED_AT = "pkg_checked_at"

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
        rows += vendorRow()
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
                detail = "「请手动解锁」「执行失败」这类提醒都靠它。Android 13 起需要在系统弹窗里允许。",
                status = PermStatus.MISSING,
                actionLabel = "申请",
                action = PermAction.NOTIFICATION
            )

            !enabled -> PermRow(
                key = "notification",
                title = "通知权限",
                detail = "系统里把本应用的通知关掉了：运行提醒和失败通知都不会出现。",
                status = PermStatus.MISSING,
                actionLabel = "去通知设置",
                action = PermAction.NOTIFICATION_SETTINGS
            )

            else -> PermRow(
                key = "notification",
                title = "通知权限",
                detail = "运行提醒与失败通知可以正常弹出。",
                status = PermStatus.OK,
                extra = "已允许"
            )
        }
    }

    // ---------------- 精确闹钟 ----------------

    private fun exactAlarmRow(context: Context): PermRow {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return PermRow(
                key = "exact_alarm",
                title = "精确闹钟",
                detail = "Android 11 及以下不需要单独授权，到点直接唤醒。",
                status = PermStatus.OK,
                extra = "系统版本无需授权"
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
            detail = "没这个权限，系统只能用非精确闹钟，触发可能推迟几分钟才执行。",
            status = if (granted) PermStatus.OK else PermStatus.MISSING,
            actionLabel = if (granted) null else "去授予",
            action = if (granted) null else PermAction.EXACT_ALARM,
            extra = if (granted) "已允许精确调度" else null
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
            detail = "被系统电池优化限制时，闹钟可能被推迟甚至被丢进「后台受限」里。",
            status = if (ignored) PermStatus.OK else PermStatus.MISSING,
            actionLabel = if (ignored) null else "用 root 加入白名单",
            action = if (ignored) null else PermAction.BATTERY,
            extra = if (ignored) "已不限制" else null
        )
    }

    // ---------------- root ----------------

    private fun rootRow(context: Context): PermRow {
        val p = prefs(context)
        val verified = p.getBoolean(KEY_ROOT_VERIFIED, false)
        val uid = p.getString(KEY_ROOT_UID, null)
        val suPresent = suBinaryPresent()
        return when {
            verified -> PermRow(
                key = "root",
                title = "root 权限",
                detail = "解锁（上滑/密码注入）、强制停止与启动应用、加入省电白名单都要用 root。",
                status = PermStatus.OK,
                extra = uid?.let { "已验证 $it" } ?: "已验证"
            )

            suPresent -> PermRow(
                key = "root",
                title = "root 权限",
                detail = "设备上有 su，但还没验证过；点「申请 root 权限」会弹一次 Magisk/KernelSU 授权框。",
                status = PermStatus.UNKNOWN,
                actionLabel = "申请 root 权限",
                action = PermAction.ROOT
            )

            else -> PermRow(
                key = "root",
                title = "root 权限",
                detail = "没有检测到 su：本模块必须在已 root 的设备上使用（Magisk / KernelSU）。",
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
        if (ok) {
            val uidLine = result.out.lineSequence().firstOrNull { it.contains("uid=0") }?.trim()
            if (uidLine != null) {
                p.edit().putString(KEY_ROOT_UID, uidLine).apply()
            }
        }
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
                detail = "用来在「选择应用」里列出已安装的应用；当前可以正常读取。",
                status = PermStatus.OK,
                extra = "可见 $count 个应用"
            )

            count >= 0 -> PermRow(
                key = "packages",
                title = "读取应用列表",
                detail = "只读到 $count 个应用，基本等于被系统限制了：去应用详情里允许「读取应用列表 / 取得已安装应用资讯」。",
                status = PermStatus.MISSING,
                actionLabel = "打开应用详情",
                action = PermAction.APP_DETAILS
            )

            else -> PermRow(
                key = "packages",
                title = "读取应用列表",
                detail = "还没读过应用列表。点「现在检查」读一次；部分系统（HyperOS、MIUI）这时会弹一次「取得已安装的应用程式资讯」，允许后「选择应用」才能看到全部应用。",
                status = PermStatus.UNKNOWN,
                actionLabel = "现在检查",
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

    private fun lsposedRow(context: Context): PermRow {
        val managerInstalled = try {
            context.packageManager.getPackageInfo("org.lsposed.manager", 0)
            true
        } catch (t: Throwable) {
            false
        }
        return PermRow(
            key = "lsposed",
            title = "LSPosed 模块",
            detail = "本模块要在 LSPosed 里勾选并作用到「系统界面」，锁屏才能真正被收起。App 自身无法判断是否已生效。",
            status = PermStatus.UNKNOWN,
            actionLabel = if (managerInstalled) "打开 LSPosed 管理器" else "未安装 LSPosed 管理器",
            action = if (managerInstalled) PermAction.LSPOSED else null
        )
    }

    // ---------------- 厂商后台限制 ----------------

    private fun vendorRow(): PermRow = PermRow(
        key = "vendor",
        title = "自启动 / 后台弹出界面",
        detail = "HyperOS、MIUI 等系统默认拦后台拉起界面：需在应用详情里允许「自启动」与「后台弹出界面」，否则锁屏时可能起不来。",
        status = PermStatus.UNKNOWN,
        actionLabel = "打开应用详情",
        action = PermAction.APP_DETAILS
    )
}
