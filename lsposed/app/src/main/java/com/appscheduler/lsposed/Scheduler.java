package com.appscheduler.lsposed;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/**
 * 用 AlarmManager 在每天指定时间点触发一次任务，执行完再排下一次。
 */
public class Scheduler {

    private static final int REQUEST_CODE = 1001;

    private static PendingIntent pendingIntent(Context context) {
        Intent intent = new Intent(context, AlarmReceiver.class);
        return PendingIntent.getBroadcast(
                context,
                REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    public static void cancel(Context context) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am != null) {
            am.cancel(pendingIntent(context));
        }
    }

    /**
     * 已启用则排下一次任务，否则取消。
     */
    public static void scheduleNext(Context context) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) {
            return;
        }
        Config config = Config.get(context);
        if (!config.enabled()) {
            cancel(context);
            return;
        }
        long next = nextTriggerMillis(config.times());
        if (next <= 0) {
            cancel(context);
            LogStore.append(context, "调度为空或无法解析，已取消定时");
            return;
        }
        PendingIntent pi = pendingIntent(context);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pi);
            } else {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pi);
            }
        } catch (SecurityException e) {
            am.set(AlarmManager.RTC_WAKEUP, next, pi);
        }
    }

    /**
     * @return 下一个触发时间戳（毫秒），无法解析返回 -1。
     */
    public static long nextTriggerMillis(String times) {
        if (times == null) {
            return -1;
        }
        long now = System.currentTimeMillis();
        long best = Long.MAX_VALUE;
        for (String raw : times.split(",")) {
            String item = raw.trim();
            int[] hm = parseHm(item);
            if (hm == null) {
                continue;
            }
            Calendar cal = Calendar.getInstance();
            cal.set(Calendar.HOUR_OF_DAY, hm[0]);
            cal.set(Calendar.MINUTE, hm[1]);
            cal.set(Calendar.SECOND, 0);
            cal.set(Calendar.MILLISECOND, 0);
            long t = cal.getTimeInMillis();
            if (t <= now + 5000L) {
                t += 24L * 60L * 60L * 1000L;
            }
            if (t < best) {
                best = t;
            }
        }
        return best == Long.MAX_VALUE ? -1 : best;
    }

    public static String describeNext(Context context) {
        long t = nextTriggerMillis(Config.get(context).times());
        if (t <= 0) {
            return "无有效时间点";
        }
        return new SimpleDateFormat("MM-dd HH:mm", Locale.CHINA).format(new Date(t));
    }

    private static int[] parseHm(String item) {
        int sep = item.indexOf(':');
        if (sep <= 0) {
            return null;
        }
        try {
            int hour = Integer.parseInt(item.substring(0, sep).trim());
            int minute = Integer.parseInt(item.substring(sep + 1).trim());
            if (hour < 0 || hour > 23 || minute < 0 || minute > 59) {
                return null;
            }
            return new int[]{hour, minute};
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
