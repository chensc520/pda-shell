package com.cn12315.pda;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
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
 *
 * 断网策略：启动时先判断网络，没网就把 WebView 整个隐藏，
 * 只显示一行纯文字提示。
 * 网络恢复后自动加载首页。
 */
public class MainActivity extends Activity {

    // ===================== 常用配置 =====================
    private static final String TARGET_URL = "https://mp.cn12315.com/pda_login";                /** 应用启动时加载的商户登录入口 */
    private static final String SH_NO = "ckbs";                                                /** 商户编号 sh_no：会话过期跳 /pda_login 要用 */
    private static final String CLIENT_TAG = "cn12315pda";                                    /** PDA 客户端标识：服务端据此判断会话过期跳 /pda_login（大小写敏感，勿改） */
    private static final String STATUS_BAR_COLOR = "#428bca";                                /** 顶部状态栏颜色 */
    private static final String OFFLINE_TIP = "当前设备未联网\n请检查Wifi或移动数据";          /** 断网时显示的文字（纯文字，不含任何网址） */
    // ===================================================================

    
    private static final String SITE_HOST = Uri.parse(TARGET_URL).getHost();   // 允许的域名 自动从 TARGET_URL 提取

    private WebView webView;
    private ProgressBar progress;
    private TextView errorView;
    private TextView offlineView;

    private ConnectivityManager connectivityManager;
    private ConnectivityManager.NetworkCallback networkCallback;
    private boolean networkCallbackRegistered = false;
    private boolean hasLoaded = false;

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
        errorView.setGravity(Gravity.CENTER);
        errorView.setVisibility(View.GONE);
        errorView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                errorView.setVisibility(View.GONE);
                webView.setVisibility(View.VISIBLE);
                webView.reload();
            }
        });

        // 断网提示：纯文字，WebView 隐藏，不加载任何网址
        offlineView = new TextView(this);
        offlineView.setText(OFFLINE_TIP);
        offlineView.setTextSize(20);
        offlineView.setTextColor(Color.rgb(43, 94, 220));
        offlineView.setGravity(Gravity.CENTER);
        offlineView.setVisibility(View.GONE);
        offlineView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (isNetworkAvailable()) {
                    showPage();
                }
            }
        });

        root.addView(webView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        root.addView(progress, new FrameLayout.LayoutParams(48, 48, Gravity.CENTER));
        root.addView(errorView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        root.addView(offlineView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        setContentView(root);

        configWebView();
        registerNetworkCallback();

        // 启动先判断网络：没网只显示提示，绝不 loadUrl（避免暴露网址）
        if (isNetworkAvailable()) {
            showPage();
        } else {
            showOffline();
        }

        // 允许 chrome://inspect 真机调试（仅调试期需要，正式包可留着无副作用）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            WebView.setWebContentsDebuggingEnabled(true);
        }
    }

    /** 是否连着可用的网络（WIFI / 移动数据 / 网线，且带 INTERNET 能力） */
    private boolean isNetworkAvailable() {
        if (connectivityManager == null) {
            return false;
        }
        Network network = connectivityManager.getActiveNetwork();
        if (network == null) {
            return false;
        }
        NetworkCapabilities caps = connectivityManager.getNetworkCapabilities(network);
        if (caps == null || !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
            return false;
        }
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                || caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
                || caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET);
    }

    /** 显示网页：有网才调用，没网绝不加载网址 */
    private void showPage() {
        offlineView.setVisibility(View.GONE);
        errorView.setVisibility(View.GONE);
        webView.setVisibility(View.VISIBLE);
        if (!hasLoaded) {
            hasLoaded = true;
            webView.loadUrl(TARGET_URL);
        } else {
            webView.reload();
        }
    }

    /** 断网：把 WebView 整个藏起来，只留一行纯文字，网址不外泄 */
    private void showOffline() {
        progress.setVisibility(View.GONE);
        errorView.setVisibility(View.GONE);
        webView.setVisibility(View.GONE);
        offlineView.setVisibility(View.VISIBLE);
    }

    /** 网络恢复自动加载；网络断开立即切换成纯文字提示 */
    private void registerNetworkCallback() {
        connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivityManager == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            return;
        }
        NetworkRequest request = new NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build();
        networkCallback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(Network network) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (offlineView.getVisibility() == View.VISIBLE && isNetworkAvailable()) {
                            showPage();
                        }
                    }
                });
            }

            @Override
            public void onLost(Network network) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        showOffline();
                    }
                });
            }
        };
        try {
            connectivityManager.registerNetworkCallback(request, networkCallback);
            networkCallbackRegistered = true;
        } catch (Exception ignored) {
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
                // 加载失败时若确实没网，切到纯文字提示，不显示系统错误页（会露出网址）
                if (!isNetworkAvailable()) {
                    showOffline();
                } else {
                    webView.setVisibility(View.VISIBLE);
                    errorView.setVisibility(View.VISIBLE);
                }
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
        if (keyCode == KeyEvent.KEYCODE_BACK && webView != null
                && webView.getVisibility() == View.VISIBLE && webView.canGoBack()) {
            webView.goBack();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onDestroy() {
        if (networkCallbackRegistered && connectivityManager != null && networkCallback != null) {
            try {
                connectivityManager.unregisterNetworkCallback(networkCallback);
            } catch (Exception ignored) {
            }
        }
        if (webView != null) {
            webView.destroy();
        }
        super.onDestroy();
    }
}
