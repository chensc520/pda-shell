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
 * PDA WebView 框架
 */
public class MainActivity extends Activity {

    // ===================== 常用配置：以后只改这一块 =====================
    private static final String TARGET_URL = "https://test.cn12315.com/pda_login?c=ckbs";       /** 应用启动时加载的商户登录入口 */
    private static final String SH_NO = "ckbs";                                                /** 商户编号 sh_no：会话过期跳 /pda_login?c=xxx 要用，需与上面 URL 里 c= 的值一致 */
    private static final String CLIENT_TAG = "cn12315pda";                                    /** PDA 客户端标识：服务端据此判断会话过期跳 /pda_login（大小写敏感，勿改） */
    private static final String STATUS_BAR_COLOR = "#428bca";                                /** 顶部状态栏颜色 */
    // ===================================================================

    // 允许的域名 自动从 TARGET_URL 提取
    private static final String SITE_HOST = Uri.parse(TARGET_URL).getHost();
    private WebView webView;
    private ProgressBar progress;
    private TextView errorView;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 去掉标题栏，保留状态栏并设背景色（PDA 上能看到电量/时间）
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        // 状态栏背景色：本项目 minSdk 26，直接调用即可（无需版本判断）
        getWindow().setStatusBarColor(Color.parseColor(STATUS_BAR_COLOR));

        // 屏幕常亮
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.rgb(238, 242, 247));

        webView = new WebView(this);
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


        s.setUserAgentString(s.getUserAgentString() + " " + CLIENT_TAG + " sh_no:" + SH_NO);

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
            s.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
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
