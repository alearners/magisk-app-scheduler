package com.appscheduler.lsposed;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * 定时到点：后台线程执行一次完整流程，结束后排下一次。
 */
public class AlarmReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        final Context appContext = context.getApplicationContext();
        final PendingResult pendingResult = goAsync();
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    TaskRunner.run(appContext);
                } catch (Throwable t) {
                    LogStore.append(appContext, "执行异常: " + t);
                } finally {
                    Scheduler.scheduleNext(appContext);
                    pendingResult.finish();
                }
            }
        }, "app-scheduler-task").start();
    }
}
