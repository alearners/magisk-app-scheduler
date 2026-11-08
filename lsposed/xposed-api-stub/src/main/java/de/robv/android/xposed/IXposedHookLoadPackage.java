package de.robv.android.xposed;

import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * 编译期桩（compileOnly）：对应 Xposed API v82 的 IXposedHookLoadPackage。
 * 运行期由 LSPosed 提供的同名类替换，这里不会被真正执行。
 */
public interface IXposedHookLoadPackage {
    void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable;
}
