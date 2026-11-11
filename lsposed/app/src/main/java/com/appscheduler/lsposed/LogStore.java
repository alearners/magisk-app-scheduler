package com.appscheduler.lsposed;

import android.content.Context;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 极简日志：两个文件滚动。
 *
 * <pre>
 * apptimer.log    当前日志（本次运行期间新写入的内容）
 * apptimer.log.1  上一次轮转下来的旧日志（只保留 1 份）
 * </pre>
 *
 * 当前文件超过 512 KB 时整体滚到 .1，然后重新从空文件写起。
 * 界面上的「当前日志」只看 .log，「所有日志」= .1 + .log 合并。
 */
public class LogStore {

    private static final String FILE_NAME = "apptimer.log";
    private static final String ROTATED_NAME = "apptimer.log.1";
    private static final long MAX_SIZE = 512 * 1024L;
    private static final String ALL_SEPARATOR = "...(以上为已轮转的旧日志，以下是当前日志)\n";

    private static File currentFile(Context context) {
        return new File(context.getFilesDir(), FILE_NAME);
    }

    private static File rotatedFile(Context context) {
        return new File(context.getFilesDir(), ROTATED_NAME);
    }

    public static synchronized void append(Context context, String text) {
        File f = currentFile(context);
        try {
            if (f.length() > MAX_SIZE) {
                rotate(context, f);
            }
            String ts = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA).format(new Date());
            FileOutputStream out = new FileOutputStream(f, true);
            out.write(("[" + ts + "] " + text + (text.endsWith("\n") ? "" : "\n")).getBytes("UTF-8"));
            out.close();
        } catch (Exception ignored) {
            // 日志失败不影响主流程
        }
    }

    /** 把当前日志整体移到 .1（旧 .1 直接丢弃），当前文件从空开始 */
    private static void rotate(Context context, File current) {
        File rotated = rotatedFile(context);
        if (rotated.exists() && !rotated.delete()) {
            // 删不掉就退化为截断，至少不无限增长
            truncateTail(current);
            return;
        }
        if (!current.renameTo(rotated)) {
            truncateTail(current);
        }
    }

    /** rename 失败时的兜底：保留最近 200 KB 文本 */
    private static void truncateTail(File f) {
        try {
            String all = readFile(f);
            String keep = all.length() > 200000 ? all.substring(all.length() - 200000) : "";
            FileOutputStream trunc = new FileOutputStream(f, false);
            trunc.write(("...(较早日志已截断)\n" + keep).getBytes("UTF-8"));
            trunc.close();
        } catch (Exception ignored) {
        }
    }

    /** 当前日志（apptimer.log） */
    public static String readCurrent(Context context) {
        return readFile(currentFile(context));
    }

    /** 已轮转的旧日志（apptimer.log.1），没有则为空串 */
    public static String readRotated(Context context) {
        return readFile(rotatedFile(context));
    }

    /** 所有日志：旧日志在前、当前日志在后 */
    public static String readAll(Context context) {
        String old = readRotated(context);
        String cur = readCurrent(context);
        if (old.isEmpty()) {
            return cur;
        }
        if (cur.isEmpty()) {
            return old;
        }
        return old + (old.endsWith("\n") ? "" : "\n") + ALL_SEPARATOR + cur;
    }

    /** 只清当前日志，已轮转的旧日志保留 */
    public static void clearCurrent(Context context) {
        File f = currentFile(context);
        if (f.exists()) {
            f.delete();
        }
    }

    /** 清所有日志：当前 + 已轮转 */
    public static void clearAll(Context context) {
        clearCurrent(context);
        File r = rotatedFile(context);
        if (r.exists()) {
            r.delete();
        }
    }

    /** 兼容旧调用：等同于“当前日志” */
    public static String read(Context context) {
        return readCurrent(context);
    }

    /** 兼容旧调用：等同于“只清当前日志” */
    public static void clear(Context context) {
        clearCurrent(context);
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
