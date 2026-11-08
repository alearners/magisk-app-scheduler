package com.appscheduler.lsposed.apps

import android.content.Context
import android.content.Intent

/**
 * 已安装可启动应用列表（供界面里勾选）。
 */
data class AppEntry(val label: String, val packageName: String)

object AppCatalog {

    fun load(context: Context): List<AppEntry> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val result = LinkedHashMap<String, AppEntry>()
        try {
            val infos = pm.queryIntentActivities(intent, 0)
            infos.forEach { info ->
                val pkg = info.activityInfo?.packageName ?: return@forEach
                if (pkg == context.packageName) return@forEach
                if (result.containsKey(pkg)) return@forEach
                val label = try {
                    info.loadLabel(pm).toString()
                } catch (t: Throwable) {
                    pkg
                }
                result[pkg] = AppEntry(if (label.isBlank()) pkg else label, pkg)
            }
        } catch (t: Throwable) {
            // 查询失败时返回空列表，界面提示重试
        }
        return result.values.sortedBy { it.label }
    }
}
