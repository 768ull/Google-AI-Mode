package com.aimode.nativeclient;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
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
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.webkit.WebSettingsCompat;
import androidx.webkit.WebViewFeature;
import java.util.ArrayList;
import java.util.List;

public final class MainActivity extends Activity {
    private static final int PICK_FILE = 4101;
    private static final int MIC_PERMISSION = 4102;
    private static final String STATE = "web_state";
    private static final String AI_MODE = "https://www.google.com/search?udm=50&aep=43&cud=0";

    /*
     * Google Search's top category strip is the GG4mbd navigation element
     * shown in the supplied snapshot (AI Mode, All, Images, News, Videos...).
     * Only that category strip is removed; the AI Mode header remains.
     */
    private static final String PAGE_CLEANUP =
            "(function(){var id='aimode-native-cleanup';" +
            "var css='div.GG4mbd[role=navigation]{display:none!important;}';" +
            "var style=document.getElementById(id);" +
            "if(!style){style=document.createElement('style');style.id=id;" +
            "(document.head||document.documentElement).appendChild(style);}" +
            "if(style.textContent!==css)style.textContent=css;" +
            "})();";

    /*
     * Context menus and text selection are allowed only in:
     * - the prompt editor where the user types;
     * - actual user-query message nodes;
     * - actual assistant/model-response message nodes.
     *
     * Generic conversation containers, navigation, feature labels, resource
     * names, buttons, links, and static tool labels are deliberately excluded.
     */
    private static final String CONTEXT_GUARD =
            "(function(){if(window.__aimodeContextGuardInstalled)return;" +
            "window.__aimodeContextGuardInstalled=true;" +
            "var editor='input,textarea,select,[contenteditable=true],[role=textbox],[aria-multiline=true]';" +
            "var messages='user-query,.user-query,.query-text,[data-turn=user],[data-role=user],[data-message-author-role=user]," +
            "model-response,.model-response,.model-response-text,.response-content," +
            "[data-turn=assistant],[data-role=assistant],[data-message-author-role=assistant]';" +
            "var interactive='button,a,[role=button],[role=link],[role=menuitem]';" +
            "function allowed(target){" +
            "if(!target||!target.closest)return false;" +
            "if(target.closest(editor))return true;" +
            "if(target.closest(interactive))return false;" +
            "return !!target.closest(messages);" +
            "}" +
            "function block(e){if(allowed(e.target))return;" +
            "e.preventDefault();e.stopPropagation();" +
            "if(e.stopImmediatePropagation)e.stopImmediatePropagation();" +
            "}" +
            "document.addEventListener('contextmenu',block,true);" +
            "document.addEventListener('selectstart',block,true);" +
            "})();";

    /*
     * Adds one read-aloud control to assistant response blocks only. The page
     * remains the source of the AI response; speech is synthesized locally.
     */
    private static final String SPEAKER_BUTTONS =
            "(function(){if(window.__aimodeSpeakerObserver)return;" +
            "var selector='[data-message-author-role=assistant],[data-role=assistant],[data-turn=assistant],model-response,.model-response,.model-response-text,.response-content';" +
            "var buttonClass='aimode-native-speak-btn';" +
            "var css='button.aimode-native-speak-btn{display:inline-flex;align-items:center;gap:6px;margin:10px 4px 4px 0;padding:6px 12px;border:1px solid currentColor;border-radius:999px;background:transparent;color:inherit;opacity:.78;font:500 13px sans-serif;cursor:pointer;}'" +
            "button.aimode-native-speak-btn:active{opacity:1;}'" +
            "button.aimode-native-speak-btn:focus-visible{outline:2px solid currentColor;outline-offset:2px;}';" +
            "var style=document.getElementById('aimode-native-speech-style');" +
            "if(!style){style=document.createElement('style');style.id='aimode-native-speech-style';(document.head||document.documentElement).appendChild(style);}" +
            "style.textContent=css;" +
            "function getText(node){var clone=node.cloneNode(true);" +
            "clone.querySelectorAll('button,a,svg,script,style,nav,footer,header,table,pre,code,[aria-hidden=true],.aimode-native-speak-btn').forEach(function(e){e.remove();});" +
            "return (clone.innerText||clone.textContent||'').replace(/\\s+/g,' ').trim();}" +
            "window.__aimodeSetSpeechState=function(active){document.querySelectorAll('button.'+buttonClass).forEach(function(b){b.dataset.active=active?'1':'0';b.textContent=active?'■ Parar':'🔊 Ouvir';});};" +
            "function addButtons(){document.querySelectorAll(selector).forEach(function(node){" +
            "if(node.closest('button,a,[role=button]'))return;" +
            "if(node.parentElement&&node.parentElement.closest(selector))return;" +
            "if(node.querySelector('button.'+buttonClass))return;" +
            "var value=getText(node);if(value.length<30)return;" +
            "var button=document.createElement('button');button.type='button';button.className=buttonClass;button.textContent='🔊 Ouvir';button.setAttribute('aria-label','Ouvir resposta da inteligência artificial');" +
            "button.addEventListener('click',function(event){event.preventDefault();event.stopPropagation();" +
            "if(button.dataset.active==='1'){if(window.AIModeSpeech)window.AIModeSpeech.stop();window.__aimodeSetSpeechState(false);return;}" +
            "var text=getText(node);if(text&&window.AIModeSpeech){window.__aimodeSetSpeechState(true);window.AIModeSpeech.speak(text);}" +
            "});node.appendChild(button);" +
            "});}" +
            "addButtons();" +
            "window.__aimodeSpeakerObserver=new MutationObserver(function(){clearTimeout(window.__aimodeSpeakerTimer);window.__aimodeSpeakerTimer=setTimeout(addButtons,180);});" +
            "window.__aimodeSpeakerObserver.observe(document.documentElement,{childList:true,subtree:true});" +
            "})();";

    private WebView web;
    private ProgressBar progress;
    private LinearLayout error;
    private ValueCallback<Uri[]> fileCallback;
    private PermissionRequest audioRequest;
    private OnDeviceTtsController speech;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        boolean dark = isNightModeEnabled();
        Window window = getWindow();

        // Draw the app behind the front-camera cutout instead of letterboxing
        // the entire WebView below a black safe-area strip.
        if (Build.VERSION.SDK_INT >= 35) {
            WindowManager.LayoutParams lp = window.getAttributes();
            lp.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
            window.setAttributes(lp);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            WindowManager.LayoutParams lp = window.getAttributes();
            lp.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            window.setAttributes(lp);
        }

        WindowCompat.setDecorFitsSystemWindows(window, false);
        window.setStatusBarColor(android.graphics.Color.TRANSPARENT);
        window.setNavigationBarColor(android.graphics.Color.TRANSPARENT);
        if (Build.VERSION.SDK_INT >= 29) {
            window.setStatusBarContrastEnforced(false);
            window.setNavigationBarContrastEnforced(false);
        }
        WindowInsetsControllerCompat bars = new WindowInsetsControllerCompat(window, window.getDecorView());
        bars.setAppearanceLightStatusBars(!dark);
        bars.setAppearanceLightNavigationBars(!dark);
        bars.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(ContextCompat.getColor(this, R.color.app_background));
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets safeSides = insets.getInsets(
                    WindowInsetsCompat.Type.systemBars()
                            | WindowInsetsCompat.Type.displayCutout());
            Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());

            // The window is now allowed to draw behind the camera cutout.
            // Keep the page content below the cutout so its header does not sit
            // underneath the front camera; the root background fills this area
            // with the app surface color instead of leaving a black system band.
            v.setPadding(safeSides.left, safeSides.top, safeSides.right, ime.bottom);
            return insets;
        });

        web = createWebView();
        speech = new OnDeviceTtsController(this, web);
        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setIndeterminate(true);
        error = createErrorPanel();
        error.setVisibility(View.GONE);
        root.addView(web, new FrameLayout.LayoutParams(-1, -1));
        FrameLayout.LayoutParams pp = new FrameLayout.LayoutParams(-1, dp(3), Gravity.TOP);
        root.addView(progress, pp);
        root.addView(error, new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);
        enterFullscreen();

        if (state != null && state.getBundle(STATE) != null) {
            if (web.restoreState(state.getBundle(STATE)) == null) web.loadUrl(AI_MODE);
        } else web.loadUrl(AI_MODE);
    }

    private void enterFullscreen() {
        WindowInsetsControllerCompat controller =
                WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        if (controller == null) return;
        controller.setSystemBarsBehavior(
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        controller.hide(WindowInsetsCompat.Type.systemBars());
    }

    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) getWindow().getDecorView().post(this::enterFullscreen);
    }

    @Override protected void onResume() {
        super.onResume();
        enterFullscreen();
    }

    private boolean isNightModeEnabled() {
        int mode = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return mode == Configuration.UI_MODE_NIGHT_YES;
    }

    private WebView createWebView() {
        WebView view = new WebView(this);
        view.setBackgroundColor(ContextCompat.getColor(this, R.color.app_background));
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

        if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
            WebSettingsCompat.setAlgorithmicDarkeningAllowed(s, isNightModeEnabled());
        }
        if (WebViewFeature.isFeatureSupported(WebViewFeature.SAFE_BROWSING_ENABLE)) {
            WebSettingsCompat.setSafeBrowsingEnabled(s, true);
        }

        view.addJavascriptInterface(new SpeechBridge(), "AIModeSpeech");

        view.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                Uri u = r.getUrl();
                if (u == null || !"https".equalsIgnoreCase(u.getScheme())) return true;
                if (isGoogleHome(u)) return true;
                return !googleHost(u.getHost());
            }

            @Override public void onPageStarted(WebView v, String url, android.graphics.Bitmap icon) {
                progress.setVisibility(View.VISIBLE);
                error.setVisibility(View.GONE);
            }

            @Override public void onPageFinished(WebView v, String url) {
                progress.setVisibility(View.GONE);
                v.evaluateJavascript(PAGE_CLEANUP, null);
                v.evaluateJavascript(CONTEXT_GUARD, null);
                v.evaluateJavascript(SPEAKER_BUTTONS, null);
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
                if (params.getMode() == FileChooserParams.MODE_OPEN_MULTIPLE) {
                    pick.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
                }
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

    private final class SpeechBridge {
        @JavascriptInterface
        public void speak(String text) {
            OnDeviceTtsController controller = speech;
            if (controller != null) controller.speak(text);
        }

        @JavascriptInterface
        public void stop() {
            OnDeviceTtsController controller = speech;
            if (controller != null) controller.stop();
        }
    }

    private boolean isGoogleHome(Uri uri) {
        if (!googleOrigin(uri.getHost())) return false;
        String path = uri.getPath();
        return path == null || path.isEmpty() || "/".equals(path) || "/webhp".equals(path);
    }

    private void requestWebPermission(PermissionRequest request) {
        String host = Uri.parse(request.getOrigin().toString()).getHost();
        boolean audio = false;
        for (String item : request.getResources()) {
            if (PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(item)) audio = true;
        }
        if (!googleOrigin(host) || !audio) {
            request.deny();
            return;
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            audioRequest = request;
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, MIC_PERMISSION);
        } else {
            request.grant(new String[]{PermissionRequest.RESOURCE_AUDIO_CAPTURE});
        }
    }

    private boolean googleHost(String host) {
        return googleOrigin(host) || domain(host, "gstatic.com") || domain(host, "googleapis.com")
                || domain(host, "googleusercontent.com") || domain(host, "ggpht.com");
    }

    private boolean googleOrigin(String host) {
        return domain(host, "google.com") || domain(host, "google.com.br");
    }

    private boolean domain(String host, String suffix) {
        if (host == null) return false;
        String h = host.toLowerCase();
        return h.equals(suffix) || h.endsWith("." + suffix);
    }

    private LinearLayout createErrorPanel() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setGravity(Gravity.CENTER);
        panel.setPadding(dp(30), dp(24), dp(30), dp(24));
        panel.setBackgroundColor(ContextCompat.getColor(this, R.color.app_background));

        TextView message = new TextView(this);
        message.setText(R.string.connection_error);
        message.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
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
            } else if (data.getData() != null) {
                selected = new Uri[]{data.getData()};
            }
        }
        if (fileCallback != null) {
            fileCallback.onReceiveValue(selected);
            fileCallback = null;
        }
        enterFullscreen();
    }

    @Override public void onRequestPermissionsResult(int code, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(code, permissions, results);
        if (code == MIC_PERMISSION && audioRequest != null) {
            if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) {
                audioRequest.grant(new String[]{PermissionRequest.RESOURCE_AUDIO_CAPTURE});
            } else {
                audioRequest.deny();
            }
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
        if (speech != null) {
            speech.release();
            speech = null;
        }
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