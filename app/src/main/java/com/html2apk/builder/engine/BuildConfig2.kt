package com.html2apk.builder.engine

import android.content.Context
import java.io.File

/** 用户构建配置 */
data class BuildConfig2(
    val appName: String,
    val packageName: String,
    val versionName: String,
    val versionCode: Int,
    val entryFile: String,        // assets 内的入口 HTML（如 index.html）
    val inputDir: String,         // 用户选择的 HTML/文件夹已拷贝到的构建输入目录
    val statusBarColor: String = "#111111",
    val navBarColor: String = "#111111",
    val backgroundColor: String = "#111111"
) {
    companion object {
        fun fromJson(json: String): BuildConfig2 {
            val o = org.json.JSONObject(json)
            return BuildConfig2(
                appName = o.optString("appName", ""),
                packageName = o.optString("packageName", ""),
                versionName = o.optString("versionName", "1.0"),
                versionCode = o.optInt("versionCode", 1),
                entryFile = o.optString("entryFile", "index.html"),
                inputDir = o.optString("inputDir", ""),
                statusBarColor = o.optString("statusBarColor", "#111111"),
                navBarColor = o.optString("navBarColor", "#111111"),
                backgroundColor = o.optString("backgroundColor", "#111111")
            )
        }
    }
}