package com.appscheduler.lsposed.ui

import androidx.compose.foundation.background
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

/** 日志页的两个视图 */
enum class LogTab(val label: String) {
    CURRENT("当前日志"),
    ALL("所有日志")
}

/**
 * 运行日志页：
 *  - 当前日志：apptimer.log，本次运行期间写入的内容
 *  - 所有日志：含已轮转到 apptimer.log.1 的旧日志
 *  - 清除当前日志 / 清除所有日志 分开操作，都要二次确认
 */
@Composable
fun LogScreen(
    current: String,
    all: String,
    tab: LogTab,
    onSelectTab: (LogTab) -> Unit,
    onRefresh: () -> Unit,
    onClearCurrent: () -> Unit,
    onClearAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    var confirm by remember { mutableStateOf<LogTab?>(null) }
    val shown = if (tab == LogTab.CURRENT) current else all

    ConfirmClearDialog(
        target = confirm,
        onDismiss = { confirm = null },
        onConfirm = {
            if (confirm == LogTab.CURRENT) onClearCurrent() else onClearAll()
            confirm = null
        }
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        ScreenHeader(
            title = "日志",
            subtitle = "当前日志只含当前文件；所有日志含已轮转的历史",
            actions = {
                TextButton(onClick = onRefresh) { Text("刷新") }
            }
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LogTab.entries.forEach { item ->
                ChoiceChip(
                    label = item.label,
                    selected = tab == item,
                    onClick = { onSelectTab(item) }
                )
            }
        }

        Text(
            "当前 ${lineCount(current)} 行 · 所有 ${lineCount(all)} 行",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f))
            ) {
                SelectionContainer {
                    Text(
                        text = shown.ifBlank {
                            if (tab == LogTab.CURRENT) "（当前还没有日志，先手动执行一次计划试试）"
                            else "（还没有任何日志）"
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(12.dp),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        lineHeight = 17.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TextButton(
                onClick = { confirm = LogTab.CURRENT },
                modifier = Modifier.weight(1f)
            ) { Text("清除当前日志") }
            TextButton(
                onClick = { confirm = LogTab.ALL },
                modifier = Modifier.weight(1f)
            ) {
                Text("清除所有日志", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun ConfirmClearDialog(
    target: LogTab?,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    if (target == null) {
        return
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (target == LogTab.CURRENT) "清除当前日志？" else "清除所有日志？") },
        text = {
            Text(
                if (target == LogTab.CURRENT) {
                    "只会删掉 apptimer.log（当前日志）；已轮转的旧日志（apptimer.log.1）会保留。删除后无法恢复。"
                } else {
                    "会同时删掉 apptimer.log 和 apptimer.log.1（当前日志 + 已轮转的旧日志）。删除后无法恢复。"
                }
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("清除") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

private fun lineCount(text: String): Int =
    if (text.isBlank()) 0 else text.count { it == '\n' }
