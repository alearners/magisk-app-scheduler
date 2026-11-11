#!/system/bin/sh
# ============================================================
#  AppTimer 配置导入（Magisk 版）
#
#  用法（root）：
#    sh /data/adb/modules/app_scheduler/config_import.sh <备份文件>
#    例：sh /data/adb/modules/app_scheduler/config_import.sh \
#          /sdcard/AppTimer/apptimer-config-20261110-103000.sh
#
#  做的事：
#    1) 校验传入文件（能通过 sh -n、确实是本模块的 config.sh）
#    2) 把当前 config.sh 另存为 config.sh.bak-<时间戳>（可回退）
#    3) 覆盖 config.sh 并重启调度（kill 旧 crond / 内部计时循环后重跑 service.sh）
#
#  说明：本文件含明文锁屏密码，只导入你自己信任的备份。
# ============================================================

MODDIR=${0%/*}
CONFIG="$MODDIR/config.sh"

usage() {
    echo "用法: sh $0 <备份文件>"
    echo "  例: sh $0 /sdcard/AppTimer/apptimer-config-20261110-103000.sh"
}

if [ "$#" -lt 1 ]; then
    usage
    exit 2
fi

SRC="$1"

if [ ! -f "$SRC" ]; then
    echo "备份文件不存在：$SRC"
    exit 1
fi

if [ ! -f "$CONFIG" ]; then
    echo "找不到 $CONFIG，模块可能没有正确安装"
    exit 1
fi

# 1) 语法校验：备份文件是一份被 source 的 shell 脚本，语法错会直接把模块弄挂
if ! sh -n "$SRC" 2>/dev/null; then
    echo "备份文件不是合法的 shell 配置（sh -n 校验失败）：$SRC"
    exit 1
fi

# 2) 内容校验：至少要有 APP_PKG 与 SCHEDULE_CRON_LINES，避免误把别的脚本导进来
if ! grep -q '^APP_PKG=' "$SRC" 2>/dev/null; then
    echo "备份文件里没有 APP_PKG，不像 AppTimer 的配置：$SRC"
    exit 1
fi
if ! grep -q '^SCHEDULE_CRON_LINES=' "$SRC" 2>/dev/null; then
    echo "备份文件里没有 SCHEDULE_CRON_LINES，不像 AppTimer 的配置：$SRC"
    exit 1
fi

STAMP=$(date '+%Y%m%d-%H%M%S')

# 3) 先把当前配置另存一份，导入错了可以把它 cp 回来
if ! cp "$CONFIG" "$CONFIG.bak-$STAMP"; then
    echo "无法备份当前配置，已中止（原配置未改动）"
    exit 1
fi
echo "已备份当前配置：$CONFIG.bak-$STAMP"

# 4) 覆盖：先落临时文件再 mv，避免写入中断留下半截 config.sh
if ! cat "$SRC" > "$CONFIG.tmp"; then
    rm -f "$CONFIG.tmp"
    echo "读取备份文件失败，已中止（原配置未改动）"
    exit 1
fi
chmod 0755 "$CONFIG.tmp" 2>/dev/null
mv "$CONFIG.tmp" "$CONFIG" || {
    rm -f "$CONFIG.tmp"
    echo "覆盖 config.sh 失败，原配置保存在 $CONFIG.bak-$STAMP"
    exit 1
}
restorecon "$CONFIG" 2>/dev/null

echo "已导入配置：$SRC"

# 5) 重启调度：新的 config.sh 里 TIMER_MODE / SCHEDULE_CRON_LINES 可能都变了
LOG_FILE=$(. "$CONFIG" 2>/dev/null; echo "${LOG_FILE:-/data/local/tmp/apptimer.log}")
echo "[$(date '+%Y-%m-%d %H:%M:%S')] [config] 已导入配置：$SRC（原配置备份 $CONFIG.bak-$STAMP）" >> "$LOG_FILE" 2>/dev/null

# 旧的内部计时循环（service.sh 会把 pid 写到 timer.pid）
if [ -f "$MODDIR/timer.pid" ]; then
    kill "$(cat "$MODDIR/timer.pid" 2>/dev/null)" 2>/dev/null
    rm -f "$MODDIR/timer.pid"
fi

# 旧 crond：新版 BusyBox 的进程名是 busybox，pkill 按名字匹配不到，需要按命令行再找一遍
pkill crond 2>/dev/null
for d in /proc/[0-9]*; do
    [ -r "$d/cmdline" ] || continue
    _pid=${d#/proc/}
    [ "$_pid" = "$$" ] && continue
    _cmd=$(tr '\0' ' ' < "$d/cmdline" 2>/dev/null)
    case "$_cmd" in
        *"crond -c"*|*"crond -b"*|*"crond -f"*) kill "$_pid" 2>/dev/null ;;
    esac
done
sleep 1

# service.sh 会等 sys.boot_completed 再按新配置起 crond / 内部计时循环；放后台跑，脚本立即返回
( sh "$MODDIR/service.sh" >> "$LOG_FILE" 2>&1 ) &

echo "已重启调度（约 5 秒后生效，详见 $LOG_FILE）"
echo "回退：cp \"$CONFIG.bak-$STAMP\" \"$CONFIG\" 后重跑 service.sh"
