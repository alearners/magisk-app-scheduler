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
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 核心执行流程（与 Magisk 版 run_app.sh 等价，全部经 su 执行）：
 * 校验包 → 判断锁屏 → 唤醒 → 广播让 LSPosed 钩子收起 Keyguard
 * → wm dismiss-keyguard → 上滑 + 密码 → 校验 → 逐个启动计划里的 App。
 * 一个计划可以包含多个 App，解锁只做一次；自动解锁失败且开启兜底时，会等用户手动解锁。
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

    /**
     * 执行一个计划。
     *
     * @return 是否全部 App 都启动成功
     */
    public static boolean run(Context context, List<String> packages) {
        StringBuilder log = new StringBuilder();
        Config config = Config.get(context);
        String stamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA).format(new Date());

        if (packages == null || packages.isEmpty()) {
            log.append("=== 开始执行 ").append(stamp).append(" ===\n");
            log.append("计划里没有选择任何应用\n");
            return finish(context, log, true);
        }

        log.append("=== 开始执行 ").append(stamp).append("（目标 ")
                .append(TextUtils.join(", ", packages)).append("） ===\n");

        if (!suAvailable()) {
            log.append("未获得 root（su 不可用），无法执行\n");
            return finish(context, log, true);
        }

        // 1. 校验安装并强制停止
        List<String> runnable = new ArrayList<>();
        for (String pkg : packages) {
            if (TextUtils.isEmpty(pkg)) {
                continue;
            }
            Result listed = su("pm list packages " + pkg);
            if (!listed.out.contains("package:" + pkg)) {
                log.append("未找到已安装的包: ").append(pkg).append('\n');
                continue;
            }
            su("am force-stop " + pkg);
            log.append("已强制停止 ").append(pkg).append('\n');
            runnable.add(pkg);
        }
        if (runnable.isEmpty()) {
            log.append("没有任何可启动的包，放弃本次任务\n");
            return finish(context, log, true);
        }

        // 2. 锁屏处理（整个计划只做一次）
        if (isLocked()) {
            log.append("锁屏状态: true\n");
            if (!unlock(context, config, log)) {
                return finish(context, log, true);
            }
        } else {
            log.append("锁屏状态: false，直接启动\n");
        }

        // 3. 逐个启动：monkey → 解析 Activity 后 am start
        boolean allOk = true;
        for (String pkg : runnable) {
            boolean ok = false;
            Result monkey = su("monkey -p " + pkg + " -c android.intent.category.LAUNCHER 1");
            ok = monkey.code == 0;
            if (ok) {
                log.append("已通过 monkey 启动 ").append(pkg).append('\n');
            } else {
                String resolved = resolveLauncherActivity(pkg);
                if (resolved != null) {
                    Result start = su("am start -n " + resolved);
                    ok = start.code == 0;
                    log.append(ok ? ("已通过 am start 启动 " + resolved + "\n")
                            : ("am start 失败: " + resolved + "\n"));
                } else {
                    log.append("无法解析启动 Activity: ").append(pkg).append('\n');
                }
            }
            if (!ok) {
                allOk = false;
            }
            sleep(600);
        }

        return finish(context, log, !allOk);
    }

    /** @return 解锁是否成功 */
    private static boolean unlock(Context context, Config config, StringBuilder log) {
        su("input keyevent 224");
        sleep(800);
        log.append("已唤醒屏幕\n");

        // 1. 交给 SystemUI 内的钩子直接收起 Keyguard
        try {
            Intent intent = new Intent(ACTION_DISMISS);
            intent.setPackage(SYSTEMUI_PKG);
            context.sendBroadcast(intent);
            log.append("已发送收起锁屏广播\n");
        } catch (Throwable t) {
            log.append("发送广播失败: ").append(t).append('\n');
        }
        sleep(1500);
        if (!isLocked()) {
            log.append("钩子收起锁屏成功\n");
            return true;
        }

        // 2. 系统命令兜底
        su("wm dismiss-keyguard");
        sleep(1200);
        if (!isLocked()) {
            log.append("wm dismiss-keyguard 收起锁屏成功\n");
            return true;
        }

        // 3. 上滑 + 密码兜底
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
        if (!isLocked()) {
            log.append("密码注入后已解锁\n");
            return true;
        }

        // 4. 手动解锁兜底
        log.append("自动解锁失败，keyguard 仍然显示\n");
        if (!config.manualFallback()) {
            log.append("未开启手动解锁兜底，放弃本次任务\n");
            return false;
        }
        int waitSec = config.manualWait();
        log.append("已启用手动解锁兜底：最多等待 ").append(waitSec)
                .append(" 秒，用户解锁后立即启动\n");
        notifyManualUnlock(context, waitSec);

        long deadline = System.currentTimeMillis() + waitSec * 1000L;
        while (System.currentTimeMillis() < deadline) {
            sleep(3000);
            if (!isLocked()) {
                log.append("检测到用户已手动解锁，继续启动 App\n");
                sleep(1200);
                return true;
            }
        }
        log.append("等待手动解锁超时（").append(waitSec).append(" 秒），放弃本次任务\n");
        return false;
    }

    private static boolean finish(Context context, StringBuilder log, boolean failed) {
        String text = log.toString();
        LogStore.append(context, text);
        if (failed) {
            notifyFailure(context, text);
        }
        return !failed;
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

    private static void notifyManualUnlock(Context context, int waitSec) {
        try {
            NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm == null) {
                return;
            }
            NotificationChannel channel = new NotificationChannel(
                    "scheduler", "定时启动 App", NotificationManager.IMPORTANCE_HIGH);
            nm.createNotificationChannel(channel);
            Notification.Builder builder = new Notification.Builder(context, "scheduler")
                    .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
                    .setContentTitle("请手动解锁")
                    .setContentText("解锁后将在 " + waitSec + " 秒内自动启动 App")
                    .setAutoCancel(true);
            nm.notify(2002, builder.build());
        } catch (Throwable ignored) {
            // 通知失败不影响主流程
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
            String[] lines = text.split("\n");
            String last = lines.length > 0 ? lines[lines.length - 1] : "执行失败";
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
