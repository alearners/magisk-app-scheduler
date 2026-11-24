# LSPosed 模块保留必要类名，关闭混淆相关告警
-keep class com.appscheduler.lsposed.XposedEntry { *; }
-keep class de.robv.android.xposed.** { *; }
-dontwarn de.robv.android.xposed.**
