# AppTimer（LSPosed 版）0.0.004

本版本不改变任何功能行为，只调整打包方式：安装包统一启用 R8 代码压缩、混淆与资源裁剪，体积由约 9.85 MB 降至约 2.79 MB。既有计划、主题、日志、权限与解锁逻辑均不受影响。

## 本版本变更

- **代码体积优化**：release 构建开启 `minifyEnabled true` 与 `shrinkResources true`，对外只产出优化后的 release 包，不再交付 debug 包。
- **保持规则**：`assets/xposed_init` 通过类名字符串加载入口，`com.appscheduler.lsposed.XposedEntry` 保持原名与全部成员；`de.robv.android.xposed.**` 编译期接口不参与裁剪；保留泛型签名、注解、内部类与行号信息，便于排查崩溃。
- **体积对比**：同一份代码，未优化 release 为 9,853,868 字节；启用 R8 与资源裁剪后为 2,790,142 字节，约缩减 71.7%。
- **版本号**：`versionCode 4` / `versionName 0.0.004`。

## 验证情况

- R8 构建 `:app:assembleRelease` 成功；单元测试与 Paparazzi 截图测试共 35 项通过。
- 结构核验：`classes.dex` 中保留 `com.appscheduler.lsposed.XposedEntry` 及其 `handleLoadPackage` 等入口方法；`assets/xposed_init`、`resources.arsc` 与四大组件均在包内，未被裁剪。
- 本版构建期间 Xiaomi 15 未连接本机，尚未做真机启动与 LSPosed 生效验证；安装前请先确认模块能在「概览」页被识别为生效。

## 安装步骤

1. 设备已取得 root，并已安装 LSPosed（Zygisk 或 Riru 均可）。请将本模块作用域设为「系统界面」，随后重启系统界面。
2. 安装 `apptimer-lsposed-v0.0.004.apk`。同签名版本可直接覆盖升级；签名不同的旧包须先卸载。
3. 打开应用，进入「概览」确认模块状态与钩子版本正常。

## 使用前须知

- 本版本只改变打包方式，界面与功能与 0.0.003 一致。
- 混淆后崩溃日志中的类名可能为短名；已保留行号与源文件名，便于对照源码。
- **使用本软件所产生的一切后果由使用者自行承担，作者不提供任何形式的担保。**
