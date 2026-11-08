package com.appscheduler.lsposed.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.appscheduler.lsposed.model.TaskItem

/**
 * 计划编辑：应用选择、三种定时方式、循环次数、启动条件。
 */
@Composable
fun TaskEditScreen(
    task: TaskItem,
    onChange: (TaskItem) -> Unit,
    onPickApps: () -> Unit,
    onSave: () -> Unit,
    onBack: () -> Unit
) {
    var intervalText by remember(task.id, task.intervalMinutes) {
        mutableStateOf(task.intervalMinutes.toString())
    }
    var maxRunsText by remember(task.id, task.maxRuns) {
        mutableStateOf(task.maxRuns.toString())
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ScreenHeader(
            title = if (task.id <= 0L) "新建计划" else "编辑计划",
            subtitle = "一个计划可同时启动多个应用",
            onBack = onBack
        )

        // ---- 应用 ----
        SectionCard("启动的应用") {
            if (task.packages.isEmpty()) {
                Text(
                    "还没有选择应用",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    task.packages.forEach { pkg ->
                        ChoiceChip(
                            label = "$pkg ✕",
                            selected = true,
                            onClick = {
                                val next = task.packages.toMutableList()
                                next.remove(pkg)
                                onChange(task.copy(packages = next))
                            }
                        )
                    }
                }
                Text(
                    "共 ${task.packages.size} 个应用，按顺序启动；点标签可移除",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Button(onClick = onPickApps, modifier = Modifier.fillMaxWidth()) {
                Text("选择应用")
            }
        }

        // ---- 定时方式 ----
        SectionCard("触发方式") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ChoiceChip("按时间点", task.scheduleType == TaskItem.TYPE_TIMES) {
                    onChange(task.copy(scheduleType = TaskItem.TYPE_TIMES))
                }
                ChoiceChip("按星期", task.scheduleType == TaskItem.TYPE_WEEKLY) {
                    onChange(task.copy(scheduleType = TaskItem.TYPE_WEEKLY))
                }
                ChoiceChip("按间隔循环", task.scheduleType == TaskItem.TYPE_INTERVAL) {
                    onChange(task.copy(scheduleType = TaskItem.TYPE_INTERVAL))
                }
            }

            when (task.scheduleType) {
                TaskItem.TYPE_WEEKLY -> {
                    Text("选择星期（不选＝每天）", style = MaterialTheme.typography.bodySmall)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        for (day in 1..7) {
                            ChoiceChip(TaskItem.weekdayName(day), task.weekdays.contains(day)) {
                                val next = task.weekdays.toMutableSet()
                                if (!next.add(day)) next.remove(day)
                                onChange(
                                    task.copy(
                                        weekdays = next,
                                        times = mutableListOf(task.times.firstOrNull() ?: "08:00")
                                    )
                                )
                            }
                        }
                    }
                    OutlinedTextField(
                        value = task.times.firstOrNull() ?: "08:00",
                        onValueChange = { onChange(task.copy(times = mutableListOf(it))) },
                        label = { Text("执行时间（24 小时制）") },
                        supportingText = { Text("例如 07:30") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                TaskItem.TYPE_INTERVAL -> {
                    OutlinedTextField(
                        value = intervalText,
                        onValueChange = { text ->
                            intervalText = text
                            val value = text.toIntOrNull()
                            if (value != null && value > 0) {
                                onChange(task.copy(intervalMinutes = value))
                            }
                        },
                        label = { Text("间隔分钟数") },
                        supportingText = { Text("例如 60 表示每小时一次，1440 表示每天一次") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val presets = listOf(30, 60, 120, 360, 720, 1440)
                        presets.forEach { minutes ->
                            val label = if (minutes % 60 == 0) "${minutes / 60} 小时" else "$minutes 分钟"
                            ChoiceChip(label, task.intervalMinutes == minutes) {
                                intervalText = minutes.toString()
                                onChange(task.copy(intervalMinutes = minutes))
                            }
                        }
                    }
                    Text(
                        "当前：" + task.describeSchedule(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                else -> {
                    Text("每天触发的时间点，可添加多个", style = MaterialTheme.typography.bodySmall)
                    task.times.forEachIndexed { index, value ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = value,
                                onValueChange = { text ->
                                    val next = task.times.toMutableList()
                                    next[index] = text
                                    onChange(task.copy(times = next))
                                },
                                label = { Text("时间点 ${index + 1}") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(
                                onClick = {
                                    val next = task.times.toMutableList()
                                    if (next.size > 1) {
                                        next.removeAt(index)
                                        onChange(task.copy(times = next))
                                    }
                                }
                            ) { Text("删除") }
                        }
                    }
                    TextButton(
                        onClick = {
                            val next = task.times.toMutableList()
                            next.add("12:00")
                            onChange(task.copy(times = next))
                        }
                    ) { Text("＋ 添加时间点") }
                }
            }
        }

        // ---- 循环次数 ----
        SectionCard("循环次数") {
            OutlinedTextField(
                value = maxRunsText,
                onValueChange = { text ->
                    maxRunsText = text
                    val value = text.toIntOrNull() ?: 0
                    onChange(task.copy(maxRuns = value, remainingRuns = value))
                },
                label = { Text("总共执行次数") },
                supportingText = { Text("填 0 表示不限次数；达到次数后计划自动停用") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            if (task.maxRuns > 0) {
                Text(
                    "剩余 ${task.remainingRuns} 次",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // ---- 启动条件 ----
        SectionCard("启动条件（不满足则跳过本次）") {
            SwitchRow(
                title = "仅在充电时",
                description = "手机没在充电就跳过",
                checked = task.onlyCharging,
                onCheckedChange = { onChange(task.copy(onlyCharging = it)) }
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            SwitchRow(
                title = "仅在连接 WiFi 时",
                description = "没连 WiFi 就跳过",
                checked = task.onlyWifi,
                onCheckedChange = { onChange(task.copy(onlyWifi = it)) }
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            SwitchRow(
                title = "仅在屏幕熄灭时",
                description = "亮屏时不执行，避免打扰",
                checked = task.onlyScreenOff,
                onCheckedChange = { onChange(task.copy(onlyScreenOff = it)) }
            )
        }

        Button(onClick = onSave, modifier = Modifier.fillMaxWidth()) {
            Text("保存计划")
        }
        Text(
            "计划启用状态、下次执行时间在列表中查看",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
