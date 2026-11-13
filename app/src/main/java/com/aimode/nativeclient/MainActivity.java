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
    /*
     * Inserts a compact read-aloud action into the matching assistant answer
     * in the WebView. A MutationObserver adds the action to new answers as the
     * conversation grows; no separate native overlay is placed over the page.
     */
    private static final String SPEAKER_BUTTONS = """
            (function () {
              if (window.__aimodeAudioControlsInstalled) {
                if (window.__aimodeEnsureAudioControls) window.__aimodeEnsureAudioControls();
                return;
              }
              window.__aimodeAudioControlsInstalled = true;

              // Only explicit assistant-message containers are eligible.
              // Generic page selectors and data-testid wildcards can match controls,
              // navigation, related questions, and other non-conversation UI.
              var answerSelector =
                '[data-message-author-role=assistant],[data-role=assistant],[data-turn=assistant],' +
                'model-response,model-response.model-response,.model-response-text,.response-content';

              var style = document.createElement('style');
              style.id = 'aimode-inline-audio-style';
              style.textContent =
                '.aimode-audio-row{display:flex;align-items:center;width:100%;margin:8px 0 2px;padding:0;background:transparent;}' +
                '.aimode-composer-audio-row{display:inline-flex;align-items:center;justify-content:center;flex:0 0 auto;margin:0 4px;}' +
                '.aimode-audio-button{display:inline-flex;align-items:center;justify-content:center;gap:6px;min-width:36px;min-height:34px;padding:6px 10px;border:1px solid rgba(128,128,128,.35);border-radius:18px;background:transparent;color:inherit;font-family:inherit;font-size:13px;font-weight:500;line-height:1.2;white-space:nowrap;cursor:pointer;opacity:.9;box-shadow:none;-webkit-tap-highlight-color:transparent;}' +
                '.aimode-audio-button:active{opacity:.65;}' +
                '.aimode-audio-button[aria-pressed=true]{border-color:#1a73e8;color:#1a73e8;}' +
                '.aimode-audio-icon{font-size:15px;line-height:1;}';
              (document.head || document.documentElement).appendChild(style);

              function visible(node) {
                if (!node || !node.getBoundingClientRect) return false;
                var rect = node.getBoundingClientRect();
                var css = getComputedStyle(node);
                return rect.width > 0 && rect.height > 0 &&
                  css.display !== 'none' && css.visibility !== 'hidden';
              }

              function clean(node) {
                if (!node) return '';
                var clone = node.cloneNode(true);
                // Keep only answer prose. Google adds action bars, feedback,
                // source cards, citation controls and related UI beside answers.
                // Those are not spoken, even when they sit inside the assistant root.
                clone.querySelectorAll(
                  'button,a,svg,script,style,nav,footer,header,form,input,textarea,select,iframe,' +
                  '[role=button],[role=link],[role=menuitem],[role=toolbar],[role=navigation],' +
                  '[aria-hidden=true],[aria-label],[contenteditable=true],' +
                  '[data-testid*=button],[data-testid*=action],[data-testid*=source],' +
                  '[data-testid*=citation],[data-testid*=feedback],[data-testid*=menu],' +
                  '[class*=toolbar],[class*=action],[class*=citation],[class*=source],[class*=menu],' +
                  '.aimode-audio-row,.aimode-composer-audio-row'
                ).forEach(function (item) { item.remove(); });

                return (clone.innerText || clone.textContent || '')
                  .replace(new RegExp(String.fromCharCode(160), 'g'), ' ')
                  .replace(/[ \\t]+/g, ' ')
                  .replace(/\\s{3,}/g, ' ')
                  .trim();
              }

              function findAnswers() {
                var root = document.querySelector('main,[role=main]');
                if (!root) return [];

                // No document/body-wide paragraph fallback: if Google changes its
                // assistant markup, fail closed instead of reading unrelated UI.
                var nodes = Array.from(root.querySelectorAll(answerSelector))
                  .filter(visible)
                  // Ignore assistant wrappers that contain a more specific
                  // assistant response node. This avoids speaking page chrome
                  // and controls attached to an outer conversation container.
                  .filter(function (node) { return !node.querySelector(answerSelector); })
                  .map(function (node) { return {node: node, text: clean(node)}; })
                  .filter(function (item) {
                    return item.text.length >= 20 && item.text.length <= 12000;
                  });

                nodes.sort(function (a, b) {
                  if (a.node === b.node) return 0;
                  return a.node.compareDocumentPosition(b.node) & Node.DOCUMENT_POSITION_FOLLOWING ? -1 : 1;
                });

                return nodes;
              }

              function latestAnswerText() {
                var answers = findAnswers();
                return answers.length ? answers[answers.length - 1].text : '';
              }

              function setActive(button, active) {
                if (!button) return;
                button.setAttribute('aria-pressed', active ? 'true' : 'false');
                button.innerHTML = active
                  ? '<span class="aimode-audio-icon">■</span><span>Parar</span>'
                  : '<span class="aimode-audio-icon">🔊</span><span>Ouvir</span>';
              }

              function makeButton(kind, answerNode) {
                var row = document.createElement('div');
                row.className = kind === 'composer' ? 'aimode-composer-audio-row' : 'aimode-audio-row';
                var button = document.createElement('button');
                button.type = 'button';
                button.className = 'aimode-audio-button';
                button.setAttribute('aria-label', kind === 'composer'
                  ? 'Ouvir a última resposta do Modo IA'
                  : 'Ouvir esta resposta do Modo IA');
                setActive(button, false);
                button.addEventListener('click', function () {
                  if (window.__aimodeCurrentAudioButton === button &&
                      button.getAttribute('aria-pressed') === 'true') {
                    if (window.AIModeSpeech) window.AIModeSpeech.stop();
                    setActive(button, false);
                    window.__aimodeCurrentAudioButton = null;
                    return;
                  }
                  var text = answerNode ? clean(answerNode) : latestAnswerText();
                  if (!text || !window.AIModeSpeech) return;
                  if (window.__aimodeCurrentAudioButton) {
                    setActive(window.__aimodeCurrentAudioButton, false);
                  }
                  window.__aimodeCurrentAudioButton = button;
                  setActive(button, true);
                  window.AIModeSpeech.speak(text);
                });
                row.appendChild(button);
                return row;
              }

              function addAnswerControls() {
                findAnswers().forEach(function (answer) {
                  var node = answer.node;
                  if (!node || node.querySelector(':scope > .aimode-audio-row')) return;
                  node.appendChild(makeButton('answer', node));
                });
              }

              function sendButtonIn(node) {
                var buttons = Array.from(node.querySelectorAll('button,[role=button],input[type=submit]'));
                return buttons.find(function (button) {
                  var label = [
                    button.getAttribute('aria-label') || '',
                    button.getAttribute('title') || '',
                    button.getAttribute('data-testid') || '',
                    button.innerText || button.textContent || '',
                    button.getAttribute('type') === 'submit' ? 'submit' : ''
                  ].join(' ').toLowerCase();
                  return /send|submit|enviar|mandar|perguntar|enviar mensagem|submit query/.test(label);
                }) || null;
              }

              function findComposer() {
                var root = document.querySelector('main,[role=main]') || document.body;
                var editors = Array.from(root.querySelectorAll(
                  'textarea,[contenteditable=true],[role=textbox],input[type=text],input:not([type])'
                )).filter(visible).filter(function (editor) {
                  if (editor.closest('nav,[role=navigation]')) return false;
                  var label = ((editor.getAttribute('placeholder') || '') + ' ' +
                    (editor.getAttribute('aria-label') || '')).toLowerCase();
                  if (/search|pesquisar|buscar/.test(label) && !/ask|pergunt|mensagem/.test(label)) return false;
                  return true;
                });

                editors.sort(function (a, b) {
                  function score(editor) {
                    var rect = editor.getBoundingClientRect();
                    var label = ((editor.getAttribute('placeholder') || '') + ' ' +
                      (editor.getAttribute('aria-label') || '')).toLowerCase();
                    return (rect.bottom / Math.max(1, window.innerHeight)) * 100 +
                      (/ask|pergunt|mensagem|follow.?up|prompt|digite/.test(label) ? 100 : 0);
                  }
                  return score(b) - score(a);
                });

                for (var i = 0; i < editors.length; i++) {
                  var editor = editors[i];
                  var ancestor = editor;
                  var form = editor.closest('form');
                  for (var depth = 0; ancestor && depth < 8; depth++, ancestor = ancestor.parentElement) {
                    var send = sendButtonIn(ancestor);
                    if (send && send.parentElement) {
                      return {host: send.parentElement, before: send, form: form};
                    }
                    if (!form && (ancestor.tagName === 'FORM' || ancestor.getAttribute('role') === 'form')) {
                      form = ancestor;
                    }
                  }
                  if (form) return {host: form, before: null, form: form, editor: editor};
                  var host = editor.parentElement && editor.parentElement.parentElement
                    ? editor.parentElement.parentElement : editor.parentElement;
                  if (host) return {host: host, before: null, form: null, editor: editor};
                }
                return null;
              }

              function addComposerControl() {
                var composer = findComposer();
                if (!composer || !composer.host) return;
                var scope = composer.form || composer.host;
                if (scope.querySelector('.aimode-composer-audio-row')) return;
                var row = makeButton('composer', null);
                if (composer.before && composer.before.parentElement === composer.host) {
                  composer.host.insertBefore(row, composer.before);
                } else if (composer.form && composer.editor) {
                  var anchor = composer.editor.closest('[role=group]') ||
                    composer.editor.parentElement;
                  if (anchor && anchor.parentElement === composer.form) {
                    composer.form.insertBefore(row, anchor.nextSibling);
                  } else {
                    composer.form.appendChild(row);
                  }
                } else {
                  composer.host.appendChild(row);
                }
              }

              function ensureControls() {
                addComposerControl();
                addAnswerControls();
              }

              window.__aimodeEnsureAudioControls = ensureControls;
              window.__aimodeSetSpeechState = function (active) {
                document.querySelectorAll('.aimode-audio-button').forEach(function (button) {
                  setActive(button, !!active && button === window.__aimodeCurrentAudioButton);
                });
                if (!active) window.__aimodeCurrentAudioButton = null;
              };

              ensureControls();
              var scheduled = false;
              var observer = new MutationObserver(function () {
                if (scheduled) return;
                scheduled = true;
                requestAnimationFrame(function () {
                  scheduled = false;
                  ensureControls();
                });
              });
              observer.observe(document.documentElement, {childList: true, subtree: true});
              window.__aimodeAudioObserver = observer;
            })();
            """;


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
        if (speech != null) speech.onResume();
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
        public void setSpeaking(boolean active) {
            WebView current = web;
            if (current != null) {
                current.post(() -> current.evaluateJavascript(
                        "if(window.__aimodeSetSpeechState){window.__aimodeSetSpeechState("
                                + active + ");}", null));
            }
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