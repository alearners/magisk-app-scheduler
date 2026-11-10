# AppTimer · Magisk 模块内容

> **风险自负**：本模块以 root 身份解锁屏幕、启动 App，并可自动熄屏上锁。误配可能导致设备被锁、数据丢失或锁屏密码泄露。完整免责条款见仓库根目录 [`README.md`](../README.md)。

本目录是打包进 ZIP 的模块内容，根目录结构与 Magisk 模块规范一致：

```
module/
├── META-INF/com/google/android/update-binary   # Magisk 官方安装器入口
├── META-INF/com/google/android/updater-script   # 占位文件
├── module.prop                                   # 模块信息（id=app_scheduler / name=AppTimer）
├── config.sh                                     # 全部可调参数
├── service.sh                                    # 开机读配置：cron 模式启 crond / internal 模式起计时循环
├── run_app.sh                                    # 核心执行脚本（解锁 → 启动 → 可选自动锁屏）
├── action.sh                                     # 管理器「执行」按钮：立即跑一次
├── customize.sh                                  # 安装时设置权限
└── uninstall.sh                                  # 卸载时停止 crond 与计时循环并清理
```

安装后实际路径为 `/data/adb/modules/app_scheduler/`（目录名沿用 `id=app_scheduler`，改名会影响既有升级与卸载）。

## 与自动锁屏、计时方式相关的参数

```sh
# 启动后无操作自动熄屏上锁
AUTO_LOCK=0            # 1=开启
AUTO_LOCK_DELAY=5      # 2–600 秒

# 计时方式：cron=crond 驱动；internal=模块自己的 sleep 循环（按 INTERVAL_MINUTES）
TIMER_MODE="cron"
INTERVAL_MINUTES=120
```

`run_app.sh` 在启动成功后起一个子进程等 `AUTO_LOCK_DELAY` 秒，到点后依次确认「屏幕没被自己锁掉」与「前台仍是目标 App」，两者同时成立才执行 `input keyevent 223` 熄屏；你已切到别的界面则跳过。

完整参数表、排查方法与已知限制见仓库根目录 `README.md`。

打包统一用 `sh scripts/package.sh`（或由 GitHub Actions 完成），产物名为 `apptimer-magisk-vX.zip`；不要手工压缩。
