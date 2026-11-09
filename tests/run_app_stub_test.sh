#!/bin/bash
# 桩测试：验证 run_app.sh 的分支逻辑（无需真机）
set -u
ROOT="$1"
TMP=$(mktemp -d)
TMP=$(cd "$TMP" && pwd)
echo "临时目录: $TMP"

cp -r "$ROOT/module" "$TMP/mod"
rm -f "$TMP/mod/config.sh"
mkdir -p "$TMP/bin" "$TMP/tmpfiles"

cat > "$TMP/mod/config.sh" <<EOF
APP_PKG="com.example.app"
APP_NAME="App"
APP_ACTIVITY=""
PASSWORD="0219"
SLIDE_START_PERCENT=80
SLIDE_END_PERCENT=25
SLIDE_DURATION=300
WAIT_AFTER_WAKE=0
WAIT_AFTER_SLIDE=0
WAIT_AFTER_PASSWORD=0
WAIT_BEFORE_LAUNCH=0
WAIT_BOOT_EXTRA=0
MANUAL_UNLOCK_FALLBACK=1
MANUAL_UNLOCK_WAIT=6
MANUAL_UNLOCK_POLL=1
LOG_FILE="$TMP/app.log"
LOG_MAX_SIZE=1048576
LOG_MAX_ROTATIONS=1
ENABLE_NOTIFY=0
SCHEDULE_CRON_LINES="0 */2 * * *"
EOF

# ---------- 桩命令 ----------
cat > "$TMP/bin/dumpsys" <<'EOF'
#!/bin/sh
# UNLOCK_AFTER=N：第 N 次查询之后模拟"用户已手动解锁"
if [ -n "${UNLOCK_AFTER:-}" ]; then
    n=$(cat "${TMP_KGCOUNT:-/tmp/kgcount}" 2>/dev/null || echo 0)
    n=$((n + 1))
    echo "$n" > "${TMP_KGCOUNT:-/tmp/kgcount}"
    if [ "$n" -gt "$UNLOCK_AFTER" ]; then
        echo false > "$KG_STATE"
    fi
fi
echo "  mKeyguardShowing=$(cat "$KG_STATE")"
EOF

cat > "$TMP/bin/wm" <<'EOF'
#!/bin/sh
echo "wm $*" >> "$CALLS"
if [ "$1" = "dismiss-keyguard" ]; then
    if [ "${WM_DISMISS_WORKS:-0}" = "1" ]; then
        echo false > "$KG_STATE"
    fi
elif [ "$1" = "size" ]; then
    echo "Physical size: 1080x2400"
fi
exit 0
EOF

cat > "$TMP/bin/input" <<'EOF'
#!/bin/sh
echo "input $*" >> "$CALLS"
if [ "$1" = "keyevent" ] && { [ "$2" = "66" ] || [ "$2" = "KEYCODE_ENTER" ]; }; then
    if [ "${PASSWORD_WORKS:-0}" = "1" ]; then
        echo false > "$KG_STATE"
    fi
fi
exit 0
EOF

cat > "$TMP/bin/am" <<'EOF'
#!/bin/sh
echo "am $*" >> "$CALLS"
exit 0
EOF

cat > "$TMP/bin/cmd" <<'EOF'
#!/bin/sh
echo "cmd $*" >> "$CALLS"
if [ "$1" = "package" ] && [ "$2" = "resolve-activity" ]; then
    echo "com.example.app/.MainActivity"
fi
exit 0
EOF

cat > "$TMP/bin/pm" <<'EOF'
#!/bin/sh
echo "pm $*" >> "$CALLS"
if [ "${PM_EMPTY:-0}" = "1" ]; then
    exit 0
fi
echo "package:com.example.app"
exit 0
EOF

cat > "$TMP/bin/monkey" <<'EOF'
#!/bin/sh
echo "monkey $*" >> "$CALLS"
exit "${MONKEY_RC:-0}"
EOF

chmod 755 "$TMP"/bin/*

# 替换 PATH 行与运行期路径（锁文件、日志目录）
sed -i "s|^export PATH=.*|export PATH=\"$TMP/bin:/usr/bin:/bin\"|" "$TMP/mod/run_app.sh"
sed -i "s|/data/local/tmp|$TMP/tmpfiles|g" "$TMP/mod/run_app.sh"

FAILED=0

reset_case() {
    echo "true" > "$TMP/kg"          # 初始锁屏
    rm -f "$TMP/kgcount"
    : > "$TMP/calls"
    : > "$TMP/app.log"
}

run_script() {
    KG_STATE="$TMP/kg" CALLS="$TMP/calls" TMP_KGCOUNT="$TMP/kgcount" "$@" sh "$TMP/mod/run_app.sh"
}

show() {
    echo "--- $1 (rc=$2) ---"
    [ -f "$TMP/kg" ] && echo "锁定状态: $(cat "$TMP/kg")"
    echo "调用序列:"; sed 's/^/  /' "$TMP/calls"
    echo "日志:"; sed 's/^/  /' "$TMP/app.log"
}

echo "=== 场景1：wm dismiss-keyguard 直接解锁 ==="
reset_case
WM_DISMISS_WORKS=1 run_script env; rc=$?
show "wm_ok" "$rc"
[ "$rc" -eq 0 ] || FAILED=1
grep -q "收起锁屏成功" "$TMP/app.log" || { echo "  !! 缺少 wm 成功日志"; FAILED=1; }
grep -q "input text" "$TMP/calls" && { echo "  !! wm 成功时不应注入密码"; FAILED=1; }

echo "=== 场景2：wm 无效，密码注入解锁 ==="
reset_case
PASSWORD_WORKS=1 run_script env; rc=$?
show "password_ok" "$rc"
[ "$rc" -eq 0 ] || FAILED=1
grep -q "input text 0219" "$TMP/calls" || { echo "  !! 未走密码注入路径"; FAILED=1; }

echo "=== 场景3：手动解锁兜底关闭，解锁失败应退出 ==="
reset_case
sed -i 's|MANUAL_UNLOCK_FALLBACK=1|MANUAL_UNLOCK_FALLBACK=0|' "$TMP/mod/config.sh"
run_script env; rc=$?
show "fallback_off" "$rc"
[ "$rc" -ne 0 ] || { echo "  !! 期望失败但返回 0"; FAILED=1; }
grep -q "monkey" "$TMP/calls" && { echo "  !! 解锁失败后不应启动 App"; FAILED=1; }
grep -q "未开启手动解锁兜底" "$TMP/app.log" || { echo "  !! 缺少兜底关闭日志"; FAILED=1; }
sed -i 's|MANUAL_UNLOCK_FALLBACK=0|MANUAL_UNLOCK_FALLBACK=1|' "$TMP/mod/config.sh"

echo "=== 场景4：兜底开启，用户在第 6 次查询后手动解锁 → 自动启动 ==="
reset_case
UNLOCK_AFTER=6 run_script env; rc=$?
show "manual_unlock_ok" "$rc"
[ "$rc" -eq 0 ] || { echo "  !! 手动解锁后应成功启动"; FAILED=1; }
grep -q "检测到用户已手动解锁" "$TMP/app.log" || { echo "  !! 缺少手动解锁日志"; FAILED=1; }
grep -q "monkey" "$TMP/calls" || { echo "  !! 解锁后未启动 App"; FAILED=1; }

echo "=== 场景5：兜底开启但始终未解锁 → 超时退出 ==="
reset_case
run_script env; rc=$?
show "manual_unlock_timeout" "$rc"
[ "$rc" -ne 0 ] || { echo "  !! 超时应返回非 0"; FAILED=1; }
grep -q "等待手动解锁超时" "$TMP/app.log" || { echo "  !! 缺少超时日志"; FAILED=1; }
grep -q "monkey" "$TMP/calls" && { echo "  !! 超时后不应启动 App"; FAILED=1; }

echo "=== 场景6：未锁屏直接启动 ==="
reset_case
echo "false" > "$TMP/kg"
run_script env; rc=$?
echo "rc=$rc"; sed 's/^/  /' "$TMP/calls"
[ "$rc" -eq 0 ] || FAILED=1
grep -q "monkey" "$TMP/calls" || FAILED=1

echo "=== 场景7：包未安装，应退出 ==="
reset_case
echo "false" > "$TMP/kg"
PM_EMPTY=1 run_script env; rc=$?
echo "rc=$rc（期望非 0）"
[ "$rc" -ne 0 ] || FAILED=1

echo "=== 场景8：monkey 失败 → 回退 am start ==="
reset_case
echo "false" > "$TMP/kg"
MONKEY_RC=3 run_script env; rc=$?
echo "rc=$rc"; sed 's/^/  /' "$TMP/calls"
[ "$rc" -eq 0 ] || FAILED=1
grep -q "am start -n com.example.app/.MainActivity" "$TMP/calls" || { echo "  !! 未回退 am start"; FAILED=1; }

echo "=== 场景9：config 里配置 APP_ACTIVITY ==="
reset_case
echo "false" > "$TMP/kg"
sed -i 's|APP_ACTIVITY=""|APP_ACTIVITY="com.example.app/.Main"|' "$TMP/mod/config.sh"
run_script env; rc=$?
echo "rc=$rc"; sed 's/^/  /' "$TMP/calls"
[ "$rc" -eq 0 ] || FAILED=1
grep -q "am start -n com.example.app/.Main" "$TMP/calls" || { echo "  !! 未优先使用 APP_ACTIVITY"; FAILED=1; }

echo "== 清理 =="
rm -rf "$TMP"
if [ "$FAILED" -ne 0 ]; then
    echo "存在失败断言"
    exit 1
fi
echo "全部断言通过"
exit 0
