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
    /** 启动完 App 后，若用户一直没操作屏幕，过多少秒自动熄屏上锁 */
    public static final String KEY_AUTO_LOCK = "auto_lock";
    public static final String KEY_AUTO_LOCK_DELAY = "auto_lock_delay";
    /** 调度触发的时间基准：系统闹钟（alarm）或应用内部计时器（internal） */
    public static final String KEY_TIMER_MODE = "timer_mode";

    /** 全局提示条停靠方式：顶部 / 底部 / 自由拖动 */
    public static final String KEY_SNACKBAR_MODE = "snackbar_mode";
    /** 自由拖动时保存的相对位置，取值 0–1，分别对应可移动范围的两端 */
    public static final String KEY_SNACKBAR_X = "snackbar_x";
    public static final String KEY_SNACKBAR_Y = "snackbar_y";

    public static final String SNACKBAR_TOP = "top";
    public static final String SNACKBAR_BOTTOM = "bottom";
    public static final String SNACKBAR_FREE = "free";
    public static final String DEFAULT_SNACKBAR_MODE = SNACKBAR_FREE;

    /** 是否已经勾选同意「使用说明与免责声明」；未同意前不排闹钟、不执行任何计划 */
    public static final String KEY_AGREED = "agreement_accepted";

    /** 单天日志文件的大小上限（KB），1–1024 */
    public static final String KEY_LOG_LIMIT_KB = "log_limit_kb";
    public static final int DEFAULT_LOG_LIMIT_KB = 512;
    public static final int MIN_LOG_LIMIT_KB = 1;
    public static final int MAX_LOG_LIMIT_KB = 1024;

    public static final String DEFAULT_PKG = "com.example.app";
    /** 不预置任何密码，必须在界面里填写；留空时跳过密码注入，只走上滑 + 兜底 */
    public static final String DEFAULT_PASSWORD = "";
    public static final String DEFAULT_TIMES = "08:00, 20:00";
    public static final int DEFAULT_MANUAL_WAIT = 120;
    public static final int DEFAULT_AUTO_LOCK_DELAY = 5;

    /** 默认不自动锁屏：它会真的把屏幕关掉，必须是用户明确打开的行为 */
    public static final boolean DEFAULT_AUTO_LOCK = false;
    public static final String TIMER_ALARM = "alarm";
    public static final String TIMER_INTERNAL = "internal";
    public static final String DEFAULT_TIMER_MODE = TIMER_ALARM;

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
        // 首次安装默认开启总开关：计划本身仍要用户手动创建，这里只表示「到点时是否执行」
        return sp.getBoolean(KEY_ENABLED, true);
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

    /**
     * 启动完 App 后是否自动熄屏上锁。
     */
    public boolean autoLock() {
        return sp.getBoolean(KEY_AUTO_LOCK, DEFAULT_AUTO_LOCK);
    }

    /**
     * 启动后等待多少秒没有操作就锁屏，2–600 秒。
     */
    public int autoLockDelay() {
        int value = sp.getInt(KEY_AUTO_LOCK_DELAY, DEFAULT_AUTO_LOCK_DELAY);
        if (value < 2) {
            value = 2;
        }
        if (value > 600) {
            value = 600;
        }
        return value;
    }

    /**
     * 调度触发的时间基准：TIMER_ALARM（系统闹钟）或 TIMER_INTERNAL（应用内部计时器）。
     */
    public String timerMode() {
        String value = sp.getString(KEY_TIMER_MODE, DEFAULT_TIMER_MODE);
        return TIMER_INTERNAL.equals(value) ? TIMER_INTERNAL : TIMER_ALARM;
    }

    /** 内部计时器模式：不依赖系统闹钟，由前台服务自己数时间 */
    public boolean internalTimer() {
        return TIMER_INTERNAL.equals(timerMode());
    }

    /**
     * 全局提示条停靠方式：顶部、底部或自由拖动。
     * 旧配置没有这一项时按自由拖动处理，默认位置仍是顶部居中。
     */
    public String snackbarMode() {
        String value = sp.getString(KEY_SNACKBAR_MODE, DEFAULT_SNACKBAR_MODE);
        if (SNACKBAR_TOP.equals(value) || SNACKBAR_BOTTOM.equals(value)) {
            return value;
        }
        return SNACKBAR_FREE;
    }

    public void setSnackbarMode(String mode) {
        String value;
        if (SNACKBAR_TOP.equals(mode)) {
            value = SNACKBAR_TOP;
        } else if (SNACKBAR_BOTTOM.equals(mode)) {
            value = SNACKBAR_BOTTOM;
        } else {
            value = SNACKBAR_FREE;
        }
        sp.edit().putString(KEY_SNACKBAR_MODE, value).apply();
    }

    /** 自由拖动位置：横向 0–1，纵向 0–1 */
    public float snackbarX() {
        return clampFraction(sp.getFloat(KEY_SNACKBAR_X, 0.5f));
    }

    public float snackbarY() {
        return clampFraction(sp.getFloat(KEY_SNACKBAR_Y, 0f));
    }

    public void saveSnackbarPosition(float x, float y) {
        sp.edit()
                .putFloat(KEY_SNACKBAR_X, clampFraction(x))
                .putFloat(KEY_SNACKBAR_Y, clampFraction(y))
                .apply();
    }

    private static float clampFraction(float value) {
        if (value < 0f) {
            return 0f;
        }
        if (value > 1f) {
            return 1f;
        }
        return value;
    }

    /**
     * 是否已勾选同意使用说明。
     * 未同意时整个自动化链路都是关的：不排闹钟、不起计时服务、不执行计划。
     */
    public boolean agreed() {
        return sp.getBoolean(KEY_AGREED, false);
    }

    /** 勾选同意后写入；只有界面上的显式勾选会调用它 */
    public void setAgreed(boolean agreed) {
        sp.edit().putBoolean(KEY_AGREED, agreed).apply();
    }

    /** 单天日志文件最大多少 KB，1–1024 */
    public int logLimitKb() {
        int value = sp.getInt(KEY_LOG_LIMIT_KB, DEFAULT_LOG_LIMIT_KB);
        if (value < MIN_LOG_LIMIT_KB) {
            value = MIN_LOG_LIMIT_KB;
        }
        if (value > MAX_LOG_LIMIT_KB) {
            value = MAX_LOG_LIMIT_KB;
        }
        return value;
    }

    public long logLimitBytes() {
        return logLimitKb() * 1024L;
    }

    public void setLogLimitKb(int kb) {
        int value = kb;
        if (value < MIN_LOG_LIMIT_KB) {
            value = MIN_LOG_LIMIT_KB;
        }
        if (value > MAX_LOG_LIMIT_KB) {
            value = MAX_LOG_LIMIT_KB;
        }
        sp.edit().putInt(KEY_LOG_LIMIT_KB, value).apply();
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
        // 兼容旧调用：不动自动锁屏与计时方式
        sp.edit()
                .putBoolean(KEY_ENABLED, enabled)
                .putString(KEY_PASSWORD, password == null ? "" : password.trim())
                .putBoolean(KEY_MANUAL_FALLBACK, manualFallback)
                .putInt(KEY_MANUAL_WAIT, manualWait)
                .apply();
    }

    public void saveGlobal(boolean enabled, String password, boolean manualFallback, int manualWait,
                           boolean autoLock, int autoLockDelay, String timerMode) {
        sp.edit()
                .putBoolean(KEY_ENABLED, enabled)
                .putString(KEY_PASSWORD, password == null ? "" : password.trim())
                .putBoolean(KEY_MANUAL_FALLBACK, manualFallback)
                .putInt(KEY_MANUAL_WAIT, manualWait)
                .putBoolean(KEY_AUTO_LOCK, autoLock)
                .putInt(KEY_AUTO_LOCK_DELAY, autoLockDelay)
                .putString(KEY_TIMER_MODE, TIMER_INTERNAL.equals(timerMode) ? TIMER_INTERNAL : TIMER_ALARM)
                .apply();
    }
}
