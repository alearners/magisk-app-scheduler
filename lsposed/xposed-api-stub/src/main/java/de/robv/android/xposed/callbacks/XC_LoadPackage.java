package de.robv.android.xposed.callbacks;

import android.content.pm.ApplicationInfo;

/**
 * 编译期桩：对应 Xposed API v82 的 XC_LoadPackage。
 */
public abstract class XC_LoadPackage {

    public static class LoadPackageParam {
        public String packageName;
        public ClassLoader classLoader;
        public ApplicationInfo appInfo;
        public boolean isFirstApplication;
        public String processName;

        public LoadPackageParam() {
        }
    }
}
