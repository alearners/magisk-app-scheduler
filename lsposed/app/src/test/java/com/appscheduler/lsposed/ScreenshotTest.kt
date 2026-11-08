package com.appscheduler.lsposed

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.appscheduler.lsposed.apps.AppEntry
import com.appscheduler.lsposed.model.TaskItem
import com.appscheduler.lsposed.ui.AppPickerScreen
import com.appscheduler.lsposed.ui.AppSchedulerTheme
import com.appscheduler.lsposed.ui.LogScreen
import com.appscheduler.lsposed.ui.TaskEditScreen
import com.appscheduler.lsposed.ui.TaskListScreen
import org.junit.Rule
import org.junit.Test

/**
 * 用 Paparazzi（layoutlib）在 JVM 上渲染真实 Compose 界面，输出 README 用截图。
 * 运行：./gradlew :app:recordPaparazziDebug
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
                onOpenLog = {}
            )
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
    fun log() = paparazzi.snapshot(name = "05-run-log") {
        Scene {
            LogScreen(log = demoLog, onBack = {}, onRefresh = {}, onClear = {})
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
