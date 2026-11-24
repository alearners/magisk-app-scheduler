package com.appscheduler.lsposed.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
            title = if (task.id <= 0L) "新建计划" else "修改计划",
            subtitle = "单份计划可按顺序启动多个应用",
            onBack = onBack
        )

        // ---- 应用 ----
        SectionCard("要启动的应用") {
            if (task.packages.isEmpty()) {
                Text(
                    "尚未选择任何应用",
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
                    "共选取 ${task.packages.size} 个应用，将按此顺序依次启动；点击标签即可移除",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Button(onClick = onPickApps, modifier = Modifier.fillMaxWidth()) {
                Text("选择应用")
            }
        }

        // ---- 定时方式 ----
        SectionCard("执行时间") {
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
                    Text("选择星期（均不选即每天）", style = MaterialTheme.typography.bodySmall)
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
                        label = { Text("时间（24 小时制）") },
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
                        label = { Text("执行间隔（分钟）") },
                        supportingText = { Text("60 表示每小时一次，1440 表示每天一次") },
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
                    Text("每日执行时间，可添加多个", style = MaterialTheme.typography.bodySmall)
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
                                label = { Text("第 ${index + 1} 个时间") },
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
                    ) { Text("＋ 添加时间") }
                }
            }
        }

        // ---- 循环次数 ----
        SectionCard("执行次数") {
            OutlinedTextField(
                value = maxRunsText,
                onValueChange = { text ->
                    maxRunsText = text
                    val value = text.toIntOrNull() ?: 0
                    // 只有「总次数」真的被改动时才重置剩余次数；否则每次进编辑页都会把进度抹掉
                    val remaining = when {
                        value <= 0 -> 0
                        value == task.maxRuns -> task.remainingRuns.coerceIn(0, value)
                        else -> value
                    }
                    onChange(task.copy(maxRuns = value, remainingRuns = remaining))
                },
                label = { Text("总执行次数") },
                supportingText = { Text("填 0 表示不限次数；达到设定次数后将自动停止") },
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
        SectionCard("执行条件（不满足则跳过本次）") {
            SwitchRow(
                title = "仅在充电时",
                description = "设备未处于充电状态则跳过",
                checked = task.onlyCharging,
                onCheckedChange = { onChange(task.copy(onlyCharging = it)) }
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            SwitchRow(
                title = "仅在连接 WiFi 时",
                description = "未连接 WiFi 则跳过",
                checked = task.onlyWifi,
                onCheckedChange = { onChange(task.copy(onlyWifi = it)) }
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            SwitchRow(
                title = "仅在屏幕熄灭时",
                description = "屏幕处于点亮状态则跳过，以免打扰使用",
                checked = task.onlyScreenOff,
                onCheckedChange = { onChange(task.copy(onlyScreenOff = it)) }
            )
        }

        // ---- 启动方式：每个计划自己决定 ----
        LaunchModeSection(task = task, onChange = onChange)

        Button(onClick = onSave, modifier = Modifier.fillMaxWidth()) {
            Text("保存计划")
        }
        Text(
            "保存后，开关状态与下次执行时间均在列表中显示",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/** 启动方式小节：四种拉起方法 + 强制停止开关；单独抽出来便于截图与帮助页复用文案 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LaunchModeSection(task: TaskItem, onChange: (TaskItem) -> Unit) {
    SectionCard("启动方式") {
        Text("请选择一种启动方式", style = MaterialTheme.typography.bodySmall)
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            ChoiceChip("直接打开", task.launchMode == TaskItem.LAUNCH_COMPONENT) {
                onChange(task.copy(launchMode = TaskItem.LAUNCH_COMPONENT))
            }
            ChoiceChip("等待界面", task.launchMode == TaskItem.LAUNCH_WAIT) {
                onChange(task.copy(launchMode = TaskItem.LAUNCH_WAIT))
            }
            ChoiceChip("模拟点击", task.launchMode == TaskItem.LAUNCH_MONKEY) {
                onChange(task.copy(launchMode = TaskItem.LAUNCH_MONKEY))
            }
            ChoiceChip("返回桌面", task.launchMode == TaskItem.LAUNCH_DESKTOP) {
                onChange(task.copy(launchMode = TaskItem.LAUNCH_DESKTOP))
            }
        }
        Text(
            launchModeDetail(task.launchMode),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        SwitchRow(
            title = "启动前先停止该应用",
            description = if (task.forceColdStart) {
                "先停止再启动，等同全新进入，界面回到初始状态"
            } else {
                "不予停止，直接切换；应用仍在后台时将回到上次退出的位置"
            },
            checked = task.forceColdStart,
            onCheckedChange = { onChange(task.copy(forceColdStart = it)) }
        )
    }
}

/** 每种启动方式的作用与可能的后果；编辑页和帮助页共用这一份文案，保证两处说法一致 */
internal fun launchModeDetail(mode: String): String = when (mode) {
    TaskItem.LAUNCH_WAIT ->
        "发出启动指令后，待界面实际显示再继续执行，较其他方式多耗时一至三秒。\n" +
            "需要指出的是：启动较慢的应用将延长等待；长期停留在开屏页的应用会等待至超时，本次计为失败。"

    TaskItem.LAUNCH_MONKEY ->
        "调用系统 monkey 模拟桌面点击，兼容性最好。\n" +
            "需要指出的是：该方式会注入触摸与导航事件。经实测，在澎湃、MIUI 上会开启系统「自动旋转屏幕」，亦可能误触界面中的其他控件。"

    TaskItem.LAUNCH_DESKTOP ->
        "先返回桌面，再启动应用，等同从桌面图标点开。\n" +
            "需要指出的是：该方式会打断当前操作，屏幕出现一次闪烁；仅当个别应用必须在桌面环境下才能启动时，方宜选用。"

    else ->
        "直接打开应用的第一个界面，最为稳妥，不点击屏幕，也不改动系统设置。\n" +
            "需要指出的是：个别应用无法解析该界面时将启动失败，日志会记录「未找到该应用的入口」，此时可改用「模拟点击」。"
}
