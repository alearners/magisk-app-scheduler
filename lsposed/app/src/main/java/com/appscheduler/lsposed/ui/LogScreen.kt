package com.appscheduler.lsposed.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.appscheduler.lsposed.Config
import com.appscheduler.lsposed.LogStore

/** 清除范围的两档 */
enum class LogClear(val label: String) {
    DATE("清除当日"),
    ALL("清除全部")
}

/**
 * 日志页：按日期分包的日志，可切日期、按级别筛选。
 *
 *  - 日期：只列出真的有日志文件的那几天，新的在后
 *  - 级别：全部 / 运行 / 错误 / 调试，另设「设定」调整单天文件上限
 *  - 单天文件大小上限：1 KB – 1 MB
 */
@Composable
fun LogScreen(
    dates: List<String>,
    selectedDate: String,
    level: LogStore.Level?,
    text: String,
    limitKb: Int,
    onSelectDate: (String) -> Unit,
    onSelectLevel: (LogStore.Level?) -> Unit,
    onRefresh: () -> Unit,
    onClearDate: () -> Unit,
    onClearAll: () -> Unit,
    onSaveLimit: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var confirm by remember { mutableStateOf<LogClear?>(null) }
    var settingOpen by remember { mutableStateOf(false) }

    ConfirmClearDialog(
        target = confirm,
        selectedDate = selectedDate,
        onDismiss = { confirm = null },
        onConfirm = {
            if (confirm == LogClear.DATE) onClearDate() else onClearAll()
            confirm = null
        }
    )

    if (settingOpen) {
        LogLimitDialog(
            current = limitKb,
            onDismiss = { settingOpen = false },
            onConfirm = {
                onSaveLimit(it)
                settingOpen = false
            }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        PageTitle(
            title = "日志",
            subtitle = "按日期分文件记录，单日上限 $limitKb KB",
            actions = { TextButton(onClick = onRefresh) { Text("刷新") } }
        )

        // 日期切换：只有存在日志文件的日子才出现
        if (dates.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                dates.forEach { date ->
                    ChoiceChip(
                        label = shortDate(date),
                        selected = date == selectedDate,
                        onClick = { onSelectDate(date) }
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ChoiceChip(label = "全部", selected = level == null, onClick = { onSelectLevel(null) })
            listOf(
                LogStore.Level.RUN,
                LogStore.Level.ERROR,
                LogStore.Level.DEBUG
            ).forEach { item ->
                ChoiceChip(
                    label = item.label,
                    selected = level == item,
                    onClick = { onSelectLevel(item) }
                )
            }
            ChoiceChip(
                label = "设定",
                selected = false,
                onClick = { settingOpen = true }
            )
        }

        Text(
            if (dates.isEmpty()) "暂无日志记录"
            else "${selectedDate} · ${lineCount(text)} 行 · 共保留 ${dates.size} 天",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // 清除按钮放在日志框上方：横屏只有 369dp 高时，按钮也不会被挤出屏幕
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TextButton(
                onClick = { if (selectedDate.isNotEmpty()) confirm = LogClear.DATE },
                modifier = Modifier.weight(1f)
            ) { Text(LogClear.DATE.label) }
            TextButton(
                onClick = { confirm = LogClear.ALL },
                modifier = Modifier.weight(1f)
            ) {
                Text(LogClear.ALL.label, color = MaterialTheme.colorScheme.error)
            }
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
            ) {
                SelectionContainer {
                    Text(
                        text = text.ifBlank {
                            when {
                                dates.isEmpty() -> "（暂无记录。可先手动执行一次计划）"
                                level != null -> "（当日无「${level.label}」级别记录）"
                                else -> "（当日暂无记录）"
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(10.dp),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        lineHeight = 17.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
private fun ConfirmClearDialog(
    target: LogClear?,
    selectedDate: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    if (target == null) {
        return
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (target == LogClear.DATE) "确认清除 $selectedDate 的日志？" else "确认清除全部日志？") },
        text = {
            Text(
                if (target == LogClear.DATE) {
                    "仅删除该日期的日志文件，其他日期不受影响。删除后无法恢复。"
                } else {
                    "将删除所有日期的日志。删除后无法恢复。"
                }
            )
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text("确认清除") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

private fun shortDate(date: String): String =
    if (date.length >= 10) date.substring(5) else date

@Composable
private fun LogLimitDialog(
    current: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    var value by remember(current) { mutableStateOf(current.toString()) }
    var invalid by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("日志设定") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = value,
                    onValueChange = {
                        value = it.filter { ch -> ch.isDigit() }
                        invalid = false
                    },
                    label = { Text("单日上限（KB）") },
                    supportingText = {
                        Text(
                            if (invalid) "请输入 1 至 1024 之间的整数。"
                            else "可设 1 至 1024 KB，默认 512 KB。达到上限后，将优先舍弃当日最早的内容。"
                        )
                    },
                    isError = invalid,
                    singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val kb = value.toIntOrNull()
                if (kb == null || kb < Config.MIN_LOG_LIMIT_KB || kb > Config.MAX_LOG_LIMIT_KB) {
                    invalid = true
                } else {
                    onConfirm(kb)
                }
            }) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

private fun lineCount(text: String): Int =
    if (text.isBlank()) 0 else text.count { it == '\n' }
