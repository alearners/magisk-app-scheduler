package com.appscheduler.lsposed.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.appscheduler.lsposed.model.TaskItem

/**
 * 计划列表：总开关 + 全部计划卡片 + 全局设置。
 */
@Composable
fun TaskListScreen(
    tasks: List<TaskItem>,
    masterEnabled: Boolean,
    onMasterEnabledChange: (Boolean) -> Unit,
    nextRunText: String,
    onAdd: () -> Unit,
    onEdit: (TaskItem) -> Unit,
    onDelete: (TaskItem) -> Unit,
    onToggle: (TaskItem, Boolean) -> Unit,
    onRunNow: (TaskItem) -> Unit,
    runningId: Long?,
    password: String,
    onPasswordChange: (String) -> Unit,
    manualFallback: Boolean,
    onManualFallbackChange: (Boolean) -> Unit,
    manualWait: String,
    onManualWaitChange: (String) -> Unit,
    onSaveSettings: () -> Unit,
    onOpenLog: () -> Unit,
    exactAlarmGranted: Boolean = true,
    onRequestExactAlarm: () -> Unit = {},
    onRequestIgnoreBattery: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ScreenHeader("定时启动 App", "LSPosed 版 · 多应用 · 多计划 · 条件触发")

        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (masterEnabled) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.secondaryContainer
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            if (masterEnabled) "调度总开关：已启用" else "调度总开关：已关闭",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            if (masterEnabled) "下次执行：$nextRunText" else "关闭后所有计划都不再触发，仍可手动执行",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    androidx.compose.material3.Switch(
                        checked = masterEnabled,
                        onCheckedChange = { onMasterEnabledChange(it) }
                    )
                }
            }
        }

        Button(onClick = onAdd, modifier = Modifier.fillMaxWidth()) {
            Text("＋ 新建启动计划")
        }

        if (tasks.isEmpty()) {
            SectionCard("还没有计划") {
                Text(
                    "点上面的按钮新建一个计划：选择要启动的应用，设置触发时间与条件。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        tasks.forEach { task ->
            PlanCard(
                task = task,
                running = runningId == task.id,
                onEdit = { onEdit(task) },
                onDelete = { onDelete(task) },
                onToggle = { onToggle(task, it) },
                onRunNow = { onRunNow(task) }
            )
        }

        SectionCard("全局设置") {
            OutlinedTextField(
                value = password,
                onValueChange = onPasswordChange,
                label = { Text("锁屏密码（仅兜底时使用）") },
                supportingText = { Text("优先由 LSPosed 钩子收起锁屏，失败才注入密码") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            SwitchRow(
                title = "等用户手动解锁后再启动",
                description = "自动解锁失败时不放弃，等你解锁后自动打开 App",
                checked = manualFallback,
                onCheckedChange = onManualFallbackChange
            )

            if (manualFallback) {
                OutlinedTextField(
                    value = manualWait,
                    onValueChange = onManualWaitChange,
                    label = { Text("最长等待秒数") },
                    supportingText = { Text("10–1800 秒，超时则放弃本次任务并发通知") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Button(onClick = onSaveSettings, modifier = Modifier.fillMaxWidth()) {
                Text("保存全局设置")
            }

            if (!exactAlarmGranted) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Text(
                    "未授予「闹钟与提醒」权限，定时只能使用非精确调度，触发可能延迟几分钟。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
                TextButton(onClick = onRequestExactAlarm, modifier = Modifier.fillMaxWidth()) {
                    Text("去授予精确闹钟权限 ›")
                }
            }

            if (!batteryOptimizationIgnored()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Text(
                    "系统正在对本 App 做省电限制，闹钟到点可能被推迟几分钟才唤醒，甚至被挡在「后台受限闹钟」里。建议把本 App 设为不优化。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
                TextButton(onClick = onRequestIgnoreBattery, modifier = Modifier.fillMaxWidth()) {
                    Text("去关闭电池优化 ›")
                }
            }
        }

        TextButton(onClick = onOpenLog, modifier = Modifier.fillMaxWidth()) {
            Text("查看运行日志 ›")
        }
    }
}

@Composable
private fun batteryOptimizationIgnored(): Boolean {
    val context = androidx.compose.ui.platform.LocalContext.current
    return try {
        val pm = context.getSystemService(android.content.Context.POWER_SERVICE) as? android.os.PowerManager
        pm?.isIgnoringBatteryOptimizations(context.packageName) ?: true
    } catch (t: Throwable) {
        // Paparazzi / layoutlib 里没有真实 PowerManager，按「已放行」处理，避免截图与实际不符
        true
    }
}

@Composable
private fun PlanCard(
    task: TaskItem,
    running: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onRunNow: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        task.title(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        task.packages.joinToString("、"),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                androidx.compose.material3.Switch(checked = task.enabled, onCheckedChange = onToggle)
            }

            Text(
                "计划：" + task.describeSchedule(),
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                "条件：" + task.describeConditions(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (task.lastResult.isNotBlank()) {
                Text(
                    "上次结果：" + task.lastResult,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onEdit) { Text("编辑") }
                TextButton(onClick = onDelete) { Text("删除") }
                TextButton(onClick = onRunNow, enabled = !running) {
                    if (running) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("执行中")
                    } else {
                        Text("立即执行")
                    }
                }
            }
        }
    }
}
