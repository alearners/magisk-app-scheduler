package com.appscheduler.lsposed;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.text.TextUtils;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * 核心执行流程（与 Magisk 版 run_app.sh 等价，全部经 su 执行）：
 * 校验包 → 判断锁屏 → 唤醒 → 广播让 LSPosed 钩子收起 Keyguard
 * → wm dismiss-keyguard → 上滑 + 密码 → 校验 → 逐个启动计划里的 App。
 * <p>
 * 这里只负责「能同步做完的部分」：解锁最多尝试三种自动方式，一旦失败立刻返回，
 * 不会在广播接收器里长时间等待——等待用户手动解锁交给 RunCoordinator 的全局解锁事件监听。
 * 每条 su 命令都有超时，避免 Magisk 授权弹窗无人响应时把线程永久挂住。
 */
public class TaskRunner {

    /** 发给 SystemUI 内 LSPosed 钩子的收起锁屏广播 */
    public static final String ACTION_DISMISS = "com.appscheduler.action.DISMISS_KEYGUARD";
    /** 该广播要求发送方持有同签名权限，防止第三方 App 伪造解锁请求 */
    public static final String DISMISS_PERMISSION = "com.appscheduler.permission.DISMISS_KEYGUARD";
    public static final String SYSTEMUI_PKG = "com.android.systemui";

    /** 单条 su 命令的超时秒数 */
    private static final int SU_TIMEOUT_SEC = 20;

    private static final String CHANNEL_ID = "scheduler";
    /** 通知投递专用线程：任何一次 binder 卡顿都不允许阻塞执行链 */
    private static final java.util.concurrent.ExecutorService NOTIFY_POOL =
            java.util.concurrent.Executors.newSingleThreadExecutor();
    private static volatile boolean channelReady = false;

    private static final int SLIDE_START_PERCENT = 80;
    private static final int SLIDE_END_PERCENT = 25;
    private static final int SLIDE_DURATION_MS = 300;

    public static class Result {
        public int code = -1;
        public String out = "";
        public boolean timeout = false;
    }

    /**
     * 校验包是否已安装。
     *
     * @return 可启动的包列表（未安装的会被写进日志并跳过）
     */
    public static List<String> validate(List<String> packages, StringBuilder log) {
        List<String> runnable = new ArrayList<>();
        if (packages == null) {
            return runnable;
        }
        for (String pkg : packages) {
            if (TextUtils.isEmpty(pkg)) {
                continue;
            }
            Result listed = su("pm list packages " + pkg);
            if (listed.timeout) {
                log.append("校验 ").append(pkg).append(" 超时（su 无响应），跳过\n");
                continue;
            }
            if (!hasPackage(listed.out, pkg)) {
                log.append("未找到已安装的包: ").append(pkg).append('\n');
                continue;
            }
            runnable.add(pkg);
        }
        return runnable;
    }

    /**
     * 逐个启动应用，解锁已在此之前完成。
     *
     * @param forceColdStart true 先强制停止再启动（冷启动）；false 直接启动，已运行的进程会被切回前台
     */
    public static boolean launch(List<String> runnable, boolean forceColdStart, StringBuilder log) {
        boolean allOk = true;
        for (String pkg : runnable) {
            boolean ok;
            if (forceColdStart) {
                su("am force-stop " + pkg);
                log.append("已强制停止 ").append(pkg).append("，准备冷启动\n");
                ok = launchByMonkey(pkg, log);
            } else {
                log.append("不强制停止 ").append(pkg).append("，直接启动\n");
                ok = launchDirect(pkg, log);
            }
            if (!ok) {
                allOk = false;
            }
            sleep(600);
        }
        return allOk;
    }

    /** 冷启动：monkey 拉起 Launcher Activity，失败再按 resolved 组件启动 */
    private static boolean launchByMonkey(String pkg, StringBuilder log) {
        Result monkey = su("monkey -p " + pkg + " -c android.intent.category.LAUNCHER 1");
        if (monkey.code == 0) {
            log.append("已通过 monkey 启动 ").append(pkg).append('\n');
            return true;
        }
        String resolved = resolveLauncherActivity(pkg);
        if (resolved != null) {
            Result start = su("am start -n " + resolved);
            boolean ok = start.code == 0;
            log.append(ok ? ("已通过 am start 启动 " + resolved + "\n")
                    : ("am start 失败: " + resolved + "\n"));
            return ok;
        }
        log.append("无法解析启动 Activity: ").append(pkg).append('\n');
        return false;
    }

    /**
     * 直接启动：优先 am start 目标 Activity——应用还在后台时这是「切回前台」，
     * 进程和界面状态都保留；解析不到组件时退回 monkey。
     */
    private static boolean launchDirect(String pkg, StringBuilder log) {
        String resolved = resolveLauncherActivity(pkg);
        if (resolved != null) {
            Result start = su("am start -n " + resolved);
            boolean ok = start.code == 0;
            log.append(ok ? ("已切到前台或新开 " + resolved + "\n")
                    : ("am start 失败: " + resolved + "\n"));
            if (ok) {
                return true;
            }
        }
        return launchByMonkey(pkg, log);
    }

    /**
     * 自动解锁：钩子广播 → wm dismiss-keyguard → 上滑 + 密码。
     * 三种都失败时直接返回 false，不做等待。
     */
    public static boolean unlock(Context context, Config config, StringBuilder log) {
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
        log.append("自动解锁失败，keyguard 仍然显示\n");
        return false;
    }

    /** su 是否可用（只做一次轻量探测） */
    public static boolean suAvailable() {
        Result r = su("id");
        return r.code == 0 && r.out.contains("uid=0");
    }

    public static boolean isLocked() {
        // 不同 ROM 把锁屏状态打在不同的 dumpsys 段里：AOSP / HyperOS 只在 `dumpsys window` 里给
        // isKeyguardShowing，部分机型只在 `dumpsys window policy` 里给 mKeyguardShowing。
        // 实测 HyperOS 3（Android 15）的 `dumpsys window policy` 根本没有 isKeyguardShowing 这一行，
        // 旧实现拿到空输出后按「锁着」兜底，结果明明已经解锁也一律走「等手动解锁」分支，永远不直启。
        Boolean byWindow = readKeyguard("dumpsys window | grep -m1 isKeyguardShowing=");
        if (byWindow != null) {
            return byWindow;
        }
        Boolean byPolicy = readKeyguard("dumpsys window policy | grep -m1 -E 'isKeyguardShowing=|mKeyguardShowing=|mShowingLockscreen='");
        if (byPolicy != null) {
            return byPolicy;
        }
        // 两处都拿不到（su 失败 / 命令超时 / 段被裁掉）时按「锁着」处理更安全：
        // 宁可多走一次解锁流程，也不要盲启应用
        return true;
    }

    /** 读一处锁屏状态；拿不到结果时返回 null，交给下一种探测方式 */
    private static Boolean readKeyguard(String cmd) {
        Result r = su(cmd);
        if (r.timeout) {
            return null;
        }
        String out = r.out.trim();
        if (out.isEmpty()) {
            return null;
        }
        if (out.contains("=true")) {
            return Boolean.TRUE;
        }
        if (out.contains("=false")) {
            return Boolean.FALSE;
        }
        return null;
    }

    /**
     * 执行一条 su 命令。读流放在独立线程，主线程只看超时，因此命令卡住不会把调用方永久阻塞。
     */
    public static Result su(String command) {
        final Result r = new Result();
        Process process = null;
        final StringBuilder sb = new StringBuilder();
        Thread reader = null;
        try {
            process = new ProcessBuilder("su", "-c", command).redirectErrorStream(true).start();
            final Process p = process;
            reader = new Thread(new Runnable() {
                @Override
                public void run() {
                    try (BufferedReader br = new BufferedReader(
                            new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                        String line;
                        while ((line = br.readLine()) != null) {
                            sb.append(line).append('\n');
                        }
                    } catch (Throwable ignored) {
                        // 进程被杀时读流会抛异常，输出已拿到的部分即可
                    }
                }
            }, "su-reader");
            reader.setDaemon(true);
            reader.start();

            if (!process.waitFor(SU_TIMEOUT_SEC, TimeUnit.SECONDS)) {
                r.timeout = true;
                r.code = -2;
                r.out = sb.toString();
                return r;
            }
            reader.join(1500);
            r.code = process.exitValue();
            r.out = sb.toString();
        } catch (Exception e) {
            r.code = -1;
            r.out = "执行失败: " + e;
        } finally {
            if (process != null && reader != null && reader.isAlive()) {
                try {
                    process.destroyForcibly();
                } catch (Throwable ignored) {
                }
            }
        }
        return r;
    }

    private static boolean hasPackage(String output, String pkg) {
        if (output == null) {
            return false;
        }
        String[] lines = output.split("\n");
        for (String line : lines) {
            if (line.trim().equals("package:" + pkg)) {
                return true;
            }
        }
        return false;
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

    /**
     * 当前拿到焦点的窗口所属包名；读不到时返回空串。
     * 用于判断「自动锁屏」前用户是否已经切到别的界面，避免把用户正在用的 App 关掉。
     */
    public static String foregroundPackage() {
        Result r = su("dumpsys window | grep -m1 mCurrentFocus=");
        if (r.timeout || r.out.isEmpty()) {
            return "";
        }
        String line = r.out.trim();
        int slash = line.indexOf('/');
        if (slash < 0) {
            return "";
        }
        int start = line.lastIndexOf(' ', slash);
        if (start < 0) {
            return "";
        }
        return line.substring(start + 1, slash).trim();
    }

    /** 熄屏并立即上锁（SLEEP 键），只有 root 下才有效 */
    public static boolean lockScreen(StringBuilder log) {
        Result r = su("input keyevent 223");
        if (r.timeout || r.code != 0) {
            log.append("熄屏上锁失败: ").append(r.out.trim()).append('\n');
            return false;
        }
        return true;
    }

    public static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public static String stamp() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA).format(new Date());
    }

    public static void notifyManualUnlock(Context context, final int waitSec) {
        // 通知只是附带提示：createNotificationChannel / notify 都是会跟系统服务排队、可能长时间不返回的
        // binder 调用（真机上实测过一次卡住约 3 分钟），放在执行线程里会把整条任务链一起拖死。
        // 因此一律丢到独立线程做尽力而为的投递，主流程立刻继续。
        final Context app = context.getApplicationContext();
        NOTIFY_POOL.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    NotificationManager nm = (NotificationManager) app.getSystemService(Context.NOTIFICATION_SERVICE);
                    if (nm == null) {
                        return;
                    }
                    ensureChannel(nm);
                    Notification.Builder builder = new Notification.Builder(app, CHANNEL_ID)
                            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
                            .setContentTitle("请手动解锁")
                            .setContentText("解锁后将在 " + waitSec + " 秒内自动启动 App")
                            .setAutoCancel(true);
                    nm.notify(2002, builder.build());
                } catch (Throwable ignored) {
                    // 通知失败不影响主流程
                }
            }
        });
    }

    public static void notifyFailure(Context context, final String text) {
        final Context app = context.getApplicationContext();
        NOTIFY_POOL.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    NotificationManager nm = (NotificationManager) app.getSystemService(Context.NOTIFICATION_SERVICE);
                    if (nm == null) {
                        return;
                    }
                    ensureChannel(nm);
                    String[] lines = text.split("\n");
                    String last = lines.length > 0 ? lines[lines.length - 1] : "执行失败";
                    Notification.Builder builder = new Notification.Builder(app, CHANNEL_ID)
                            .setSmallIcon(android.R.drawable.ic_dialog_alert)
                            .setContentTitle("AppTimer 执行失败")
                            .setContentText(last)
                            .setAutoCancel(true);
                    nm.notify(2001, builder.build());
                } catch (Throwable ignored) {
                    // 通知失败不影响主流程
                }
            }
        });
    }

    /** 通知渠道只建一次，避免每次失败都走一遍 binder */
    private static void ensureChannel(NotificationManager nm) {
        if (channelReady) {
            return;
        }
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID, "AppTimer", NotificationManager.IMPORTANCE_HIGH);
        nm.createNotificationChannel(channel);
        channelReady = true;
    }
}
