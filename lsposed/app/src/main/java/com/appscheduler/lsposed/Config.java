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

    public static final String DEFAULT_PKG = "com.example.app";
    public static final String DEFAULT_PASSWORD = "0219";
    public static final String DEFAULT_TIMES = "08:00, 20:00";

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

    public void save(String pkg, String password, String times, boolean enabled) {
        sp.edit()
                .putString(KEY_PKG, pkg == null ? "" : pkg.trim())
                .putString(KEY_PASSWORD, password == null ? "" : password.trim())
                .putString(KEY_TIMES, times == null ? "" : times.trim())
                .putBoolean(KEY_ENABLED, enabled)
                .apply();
    }
}
