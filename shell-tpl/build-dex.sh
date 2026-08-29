#!/usr/bin/env bash
# build-dex.sh —— 在开发期把固定壳 Java 源码编译为 classes.dex
# 用法：bash build-dex.sh <android.jar 路径> <d8 路径>
# 示例：bash build-dex.sh /storage/emulated/0/WORK/02-工具链/android-sdk/platforms/android-36/android.jar /storage/emulated/0/WORK/02-工具链/android-sdk/build-tools/35.0.0/d8
set -e

HERE="$(cd "$(dirname "$0")" && pwd)"
ANDROID_JAR="${1:?usage: build-dex.sh <android.jar> <d8>}"
D8="${2:?usage: build-dex.sh <android.jar> <d8>}"
OUT="$HERE/out"

rm -rf "$OUT"
mkdir -p "$OUT/classes"

echo "[1/3] javac..."
javac -source 8 -target 8 -bootclasspath "$ANDROID_JAR" \
    -d "$OUT/classes" \
    "$HERE/src/com/html2apk/shell/MainActivity.java" \
    "$HERE/src/com/html2apk/shell/Config.java"

echo "[2/3] d8..."
# 收集所有编译出的 .class（含匿名内部类 MainActivity$1.class 等）
CLASSES=$(find "$OUT/classes" -name '*.class' | tr '\n' ' ')
bash "$D8" --release --lib "$ANDROID_JAR" --min-api 24 \
    --output "$OUT" $CLASSES

echo "[3/3] done: $OUT/classes.dex"
ls -la "$OUT/classes.dex"
