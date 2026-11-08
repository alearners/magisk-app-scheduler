package com.appscheduler.lsposed;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * 开机后重新排定定时任务。
 */
public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        Context appContext = context.getApplicationContext();
        if (Config.get(appContext).enabled()) {
            Scheduler.scheduleNext(appContext);
            LogStore.append(appContext, "开机完成，已重新排定定时任务：" + Scheduler.describeNext(appContext));
        }
    }
}
