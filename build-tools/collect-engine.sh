#!/usr/bin/env bash
# collect-engine.sh —— 从工作区工具链采集引擎资产到 App 工程
# 布局（Android 16 起禁止从 data 目录 exec ELF）：
#   - 可执行文件（aapt2、zipalign）→ app/src/main/jniLibs/arm64-v8a/
#     （打包进 APK lib/<abi>/，运行时从 applicationInfo.nativeLibraryDir exec）
#   - 数据资产（android.jar、template.dex、manifest 模板）→ app/src/main/assets/engine/
#   - apksig jar → app/libs/
# 用法：bash collect-engine.sh
set -e

HERE="$(cd "$(dirname "$0")" && pwd)"
APP="$HERE/../app/src/main"
ENGINE="$APP/assets/engine"
JNI="$APP/jniLibs/arm64-v8a"
SDK_TOOLS="/storage/emulated/0/WORK/02-工具链/toolchain/sdk-tools-aarch64"
ANDROID_JAR="/storage/emulated/0/WORK/02-工具链/android-sdk/platforms/android-36/android.jar"
APKSIG="/storage/emulated/0/WORK/02-工具链/toolchain/apksig-m1/apksig-8.5.2.jar"
SHELL_TPL="$HERE/../shell-tpl"

mkdir -p "$ENGINE" "$JNI" "$APP/../libs"

echo "==> aapt2 (jniLibs)"
cp "$SDK_TOOLS/build-tools/aapt2" "$JNI/aapt2"
echo "==> zipalign (jniLibs)"
cp "$SDK_TOOLS/build-tools/zipalign" "$JNI/zipalign"
echo "==> android.jar (assets)"
cp "$ANDROID_JAR" "$ENGINE/android.jar"
echo "==> apksig (libs)"
cp "$APKSIG" "$APP/../libs/apksig-8.5.2.jar"

echo "==> 编译壳模板 classes.dex"
bash "$SHELL_TPL/build-dex.sh" "$ANDROID_JAR" \
    "/storage/emulated/0/WORK/02-工具链/android-sdk/build-tools/35.0.0/d8" >/dev/null 2>&1 || {
  echo "!! build-dex 失败，使用已有 dex" >&2
}
cp "$SHELL_TPL/out/classes.dex" "$ENGINE/template.dex"

echo "==> manifest 模板"
cp "$SHELL_TPL/AndroidManifest.tpl.xml" "$ENGINE/AndroidManifest.tpl.xml"

echo "DONE. engine assets:"
ls -la "$ENGINE"
echo "--- jniLibs:"
ls -la "$JNI"