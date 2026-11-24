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
import com.appscheduler.lsposed.Config
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
    autoLock: Boolean = false,
    onAutoLockChange: (Boolean) -> Unit = {},
    autoLockDelay: String = "5",
    onAutoLockDelayChange: (String) -> Unit = {},
    timerMode: String = Config.TIMER_ALARM,
    onTimerModeChange: (String) -> Unit = {},
    onSaveSettings: () -> Unit,
    onOpenLog: () -> Unit,
    exactAlarmGranted: Boolean = true,
    onRequestExactAlarm: () -> Unit = {},
    batteryIgnored: Boolean = true,
    onRequestIgnoreBattery: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ScreenHeader("AppTimer", "LSPosed 版 · 支持多应用、多计划，并按条件决定是否执行")

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
                            if (masterEnabled) "定时开关：已启用" else "定时开关：已停用",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            if (masterEnabled) "下一次：$nextRunText" else "总开关关闭后，计划不再自动执行；如需验证，仍可手动执行",
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
            SectionCard("暂无计划") {
                Text(
                    "请点击上方按钮新建第一份计划：选定需要在指定时间自动打开的应用，并设定执行时间与执行条件。",
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
                label = { Text("锁屏密码") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            SwitchRow(
                title = "自动解锁失败时，等待手动解锁",
                description = "若自动输入密码未成功，本应用不会放弃，将在你完成解锁后立即打开应用",
                checked = manualFallback,
                onCheckedChange = onManualFallbackChange
            )

            if (manualFallback) {
                OutlinedTextField(
                    value = manualWait,
                    onValueChange = onManualWaitChange,
                    label = { Text("最长等待时间（秒）") },
                    supportingText = { Text("10–1800 秒。超时则放弃本次执行，并发出通知") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            SwitchRow(
                title = "应用无人操作时自动锁屏",
                description = "应用处于前台且长时间无人操作，将熄屏并锁定；若中途切换至其他应用，则不予锁定",
                checked = autoLock,
                onCheckedChange = onAutoLockChange
            )

            if (autoLock) {
                OutlinedTextField(
                    value = autoLockDelay,
                    onValueChange = onAutoLockDelayChange,
                    label = { Text("无操作后锁定（秒）") },
                    supportingText = { Text("2–600 秒，默认为 5 秒") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            SwitchRow(
                title = "使用应用内置计时器",
                description = if (timerMode == Config.TIMER_INTERNAL) {
                    "当前由应用自身计时：精度较高，但通知栏会常驻一条通知，应用被清理后将不再计时"
                } else {
                    "当前交由系统闹钟负责：应用被清理后仍可按时触发，但省电策略可能导致延后数分钟"
                },
                checked = timerMode == Config.TIMER_INTERNAL,
                onCheckedChange = {
                    onTimerModeChange(if (it) Config.TIMER_INTERNAL else Config.TIMER_ALARM)
                }
            )

            Button(onClick = onSaveSettings, modifier = Modifier.fillMaxWidth()) {
                Text("保存设置")
            }

            if (!exactAlarmGranted) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Text(
                    "尚未允许「闹钟与提醒」，系统仅可提供大致时间，可能延后数分钟触发。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
                TextButton(onClick = onRequestExactAlarm, modifier = Modifier.fillMaxWidth()) {
                    Text("前往允许准时唤醒 ›")
                }
            }

            if (!batteryIgnored) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Text(
                    "系统正在限制本应用的后台耗电：闹钟可能延后数分钟，严重时将不予触发。将其设为「不受限制」可显著提高可靠性。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
                TextButton(onClick = onRequestIgnoreBattery, modifier = Modifier.fillMaxWidth()) {
                    Text("使用 root 解除省电限制 ›")
                }
            }
        }

        TextButton(onClick = onOpenLog, modifier = Modifier.fillMaxWidth()) {
            Text("查看运行日志 ›")
        }
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
                "执行时间：" + task.describeSchedule(),
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                "不执行的条件：" + task.describeConditions(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "启动方式：" + task.describeLaunch() + if (task.forceColdStart) " · 先停止再重新启动" else " · 直接切换至前台",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (task.lastResult.isNotBlank()) {
                Text(
                    "上次执行结果：" + task.lastResult,
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
