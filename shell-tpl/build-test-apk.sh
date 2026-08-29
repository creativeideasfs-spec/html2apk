#!/usr/bin/env bash
# build-test-apk.sh —— 开发期：用壳模板构建一个完整测试 APK 并签名
# 依赖：/data/local/tmp/h2a/{aapt2,zipalign}（Android shell 已复制）
#        apksig-m1/ 下的 ApkSignerTest（proot JDK）
set -e

HERE="$(cd "$(dirname "$0")" && pwd)"
TEST="$HERE/test"
ANDROID_JAR="/storage/emulated/0/WORK/02-工具链/android-sdk/platforms/android-36/android.jar"
SIGN_DIR="/storage/emulated/0/WORK/02-工具链/toolchain/apksig-m1"
KEYSTORE="/storage/emulated/0/WORK/01-开发项目/dsh-mobile-apk/keystore/debug.keystore"
PKG="${1:-com.html2apk.test}"

echo "==> 生成 manifest（包名 $PKG）"
sed -e "s/__PACKAGE__/$PKG/g" -e 's/__VERSION_CODE__/1/g' -e 's/__VERSION_NAME__/1.0/g' \
    "$HERE/AndroidManifest.tpl.xml" > "$TEST/AndroidManifest.xml"

echo "==> 编译 dex（若缺失）"
if [ ! -f "$HERE/out/classes.dex" ]; then
  bash "$HERE/build-dex.sh" "$ANDROID_JAR" "/storage/emulated/0/WORK/02-工具链/android-sdk/build-tools/35.0.0/d8"
fi

echo "==> 准备设备构建目录"
adb shell "mkdir -p /data/local/tmp/h2a/buildtest"
adb push "$TEST/assets" /data/local/tmp/h2a/buildtest/assets >/dev/null 2>&1 || \
  cp -r "$TEST/assets" /data/local/tmp/h2a/buildtest/ 2>/dev/null || true

echo "==> 设备上 aapt2 compile + link"
adb shell "cd /data/local/tmp/h2a/buildtest && rm -f res.zip unsigned.apk aligned.apk && \
  /data/local/tmp/h2a/aapt2 compile --dir /storage/emulated/0/WORK/01-开发项目/html2apk/shell-tpl/test/res -o res.zip && \
  /data/local/tmp/h2a/aapt2 link -o unsigned.apk -I /storage/emulated/0/WORK/02-工具链/android-sdk/platforms/android-36/android.jar \
    --manifest /storage/emulated/0/WORK/01-开发项目/html2apk/shell-tpl/test/AndroidManifest.xml res.zip \
    -A /storage/emulated/0/WORK/01-开发项目/html2apk/shell-tpl/test/assets \
    --min-sdk-version 24 --target-sdk-version 33 --version-code 1 --version-name 1.0 --auto-add-overlay && \
  echo LINK_OK"

echo "==> 合并 classes.dex"
cd "$TEST"
python3 - <<PY
import zipfile
src='/data/local/tmp/h2a/buildtest/unsigned.apk'
dst='withdex.apk'
with zipfile.ZipFile(src) as zin, zipfile.ZipFile(dst,'w',zipfile.ZIP_DEFLATED) as zout:
    for item in zin.infolist():
        zout.writestr(item, zin.read(item.filename))
    with open('$HERE/out/classes.dex','rb') as f:
        zout.writestr('classes.dex', f.read())
print('withdex written')
PY

echo "==> 设备上 zipalign"
cp withdex.apk /data/local/tmp/h2a/buildtest/withdex.apk
adb shell "cd /data/local/tmp/h2a/buildtest && /data/local/tmp/h2a/zipalign -f 4 withdex.apk aligned.apk && echo ALIGN_OK"
cp /data/local/tmp/h2a/buildtest/aligned.apk aligned.apk

echo "==> apksig 签名"
(cd "$SIGN_DIR" && java -cp apksig-8.5.2.jar:. ApkSignerTest \
  "$TEST/aligned.apk" "$TEST/shell-test.apk" "$KEYSTORE" android)

echo "==> 验证签名"
(cd "$SIGN_DIR" && java -cp apksig-8.5.2.jar:. ApkVerifyTest "$TEST/shell-test.apk")

echo "DONE: $TEST/shell-test.apk"