package com.appscheduler.lsposed.store

import android.content.Context
import android.content.SharedPreferences
import com.appscheduler.lsposed.Config
import com.appscheduler.lsposed.LogStore
import com.appscheduler.lsposed.model.TaskItem
import org.json.JSONArray

/**
 * 计划列表持久化：全部计划以一条 JSON 数组存在 SharedPreferences 里。
 */
object TaskStore {

    private const val KEY_TASKS = "tasks_json"
    private const val KEY_NEXT_ID = "next_task_id"
    private const val KEY_TASKS_BACKUP = "tasks_json_corrupt_backup"

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(Config.PREFS, Context.MODE_PRIVATE)

    fun all(context: Context): MutableList<TaskItem> {
        val raw = prefs(context).getString(KEY_TASKS, null) ?: return mutableListOf()
        val list = mutableListOf<TaskItem>()
        if (raw.isBlank()) return list
        try {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                list.add(TaskItem.fromJson(arr.getJSONObject(i)))
            }
        } catch (t: Throwable) {
            // 数据损坏时不能直接当空列表：先原样留一份备份，避免下一次保存把用户计划彻底冲掉
            prefs(context).edit().putString(KEY_TASKS_BACKUP, raw).apply()
            LogStore.append(context, "计划数据解析失败，已备份原文到 $KEY_TASKS_BACKUP: $t")
        }
        return list
    }

    fun saveAll(context: Context, tasks: List<TaskItem>) {
        val arr = JSONArray()
        tasks.forEach { arr.put(it.toJson()) }
        prefs(context).edit().putString(KEY_TASKS, arr.toString()).apply()
    }

    fun upsert(context: Context, task: TaskItem): TaskItem {
        val list = all(context)
        if (task.id <= 0L) {
            task.id = nextId(context)
        }
        val index = list.indexOfFirst { it.id == task.id }
        if (index >= 0) {
            list[index] = task
        } else {
            list.add(task)
        }
        saveAll(context, list)
        return task
    }

    fun delete(context: Context, id: Long) {
        val list = all(context)
        list.removeAll { it.id == id }
        saveAll(context, list)
    }

    fun find(context: Context, id: Long): TaskItem? = all(context).firstOrNull { it.id == id }

    fun nextId(context: Context): Long {
        val p = prefs(context)
        val id = p.getLong(KEY_NEXT_ID, 1L)
        p.edit().putLong(KEY_NEXT_ID, id + 1L).apply()
        return id
    }
}
