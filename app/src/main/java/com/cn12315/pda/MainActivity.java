package com.cn12315.pda;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

/**
 * PDA WebView 壳
 *
 * 只做一件事：全屏加载 PDA 专用登录页，登录后由网页跳转到扫码发货页。
 * 扫码走 PDA 键盘模拟（HID）：网页输入框聚焦后扫码即填入 + 回车，
 * 因此这里不接收扫码广播，只负责：
 *   1) 常亮          —— 仓库作业不必反复点亮
 *   2) 不弹软键盘占位 —— 有物理键盘，软键盘只会遮挡（Manifest 里 adjustNothing）
 *   3) Cookie 保持    —— 登录态跨页面延续
 *   4) 返回键交给网页 —— 在 WebView 内后退，不直接退出 App
 */
public class MainActivity extends Activity {

    // ===== 改成你的地址：正式站把 test 换成实际域名即可 =====
    private static final String TARGET_URL = "https://test.cn12315.com/pda_login";
    // 只允许本站域名在 WebView 内打开，外部链接交给系统浏览器
    private static final String SITE_HOST = "test.cn12315.com";

    private WebView webView;
    private ProgressBar progress;
    private TextView errorView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 去掉标题栏，保留状态栏（PDA 上要看电量/时间）
        requestWindowFeature(Window.FEATURE_NO_TITLE);

        // 屏幕常亮
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.rgb(238, 242, 247));

        webView = new WebView(getApplicationContext());
        progress = new ProgressBar(this);
        errorView = new TextView(this);
        errorView.setText("加载失败，点击重试");
        errorView.setTextSize(20);
        errorView.setTextColor(Color.rgb(43, 94, 220));
        errorView.setGravity(android.view.Gravity.CENTER);
        errorView.setVisibility(View.GONE);
        errorView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                errorView.setVisibility(View.GONE);
                webView.reload();
            }
        });

        root.addView(webView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        root.addView(progress, new FrameLayout.LayoutParams(48, 48, android.view.Gravity.CENTER));
        root.addView(errorView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        setContentView(root);

        configWebView();
        webView.loadUrl(TARGET_URL);

        // 允许 chrome://inspect 真机调试（仅调试期需要，正式包可留着无副作用）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            WebView.setWebContentsDebuggingEnabled(true);
        }
    }

    private void configWebView() {
        WebSettings s = webView.getSettings();

        // JS 必须开：登录/发货页靠 ajax 调接口
        s.setJavaScriptEnabled(true);
        // 支持 viewport meta，页面按 320 CSS 像素渲染
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(true);
        // 禁止缩放（页面已 user-scalable=no）
        s.setSupportZoom(false);
        s.setBuiltInZoomControls(false);
        s.setDisplayZoomControls(false);
        // DOM Storage / Database：页面与框架可能用到
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        // 缓存策略：正常缓存，弱网下也能开页面
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            // 兼容页面里可能混用的 http 静态资源（https 主站）
            s.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBLE_MODE);
        }

        // Cookie 保持：登录态靠 PHPSESSID，必须开
        CookieManager cm = CookieManager.getInstance();
        cm.setAcceptCookie(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            cm.setAcceptThirdPartyCookies(webView, true);
        }

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                if (newProgress >= 100) {
                    progress.setVisibility(View.GONE);
                } else {
                    progress.setVisibility(View.VISIBLE);
                    progress.setProgress(newProgress);
                }
            }
        });

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return handleUrl(request.getUrl().toString());
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return handleUrl(url);
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                progress.setVisibility(View.GONE);
                errorView.setVisibility(View.VISIBLE);
            }
        });
    }

    /** 本站链接留在 WebView，外部链接（tel/mail/其他域名）交给系统 */
    private boolean handleUrl(String url) {
        if (url == null) return true;
        if (url.startsWith("tel:") || url.startsWith("mailto:")) {
            try {
                startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse(url)));
            } catch (Exception ignored) {
            }
            return true;
        }
        Uri uri = Uri.parse(url);
        String host = uri.getHost();
        if (host != null && (host.equals(SITE_HOST) || host.endsWith("." + SITE_HOST))) {
            return false; // WebView 内部打开
        }
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception ignored) {
        }
        return true;
    }

    /** 返回键：网页有历史就后退，没有才退出（避免一按返回就退出登录） */
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && webView != null && webView.canGoBack()) {
            webView.goBack();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.destroy();
        }
        super.onDestroy();
    }
}
