#!/system/bin/sh
# ============================================================
#  定时启动 App —— 开机服务
#  读取 config.sh 中的 SCHEDULE_CRON_LINES，动态生成 crontab
#  并启动 BusyBox crond。修改调度后重跑本脚本即可生效。
# ============================================================

umask 022
export PATH=/data/adb/magisk:/system/bin:/system/xbin:/sbin:/vendor/bin:/product/bin

# ---------- crond 进程识别 ----------
# 新版 Magisk 自带的 BusyBox 把 crond 的进程名显示为 busybox / libbusybox.so，
# 此时 pidof、pkill 都会按名字匹配失败：表现为「crond 启动失败」误报，
# 以及旧 crond 杀不掉而越积越多。这里改为按完整命令行匹配。
crond_pids() {
    for d in /proc/[0-9]*; do
        [ -r "$d/cmdline" ] || continue
        _pid=${d#/proc/}
        [ "$_pid" = "$$" ] && continue
        _cmd=$(tr '\0' ' ' < "$d/cmdline" 2>/dev/null)
        case "$_cmd" in
            *"crond -c"*|*"crond -b"*|*"crond -f"*) echo "$_pid" ;;
        esac
    done
}

kill_crond() {
    # 两条路径都试：名字能匹配到的设备用 pkill，匹配不到的用 crond_pids
    pkill crond 2>/dev/null
    for _p in $(crond_pids); do
        kill "$_p" 2>/dev/null
    done
}

MODDIR=${0%/*}
CONFIG_FILE="$MODDIR/config.sh"

if [ ! -f "$CONFIG_FILE" ]; then
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] 缺少 config.sh，模块未启动" >> /data/local/tmp/apptimer.log
    exit 1
fi

# 先加载配置，才能拿到日志路径与等待时间
. "$CONFIG_FILE"

LOG_FILE=${LOG_FILE:-/data/local/tmp/apptimer.log}
WAIT_BOOT_EXTRA=${WAIT_BOOT_EXTRA:-5}
ENABLE_NOTIFY=${ENABLE_NOTIFY:-0}
NOTIFY_TITLE=${NOTIFY_TITLE:-定时启动 App}
NOTIFY_TAG=${NOTIFY_TAG:-apptimer}

mkdir -p "$(dirname "$LOG_FILE")" 2>/dev/null

log() {
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] [service] $1" >> "$LOG_FILE"
}

notify() {
    [ "$ENABLE_NOTIFY" = "1" ] || return 0
    cmd notification post -S bigtext -t "$NOTIFY_TITLE" "$NOTIFY_TAG" "$1" >/dev/null 2>&1
}

log "=== 开机服务启动 ==="

# ---------- 等待系统启动完成（最多 300 秒） ----------
COUNT=0
while [ "$(getprop sys.boot_completed)" != "1" ]; do
    sleep 2
    COUNT=$((COUNT + 2))
    if [ "$COUNT" -ge 300 ]; then
        log "等待 sys.boot_completed 超时，继续尝试启动 crond"
        break
    fi
done

# 额外等待，避免过早启动 crond
sleep "$WAIT_BOOT_EXTRA"

# ---------- 计时方式 ----------
# internal：不用 crond，由模块自己的循环数时间（等价于「应用内部计时器」）
TIMER_MODE=${TIMER_MODE:-cron}
if [ "$TIMER_MODE" = "internal" ]; then
    INTERVAL_MINUTES=${INTERVAL_MINUTES:-120}
    case "$INTERVAL_MINUTES" in
        ""|*[!0-9]*) INTERVAL_MINUTES=120 ;;
    esac
    [ "$INTERVAL_MINUTES" -lt 1 ] && INTERVAL_MINUTES=1
    INTERVAL_SEC=$((INTERVAL_MINUTES * 60))
    kill_crond
    log "计时方式：内部计时器，每 ${INTERVAL_MINUTES} 分钟执行一次（未启动 crond）"
    ( while :; do
          sleep "$INTERVAL_SEC"
          "$MODDIR/run_app.sh" >> "$LOG_FILE" 2>&1
      done ) &
    echo $! > "$MODDIR/timer.pid" 2>/dev/null
    log "=== 开机服务结束（内部计时器已启动） ==="
    exit 0
fi
log "计时方式：crond（系统级定时）"

# ---------- 查找 BusyBox ----------
BUSYBOX="/data/adb/magisk/busybox"
if [ ! -x "$BUSYBOX" ]; then
    BUSYBOX=$(command -v busybox 2>/dev/null)
fi
if [ ! -x "$BUSYBOX" ]; then
    BUSYBOX="/data/adb/ksu/bin/busybox"
fi
if [ ! -x "$BUSYBOX" ]; then
    log "未找到 BusyBox，crond 未启动"
    notify "crond 未启动：未找到 BusyBox"
    exit 1
fi
log "BusyBox: $BUSYBOX"

# ---------- 根据配置动态生成 crontab ----------
CRONTAB_DIR="$MODDIR/crontab_dir"
mkdir -p "$CRONTAB_DIR"
CRONTAB_FILE="$CRONTAB_DIR/root"
LIST_FILE="$CRONTAB_DIR/.schedule_lines"

: > "$CRONTAB_FILE"
: > "$LIST_FILE"
printf '%s\n' "$SCHEDULE_CRON_LINES" > "$LIST_FILE"

TASK_COUNT=0
while IFS= read -r line; do
    # 去掉行首尾空白与 Windows 残留的 CR
    line=$(printf '%s' "$line" | tr -d '\r' | sed -e 's/^[[:space:]]*//' -e 's/[[:space:]]*$//')
    case "$line" in
        ""|\#*) continue ;;
    esac
    echo "$line $MODDIR/run_app.sh >> $LOG_FILE 2>&1" >> "$CRONTAB_FILE"
    TASK_COUNT=$((TASK_COUNT + 1))
done < "$LIST_FILE"
rm -f "$LIST_FILE"

if [ "$TASK_COUNT" -le 0 ]; then
    log "SCHEDULE_CRON_LINES 中没有有效的调度行，crond 未启动"
    notify "调度配置为空，crond 未启动"
    exit 1
fi

log "已生成 $TASK_COUNT 条定时任务："
while IFS= read -r line; do
    log "  $line"
done < "$CRONTAB_FILE"

# ---------- 清理旧 crond 后重新启动 ----------
kill_crond
sleep 1

"$BUSYBOX" crond -b -c "$CRONTAB_DIR" -l 2
CROND_BG_EXIT=$?
sleep 2

set -- $(crond_pids)
CROND_PID=${1:-}
if [ -n "$CROND_PID" ]; then
    log "crond 已启动 (pid $CROND_PID)，调度生效"
elif [ "$CROND_BG_EXIT" -eq 0 ]; then
    # /proc 对当前域不可读时拿不到进程号，退化为按启动返回码判断，避免误报失败
    log "crond 已在后台启动（未能确认进程号：/proc 不可读，按启动返回码视为成功）"
else
    log "crond 启动失败（返回码 $CROND_BG_EXIT）"
    notify "crond 启动失败"
    exit 1
fi

log "=== 开机服务结束 ==="
