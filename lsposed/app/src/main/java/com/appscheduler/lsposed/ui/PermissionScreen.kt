package com.appscheduler.lsposed.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.appscheduler.lsposed.permissions.PermAction
import com.appscheduler.lsposed.permissions.PermRow
import com.appscheduler.lsposed.permissions.PermStatus

/**
 * 概览页：模块状态 + 同意使用说明的勾选框 + 各项权限体检。
 * 只负责展示与转发动作，判定逻辑在 PermissionAudit 里，因此可以在 Paparazzi 里用固定数据渲染截图。
 *
 * 版式对齐 Sesame-AG：大标题 + 分区小标题 + 12dp 圆角通栏条目，不用阴影卡片。
 */
@Composable
fun PermissionScreen(
    rows: List<PermRow>,
    about: AboutInfo,
    checking: Boolean,
    agreed: Boolean,
    onAgreedChange: (Boolean) -> Unit,
    onOpenTerms: () -> Unit,
    onRecheck: () -> Unit,
    onAction: (PermAction) -> Unit,
    modifier: Modifier = Modifier
) {
    val okCount = rows.count { it.status.ready }
    val todoCount = rows.count { !it.status.ready }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        PageTitle(
            title = "概览",
            actions = {
                TextButton(onClick = onRecheck, enabled = !checking && agreed) {
                    Text(if (checking) "核验中…" else "重新核验")
                }
            }
        )

        Spacer(Modifier.padding(top = 4.dp))

        SummaryCard(todoCount = todoCount, okCount = okCount, agreed = agreed)

        SectionLabel("模块状态")
        SesameCard {
            InfoRow("名称", about.appName + "（LSPosed 版）")
            InfoRow("版本", about.versionName)
            InfoRow("包名", about.packageName)
            InfoRow("最低系统", "Android ${androidName(about.minSdk)} 及以上")
            InfoRow("运行环境", "已取得 root（Magisk / KernelSU），LSPosed ${about.lsposedVersion}")
            InfoRow("模块作用域", "系统界面")
            InfoRow("开源协议", about.license)
        }

        SectionLabel("使用说明")
        SesameCard {
            // 整行都能点：Compose 的 Checkbox 只有方块本身响应点击，点右侧文字没反应，
            // 所以把开关语义放到 Row 上，Checkbox 只做展示。
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .toggleable(
                        value = agreed,
                        role = Role.Checkbox,
                        onValueChange = onAgreedChange
                    ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(checked = agreed, onCheckedChange = null)
                Text(
                    Terms.CHECK_LABEL,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium
                )
            }
            Text(
                if (agreed) "已确认同意。计划将按既定时间表执行，下列核验项目一并运行。"
                else "尚未确认：本应用不会设置闹钟，也不会执行任何计划，下列核验项目同样不会启动。",
                style = MaterialTheme.typography.bodySmall,
                color = if (agreed) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
            TextButton(onClick = onOpenTerms, modifier = Modifier.fillMaxWidth()) {
                Text("查阅完整说明 ›")
            }
        }

        SectionLabel("核验项目")

        if (!agreed) {
            SesameCard {
                Text(
                    "请先勾选上述同意项，此处才会逐项核验权限状态。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else if (rows.isEmpty()) {
            SesameCard {
                Text(
                    "正在核验当前权限状态，请稍候；如长时间无响应，请点击右上角「重新核验」。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (agreed) {
            rows.forEach { row ->
                PermItem(row = row, onAction = onAction)
            }

            Text(
                "本页仅呈现状态，不会主动弹出系统授权框；点击「申请 / 前往设置」后才会跳转。",
                modifier = Modifier.padding(start = 4.dp, top = 8.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** 顶部汇总：Sesame-AG 用整块着色卡表示状态，不用数字徽标 */
@Composable
private fun SummaryCard(todoCount: Int, okCount: Int, agreed: Boolean) {
    val allReady = agreed && todoCount == 0
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = if (allReady) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.secondaryContainer
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                when {
                    !agreed -> "尚未确认使用说明"
                    allReady -> "各项条件均已具备"
                    else -> "尚有 $todoCount 项待处理，$okCount 项已就绪"
                },
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                when {
                    !agreed -> "请先在下方勾选「本人已阅读并同意上述内容」，定时与解锁功能方可开始工作。"
                    allReady -> "所需权限均已具备，准时唤醒与自动解锁流程可全程运行。"
                    else -> "标注「尚未授予」的项目将直接影响功能；标注「待确认」的项目，请前往管理器或系统设置核对，返回后点击「本人确认」，状态即转为「已确认」。"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PermItem(row: PermRow, onAction: (PermAction) -> Unit) {
    SesameCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                row.title,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.width(8.dp))
            StatusText(row.status)
        }
        Text(
            row.detail,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (!row.extra.isNullOrBlank()) {
            Text(
                row.extra,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
        val action = row.action
        val second = row.secondAction
        val hasFirst = action != null && !row.actionLabel.isNullOrBlank()
        val hasSecond = second != null && !row.secondActionLabel.isNullOrBlank()
        // 两个按钮各自独立：管理器没装时第一颗按钮会缺席，但「我已确认」必须还在，
        // 否则这一项就彻底没法标记成「已验证」了
        if (hasFirst || hasSecond) {
            HorizontalDivider(
                modifier = Modifier.padding(top = 8.dp),
                color = MaterialTheme.colorScheme.outlineVariant
            )
            val padding = PaddingValues(horizontal = 0.dp, vertical = 6.dp)
            if (hasFirst && hasSecond) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        onClick = { onAction(action!!) },
                        modifier = Modifier.weight(1f),
                        contentPadding = padding
                    ) {
                        Text(row.actionLabel!!, maxLines = 1)
                    }
                    TextButton(
                        onClick = { onAction(second!!) },
                        modifier = Modifier.weight(1f),
                        contentPadding = padding
                    ) {
                        Text(row.secondActionLabel!!, maxLines = 1)
                    }
                }
            } else {
                val only = if (hasFirst) action!! to row.actionLabel!! else second!! to row.secondActionLabel!!
                TextButton(
                    onClick = { onAction(only.first) },
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = padding
                ) {
                    Text(only.second + " ›")
                }
            }
        }
    }
}

/** 状态用文字 + 一个小圆点，Sesame-AG 的卡片里不堆色块徽标 */
@Composable
private fun StatusText(status: PermStatus) {
    val color = when (status) {
        PermStatus.OK, PermStatus.VERIFIED -> MaterialTheme.colorScheme.primary
        PermStatus.MISSING -> MaterialTheme.colorScheme.error
        PermStatus.UNKNOWN -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(color)
                .padding(horizontal = 3.dp, vertical = 3.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            status.label,
            style = MaterialTheme.typography.bodySmall,
            color = color
        )
    }
}
