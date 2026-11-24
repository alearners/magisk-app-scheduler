package com.appscheduler.lsposed

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.appscheduler.lsposed.apps.AppEntry
import com.appscheduler.lsposed.model.TaskItem
import com.appscheduler.lsposed.permissions.PermAction
import com.appscheduler.lsposed.permissions.PermRow
import com.appscheduler.lsposed.permissions.PermStatus
import com.appscheduler.lsposed.ui.AboutInfo
import com.appscheduler.lsposed.ui.AppPickerScreen
import com.appscheduler.lsposed.ui.AppSchedulerTheme
import com.appscheduler.lsposed.ui.BottomTabBar
import com.appscheduler.lsposed.ui.HelpPage
import com.appscheduler.lsposed.ui.HelpScreen
import com.appscheduler.lsposed.ui.LaunchModeSection
import com.appscheduler.lsposed.ui.LogScreen
import com.appscheduler.lsposed.ui.MainTab
import com.appscheduler.lsposed.ui.PermissionScreen
import com.appscheduler.lsposed.ui.TaskEditScreen
import com.appscheduler.lsposed.ui.TaskListScreen
import org.junit.Rule
import org.junit.Test

/**
 * 用 Paparazzi（layoutlib）在 JVM 上渲染真实 Compose 界面，输出 README 用截图。
 * 运行：gradle -p lsposed :app:recordPaparazziDebug
 * 产物：app/src/test/snapshots/images 目录下的 PNG
 */
class ScreenshotTest {

    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5.copy(softButtons = false),
        theme = "android:Theme.Material.Light.NoActionBar",
        showSystemUi = false
    )

    private val demoLog = """
        [2026-11-08 07:30:12][运行] 计划「早间自动签到」开始，共 2 个应用
        [2026-11-08 07:30:12][调试] 已排闹钟「早间自动签到」→ 11-08 21:30:00
        [2026-11-08 07:30:12][运行] 锁屏状态：已锁
        [2026-11-08 07:30:13][运行] LSPosed 钩子已收起锁屏
        [2026-11-08 07:30:14][运行] 启动 com.example.notes 成功
        [2026-11-08 07:31:02][运行] 启动 com.example.reader 成功
        [2026-11-08 07:31:03][运行] 计划完成，耗时 1.2s
        [2026-11-08 09:00:00][运行] 条件不满足（未连接 WiFi），本次跳过
        [2026-11-08 09:00:01][调试] 握手检测：loaded=true pid=7373 hookVc=31
        [2026-11-08 12:00:00][运行] 计划「夜间同步」开始，共 1 个应用
        [2026-11-08 12:00:05][错误] 守候服务启动被拒，仅靠超时闹钟与解锁广播兜底
        [2026-11-08 12:03:00][运行] 检测到手动解锁，3 秒后启动
        [2026-11-08 12:03:03][运行] 启动 com.example.cloud 成功
    """.trimIndent()

    private val demoErrors = """
        [2026-11-08 12:00:05][错误] 守候服务启动被拒，仅靠超时闹钟与解锁广播兜底
        [2026-11-08 15:42:11][错误] 前台服务执行异常: SecurityException: INJECT_EVENTS
    """.trimIndent()

    private val permRows = listOf(
        PermRow(
            key = "notification",
            title = "通知权限",
            detail = "运行提醒与失败提醒均可正常送达。",
            status = PermStatus.OK,
            extra = "已获允许"
        ),
        PermRow(
            key = "exact_alarm",
            title = "精确闹钟",
            detail = "若未授予，系统仅能按大致时间触发，可能延后数分钟。",
            status = PermStatus.MISSING,
            actionLabel = "前往授予",
            action = PermAction.EXACT_ALARM
        ),
        PermRow(
            key = "battery",
            title = "省电白名单",
            detail = "系统启用省电限制后，定时可能被延后；情况严重时，本应用会被列入后台受限名单而不再触发。",
            status = PermStatus.OK,
            extra = "已不受省电限制"
        ),
        PermRow(
            key = "root",
            title = "root 权限",
            detail = "解锁（上滑、输入密码）、强制停止与启动应用、加入省电白名单，均依赖此项。",
            status = PermStatus.OK,
            extra = "已验证，确实取得了 root"
        ),
        PermRow(
            key = "packages",
            title = "读取应用列表",
            detail = "尚未统计应用列表。请点击「现在核验」执行一次；澎湃、MIUI 等系统会同时征求「取得已安装的应用资讯」许可，允许后「应用选择」才能完整显示。",
            status = PermStatus.UNKNOWN,
            actionLabel = "现在核验",
            action = PermAction.PACKAGE_CHECK
        ),
        PermRow(
            key = "lsposed",
            title = "LSPosed 模块",
            detail = "请在 LSPosed 中启用本应用，并将作用域设为「系统界面」。root 验证完成后，此处将直接向系统界面查询模块是否在位，无需手动确认。",
            status = PermStatus.UNKNOWN,
            actionLabel = "前往 LSPosed 管理器",
            action = PermAction.LSPOSED,
            secondActionLabel = "本人确认其已生效",
            secondAction = PermAction.CONFIRM_LSPOSED
        ),
        PermRow(
            key = "vendor",
            title = "自启动 / 后台弹出界面",
            detail = "你已在应用详情中开启「自启动」与「后台弹出界面」。锁屏时能否成功启动，仍以运行日志为准。",
            status = PermStatus.VERIFIED,
            actionLabel = "前往应用详情查看",
            action = PermAction.APP_DETAILS
        )
    )

    private val listTasks = listOf(
        TaskItem(
            id = 1L,
            name = "早间自动签到",
            packages = mutableListOf("com.example.notes", "com.example.reader"),
            scheduleType = TaskItem.TYPE_TIMES,
            times = mutableListOf("07:30", "21:30"),
            onlyCharging = true,
            onlyWifi = true,
            lastResult = "07:30 成功启动 2 个应用"
        ),
        TaskItem(
            id = 2L,
            name = "夜间同步",
            packages = mutableListOf("com.example.cloud"),
            scheduleType = TaskItem.TYPE_INTERVAL,
            intervalMinutes = 60,
            maxRuns = 5,
            remainingRuns = 3,
            onlyScreenOff = true,
            lastResult = "12:03 手动解锁后启动成功"
        ),
        TaskItem(
            id = 3L,
            name = "每周一三五",
            packages = mutableListOf("com.example.clock"),
            scheduleType = TaskItem.TYPE_WEEKLY,
            weekdays = mutableSetOf(1, 3, 5),
            times = mutableListOf("08:00"),
            enabled = false
        )
    )

    @Test
    fun planList() = paparazzi.snapshot(name = "01-plan-list") {
        Scene {
            Column(Modifier.fillMaxSize()) {
                TaskListScreen(
                    tasks = listTasks,
                    masterEnabled = true,
                    onMasterEnabledChange = {},
                    nextRunText = "11-08 21:30 · 早间自动签到",
                    onAdd = {},
                    onEdit = {},
                    onDelete = {},
                    onToggle = { _, _ -> },
                    onRunNow = {},
                    runningId = null,
                    password = "",
                    onPasswordChange = {},
                    manualFallback = true,
                    onManualFallbackChange = {},
                    manualWait = "180",
                    onManualWaitChange = {},
                    onSaveSettings = {},
                    onOpenLog = {},
                    modifier = Modifier.weight(1f)
                )
                BottomTabBar(selected = MainTab.CONFIG, onSelect = {})
            }
        }
    }

    @Test
    fun planEditTimes() = paparazzi.snapshot(name = "02-plan-edit-times") {
        Scene {
            TaskEditScreen(
                task = TaskItem(
                    id = 1L,
                    name = "早间自动签到",
                    packages = mutableListOf("com.example.notes", "com.example.reader"),
                    scheduleType = TaskItem.TYPE_TIMES,
                    times = mutableListOf("07:30", "21:30"),
                    onlyCharging = true,
                    onlyWifi = true
                ),
                onChange = {},
                onPickApps = {},
                onSave = {},
                onBack = {}
            )
        }
    }

    @Test
    fun planEditInterval() = paparazzi.snapshot(name = "03-plan-edit-interval") {
        Scene {
            TaskEditScreen(
                task = TaskItem(
                    id = 2L,
                    name = "夜间同步",
                    packages = mutableListOf("com.example.cloud"),
                    scheduleType = TaskItem.TYPE_INTERVAL,
                    intervalMinutes = 60,
                    maxRuns = 5,
                    remainingRuns = 3,
                    onlyScreenOff = true
                ),
                onChange = {},
                onPickApps = {},
                onSave = {},
                onBack = {}
            )
        }
    }

    @Test
    fun appPicker() {
        val demoApps = listOf(
            AppEntry("笔记", "com.example.notes"),
            AppEntry("阅读", "com.example.reader"),
            AppEntry("云盘", "com.example.cloud"),
            AppEntry("时钟", "com.example.clock"),
            AppEntry("邮件", "com.example.mail"),
            AppEntry("相机", "com.example.camera")
        )
        paparazzi.snapshot(name = "04-app-picker") {
            Scene {
                AppPickerScreen(
                    selected = listOf("com.example.notes", "com.example.reader"),
                    onConfirm = {},
                    onBack = {},
                    appLoader = { demoApps }
                )
            }
        }
    }

    @Test
    fun logCurrent() = paparazzi.snapshot(name = "05-log-current") {
        Scene {
            Column(Modifier.fillMaxSize()) {
                LogScreen(
                    dates = listOf("2026-11-08", "2026-11-07"),
                    selectedDate = "2026-11-08",
                    level = null,
                    text = demoLog,
                    limitKb = 512,
                    onSelectDate = {},
                    onSelectLevel = {},
                    onRefresh = {},
                    onClearDate = {},
                    onClearAll = {},
                    onSaveLimit = {},
                    modifier = Modifier.weight(1f)
                )
                BottomTabBar(selected = MainTab.LOG, onSelect = {})
            }
        }
    }

    @Test
    fun logError() = paparazzi.snapshot(name = "06-log-error") {
        Scene {
            LogScreen(
                dates = listOf("2026-11-08", "2026-11-07"),
                selectedDate = "2026-11-08",
                level = com.appscheduler.lsposed.LogStore.Level.ERROR,
                text = demoErrors,
                limitKb = 512,
                onSelectDate = {},
                onSelectLevel = {},
                onRefresh = {},
                onClearDate = {},
                onClearAll = {},
                onSaveLimit = {}
            )
        }
    }

    @Test
    fun permission() = paparazzi.snapshot(name = "07-permission") {
        Scene {
            Column(Modifier.fillMaxSize()) {
                PermissionScreen(
                    rows = permRows,
                    about = AboutInfo(),
                    checking = false,
                    agreed = true,
                    onAgreedChange = {},
                    onOpenTerms = {},
                    onRecheck = {},
                    onAction = {},
                    modifier = Modifier.weight(1f)
                )
                BottomTabBar(selected = MainTab.OVERVIEW, onSelect = {})
            }
        }
    }

    @Test
    fun overviewUnsigned() = paparazzi.snapshot(name = "15-overview-unsigned") {
        Scene {
            PermissionScreen(
                rows = emptyList(),
                about = AboutInfo(),
                checking = false,
                agreed = false,
                onAgreedChange = {},
                onOpenTerms = {},
                onRecheck = {},
                onAction = {}
            )
        }
    }

    @Test
    fun helpHome() = paparazzi.snapshot(name = "08-help") {
        Scene {
            Column(Modifier.fillMaxSize()) {
                HelpScreen(
                    page = HelpPage.HOME,
                    about = AboutInfo(),
                    onOpen = {},
                    onBack = {},
                    modifier = Modifier.weight(1f)
                )
                BottomTabBar(selected = MainTab.MORE, onSelect = {})
            }
        }
    }

    @Test
    fun helpAbout() = paparazzi.snapshot(name = "09-about") {
        Scene {
            HelpScreen(
                page = HelpPage.ABOUT,
                about = AboutInfo(),
                onOpen = {},
                onBack = {}
            )
        }
    }

    @Test
    fun helpConfigDoc() = paparazzi.snapshot(name = "10-config-doc") {
        Scene {
            HelpScreen(
                page = HelpPage.CONFIG_DOC,
                about = AboutInfo(),
                onOpen = {},
                onBack = {}
            )
        }
    }

    @Test
    fun helpLaunchDoc() = paparazzi.snapshot(name = "11-launch-doc") {
        Scene {
            HelpScreen(
                page = HelpPage.LAUNCH_DOC,
                about = AboutInfo(),
                onOpen = {},
                onBack = {}
            )
        }
    }

    @Test
    fun permissionManual() = paparazzi.snapshot(name = "14-permission-manual") {
        Scene {
            PermissionScreen(
                rows = permRows.filter { it.key == "lsposed" || it.key == "vendor" },
                about = AboutInfo(),
                checking = false,
                agreed = true,
                onAgreedChange = {},
                onOpenTerms = {},
                onRecheck = {},
                onAction = {}
            )
        }
    }

    @Test
    fun launchModeSection() = paparazzi.snapshot(name = "12-launch-mode") {        Scene {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                LaunchModeSection(
                    task = TaskItem(
                        packages = mutableListOf("com.example.notes"),
                        launchMode = TaskItem.LAUNCH_MONKEY
                    ),
                    onChange = {}
                )
            }
        }
    }

    /** 固定用 Sesame-AG 那张浅色配色，避免布局库取系统动态色导致截图偏色 */
    @Composable
    private fun Scene(content: @Composable () -> Unit) {
        AppSchedulerTheme(darkTheme = false, dynamicColor = false) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                content()
            }
        }
    }
}
