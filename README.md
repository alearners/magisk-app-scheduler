# 定时启动 App · Magisk / LSPosed 模块

按可配置的 crontab 时间点**强制停止并重新启动**指定 App 的 Android 模块。全部可调参数集中在 `config.sh`，升级模块时只需替换脚本、保留自己的配置即可。

目标环境：小米机型 / HyperOS（MIUI）Android 13–15，已 Root（Magisk 或 KernelSU）。

> 说明：本仓库所有模块文案、日志、变量统一使用 `App` 作为目标应用代称，不出现任何具体应用名。

---

## 两条分支

| 分支 | 产物 | 形态 | 适用场景 |
| --- | --- | --- | --- |
| `main` | `app_scheduler-vX.zip` | Magisk 模块（纯 shell） | 首选。Magisk / KernelSU 直接刷入，无需额外框架 |
| `lsposed` | `app-scheduler-lsposed-vX.apk` | LSPosed 模块（APK）+ 内置调度 | 需要更可靠的解锁方式：用 Xposed 钩子收起锁屏，不再依赖 `input text` 注入密码 |

`lsposed` 分支在 `main` 的基础上新增 `lsposed/` 工程：APK 自带界面与定时器，通过 `su` 执行与 shell 版等价的流程，并用 Xposed 钩子在 SystemUI 内收起 Keyguard，注入密码只作为兜底。

界面用 **Kotlin + Jetpack Compose（Material3）** 重写：状态卡、调度配置卡、操作按钮、滚动日志卡四块结构，圆角卡片 + 青蓝主色（`#1FA0CC`）+ 浅蓝灰背景，Android 12 以上跟随系统动态取色，整体风格参考开源项目 Sesame-AG 的 Compose 主题与卡片式布局。

---

## 功能特性

- **定时调度**：`config.sh` 中 `SCHEDULE_CRON_LINES` 支持任意多行 crontab 表达式，`service.sh` 开机动态生成 crontab 并启动 BusyBox `crond`。
- **强制重启**：先 `am force-stop`，再 `monkey` / `am start` 启动，失败自动降级。
- **锁屏解锁**：优先 `wm dismiss-keyguard`（无需模拟滑动）；未解锁时按屏幕比例上滑 + 输入密码 + 回车，解锁后二次校验 Keyguard 状态。
- **手动解锁兜底**：自动解锁失败时不放弃，改为等待用户自己解锁（默认最长 180 秒，可关）；一旦检测到解锁立即启动 App，超时才记失败并发通知。
- **失败即退出**：兜底关闭或等待超时时不会盲目启动 App，改发通知提醒。
- **日志轮转**：单文件超过 `LOG_MAX_SIZE`（默认 1 MB）自动轮转，保留份数由 `LOG_MAX_ROTATIONS` 控制。
- **单实例锁**：上一轮未结束时本轮直接跳过，避免重叠执行。
- **失败通知**：`cmd notification post` 发送系统通知。
- **动作按钮**：Magisk 管理器里点「执行」即运行 `action.sh`，手动跑一次。
- **卸载清理**：`uninstall.sh` 停止 crond 并清理锁文件。

### v1.4 相对 v1.3 的变化

1. 新增 `action.sh`：管理器「执行」按钮一键跑一次；
2. 自动解锁新增 `wm dismiss-keyguard` 前置路径，多数机型无需输入密码即可解锁；
3. 新增可选 `APP_ACTIVITY`，可绕过 `monkey` 直接指定启动 Activity；
4. 打包全部迁到 GitHub Actions（统一 LF、755 权限、ZIP 结构校验、sha256、Tag 自动发布）；
5. 移除「蓝牙解锁方案」这一改进方向，解锁链只保留 `wm` + 上滑密码两条路径；
6. 新增 `lsposed` 分支：Xposed 钩子替代密码注入的模块形态，界面用 Kotlin + Compose 重写；
7. 新增手动解锁兜底：自动解锁失败可等待用户解锁后自动启动（`MANUAL_UNLOCK_FALLBACK` / `MANUAL_UNLOCK_WAIT` / `MANUAL_UNLOCK_POLL`）。

---

## 仓库结构

```
.
├── .github/workflows/
│   ├── build.yml                 # main：打包 Magisk ZIP + Tag 发布
│   └── build-lsposed-apk.yml     # lsposed 分支：编译 LSPosed APK + Tag 发布
├── module/                       # Magisk 模块内容（ZIP 根目录）
│   ├── META-INF/com/google/android/{update-binary,updater-script}
│   ├── module.prop
│   ├── config.sh
│   ├── service.sh
│   ├── run_app.sh
│   ├── action.sh
│   ├── customize.sh
│   └── uninstall.sh
├── scripts/package.sh            # 本地打包（与 CI 等价，便于自查）
├── tests/run_app_stub_test.sh    # 桩测试：无需真机验证 run_app.sh 分支
├── docs/DEVELOPMENT.md           # 开发文档
└── lsposed/                      # 仅 lsposed 分支存在
```

---

## 安装

1. 从 [Releases](../../releases) 下载对应版本的 ZIP（或在 Actions 的 artifact 中取）。
2. Magisk / KernelSU 管理器 → 模块 → 从本地安装 → 选择 ZIP → 重启。
3. 重启后编辑配置：

```bash
su
vi /data/adb/modules/app_scheduler/config.sh
```

至少填写 `APP_PKG`（目标应用包名，`pm list packages | grep 关键字` 可查）与 `PASSWORD`。

4. 重新生成 crontab 使配置生效：

```bash
su -c sh /data/adb/modules/app_scheduler/service.sh
```

（只改密码、滑动比例、等待时间时不必执行这一步，`run_app.sh` 每次运行都会重新加载 `config.sh`；只有改调度才需要。）

---

## 配置说明（`module/config.sh`）

| 配置项 | 默认 | 作用 |
| --- | --- | --- |
| `APP_PKG` | `com.example.app` | 目标应用包名，**必须改** |
| `APP_NAME` | `App` | 日志与通知中显示的名称 |
| `APP_ACTIVITY` | 空 | 可选，形如 `com.example.app/.MainActivity`；留空自动解析 |
| `PASSWORD` | `0219` | 锁屏密码，建议纯数字 |
| `SLIDE_START_PERCENT` | `80` | 上滑起点（屏幕高度百分比） |
| `SLIDE_END_PERCENT` | `25` | 上滑终点 |
| `SLIDE_DURATION` | `300` | 滑动时长（毫秒），过短会被识别为点击 |
| `WAIT_AFTER_WAKE` | `1` | 唤醒后等待（秒） |
| `WAIT_AFTER_SLIDE` | `1` | 上滑后等待密码盘出现 |
| `WAIT_AFTER_PASSWORD` | `2` | 输入密码并回车后等待解锁完成 |
| `WAIT_BEFORE_LAUNCH` | `1` | 启动 App 前等待 |
| `WAIT_BOOT_EXTRA` | `5` | 开机完成后额外等待 |
| `MANUAL_UNLOCK_FALLBACK` | `1` | 自动解锁失败后是否等用户手动解锁再启动（`1` 开 / `0` 关） |
| `MANUAL_UNLOCK_WAIT` | `180` | 等待手动解锁的最长秒数，超时放弃本次任务 |
| `MANUAL_UNLOCK_POLL` | `3` | 等待期间的锁屏状态轮询间隔（秒） |
| `LOG_FILE` | `/data/local/tmp/app_scheduler.log` | 日志路径 |
| `LOG_MAX_SIZE` | `1048576` | 单份日志上限（字节） |
| `LOG_MAX_ROTATIONS` | `1` | 保留旧日志份数，`0` 表示不留 |
| `ENABLE_NOTIFY` | `1` | 失败通知开关 |
| `NOTIFY_TITLE` / `NOTIFY_TAG` | `定时启动 App` / `app_scheduler` | 通知标题与标签 |
| `SCHEDULE_CRON_LINES` | `0 */2 * * *` | 调度表达式，可多行 |

### 调度示例

```bash
# 每天 8:30、20:30
SCHEDULE_CRON_LINES="30 8 * * *
30 20 * * *"

# 工作日 9:00、18:00
SCHEDULE_CRON_LINES="0 9 * * 1-5
0 18 * * 1-5"

# 每 30 分钟
SCHEDULE_CRON_LINES="*/30 * * * *"
```

空白行与 `#` 开头的行会被 `service.sh` 跳过。

---

## 使用与排查

```bash
# 立即跑一次
su -c sh /data/adb/modules/app_scheduler/run_app.sh

# 确认 crond 与生成的任务
ps -A | grep crond
cat /data/adb/modules/app_scheduler/crontab_dir/root

# 看日志与轮转情况
cat /data/local/tmp/app_scheduler.log
ls -l /data/local/tmp/app_scheduler.log*

# 锁屏字段自检（不同 ROM 字段不同）
dumpsys window policy | grep -i keyguard
```

日志中出现 `自动解锁失败，keyguard 仍然显示` 说明密码注入被系统拦截：默认已开启手动解锁兜底，你手动解锁后 App 会自动启动；若要恢复"失败即退出"的旧行为，把 `MANUAL_UNLOCK_FALLBACK` 设为 `0`。长期失败则改用 `lsposed` 分支的钩子方案。

---

## GitHub 打包流程

两个分支共用同一套约定：**仓库里不放构建产物，ZIP / APK 一律由 Actions 产出。**

### main 分支（Magisk ZIP）

- 触发：push 到 `main`、提交/推送 `v*` 标签、PR、手动 `workflow_dispatch`。
- 步骤：读取 `module.prop` → 统一 LF → 修正 755/644 权限 → 校验必需文件 → `sh -n` 语法检查 → `zip` 打包 → 生成 sha256 → 上传 artifact（保留 90 天）。
- 推送 `v*` 标签时额外创建 Release，附 ZIP 与 sha256。

```bash
git tag v1.4
git push origin v1.4
```

### lsposed 分支（APK）

- 触发：push 到 `lsposed`、`lsposed-v*` 标签、手动触发。
- 步骤：JDK 17 + Gradle 8.7 → `:app:assembleRelease` → 重命名并上传 `app-scheduler-lsposed-vX.apk` + sha256 → 标签时发布 Release。

### 本地自查

```bash
sh scripts/package.sh          # 生成 build/app_scheduler-v1.4.zip，与 CI 同样的校验
```

---

## 版本历史

| 版本 | versionCode | 变更 |
| --- | --- | --- |
| v1.4 | 5 | `action.sh`、`wm dismiss-keyguard` 前置解锁、`APP_ACTIVITY`、手动解锁兜底、GitHub Actions 打包、移除蓝牙解锁方向、新增 lsposed 分支 |
| v1.3 | 4 | 参数集中到 `config.sh`、多时间点调度、日志轮转份数可配置、`customize.sh` |
| v1.2 | 3 | 失败通知、单实例锁、坐标自适应 |
| v1.1 | 2 | 锁屏判断与二次校验 |
| v1.0 | 1 | 基础强制重启 + crond 调度 |

---

## 已知限制

| 限制 | 说明 |
| --- | --- |
| HyperOS 2 可能拦截密码注入 | 核心风险，必须真机验证；优先依赖 `wm dismiss-keyguard` |
| 滑动比例需按机型微调 | 默认 80% → 25%，不同锁屏界面布局不同 |
| 修改调度需重跑 `service.sh` 或重启 | 普通参数改完即生效 |
| 依赖 Magisk BusyBox | 路径变化时改 `service.sh` 的 `BUSYBOX` 检测链 |
| 密码明文保存 | `config.sh` 属 Root 可读范围，请自行评估 |
| crond 启动有延迟 | 开机完成后额外等待 `WAIT_BOOT_EXTRA` 秒 |

---

## 许可证

本项目采用 **GNU General Public License v3.0（GPL-3.0）** 授权，完整条款见仓库根目录 [`LICENSE`](LICENSE)。

你可以自由使用、修改、分发本模块；但分发修改后的版本时，必须同样以 GPL-3.0 开放源代码、保留版权声明，并说明改动内容。

---

## 免责声明

本项目仅用于个人设备上的自动化测试与自用场景。强制停止应用可能导致未保存的数据丢失、消息延迟接收，请自行评估影响；因使用本模块造成的任何后果由使用者承担。
