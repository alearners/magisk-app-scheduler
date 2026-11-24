# AppTimer

> ## 使用前须知
>
> 本工具以 root 权限运行，可代为解锁设备、启动应用，并在无人操作时熄屏锁定。
>
> 作者不对其在任何具体设备上的可用性作出保证，亦不承担由此产生的任何后果，包括但不限于：
>
> - **设备被锁于系统之外**——自动锁定、反复熄屏与密码试错可能引发连锁反应；
> - **锁屏密码泄露**——密码以明文存储于本机，任何取得 root 权限的应用均可读取；
> - **数据丢失**——强制停止应用将导致未保存的进度与未发送的消息丢失；
> - **误启动应用**——在不当时间启动应用，可能触发扣费、定位或数据上传；
> - **设备异常**——各厂商的省电与冻结策略可能与本工具冲突，唤醒行为存在不确定性；
> - **违反第三方服务条款**，以及由此产生的账号风险。
>
> 请仅在**你本人所有、可随时刷机恢复**的设备上使用；每次仅调整一项参数，查阅一轮日志后再进行下一项。**不得**安装于他人设备，**不得**用于规避任何平台的设备管理或考勤规则。安装即视为你已理解并接受上述内容。

AppTimer 按既定计划，在指定时间将设备唤醒至可用状态，并启动所选应用；若无人操作，可自动熄屏锁定。本仓库提供两种形态：

| 形态 | 产物 | 前置条件 | 说明 |
| --- | --- | --- | --- |
| Magisk 模块（纯 shell） | `apptimer-magisk-vX.zip` | Magisk / KernelSU，无需额外框架 | 刷入即可使用，参数集中于 `config.sh` |
| LSPosed 模块（APK） | `apptimer-lsposed-vX.apk` | LSPosed + root | 自带界面与定时功能，由 Xposed 收起锁屏，密码仅作兜底 |

两种形态功能一致：自动解锁 → 按顺序启动应用 → 可选「无操作后熄屏锁定」；计时可由系统负责，亦可由应用自行计时。

文档与界面中一律以「App / 应用」指代目标应用，不列具体名称。

---

## 一、功能范围

- **按计划执行**：Magisk 版使用 crontab 表达式；LSPosed 版支持按时间点、按星期、按间隔循环，并可限定总执行次数。
- **自动解锁**：优先由系统或 Xposed 收起锁屏；不可行时按屏幕比例上滑并输入密码，随后再次核验解锁状态。
- **解锁失败后的处理**：可设为等待手动解锁，解锁后立即补执行；超时则放弃本次，并发出通知。
- **启动前停止应用**：可选先彻底停止应用再启动，以确保处于初始状态。
- **无操作后锁定**：应用启动后开始计时（默认 5 秒）。仅在「屏幕未被用户锁定」且「前台仍为本次启动的应用」两项同时成立时熄屏锁定；若已切换至其他界面，则跳过该步骤，**不会误锁**。
- **条件限制**（LSPosed 版）：可限定仅在充电、连接 WiFi 或屏幕熄灭时执行；条件不满足则跳过本次并记录。
- **状态核验**（LSPosed 版）：底部「概览」页逐项列出通知、准时唤醒、省电白名单、root、LSPosed 作用域、应用列表与厂商后台限制，缺失项可点击跳转至系统设置处理；页面本身不会主动弹出授权框。
- **日志按日归档**（LSPosed 版）：每日一个文件，可切换日期、按级别筛选，并可清除单日或全部日志。
- **全程留痕**：失败时发出通知；Magisk 管理器内可点击「执行」手动触发一次。

---

## 二、安装

### Magisk 模块

1. 在 Releases 下载 `apptimer-magisk-vX.zip`。
2. Magisk / KernelSU → 模块 → 从本地安装 → 选择该 ZIP → 重启。
3. 修改配置（**请先正确填写 `APP_PKG`，再考虑密码**）：

```bash
su
vi /data/adb/modules/app_scheduler/config.sh
```

4. 使配置生效：

```bash
su -c sh /data/adb/modules/app_scheduler/service.sh
```

> 仅修改密码、滑动比例与等待秒数时，无需重复此步骤——`run_app.sh` 每次执行均会重新读取 `config.sh`。修改调度相关行（`SCHEDULE_CRON_LINES` / `TIMER_MODE` / `INTERVAL_MINUTES`）后方需执行。

### LSPosed APK

1. 设备已取得 root，并已安装 LSPosed（Zygisk 或 Riru 均可）。请在 LSPosed 中将本模块**作用域设为「系统界面」**。
2. 安装 `apptimer-lsposed-vX.apk`。同签名旧版可直接覆盖升级；**签名不同的旧包须先卸载**，否则无法安装。
3. 打开应用，底部四个入口依次为「概览 / 日志 / 配置 / 更多」。请先在「概览」逐项处理标红项目（通知、准时唤醒、省电白名单、root），再继续后续操作。
4. 在「配置」页新建计划：选择应用、设定时间；如需自动锁屏，请开启开关并填写秒数。

---

## 三、配置

### Magisk 版（`module/config.sh`）

| 配置项 | 默认值 | 作用 |
| --- | --- | --- |
| `APP_PKG` | `com.example.app` | 待启动应用的包名，**必须修改** |
| `APP_NAME` | `App` | 日志与通知中的显示名称 |
| `APP_ACTIVITY` | 空 | 可选，指定界面；留空则自动解析 |
| `PASSWORD` | 空 | 锁屏密码，明文存储；留空则仅上滑 |
| `SLIDE_START_PERCENT` / `SLIDE_END_PERCENT` | `80` / `25` | 上滑起止位置（按屏幕高度计算） |
| `SLIDE_DURATION` | `300` | 滑动时长（毫秒），过短将被识别为点击 |
| `WAIT_AFTER_WAKE` / `WAIT_AFTER_SLIDE` / `WAIT_AFTER_PASSWORD` / `WAIT_BEFORE_LAUNCH` | `1` / `1` / `2` / `1` | 各阶段等待秒数 |
| `MANUAL_UNLOCK_FALLBACK` | `1` | 自动解锁失败后是否等待手动解锁 |
| `MANUAL_UNLOCK_WAIT` | `180` | 最长等待秒数 |
| `MANUAL_UNLOCK_POLL` | `3` | 等待期间锁屏状态轮询间隔（秒） |
| `AUTO_LOCK` | `0` | 启动后无操作是否自动熄屏锁定 |
| `AUTO_LOCK_DELAY` | `5` | 无操作后锁定延时（2–600 秒） |
| `TIMER_MODE` | `cron` | `cron` 交由 crond；`internal` 由模块自行计时 |
| `INTERVAL_MINUTES` | `120` | `internal` 模式下的执行间隔（分钟） |
| `SCHEDULE_CRON_LINES` | `0 */2 * * *` | crontab 表达式，可写多行，`#` 开头者跳过 |
| `LOG_FILE` | `/data/local/tmp/apptimer.log` | 日志路径 |
| `LOG_MAX_SIZE` / `LOG_MAX_ROTATIONS` | `1048576` / `1` | 日志轮转阈值与保留份数 |
| `ENABLE_NOTIFY` | `1` | 失败时是否通知 |
| `NOTIFY_TITLE` / `NOTIFY_TAG` | `AppTimer` / `apptimer` | 通知标题与标签 |

### LSPosed 版（「配置」页全局设置）

| 设置项 | 作用 |
| --- | --- |
| 锁屏密码 | 仅在兜底解锁时使用；可收起锁屏时无需输入 |
| 自动解锁失败时，等待手动解锁 | 对应 `MANUAL_UNLOCK_FALLBACK`，可设定最长等待秒数（10–1800） |
| 应用无人操作时自动锁屏 | 开关 + 延时秒数（2–600，默认 5） |
| 计时方式 | 「系统闹钟」交由 `AlarmManager`；「应用内置计时器」依赖常驻前台服务 |

| 计划可配置项 | 说明 |
| --- | --- |
| 单计划多应用 | 可选择多个应用，按顺序启动，解锁仅执行一次 |
| 执行时间 | 每日多个 `HH:MM` / 指定星期 / 固定间隔 |
| 总执行次数 | 填 0 表示不限；填写数字后达到即自动停止 |
| 执行条件 | 无条件 / 仅充电 / 仅连接 WiFi / 仅屏幕熄灭 |

---

## 四、故障排查

```bash
# Magisk 版
su -c sh /data/adb/modules/app_scheduler/run_app.sh        # 立即执行一次
cat /data/local/tmp/apptimer.log                            # 查看日志
ps -A | grep crond                                          # 确认 cron 模式仍在运行
cat /data/adb/modules/app_scheduler/crontab_dir/root        # 查看生成的 crontab
dumpsys window | grep -i keyguard                           # 查看当前锁屏状态

# LSPosed 版
# 界面 → 日志；或直接读取应用私有目录
su -c cat /data/data/com.appscheduler.lsposed/files/apptimer-$(date +%F).log
```

常见现象：

| 现象 | 可能原因 |
| --- | --- |
| 日志出现「自动解锁未成功，锁屏仍处于锁定状态」 | 密码解锁被系统拦截。默认转为等待手动解锁；如持续失败，建议改用 LSPosed 版，由钩子收起锁屏 |
| 到点未执行 | 应用被系统冻结。LSPosed 版请放行「自启动 / 不受限制」；Magisk 版请确认 `crond` 仍在运行 |
| 自动锁屏未在设定秒数内生效 | 系统闹钟因冻结状态被延后。可改用「应用内置计时器」，或先放行自启动 |
| 启动的应用与选择不符 | 核对包名与界面；LSPosed 版请重新选择应用 |
| APK 安装失败并提示签名冲突 | 旧包签名不同。请卸载后重新安装（将清除数据） |

---

## 五、功能边界

| 限制 | 说明 |
| --- | --- |
| 依赖 root | 熄屏、解锁与强制停止均通过 `su` 执行，无 root 无法运行 |
| 解锁成功率因机型而异 | 密码解锁取决于锁屏样式与输入法兼容性；钩子方式更稳定，但需 LSPosed |
| 密码为明文 | 配置位于 root 可读路径，任何 root 应用均可读取 |
| 厂商冻结策略 | 澎湃、MIUI 在后台会冻结应用，定时与按秒计时均可能被延后，需手动放行 |
| 强制停止的代价 | 目标应用未保存的数据将丢失 |
| 适配范围有限 | 主要在澎湃（Android 15）真机验证；其他系统请自行测试 |

---

## 六、构建与发布

本仓库**不托管成品**，ZIP 与 APK 均由 GitHub Actions 生成。

- `scripts/package.sh`：本地一次性打包（Magisk ZIP + LSPosed APK，各附 sha256），与 CI 流程一致，便于核对。
- `.github/workflows/build.yml`：`main` 分支 → 生成 `apptimer-magisk-vX.zip`。
- `.github/workflows/build-lsposed-apk.yml`：`lsposed` 分支 → 编译 `apptimer-lsposed-vX.apk`。
- 推送 tag 即发布：`v*` 发布 Magisk 包，`lsposed-v*` 发布 APK。
- APK 使用仓库内固定签名（证书 SHA-256 以 `10948BFB9…000B2E5` 开头），同签名版本可直接覆盖升级。

---

## 七、许可证

**GNU General Public License v3.0（GPL-3.0）**，全文见 [`LICENSE`](LICENSE)。分发修改版本时，须按 GPL-3.0 开源、保留版权声明，并说明修改内容。

---

## 八、免责声明

本仓库按「现状」提供，**不作任何明示或暗示的担保**，包括可销售性、特定用途适用性及不侵犯第三方权利。作者不担任你的设备管理员、运维人员或法律顾问，对你的设备、数据、账号、业务及第三方权益概不负责。安装、配置、运行、修改与分发本软件所产生的一切行为与后果，**均由你自行承担**。如不予认同，请立即停止使用并删除相关文件。
