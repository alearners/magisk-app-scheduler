package com.appscheduler.lsposed.transfer

import android.content.Context
import android.content.pm.PackageManager
import com.appscheduler.lsposed.Config
import com.appscheduler.lsposed.LogStore
import com.appscheduler.lsposed.model.TaskItem
import com.appscheduler.lsposed.schedule.Scheduler
import com.appscheduler.lsposed.store.TaskStore
import com.appscheduler.lsposed.ui.BackgroundStyle
import com.appscheduler.lsposed.ui.ThemeMath
import com.appscheduler.lsposed.ui.ThemeMode
import com.appscheduler.lsposed.ui.ThemeSettings
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 配置文件导入 / 导出。
 *
 * 一个 JSON 文件同时装下「全局设置 + 全部计划」，方便换机、备份、分享。
 * 安全边界：文件里含明文锁屏密码（App 自己的 prefs 本来也是明文），导出前界面会提示一次。
 *
 * schema 变更规则：只加字段时保持 1；字段含义变了或者删字段才 +1，
 * 导入时遇到比自己新的 schema 直接拒绝，避免把老版本 App 的配置写坏。
 */
object ConfigTransfer {

    const val SCHEMA = 1
    const val MIME = "application/json"

    private const val APP_TAG = "AppTimer"
    private const val MAX_CHARS = 512 * 1024

    data class Result(val ok: Boolean, val message: String, val taskCount: Int = 0)

    fun defaultFileName(): String {
        val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.CHINA).format(Date())
        return "apptimer-config-$stamp.json"
    }

    /** 生成待写出的 JSON 文本 */
    fun exportJson(context: Context): String {
        val config = Config.get(context)
        val root = JSONObject()
        root.put("app", APP_TAG)
        root.put("schema", SCHEMA)
        root.put("exportedAt", SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.CHINA).format(Date()))
        root.put("versionName", versionName(context))

        val global = JSONObject()
        global.put("enabled", config.enabled())
        global.put("password", config.password())
        global.put("manualFallback", config.manualFallback())
        global.put("manualWait", config.manualWait())
        global.put("autoLock", config.autoLock())
        global.put("autoLockDelay", config.autoLockDelay())
        global.put("timerMode", config.timerMode())
        root.put("global", global)

        // 主题随配置迁移；自选图片 URI 换机后无效，因此只写参数，不写图片地址
        val theme = ThemeSettings.load(context).forTransfer()
        root.put("theme", JSONObject().apply {
            put("mode", theme.mode.storage)
            put("accentColor", theme.accentColor)
            put("backgroundStyle", theme.backgroundStyle.storage)
            put("backgroundAlpha", theme.backgroundAlpha.toDouble())
            put("backgroundScrim", theme.backgroundScrim.toDouble())
            put("buttonAlpha", theme.buttonAlpha.toDouble())
            put("timeAccentEnabled", theme.timeAccentEnabled)
            put("dayAccent", theme.dayAccent)
            put("nightAccent", theme.nightAccent)
            put("dayStart", theme.dayStart)
            put("nightStart", theme.nightStart)
        })

        val tasks = JSONArray()
        TaskStore.all(context).forEach { tasks.put(it.toJson()) }
        root.put("tasks", tasks)
        return root.toString(2)
    }

    /** 先校验、只数数量，不落盘：给界面弹确认框用 */
    fun inspect(text: String): Result {
        val parsed = parse(text)
        if (!parsed.ok) return Result(false, parsed.message)
        return Result(true, "配置来自 ${parsed.message}", parsed.taskCount)
    }

    /**
     * 覆盖式导入：先撤掉当前所有计划闹钟，再写入文件里的全局设置与计划，最后重排。
     * @return Result；失败时不会改动任何现有数据。
     */
    fun importJson(context: Context, text: String): Result {
        val parsed = parse(text)
        if (!parsed.ok) return Result(false, parsed.message)
        val payload = parsed.payload ?: return Result(false, "配置内容为空")
        val tasks = payload.tasks
        if (tasks.isEmpty() && payload.hasTasksArray.not()) {
            return Result(false, "配置中缺少计划列表")
        }

        // 1) 撤掉现有计划，避免旧闹钟残留后指向新数据
        TaskStore.all(context).forEach { Scheduler.cancel(context, it.id) }

        // 2) 全局设置先落地：Scheduler.schedule 会读它决定要不要排闹钟
        payload.global?.let { g ->
            Config.get(context).saveGlobal(
                g.optBoolean("enabled", true),
                g.optString("password", ""),
                g.optBoolean("manualFallback", true),
                g.optInt("manualWait", Config.DEFAULT_MANUAL_WAIT),
                g.optBoolean("autoLock", false),
                g.optInt("autoLockDelay", Config.DEFAULT_AUTO_LOCK_DELAY),
                g.optString("timerMode", Config.TIMER_ALARM)
            )
        }

        // 2.1) 主题：旧备份没有该对象时保持本机现有主题；图片一律退回无背景
        payload.theme?.let { t ->
            val base = ThemeSettings.load(context)
            val style = BackgroundStyle.from(
                t.optString("backgroundStyle", base.backgroundStyle.storage)
            )
            val safeStyle = if (style == BackgroundStyle.IMAGE) BackgroundStyle.NONE else style
            val dayStart = t.optString("dayStart", base.dayStart)
                .takeIf { ThemeMath.parseTimeToMinutes(it) != null } ?: base.dayStart
            val nightStart = t.optString("nightStart", base.nightStart)
                .takeIf { ThemeMath.parseTimeToMinutes(it) != null } ?: base.nightStart
            ThemeSettings.save(
                context,
                base.copy(
                    mode = ThemeMode.from(t.optString("mode", base.mode.storage)),
                    accentColor = ThemeMath.opaque(t.optInt("accentColor", base.accentColor)),
                    backgroundStyle = safeStyle,
                    backgroundImageUri = null,
                    backgroundAlpha = t.optDouble("backgroundAlpha", base.backgroundAlpha.toDouble()).toFloat(),
                    backgroundScrim = t.optDouble("backgroundScrim", base.backgroundScrim.toDouble()).toFloat(),
                    buttonAlpha = t.optDouble("buttonAlpha", base.buttonAlpha.toDouble()).toFloat(),
                    timeAccentEnabled = t.optBoolean("timeAccentEnabled", base.timeAccentEnabled),
                    dayAccent = ThemeMath.opaque(t.optInt("dayAccent", base.dayAccent)),
                    nightAccent = ThemeMath.opaque(t.optInt("nightAccent", base.nightAccent)),
                    dayStart = dayStart,
                    nightStart = nightStart
                )
            )
        }

        // 3) 计划重新编号后整体覆盖，再逐个排下一次
        var nextId = 1L
        tasks.forEach { task ->
            task.id = nextId++
            if (task.maxRuns > 0 && task.remainingRuns <= 0) {
                // 导入一份「已跑完」的备份时，把剩余次数拉回总数，否则这份计划一进来就是死状态
                task.remainingRuns = task.maxRuns
            }
        }
        TaskStore.saveAll(context, tasks)
        TaskStore.setNextId(context, nextId)
        Scheduler.applyMode(context)
        tasks.forEach { Scheduler.schedule(context, it) }

        val skipped = tasks.count { it.packages.isEmpty() }
        val mode = if (Config.get(context).internalTimer()) "应用内部计时器" else "系统闹钟"
        val message = buildString {
            append("已导入 ${tasks.size} 份计划（$mode）")
            if (skipped > 0) append("，其中 $skipped 份未选择应用，将不会触发")
        }
        LogStore.append(context, "导入配置：$message")
        return Result(true, message, tasks.size)
    }

    private data class Payload(
        val global: JSONObject?,
        val theme: JSONObject?,
        val tasks: List<TaskItem>,
        val hasTasksArray: Boolean
    )

    private data class Parsed(val ok: Boolean, val message: String, val taskCount: Int = 0, val payload: Payload? = null)

    private fun parse(text: String): Parsed {
        if (text.isBlank()) return Parsed(false, "文件内容为空")
        if (text.length > MAX_CHARS) return Parsed(false, "文件过大（超过 512 KB），不似配置备份")
        val root = try {
            JSONObject(text)
        } catch (t: Throwable) {
            return Parsed(false, "非合法的 JSON 文件")
        }
        if (root.optString("app") != APP_TAG) {
            return Parsed(false, "该文件不是 AppTimer 的配置文件")
        }
        val schema = root.optInt("schema", 0)
        if (schema <= 0) return Parsed(false, "配置缺少 schema 标记")
        if (schema > SCHEMA) return Parsed(false, "配置来自更高版本的 App（schema $schema），当前仅支持至 $SCHEMA")

        val arr = root.optJSONArray("tasks")
        val tasks = mutableListOf<TaskItem>()
        if (arr != null) {
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                tasks.add(TaskItem.fromJson(obj))
            }
        }
        val version = root.optString("versionName", "").ifBlank { "未知版本" }
        val global = root.optJSONObject("global")
        val theme = root.optJSONObject("theme")
        return Parsed(
            ok = true,
            message = "AppTimer $version",
            taskCount = tasks.size,
            payload = Payload(global, theme, tasks, arr != null)
        )
    }

    private fun versionName(context: Context): String = try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "0.0.003"
    } catch (t: PackageManager.NameNotFoundException) {
        "0.0.003"
    }
}
