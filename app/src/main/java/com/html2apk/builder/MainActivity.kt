package com.html2apk.builder

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import com.html2apk.builder.engine.BuildConfig2
import com.html2apk.builder.engine.BuildEngine
import com.html2apk.builder.engine.EngineManager
import com.html2apk.builder.file.FileService
import com.html2apk.builder.install.ApkInstaller
import org.json.JSONObject

@SuppressLint("SetJavaScriptEnabled")
class MainActivity : Activity() {

    private lateinit var webView: WebView
    private lateinit var engineManager: EngineManager
    private var lastApkPath: String? = null
    private var isBuilding = false

    companion object {
        private const val REQ_PICK_HTML = 1001
        private const val REQ_PICK_FOLDER = 1002
        private const val REQ_INSTALL_PERM = 1003
        private var pendingPickCallback: String? = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        engineManager = EngineManager(this)

        webView = WebView(this)
        val s = webView.settings
        s.javaScriptEnabled = true
        s.domStorageEnabled = true
        s.allowFileAccess = true
        s.allowContentAccess = true
        s.allowFileAccessFromFileURLs = true
        s.allowUniversalAccessFromFileURLs = true
        s.setSupportZoom(false)
        s.loadWithOverviewMode = true
        s.useWideViewPort = true
        s.cacheMode = WebSettings.LOAD_DEFAULT
        s.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                if (url == null) return false
                // 仅允许加载内置 UI
                return !url.startsWith("file:///android_asset/ui/") && !url.startsWith("about:")
            }
        }
        webView.webChromeClient = WebChromeClient()
        webView.addJavascriptInterface(Bridge(), "html2apk")
        setContentView(webView)
        // UI 资产升级后清除 WebView 缓存，避免加载旧版界面
        webView.clearCache(true)
        webView.loadUrl("file:///android_asset/ui/index.html")
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val cb = pendingPickCallback
        pendingPickCallback = null
        if (resultCode != RESULT_OK || data == null) {
            if (cb != null) evalJs("window.$cb && window.$cb('')")
            return
        }
        when (requestCode) {
            REQ_PICK_HTML -> {
                val uri = data.data
                val copied = if (uri != null) FileService.copySingleToBuildDir(this, uri) else null
                if (cb != null) evalJs("window.$cb && window.$cb(${JSONObject.quote(copied ?: "")})")
            }
            REQ_PICK_FOLDER -> {
                val uri = data.data
                val copied = if (uri != null) FileService.copyTreeToBuildDir(this, uri) else null
                if (cb != null) evalJs("window.$cb && window.$cb(${JSONObject.quote(copied ?: "")})")
            }
        }
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }

    override fun onDestroy() {
        webView.destroy()
        super.onDestroy()
    }

    /** JS 桥：window.html2apk.* */
    inner class Bridge {
        @JavascriptInterface
        fun pickHtmlFile(callback: String) {
            runOnUiThread {
                pendingPickCallback = callback
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "text/html"
                    putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("text/html", "text/plain", "application/xhtml+xml"))
                }
                try {
                    startActivityForResult(intent, REQ_PICK_HTML)
                } catch (e: Exception) {
                    pendingPickCallback = null
                    toastMsg("无法打开文件选择器: ${e.message}")
                }
            }
        }

        @JavascriptInterface
        fun pickFolder(callback: String) {
            runOnUiThread {
                pendingPickCallback = callback
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
                    putExtra("android.content.extra.SHOW_ADVANCED", true)
                }
                try {
                    startActivityForResult(intent, REQ_PICK_FOLDER)
                } catch (e: Exception) {
                    pendingPickCallback = null
                    toastMsg("无法打开文件夹选择器: ${e.message}")
                }
            }
        }

        @JavascriptInterface
        fun startBuild(configJson: String) {
            if (isBuilding) {
                toastMsg("正在构建中，请稍候")
                return
            }
            isBuilding = true
            val cfg = try {
                BuildConfig2.fromJson(configJson)
            } catch (e: Exception) {
                toastMsg("配置解析失败: ${e.message}")
                isBuilding = false
                return
            }
            if (cfg.appName.isBlank()) { toastMsg("请填写应用名称"); isBuilding = false; return }
            if (cfg.packageName.isBlank() || !cfg.packageName.matches(Regex("^[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z][a-zA-Z0-9_]*)+$"))) {
                toastMsg("包名格式不正确（如 com.example.app）")
                isBuilding = false
                return
            }
            Thread {
                val result = BuildEngine.build(this@MainActivity, engineManager, cfg) { step, msg ->
                    runOnUiThread { evalJs("window.onBuildProgress && window.onBuildProgress(${JSONObject.quote(step)}, ${JSONObject.quote(msg)})") }
                }
                runOnUiThread {
                    isBuilding = false
                    when (result) {
                        is BuildEngine.Result.Success -> {
                            lastApkPath = result.apkPath
                            evalJs("window.onBuildDone && window.onBuildDone(${JSONObject.quote(result.apkPath)}, ${JSONObject.quote(result.sizeLabel)})")
                        }
                        is BuildEngine.Result.Failure -> {
                            evalJs("window.onBuildError && window.onBuildError(${JSONObject.quote(result.message)})")
                        }
                    }
                }
            }.start()
        }

        @JavascriptInterface
        fun installApk() {
            val path = lastApkPath ?: run { toastMsg("还没有可安装的 APK"); return }
            runOnUiThread {
                if (Build.VERSION.SDK_INT >= 26 && !packageManager.canRequestPackageInstalls()) {
                    toastMsg("请先允许安装未知应用")
                    try {
                        startActivityForResult(
                            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName")),
                            REQ_INSTALL_PERM
                        )
                    } catch (e: Exception) {
                        toastMsg("无法打开安装权限设置")
                    }
                    return@runOnUiThread
                }
                val ok = ApkInstaller.install(this@MainActivity, path)
                if (!ok) toastMsg("安装失败，APK 位于: $path")
            }
        }

        @JavascriptInterface
        fun openApkFolder() {
            val path = lastApkPath ?: return
            runOnUiThread {
                ApkInstaller.openInFiles(this@MainActivity, path)
            }
        }

        @JavascriptInterface
        fun getEngineStatus(): String {
            return try {
                val ok = engineManager.ensureEngine()
                "{\"ready\":${ok},\"dir\":\"${engineManager.engineDir.absolutePath}\"}"
            } catch (e: Exception) {
                "{\"ready\":false,\"error\":\"${e.message}\"}"
            }
        }

        @JavascriptInterface
        fun getDownloadDir(): String {
            return Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                .absolutePath + "/HTML2APK"
        }

        @JavascriptInterface
        fun toast(msg: String) {
            toastMsg(msg)
        }
    }

    private fun evalJs(js: String) {
        runOnUiThread {
            try { webView.evaluateJavascript(js, null) } catch (_: Exception) {}
        }
    }

    private fun toastMsg(msg: String) {
        runOnUiThread { Toast.makeText(this, msg, Toast.LENGTH_SHORT).show() }
    }
}