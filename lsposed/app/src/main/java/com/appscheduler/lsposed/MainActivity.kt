package com.appscheduler.lsposed

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.appscheduler.lsposed.schedule.Scheduler
import com.appscheduler.lsposed.ui.AppSchedulerTheme
import com.appscheduler.lsposed.ui.MainScreen

/**
 * 入口界面：Kotlin + Jetpack Compose（Material3），配色参考 Sesame-AG。
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestNotificationPermission()
        // 进程刚起来时把计时方式对上：内部计时器模式要确保常驻服务在跑，
        // 系统闹钟模式则重排一次闹钟（进程被杀期间可能漏排）
        try {
            Scheduler.applyMode(this)
        } catch (t: Throwable) {
            // 计时模式恢复失败不应该拦住界面
        }
        setContent {
            AppSchedulerTheme {
                MainScreen()
            }
        }
    }

    /**
     * Android 13+ 需要运行时授予通知权限，否则「请手动解锁」提醒与失败通知都不会显示。
     */
    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return
        }
        val granted = checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        if (!granted) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQ_NOTIFICATION)
        }
    }

    private companion object {
        const val REQ_NOTIFICATION = 1001
    }
}
