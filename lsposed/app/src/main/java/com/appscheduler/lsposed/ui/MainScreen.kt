package com.appscheduler.lsposed.ui

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.appscheduler.lsposed.Config
import com.appscheduler.lsposed.LogStore
import com.appscheduler.lsposed.RunCoordinator
import com.appscheduler.lsposed.model.TaskItem
import com.appscheduler.lsposed.schedule.Scheduler
import com.appscheduler.lsposed.store.TaskStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class Screen { LIST, EDIT, PICKER, LOG }

/**
 * 应用入口：计划列表 → 计划编辑 → 应用选择 → 日志，四屏互跳。
 */
@Composable
fun MainScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val prefs = remember { Config.get(context) }

    var tasks by remember { mutableStateOf(TaskStore.all(context)) }
    var masterEnabled by remember { mutableStateOf(prefs.enabled()) }
    var screen by remember { mutableStateOf(Screen.LIST) }
    var draft by remember { mutableStateOf<TaskItem?>(null) }
    var runningId by remember { mutableStateOf<Long?>(null) }
    var password by remember { mutableStateOf(prefs.password()) }
    var manualFallback by remember { mutableStateOf(prefs.manualFallback()) }
    var manualWait by remember { mutableStateOf(prefs.manualWait().toString()) }
    var log by remember { mutableStateOf(LogStore.read(context)) }

    fun reload() {
        tasks = TaskStore.all(context)
        log = LogStore.read(context)
    }

    fun toast(text: String) {
        scope.launch { snackbar.showSnackbar(text) }
    }

    fun saveSettings() {
        prefs.saveGlobal(
            masterEnabled,
            password,
            manualFallback,
            manualWait.toIntOrNull() ?: Config.DEFAULT_MANUAL_WAIT
        )
        Scheduler.rescheduleAll(context)
        reload()
        toast(if (masterEnabled) "已保存，定时已启用" else "已保存，定时未启用")
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
        screen = Screen.LIST
        reload()
        toast("已保存计划：${task.title()}")
    }

    fun toggleTask(task: TaskItem, enabled: Boolean) {
        val updated = task.copy(enabled = enabled)
        TaskStore.upsert(context, updated)
        Scheduler.schedule(context, updated)
        reload()
    }

    fun deleteTask(task: TaskItem) {
        Scheduler.cancel(context, task.id)
        TaskStore.delete(context, task.id)
        reload()
        toast("已删除计划：${task.title()}")
    }

    fun runNow(task: TaskItem) {
        if (runningId != null) return
        runningId = task.id
        scope.launch {
            withContext(Dispatchers.IO) { RunCoordinator.runNow(context, task.packages) }
            runningId = null
            reload()
            toast("执行完成，详见运行日志")
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

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (screen) {
                Screen.LIST -> TaskListScreen(
                    tasks = tasks,
                    masterEnabled = masterEnabled,
                    onMasterEnabledChange = { masterEnabled = it },
                    nextRunText = nextRunText,
                    onAdd = {
                        draft = TaskItem()
                        screen = Screen.EDIT
                    },
                    onEdit = { task ->
                        draft = task.copy(
                            packages = task.packages.toMutableList(),
                            times = task.times.toMutableList(),
                            weekdays = task.weekdays.toMutableSet()
                        )
                        screen = Screen.EDIT
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
                    onSaveSettings = { saveSettings() },
                    onOpenLog = { screen = Screen.LOG },
                    exactAlarmGranted = exactAlarmGranted,
                    onRequestExactAlarm = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            try {
                                context.startActivity(
                                    Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                                )
                            } catch (t: Throwable) {
                                toast("无法打开系统设置，请手动授予「闹钟与提醒」权限")
                            }
                        }
                    }
                )

                Screen.EDIT -> TaskEditScreen(
                    task = draft ?: TaskItem(),
                    onChange = { draft = it },
                    onPickApps = { screen = Screen.PICKER },
                    onSave = { saveDraft() },
                    onBack = {
                        draft = null
                        screen = Screen.LIST
                    }
                )

                Screen.PICKER -> AppPickerScreen(
                    selected = draft?.packages ?: emptyList(),
                    onConfirm = { picked ->
                        draft = draft?.copy(packages = picked.toMutableList())
                        screen = Screen.EDIT
                    },
                    onBack = { screen = Screen.EDIT }
                )

                Screen.LOG -> LogScreen(
                    log = log,
                    onBack = { screen = Screen.LIST },
                    onRefresh = { reload() },
                    onClear = {
                        LogStore.clear(context)
                        log = ""
                    }
                )
            }
        }
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
