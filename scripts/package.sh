#!/system/bin/sh
# ============================================================
#  AppTimer 打包脚本（与 GitHub Actions 的构建流程等价，仅用于自查）
#
#  用法：仓库根目录执行  sh scripts/package.sh
#  产物（统一命名，方便直接拖进 Release）：
#    build/apptimer-magisk-vX.zip           Magisk 模块包
#    build/apptimer-lsposed-vX.apk          LSPosed APK（本机有 gradle 时才会生成）
#    build/<产物>.sha256
#
#  两条支路（main / lsposed）用同一套命名与同一把发布签名：
#    · APK 签名取环境变量 KEYSTORE_PATH / KEYSTORE_PASSWORD / KEY_ALIAS / KEY_PASSWORD，
#      与 CI 的 LSPOSED_* Secret 一一对应；
#    · 模块 zip 是 Magisk 安装包，不做签名（Magisk 不校验模块签名）。
# ============================================================

set -e

ROOT=$(cd "$(dirname "$0")/.." && pwd)
cd "$ROOT"

PROP=module/module.prop
MODULE_VERSION=$(grep -m1 '^version=' "$PROP" | cut -d= -f2- | tr -d '\r' | sed 's/^v//')
ZIP_NAME="apptimer-magisk-v${MODULE_VERSION}.zip"

APK_VERSION=""
if [ -f lsposed/app/build.gradle ]; then
    APK_VERSION=$(grep -m1 'versionName' lsposed/app/build.gradle | sed -E 's/.*"([^"]+)".*/\1/')
fi
APK_NAME="apptimer-lsposed-v${APK_VERSION}.apk"

echo "== 模块版本 v${MODULE_VERSION} / APK 版本 ${APK_VERSION:-不在本分支} =="

echo "== 校验必需文件 =="
for f in module/module.prop module/config.sh module/service.sh module/run_app.sh \
         module/customize.sh module/uninstall.sh module/action.sh \
         module/META-INF/com/google/android/update-binary; do
    [ -f "$f" ] || { echo "缺少文件: $f"; exit 1; }
    echo "  ok $f"
done

echo "== 检查换行符（禁止 CRLF）=="
if grep -rlU $'\r' module >/dev/null 2>&1; then
    echo "发现 CRLF，已就地修正为 LF"
    find module -type f \( -name '*.sh' -o -name '*.prop' -o -name 'update-binary' -o -name 'updater-script' -o -name '*.md' \) -print0 \
        | xargs -0 -r sed -i 's/\r$//'
fi

echo "== 设置脚本权限 755 =="
find module -type f -name '*.sh' -print0 | xargs -0 -r chmod 0755
chmod 0755 module/META-INF/com/google/android/update-binary

echo "== sh -n 语法检查 =="
for f in module/*.sh; do
    sh -n "$f" && echo "  ok $f"
done

mkdir -p build
rm -f "build/${ZIP_NAME}"
( cd module && zip -r -X "../build/${ZIP_NAME}" . -x '*.DS_Store' -x '*/.git/*' )

if [ -n "$APK_VERSION" ]; then
    if command -v gradle >/dev/null 2>&1; then
        echo "== 构建 APK（发布签名）=="
        gradle -p lsposed :app:assembleRelease --no-daemon
        APK=$(find lsposed/app/build/outputs/apk/release -name '*.apk' | head -n 1)
        cp "$APK" "build/${APK_NAME}"
    else
        echo "!! 本机没有 gradle，跳过 APK 构建（仅生成模块包）"
    fi
fi

echo "== 产物 =="
ls -lh build/
if command -v sha256sum >/dev/null 2>&1; then
    for f in "build/${ZIP_NAME}" "build/${APK_NAME}"; do
        [ -f "$f" ] && sha256sum "$f" > "$f.sha256" && cat "$f.sha256"
    done
fi
