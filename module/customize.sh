#!/system/bin/sh
# ============================================================
#  定时启动 App —— 安装阶段脚本（由 Magisk 安装器调用）
#  作用：统一设置目录与脚本权限，避免打包时权限丢失
# ============================================================

ui_print "- 定时启动 App v1.4"
ui_print "- 正在设置模块文件权限"

set_perm_recursive "$MODPATH" 0 0 0755 0644

for f in "$MODPATH"/*.sh; do
    [ -f "$f" ] && set_perm "$f" 0 0 0755
done

ui_print "- 已设置 *.sh 权限为 755"
ui_print "- 下一步：编辑 /data/adb/modules/app_scheduler/config.sh"
ui_print "  填写 APP_PKG（目标应用包名）与 PASSWORD，然后重启设备"
ui_print "- 修改调度后执行：su -c sh /data/adb/modules/app_scheduler/service.sh"
ui_print "- 立即执行一次：su -c sh /data/adb/modules/app_scheduler/run_app.sh"
