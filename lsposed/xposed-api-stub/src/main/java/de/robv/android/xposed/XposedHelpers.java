package de.robv.android.xposed;

/**
 * 编译期桩：对应 Xposed API v82 中本模块实际用到的 XposedHelpers 方法。
 */
public final class XposedHelpers {

    private XposedHelpers() {
    }

    public static Class<?> findClass(String className, ClassLoader classLoader) {
        throw new UnsupportedOperationException("stub");
    }

    public static XC_MethodHook.Unhook findAndHookMethod(String className, ClassLoader classLoader,
                                                        String methodName, Object... parameterTypesAndCallback) {
        throw new UnsupportedOperationException("stub");
    }

    public static XC_MethodHook.Unhook findAndHookMethod(Class<?> clazz, String methodName,
                                                        Object... parameterTypesAndCallback) {
        throw new UnsupportedOperationException("stub");
    }

    public static Object callMethod(Object obj, String methodName, Object... args) {
        throw new UnsupportedOperationException("stub");
    }

    public static Object getObjectField(Object obj, String fieldName) {
        throw new UnsupportedOperationException("stub");
    }
}
