package com.html2apk.builder.engine

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
     * 生成默认图标（矢量，深色底 + HTML 风格三横线）
     * 生成到 res/drawable/ic_launcher.xml
     */
    fun generateDefaultIcon(resDir: File): File {
        val drawableDir = File(resDir, "drawable")
        drawableDir.mkdirs()
        val f = File(drawableDir, "ic_launcher.xml")
        f.writeText(
            """<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <path
        android:fillColor="#0B1020"
        android:pathData="M0,0h108v108h-108z" />
    <path
        android:fillColor="#4C8DFF"
        android:pathData="M20,26h68v9h-68zM20,49h68v9h-68zM20,72h40v9h-40z" />
</vector>
""",
            Charsets.UTF_8
        )
        return f
    }
}