package com.appscheduler.lsposed.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.appscheduler.lsposed.permissions.PermAction
import com.appscheduler.lsposed.permissions.PermRow
import com.appscheduler.lsposed.permissions.PermStatus

/**
 * 权限检查页：只负责展示与转发动作，判定逻辑在 PermissionAudit 里，
 * 因此可以在 Paparazzi 里用固定数据渲染截图。
 */
@Composable
fun PermissionScreen(
    rows: List<PermRow>,
    checking: Boolean,
    onRecheck: () -> Unit,
    onAction: (PermAction) -> Unit,
    modifier: Modifier = Modifier
) {
    val okCount = rows.count { it.status == PermStatus.OK }
    val todoCount = rows.count { it.status != PermStatus.OK }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ScreenHeader(
            title = "权限",
            subtitle = "逐项检查本 App 需要的权限，缺哪个点哪个",
            actions = {
                TextButton(onClick = onRecheck, enabled = !checking) {
                    Text(if (checking) "检查中…" else "重新检测")
                }
            }
        )

        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (todoCount == 0) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.secondaryContainer
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    if (todoCount == 0) "全部就绪" else "$todoCount 项待处理，$okCount 项已就绪",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    if (todoCount == 0) "权限都齐了，定时触发与自动解锁可以正常走完。"
                    else "标「未授予」的会直接影响功能；标「需手动确认」的需要你去管理器或系统设置里看一眼。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (rows.isEmpty()) {
            SectionCard("正在检测") {
                Text(
                    "首次进入会读一遍当前权限状态，稍等一下；失败可以点右上角「重新检测」。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        rows.forEach { row ->
            PermCard(row = row, onAction = onAction)
        }

        Text(
            "提示：本页只读取状态，不会自动弹系统授权框；点了「申请 / 去设置」才会跳转。",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun PermCard(row: PermRow, onAction: (PermAction) -> Unit) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    row.title,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.width(8.dp))
                StatusChip(row.status)
            }
            Text(
                row.detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (!row.extra.isNullOrBlank()) {
                Text(
                    row.extra,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            val action = row.action
            if (action != null && !row.actionLabel.isNullOrBlank()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                TextButton(
                    onClick = { onAction(action) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(row.actionLabel + " ›")
                }
            }
        }
    }
}

@Composable
private fun StatusChip(status: PermStatus) {
    val bg = when (status) {
        PermStatus.OK -> MaterialTheme.colorScheme.primaryContainer
        PermStatus.MISSING -> MaterialTheme.colorScheme.errorContainer
        PermStatus.UNKNOWN -> MaterialTheme.colorScheme.surfaceVariant
    }
    val fg = when (status) {
        PermStatus.OK -> MaterialTheme.colorScheme.onPrimaryContainer
        PermStatus.MISSING -> MaterialTheme.colorScheme.onErrorContainer
        PermStatus.UNKNOWN -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(bg)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(
            status.label,
            style = MaterialTheme.typography.labelSmall,
            color = fg
        )
    }
}
