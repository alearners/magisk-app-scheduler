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
 * 极简日志：单文件，超过 512 KB 时截断为最近一半。
 */
public class LogStore {

    private static final String FILE_NAME = "scheduler.log";
    private static final long MAX_SIZE = 512 * 1024L;

    private static File file(Context context) {
        return new File(context.getFilesDir(), FILE_NAME);
    }

    public static synchronized void append(Context context, String text) {
        File f = file(context);
        try {
            if (f.length() > MAX_SIZE) {
                String all = read(context);
                String keep = all.length() > 200000 ? all.substring(all.length() - 200000) : all;
                FileOutputStream trunc = new FileOutputStream(f, false);
                trunc.write(("...(较早日志已截断)\n" + keep).getBytes("UTF-8"));
                trunc.close();
            }
            String ts = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA).format(new Date());
            FileOutputStream out = new FileOutputStream(f, true);
            out.write(("[" + ts + "] " + text + (text.endsWith("\n") ? "" : "\n")).getBytes("UTF-8"));
            out.close();
        } catch (Exception ignored) {
            // 日志失败不影响主流程
        }
    }

    public static String read(Context context) {
        File f = file(context);
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

    public static void clear(Context context) {
        file(context).delete();
    }
}
