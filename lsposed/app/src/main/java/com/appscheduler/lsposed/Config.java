package com.appscheduler.lsposed;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * 配置读写：包名、锁屏密码、调度时间点、启用开关。
 */
public class Config {

    public static final String PREFS = "app_scheduler";

    public static final String KEY_PKG = "pkg";
    public static final String KEY_PASSWORD = "password";
    public static final String KEY_TIMES = "times";
    public static final String KEY_ENABLED = "enabled";
    public static final String KEY_MANUAL_FALLBACK = "manual_fallback";
    public static final String KEY_MANUAL_WAIT = "manual_wait";

    public static final String DEFAULT_PKG = "com.example.app";
    /** 不预置任何密码，必须在界面里填写；留空时跳过密码注入，只走上滑 + 兜底 */
    public static final String DEFAULT_PASSWORD = "";
    public static final String DEFAULT_TIMES = "08:00, 20:00";
    public static final int DEFAULT_MANUAL_WAIT = 120;

    private final SharedPreferences sp;

    private Config(Context context) {
        this.sp = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static Config get(Context context) {
        return new Config(context);
    }

    public String pkg() {
        return sp.getString(KEY_PKG, DEFAULT_PKG);
    }

    public String password() {
        return sp.getString(KEY_PASSWORD, DEFAULT_PASSWORD);
    }

    public String times() {
        return sp.getString(KEY_TIMES, DEFAULT_TIMES);
    }

    public boolean enabled() {
        return sp.getBoolean(KEY_ENABLED, false);
    }

    /**
     * 自动解锁失败时，是否等待用户手动解锁（解锁后立即启动 App）。
     */
    public boolean manualFallback() {
        return sp.getBoolean(KEY_MANUAL_FALLBACK, true);
    }

    /**
     * 等待手动解锁的最长秒数。
     */
    public int manualWait() {
        int value = sp.getInt(KEY_MANUAL_WAIT, DEFAULT_MANUAL_WAIT);
        if (value < 10) {
            value = 10;
        }
        if (value > 1800) {
            value = 1800;
        }
        return value;
    }

    public void save(String pkg, String password, String times, boolean enabled) {
        sp.edit()
                .putString(KEY_PKG, pkg == null ? "" : pkg.trim())
                .putString(KEY_PASSWORD, password == null ? "" : password.trim())
                .putString(KEY_TIMES, times == null ? "" : times.trim())
                .putBoolean(KEY_ENABLED, enabled)
                .apply();
    }

    public void save(String pkg, String password, String times, boolean enabled,
                     boolean manualFallback, int manualWait) {
        sp.edit()
                .putString(KEY_PKG, pkg == null ? "" : pkg.trim())
                .putString(KEY_PASSWORD, password == null ? "" : password.trim())
                .putString(KEY_TIMES, times == null ? "" : times.trim())
                .putBoolean(KEY_ENABLED, enabled)
                .putBoolean(KEY_MANUAL_FALLBACK, manualFallback)
                .putInt(KEY_MANUAL_WAIT, manualWait)
                .apply();
    }

    /**
     * 全局设置：调度总开关、锁屏密码、手动解锁兜底。计划本身存在 TaskStore 里。
     */
    public void saveGlobal(boolean enabled, String password, boolean manualFallback, int manualWait) {
        sp.edit()
                .putBoolean(KEY_ENABLED, enabled)
                .putString(KEY_PASSWORD, password == null ? "" : password.trim())
                .putBoolean(KEY_MANUAL_FALLBACK, manualFallback)
                .putInt(KEY_MANUAL_WAIT, manualWait)
                .apply();
    }
}
