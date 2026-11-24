package com.appscheduler.lsposed.ui

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
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
    // 主菜单的浏览历史：返回键先退回上一个主菜单，最后才退出程序
    var tabStack by remember { mutableStateOf(listOf(MainTab.OVERVIEW)) }
    val tab = tabStack.last()
    var helpPage by remember { mutableStateOf(HelpPage.HOME) }
    var draft by remember { mutableStateOf<TaskItem?>(null) }
    var runningId by remember { mutableStateOf<Long?>(null) }
    var password by remember { mutableStateOf(prefs.password()) }
    var manualFallback by remember { mutableStateOf(prefs.manualFallback()) }
    var manualWait by remember { mutableStateOf(prefs.manualWait().toString()) }
    var autoLock by remember { mutableStateOf(prefs.autoLock()) }
    var autoLockDelay by remember { mutableStateOf(prefs.autoLockDelay().toString()) }
    var timerMode by remember { mutableStateOf(prefs.timerMode()) }

    var logDate by remember { mutableStateOf(LogStore.today()) }
    var logLevel by remember { mutableStateOf<LogStore.Level?>(null) }
    var logDates by remember { mutableStateOf(LogStore.dates(context)) }
    var logText by remember { mutableStateOf("") }
    var logLimitKb by remember { mutableStateOf(prefs.logLimitKb()) }

    var permRows by remember { mutableStateOf<List<PermRow>>(emptyList()) }
    var permChecking by remember { mutableStateOf(false) }

    // 同意使用说明的勾选状态：未勾选时不排闹钟、不执行计划，也不做权限体检
    var agreed by remember { mutableStateOf(prefs.agreed()) }

    // 导入是覆盖式的：选完文件先弹确认，拿到确认再真正落盘
    var pendingImport by remember { mutableStateOf<String?>(null) }
    var pendingImportInfo by remember { mutableStateOf("") }

    fun reloadTasks() {
        tasks = TaskStore.all(context)
    }

    fun reloadLog() {
        logDates = LogStore.dates(context)
        logText = LogStore.readDate(context, logDate, logLevel)
    }

    fun recheckPermissions() {
        if (!prefs.agreed()) {
            permRows = emptyList()
            permChecking = false
            return
        }
        scope.launch {
            permChecking = true
            permRows = PermissionAudit.collect(context)
            permChecking = false
        }
    }

    fun toast(text: String) {
        scope.launch { snackbar.showSnackbar(text) }
    }

    /**
     * 点底部菜单：换 tab 时记一条历史；点当前 tab 时回到这个 tab 的首页
     * （「更多」里的子页会被收回，其它三个 tab 本来就是首页）。
     */
    fun selectTab(next: MainTab) {
        if (next == tab) {
            if (next == MainTab.MORE) {
                helpPage = HelpPage.HOME
            }
            if (next == MainTab.LOG) {
                reloadLog()
            }
            return
        }
        // 回到已经走过的 tab 时截断它后面的历史，避免来回点堆出一长串
        val existing = tabStack.indexOf(next)
        tabStack = when {
            existing >= 0 -> tabStack.take(existing + 1)
            tabStack.size >= 5 -> tabStack.drop(1) + next
            else -> tabStack + next
        }
        // 每次从底部栏进「更多」都先落在首页，子页只由「更多」页里的入口打开
        if (next == MainTab.MORE) {
            helpPage = HelpPage.HOME
        }
        // 日志一直在写，每次切进来都读一遍最新的，免得看到的是上次留下的旧内容
        if (next == MainTab.LOG) {
            reloadLog()
        }
    }

    // 返回键：子页 → 上一页（编辑/选择 → 配置；说明子页 → 更多首页；其它 tab → 上一个 tab），
    // 都已经是最外层时才交给系统退出。
    BackHandler(
        enabled = subScreen != null ||
            (tab == MainTab.MORE && helpPage != HelpPage.HOME) ||
            tabStack.size > 1
    ) {
        when {
            subScreen == SubScreen.PICKER -> subScreen = SubScreen.EDIT
            subScreen == SubScreen.EDIT -> {
                // 返回等于放弃这次编辑，草稿不留
                draft = null
                subScreen = null
            }
            tab == MainTab.MORE && helpPage != HelpPage.HOME -> helpPage = HelpPage.HOME
            tabStack.size > 1 -> tabStack = tabStack.dropLast(1)
        }
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
        logLimitKb = prefs.logLimitKb()
    }

    // 进页面、切到日志/概览 tab 时刷新对应的实时内容
    LaunchedEffect(tab) {
        when (tab) {
            MainTab.LOG -> reloadLog()
            MainTab.OVERVIEW -> recheckPermissions()
            else -> Unit
        }
    }

    /** 勾选 / 取消「同意使用说明」：取消后立刻撤掉全部闹钟与计时服务 */
    fun setAgreed(value: Boolean) {
        prefs.setAgreed(value)
        agreed = value
        if (value) {
            Scheduler.applyMode(context)
            recheckPermissions()
            toast("已确认同意。自此刻起，计划将按时间表执行。")
        } else {
            Scheduler.applyMode(context)
            permRows = emptyList()
            toast("已撤回同意，闹钟与计划均已停止。")
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
                        toast("未能打开系统设置，请手动允许「闹钟与提醒」。")
                    }
                } else {
                    toast("该系统无需单独授予，可直接准时唤醒。")
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
                        toast("已将本应用加入省电白名单，系统不再对其施加限制。")
                    } else {
                        withContext(Dispatchers.IO) {
                            TaskRunner.su(
                                "am start -a android.settings.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS " +
                                    "-d package:" + context.packageName
                            )
                        }
                        toast("请在打开的页面中选择「无限制」。")
                    }
                    recheckPermissions()
                }
            }

            PermAction.ROOT -> {
                scope.launch {
                    toast("正在请求 root 授权，请在 Magisk / KernelSU 弹窗中选择允许…")
                    val ok = PermissionAudit.verifyRoot(context)
                    toast(if (ok) "root 权限已验证。" else "root 未通过，请前往管理器授予权限。")
                    recheckPermissions()
                }
            }

            PermAction.LSPOSED -> {
                scope.launch {
                    val opened = withContext(Dispatchers.IO) { openLsposedManager(context) }
                    toast(
                        if (opened) "请在 LSPosed 管理器中核对：模块是否已启用，作用域是否为「系统界面」。"
                        else "未能打开 LSPosed 管理器：请确认 root 可用，或从 Magisk / KernelSU 的模块菜单进入。"
                    )
                }
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
                    toast("未能打开应用详情页。")
                }
            }

            // 只有用户主动点这一下，才真去读一次应用列表（部分系统到这里才弹授权框）
            PermAction.CONFIRM_LSPOSED, PermAction.CONFIRM_VENDOR -> {
                PermissionAudit.markConfirmed(context, action)
                toast(
                    if (action == PermAction.CONFIRM_LSPOSED)
                        "已记录：LSPosed 一项此后无需再次确认。"
                    else "已记录：自启动 / 后台弹出界面一项此后无需再次确认。"
                )
                recheckPermissions()
            }

            PermAction.PACKAGE_CHECK -> {
                scope.launch {
                    val count = PermissionAudit.checkPackages(context)
                    if (count >= 10) {
                        toast("已检测到 $count 个应用，「应用选择」可正常列出。")
                    } else {
                        toast("仅检测到 $count 个应用，请前往应用详情允许「取得已安装应用资讯」。")
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
                        LogStore.appendError(context, "导出配置失败: $t")
                        false
                    }
                }
                toast(if (ok) "导出完成（文件内含明文锁屏密码，请妥善保管）。" else "导出失败，请更换保存位置后重试。")
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
                        LogStore.appendError(context, "读取导入文件失败: $t")
                        null
                    }
                }
                if (text == null) {
                    toast("该文件无法读取，请更换文件。")
                    return@launch
                }
                val info = withContext(Dispatchers.IO) { ConfigTransfer.inspect(text) }
                if (!info.ok) {
                    toast(info.message)
                } else {
                    pendingImportInfo = "${info.message}，共 ${info.taskCount} 份计划"
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
            if (masterEnabled) "设置已保存，定时功能已启用（$modeText）。"
            else "设置已保存，但定时功能处于停用状态。"
        )
    }

    fun saveDraft() {
        val task = draft ?: return
        if (task.packages.isEmpty()) {
            toast("请先选择需要启动的应用。")
            return
        }
        TaskStore.upsert(context, task)
        Scheduler.schedule(context, task)
        draft = null
        subScreen = null
        reloadTasks()
        toast("计划已保存：${task.title()}。")
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
        toast("计划已删除：${task.title()}。")
    }

    fun runNow(task: TaskItem) {
        if (runningId != null) return
        runningId = task.id
        scope.launch {
            withContext(Dispatchers.IO) {
                RunCoordinator.runNow(context, task.packages, task.forceColdStart, task.launchMode)
            }
            runningId = null
            reloadTasks()
            reloadLog()
            toast("执行完成，详细信息见「日志」页。")
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

    var about by remember { mutableStateOf(AboutInfo(packageName = context.packageName)) }
    LaunchedEffect(Unit) {
        about = withContext(Dispatchers.IO) { loadAboutInfo(context) }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { innerPadding ->
        pendingImport?.let { text ->
            AlertDialog(
                onDismissRequest = { pendingImport = null },
                title = { Text("确认以该文件替换当前配置？") },
                text = {
                    Text(
                        "文件中包含：$pendingImportInfo。\n" +
                            "当前共有 ${tasks.size} 份计划，导入后将被整体替换；全局设置（含锁屏密码）亦将一并替换。"
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
                    }) { Text("确认替换") }
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
                            onOpenLog = { selectTab(MainTab.LOG) },
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
                                        toast("已将本应用加入省电白名单，系统不再对其施加限制。")
                                    } else {
                                        withContext(Dispatchers.IO) {
                                            TaskRunner.su(
                                                "am start -a android.settings.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS " +
                                                    "-d package:" + context.packageName
                                            )
                                        }
                                        toast("请在打开的页面中选择「无限制」。")
                                    }
                                }
                            }
                        )

                        MainTab.LOG -> LogScreen(
                            dates = logDates,
                            selectedDate = logDate,
                            level = logLevel,
                            text = logText,
                            limitKb = logLimitKb,
                            onSelectDate = {
                                logDate = it
                                reloadLog()
                            },
                            onSelectLevel = {
                                logLevel = it
                                reloadLog()
                            },
                            onRefresh = { reloadLog() },
                            onClearDate = {
                                LogStore.clearDate(context, logDate)
                                reloadLog()
                                toast("已清除 $logDate 的日志。")
                            },
                            onClearAll = {
                                LogStore.clearAll(context)
                                reloadLog()
                                toast("已清除全部日志。")
                            },
                            onSaveLimit = { kb ->
                                prefs.setLogLimitKb(kb)
                                logLimitKb = prefs.logLimitKb()
                                reloadLog()
                                toast("日志单日上限已设为 ${logLimitKb} KB。")
                            }
                        )

                        MainTab.OVERVIEW -> PermissionScreen(
                            rows = permRows,
                            about = about,
                            checking = permChecking,
                            agreed = agreed,
                            onAgreedChange = { setAgreed(it) },
                            onOpenTerms = {
                                selectTab(MainTab.MORE)
                                helpPage = HelpPage.TERMS
                            },
                            onRecheck = { recheckPermissions() },
                            onAction = { handlePermAction(it) }
                        )

                        MainTab.MORE -> HelpScreen(
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
                BottomTabBar(selected = tab, onSelect = { selectTab(it) })
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

/**
 * 打开 LSPosed 管理器，按装法依次尝试：
 * 1) 独立安装的 org.lsposed.manager；
 * 2) 寄生在 com.android.shell 里的管理器组件；
 * 3) LSPosed 2.1.0 那种只能靠 secret code 广播拉起的装法（需要 root）。
 */
private fun openLsposedManager(context: Context): Boolean {
    val launches = listOfNotNull(
        context.packageManager.getLaunchIntentForPackage("org.lsposed.manager"),
        // 有些装法把管理器塞在 com.android.shell 里，包名查不到，只能按组件直接打
        Intent().setComponent(
            android.content.ComponentName(
                "com.android.shell",
                "org.lsposed.manager.ui.activity.MainActivity"
            )
        )
    )
    for (launch in launches) {
        try {
            context.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return true
        } catch (t: Throwable) {
            // 换下一条路子
        }
    }
    // 管理器没装成独立 App 时，LSPosed 靠这条 secret code 把它拉进 shell 进程
    val action = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        "android.telephony.action.SECRET_CODE"
    } else {
        "android.provider.Telephony.SECRET_CODE"
    }
    val r = TaskRunner.su("am broadcast -a $action -d android_secret_code://5776733 android")
    return !r.timeout && r.out.contains("Broadcast completed")
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
            minSdk = context.applicationInfo.minSdkVersion,
            lsposedVersion = lsposedVersion(context)
        )
    } catch (t: Throwable) {
        AboutInfo(packageName = context.packageName)
    }
}

/** 读取已安装的 LSPosed 版本：先查管理器应用，再读模块元数据；均失败才写「未检测到」 */
private fun lsposedVersion(context: Context): String {
    val managers = listOf("org.lsposed.manager", "org.lsposed.manager.debug")
    for (pkg in managers) {
        try {
            val info = context.packageManager.getPackageInfo(pkg, 0)
            val name = info.versionName
            if (!name.isNullOrBlank()) return name
        } catch (_: Throwable) {
            // 没装这个包，换下一个候选
        }
    }
    // 部分安装方式只在 Magisk 模块内提供管理器，不注册为应用；改读模块元数据
    val moduleDirs = listOf(
        "/data/adb/modules/zygisk_lsposed",
        "/data/adb/modules/lsposed",
        "/data/adb/modules/riru_lsposed"
    )
    for (dir in moduleDirs) {
        val r = TaskRunner.su("cat $dir/module.prop")
        if (r.timeout || r.code != 0) continue
        val version = Regex("^version=(.+)$", RegexOption.MULTILINE)
            .find(r.out)?.groupValues?.get(1)?.trim() ?: continue
        val clean = version.removePrefix("v").substringBefore(" (").trim()
        if (clean.isNotEmpty()) return clean
    }
    return "未检测到"
}

/** 所有启用计划里最近一次的触发时间 */
private fun describeSoonest(context: Context, tasks: List<TaskItem>, masterEnabled: Boolean): String {
    if (!masterEnabled) return "定时功能已停用"
    val usable = tasks.filter { it.enabled && it.packages.isNotEmpty() }
    if (usable.isEmpty()) return "暂无已启用计划"
    val soonest = Scheduler.soonest(context) ?: return "尚未设定时间"
    val stamp = SimpleDateFormat("MM-dd HH:mm", Locale.CHINA).format(Date(soonest.second))
    return "$stamp · ${soonest.first.title()}"
}
