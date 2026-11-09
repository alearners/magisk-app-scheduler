# AppTimer（Magisk 模块内容）

本目录是打包进 ZIP 的模块内容，根目录结构与 Magisk 模块规范一致：

```
module/
├── META-INF/com/google/android/update-binary   # Magisk 官方安装器入口
├── META-INF/com/google/android/updater-script   # 占位文件
├── module.prop                                   # 模块信息
├── config.sh                                     # 全部可调参数（含自动锁屏、计时方式）
├── service.sh                                    # 开机读配置：cron 模式启 crond / internal 模式起计时循环
├── run_app.sh                                    # 核心执行脚本（解锁 → 启动 → 可选自动锁屏）
├── action.sh                                     # 管理器「执行」按钮：立即跑一次
├── customize.sh                                  # 安装时设置权限
└── uninstall.sh                                  # 卸载时停止 crond 与计时循环并清理
```

新增的两个可调项（`config.sh`）：

```sh
# 启动后无操作自动熄屏上锁
AUTO_LOCK=0            # 1=开启
AUTO_LOCK_DELAY=5      # 2–600 秒

# 计时方式：cron=crond 驱动；internal=模块自己的 sleep 循环（按 INTERVAL_MINUTES）
TIMER_MODE="cron"
INTERVAL_MINUTES=120
```

完整说明见仓库根目录 `README.md` 与 `docs/DEVELOPMENT.md`。

打包统一用 `sh scripts/package.sh`（或由 GitHub Actions 完成），产物名为 `apptimer-magisk-vX.zip`；不要手工压缩。
