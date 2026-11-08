package com.appscheduler.lsposed.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.appscheduler.lsposed.apps.AppCatalog
import com.appscheduler.lsposed.apps.AppEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 应用选择：列出已安装的可启动应用，支持搜索与多选。
 */
@Composable
fun AppPickerScreen(
    selected: List<String>,
    onConfirm: (List<String>) -> Unit,
    onBack: () -> Unit,
    // 应用列表来源，默认读系统已安装应用；截图测试可注入固定数据
    appLoader: suspend (Context) -> List<AppEntry> = { AppCatalog.load(it) }
) {
    val context = LocalContext.current
    var apps by remember { mutableStateOf<List<AppEntry>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var query by remember { mutableStateOf("") }
    var picked by remember { mutableStateOf(selected.toSet()) }

    LaunchedEffect(Unit) {
        loading = true
        apps = withContext(Dispatchers.IO) { appLoader(context) }
        loading = false
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        ScreenHeader("选择应用", "已选 ${picked.size} 个", onBack = onBack)

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("搜索应用名或包名") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        val filtered = if (query.isBlank()) apps else apps.filter {
            it.label.contains(query, ignoreCase = true) || it.packageName.contains(query, ignoreCase = true)
        }

        if (loading) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                CircularProgressIndicator()
            }
        } else if (filtered.isEmpty()) {
            Text(
                if (apps.isEmpty()) "没有读取到可启动的应用，请确认已授权或稍后重试"
                else "没有匹配的应用",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(filtered) { app ->
                val checked = picked.contains(app.packageName)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            if (checked) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                            else MaterialTheme.colorScheme.surface,
                            RoundedCornerShape(12.dp)
                        )
                        .clickable {
                            picked = if (checked) picked - app.packageName else picked + app.packageName
                        }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = checked,
                        onCheckedChange = { isChecked ->
                            picked = if (isChecked) picked + app.packageName else picked - app.packageName
                        }
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            app.label,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            app.packageName,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { onConfirm(picked.toList()) },
                modifier = Modifier.weight(1f)
            ) {
                Text("完成（已选 ${picked.size}）")
            }
        }
    }
}
