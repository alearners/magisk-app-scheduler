package com.appscheduler.lsposed;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;

import java.lang.reflect.Method;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * LSPosed 入口：只在 SystemUI 进程内工作。
 * <p>
 * 1) 在 Application.onCreate 之后注册一个动态广播接收器；
 * 2) 捕获 KeyguardViewMediator 实例；
 * 3) 收到收起锁屏广播时，按候选方法列表反射调用，把 Keyguard 收起。
 */
public class XposedEntry implements IXposedHookLoadPackage {

    private static final String TAG = "[AppScheduler] ";
    private static final String KEYGUARD_MEDIATOR =
            "com.android.systemui.keyguard.KeyguardViewMediator";

    private static volatile Object sMediator;

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (!TaskRunner.SYSTEMUI_PKG.equals(lpparam.packageName)) {
            return;
        }
        log("模块已注入 " + lpparam.packageName);

        hookApplication(lpparam);
        hookMediator(lpparam);
    }

    private void hookApplication(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            XposedHelpers.findAndHookMethod("android.app.Application", lpparam.classLoader, "onCreate",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            try {
                                Context context = (Context) param.thisObject;
                                registerReceiver(context);
                            } catch (Throwable t) {
                                log("注册广播失败: " + t);
                            }
                        }
                    });
            log("已挂钩 Application.onCreate");
        } catch (Throwable t) {
            log("挂钩 Application.onCreate 失败: " + t);
        }
    }

    private void hookMediator(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            final Class<?> cls = XposedHelpers.findClass(KEYGUARD_MEDIATOR, lpparam.classLoader);

            XposedBridge.hookAllConstructors(cls, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    sMediator = param.thisObject;
                    log("已捕获 KeyguardViewMediator 实例");
                }
            });

            // 兜底：构造挂钩失效时，从任意一次 handleHideKeyguard 调用中捕获实例
            XposedBridge.hookAllMethods(cls, "handleHideKeyguard", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    if (sMediator == null) {
                        sMediator = param.thisObject;
                        log("已从 handleHideKeyguard 捕获实例");
                    }
                }
            });

            log("已挂钩 " + KEYGUARD_MEDIATOR);
        } catch (Throwable t) {
            log("挂钩 KeyguardViewMediator 失败: " + t);
        }
    }

    private void registerReceiver(Context context) {
        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context ctx, Intent intent) {
                log("收到收起锁屏广播");
                dismissKeyguard();
            }
        };

        IntentFilter filter = new IntentFilter(TaskRunner.ACTION_DISMISS);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED);
        } else {
            context.registerReceiver(receiver, filter);
        }
        log("广播接收器已注册: " + context.getPackageName());
    }

    private void dismissKeyguard() {
        Object mediator = sMediator;
        if (mediator == null) {
            log("KeyguardViewMediator 尚未捕获，无法收起锁屏");
            return;
        }
        if (tryCall(mediator, "dismissKeyguard", null, null)) {
            return;
        }
        if (tryCall(mediator, "handleHideKeyguard", new Class<?>[]{boolean.class},
                new Object[]{Boolean.TRUE})) {
            return;
        }
        if (tryCall(mediator, "handleHideKeyguard", new Class<?>[]{boolean.class, boolean.class},
                new Object[]{Boolean.TRUE, Boolean.TRUE})) {
            return;
        }
        if (tryCall(mediator, "setShowingLocked", new Class<?>[]{boolean.class, boolean.class},
                new Object[]{Boolean.FALSE, Boolean.TRUE})) {
            return;
        }
        if (tryCall(mediator, "hideKeyguard", null, null)) {
            return;
        }
        log("所有候选方法均不可用，需要按当前 ROM 的 KeyguardViewMediator 调整");
    }

    private boolean tryCall(Object target, String name, Class<?>[] types, Object[] args) {
        try {
            Method method = types == null
                    ? target.getClass().getDeclaredMethod(name)
                    : target.getClass().getDeclaredMethod(name, types);
            method.setAccessible(true);
            method.invoke(target, args);
            log("收起锁屏成功: " + name);
            return true;
        } catch (Throwable t) {
            log("尝试 " + name + " 失败: " + t);
            return false;
        }
    }

    private static void log(String message) {
        try {
            XposedBridge.log(TAG + message);
        } catch (Throwable ignored) {
            // 日志失败不影响流程
        }
    }
}
