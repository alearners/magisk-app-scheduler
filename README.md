# AppTimer

> ## ⚠️ 使用前必读：全部后果由使用者自负
>
> 这是一个**会替你解锁手机、启动 App、并在你不操作时把屏幕关掉**的自动化工具，全程以 root 身份运行。
>
> 作者**不保证**它在你的机型、你的系统版本、你的锁屏样式上可用，也**不承担**任何直接或间接后果，包括但不限于：
>
> - **把自己或他人锁在手机外面**（自动上锁、反复锁屏、密码注入失败后的连锁反应）；
> - **锁屏密码泄露**——密码以明文写在配置里，任何拿到 root 的应用都能读到；
> - **数据丢失**——强制停止 App 会丢未保存的进度、脏数据、未发送的消息；
> - **误启动**——在不该启动的 App 上启动，触发扣费、上报位置、上传数据等业务动作；
> - **设备异常**——厂商 ROM 的省电/冻结策略与本模块互相影响，可能造成无法预料的唤醒行为；
> - **违反第三方 App 的服务条款**，以及由此产生的账号风险。
>
> 请只在**你自己拥有、可以随时刷机恢复**的设备上使用，并一次只改一个参数、观察一轮日志再继续。**不要**装在别人的手机上，**不要**用它绕过任何平台的设备管理或考勤规则。装上即表示你已理解并接受上述全部风险。

**AppTimer** 是一个按计划自动解锁、启动指定 App、并在无人操作时自动熄屏上锁的 Android 模块，提供两种形态：

| 形态 | 产物 | 依赖 | 说明 |
| --- | --- | --- | --- |
| Magisk 模块（纯 shell） | `apptimer-magisk-vX.zip` | Magisk / KernelSU，无需额外框架 | 刷入即用，参数全在 `config.sh` |
| LSPosed 模块（APK） | `apptimer-lsposed-vX.apk` | LSPosed + root | 自带界面与调度；用 Xposed 钩子收起锁屏，密码注入只作兜底 |

两种形态**功能对等**：自动解锁 → 按顺序启动 App → 可选「无操作 N 秒自动熄屏上锁」，计时基准都可选「系统定时」或「模块内部计时」。

文档与界面一律用「App」代称目标应用，不出现具体应用名。

---

## 一、能做什么

- **定时触发**：Magisk 版用 crontab 表达式；LSPosed 版支持「按时间点 / 按星期 / 按间隔循环」三种方式，可设总执行次数。
- **自动解锁**：优先用系统接口或 Xposed 钩子收起 Keyguard；失败时按屏幕比例上滑并注入密码，注入后再校验一次锁屏状态。
- **解锁兜底**：自动解锁失败不放弃，改为等你手动解锁，解锁后立即补跑；超时才记失败并发通知。
- **强制启动**：先 `am force-stop` 再启动，保证每次都是干净冷启动。
- **无操作自动锁屏**：启动成功后开始计时（默认 5 秒）。到点时只有同时满足「屏幕没被你自己锁掉」和「前台仍是本次启动的那个 App」才熄屏上锁；你一旦切到别的界面，这一步自动跳过，**不会误锁**。
- **启动条件**（LSPosed 版）：仅充电 / 仅 WiFi / 仅熄屏，不满足就跳过本次并记日志。
- **配置导出与导入**：两版都能把当前配置存成一个文件，换机、重装、刷回旧版后一条命令 / 两次点击就能恢复，不用重新手敲参数。
- **可观测**：每一步都写日志；失败发系统通知；Magisk 管理器点「执行」可手动跑一次。

---

## 二、安装

### Magisk 模块

1. 从 Releases 下载 `apptimer-magisk-vX.zip`。
2. Magisk / KernelSU → 模块 → 从本地安装 → 选择 ZIP → 重启。
3. 编辑配置（**先改 `APP_PKG`，再考虑密码**）：

```bash
su
vi /data/adb/modules/app_scheduler/config.sh
```

4. 改完调度后让配置生效：

```bash
su -c sh /data/adb/modules/app_scheduler/service.sh
```

> 只改密码、滑动比例、等待秒数不必重跑，`run_app.sh` 每次运行都会重新读 `config.sh`；改调度行（`SCHEDULE_CRON_LINES` / `TIMER_MODE` / `INTERVAL_MINUTES`）才需要。

### LSPosed APK

1. 设备已 root 且装好 LSPosed（Zygisk / Riru 任一），APK 需在 LSPosed 里**勾选作用域到「系统界面」**。
2. 安装 `apptimer-lsposed-vX.apk`（固定签名，可直接覆盖升级同签名旧版；**换过签名的旧包必须先卸载**，否则装不上）。
3. 打开 App → 授予通知权限 → 按提示把本应用加入电池优化白名单。
4. 新建计划：选 App、选定时方式、可开启「启动后无操作自动锁屏」并填秒数。

---

## 三、配置

### Magisk 版（`module/config.sh`）

| 配置项 | 默认 | 作用 |
| --- | --- | --- |
| `APP_PKG` | `com.example.app` | 目标 App 包名，**必须改** |
| `APP_NAME` | `App` | 日志与通知里的显示名 |
| `APP_ACTIVITY` | 空 | 可选，指定 Activity；留空自动解析 |
| `PASSWORD` | 空 | 锁屏密码，明文保存，留空则只走上滑 |
| `SLIDE_START_PERCENT` / `SLIDE_END_PERCENT` | `80` / `25` | 上滑起点/终点（屏幕高度百分比） |
| `SLIDE_DURATION` | `300` | 滑动时长（毫秒），太短会被当成点击 |
| `WAIT_AFTER_WAKE` / `WAIT_AFTER_SLIDE` / `WAIT_AFTER_PASSWORD` / `WAIT_BEFORE_LAUNCH` | `1` / `1` / `2` / `1` | 各阶段等待秒数 |
| `MANUAL_UNLOCK_FALLBACK` | `1` | 自动解锁失败是否等你手动解锁再启动 |
| `MANUAL_UNLOCK_WAIT` | `180` | 等待手动解锁的最长秒数 |
| `MANUAL_UNLOCK_POLL` | `3` | 等待期间锁屏轮询间隔（秒） |
| `AUTO_LOCK` | `0` | 启动后无操作是否自动熄屏上锁 |
| `AUTO_LOCK_DELAY` | `5` | 无操作多少秒后上锁（2–600） |
| `TIMER_MODE` | `cron` | `cron`=crond 驱动；`internal`=模块内部 sleep 循环 |
| `INTERVAL_MINUTES` | `120` | `internal` 模式下每隔多少分钟跑一次 |
| `SCHEDULE_CRON_LINES` | `0 */2 * * *` | crontab 表达式，可多行，`#` 开头跳过 |
| `LOG_FILE` | `/data/local/tmp/apptimer.log` | 日志路径 |
| `LOG_MAX_SIZE` / `LOG_MAX_ROTATIONS` | `1048576` / `1` | 日志轮转大小与保留份数 |
| `ENABLE_NOTIFY` | `1` | 失败通知开关 |
| `NOTIFY_TITLE` / `NOTIFY_TAG` | `AppTimer` / `apptimer` | 通知标题与标签 |

### LSPosed 版（界面里的「全局设置」）

| 设置 | 说明 |
| --- | --- |
| 锁屏密码 | 仅兜底时使用；优先由钩子收起锁屏 |
| 等用户手动解锁后再启动 | 对应 `MANUAL_UNLOCK_FALLBACK`，可设最长等待秒数（10–1800） |
| 启动后无操作自动锁屏 | 开关 + 秒数（2–600，默认 5） |
| 计时方式 | 「系统闹钟」= `AlarmManager`；「应用内部计时器」= 常驻前台服务计秒 |

| 计划字段 | 说明 |
| --- | --- |
| 启动多个 App | 一个计划可勾选多个，按顺序启动，解锁只做一次 |
| 定时方式 | 时间点（每天多个 `HH:MM`）/ 按星期 / 每 N 分钟 |
| 循环次数 | `0` 不限；>0 时跑完自动停用该计划 |
| 启动条件 | 无限制 / 仅充电 / 仅 WiFi / 仅熄屏 |

### 导出与导入

两版都支持，但**文件格式互不通用**：Magisk 版导出的是 `config.sh` 副本（`.sh`），LSPosed 版导出的是 JSON 备份（含全部计划 + 全局设置）。

**Magisk 版**（root 下执行，备份落在 `/sdcard/AppTimer/`）：

```bash
# 导出：生成 apptimer-config-YYYYmmdd-HHMMSS.sh
su -c sh /data/adb/modules/app_scheduler/config_export.sh

# 导入：先语法与内容校验，把原 config.sh 另存为 config.sh.bak-<时间戳>，再覆盖并重启调度
su -c sh /data/adb/modules/app_scheduler/config_import.sh /sdcard/AppTimer/apptimer-config-20261110-103000.sh
```

导入不会碰你的其它参数：只有校验通过才覆盖，失败则原配置保持不动。要回退就 `cp` 那份 `.bak` 回 `config.sh`，再 `sh service.sh`。

**LSPosed 版**（界面底部「配置备份」卡片）：

- **导出配置** → 系统文件选择器给个路径，写出 `apptimer-config-YYYYmmdd-HHmm.json`；
- **导入配置** → 选文件 → 先校验（是不是 AppTimer 的、schema 是否兼容）→ 弹确认框 → 确认后**覆盖**当前全部计划与全局设置，并立刻重排闹钟 / 拉起内部计时器。

> ⚠️ 两种备份文件都含**明文锁屏密码**，别放进网盘或发给别人。LSPosed 版导入是覆盖式：当前计划会被整体替换，导入前先导出一份现有配置更稳妥。

---

## 四、排查

```bash
# Magisk 版
su -c sh /data/adb/modules/app_scheduler/run_app.sh        # 立即跑一次
cat /data/local/tmp/apptimer.log                            # 看日志
ps -A | grep crond                                          # cron 模式是否在跑（部分 ROM 进程名显示为 busybox，可改用下一行）
grep -la crond /proc/[0-9]*/cmdline 2>/dev/null             # 上面看不到 crond 时，用命令行精确确认
cat /data/adb/modules/app_scheduler/crontab_dir/root        # 生成的 crontab
dumpsys window | grep -i keyguard                           # 锁屏状态自检

# LSPosed 版
# 界面 → 运行日志；或直接看应用私有日志
su -c cat /data/data/com.appscheduler.lsposed/files/apptimer.log
```

常见现象与原因：

| 现象 | 原因 / 处理 |
| --- | --- |
| 日志出现「自动解锁失败，keyguard 仍然显示」 | 密码注入被系统拦截。已默认开启手动解锁兜底；长期失败改用 LSPosed 版钩子 |
| 到点没执行 | 厂商省电策略冻结了应用。LSPosed 版需在系统设置里给本应用「自启动 / 无限制」；Magisk 版确认 `crond` 仍在运行 |
| 自动锁屏没在约定秒数生效 | 系统闹钟模式在冻结状态下会被推迟。改用「应用内部计时器」可保住精度；或先给应用自启动权限 |
| 启动的不是我选的那个 App | 检查包名/Activity；LSPosed 版重选一次应用 |
| APK 装不上，报签名冲突 | 旧包是别的签名。卸载旧包后重装（会清数据） |

---

## 五、已知限制

| 限制 | 说明 |
| --- | --- |
| 依赖 root | 熄屏、解锁、强杀都走 `su`，无 root 无法工作 |
| 解锁成功率随机型/ROM 而变 | 密码注入依赖锁屏布局与输入法；钩子方案更稳但需要 LSPosed |
| 密码明文存储 | 配置在 root 可读位置，任何 root 应用都能读 |
| 厂商冻结策略 | MIUI/HyperOS 会在后台冻结应用，导致定时与秒级计时被推迟；需手动放行 |
| 强制停止有代价 | 目标 App 的未保存数据会丢失 |
| 只测过少数机型 | 主要在 HyperOS（Android 15）真机验证；其他 ROM 请自测 |
| 备份文件是明文 | 导出的配置含明文密码，且两版格式互不通用：Magisk 版是 `.sh`，LSPosed 版是 `.json` |

---

## 六、构建与发布

仓库里**不放构建产物**，ZIP 与 APK 一律由 GitHub Actions 产出。

- `scripts/package.sh`：本地统一打包入口（Magisk ZIP + LSPosed APK，各自 sha256），与 CI 等价，便于自查。
- `.github/workflows/build.yml`：`main` 分支 → 打包 `apptimer-magisk-vX.zip`。
- `.github/workflows/build-lsposed-apk.yml`：`lsposed` 分支 → 编译 `apptimer-lsposed-vX.apk`。
- 打标签即发布：`v*` 触发 Magisk 包发布，`lsposed-v*` 触发 APK 发布。
- APK 使用仓库固定签名密钥（证书 SHA-256 以 `10948BFB9…000B2E5` 开头），因此同签名版本可覆盖升级。

---

## 七、许可证

**GNU General Public License v3.0（GPL-3.0）**，完整条款见 [`LICENSE`](LICENSE)。分发修改版时必须同样以 GPL-3.0 开源、保留版权声明并说明改动。

---

## 八、免责声明

本仓库按「现状」提供，**不附带任何明示或暗示的担保**，包括但不限于适销性、特定用途适用性与不侵权担保。作者不是你的设备管理员、运维或法律顾问，也不对你的设备、数据、账号、业务或第三方权益承担任何责任。你在安装、配置、运行、修改、分发本项目的全部行为及其后果，**完全由你自己承担**。若你不同意这一点，请立即停止使用并删除相关文件。
