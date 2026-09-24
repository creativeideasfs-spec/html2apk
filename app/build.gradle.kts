plugins {
  id("com.android.application")
  id("org.jetbrains.kotlin.android")
}

android {
  namespace = "com.html2apk.builder"
  compileSdk = 35

  defaultConfig {
    applicationId = "com.html2apk.builder"
    minSdk = 26
    // targetSdk 34: 与 dsh-mobile-apk 一致，避免 Android 15+ 对 exec app-data ELF 的限制
    targetSdk = 34
    versionCode = 6
    versionName = "1.1.1"
  }

  buildFeatures {
    buildConfig = true
  }

  androidResources {
    // 引擎数据资产不压缩，保证 exec 可用
    noCompress += listOf("so", "dex", "jar", "arsc", "png")
  }

  packaging {
    jniLibs {
      // 强制 native lib 解压为实体文件（extractNativeLibs=true）。
      // aapt2/zipalign 需要从 nativeLibraryDir exec，必须落盘。
      useLegacyPackaging = true
      // 静态链接的引擎二进制，防止 AGP strip 破坏
      keepDebugSymbols += listOf("**/libaapt2.so", "**/libzipalign.so")
    }
  }

  signingConfigs {
    create("repoDebug") {
      storeFile = rootProject.file("../dsh-mobile-apk/keystore/debug.keystore")
      storePassword = "android"
      keyAlias = "androiddebugkey"
      keyPassword = "android"
    }
  }

  buildTypes {
    release {
      isMinifyEnabled = false
      signingConfig = signingConfigs.getByName("repoDebug")
    }
    debug {
      signingConfig = signingConfigs.getByName("repoDebug")
    }
  }

  lint {
    checkReleaseBuilds = false
    abortOnError = false
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
  kotlinOptions {
    jvmTarget = "17"
  }
}

dependencies {
  implementation("androidx.activity:activity-ktx:1.10.1")
  implementation("androidx.core:core-ktx:1.15.0")
  // apksig 纯 Java 签名库（本地 jar，避免 Maven 依赖解析问题）
  implementation(files("libs/apksig-8.5.2.jar"))
}
