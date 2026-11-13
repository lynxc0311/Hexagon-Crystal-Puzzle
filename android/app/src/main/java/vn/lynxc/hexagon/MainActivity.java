package vn.lynxc.hexagon;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.res.Configuration;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.DisplayCutout;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;

import androidx.webkit.WebViewAssetLoader;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;

/** Runs the unchanged game from bundled, offline HTTPS-origin assets. */
public final class MainActivity extends Activity {
    private static final String HOST = "appassets.androidplatform.net";
    private static final String START_URL = "https://" + HOST + "/assets/index.html";
    private FrameLayout container;
    private WebView webView;

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        container = new FrameLayout(this);
        container.setBackgroundColor(Color.rgb(3, 3, 4));
        if (Build.VERSION.SDK_INT >= 28) {
            container.setOnApplyWindowInsetsListener((view, insets) -> {
                DisplayCutout cutout = insets.getDisplayCutout();
                if (cutout != null) {
                    view.setPadding(cutout.getSafeInsetLeft(), cutout.getSafeInsetTop(),
                            cutout.getSafeInsetRight(), cutout.getSafeInsetBottom());
                } else {
                    view.setPadding(0, 0, 0, 0);
                }
                return insets;
            });
        }
        setContentView(container);
        createGameWebView();
        enterFullscreen();
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void createGameWebView() {
        WebView view = new WebView(this);
        webView = view;
        view.setBackgroundColor(Color.rgb(3, 3, 4));
        view.setOverScrollMode(View.OVER_SCROLL_NEVER);
        WebSettings settings = view.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setSupportZoom(false);
        settings.setBuiltInZoomControls(false);
        view.setWebChromeClient(new WebChromeClient());

        WebViewAssetLoader loader = new WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
                .build();
        view.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView current,
                    WebResourceRequest request) {
                if (isLocalAsset(request.getUrl())) {
                    WebResourceResponse asset = loader.shouldInterceptRequest(request.getUrl());
                    if (asset != null) return asset;
                }
                return new WebResourceResponse("text/plain", "UTF-8", 404, "Not Found",
                        Collections.emptyMap(), new ByteArrayInputStream(
                                "Offline asset unavailable".getBytes(StandardCharsets.UTF_8)));
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView current, WebResourceRequest request) {
                return !isLocalAsset(request.getUrl());
            }

            @Override
            public boolean onRenderProcessGone(WebView current, RenderProcessGoneDetail detail) {
                container.removeView(current);
                current.destroy();
                if (webView == current) webView = null;
                if (!isFinishing() && !isDestroyed()) {
                    new AlertDialog.Builder(MainActivity.this)
                            .setMessage("Đồ họa đã dừng. Mở lại trò chơi?")
                            .setPositiveButton("Mở lại", (dialog, which) -> createGameWebView())
                            .setNegativeButton("Thoát", (dialog, which) -> finish())
                            .setCancelable(false).show();
                }
                return true;
            }
        });
        container.addView(view, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        view.loadUrl(START_URL);
    }

    private static boolean isLocalAsset(Uri uri) {
        return "https".equals(uri.getScheme()) && HOST.equals(uri.getHost())
                && (uri.getPort() == -1 || uri.getPort() == 443)
                && uri.getPath() != null && uri.getPath().startsWith("/assets/");
    }

    @SuppressWarnings("deprecation")
    private void enterFullscreen() {
        if (Build.VERSION.SDK_INT >= 30) {
            WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) {
                controller.hide(WindowInsets.Type.systemBars());
                controller.setSystemBarsBehavior(
                        WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) enterFullscreen();
    }

    @Override
    public void onConfigurationChanged(Configuration configuration) {
        super.onConfigurationChanged(configuration);
        enterFullscreen();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (webView != null) { webView.onResume(); webView.resumeTimers(); }
    }

    @Override
    protected void onPause() {
        if (webView != null) { webView.onPause(); webView.pauseTimers(); }
        super.onPause();
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        new AlertDialog.Builder(this).setMessage("Thoát trò chơi?")
                .setPositiveButton("Thoát", (dialog, which) -> finish())
                .setNegativeButton("Chơi tiếp", null).show();
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            container.removeView(webView);
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }
}
