package com.appscheduler.lsposed.conditions

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.PowerManager
import com.appscheduler.lsposed.model.TaskItem

/**
 * 启动条件检查：任一开启的条件不满足即跳过本次。
 */
object ConditionChecker {

    /** @return 未满足的条件描述；全部满足返回 null */
    fun unfilledReason(context: Context, task: TaskItem): String? {
        if (task.onlyCharging && !isCharging(context)) {
            return "未在充电"
        }
        if (task.onlyWifi && !isWifiConnected(context)) {
            return "未连接 WiFi"
        }
        if (task.onlyScreenOff && isScreenOn(context)) {
            return "屏幕未熄灭"
        }
        return null
    }

    private fun isCharging(context: Context): Boolean {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager ?: return false
        return bm.isCharging
    }

    private fun isWifiConnected(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    private fun isScreenOn(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return true
        return pm.isInteractive
    }
}
