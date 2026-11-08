package com.appscheduler.lsposed;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.text.TextUtils;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 核心流程（与 Magisk 版 run_app.sh 等价，通过 su 执行）：
 * force-stop → 判断锁屏 → 唤醒 → 广播让 LSPosed 钩子收起 Keyguard
 * → wm dismiss-keyguard → 上滑 + 密码 → 校验 → 启动 App。
 */
public class TaskRunner {

    /** 发给 SystemUI 内 LSPosed 钩子的收起锁屏广播 */
    public static final String ACTION_DISMISS = "com.appscheduler.action.DISMISS_KEYGUARD";
    public static final String SYSTEMUI_PKG = "com.android.systemui";

    private static final int SLIDE_START_PERCENT = 80;
    private static final int SLIDE_END_PERCENT = 25;
    private static final int SLIDE_DURATION_MS = 300;

    private static class Result {
        int code = -1;
        String out = "";
    }

    public static String run(Context context) {
        StringBuilder log = new StringBuilder();
        Config config = Config.get(context);
        String pkg = config.pkg();
        String stamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA).format(new Date());
        log.append("=== 开始执行 ").append(stamp).append("（目标 ").append(pkg).append("） ===\n");

        if (TextUtils.isEmpty(pkg) || pkg.contains("example")) {
            log.append("目标包名未配置，请在界面中填写 APP_PKG\n");
            return finish(context, log, true);
        }

        if (!suAvailable()) {
            log.append("未获得 root（su 不可用），无法执行\n");
            return finish(context, log, true);
        }

        // 1. 包是否安装
        Result listed = su("pm list packages " + pkg);
        if (!listed.out.contains("package:" + pkg)) {
            log.append("未找到已安装的包: ").append(pkg).append('\n');
            return finish(context, log, true);
        }

        // 2. 强制停止
        su("am force-stop " + pkg);
        log.append("已强制停止 ").append(pkg).append('\n');

        // 3. 锁屏处理
        boolean locked = isLocked();
        log.append("锁屏状态: ").append(locked).append('\n');
        if (locked) {
            su("input keyevent 224");
            sleep(800);
            log.append("已唤醒屏幕\n");

            // 3.1 交给 SystemUI 内的钩子直接收起 Keyguard
            try {
                Intent intent = new Intent(ACTION_DISMISS);
                intent.setPackage(SYSTEMUI_PKG);
                context.sendBroadcast(intent);
                log.append("已发送收起锁屏广播\n");
            } catch (Throwable t) {
                log.append("发送广播失败: ").append(t).append('\n');
            }
            sleep(1500);
            locked = isLocked();
            log.append("广播后锁屏状态: ").append(locked).append('\n');

            // 3.2 系统命令兜底
            if (locked) {
                su("wm dismiss-keyguard");
                sleep(1200);
                locked = isLocked();
                log.append("wm dismiss-keyguard 后锁屏状态: ").append(locked).append('\n');
            }

            // 3.3 上滑 + 密码兜底
            if (locked) {
                int[] size = screenSize();
                int startX = size[0] / 2;
                int startY = size[1] * SLIDE_START_PERCENT / 100;
                int endY = size[1] * SLIDE_END_PERCENT / 100;
                log.append("屏幕 ").append(size[0]).append('x').append(size[1])
                        .append("，上滑 (").append(startX).append(',').append(startY)
                        .append(") -> (").append(startX).append(',').append(endY).append(")\n");
                su("input swipe " + startX + " " + startY + " " + startX + " " + endY + " " + SLIDE_DURATION_MS);
                sleep(1000);
                String pwd = config.password();
                if (!TextUtils.isEmpty(pwd)) {
                    su("input text " + pwd);
                    sleep(800);
                    su("input keyevent 66");
                    sleep(1500);
                }
                locked = isLocked();
                log.append("密码注入后锁屏状态: ").append(locked).append('\n');
            }

            if (locked) {
                log.append("解锁失败，未启动 App（可检查 LSPosed 模块是否已启用并勾选 SystemUI 作用域）\n");
                return finish(context, log, true);
            }
        }

        // 4. 启动 App
        Result monkey = su("monkey -p " + pkg + " -c android.intent.category.LAUNCHER 1");
        boolean ok = monkey.code == 0;
        if (ok) {
            log.append("已通过 monkey 启动\n");
        } else {
            String activity = resolveLauncherActivity(pkg);
            if (activity != null) {
                Result start = su("am start -n " + activity);
                ok = start.code == 0;
                log.append(ok ? ("已通过 am start 启动: " + activity + "\n")
                        : ("am start 失败: " + activity + "\n"));
            } else {
                log.append("无法解析启动 Activity，启动失败\n");
            }
        }

        return finish(context, log, !ok);
    }

    private static String finish(Context context, StringBuilder log, boolean failed) {
        String text = log.toString();
        LogStore.append(context, text);
        if (failed) {
            notifyFailure(context, text);
        }
        return text;
    }

    private static boolean suAvailable() {
        Result r = su("id");
        return r.code == 0 && r.out.contains("uid=0");
    }

    private static Result su(String command) {
        Result r = new Result();
        Process process = null;
        try {
            process = new ProcessBuilder("su", "-c", command).redirectErrorStream(true).start();
            StringBuilder sb = new StringBuilder();
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), "UTF-8"));
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append('\n');
            }
            r.code = process.waitFor();
            r.out = sb.toString();
        } catch (Exception e) {
            r.code = -1;
            r.out = "执行失败: " + e;
        } finally {
            if (process != null) {
                process.destroy();
            }
        }
        return r;
    }

    private static boolean isLocked() {
        Result r = su("dumpsys window policy | grep -E 'isKeyguardShowing|mKeyguardShowing|keyguardShowing|mShowingLockscreen' | head -n 1");
        return r.out.contains("=true");
    }

    private static int[] screenSize() {
        Result r = su("wm size");
        int width = 1080;
        int height = 2400;
        String[] lines = r.out.split("\n");
        for (int i = lines.length - 1; i >= 0; i--) {
            String line = lines[i].trim();
            int colon = line.indexOf(':');
            String size = colon >= 0 ? line.substring(colon + 1).trim() : line;
            int x = size.indexOf('x');
            if (x > 0) {
                try {
                    width = Integer.parseInt(size.substring(0, x).trim());
                    height = Integer.parseInt(size.substring(x + 1).trim());
                    break;
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return new int[]{width, height};
    }

    private static String resolveLauncherActivity(String pkg) {
        Result r = su("cmd package resolve-activity --brief -c android.intent.category.LAUNCHER " + pkg);
        String[] lines = r.out.split("\n");
        for (int i = lines.length - 1; i >= 0; i--) {
            String line = lines[i].trim();
            if (line.contains("/")) {
                return line;
            }
        }
        return null;
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void notifyFailure(Context context, String text) {
        try {
            NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm == null) {
                return;
            }
            NotificationChannel channel = new NotificationChannel(
                    "scheduler", "定时启动 App", NotificationManager.IMPORTANCE_DEFAULT);
            nm.createNotificationChannel(channel);
            String last = text.split("\n")[text.split("\n").length - 1];
            Notification.Builder builder = new Notification.Builder(context, "scheduler")
                    .setSmallIcon(android.R.drawable.ic_dialog_alert)
                    .setContentTitle("定时启动 App 执行失败")
                    .setContentText(last)
                    .setAutoCancel(true);
            nm.notify(2001, builder.build());
        } catch (Throwable ignored) {
            // 通知失败不影响主流程
        }
    }
}
