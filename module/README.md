# 定时启动 App（Magisk 模块内容）

本目录是打包进 ZIP 的模块内容，根目录结构与 Magisk 模块规范一致：

```
module/
├── META-INF/com/google/android/update-binary   # Magisk 官方安装器入口
├── META-INF/com/google/android/updater-script   # 占位文件
├── module.prop                                   # 模块信息
├── config.sh                                     # 全部可调参数
├── service.sh                                    # 开机读取配置、生成 crontab、启动 crond
├── run_app.sh                                    # 核心执行脚本
├── action.sh                                     # 管理器「执行」按钮：立即跑一次
├── customize.sh                                  # 安装时设置权限
└── uninstall.sh                                  # 卸载时停止 crond 并清理
```

完整说明见仓库根目录 `README.md` 与 `docs/DEVELOPMENT.md`。

打包由 GitHub Actions 完成，不要手工压缩：推送 `v*` 标签即自动生成带版本的 ZIP 并发布 Release。
