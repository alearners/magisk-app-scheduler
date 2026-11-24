package com.appscheduler.lsposed;

import android.content.Context;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 按日期分包的日志：
 *
 * <pre>
 * files/apptimer-2026-11-23.log   当天的全部日志（运行 / 调试 / 错误三级混在一起，每行带级别）
 * files/apptimer-2026-11-22.log   前一天
 * </pre>
 *
 * 每行的格式：{@code [2026-11-23 14:32:24][运行] 正文}
 * 单天文件超过「设置里的大小上限」（1 KB – 1 MB）时，丢掉最旧的部分，只留最新的内容。
 * 只保留最近 [KEEP_DAYS] 天，更早的日期文件在写入时顺手删掉。
 *
 * 旧版的两个滚动文件（apptimer.log / apptimer.log.1）会在第一次写入时并进当天文件里。
 */
public class LogStore {

    /** 日志级别：运行（默认，业务过程）、调试（诊断细节）、错误（失败与异常） */
    public enum Level {
        RUN("运行"),
        DEBUG("调试"),
        ERROR("错误");

        public final String label;

        Level(String label) {
            this.label = label;
        }
    }

    private static final String PREFIX = "apptimer-";
    private static final String SUFFIX = ".log";
    private static final String DATE_PATTERN = "yyyy-MM-dd";
    private static final int KEEP_DAYS = 30;
    private static final String TRUNCATED_MARK = "...(更早的日志已按单日大小上限清理)\n";

    // ---------------- 写入 ----------------

    public static void append(Context context, String text) {
        append(context, Level.RUN, text);
    }

    public static void appendDebug(Context context, String text) {
        append(context, Level.DEBUG, text);
    }

    public static void appendError(Context context, String text) {
        append(context, Level.ERROR, text);
    }

    public static synchronized void append(Context context, Level level, String text) {
        if (text == null || text.isEmpty()) {
            return;
        }
        File dir = context.getFilesDir();
        String today = today();
        File target = fileForDate(dir, today);
        try {
            migrateLegacy(context, dir, target);
            pruneOld(dir, today);

            String stamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA)
                    .format(new Date());
            StringBuilder sb = new StringBuilder(text.length() + 32);
            // 逐行加前缀：这样按级别、按日期筛选时每一行都能单独判断
            for (String line : text.split("\n", -1)) {
                if (line.isEmpty()) {
                    continue;
                }
                sb.append('[').append(stamp).append("][").append(level.label).append("] ")
                        .append(line).append('\n');
            }
            FileOutputStream out = new FileOutputStream(target, true);
            out.write(sb.toString().getBytes("UTF-8"));
            out.close();

            enforceLimit(context, target);
        } catch (Exception ignored) {
            // 日志失败不影响主流程
        }
    }

    /** 单天文件超过上限时，保留最新的部分 */
    private static void enforceLimit(Context context, File f) {
        long limit = Config.get(context).logLimitBytes();
        if (f.length() <= limit) {
            return;
        }
        try {
            String all = readFile(f);
            // 目标大小留 3/4，避免每写一行就重写一次文件
            int keepChars = (int) Math.max(256L, limit * 3 / 4);
            String keep = all.length() > keepChars
                    ? all.substring(all.length() - keepChars)
                    : all;
            int cut = keep.indexOf('\n');
            if (cut >= 0 && cut < keep.length() - 1) {
                keep = keep.substring(cut + 1);
            }
            FileOutputStream out = new FileOutputStream(f, false);
            out.write((TRUNCATED_MARK + keep).getBytes("UTF-8"));
            out.close();
        } catch (Exception ignored) {
        }
    }

    /** 旧版滚动日志（apptimer.log / apptimer.log.1）并进当天文件后删除 */
    private static void migrateLegacy(Context context, File dir, File target) {
        for (String name : new String[]{"apptimer.log", "apptimer.log.1"}) {
            File legacy = new File(dir, name);
            if (!legacy.exists()) {
                continue;
            }
            String text = readFile(legacy);
            if (!text.isEmpty()) {
                try {
                    FileOutputStream out = new FileOutputStream(target, true);
                    out.write(text.getBytes("UTF-8"));
                    out.close();
                } catch (Exception ignored) {
                }
            }
            legacy.delete();
        }
    }

    /** 只保留最近 KEEP_DAYS 天的文件 */
    private static void pruneOld(File dir, String today) {
        List<String> dates = datesIn(dir);
        if (dates.size() <= KEEP_DAYS) {
            return;
        }
        Collections.sort(dates);
        int drop = dates.size() - KEEP_DAYS;
        for (int i = 0; i < drop; i++) {
            fileForDate(dir, dates.get(i)).delete();
        }
    }

    // ---------------- 读取 ----------------

    /** 有日志的日期，新的在前 */
    public static List<String> dates(Context context) {
        List<String> dates = datesIn(context.getFilesDir());
        Collections.sort(dates, Collections.reverseOrder());
        return dates;
    }

    /** 读某一天；level 为 null 表示全部级别 */
    public static String readDate(Context context, String date, Level level) {
        File f = fileForDate(context.getFilesDir(), date);
        if (!f.exists()) {
            return "";
        }
        if (level == null) {
            return readFile(f);
        }
        String tag = "[" + level.label + "]";
        StringBuilder sb = new StringBuilder();
        for (String line : readFile(f).split("\n")) {
            if (line.contains(tag)) {
                sb.append(line).append('\n');
            }
        }
        return sb.toString();
    }

    /** 全部日期的日志，按日期从旧到新拼接 */
    public static String readAll(Context context, Level level) {
        List<String> dates = dates(context);
        Collections.sort(dates);
        StringBuilder sb = new StringBuilder();
        for (String date : dates) {
            String text = readDate(context, date, level);
            if (text.isEmpty()) {
                continue;
            }
            sb.append("—— ").append(date).append(" ——\n").append(text);
        }
        return sb.toString();
    }

    /** 兼容旧调用：等同于「今天的全部级别」 */
    public static String readCurrent(Context context) {
        return readDate(context, today(), null);
    }

    /** 兼容旧调用：等同于「今天的全部级别」 */
    public static String read(Context context) {
        return readCurrent(context);
    }

    // ---------------- 清除 ----------------

    public static void clearDate(Context context, String date) {
        fileForDate(context.getFilesDir(), date).delete();
    }

    /** 兼容旧调用：清今天 */
    public static void clearCurrent(Context context) {
        clearDate(context, today());
    }

    /** 兼容旧调用：清今天 */
    public static void clear(Context context) {
        clearCurrent(context);
    }

    public static void clearAll(Context context) {
        for (String date : dates(context)) {
            clearDate(context, date);
        }
        // 顺手清掉可能残留的旧版文件
        new File(context.getFilesDir(), "apptimer.log").delete();
        new File(context.getFilesDir(), "apptimer.log.1").delete();
    }

    // ---------------- 工具 ----------------

    public static String today() {
        return new SimpleDateFormat(DATE_PATTERN, Locale.CHINA).format(new Date());
    }

    private static File fileForDate(File dir, String date) {
        return new File(dir, PREFIX + date + SUFFIX);
    }

    private static List<String> datesIn(File dir) {
        List<String> dates = new ArrayList<>();
        String[] names = dir.list();
        if (names == null) {
            return dates;
        }
        for (String name : names) {
            if (name.startsWith(PREFIX) && name.endsWith(SUFFIX) && name.length() > PREFIX.length()) {
                String date = name.substring(PREFIX.length(), name.length() - SUFFIX.length());
                if (date.length() == 10) {
                    dates.add(date);
                }
            }
        }
        return dates;
    }

    private static String readFile(File f) {
        if (!f.exists()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        BufferedReader br = null;
        try {
            br = new BufferedReader(new InputStreamReader(new FileInputStream(f), "UTF-8"));
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line).append('\n');
            }
        } catch (Exception e) {
            sb.append("读取日志失败: ").append(e.getMessage());
        } finally {
            if (br != null) {
                try {
                    br.close();
                } catch (Exception ignored) {
                }
            }
        }
        return sb.toString();
    }
}
