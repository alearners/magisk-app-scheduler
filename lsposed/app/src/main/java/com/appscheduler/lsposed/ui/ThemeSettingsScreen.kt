package com.appscheduler.lsposed.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private enum class ColorTarget { ACCENT, DAY, NIGHT }

/**
 * 主题设置：主题模式、按钮色、按时段切换、背景与透明度、可读性提示。
 * 页面内先在草稿上预览，确认保存后才写入本机并作用于全局。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ThemeSettingsScreen(
    settings: ThemeSettings,
    onSave: (ThemeSettings) -> Unit,
    onBack: () -> Unit,
    nowMinutes: Int = currentMinuteOfDay(),
    imagePickerEnabled: Boolean = true,
    initialScroll: Int = 0
) {
    val context = LocalContext.current
    val systemDark = isSystemInDarkTheme()
    val now = nowMinutes
    val scrollState = rememberScrollState(initialScroll)
    val scope = rememberCoroutineScope()
    val timeFieldsBringIntoView = remember { BringIntoViewRequester() }
    var draft by remember(settings) { mutableStateOf(settings) }
    var attemptedSave by remember { mutableStateOf(false) }
    var colorTarget by remember { mutableStateOf<ColorTarget?>(null) }
    var confirmReset by remember { mutableStateOf(false) }

    val dayValid = ThemeMath.parseTimeToMinutes(draft.dayStart) != null
    val nightValid = ThemeMath.parseTimeToMinutes(draft.nightStart) != null

    // 截图测试没有 ActivityResultRegistryOwner，此时不注册文件选择器
    val launchImagePicker: () -> Unit = if (imagePickerEnabled) {
        val picker = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocument()
        ) { uri ->
            if (uri != null) {
                try {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (_: Throwable) {
                    // 某些文件提供方不授予持久权限；本次仍可预览，重启后若失效需重新选择。
                }
                draft = draft.copy(
                    backgroundStyle = BackgroundStyle.IMAGE,
                    backgroundImageUri = uri.toString()
                )
            }
        }
        val launch: () -> Unit = { picker.launch(arrayOf("image/*")) }
        launch
    } else {
        {}
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ScreenHeader(
            title = "主题设置",
            subtitle = "调整按钮配色、背景、透明度与按时段切换规则",
            onBack = onBack
        )

        ThemePreview(draft, systemDark, now)

        SectionCard("主题模式") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ThemeMode.entries.forEach { mode ->
                    ChoiceChip(mode.label, draft.mode == mode) {
                        draft = draft.copy(mode = mode)
                    }
                }
            }
            HelpParagraph("「跟随系统」会随系统深浅色设置切换；固定模式不受系统设置影响。")
        }

        SectionCard("按钮配色") {
            HelpParagraph("按钮色同时作用于主要按钮、选中标签、开关与文字链接。")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ThemePalette.presets.take(4).forEach { preset ->
                    ColorSwatch(
                        argb = preset.argb,
                        label = preset.name,
                        selected = !draft.timeAccentEnabled && draft.accentColor == preset.argb,
                        onClick = {
                            draft = draft.copy(accentColor = preset.argb, timeAccentEnabled = false)
                        }
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ThemePalette.presets.drop(4).forEach { preset ->
                    ColorSwatch(
                        argb = preset.argb,
                        label = preset.name,
                        selected = !draft.timeAccentEnabled && draft.accentColor == preset.argb,
                        onClick = {
                            draft = draft.copy(accentColor = preset.argb, timeAccentEnabled = false)
                        }
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "基础按钮色（未开启时段切换时）：${hex(draft.accentColor)}",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = { colorTarget = ColorTarget.ACCENT }) {
                    Text("自定义")
                }
            }

            SwitchRow(
                title = "按时间切换按钮配色",
                description = "开启后，日间时段使用日间色，其余时间使用夜间色；配色每分钟自动刷新。",
                checked = draft.timeAccentEnabled,
                onCheckedChange = { draft = draft.copy(timeAccentEnabled = it) }
            )

            if (draft.timeAccentEnabled) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    PaletteRow(
                        title = "日间色",
                        argb = draft.dayAccent,
                        onClick = { colorTarget = ColorTarget.DAY }
                    )
                    PaletteRow(
                        title = "夜间色",
                        argb = draft.nightAccent,
                        onClick = { colorTarget = ColorTarget.NIGHT }
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .bringIntoViewRequester(timeFieldsBringIntoView),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = draft.dayStart,
                        onValueChange = { draft = draft.copy(dayStart = it.take(5)) },
                        label = { Text("日间开始") },
                        singleLine = true,
                        isError = attemptedSave && !dayValid,
                        supportingText = {
                            Text(if (attemptedSave && !dayValid) "请输入 00:00 至 23:59" else "例如 07:00")
                        },
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = draft.nightStart,
                        onValueChange = { draft = draft.copy(nightStart = it.take(5)) },
                        label = { Text("夜间开始") },
                        singleLine = true,
                        isError = attemptedSave && !nightValid,
                        supportingText = {
                            Text(if (attemptedSave && !nightValid) "请输入 00:00 至 23:59" else "例如 19:00")
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
                HelpParagraph(
                    "当前为「${draft.phaseLabel(now)}」，实际按钮色 ${hex(draft.effectiveAccent(now))}。"
                )
            }
        }

        SectionCard("背景") {
            HelpParagraph("内置渐变不依赖网络，可单独调整透明度；自选图片仅保存在本机。")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(BackgroundStyle.NONE, BackgroundStyle.MIST, BackgroundStyle.AURORA).forEach { style ->
                    BackgroundChoice(
                        style = style,
                        darkTheme = draft.darkTheme(systemDark),
                        selected = draft.backgroundStyle == style,
                        onClick = {
                            draft = draft.copy(
                                backgroundStyle = style,
                                backgroundImageUri = null
                            )
                        }
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(BackgroundStyle.DUSK, BackgroundStyle.FOREST, BackgroundStyle.INK).forEach { style ->
                    BackgroundChoice(
                        style = style,
                        darkTheme = draft.darkTheme(systemDark),
                        selected = draft.backgroundStyle == style,
                        onClick = {
                            draft = draft.copy(
                                backgroundStyle = style,
                                backgroundImageUri = null
                            )
                        }
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = { launchImagePicker() },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(if (draft.backgroundStyle == BackgroundStyle.IMAGE) "更换图片" else "选择图片")
                }
                if (draft.backgroundStyle == BackgroundStyle.IMAGE) {
                    Spacer(Modifier.width(8.dp))
                    TextButton(
                        onClick = {
                            draft = draft.copy(
                                backgroundStyle = BackgroundStyle.NONE,
                                backgroundImageUri = null
                            )
                        }
                    ) {
                        Text("移除")
                    }
                }
            }
            if (draft.backgroundStyle == BackgroundStyle.IMAGE) {
                Text(
                    "当前使用自选图片。若重启后显示异常，请重新选择图片。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            SliderSetting(
                title = "背景透明度",
                value = draft.backgroundAlpha,
                valueRange = 0f..1f,
                valueText = "${(draft.backgroundAlpha * 100).roundToInt()}%",
                description = "数值越低，背景越淡；设为 0% 时仅保留主题底色。"
            ) { draft = draft.copy(backgroundAlpha = it) }
            SliderSetting(
                title = "内容遮罩",
                value = draft.backgroundScrim,
                valueRange = 0f..0.8f,
                valueText = "${(draft.backgroundScrim * 100).roundToInt()}%",
                description = "在背景之上叠加主题底色，用于保障标题与正文可读；默认 18%。"
            ) { draft = draft.copy(backgroundScrim = it) }
        }

        SectionCard("按钮透明度") {
            SliderSetting(
                title = "按钮不透明度",
                value = draft.buttonAlpha,
                valueRange = 0.2f..1f,
                valueText = "${(draft.buttonAlpha * 100).roundToInt()}%",
                description = "最低 20%，以免按钮与开关不可见；文字色会按实际合成底色自动选择黑白。"
            ) { draft = draft.copy(buttonAlpha = it) }
            val ratio = buttonContrastRatio(draft, now, systemDark)
            Text(
                text = "按钮与底色区分度：${"%.1f".format(ratio)}:1" +
                    if (ratio < 3.0) "。区分度偏低，建议提高不透明度或更换按钮色。" else "。当前设置可正常辨识。",
                style = MaterialTheme.typography.labelSmall,
                color = if (ratio < 3.0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        SectionCard("说明与恢复") {
            HelpParagraph(
                "主题设置只影响本应用界面，不改变计划、日志、权限与模块作用域。保存后在全部页面立即生效。"
            )
            HelpParagraph(
                "导出配置时，主题参数会一并写入备份；自选背景图片因涉及本机文件授权，不随备份迁移。"
            )
            OutlinedButton(
                onClick = { confirmReset = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("恢复默认主题")
            }
        }

        Button(
            onClick = {
                attemptedSave = true
                if (!draft.timeAccentEnabled || (dayValid && nightValid)) {
                    onSave(draft)
                } else {
                    scope.launch {
                        // 等错误提示完成布局后再滚动，确保提示行一并进入可视区
                        withFrameNanos { }
                        timeFieldsBringIntoView.bringIntoView()
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("保存主题")
        }
        Text(
            "保存后立即应用；未保存返回时，本次调整不写入配置。",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
    }

    colorTarget?.let { target ->
        val initial = when (target) {
            ColorTarget.ACCENT -> draft.accentColor
            ColorTarget.DAY -> draft.dayAccent
            ColorTarget.NIGHT -> draft.nightAccent
        }
        ColorPickerDialog(
            initial = initial,
            onDismiss = { colorTarget = null },
            onConfirm = { argb ->
                draft = when (target) {
                    ColorTarget.ACCENT -> draft.copy(accentColor = argb, timeAccentEnabled = false)
                    ColorTarget.DAY -> draft.copy(dayAccent = argb, timeAccentEnabled = true)
                    ColorTarget.NIGHT -> draft.copy(nightAccent = argb, timeAccentEnabled = true)
                }
                colorTarget = null
            }
        )
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("确认恢复默认主题？") },
            text = {
                Text("将恢复跟随系统、青蓝按钮色与无背景，并清除自选图片授权。该操作不会删除计划与日志。")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val defaults = ThemeSettings()
                        draft = defaults
                        confirmReset = false
                        onSave(defaults)
                    }
                ) { Text("恢复默认") }
            },
            dismissButton = {
                TextButton(onClick = { confirmReset = false }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun ThemePreview(settings: ThemeSettings, systemDark: Boolean, now: Int) {
    AppSchedulerTheme(settings = settings, nowMinutes = now, darkTheme = systemDark) {
        AppThemeBackground(
            settings = settings,
            darkTheme = settings.darkTheme(systemDark),
            modifier = Modifier
                .fillMaxWidth()
                .height(196.dp)
                .clip(RoundedCornerShape(20.dp))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    "界面预览",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.86f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            "示例卡片：文字保持可读，按钮随主题变化。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = {}) { Text("示例按钮") }
                            ChoiceChip("已选中", true) {}
                        }
                        Text(
                            settings.summary(now),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ColorSwatch(
    argb: Int,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(horizontal = 2.dp, vertical = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(Color(argb))
                .border(
                    width = if (selected) 3.dp else 1.dp,
                    color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outlineVariant,
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            if (selected) {
                Text(
                    "✓",
                    color = Color(ThemeMath.contrastOn(argb)),
                    fontWeight = FontWeight.Bold
                )
            }
        }
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun PaletteRow(title: String, argb: Int, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .padding(horizontal = 4.dp, vertical = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(Color(argb))
        )
        Spacer(Modifier.width(8.dp))
        Column {
            Text(title, style = MaterialTheme.typography.labelSmall)
            Text(hex(argb), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        TextButton(onClick = onClick) { Text("修改") }
    }
}

@Composable
private fun BackgroundChoice(
    style: BackgroundStyle,
    darkTheme: Boolean,
    selected: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .padding(4.dp)
    ) {
        Box(
            modifier = Modifier
                .width(66.dp)
                .height(46.dp)
                .clip(RoundedCornerShape(10.dp))
                .then(
                    if (style == BackgroundStyle.NONE) {
                        Modifier.background(MaterialTheme.colorScheme.surfaceVariant)
                    } else {
                        Modifier.background(backgroundBrush(style, darkTheme) ?: Brush.verticalGradient(listOf(Color.Gray, Color.DarkGray)))
                    }
                )
                .border(
                    width = if (selected) 3.dp else 1.dp,
                    color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outlineVariant,
                    shape = RoundedCornerShape(10.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            if (style == BackgroundStyle.NONE) {
                Text("无", style = MaterialTheme.typography.labelSmall)
            }
        }
        Text(style.label, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun SliderSetting(
    title: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    valueText: String,
    description: String,
    onChange: (Float) -> Unit
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(valueText, style = MaterialTheme.typography.labelLarge)
        }
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = valueRange,
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            description,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ColorPickerDialog(
    initial: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    var red by remember(initial) { mutableStateOf((initial shr 16 and 0xFF).toFloat()) }
    var green by remember(initial) { mutableStateOf((initial shr 8 and 0xFF).toFloat()) }
    var blue by remember(initial) { mutableStateOf((initial and 0xFF).toFloat()) }
    val argb = (0xFF shl 24) or
        (red.roundToInt() shl 16) or
        (green.roundToInt() shl 8) or
        blue.roundToInt()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("调整颜色") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(argb))
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(hex(argb), style = MaterialTheme.typography.titleMedium)
                }
                ColorChannel("红", red) { red = it }
                ColorChannel("绿", green) { green = it }
                ColorChannel("蓝", blue) { blue = it }
                Text(
                    "文字预览",
                    color = Color(ThemeMath.contrastOn(argb)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(argb))
                        .padding(10.dp)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(argb) }) { Text("采用") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@Composable
private fun ColorChannel(label: String, value: Float, onChange: (Float) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.width(28.dp), style = MaterialTheme.typography.labelLarge)
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = 0f..255f,
            modifier = Modifier.weight(1f)
        )
        Text(
            value.roundToInt().toString(),
            modifier = Modifier.width(34.dp),
            style = MaterialTheme.typography.labelSmall
        )
    }
}

private fun hex(argb: Int): String = "#%06X".format(argb and 0xFFFFFF)
