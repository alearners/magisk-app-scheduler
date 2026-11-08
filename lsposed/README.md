# 定时启动 App · LSPosed 模块

本目录是 `lsposed` 分支独有的 Gradle 工程，产物为可安装的 Xposed 模块 APK。与 `main` 分支的 Magisk 版相比：

| 对比项 | Magisk 版（`main`） | LSPosed 版（`lsposed`） |
| --- | --- | --- |
| 形态 | Magisk 模块 ZIP（纯 shell） | APK（Xposed 模块） |
| 调度 | BusyBox `crond` + crontab | `AlarmManager`（`setExactAndAllowWhileIdle`） |
| 解锁 | `wm dismiss-keyguard` → 上滑 + `input text` | **SystemUI 内钩子收起 Keyguard** → `wm` → 上滑 + `input text` |
| 执行权限 | 模块脚本天然 root | App 内 `su -c` |
| 配置 | 编辑 `config.sh` | App 界面（包名 / 密码 / 时间点 / 开关） |

## 工作原理

1. **调度**：`Scheduler` 解析界面里配置的时间点（`HH:MM` 逗号分隔），取最近一个时间点用 `AlarmManager` 精确定时；`AlarmReceiver` 触发后执行一次任务并排下一次；`BootReceiver` 开机重排。
2. **执行**：`TaskRunner` 通过 `su -c` 依次执行 `pm list packages` 校验、`am force-stop`、锁屏判断、`input keyevent 224` 唤醒、广播收起锁屏、`wm dismiss-keyguard`、上滑 + `input text` + 回车、`am start` / `monkey` 启动。
3. **钩子**：`XposedEntry` 只作用于 `com.android.systemui`：
   - 挂钩 `Application.onCreate`，在其中注册 `com.appscheduler.action.DISMISS_KEYGUARD` 广播接收器；
   - 挂钩 `com.android.systemui.keyguard.KeyguardViewMediator` 的构造函数捕获实例；
   - 收到广播后按候选方法列表反射调用：`dismissKeyguard()` → `handleHideKeyguard(boolean)` → `handleHideKeyguard(boolean, boolean)` → `setShowingLocked(false, true)` → `hideKeyguard()`，逐个尝试并在 LSPosed 日志中记录结果。

## 构建

全部在 GitHub Actions 内完成（JDK 17 + Gradle 8.7 + Android SDK）：

```bash
# 触发一次分支构建
git push origin lsposed

# 或者打标签发布 Release
git tag lsposed-v1.4 && git push origin lsposed-v1.4
```

本地等价命令（需要本机 Android SDK）：

```bash
gradle -p lsposed :app:assembleRelease
```

## 安装

1. 下载 `app-scheduler-lsposed-v1.4.apk`；
2. 正常安装（授予 root 权限提示时允许）；
3. LSPosed 管理器 → 模块 → 启用「定时启动 App · LSPosed」→ 勾选作用域 **System UI**（`com.android.systemui`）；
4. 重启手机（或重启 SystemUI）；
5. 打开 App 填写包名、密码、时间点，勾选「启用定时任务」→ 保存并应用。

## 验证钩子是否生效

```bash
# LSPosed 日志
logcat -s LSPosed | grep AppScheduler
```

日志中应出现：

```
[AppScheduler] 模块已注入 com.android.systemui
[AppScheduler] 已挂钩 Application.onCreate
[AppScheduler] 已挂钩 com.android.systemui.keyguard.KeyguardViewMediator
[AppScheduler] 已捕获 KeyguardViewMediator 实例
```

若 `handleHideKeyguard` 等候选方法在 HyperOS 上不存在，日志会输出每个候选失败的异常，此时模块自动退回 `wm dismiss-keyguard` 与上滑密码路径，功能不会中断。

> 说明：`xposed-api-stub/` 只是编译期桩模块（`compileOnly`），提供 `de.robv.android.xposed` v82 的接口签名，不会打进 APK；运行期由 LSPosed 提供实现。
