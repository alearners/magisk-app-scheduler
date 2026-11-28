# R8 保持规则：LSPosed 按类名字符串加载入口，混淆后必须保持原名

# assets/xposed_init 里写的是 com.appscheduler.lsposed.XposedEntry，不能改名
-keep class com.appscheduler.lsposed.XposedEntry { *; }

# 编译期接口，运行期由 LSPosed 注入，不做裁剪
-keep class de.robv.android.xposed.** { *; }
-dontwarn de.robv.android.xposed.**

# 反射与钩子回调依赖泛型签名、注解与内部类信息
-keepattributes Signature,*Annotation*,InnerClasses,EnclosingMethod
# 保留行号，便于排查崩溃
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
