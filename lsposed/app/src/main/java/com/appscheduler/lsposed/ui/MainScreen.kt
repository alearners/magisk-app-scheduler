package com.appscheduler.lsposed.ui

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.appscheduler.lsposed.Config
import com.appscheduler.lsposed.LogStore
import com.appscheduler.lsposed.R
import com.appscheduler.lsposed.RunCoordinator
import com.appscheduler.lsposed.TaskRunner
import com.appscheduler.lsposed.model.TaskItem
import com.appscheduler.lsposed.permissions.PermAction
import com.appscheduler.lsposed.permissions.PermRow
import com.appscheduler.lsposed.permissions.PermissionAudit
import com.appscheduler.lsposed.schedule.Scheduler
import com.appscheduler.lsposed.store.TaskStore
import com.appscheduler.lsposed.transfer.ConfigTransfer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 配置页里跳出去的整屏子页面（计划编辑 / 应用选择） */
private enum class SubScreen { EDIT, PICKER }

/**
 * 应用入口：底部菜单栏「配置 / 日志 / 权限 / 帮助」四个入口，
 * 配置页里再跳「计划编辑 / 选择应用」两个整屏子页面。
 */
@Composable
fun MainScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val prefs = remember { Config.get(context) }

    var tasks by remember { mutableStateOf(TaskStore.all(context)) }
    var masterEnabled by remember { mutableStateOf(prefs.enabled()) }
    var subScreen by remember { mutableStateOf<SubScreen?>(null) }
    var tab by remember { mutableStateOf(MainTab.CONFIG) }
    var helpPage by remember { mutableStateOf(HelpPage.HOME) }
    var draft by remember { mutableStateOf<TaskItem?>(null) }
    var runningId by remember { mutableStateOf<Long?>(null) }
    var password by remember { mutableStateOf(prefs.password()) }
    var manualFallback by remember { mutableStateOf(prefs.manualFallback()) }
    var manualWait by remember { mutableStateOf(prefs.manualWait().toString()) }
    var autoLock by remember { mutableStateOf(prefs.autoLock()) }
    var autoLockDelay by remember { mutableStateOf(prefs.autoLockDelay().toString()) }
    var timerMode by remember { mutableStateOf(prefs.timerMode()) }

    var logTab by remember { mutableStateOf(LogTab.CURRENT) }
    var logCurrent by remember { mutableStateOf(LogStore.readCurrent(context)) }
    var logAll by remember { mutableStateOf(LogStore.readAll(context)) }

    var permRows by remember { mutableStateOf<List<PermRow>>(emptyList()) }
    var permChecking by remember { mutableStateOf(true) }

    // 导入是覆盖式的：选完文件先弹确认，拿到确认再真正落盘
    var pendingImport by remember { mutableStateOf<String?>(null) }
    var pendingImportInfo by remember { mutableStateOf("") }

    fun reloadTasks() {
        tasks = TaskStore.all(context)
    }

    fun reloadLog() {
        logCurrent = LogStore.readCurrent(context)
        logAll = LogStore.readAll(context)
    }

    fun recheckPermissions() {
        scope.launch {
            permChecking = true
            permRows = PermissionAudit.collect(context)
            permChecking = false
        }
    }

    fun toast(text: String) {
        scope.launch { snackbar.showSnackbar(text) }
    }

    /** 把当前内存里的全局设置同步回界面状态（导入后会整体变掉） */
    fun reloadSettings() {
        masterEnabled = prefs.enabled()
        password = prefs.password()
        manualFallback = prefs.manualFallback()
        manualWait = prefs.manualWait().toString()
        autoLock = prefs.autoLock()
        autoLockDelay = prefs.autoLockDelay().toString()
        timerMode = prefs.timerMode()
    }

    // 进页面、切到日志/权限 tab 时刷新对应的实时内容
    LaunchedEffect(tab) {
        when (tab) {
            MainTab.LOG -> reloadLog()
            MainTab.PERM -> {
                permChecking = true
                permRows = PermissionAudit.collect(context)
                permChecking = false
            }

            else -> Unit
        }
    }

    // 通知权限申请回来后重新判定
    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { recheckPermissions() }

    fun handlePermAction(action: PermAction) {
        when (action) {
            PermAction.NOTIFICATION -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    openNotificationSettings(context)
                }
            }

            PermAction.NOTIFICATION_SETTINGS -> openNotificationSettings(context)

            PermAction.EXACT_ALARM -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    try {
                        context.startActivity(
                            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                        )
                    } catch (t: Throwable) {
                        toast("无法打开系统设置，请手动授予「闹钟与提醒」权限")
                    }
                } else {
                    toast("当前系统版本不需要单独授予精确闹钟")
                }
            }

            PermAction.BATTERY -> {
                // HyperOS 会把 REQUEST_IGNORE_BATTERY_OPTIMIZATIONS 改跳到「通知与状态列」，
                // 所以优先用 root 把本应用加进 deviceidle 白名单，失败再让用户手动选。
                scope.launch {
                    val done = withContext(Dispatchers.IO) {
                        val r = TaskRunner.su("cmd deviceidle whitelist +" + context.packageName)
                        r.code == 0 && !r.timeout
                    }
                    if (done) {
                        toast("已加入省电白名单，定时不会再被系统推迟")
                    } else {
                        withContext(Dispatchers.IO) {
                            TaskRunner.su(
                                "am start -a android.settings.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS " +
                                    "-d package:" + context.packageName
                            )
                        }
                        toast("请在弹出的页面选择「无限制」")
                    }
                    recheckPermissions()
                }
            }

            PermAction.ROOT -> {
                scope.launch {
                    toast("正在请求 root 授权，请在 Magisk / KernelSU 弹窗里允许…")
                    val ok = PermissionAudit.verifyRoot(context)
                    toast(if (ok) "root 权限验证通过" else "root 授权未通过：请在管理器里对本应用授予 root")
                    recheckPermissions()
                }
            }

            PermAction.LSPOSED -> {
                val opened = openLsposedManager(context)
                toast(if (opened) "请在 LSPosed 管理器里确认模块已勾选、作用域为「系统界面」" else "没有找到 LSPosed 管理器")
            }

            PermAction.APP_DETAILS -> {
                try {
                    context.startActivity(
                        Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.parse("package:" + context.packageName)
                        )
                    )
                } catch (t: Throwable) {
                    toast("无法打开应用详情页")
                }
            }

            // 只有用户主动点这一下，才真去读一次应用列表（部分系统到这里才弹授权框）
            PermAction.PACKAGE_CHECK -> {
                scope.launch {
                    val count = PermissionAudit.checkPackages(context)
                    if (count >= 10) {
                        toast("读到 $count 个应用，「选择应用」可以正常列出")
                    } else {
                        toast("只读到 $count 个应用：请在应用详情里允许「取得已安装应用资讯」")
                    }
                    recheckPermissions()
                }
            }
        }
    }

    // 导出：系统文件选择器给一个目标路径，直接把 JSON 写进去
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(ConfigTransfer.MIME)
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val ok = withContext(Dispatchers.IO) {
                    try {
                        context.contentResolver.openOutputStream(uri)?.use { out ->
                            out.write(ConfigTransfer.exportJson(context).toByteArray(Charsets.UTF_8))
                            out.flush()
                        } != null
                    } catch (t: Throwable) {
                        LogStore.append(context, "导出配置失败: $t")
                        false
                    }
                }
                toast(if (ok) "配置已导出（含明文锁屏密码，注意保管）" else "导出失败，换一个目录再试")
            }
        }
    }

    // 导入：读文件 → 校验 → 弹确认 → 覆盖写入
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val text = withContext(Dispatchers.IO) {
                    try {
                        context.contentResolver.openInputStream(uri)?.use {
                            it.readBytes().toString(Charsets.UTF_8)
                        }
                    } catch (t: Throwable) {
                        LogStore.append(context, "读取导入文件失败: $t")
                        null
                    }
                }
                if (text == null) {
                    toast("读取文件失败，换一个文件试试")
                    return@launch
                }
                val info = withContext(Dispatchers.IO) { ConfigTransfer.inspect(text) }
                if (!info.ok) {
                    toast(info.message)
                } else {
                    pendingImportInfo = "${info.message}，${info.taskCount} 个计划"
                    pendingImport = text
                }
            }
        }
    }

    fun saveSettings() {
        prefs.saveGlobal(
            masterEnabled,
            password,
            manualFallback,
            manualWait.toIntOrNull() ?: Config.DEFAULT_MANUAL_WAIT,
            autoLock,
            autoLockDelay.toIntOrNull() ?: Config.DEFAULT_AUTO_LOCK_DELAY,
            timerMode
        )
        // 计时方式可能变了：切内部计时器要拉起常驻服务，切回闹钟要重排闹钟
        Scheduler.applyMode(context)
        reloadLog()
        val modeText = if (timerMode == Config.TIMER_INTERNAL) "应用内部计时器" else "系统闹钟"
        toast(
            if (masterEnabled) "已保存，定时已启用（$modeText）"
            else "已保存，定时未启用"
        )
    }

    fun saveDraft() {
        val task = draft ?: return
        if (task.packages.isEmpty()) {
            toast("请先选择要启动的应用")
            return
        }
        TaskStore.upsert(context, task)
        Scheduler.schedule(context, task)
        draft = null
        subScreen = null
        reloadTasks()
        toast("已保存计划：${task.title()}")
    }

    fun toggleTask(task: TaskItem, enabled: Boolean) {
        val updated = task.copy(enabled = enabled)
        TaskStore.upsert(context, updated)
        Scheduler.schedule(context, updated)
        reloadTasks()
    }

    fun deleteTask(task: TaskItem) {
        Scheduler.cancel(context, task.id)
        TaskStore.delete(context, task.id)
        reloadTasks()
        toast("已删除计划：${task.title()}")
    }

    fun runNow(task: TaskItem) {
        if (runningId != null) return
        runningId = task.id
        scope.launch {
            withContext(Dispatchers.IO) {
                RunCoordinator.runNow(context, task.packages, task.forceColdStart)
            }
            runningId = null
            reloadTasks()
            reloadLog()
            toast("执行完成，详见「日志」页")
        }
    }

    val nextRunText = remember(tasks, masterEnabled) {
        describeSoonest(context, tasks, masterEnabled)
    }

    val exactAlarmGranted = remember {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            true
        } else {
            // 在 Paparazzi/layoutlib 里这个调用不可用，异常时按「已授予」处理，避免截图与实际不符
            try {
                val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
                am?.canScheduleExactAlarms() ?: true
            } catch (t: Throwable) {
                true
            }
        }
    }

    // 省电白名单状态：点过「关闭电池优化」后 +1 强制重算，否则界面会一直显示旧状态
    var powerTick by remember { mutableStateOf(0) }
    val batteryIgnored = remember(powerTick) { isBatteryOptimizationIgnored(context) }

    val about = remember { loadAboutInfo(context) }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { innerPadding ->
        pendingImport?.let { text ->
            AlertDialog(
                onDismissRequest = { pendingImport = null },
                title = { Text("用这个文件覆盖当前配置？") },
                text = {
                    Text(
                        "文件信息：$pendingImportInfo。\n" +
                            "当前有 ${tasks.size} 个计划，导入后会被全部替换，全局设置（含锁屏密码）也一起覆盖。"
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        pendingImport = null
                        scope.launch {
                            val res = withContext(Dispatchers.IO) {
                                ConfigTransfer.importJson(context, text)
                            }
                            if (res.ok) {
                                reloadSettings()
                                reloadTasks()
                            }
                            toast(res.message)
                        }
                    }) { Text("覆盖导入") }
                },
                dismissButton = {
                    TextButton(onClick = { pendingImport = null }) { Text("取消") }
                }
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Box(modifier = Modifier.weight(1f)) {
                when (subScreen) {
                    SubScreen.EDIT -> TaskEditScreen(
                        task = draft ?: TaskItem(),
                        onChange = { draft = it },
                        onPickApps = { subScreen = SubScreen.PICKER },
                        onSave = { saveDraft() },
                        onBack = {
                            draft = null
                            subScreen = null
                        }
                    )

                    SubScreen.PICKER -> AppPickerScreen(
                        selected = draft?.packages ?: emptyList(),
                        onConfirm = { picked ->
                            draft = draft?.copy(packages = picked.toMutableList())
                            subScreen = SubScreen.EDIT
                        },
                        onBack = { subScreen = SubScreen.EDIT }
                    )

                    null -> when (tab) {
                        MainTab.CONFIG -> TaskListScreen(
                            tasks = tasks,
                            masterEnabled = masterEnabled,
                            onMasterEnabledChange = { masterEnabled = it },
                            nextRunText = nextRunText,
                            onAdd = {
                                draft = TaskItem()
                                subScreen = SubScreen.EDIT
                            },
                            onEdit = { task ->
                                draft = task.copy(
                                    packages = task.packages.toMutableList(),
                                    times = task.times.toMutableList(),
                                    weekdays = task.weekdays.toMutableSet()
                                )
                                subScreen = SubScreen.EDIT
                            },
                            onDelete = { deleteTask(it) },
                            onToggle = { task, enabled -> toggleTask(task, enabled) },
                            onRunNow = { runNow(it) },
                            runningId = runningId,
                            password = password,
                            onPasswordChange = { password = it },
                            manualFallback = manualFallback,
                            onManualFallbackChange = { manualFallback = it },
                            manualWait = manualWait,
                            onManualWaitChange = { manualWait = it },
                            autoLock = autoLock,
                            onAutoLockChange = { autoLock = it },
                            autoLockDelay = autoLockDelay,
                            onAutoLockDelayChange = { autoLockDelay = it },
                            timerMode = timerMode,
                            onTimerModeChange = { timerMode = it },
                            onSaveSettings = { saveSettings() },
                            onOpenLog = { tab = MainTab.LOG },
                            onOpenHelp = { tab = MainTab.HELP },
                            exactAlarmGranted = exactAlarmGranted,
                            onRequestExactAlarm = { handlePermAction(PermAction.EXACT_ALARM) },
                            batteryIgnored = batteryIgnored,
                            onRequestIgnoreBattery = {
                                scope.launch {
                                    val done = withContext(Dispatchers.IO) {
                                        val r = TaskRunner.su("cmd deviceidle whitelist +" + context.packageName)
                                        r.code == 0 && !r.timeout
                                    }
                                    if (done) {
                                        powerTick++
                                        toast("已加入省电白名单，定时不会再被系统推迟")
                                    } else {
                                        withContext(Dispatchers.IO) {
                                            TaskRunner.su(
                                                "am start -a android.settings.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS " +
                                                    "-d package:" + context.packageName
                                            )
                                        }
                                        toast("请在弹出的页面选择「无限制」")
                                    }
                                }
                            }
                        )

                        MainTab.LOG -> LogScreen(
                            current = logCurrent,
                            all = logAll,
                            tab = logTab,
                            onSelectTab = { logTab = it },
                            onRefresh = { reloadLog() },
                            onClearCurrent = {
                                LogStore.clearCurrent(context)
                                reloadLog()
                                toast("已清除当前日志")
                            },
                            onClearAll = {
                                LogStore.clearAll(context)
                                reloadLog()
                                toast("已清除所有日志")
                            }
                        )

                        MainTab.PERM -> PermissionScreen(
                            rows = permRows,
                            checking = permChecking,
                            onRecheck = { recheckPermissions() },
                            onAction = { handlePermAction(it) }
                        )

                        MainTab.HELP -> HelpScreen(
                            page = helpPage,
                            about = about,
                            onOpen = { helpPage = it },
                            onBack = { helpPage = HelpPage.HOME },
                            onExportConfig = { exportLauncher.launch(ConfigTransfer.defaultFileName()) },
                            onImportConfig = { importLauncher.launch(arrayOf("*/*")) }
                        )
                    }
                }
            }

            // 整屏子页面（编辑/选择应用）不显示底部栏，返回后回到原 tab
            if (subScreen == null) {
                BottomTabBar(selected = tab, onSelect = { tab = it })
            }
        }
    }
}

/** 是否已在系统省电白名单里（Paparazzi / layoutlib 里取不到时按「已放行」处理，避免截图与实际不符） */
private fun isBatteryOptimizationIgnored(context: Context): Boolean {
    return try {
        val pm = context.getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager
        pm?.isIgnoringBatteryOptimizations(context.packageName) ?: true
    } catch (t: Throwable) {
        true
    }
}

private fun openNotificationSettings(context: Context) {
    try {
        context.startActivity(
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        )
    } catch (t: Throwable) {
        try {
            context.startActivity(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + context.packageName)
                )
            )
        } catch (ignored: Throwable) {
            // 打不开就算了，页面上的状态仍然可见
        }
    }
}

private fun openLsposedManager(context: Context): Boolean {
    val launch = context.packageManager.getLaunchIntentForPackage("org.lsposed.manager")
    if (launch != null) {
        try {
            context.startActivity(launch)
            return true
        } catch (t: Throwable) {
            return false
        }
    }
    return false
}

/** 版本信息集中在关于页展示；取不到时用占位值，不让页面崩 */
private fun loadAboutInfo(context: Context): AboutInfo {
    return try {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        val code = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            info.versionCode.toLong()
        }
        AboutInfo(
            appName = context.getString(R.string.app_name),
            versionName = info.versionName ?: "-",
            versionCode = code,
            packageName = context.packageName,
            minSdk = context.applicationInfo.minSdkVersion
        )
    } catch (t: Throwable) {
        AboutInfo(packageName = context.packageName)
    }
}

/** 所有启用计划里最近一次的触发时间 */
private fun describeSoonest(context: Context, tasks: List<TaskItem>, masterEnabled: Boolean): String {
    if (!masterEnabled) return "未启用"
    val usable = tasks.filter { it.enabled && it.packages.isNotEmpty() }
    if (usable.isEmpty()) return "暂无计划"
    val soonest = Scheduler.soonest(context) ?: return "无有效时间点"
    val stamp = SimpleDateFormat("MM-dd HH:mm", Locale.CHINA).format(Date(soonest.second))
    return "$stamp · ${soonest.first.title()}"
}
