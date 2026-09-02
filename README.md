# HTML2APK — 手机上把 HTML 打包成 APK 的工具

> 在 Android 设备上**完全离线**地把单个 HTML 文件或整个网页文件夹打包成可安装的 APK。
> 无需电脑、无需 Termux、无需云端构建。

## 功能

- 📄 **两种输入**：单个 HTML 文件 / 文件夹（含 CSS、JS、图片、子目录，保持相对结构）
- 🎨 **自定义封面图标**：上传任意图片作为产物 APK 的桌面图标（自动中心裁剪 + 多密度生成）；不选则使用内置默认图标
- 🧱 **自包含构建引擎**：App 内置 arm64 静态 `aapt2` + `zipalign` + `apksig` 签名库 + 固定壳 `classes.dex`，全程在设备本地完成 compile → link → merge dex → align → sign
- 🎨 **工业复古控制台 UI**（WebView Hybrid，JS Bridge 调原生构建）
- 📦 **产物**：`Download/HTML2APK/<应用名>-v<版本>.apk`，v2/v3 签名，minSdk 24 / targetSdk 33
- 🚀 **一键安装**：产物支持直接拉起系统安装器

## 架构

```
┌─ HTML2APK App（com.html2apk.builder）───────────────────┐
│ WebView UI（assets/ui/：index.html + css + js）         │
│   ↕ window.html2apk.* JS Bridge                        │
│ 原生层：                                                │
│  · FileService     SAF 选文件/文件夹 → work/input       │
│  · BuildEngine     aapt2 → link → mergeDex → align →    │
│                    apksig 签名 → Download/HTML2APK/     │
│  · EngineManager   引擎资产版本同步 + 就绪验证           │
└─────────────────────────────────────────────────────────┘
引擎资产：
  lib/arm64-v8a/libaapt2.so / libzipalign.so   ← jniLibs（native 可执行区）
  assets/engine/  android.jar · template.dex · AndroidManifest.tpl.xml · VERSION
  app/libs/apksig-8.5.2.jar
```

### 核心设计决策（踩坑记录）

| 问题 | 方案 |
|---|---|
| Android 16 禁止从 app data 目录 exec ELF（无论 targetSdk） | aapt2/zipalign 打进 **jniLibs**（`lib/<abi>/`），运行时从 `applicationInfo.nativeLibraryDir` exec（系统安装时提取、不可写，放行执行） |
| AGP 8.x 只打包 `.so` 文件进 jniLibs | 二进制命名为 `libaapt2.so` / `libzipalign.so`（文件名不影响 exec），并 `keepDebugSymbols` 防 strip |
| aapt2 动态链接缺 lib 依赖 | 使用 lzhiyong/android-sdk-tools 的**静态 aarch64 版** |
| targetSdk 30+ 要求 `resources.arsc` 不压缩且 4 字节对齐 | link 加 `-0 arsc`；mergeDex 对 arsc 用 STORED 写入（预计算 CRC/size）；zipalign 对齐 |
| 资源名 `.keystore` 被 aapt2 剥离扩展名 | 密钥资源实际名 `raw/release`，用 `R.raw.release` 编译期常量引用 |
| 部分 Android 系统/ROM 的 BouncyCastle 裁剪 PBE 算法，运行时 `KeyStore("PKCS12")` 解析内置 keystore 抛 `No installed provider supports this key: ...PKCS12Key` | 密钥改为**打包 PKCS8 DER 私钥 + X.509 DER 证书**（raw/release_key、release_cert），运行时用 `KeyFactory`/`CertificateFactory` 直接构建，只依赖 Conscrypt（全设备可用） |
| `pm install -r` 升级后残留旧引擎资产 | `assets/engine/VERSION` 版本号机制，不一致全量刷新 |

## 目录结构

```
html2apk/
├── app/                        # App 本体（Kotlin，AGP 8.8.2）
│   ├── src/main/
│   │   ├── java/com/html2apk/builder/
│   │   │   ├── MainActivity.kt         # WebView 壳 + JS Bridge
│   │   │   └── engine/
│   │   │       ├── EngineManager.kt    # 资产版本同步/就绪验证
│   │   │       ├── BuildEngine.kt      # 构建流水线
│   │   │       ├── ManifestGenerator.kt# manifest/res 生成
│   │   │       └── ApkSignerRunner.kt  # apksig v2/v3 签名
│   │   ├── assets/
│   │   │   ├── ui/                     # Hybrid UI（工业复古控制台风）
│   │   │   └── engine/                 # android.jar / template.dex / manifest 模板 / VERSION
│   │   ├── jniLibs/arm64-v8a/          # libaapt2.so / libzipalign.so
│   │   └── res/raw/                   # 内置默认签名密钥：release_key(PKCS8 DER) + release_cert(X.509 DER)
│   └── libs/apksig-8.5.2.jar
├── shell-tpl/                  # 产物壳源码（Java 8，预编译 classes.dex）
│   ├── src/com/html2apk/shell/MainActivity.java
│   ├── Config.java
│   ├── AndroidManifest.tpl.xml
│   └── build-dex.sh            # javac + d8 → classes.dex
└── build-tools/collect-engine.sh  # 引擎资产采集脚本
```

## 开发期构建（在设备上重新编译 App 本体）

```bash
# 需要：proot Ubuntu 环境 + JDK17 + 工作区 Gradle/SDK
cd /storage/emulated/0/WORK/01-开发项目/html2apk
export GRADLE_USER_HOME=/root/.gradle        # sdcard noexec，必须放可执行分区
bash gradlew :app:assembleDebug \
  -Pandroid.aapt2FromMavenOverride=/root/aapt2-bin/aapt2   # arm64 静态 aapt2
# 产物：app/build/outputs/apk/debug/app-debug.apk
```

### 更新引擎资产

```bash
bash build-tools/collect-engine.sh   # 从工作区工具链采集 → jniLibs + assets
# 若 assets/engine 内容有变化，记得递增 assets/engine/VERSION，App 升级后会自动刷新
```

## 使用

1. 打开 HTML2APK → 选择「HTML 文件」或「文件夹」（SAF）
2. 填写应用名称（必填）、包名（留空自动生成）、版本号；可选上传一张封面图标图片
3. 点「开始构建」→ 终端日志实时输出 → 完成后产物在 `Download/HTML2APK/`
4. 点「安装 APK」直接拉起系统安装器

## 产物壳说明（shell-tpl）

- `MainActivity`：WebView 加载 `assets/` 下 HTML（入口见 `ht2a.config`，默认 `index.html`）
- 支持 `ht2a.config` 配置状态栏/导航栏/背景色
- 产物包名/应用名/图标由构建时动态生成，壳代码本身固定不变

## 已知限制（v1）

- 仅 arm64 设备（引擎二进制为 aarch64 静态编译）
- 面向经典 HTML（file:// 加载）；ES Module / 远程 URL 打包列为 v2
- 默认内置签名密钥（仅供个人分发；自定义密钥导入为 v2 功能）
- 产物 minSdk 24（Android 7.0+）
