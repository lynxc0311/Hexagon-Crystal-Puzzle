package vn.lynxc.hexagon;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.res.Configuration;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.Surface;
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
import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewFeature;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.Locale;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;

/** Runs the unchanged game from bundled, offline HTTPS-origin assets. */
public final class MainActivity extends Activity implements SensorEventListener {
    private static final String HOST = "appassets.androidplatform.net";
    private static final String START_URL = "https://" + HOST + "/assets/index.html";
    private FrameLayout container;
    private WebView webView;
    private SensorManager sensorManager;
    private Sensor rotationSensor;
    private final TiltMath tiltMath = new TiltMath();
    private final float[] sensorMatrix = new float[9];
    private final float[] screenMatrix = new float[9];
    private boolean resumed, pageReady, bridgeReady, motionEnabled = true, listening;
    private long lastMotionTime;
    private int lastDisplayRotation = -1;
    private float lastX = Float.NaN, lastY = Float.NaN;

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        if (sensorManager != null) {
            rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR);
            if (rotationSensor == null) rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
        }
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
        pageReady = false;
        stopMotion();
        bridgeReady = WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER);
        if (bridgeReady) {
            WebViewCompat.addWebMessageListener(view, "HexagonMotion",
                    Collections.singleton("https://" + HOST), (current, message, origin, mainFrame, reply) -> {
                if (!mainFrame || current != webView || !HOST.equals(origin.getHost())) return;
                try {
                    JSONObject data = new JSONObject(message.getData());
                    if ("motion-settings".equals(data.optString("type"))) {
                        motionEnabled = data.optBoolean("enabled", true);
                        if (motionEnabled) startMotion(); else stopMotion();
                    } else if ("recenter".equals(data.optString("type"))) {
                        resetMotionCenter();
                    }
                } catch (JSONException | NullPointerException ignored) {
                    // The local page only sends the two explicit control messages above.
                }
            });
        }
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
            public void onPageFinished(WebView current, String url) {
                if (current != webView || !isLocalAsset(Uri.parse(url))) return;
                pageReady = true;
                sendMotion("{\"available\":" + (rotationSensor != null && bridgeReady) + "}");
                startMotion();
            }

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
                pageReady = false;
                stopMotion();
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
        resetMotionCenter();
        enterFullscreen();
    }

    @Override
    protected void onResume() {
        super.onResume();
        resumed = true;
        startMotion();
        if (webView != null) { webView.onResume(); webView.resumeTimers(); }
    }

    @Override
    protected void onPause() {
        resumed = false;
        stopMotion();
        if (webView != null) { webView.onPause(); webView.pauseTimers(); }
        super.onPause();
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        if (webView != null && pageReady) {
            webView.evaluateJavascript("(()=>{const d=document.getElementById('helpDialog');if(d&&d.open){d.close();return true}return false})()",
                    result -> { if (!"true".equals(result)) showExitDialog(); });
        } else showExitDialog();
    }

    private void showExitDialog() {
        new AlertDialog.Builder(this).setMessage("Thoát trò chơi?")
                .setPositiveButton("Thoát", (dialog, which) -> finish())
                .setNegativeButton("Chơi tiếp", null).show();
    }

    private void resetMotionCenter() {
        tiltMath.reset();
        lastMotionTime = 0;
        lastX = Float.NaN;
        lastY = Float.NaN;
    }

    private void startMotion() {
        if (!resumed || !pageReady || !bridgeReady || !motionEnabled || rotationSensor == null || listening) return;
        resetMotionCenter();
        listening = sensorManager.registerListener(this, rotationSensor, SensorManager.SENSOR_DELAY_GAME);
        if (!listening) sendMotion("{\"available\":false}");
    }

    private void stopMotion() {
        if (sensorManager != null) sensorManager.unregisterListener(this);
        listening = false;
        resetMotionCenter();
    }

    private void sendMotion(String json) {
        runOnUiThread(() -> {
            if (webView != null && pageReady && isLocalAsset(Uri.parse(webView.getUrl() == null ? "" : webView.getUrl()))) {
                webView.evaluateJavascript("window.dispatchEvent(new CustomEvent('hexagon-motion',{detail:" + json + "}));", null);
            }
        });
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onSensorChanged(SensorEvent event) {
        if (!listening || !resumed || !pageReady || !motionEnabled) return;
        long now = SystemClock.elapsedRealtime();
        if (now - lastMotionTime < 33) return;
        lastMotionTime = now;
        SensorManager.getRotationMatrixFromVector(sensorMatrix, event.values);
        int x = SensorManager.AXIS_X, y = SensorManager.AXIS_Y;
        int rotation = getWindowManager().getDefaultDisplay().getRotation();
        if (rotation != lastDisplayRotation) { resetMotionCenter(); lastDisplayRotation = rotation; }
        if (rotation == Surface.ROTATION_90) { x = SensorManager.AXIS_Y; y = SensorManager.AXIS_MINUS_X; }
        else if (rotation == Surface.ROTATION_180) { x = SensorManager.AXIS_MINUS_X; y = SensorManager.AXIS_MINUS_Y; }
        else if (rotation == Surface.ROTATION_270) { x = SensorManager.AXIS_MINUS_Y; y = SensorManager.AXIS_X; }
        if (!SensorManager.remapCoordinateSystem(sensorMatrix, x, y, screenMatrix)) return;
        float[] tilt = tiltMath.sample(screenMatrix);
        if (tilt == null) return;
        if (Float.isFinite(lastX) && Math.abs(tilt[0] - lastX) < .002f && Math.abs(tilt[1] - lastY) < .002f) return;
        lastX = tilt[0]; lastY = tilt[1];
        sendMotion(String.format(Locale.US, "{\"available\":true,\"x\":%.4f,\"y\":%.4f}", lastX, lastY));
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // No compass heading or high-rate sensor permission is needed.
    }

    @Override
    protected void onDestroy() {
        stopMotion();
        pageReady = false;
        if (webView != null) {
            container.removeView(webView);
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }
}
