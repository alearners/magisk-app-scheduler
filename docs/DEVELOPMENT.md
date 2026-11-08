# 定时启动 App —— 开发文档

文档版本：v1.4
目标设备：小米机型，HyperOS 2，Android 15，已 Root（Magisk / KernelSU）
核心功能：按 `config.sh` 中配置的 crontab 时间点强制停止并重新启动指定 App；锁屏时尝试自动解锁（默认 80% → 25% 上滑 + 纯数字密码，优先走 `wm dismiss-keyguard`）；开机自动恢复定时任务，也可手动触发；日志超过 1 MB 自动轮转，份数可配置；全部参数集中在 `config.sh`。
文档状态：自动解锁环节存在 HyperOS 2 兼容性风险，必须真机验证后再视为可用。若密码注入被拦截，改用 `lsposed` 分支的钩子方案。

---

## 1. 项目概述

模块通过 Magisk 的 `service.sh` 开机启动 BusyBox `crond`。`service.sh` 读取 `config.sh` 中的调度表达式，动态生成 crontab，按配置的时间点调用 `run_app.sh`。`run_app.sh` 执行流程：

1. 读取 `config.sh`；
2. 检查日志大小，超过上限按配置份数轮转；
3. 获取单实例锁（`flock`，不可用时退化为 `mkdir` 锁目录）；
4. `am force-stop` 强制停止目标 App；
5. 检测设备是否锁屏（`dumpsys window policy`）；
6. 若锁屏：唤醒屏幕 → `wm dismiss-keyguard` 尝试直接收起锁屏 → 未解锁则按分辨率比例上滑调出密码盘 → 输入密码 → 回车确认；
7. 再次检测锁屏状态，若仍未解锁则记录失败、发通知并退出；
8. 若已解锁或原本未锁屏：按 `APP_ACTIVITY`（若配置）→ `monkey` → `am start` 的顺序启动 App。

核心风险：HyperOS 2 的锁屏安全策略可能拒绝通过 `input text` 注入的密码。若系统拦截注入文本，自动解锁会失败，App 不会启动。该环节必须在真机上先行验证。

---

## 2. 仓库与模块结构

仓库（GitHub）：

```
.
├── .github/workflows/build.yml      # main：ZIP 打包 + Tag 发布
├── module/                          # ZIP 根目录
│   ├── META-INF/com/google/android/update-binary
│   ├── META-INF/com/google/android/updater-script
│   ├── module.prop
│   ├── config.sh
│   ├── service.sh
│   ├── run_app.sh
│   ├── action.sh
│   ├── customize.sh
│   └── uninstall.sh
├── scripts/package.sh               # 本地打包自查
├── docs/DEVELOPMENT.md              # 本文档
└── README.md
```

`lsposed` 分支额外包含 `lsposed/`（Gradle 工程）与 `.github/workflows/build-lsposed-apk.yml`。

打包要求：

- 所有 `.sh` 权限 755，`module.prop` 与 `updater-script` 为 644；
- 换行符必须为 Unix（LF），不可用 Windows 记事本直接保存；
- ZIP 内保持上述目录结构，`module.prop` 位于 ZIP 根。

以上三条由 CI 强制校验（`.github/workflows/build.yml`），打包不再依赖本地环境。

---

## 3. 文件职责

| 文件 | 职责 |
| --- | --- |
| `module.prop` | 模块 id / 名称 / 版本 / 描述 |
| `config.sh` | 唯一可调参数入口（App、密码、比例、等待、日志、通知、调度） |
| `service.sh` | 等待 `sys.boot_completed`，查找 BusyBox，按 `SCHEDULE_CRON_LINES` 生成 crontab，重启 crond 并校验进程 |
| `run_app.sh` | 核心流程：轮转 → 单实例锁 → force-stop → 锁屏判断 → 解锁 → 启动 → 收尾 |
| `action.sh` | 管理器「执行」按钮：调用 `run_app.sh` 并回显返回码 |
| `customize.sh` | 安装阶段设置权限并打印下一步提示 |
| `uninstall.sh` | 卸载时停止 crond、清理锁文件 |

`run_app.sh` 通过 `SCRIPT_DIR=$(cd "$(dirname "$0")" && pwd)` 定位配置，保证 crond 调用与手动执行都能找到 `config.sh`。

---

## 4. 关键实现说明

### 4.1 外部配置（所有参数唯一入口）

`run_app.sh` / `service.sh` 均通过 `. "$CONFIG_FILE"` 加载配置，并对每个变量给出默认值兜底，避免用户在 `config.sh` 中删掉某行导致脚本异常。升级模块时可只替换脚本，保留自己的 `config.sh`。

### 4.2 多时间点调度

`config.sh` 的 `SCHEDULE_CRON_LINES` 支持多行 crontab 表达式。`service.sh` 将内容写入临时文件后逐行读取，`tr -d '\r'` 去掉 Windows 残留的 CR，跳过空行与 `#` 注释，每行追加 `run_app.sh >> LOG 2>&1` 写入 `crontab_dir/root`，并在日志中回显生成的任务条数。

| 需求 | 配置 |
| --- | --- |
| 每 2 小时 | `0 */2 * * *` |
| 每天 8:30 与 20:30 | 两行 `30 8 * * *` / `30 20 * * *` |
| 工作日 9:00 与 18:00 | 两行 `0 9 * * 1-5` / `0 18 * * 1-5` |
| 每 30 分钟 | `*/30 * * * *` |

修改调度后生效方式：重启设备，或执行 `su -c sh /data/adb/modules/app_scheduler/service.sh`。只改密码 / 比例 / 等待时间不需要重跑 `service.sh`。

### 4.3 日志轮转

`LOG_MAX_ROTATIONS` 控制保留份数：

| 值 | 行为 | 占用上限 |
| --- | --- | --- |
| 0 | 超限直接删除，不保留 | ≈ `LOG_MAX_SIZE` |
| 1 | 保留 `.log.1`（默认） | ≈ 2× |
| 2 | 保留 `.log.1`、`.log.2` | ≈ 3× |
| N | 保留 `.log.1` … `.log.N` | ≈ (N+1)× |

轮转在每次执行开始时触发：删除最旧一份 → 依次把 `.i` 改名为 `.i+1` → 把 `.log` 改名为 `.log.1`；`LOG_MAX_ROTATIONS=0` 时直接删除。

### 4.4 单实例锁

优先 `flock -n`（fd 9），不可用时退化为 `mkdir` 锁目录，并通过 `trap` 在退出时 `rmdir`。锁冲突直接跳过本轮，避免上一轮未结束时叠加执行。

### 4.5 锁屏判断

通过 `dumpsys window policy` 中 `isKeyguardShowing` / `mKeyguardShowing` / `keyguardShowing` / `mShowingLockscreen` 的第一条匹配行判断。不同 ROM 字段不同，先在锁屏状态下手动执行确认：

```bash
dumpsys window policy | grep -i keyguard
```

判断函数 `is_locked` 接收**已捕获的字段串**而不是自行再查一次，避免"取状态"与"判断状态"两次查询结果不一致导致日志与实际分支分叉。

### 4.6 解锁流程（v1.4）

1. `input keyevent KEYCODE_WAKEUP`（失败回落 `224`）唤醒屏幕；
2. `wm dismiss-keyguard`：多数机型可直接收起锁屏，无需输入密码；
3. 若仍锁屏：`wm size` 取分辨率（多段输出取最后一段，兼容 Override），按 `SLIDE_START_PERCENT` / `SLIDE_END_PERCENT` 计算上滑坐标，日志记录实际坐标；
4. `input swipe` → `input text "$PASSWORD"` → `KEYCODE_ENTER`；
5. 二次检查 Keyguard，若仍锁屏则记失败、发通知、退出（不启动 App）。

日志示例：

```
屏幕尺寸: 1080x2400, 比例: 80%->25%, 上滑: (540,1920) -> (540,600)
```

### 4.7 自动解锁风险

HyperOS 2 可能拒绝 `input text` 注入的密码。真机验证步骤：

```bash
input keyevent 224
input swipe <START_X> <START_Y> <END_X> <END_Y> 300
input text 0219
input keyevent 66
```

若密码被拒绝且 `wm dismiss-keyguard` 也无法收起锁屏，则本模块的自动解锁方案不可用，请改用 `lsposed` 分支（在 SystemUI 内用钩子收起 Keyguard，密码注入仅作兜底）。

### 4.8 启动 App

按顺序尝试，任一成功即停止：

1. `APP_ACTIVITY`（`config.sh` 显式配置时）；
2. `monkey -p <pkg> -c android.intent.category.LAUNCHER 1`；
3. `cmd package resolve-activity --brief` 解析出 Activity 后 `am start -n`。

启动前先 `pm list packages | grep -F "package:$APP_PKG"` 校验包已安装，避免拼错包名时静默失败。

### 4.9 密码存储

密码以明文写在 `config.sh`。任何 Root 权限进程均可读取，请自行评估风险。

---

## 5. GitHub 打包流程

### main 分支（`build.yml`）

触发：push `main`、`v*` 标签、PR、`workflow_dispatch`。

步骤：

1. `actions/checkout@v4`；
2. 解析 `module.prop` 的 `id` / `version` / `versionCode`，生成 `ZIP_NAME="<id>-<version>.zip"` 写入 `GITHUB_OUTPUT` 与 `GITHUB_ENV`；
3. `sed` 统一 LF，`chmod` 修正 755 / 644；
4. 必需文件清单校验（`module.prop`、四个脚本、`customize.sh`、`update-binary`）；
5. `sh -n` 语法检查（Ubuntu 的 `sh` 为 dash，能捕获括号 / 引号错误）；
6. `cd module && zip -r -X ../$ZIP_NAME .`（保证 `module.prop` 位于 ZIP 根）；
7. `unzip -l` 打印内容，`sha256sum` 生成校验文件；
8. `actions/upload-artifact@v4` 上传 ZIP 与 sha256；
9. 标签触发时 `softprops/action-gh-release@v2` 创建 Release 并附产物。

发布新版本：

```bash
# 先更新 module.prop 的 version / versionCode
git tag v1.4
git push origin v1.4
```

### lsposed 分支（`build-lsposed-apk.yml`）

触发：push `lsposed`、`lsposed-v*` 标签、`workflow_dispatch`。

步骤：JDK 17 → Gradle（`gradle/actions/setup-gradle`）→ `gradle -p lsposed :app:assembleDebug` → 重命名产物为 `app-scheduler-lsposed-<version>.apk` → 上传 artifact → 标签时发布 Release。

### 本地自查

`sh scripts/package.sh` 复现 CI 的校验与打包逻辑，产物在 `build/`。

---

## 6. 安装与测试

### 6.1 刷入

Magisk / KernelSU 管理器 → 模块 → 从本地安装 → 选择 ZIP → 重启。

### 6.2 验证 crond 与 crontab

```bash
su
ps -A | grep crond
cat /data/adb/modules/app_scheduler/crontab_dir/root
```

`crontab_dir/root` 中应包含根据 `SCHEDULE_CRON_LINES` 生成的任务行，条数与有效行一致。

### 6.3 查看日志

```bash
cat /data/local/tmp/app_scheduler.log
ls -l /data/local/tmp/app_scheduler.log*
```

### 6.4 手动触发

```bash
su -c sh /data/adb/modules/app_scheduler/run_app.sh
# 或点管理器模块的「执行」按钮（action.sh）
```

建议分别在锁屏与解锁状态各测一次，对照日志与实际行为。

### 6.5 调整滑动比例

改 `SLIDE_START_PERCENT` / `SLIDE_END_PERCENT` 后直接手动触发即可，无需重启。

### 6.6 验证日志轮转

临时把 `LOG_MAX_SIZE` 改为 `1024`、`LOG_MAX_ROTATIONS` 改为 `2`，手动触发数次，确认 `.log.1`、`.log.2` 顺序正确，验证后改回。

### 6.7 PC 侧桩测试（可选）

没有真机时可用 Git Bash 搭桩：为 `am`、`cmd`、`input`、`dumpsys`、`wm`、`pm`、`monkey`、`busybox` 各写一个桩脚本放入测试 `bin` 并加进 `PATH`，桩按环境变量返回不同输出并记录调用参数；仅把 `run_app.sh` 的 `export PATH=` 行与 `/data/local/tmp` 路径替换为测试目录，用 `sh run_app.sh` 显式调用（Git Bash 对无执行位文件无法直接执行），再断言调用序列与日志。

---

## 7. 已知限制

| 限制项 | 说明 |
| --- | --- |
| 自动解锁可能被 HyperOS 2 拦截 | 核心风险，必须真机验证；优先依赖 `wm dismiss-keyguard` |
| 滑动比例需按机型微调 | 默认 80% → 25%，不同锁屏界面布局不同 |
| 修改调度需重启或重跑 `service.sh` | 普通参数改完即生效 |
| 依赖 Magisk BusyBox | 路径变化需调整 `service.sh` 的 `BUSYBOX` 检测链 |
| 密码明文存储 | Root 环境均可读取 |
| crond 启动有延迟 | 开机完成后额外等待 `WAIT_BOOT_EXTRA` 秒 |
| 解锁失败不启动 App | 记录失败并发通知，避免误操作 |

---

## 8. 后续可选方向

1. 失败通知附带截图或 `dumpsys` 片段，便于远程定位；
2. 把调度从 crontab 扩展为"仅充电时执行"等条件触发；
3. `lsposed` 分支补齐图形化配置（当前为内置界面 + 手动启动一次）。

（说明：早期文档中的"蓝牙设备解锁"方案已移除，解锁链只保留 `wm dismiss-keyguard` 与上滑密码两条路径。）
