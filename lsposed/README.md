# AppTimer · LSPosed 版

> **风险自负**：本模块会以 root 身份替你解锁屏幕、启动 App，并在你无操作时关闭屏幕。误配可能导致设备被锁、数据丢失或锁屏密码泄露。完整免责条款见仓库根目录 [`README.md`](../README.md) 顶部「使用前必读」与末尾「免责声明」。

本目录是 `lsposed` 分支独有的 Android 工程，产物为 `apptimer-lsposed-vX.apk`。

## 它和 Magisk 版的差别

| 维度 | Magisk 版（`main` 分支） | 本版（`lsposed` 分支） |
| --- | --- | --- |
| 形态 | 纯 shell 模块 | APK + Xposed 入口 |
| 调度 | crontab / 内部 sleep 循环 | 界面配置：时间点 / 星期 / 间隔，带次数与条件 |
| 触发载体 | crond 或后台循环 | 系统闹钟，或常驻前台服务计时 |
| 解锁 | `wm dismiss-keyguard` → 上滑 + 注入密码 | SystemUI 钩子收起 Keyguard → `wm` → 注入密码兜底 |
| 应用数量 | 一个固定包名 | 一个计划可启动多个 App |
| 配置 | 编辑 `config.sh` | 图形界面 |

功能对齐的几项（两边都有）：多 App 顺序启动、解锁校验与兜底、无操作自动锁屏（可配秒数、切走不误锁）、计时基准二选一、失败通知与日志。

## 包信息

| 项 | 值 |
| --- | --- |
| applicationId | `com.appscheduler.lsposed` |
| 显示名 | `AppTimer` |
| 版本 | `versionCode 20` / `versionName 2.0.0` |
| 最低支持 | Android 8.0（API 26） |
| 目标 SDK | 34 |
| 签名 | 仓库固定密钥（`CN=App Scheduler, O=alearners, C=CN`），证书 SHA-256 `10948BFB9…000B2E5` |

`applicationId`、Kotlin 包名、`Config.PREFS`、权限名与 action 前缀都保持 `appscheduler` 不动：改它们会让 LSPosed 作用域、`signature` 权限与既有安装的覆盖升级链路全部失效。改名只动显示名、产物文件名与文案。

## 代码结构

```
lsposed/app/src/main/
├── java/com/appscheduler/lsposed/
│   ├── MainActivity.kt / XposedEntry.java      # 入口与钩子
│   ├── AutoLock.kt                             # 无操作自动锁屏（arm → 原地等秒 → fire）
│   ├── RunService.kt                           # 前台服务：执行任务、等秒、守候解锁
│   ├── TimerService.kt                         # 「应用内部计时器」模式下的常驻计秒服务
│   ├── RunCoordinator.kt / TaskRunner.java     # 执行编排与 su 调用
│   ├── schedule/                               # 调度器与接收器
│   ├── store/                                  # 计划、挂起队列、日志
│   └── ui/                                     # Compose 界面
└── res/                                        # 图标、文案（app_name=AppTimer）
```

## 构建

```bash
cd lsposed
./gradlew :app:assembleRelease
# 产物：app/build/outputs/apk/release/app-release.apk
```

没有本地 Android SDK 时直接用仓库的 Actions：push 到 `lsposed` 分支即编译并上传 artifact；打 `lsposed-v*` 标签会额外发布 Release。

## 真机自查清单

装完后按顺序确认，每步只看一件事：

1. LSPosed 作用域勾选「系统界面」，重启一次；
2. App 内授予通知权限，并加入电池优化白名单（HyperOS 还要给「自启动 / 无限制」）；
3. 建一个 1 分钟间隔、只启动单个 App 的计划，等一轮，看日志里是否有「定时触发 → 解锁 → 启动」；
4. 打开「启动后无操作自动锁屏」，填 5 秒，等下一轮，确认日志出现「启动后 5 秒无操作，已自动熄屏上锁」；
5. 同一轮里触发后立刻切到别的界面，确认日志出现「前台已是…，判定为用户已接管，跳过」，且屏幕没有被关掉。

任何一步不符合预期，先看日志再改参数；不要同时改多个设置。
