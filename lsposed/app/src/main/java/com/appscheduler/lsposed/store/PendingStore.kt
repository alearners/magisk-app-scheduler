package com.appscheduler.lsposed.store

import android.content.Context
import android.content.SharedPreferences
import com.appscheduler.lsposed.Config
import org.json.JSONArray
import org.json.JSONObject

/**
 * 「自动解锁失败、等待用户手动解锁」期间待启动的计划。
 * 存在 SharedPreferences 里，进程被系统回收也不会丢。
 */
data class PendingLaunch(
    val taskId: Long = 0L,
    val title: String = "",
    val packages: List<String> = emptyList(),
    val deadline: Long = 0L
)

object PendingStore {

    private const val KEY_PENDING = "pending_launches"

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(Config.PREFS, Context.MODE_PRIVATE)

    fun all(context: Context): MutableList<PendingLaunch> {
        val raw = prefs(context).getString(KEY_PENDING, null)
        val list = mutableListOf<PendingLaunch>()
        if (raw.isNullOrBlank()) {
            return list
        }
        try {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val pkgs = mutableListOf<String>()
                val pk = o.optJSONArray("packages")
                if (pk != null) {
                    for (j in 0 until pk.length()) {
                        val item = pk.optString(j, "")
                        if (item.isNotBlank()) pkgs.add(item)
                    }
                }
                list.add(
                    PendingLaunch(
                        taskId = o.optLong("taskId", 0L),
                        title = o.optString("title", ""),
                        packages = pkgs,
                        deadline = o.optLong("deadline", 0L)
                    )
                )
            }
        } catch (t: Throwable) {
            // 解析失败时按空处理，避免卡住整条解锁链路
        }
        return list
    }

    private fun save(context: Context, list: List<PendingLaunch>) {
        val arr = JSONArray()
        list.forEach { entry ->
            arr.put(
                JSONObject()
                    .put("taskId", entry.taskId)
                    .put("title", entry.title)
                    .put("packages", JSONArray(entry.packages))
                    .put("deadline", entry.deadline)
            )
        }
        prefs(context).edit().putString(KEY_PENDING, arr.toString()).apply()
    }

    /** 同一计划只保留最新一条，避免重复触发堆叠 */
    fun add(context: Context, entry: PendingLaunch) {
        val list = all(context).filter { it.taskId != entry.taskId }.toMutableList()
        list.add(entry)
        save(context, list)
    }

    fun remove(context: Context, taskId: Long) {
        val list = all(context).filter { it.taskId != taskId }
        save(context, list)
    }

    fun clear(context: Context) {
        prefs(context).edit().remove(KEY_PENDING).apply()
    }
}
