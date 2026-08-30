package com.html2apk.builder.engine

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.util.Base64
import java.io.ByteArrayOutputStream
import java.io.File

/** 生成 AndroidManifest.xml 与 res 资源 */
object ManifestGenerator {

    /** 生成 AndroidManifest.xml（文本形式，供 aapt2 link 使用） */
    fun generateManifest(tpl: File, cfg: BuildConfig2, dest: File): File {
        val content = tpl.readText(Charsets.UTF_8)
            .replace("__PACKAGE__", cfg.packageName)
            .replace("__VERSION_CODE__", cfg.versionCode.toString())
            .replace("__VERSION_NAME__", cfg.versionName)
        dest.parentFile?.mkdirs()
        dest.writeText(content, Charsets.UTF_8)
        return dest
    }

    /** 生成 res/values/strings.xml（app_name） */
    fun generateStringsXml(resDir: File, appName: String): File {
        val valuesDir = File(resDir, "values")
        valuesDir.mkdirs()
        val f = File(valuesDir, "strings.xml")
        // 转义 XML 特殊字符（元素文本中引号无需转义）
        val escaped = appName
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
        f.writeText(
            "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                "<resources>\n" +
                "    <string name=\"app_name\">$escaped</string>\n" +
                "</resources>\n",
            Charsets.UTF_8
        )
        return f
    }

    /**
     * 生成启动图标（多密度 mipmap PNG，供 @mipmap/ic_launcher 引用）。
     *
     * @param iconPath 用户自定义封面图片路径；为空/解码失败时使用默认程序化图标
     * @return true = 使用了自定义图片，false = 默认图标
     */
    fun generateIconSet(resDir: File, iconPath: String?): Boolean {
        val src = if (iconPath.isNullOrBlank()) null else try {
            BitmapFactory.decodeFile(iconPath)
        } catch (e: Exception) {
            null
        }
        val square = src?.let { centerCrop(it) }

        // 标准 launcher 密度：mdpi48 / hdpi72 / xhdpi96 / xxhdpi144 / xxxhdpi192
        val densities = mapOf(
            "mdpi" to 48, "hdpi" to 72, "xhdpi" to 96,
            "xxhdpi" to 144, "xxxhdpi" to 192
        )
        for ((density, size) in densities) {
            val dir = File(File(resDir, "mipmap-$density"), "ic_launcher.png")
            dir.parentFile?.mkdirs()
            val bmp = if (square != null) Bitmap.createScaledBitmap(square, size, size, true)
            else drawDefaultIcon(size)
            dir.writeBytes(toPng(bmp))
        }
        // 无密度兜底（部分旧 launcher 直接查 mipmap/）
        val base = File(File(resDir, "mipmap"), "ic_launcher.png")
        base.parentFile?.mkdirs()
        val bmpBase = if (square != null) Bitmap.createScaledBitmap(square, 48, 48, true)
        else drawDefaultIcon(48)
        base.writeBytes(toPng(bmpBase))

        return square != null
    }

    /**
     * 生成封面预览 data URI（128px PNG base64），供 WebView UI 显示。
     * 返回 null 表示无法生成（图标文件缺失或无法解码）。
     */
    fun makePreviewDataUri(iconPath: String): String? {
        if (iconPath.isBlank()) return null
        val src = try { BitmapFactory.decodeFile(iconPath) } catch (e: Exception) { null } ?: return null
        val side = 128
        val square = centerCrop(src)
        val thumb = Bitmap.createScaledBitmap(square, side, side, true)
        val bos = ByteArrayOutputStream()
        thumb.compress(Bitmap.CompressFormat.PNG, 100, bos)
        return "data:image/png;base64," + Base64.encodeToString(bos.toByteArray(), Base64.NO_WRAP)
    }

    /** 中心裁剪为正方形 */
    private fun centerCrop(src: Bitmap): Bitmap {
        val side = minOf(src.width, src.height)
        val x = (src.width - side) / 2
        val y = (src.height - side) / 2
        return Bitmap.createBitmap(src, x, y, side, side)
    }

    /** 程序化绘制默认图标（深蓝底 + 三横线，与旧矢量外观一致） */
    private fun drawDefaultIcon(size: Int): Bitmap {
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val bg = Paint().apply { color = 0xFF0B1020.toInt(); style = Paint.Style.FILL }
        val fg = Paint().apply { color = 0xFF4C8DFF.toInt(); style = Paint.Style.FILL }
        c.drawRect(0f, 0f, size.toFloat(), size.toFloat(), bg)
        val s = size / 108f
        // 对应旧 vector：三条横线（第 3 条较短）
        c.drawRect(20f * s, 26f * s, 88f * s, 35f * s, fg)
        c.drawRect(20f * s, 49f * s, 88f * s, 58f * s, fg)
        c.drawRect(20f * s, 72f * s, 60f * s, 81f * s, fg)
        return bmp
    }

    private fun toPng(bmp: Bitmap): ByteArray {
        val bos = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.PNG, 100, bos)
        return bos.toByteArray()
    }
}