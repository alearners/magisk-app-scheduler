# 开发说明

本文档面向需要修改代码或从零复现的开发者。安装与使用请参见根目录 [`README.md`](../README.md) 与 [`lsposed/README.md`](../lsposed/README.md)。

当前版本：`0.0.001`（versionCode 1，钩子常量 1）。本版本为仓库重置后的首个版本，仓库中仅保留此次初始化提交。

## 工程结构

```
.
├── .github/workflows/
│   ├── build.yml                    # main 分支：生成 Magisk ZIP
│   └── build-lsposed-apk.yml        # lsposed 分支：编译 APK，推送 tag 即发布
├── module/                          # Magisk 模块（shell）
│   ├── config.sh                    # 全部参数集中于此
│   ├── service.sh                   # 开机启动 crond
│   ├── run_app.sh                   # 一次完整的「唤醒 → 解锁 → 启动 → 可选锁定」
│   └── ...
├── lsposed/                         # LSPosed 版 Android 工程
│   ├── README.md                    # 本版结构与验收清单
│   └── app/src/main/java/com/appscheduler/lsposed/
├── docs/
│   ├── dev-table/                   # 开发表：21 段可独立粘贴的提示词
│   ├── screenshots/                 # Paparazzi 渲染的界面图
│   └── DEVELOPMENT.md               # 本文件
├── scripts/package.sh               # 本地打包与核对
└── README.md
```

## Magisk 版执行流程

`service.sh` 开机读取 `config.sh`，将调度表达式写入 crontab，交由 BusyBox 的 `crond` 执行。到达触发时间后由 `run_app.sh` 执行：

1. 读取配置；
2. 检查日志大小，超出上限时按配置份数轮转；
3. 获取单实例锁（优先 `flock`，不可用时使用目录锁）；
4. 强制停止目标应用；
5. 查询锁屏状态；
6. 若处于锁定状态：点亮屏幕 → 先尝试 `wm dismiss-keyguard` 收起 → 失败则按屏幕比例上滑、输入密码并回车；
7. 再次查询锁屏状态：已解锁则继续；仍锁定且启用手动兜底时，等待手动解锁（最长按配置秒数）；超时或未启用兜底则记录失败、发出通知并结束；
8. 设备可用后，按 `APP_ACTIVITY`（若已填写）→ `monkey` → `am start` 的顺序启动应用。

需特别注意：澎湃等系统的锁屏可能无法识别 `input text` 注入的密码。如确实无法识别，可依靠手动兜底；长期不可用时，建议改用 LSPosed 版，由钩子收起锁屏。

## LSPosed 版界面结构

界面使用 Compose 编写，页面均挂在底部四个入口之下：

```
ui/
├── MainScreen.kt        # 页面路由、历史栈、返回键、动作分发
├── BottomBar.kt         # 底部：概览 / 日志 / 配置 / 更多
├── TaskListScreen.kt    # 配置页：定时开关 + 计划列表 + 全局设置
├── TaskEditScreen.kt    # 新建 / 修改计划（整屏子页）
├── AppPickerScreen.kt   # 应用选择（整屏子页）
├── LogScreen.kt         # 日志页：按日期归档、按级别筛选、清除单日 / 全部、日志设定
├── PermissionScreen.kt  # 概览页：模块状态 + 使用说明确认 + 核验项目
└── HelpScreen.kt        # 更多 / 使用说明 / 启动方式 / 配置说明 / 关于
```

概览页有意将「判断」与「显示」分离：`permissions/PermissionAudit.kt` 仅读取状态、不弹出授权框；`PermissionScreen` 仅负责渲染传入的 `PermRow`。因此截图测试可注入固定的「已就绪 / 尚未授予 / 待确认」三种状态，不受真机影响。

日志大小上限不在配置页，而由日志页「设定」按钮打开对话框调整；配置页不再提供导入导出入口与说明，相关功能位于「更多 → 配置备份」。

## 界面截图生成

无需模拟器或连接设备，Paparazzi 在电脑上将真实 Compose 界面渲染为 PNG：

```bash
cd lsposed
./gradlew :app:recordPaparazziDebug
# 产物：app/src/test/snapshots/images/，文件名 01-plan-list … 15-overview-unsigned
```

生成后将新的 PNG 覆盖至 `docs/screenshots/`，README 中的图片链接无需改动。

## 从零复现

参见 [`dev-table`](dev-table)：21 段提示词，每段可直接粘贴至新会话，附验收标准与已知问题。表格版为 `AppTimer-开发表.xlsx`，阅读版为 `开发表.md`。

修改数据（`*.jsonl`、`book.json`）后重建两份产物：

```bash
cd docs/dev-table
python -m pip install openpyxl        # 仅首次需要
python <path-to>/excel_build_workbook.py --spec ./book.json --output ./AppTimer-开发表.xlsx --overwrite
python ./gen_md.py
```

## 修改代码须遵守的约束

- 包名、applicationId、`Config.PREFS`、权限名与 action 前缀均不得修改：一旦修改，LSPosed 作用域、签名权限与覆盖升级链将中断。
- 版本变更须同步四处：`app/build.gradle`、`HelpScreen` 的 `AboutInfo`、README 版本表、Paparazzi 快照。
- 文案面向普通用户，统一使用「App / 应用」，避免堆砌技术括号；当前统一采用正式书面语。
- 不为应用锁定屏幕方向，也不修改系统的自动旋转设置。
- `monkey` 作为可选启动方式必须保留，它是部分应用最后的兼容手段。
- 修改后先本地编译与单元测试，再安装至真机；仅在自有设备测试，测试完成后清除数据并恢复原状。
