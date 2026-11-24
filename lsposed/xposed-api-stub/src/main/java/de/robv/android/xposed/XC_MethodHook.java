package de.robv.android.xposed;

import java.lang.reflect.Member;

/**
 * 编译期桩：对应 Xposed API v82 的 XC_MethodHook。
 */
public abstract class XC_MethodHook {

    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
        throw new UnsupportedOperationException("stub");
    }

    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
        throw new UnsupportedOperationException("stub");
    }

    public static class MethodHookParam {
        public Member method;
        public Object thisObject;
        public Object[] args;

        private Object result;
        private Throwable throwable;

        public Object getResult() {
            throw new UnsupportedOperationException("stub");
        }

        public void setResult(Object result) {
            throw new UnsupportedOperationException("stub");
        }

        public Throwable getThrowable() {
            throw new UnsupportedOperationException("stub");
        }

        public boolean hasThrowable() {
            throw new UnsupportedOperationException("stub");
        }

        public void setThrowable(Throwable throwable) {
            throw new UnsupportedOperationException("stub");
        }

        public Object getResultOrThrowable() throws Throwable {
            throw new UnsupportedOperationException("stub");
        }
    }

    public final class Unhook {
        private Unhook() {
        }

        public void unhook() {
            throw new UnsupportedOperationException("stub");
        }
    }

    public final Unhook unhook() {
        throw new UnsupportedOperationException("stub");
    }
}
