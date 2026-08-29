package com.html2apk.builder.engine

import android.content.Context
import android.util.Log
import java.io.File

/**
 * 引擎资产管理：
 * - 可执行文件（aapt2、zipalign）打包在 APK 的 jniLibs（lib/<abi>/），
 *   运行时从 applicationInfo.nativeLibraryDir 定位。
 *   注意：Android 15+/16 禁止从 app data 目录（filesDir）exec ELF，
 *   而 native lib 目录由系统安装时提取、不可写，是唯一允许 exec 的位置。
 * - 数据资产（android.jar、template.dex、manifest 模板）仍从 assets 解压到私有目录。
 */
class EngineManager(private val ctx: Context) {

    val engineDir: File = File(ctx.filesDir, "engine")
    val workDir: File = File(ctx.filesDir, "work")
    val outDir: File = File(ctx.filesDir, "out")

    /** native lib 目录（/data/app/.../lib/<abi>，系统安装时解压，不可写） */
    private val nativeLibDir: File = File(ctx.applicationInfo.nativeLibraryDir)

    val aapt2: File get() = File(nativeLibDir, "libaapt2.so")
    val zipalign: File get() = File(nativeLibDir, "libzipalign.so")
    val androidJar: File get() = File(engineDir, "android.jar")
    val templateDex: File get() = File(engineDir, "template.dex")
    val manifestTpl: File get() = File(engineDir, "AndroidManifest.tpl.xml")

    private val locked = Any()

    /** 引擎资产版本号（与 assets/engine/VERSION 一致；引擎资产更新时递增，升级安装后强制刷新） */
    private val assetVersion: String =
        try {
            ctx.assets.open("engine/VERSION").use { it.bufferedReader().readText().trim() }
        } catch (e: Exception) {
            "0"
        }

    /** 确保引擎资产就绪；返回是否成功 */
    fun ensureEngine(): Boolean {
        synchronized(locked) {
            try {
                // 可执行文件必须来自 native lib 目录（Android 16 起 data 目录禁止 exec）
                if (!aapt2.exists() || !aapt2.canExecute()) {
                    Log.e(TAG, "native aapt2 not available: ${aapt2.absolutePath} (nativeLibDir=$nativeLibDir)")
                    return false
                }

                // 数据资产（只读，无 exec 需求）——版本变化时全量刷新，
                // 避免 pm install -r 升级后残留旧模板/旧资产
                val versionFile = File(engineDir, "VERSION")
                val needRefresh = !versionFile.exists() || versionFile.readText().trim() != assetVersion
                if (needRefresh) {
                    engineDir.deleteRecursively()
                    engineDir.mkdirs()
                    extractAsset("engine/android.jar", androidJar)
                    extractAsset("engine/template.dex", templateDex)
                    extractAsset("engine/AndroidManifest.tpl.xml", manifestTpl)
                    versionFile.writeText(assetVersion)
                    Log.i(TAG, "engine assets refreshed to version=$assetVersion")
                }

                // 真实验证 aapt2 可执行
                val ok = runAaptVersion()
                Log.i(TAG, "engine ready, aapt2=$ok nativeLib=$nativeLibDir")
                return ok
            } catch (e: Exception) {
                Log.e(TAG, "engine init failed", e)
                return false
            }
        }
    }

    private fun extractAsset(assetPath: String, dest: File) {
        ctx.assets.open(assetPath).use { input ->
            dest.outputStream().use { output -> input.copyTo(output) }
        }
    }

    private fun runAaptVersion(): Boolean {
        return try {
            val p = ProcessBuilder(aapt2.absolutePath, "version")
                .redirectErrorStream(true)
                .start()
            val out = p.inputStream.readBytes().toString(Charsets.UTF_8)
            p.waitFor()
            Log.i(TAG, "aapt2 version: $out")
            p.exitValue() == 0
        } catch (e: Exception) {
            Log.e(TAG, "aapt2 exec failed", e)
            false
        }
    }

    companion object {
        private const val TAG = "HTML2APK.Engine"
    }
}