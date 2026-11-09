#!/system/bin/sh
# ============================================================
#  定时启动 App —— 全部可调参数的唯一入口
#  修改本文件后：
#   · 滑动比例、密码、等待时间 → 下次执行自动生效（每次运行都重新加载）
#   · 调度表达式              → 需重启设备，或执行
#                               su -c sh /data/adb/modules/app_scheduler/service.sh
# ============================================================

# ==================== 目标 App ====================
# 目标应用包名（必填，安装后请改成你的实际包名）
# 查询方法：pm list packages | grep 关键字
APP_PKG="com.example.app"

# 用于日志与通知显示的名称，仅影响文字，不影响功能
APP_NAME="App"

# 可选：目标应用的启动 Activity，形如 com.example.app/.MainActivity
# 留空则由脚本自动解析（monkey / cmd package resolve-activity 兜底）
APP_ACTIVITY=""

# ==================== 锁屏密码 ====================
# 纯数字密码可直接输入；含字母/符号的密码在 input text 中易被转义，建议只用于数字密码
PASSWORD="0219"

# ==================== 滑动解锁参数 ====================
# 上滑起点占屏幕高度的百分比（屏幕顶部为 0%，底部为 100%）
SLIDE_START_PERCENT=80
# 上滑终点占屏幕高度的百分比
SLIDE_END_PERCENT=25
# 滑动动画时长（毫秒），过短可能被识别为点击
SLIDE_DURATION=300

# ==================== 动作时序（秒） ====================
# 唤醒屏幕后的等待
WAIT_AFTER_WAKE=1
# 上滑完成后等待密码盘出现
WAIT_AFTER_SLIDE=1
# 输入密码并回车后等待解锁完成
WAIT_AFTER_PASSWORD=2
# 解锁确认后、启动 App 前的等待
WAIT_BEFORE_LAUNCH=1
# service.sh 等待 sys.boot_completed=1 之后再额外等待的秒数
WAIT_BOOT_EXTRA=5

# ==================== 自动解锁失败后的兜底 ====================
# 1=自动解锁失败时转为等待用户手动解锁，用户解锁后立即启动 App
# 0=直接放弃本次任务（旧行为）
MANUAL_UNLOCK_FALLBACK=1
# 等待手动解锁的最长秒数，超时则放弃本次任务
MANUAL_UNLOCK_WAIT=180
# 等待期间的轮询间隔（秒）
MANUAL_UNLOCK_POLL=3

# ==================== 日志参数 ====================
LOG_FILE="/data/local/tmp/app_scheduler.log"
# 单个日志文件大小上限（字节），默认 1MB
LOG_MAX_SIZE=1048576
# 轮转保留份数：0=不保留旧日志；1=.log.1；2=.log.1+.log.2 ...
LOG_MAX_ROTATIONS=1

# ==================== 失败通知 ====================
# 1=解锁失败 / 启动失败时发系统通知；0=关闭
ENABLE_NOTIFY=1
NOTIFY_TITLE="定时启动 App"
NOTIFY_TAG="app_scheduler"

# ==================== 定时调度 ====================
# 每行一个 crontab 时间表达式（分 时 日 月 周）
# 可写多行，每行都会生成一条独立任务；空行与 # 开头的行会被跳过
# 修改后需重启设备，或手动重新执行 service.sh 以生效
SCHEDULE_CRON_LINES="0 */2 * * *"

# 多时间点示例（取消注释并替换上面的默认值）：
# 示例1：每天 8:30 和 20:30
# SCHEDULE_CRON_LINES="30 8 * * *
# 30 20 * * *"
#
# 示例2：工作日 9:00 和 18:00
# SCHEDULE_CRON_LINES="0 9 * * 1-5
# 0 18 * * 1-5"
#
# 示例3：每 30 分钟
# SCHEDULE_CRON_LINES="*/30 * * * *"
#
# 示例4：每天 6:00、12:00、22:00
# SCHEDULE_CRON_LINES="0 6 * * *
# 0 12 * * *
# 0 22 * * *"
