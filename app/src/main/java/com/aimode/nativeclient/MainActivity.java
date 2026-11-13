package com.aimode.nativeclient;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.PermissionRequest;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.webkit.WebSettingsCompat;
import androidx.webkit.WebViewFeature;
import java.util.ArrayList;
import java.util.List;

public final class MainActivity extends Activity {
    private static final int PICK_FILE = 4101;
    private static final int MIC_PERMISSION = 4102;
    private static final String STATE = "web_state";
    private static final String AI_MODE = "https://www.google.com/search?udm=50&aep=43&cud=0";
    private WebView web;
    private ProgressBar progress;
    private LinearLayout error;
    private ValueCallback<Uri[]> fileCallback;
    private PermissionRequest audioRequest;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        Window window = getWindow();
        WindowCompat.setDecorFitsSystemWindows(window, false);
        window.setStatusBarColor(Color.rgb(23, 24, 27));
        window.setNavigationBarColor(Color.rgb(23, 24, 27));
        if (Build.VERSION.SDK_INT >= 29) window.setNavigationBarContrastEnforced(false);
        WindowInsetsControllerCompat bars = new WindowInsetsControllerCompat(window, window.getDecorView());
        bars.setAppearanceLightStatusBars(false);
        bars.setAppearanceLightNavigationBars(false);
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.rgb(32, 33, 36));
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets sys = insets.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.displayCutout());
            Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
            v.setPadding(sys.left, sys.top, sys.right, Math.max(sys.bottom, ime.bottom));
            return insets;
        });

        web = createWebView();
        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setIndeterminate(true);
        error = createErrorPanel();
        error.setVisibility(View.GONE);
        root.addView(web, new FrameLayout.LayoutParams(-1, -1));
        FrameLayout.LayoutParams pp = new FrameLayout.LayoutParams(-1, dp(3), Gravity.TOP);
        root.addView(progress, pp);
        root.addView(error, new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);

        if (state != null && state.getBundle(STATE) != null) {
            if (web.restoreState(state.getBundle(STATE)) == null) web.loadUrl(AI_MODE);
        } else web.loadUrl(AI_MODE);
    }

    private WebView createWebView() {
        WebView view = new WebView(this);
        view.setBackgroundColor(Color.rgb(32, 33, 36));
        view.setVerticalScrollBarEnabled(false);
        view.setHorizontalScrollBarEnabled(false);
        view.setOverScrollMode(View.OVER_SCROLL_NEVER);
        view.setScrollBarStyle(View.SCROLLBARS_INSIDE_OVERLAY);
        WebSettings s = view.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setLoadsImagesAutomatically(true);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(true);
        s.setSupportZoom(false);
        s.setBuiltInZoomControls(false);
        s.setDisplayZoomControls(false);
        s.setJavaScriptCanOpenWindowsAutomatically(false);
        s.setSupportMultipleWindows(false);
        s.setMediaPlaybackRequiresUserGesture(true);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        if (WebViewFeature.isFeatureSupported(WebViewFeature.SAFE_BROWSING_ENABLE)) {
            WebSettingsCompat.setSafeBrowsingEnabled(s, true);
        }
        view.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                Uri u = r.getUrl();
                return u == null || !"https".equalsIgnoreCase(u.getScheme()) || !googleHost(u.getHost());
            }
            @Override public void onPageStarted(WebView v, String url, android.graphics.Bitmap icon) {
                progress.setVisibility(View.VISIBLE);
                error.setVisibility(View.GONE);
            }
            @Override public void onPageFinished(WebView v, String url) {
                progress.setVisibility(View.GONE);
            }
            @Override public void onReceivedError(WebView v, WebResourceRequest r, WebResourceError e) {
                if (r.isForMainFrame()) {
                    progress.setVisibility(View.GONE);
                    error.setVisibility(View.VISIBLE);
                }
            }
        });
        view.setWebChromeClient(new WebChromeClient() {
            @Override public void onProgressChanged(WebView v, int value) {
                progress.setVisibility(value >= 100 ? View.GONE : View.VISIBLE);
            }
            @Override public boolean onShowFileChooser(WebView v, ValueCallback<Uri[]> callback,
                                                        FileChooserParams params) {
                if (fileCallback != null) fileCallback.onReceiveValue(null);
                fileCallback = callback;
                Intent pick = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                pick.addCategory(Intent.CATEGORY_OPENABLE);
                pick.setType("*/*");
                String[] types = params.getAcceptTypes();
                if (types != null) {
                    List<String> valid = new ArrayList<>();
                    for (String t : types) if (t != null && !t.trim().isEmpty()) valid.add(t.trim());
                    if (valid.size() == 1) pick.setType(valid.get(0));
                    else if (!valid.isEmpty()) pick.putExtra(Intent.EXTRA_MIME_TYPES, valid.toArray(new String[0]));
                }
                if (params.getMode() == FileChooserParams.MODE_OPEN_MULTIPLE)
                    pick.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
                try {
                    startActivityForResult(Intent.createChooser(pick, getString(R.string.choose_file)), PICK_FILE);
                    return true;
                } catch (Exception ex) {
                    fileCallback = null;
                    return false;
                }
            }
            @Override public void onPermissionRequest(PermissionRequest request) {
                runOnUiThread(() -> requestWebPermission(request));
            }
            @Override public void onPermissionRequestCanceled(PermissionRequest request) {
                if (audioRequest == request) audioRequest = null;
            }
            @Override public boolean onCreateWindow(WebView v, boolean dialog, boolean gesture,
                                                     android.os.Message result) {
                return false;
            }
        });
        return view;
    }

    private void requestWebPermission(PermissionRequest request) {
        String host = Uri.parse(request.getOrigin().toString()).getHost();
        boolean audio = false;
        for (String item : request.getResources()) {
            if (PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(item)) audio = true;
        }
        if (!googleHost(host) || !audio) { request.deny(); return; }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            audioRequest = request;
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, MIC_PERMISSION);
        } else request.grant(new String[]{PermissionRequest.RESOURCE_AUDIO_CAPTURE});
    }

    private boolean googleHost(String host) {
        if (host == null) return false;
        String h = host.toLowerCase();
        return domain(h, "google.com") || domain(h, "google.com.br")
                || domain(h, "gstatic.com") || domain(h, "googleapis.com")
                || domain(h, "googleusercontent.com") || domain(h, "ggpht.com");
    }

    private boolean domain(String host, String suffix) {
        return host.equals(suffix) || host.endsWith("." + suffix);
    }

    private LinearLayout createErrorPanel() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setGravity(Gravity.CENTER);
        panel.setPadding(dp(30), dp(24), dp(30), dp(24));
        panel.setBackgroundColor(Color.rgb(32, 33, 36));
        TextView message = new TextView(this);
        message.setText(R.string.connection_error);
        message.setTextColor(Color.WHITE);
        message.setTextSize(16);
        message.setGravity(Gravity.CENTER);
        panel.addView(message, new LinearLayout.LayoutParams(-1, -2));
        Button retry = new Button(this);
        retry.setText(R.string.retry);
        retry.setOnClickListener(v -> {
            error.setVisibility(View.GONE);
            progress.setVisibility(View.VISIBLE);
            web.loadUrl(AI_MODE);
        });
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(-2, -2);
        bp.topMargin = dp(16);
        panel.addView(retry, bp);
        return panel;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override protected void onActivityResult(int code, int result, Intent data) {
        super.onActivityResult(code, result, data);
        if (code != PICK_FILE) return;
        Uri[] selected = null;
        if (result == RESULT_OK && data != null) {
            if (data.getClipData() != null) {
                int n = data.getClipData().getItemCount();
                selected = new Uri[n];
                for (int i = 0; i < n; i++) selected[i] = data.getClipData().getItemAt(i).getUri();
            } else if (data.getData() != null) selected = new Uri[]{data.getData()};
        }
        if (fileCallback != null) {
            fileCallback.onReceiveValue(selected);
            fileCallback = null;
        }
    }

    @Override public void onRequestPermissionsResult(int code, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(code, permissions, results);
        if (code == MIC_PERMISSION && audioRequest != null) {
            if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED)
                audioRequest.grant(new String[]{PermissionRequest.RESOURCE_AUDIO_CAPTURE});
            else audioRequest.deny();
            audioRequest = null;
        }
    }

    @Override protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        if (web != null) {
            Bundle saved = new Bundle();
            web.saveState(saved);
            out.putBundle(STATE, saved);
        }
    }

    @Override public void onBackPressed() {
        if (web != null && web.canGoBack()) web.goBack();
        else super.onBackPressed();
    }

    @Override protected void onDestroy() {
        if (fileCallback != null) fileCallback.onReceiveValue(null);
        if (web != null) {
            web.stopLoading();
            web.setWebChromeClient(null);
            web.setWebViewClient(null);
            web.destroy();
            web = null;
        }
        super.onDestroy();
    }
}