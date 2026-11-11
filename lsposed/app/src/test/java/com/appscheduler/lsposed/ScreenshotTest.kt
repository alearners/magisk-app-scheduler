package com.appscheduler.lsposed

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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
import com.appscheduler.lsposed.ui.LogScreen
import com.appscheduler.lsposed.ui.LogTab
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
        [11-08 07:30] 计划「早间自动签到」开始，共 2 个应用
        [11-08 07:30] 锁屏状态：已锁
        [11-08 07:30] LSPosed 钩子已收起锁屏
        [11-08 07:30] 启动 com.example.notes 成功
        [11-08 07:31] 启动 com.example.reader 成功
        [11-08 07:31] 计划完成，耗时 1.2s
        [11-08 09:00] 条件不满足（未连接 WiFi），本次跳过
        [11-08 09:00] 已排下一次：11-08 10:00
        [11-08 12:00] 计划「夜间同步」开始，共 1 个应用
        [11-08 12:00] 自动解锁失败，已发送通知：请手动解锁
        [11-08 12:03] 检测到手动解锁，3 秒后启动
        [11-08 12:03] 启动 com.example.cloud 成功
    """.trimIndent()

    private val rotatedLog = """
        [11-07 21:30] 计划「早间自动签到」开始，共 2 个应用
        [11-07 21:31] 启动 com.example.notes 成功
        [11-07 22:00] 条件不满足（未插电），本次跳过
        [11-07 23:00] 已排下一次：11-08 07:30
    """.trimIndent()

    private val permRows = listOf(
        PermRow(
            key = "notification",
            title = "通知权限",
            detail = "「请手动解锁」「执行失败」这类提醒都靠它。Android 13 起需要在系统弹窗里允许。",
            status = PermStatus.OK,
            extra = "已允许"
        ),
        PermRow(
            key = "exact_alarm",
            title = "精确闹钟",
            detail = "没这个权限，系统只能用非精确闹钟，触发可能推迟几分钟才执行。",
            status = PermStatus.MISSING,
            actionLabel = "去授予",
            action = PermAction.EXACT_ALARM
        ),
        PermRow(
            key = "battery",
            title = "省电白名单",
            detail = "被系统电池优化限制时，闹钟可能被推迟甚至被丢进「后台受限」里。",
            status = PermStatus.OK,
            extra = "已不限制"
        ),
        PermRow(
            key = "root",
            title = "root 权限",
            detail = "解锁（上滑/密码注入）、强制停止与启动应用、加入省电白名单都要用 root。",
            status = PermStatus.OK,
            extra = "已验证 uid=0(root) gid=0(root)"
        ),
        PermRow(
            key = "packages",
            title = "读取应用列表",
            detail = "还没读过应用列表。点「现在检查」读一次；部分系统（HyperOS、MIUI）这时会弹一次「取得已安装的应用程式资讯」，允许后「选择应用」才能看到全部应用。",
            status = PermStatus.UNKNOWN,
            actionLabel = "现在检查",
            action = PermAction.PACKAGE_CHECK
        ),
        PermRow(
            key = "lsposed",
            title = "LSPosed 模块",
            detail = "本模块要在 LSPosed 里勾选并作用到「系统界面」，锁屏才能真正被收起。App 自身无法判断是否已生效。",
            status = PermStatus.UNKNOWN,
            actionLabel = "打开 LSPosed 管理器",
            action = PermAction.LSPOSED
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
                    current = demoLog,
                    all = rotatedLog + "\n" + demoLog,
                    tab = LogTab.CURRENT,
                    onSelectTab = {},
                    onRefresh = {},
                    onClearCurrent = {},
                    onClearAll = {},
                    modifier = Modifier.weight(1f)
                )
                BottomTabBar(selected = MainTab.LOG, onSelect = {})
            }
        }
    }

    @Test
    fun logAll() = paparazzi.snapshot(name = "06-log-all") {
        Scene {
            LogScreen(
                current = demoLog,
                all = rotatedLog + "\n...(以上为已轮转的旧日志，以下是当前日志)\n" + demoLog,
                tab = LogTab.ALL,
                onSelectTab = {},
                onRefresh = {},
                onClearCurrent = {},
                onClearAll = {}
            )
        }
    }

    @Test
    fun permission() = paparazzi.snapshot(name = "07-permission") {
        Scene {
            Column(Modifier.fillMaxSize()) {
                PermissionScreen(
                    rows = permRows,
                    checking = false,
                    onRecheck = {},
                    onAction = {},
                    modifier = Modifier.weight(1f)
                )
                BottomTabBar(selected = MainTab.PERM, onSelect = {})
            }
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
                BottomTabBar(selected = MainTab.HELP, onSelect = {})
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
