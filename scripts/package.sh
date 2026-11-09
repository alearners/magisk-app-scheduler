#!/system/bin/sh
# ============================================================
#  本地打包脚本（与 GitHub Actions 的 build.yml 等价，仅用于自查）
#  用法：仓库根目录执行 sh scripts/package.sh
#  产物：build/<module-id>-<version>.zip
# ============================================================

set -e

ROOT=$(cd "$(dirname "$0")/.." && pwd)
cd "$ROOT"

PROP=module/module.prop
MODULE_ID=$(grep -m1 '^id=' "$PROP" | cut -d= -f2- | tr -d '\r')
VERSION=$(grep -m1 '^version=' "$PROP" | cut -d= -f2- | tr -d '\r')
ZIP_NAME="${MODULE_ID}-${VERSION}.zip"

echo "== 读取 module.prop =="
cat "$PROP"

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

echo "== 产物 =="
ls -lh "build/${ZIP_NAME}"
if command -v sha256sum >/dev/null 2>&1; then
    sha256sum "build/${ZIP_NAME}" > "build/${ZIP_NAME}.sha256"
    cat "build/${ZIP_NAME}.sha256"
fi
