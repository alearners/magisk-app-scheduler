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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.appscheduler.lsposed.Config

/**
 * 「更多 → 通知位置」：选择全局提示条的停靠方式。
 * 自由拖动模式下提示条可直接按住移动，松手后的位置按屏幕比例保存。
 */
@Composable
fun NotificationSettingsScreen(
    mode: String,
    onModeChange: (String) -> Unit,
    onReset: () -> Unit,
    onPreview: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val freeMode = mode == Config.SNACKBAR_FREE

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ScreenHeader(
            title = "通知位置",
            subtitle = "选择提示条停靠方式，或按住提示条自由拖动",
            onBack = onBack
        )

        SectionCard("停靠方式") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ChoiceChip(
                    label = "顶部",
                    selected = mode == Config.SNACKBAR_TOP
                ) { onModeChange(Config.SNACKBAR_TOP) }
                ChoiceChip(
                    label = "底部",
                    selected = mode == Config.SNACKBAR_BOTTOM
                ) { onModeChange(Config.SNACKBAR_BOTTOM) }
                ChoiceChip(
                    label = "自由拖动",
                    selected = freeMode
                ) { onModeChange(Config.SNACKBAR_FREE) }
            }
            HelpParagraph(
                when (mode) {
                    Config.SNACKBAR_TOP -> "提示条固定在状态栏下方的顶部中央，不响应拖动。"
                    Config.SNACKBAR_BOTTOM -> "提示条固定在底部中央，位于内容与导航栏上方，不响应拖动。"
                    else -> "提示条可按住拖到页面内任意位置；松手后自动记忆，切换页面或重启应用仍保持。"
                }
            )
        }

        SectionCard("位置预览与重置") {
            HelpParagraph(
                "预览会显示当前全局提示条；自由拖动模式下可直接按住它移动，用于确认是否遮挡按钮或导航栏。"
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(onClick = onPreview, modifier = Modifier.weight(1f)) {
                    Text("预览提示条")
                }
                OutlinedButton(onClick = onReset, modifier = Modifier.weight(1f)) {
                    Text("重置位置")
                }
            }
            HelpParagraph("「重置位置」将自由拖动位置恢复为顶部居中，不改变当前停靠方式。")
        }

        if (freeMode) {
            SectionCard("拖动说明") {
                HelpParagraph(
                    "按住提示条任意位置上下左右拖动即可；接近状态栏或屏幕边缘时会自动限位，避免文字被裁掉。\n" +
                        "顶部、底部两种停靠方式下拖动不生效；需要移动时先切回「自由拖动」。"
                )
            }
        }
    }
}
