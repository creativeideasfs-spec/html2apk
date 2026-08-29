package com.html2apk.shell;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebChromeClient;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;

/**
 * HTML2APK shell - 固定壳代码。
 * 包名固定为 com.html2apk.shell（与用户自定义 applicationId 解耦，manifest 中全限定引用）。
 * 运行时从 assets 加载 index.html（或 assets 根目录下的 HTML）。
 * 行为可被 assets/ht2a.config 覆盖（见 Config 类）。
 */
public class MainActivity extends Activity {

    private WebView webView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Config cfg = Config.load(this);

        // 状态栏/导航栏颜色
        Window window = getWindow();
        if (window != null) {
            try {
                int barColor = Color.parseColor(cfg.statusBarColor);
                window.setStatusBarColor(barColor);
                window.setNavigationBarColor(Color.parseColor(cfg.navBarColor));
                if (Build.VERSION.SDK_INT >= 30) {
                    window.getDecorView().setSystemUiVisibility(
                            View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
                }
            } catch (Exception ignored) {
            }
        }

        RelativeLayout root = new RelativeLayout(this);
        webView = new WebView(this);
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        s.setAllowFileAccessFromFileURLs(true);
        s.setAllowUniversalAccessFromFileURLs(true);
        s.setBuiltInZoomControls(false);
        s.setDisplayZoomControls(false);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);

        webView.setBackgroundColor(Color.parseColor(cfg.backgroundColor));
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();
                return handleUrl(url);
            }

            @SuppressWarnings("deprecation")
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return handleUrl(url);
            }
        });
        webView.setWebChromeClient(new WebChromeClient());
        webView.addJavascriptInterface(new Bridge(), "HTML2APK");

        root.addView(webView, new RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.MATCH_PARENT,
                RelativeLayout.LayoutParams.MATCH_PARENT));

        // 启动进度条
        final ProgressBar bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(100);
        bar.setProgress(0);
        RelativeLayout.LayoutParams barLp = new RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.MATCH_PARENT, 6);
        barLp.addRule(RelativeLayout.ALIGN_PARENT_TOP);
        root.addView(bar, barLp);
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                bar.setProgress(newProgress);
                bar.setVisibility(newProgress >= 100 ? View.GONE : View.VISIBLE);
            }
        });

        setContentView(root);
        webView.loadUrl("file:///android_asset/" + cfg.entryFile);
    }

    private boolean handleUrl(String url) {
        if (url == null) return false;
        if (url.startsWith("file:///android_asset/")) return false; // 内置资源
        if (url.startsWith("about:")) return false;
        if (url.startsWith("data:")) return false;
        if (url.startsWith("javascript:")) return false;
        // 外部 http(s) 链接：留在 WebView 内打开（简单策略），不跳系统浏览器
        if (url.startsWith("http://") || url.startsWith("https://")) {
            return false; // 留在 WebView
        }
        // 其它 scheme（tel:, mailto:, intent: 等）交给系统
        try {
            Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            startActivity(i);
        } catch (Exception ignored) {
        }
        return true;
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
            return;
        }
        super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        if (webView != null) webView.destroy();
        super.onDestroy();
    }

    /** JS 桥：页面可调用 window.HTML2APK.* */
    private class Bridge {
        @android.webkit.JavascriptInterface
        public String getVersion() {
            return "1.0";
        }
    }
}
