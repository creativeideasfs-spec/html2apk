package com.html2apk.builder.engine

import android.content.Context
import android.os.Environment
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * 核心构建流水线：
 * 1. 准备输入（HTML 已由 FileService 拷贝到 workDir/input）
 * 2. 生成 manifest + res
 * 3. aapt2 compile + link
 * 4. 合并 template.dex
 * 5. zipalign
 * 6. apksig 签名
 * 7. 输出到 Download/HTML2APK/
 */
object BuildEngine {

    sealed class Result {
        data class Success(val apkPath: String, val sizeLabel: String) : Result()
        data class Failure(val message: String) : Result()
    }

    private const val TAG = "HTML2APK.Build"

    fun build(
        ctx: Context,
        engine: EngineManager,
        cfg: BuildConfig2,
        progress: (String, String) -> Unit
    ): Result {
        try {
            // 0. 引擎就绪
            progress("init", "初始化构建引擎…")
            if (!engine.ensureEngine()) {
                return Result.Failure("构建引擎初始化失败（aapt2 不可用）")
            }

            // 1. 工作目录
            val work = engine.workDir
            val buildDir = File(work, "build_${System.currentTimeMillis()}")
            buildDir.mkdirs()
            val resDir = File(buildDir, "res")
            val assetsDir = File(buildDir, "assets")

            // 2. 拷贝输入 HTML 到 assets
            progress("input", "拷贝 HTML 资源…")
            copyInput(cfg.inputDir, assetsDir)

            // 3. 生成 manifest + res
            progress("config", "生成配置与资源…")
            ManifestGenerator.generateManifest(engine.manifestTpl, cfg, File(buildDir, "AndroidManifest.xml"))
            ManifestGenerator.generateStringsXml(resDir, cfg.appName)
            ManifestGenerator.generateDefaultIcon(resDir)

            // 确定入口 HTML（优先 index.html；单文件输入已被重命名；否则取 assets 下第一个 .html）
            val entry = resolveEntryFile(assetsDir, cfg.entryFile)
                ?: return Result.Failure("未找到 HTML 入口文件（assets 目录无 .html）")
            val entryName = entry.name

            // 生成 ht2a.config（壳可选配置）
            File(assetsDir, "ht2a.config").writeText(
                "entryFile=$entryName\n" +
                    "statusBarColor=${cfg.statusBarColor}\n" +
                    "navBarColor=${cfg.navBarColor}\n" +
                    "backgroundColor=${cfg.backgroundColor}\n",
                Charsets.UTF_8
            )

            // 4. aapt2 compile
            progress("compile", "编译资源…")
            val resZip = File(buildDir, "res.zip")
            val compileCmd = arrayOf(
                engine.aapt2.absolutePath, "compile", "--dir", resDir.absolutePath, "-o", resZip.absolutePath
            )
            val compileOut = exec(compileCmd, buildDir)
            if (compileOut.exitCode != 0 || !resZip.exists()) {
                return Result.Failure("资源编译失败:\n${compileOut.output.takeLast(2000)}")
            }

            // 5. aapt2 link
            progress("link", "链接 APK…")
            val unsignedApk = File(buildDir, "unsigned.apk")
            val linkCmd = arrayOf(
                engine.aapt2.absolutePath, "link",
                "-o", unsignedApk.absolutePath,
                "-I", engine.androidJar.absolutePath,
                "-0", "arsc",
                "--manifest", File(buildDir, "AndroidManifest.xml").absolutePath,
                resZip.absolutePath,
                "-A", assetsDir.absolutePath,
                "--min-sdk-version", "24",
                "--target-sdk-version", "33",
                "--version-code", cfg.versionCode.toString(),
                "--version-name", cfg.versionName,
                "--auto-add-overlay"
            )
            val linkOut = exec(linkCmd, buildDir)
            if (linkOut.exitCode != 0 || !unsignedApk.exists()) {
                return Result.Failure("APK 链接失败:\n${linkOut.output.takeLast(2000)}")
            }

            // 6. 合并 template.dex
            progress("dex", "合并壳代码…")
            val withDex = File(buildDir, "withdex.apk")
            mergeDex(unsignedApk, engine.templateDex, withDex)

            // 7. zipalign
            progress("align", "对齐优化…")
            val alignedApk = File(buildDir, "aligned.apk")
            val alignOut = exec(
                arrayOf(engine.zipalign.absolutePath, "-f", "4", withDex.absolutePath, alignedApk.absolutePath),
                buildDir
            )
            if (alignOut.exitCode != 0 || !alignedApk.exists()) {
                return Result.Failure("对齐失败:\n${alignOut.output.takeLast(1000)}")
            }

            // 8. 签名（apksig 库，App 内调用）
            progress("sign", "签名…")
            val signedApk = File(buildDir, "signed.apk")
            val signResult = ApkSignerRunner.sign(ctx, alignedApk, signedApk)
            if (signResult != null) {
                return Result.Failure("签名失败: $signResult")
            }

            // 9. 输出到 Download/HTML2APK/
            progress("output", "输出 APK…")
            val outDir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                "HTML2APK"
            )
            outDir.mkdirs()
            val safeName = cfg.appName.replace(Regex("[\\\\/:*?\"<>|]"), "_")
            val outApk = File(outDir, "${safeName}-v${cfg.versionName}.apk")
            signedApk.copyTo(outApk, overwrite = true)

            val sizeLabel = formatSize(outApk.length())
            progress("done", "构建完成: $sizeLabel")
            return Result.Success(outApk.absolutePath, sizeLabel)
        } catch (e: Exception) {
            Log.e(TAG, "build failed", e)
            return Result.Failure("构建异常: ${e.message}")
        }
    }

    private fun copyInput(inputDir: String, assetsDir: File) {
        assetsDir.mkdirs()
        val src = File(inputDir)
        if (!src.exists()) return
        // 单个 HTML 文件 → 统一重命名为 index.html 作为入口（保证 entryFile 命中）
        if (src.isFile) {
            src.copyTo(File(assetsDir, "index.html"), overwrite = true)
            return
        }
        // 文件夹 → 递归拷贝，保持相对结构
        src.copyRecursively(assetsDir, overwrite = true)
    }

    /** 解析入口 HTML：优先 index.html；否则取 assets 下第一个 .html */
    private fun resolveEntryFile(assetsDir: File, preferred: String): File? {
        val pref = File(assetsDir, preferred)
        if (pref.isFile) return pref
        val found = assetsDir.walkTopDown()
            .filter { it.isFile && it.extension.equals("html", ignoreCase = true) }
            .toList()
            .sortedBy { it.name }
            .firstOrNull()
        return found
    }

    private fun mergeDex(apk: File, dex: File, out: File) {
        ZipFile(apk).use { zin ->
            ZipOutputStream(out.outputStream().buffered()).use { zout ->
                val entries = zin.entries()
                while (entries.hasMoreElements()) {
                    val e = entries.nextElement()
                    val ne = ZipEntry(e.name)
                    ne.time = e.time
                    if (e.name == "resources.arsc") {
                        // targetSdk 30+ 要求 resources.arsc 不压缩（STORED），
                        // 后续 zipalign 负责 4 字节对齐
                        val data = zin.getInputStream(e).use { it.readBytes() }
                        ne.method = ZipEntry.STORED
                        ne.size = data.size.toLong()
                        ne.compressedSize = data.size.toLong()
                        ne.crc = CRC32().apply { update(data) }.value
                        zout.putNextEntry(ne)
                        zout.write(data)
                    } else {
                        // 其余条目统一 DEFLATED 重写（HTML 等 assets 可压缩）
                        ne.method = ZipEntry.DEFLATED
                        zout.putNextEntry(ne)
                        zin.getInputStream(e).use { it.copyTo(zout) }
                    }
                    zout.closeEntry()
                }
                // 追加壳代码 classes.dex（压缩存储即可，DEX 无对齐要求）
                val dexData = dex.readBytes()
                val dNe = ZipEntry("classes.dex")
                dNe.time = System.currentTimeMillis()
                dNe.method = ZipEntry.DEFLATED
                zout.putNextEntry(dNe)
                zout.write(dexData)
                zout.closeEntry()
            }
        }
    }

    private fun formatSize(bytes: Long): String {
        return when {
            bytes >= 1024 * 1024 -> String.format(Locale.US, "%.1f MB", bytes / 1024.0 / 1024.0)
            bytes >= 1024 -> String.format(Locale.US, "%.1f KB", bytes / 1024.0)
            else -> "$bytes B"
        }
    }

    private data class ExecResult(val exitCode: Int, val output: String)

    private fun exec(cmd: Array<String>, cwd: File): ExecResult {
        val pb = ProcessBuilder(*cmd)
        pb.directory(cwd)
        pb.redirectErrorStream(true)
        val p = pb.start()
        val output = p.inputStream.readBytes().toString(Charsets.UTF_8)
        val exit = p.waitFor()
        Log.i(TAG, "exec ${cmd[cmd.size - 1]} exit=$exit")
        return ExecResult(exit, output)
    }
}