#!/system/bin/sh
# ============================================================
#  定时启动 App —— 卸载清理脚本
#  模块被移除时由 Magisk 执行。停止 crond 并清理运行时文件，
#  日志保留（如需一并删除，手动执行 rm -f /data/local/tmp/app_scheduler.log*）
# ============================================================

export PATH=/data/adb/magisk:/system/bin:/system/xbin:/sbin:/vendor/bin:/product/bin

pkill crond 2>/dev/null
sleep 1
rm -rf /data/local/tmp/app_scheduler.lock /data/local/tmp/app_scheduler.lock.d
echo "[$(date '+%Y-%m-%d %H:%M:%S')] [uninstall] 已停止 crond 并清理运行时文件" >> /data/local/tmp/app_scheduler.log
