#!/system/bin/sh
# ============================================================
#  定时启动 App —— 核心执行脚本
#  由 crond 调用，也可手动执行：
#    su -c /data/adb/modules/app_scheduler/run_app.sh
#  执行流程：
#    日志轮转 → 单实例锁 → 强制停止 App → 判断锁屏
#    → 锁屏则唤醒并自动解锁 → 二次确认 → 启动 App
#    （自动解锁失败时，可等待用户手动解锁后再自动启动，见 config.sh 的 MANUAL_UNLOCK_*）
# ============================================================

export PATH=/data/adb/magisk:/system/bin:/system/xbin:/sbin:/vendor/bin:/product/bin

SCRIPT_DIR=$(cd "$(dirname "$0")" 2>/dev/null && pwd)
[ -z "$SCRIPT_DIR" ] && SCRIPT_DIR=${0%/*}
CONFIG_FILE="$SCRIPT_DIR/config.sh"

if [ ! -f "$CONFIG_FILE" ]; then
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] 缺少配置文件: $CONFIG_FILE" >> /data/local/tmp/apptimer.log
    exit 1
fi

# 每次执行都重新加载配置，改完参数无需重启也不需重跑 service.sh
. "$CONFIG_FILE"

LOG_FILE=${LOG_FILE:-/data/local/tmp/apptimer.log}
LOG_MAX_SIZE=${LOG_MAX_SIZE:-1048576}
LOG_MAX_ROTATIONS=${LOG_MAX_ROTATIONS:-1}
APP_PKG=${APP_PKG:-com.example.app}
APP_NAME=${APP_NAME:-App}
# 可选：留空则由脚本自动解析启动 Activity
APP_ACTIVITY=${APP_ACTIVITY:-}
PASSWORD=${PASSWORD:-}
SLIDE_START_PERCENT=${SLIDE_START_PERCENT:-80}
SLIDE_END_PERCENT=${SLIDE_END_PERCENT:-25}
SLIDE_DURATION=${SLIDE_DURATION:-300}
WAIT_AFTER_WAKE=${WAIT_AFTER_WAKE:-1}
WAIT_AFTER_SLIDE=${WAIT_AFTER_SLIDE:-1}
WAIT_AFTER_PASSWORD=${WAIT_AFTER_PASSWORD:-2}
WAIT_BEFORE_LAUNCH=${WAIT_BEFORE_LAUNCH:-1}
ENABLE_NOTIFY=${ENABLE_NOTIFY:-0}
NOTIFY_TITLE=${NOTIFY_TITLE:-定时启动 App}
NOTIFY_TAG=${NOTIFY_TAG:-apptimer}
# 自动解锁失败后：1=等待用户手动解锁，解锁后立即启动；0=直接放弃
MANUAL_UNLOCK_FALLBACK=${MANUAL_UNLOCK_FALLBACK:-1}
MANUAL_UNLOCK_WAIT=${MANUAL_UNLOCK_WAIT:-180}
MANUAL_UNLOCK_POLL=${MANUAL_UNLOCK_POLL:-3}

mkdir -p "$(dirname "$LOG_FILE")" 2>/dev/null

# ---------- 日志轮转 ----------
# .log -> .log.1 -> .log.2 ... 超过 LOG_MAX_ROTATIONS 份的最旧文件被删除
rotate_logs() {
    if [ "$LOG_MAX_ROTATIONS" -le 0 ]; then
        rm -f "$LOG_FILE"
        return 0
    fi
    rm -f "${LOG_FILE}.${LOG_MAX_ROTATIONS}"
    i=$((LOG_MAX_ROTATIONS - 1))
    while [ "$i" -ge 1 ]; do
        [ -f "${LOG_FILE}.${i}" ] && mv -f "${LOG_FILE}.${i}" "${LOG_FILE}.$((i + 1))"
        i=$((i - 1))
    done
    [ -f "$LOG_FILE" ] && mv -f "$LOG_FILE" "${LOG_FILE}.1"
    return 0
}

log() {
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] $1" >> "$LOG_FILE"
}

notify() {
    [ "$ENABLE_NOTIFY" = "1" ] || return 0
    cmd notification post -S bigtext -t "$NOTIFY_TITLE" "$NOTIFY_TAG" "$1" >/dev/null 2>&1
}

if [ -f "$LOG_FILE" ]; then
    LOG_SIZE=$(stat -c %s "$LOG_FILE" 2>/dev/null)
    if [ -z "$LOG_SIZE" ]; then
        LOG_SIZE=$(wc -c < "$LOG_FILE" | tr -d ' \n')
    fi
    if [ "$LOG_SIZE" -ge "$LOG_MAX_SIZE" ]; then
        rotate_logs
    fi
fi

# ---------- 单实例锁（避免上一轮未结束时重叠执行） ----------
# 用 mkdir 做原子锁：不依赖 fd 继承。部分 ROM 上 flock 传 fd（flock -n 9）会报
# 「Bad file descriptor」（toybox flock 与 BusyBox flock 都一样），会把每次执行
# 都误判成「上一轮仍在跑」而直接跳过，故不再使用 flock。
LOCK_FILE=/data/local/tmp/apptimer.lock
LOCK_DIR="${LOCK_FILE}.d"
HOLD_DIR_LOCK=0

release_lock() {
    if [ "$HOLD_DIR_LOCK" = "1" ]; then
        rm -rf "$LOCK_DIR" 2>/dev/null
        HOLD_DIR_LOCK=0
    fi
}
trap 'release_lock' EXIT

if ! mkdir "$LOCK_DIR" 2>/dev/null; then
    # 锁目录已存在：判断持有者是否还活着，进程已消失则视为残留锁，清掉重抢一次
    OLD_LOCK_PID=$(cat "$LOCK_DIR/pid" 2>/dev/null)
    if [ -n "$OLD_LOCK_PID" ] && ! kill -0 "$OLD_LOCK_PID" 2>/dev/null; then
        rm -rf "$LOCK_DIR" 2>/dev/null
        if ! mkdir "$LOCK_DIR" 2>/dev/null; then
            log "上一次任务仍在执行，本次跳过"
            exit 0
        fi
    else
        log "上一次任务仍在执行，本次跳过"
        exit 0
    fi
fi
echo $$ > "$LOCK_DIR/pid" 2>/dev/null
HOLD_DIR_LOCK=1

START_TS=$(date +%s)

log "=== 开始执行（目标: $APP_NAME / $APP_PKG） ==="

# ---------- 1. 强制停止 App ----------
am force-stop "$APP_PKG" >/dev/null 2>&1
if [ $? -ne 0 ]; then
    cmd activity force-stop "$APP_PKG" >/dev/null 2>&1
fi
log "已强制停止 $APP_NAME"
sleep 1

# ---------- 2. 判断锁屏状态 ----------
# 不同 ROM 字段不同，取第一条匹配行判断是否为 true
get_keyguard() {
    dumpsys window policy 2>/dev/null \
        | grep -E 'isKeyguardShowing|mKeyguardShowing|keyguardShowing|mShowingLockscreen' \
        | head -n 1 | tr -d '\r'
}

is_locked() {
    case "$1" in
        *"=true"*) return 0 ;;
    esac
    return 1
}

KEYGUARD_STATE=$(get_keyguard)
if is_locked "$KEYGUARD_STATE"; then
    IS_LOCKED=true
else
    IS_LOCKED=false
fi
log "锁屏状态: $IS_LOCKED ($KEYGUARD_STATE)"

# ---------- 3. 锁屏则尝试自动解锁 ----------
if [ "$IS_LOCKED" = "true" ]; then
    log "检测到锁屏，尝试自动解锁"

    # 点亮屏幕
    input keyevent KEYCODE_WAKEUP >/dev/null 2>&1 || input keyevent 224 >/dev/null 2>&1
    sleep "$WAIT_AFTER_WAKE"

    # 优先用系统命令尝试收起锁屏：不需要模拟滑动路径，适配性更好
    LOCK_ALREADY_CLEARED=false
    if command -v wm >/dev/null 2>&1; then
        wm dismiss-keyguard >/dev/null 2>&1
        sleep "$WAIT_AFTER_SLIDE"
        if ! is_locked "$(get_keyguard)"; then
            LOCK_ALREADY_CLEARED=true
            log "wm dismiss-keyguard 收起锁屏成功，无需输入密码"
        fi
    fi

    if [ "$LOCK_ALREADY_CLEARED" != "true" ]; then
        # 坐标自适应：取 wm size 最后一段（有 Override 时以 Override 为准）
        SCREEN_SIZE=$(wm size 2>/dev/null | tr -d '\r' | grep -oE '[0-9]+x[0-9]+' | tail -n 1)
        SCREEN_W=${SCREEN_SIZE%x*}
        SCREEN_H=${SCREEN_SIZE#*x}
        case "$SCREEN_W$SCREEN_H" in
            *[!0-9]*|"")
                SCREEN_W=1080
                SCREEN_H=2400
                log "未能获取屏幕分辨率，使用默认 ${SCREEN_W}x${SCREEN_H}"
                ;;
        esac

        # 按可配置比例计算上滑坐标
        START_X=$((SCREEN_W / 2))
        START_Y=$((SCREEN_H * SLIDE_START_PERCENT / 100))
        END_X=$((SCREEN_W / 2))
        END_Y=$((SCREEN_H * SLIDE_END_PERCENT / 100))

        log "屏幕尺寸: ${SCREEN_W}x${SCREEN_H}, 比例: ${SLIDE_START_PERCENT}%->${SLIDE_END_PERCENT}%, 上滑: ($START_X,$START_Y) -> ($END_X,$END_Y)"
        input swipe "$START_X" "$START_Y" "$END_X" "$END_Y" "$SLIDE_DURATION" >/dev/null 2>&1
        sleep "$WAIT_AFTER_SLIDE"

        # 输入密码并回车确认
        input text "$PASSWORD" >/dev/null 2>&1
        sleep "$WAIT_AFTER_SLIDE"
        input keyevent KEYCODE_ENTER >/dev/null 2>&1
        sleep "$WAIT_AFTER_PASSWORD"
    fi

    # 二次检查是否解锁成功
    KEYGUARD_AFTER=$(get_keyguard)
    if is_locked "$KEYGUARD_AFTER"; then
        log "自动解锁失败，keyguard 仍然显示: $KEYGUARD_AFTER"

        if [ "$MANUAL_UNLOCK_FALLBACK" = "1" ]; then
            # 兜底：等用户自己解锁，解锁后立即启动 App
            log "已启用手动解锁兜底：最多等待 ${MANUAL_UNLOCK_WAIT} 秒，用户解锁后立即启动 $APP_NAME"
            notify "$APP_NAME：请手动解锁，解锁后会自动启动"
            MANUAL_UNLOCKED=false
            WAITED=0
            while [ "$WAITED" -lt "$MANUAL_UNLOCK_WAIT" ]; do
                sleep "$MANUAL_UNLOCK_POLL"
                WAITED=$((WAITED + MANUAL_UNLOCK_POLL))
                if ! is_locked "$(get_keyguard)"; then
                    MANUAL_UNLOCKED=true
                    log "检测到用户已手动解锁（等待 ${WAITED} 秒），继续启动 $APP_NAME"
                    break
                fi
            done

            if [ "$MANUAL_UNLOCKED" != "true" ]; then
                log "等待手动解锁超时（${MANUAL_UNLOCK_WAIT} 秒），放弃本次任务"
                log "=== 执行终止（未启动 $APP_NAME） ==="
                notify "$APP_NAME 未能启动：等待手动解锁超时"
                exit 1
            fi
            sleep "$WAIT_BEFORE_LAUNCH"
        else
            log "未开启手动解锁兜底（MANUAL_UNLOCK_FALLBACK=0），直接放弃"
            log "=== 执行终止（未启动 $APP_NAME） ==="
            notify "$APP_NAME 未能启动：自动解锁失败"
            exit 1
        fi
    else
        log "解锁成功"
    fi
else
    log "设备未锁屏，直接启动 $APP_NAME"
fi

# ---------- 4. 启动 App ----------
sleep "$WAIT_BEFORE_LAUNCH"

if ! pm list packages 2>/dev/null | grep -Fq "package:$APP_PKG"; then
    log "未找到已安装的包: $APP_PKG，请检查 config.sh 中的 APP_PKG"
    notify "$APP_NAME 启动失败：未安装 $APP_PKG"
    exit 1
fi

LAUNCH_OK=false

# 1) config.sh 中显式指定了 Activity 时优先使用
if [ -n "$APP_ACTIVITY" ]; then
    if am start -n "$APP_ACTIVITY" >/dev/null 2>&1; then
        LAUNCH_OK=true
        log "$APP_NAME 启动命令已发送（am start -n $APP_ACTIVITY，来自 config.sh）"
    else
        log "config.sh 中的 APP_ACTIVITY 启动失败：$APP_ACTIVITY"
    fi
fi

# 2) monkey 启动
if [ "$LAUNCH_OK" != "true" ]; then
    if monkey -p "$APP_PKG" -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1; then
        LAUNCH_OK=true
        log "$APP_NAME 启动命令已发送（monkey）"
    else
        log "monkey 启动返回非零，改用 am start 兜底"
        ACTIVITY=$(cmd package resolve-activity --brief -c android.intent.category.LAUNCHER "$APP_PKG" 2>/dev/null | tail -n 1 | tr -d '\r')
        case "$ACTIVITY" in
            */*)
                if am start -n "$ACTIVITY" >/dev/null 2>&1; then
                    LAUNCH_OK=true
                    log "$APP_NAME 启动命令已发送（am start -n $ACTIVITY）"
                fi
                ;;
        esac
    fi
fi

if [ "$LAUNCH_OK" != "true" ]; then
    log "$APP_NAME 启动失败：monkey 与 am start 均未成功"
    notify "$APP_NAME 启动失败（$APP_PKG）"
    exit 1
fi

END_TS=$(date +%s)
log "=== 执行结束（耗时 $((END_TS - START_TS)) 秒） ==="

# ---------- 5. 启动后无操作自动锁屏 ----------
# 独立子进程盯着：不影响本次任务收尾，也不会占着锁。
# 判定「无操作」= 到点时屏幕还没被用户自己锁掉，且前台仍然是本次启动的 App。
if [ "${AUTO_LOCK:-0}" = "1" ]; then
    AUTO_LOCK_DELAY=${AUTO_LOCK_DELAY:-5}
    case "$AUTO_LOCK_DELAY" in
        ""|*[!0-9]*) AUTO_LOCK_DELAY=5 ;;
    esac
    [ "$AUTO_LOCK_DELAY" -lt 2 ] && AUTO_LOCK_DELAY=2
    [ "$AUTO_LOCK_DELAY" -gt 600 ] && AUTO_LOCK_DELAY=600
    log "自动锁屏已启用：${AUTO_LOCK_DELAY} 秒内无操作则熄屏上锁"
    (
        sleep "$AUTO_LOCK_DELAY"
        if is_locked "$(get_keyguard)"; then
            echo "[$(date '+%Y-%m-%d %H:%M:%S')] 自动锁屏：用户已自行锁屏，跳过" >> "$LOG_FILE"
            exit 0
        fi
        FG=$(dumpsys window 2>/dev/null | grep -m1 'mCurrentFocus=' | tr -d '\r' \
            | sed -e 's/.* u0 //' -e 's/\/.*//' | tr -d ' ')
        if [ -n "$FG" ] && [ "$FG" != "$APP_PKG" ]; then
            echo "[$(date '+%Y-%m-%d %H:%M:%S')] 自动锁屏：前台已是 $FG，判定用户已接管，跳过" >> "$LOG_FILE"
            exit 0
        fi
        if input keyevent 223 >/dev/null 2>&1; then
            echo "[$(date '+%Y-%m-%d %H:%M:%S')] 启动后 ${AUTO_LOCK_DELAY} 秒无操作，已自动熄屏上锁" >> "$LOG_FILE"
        else
            echo "[$(date '+%Y-%m-%d %H:%M:%S')] 自动锁屏失败：input keyevent 223 未成功" >> "$LOG_FILE"
        fi
    ) &
fi

exit 0
