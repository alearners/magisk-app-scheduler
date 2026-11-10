#!/system/bin/sh
# ============================================================
#  AppTimer 配置导出（Magisk 版）
#
#  用法（root）：
#    sh /data/adb/modules/app_scheduler/config_export.sh
#
#  产物：/sdcard/AppTimer/apptimer-config-YYYYmmdd-HHMMSS.sh
#        就是一份带时间戳的 config.sh 副本，重装模块 / 换机后可直接导回。
#
#  说明：本文件含明文锁屏密码，导出后请注意保存位置，不要随手分享。
# ============================================================

MODDIR=${0%/*}
CONFIG="$MODDIR/config.sh"
BACKUP_DIR=${BACKUP_DIR:-/sdcard/AppTimer}

if [ ! -f "$CONFIG" ]; then
    echo "找不到 $CONFIG，模块可能没有正确安装"
    exit 1
fi

if [ ! -d "$BACKUP_DIR" ]; then
    mkdir -p "$BACKUP_DIR" || {
        echo "无法创建备份目录 $BACKUP_DIR"
        exit 1
    }
fi

STAMP=$(date '+%Y%m%d-%H%M%S')
OUT="$BACKUP_DIR/apptimer-config-$STAMP.sh"

# 先写临时文件再改名：中途失败时不会留下一个半截备份
if ! cat "$CONFIG" > "$OUT.tmp"; then
    rm -f "$OUT.tmp"
    echo "读取 config.sh 失败"
    exit 1
fi
mv "$OUT.tmp" "$OUT" || {
    rm -f "$OUT.tmp"
    echo "写入 $OUT 失败"
    exit 1
}
chmod 600 "$OUT" 2>/dev/null

SIZE=$(wc -c < "$OUT" 2>/dev/null | tr -d ' ')
LOG_FILE=$(. "$CONFIG" 2>/dev/null; echo "${LOG_FILE:-/data/local/tmp/apptimer.log}")
echo "[$(date '+%Y-%m-%d %H:%M:%S')] [config] 已导出配置 → $OUT（${SIZE:-?} 字节）" >> "$LOG_FILE" 2>/dev/null

echo "已导出配置："
echo "  $OUT"
echo "  ${SIZE:-?} 字节"
echo
echo "导入：sh $MODDIR/config_import.sh \"$OUT\""
