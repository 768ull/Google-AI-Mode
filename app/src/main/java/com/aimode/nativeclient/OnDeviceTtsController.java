package com.aimode.nativeclient;

import android.app.Activity;
import android.content.Intent;
import android.media.AudioAttributes;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.speech.tts.Voice;
import android.webkit.WebView;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Lightweight speech output through the Android TTS service.
 *
 * Prefer Google's Speech Services when installed, but allow the system default
 * TTS engine as a fallback. Unlike the embedded PocketTTS runtime, this does
 * not load a 100M-parameter model into the WebView application's process.
 */
public final class OnDeviceTtsController {
    private static final String GOOGLE_TTS_PACKAGE = "com.google.android.tts";
    private static final int MAX_CHUNK_CHARS = 2800;
    private static final int MAX_TOTAL_CHARS = 9000;

    private final Activity activity;
    private final WebView webView;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private volatile TextToSpeech engine;
    private volatile boolean released;
    private boolean engineReady;
    private boolean tryingGoogleEngine;
    private boolean defaultFallbackTried;
    private int requestGeneration;
    private int completedChunks;
    private int expectedChunks;
    private String pendingText;

    public OnDeviceTtsController(Activity activity, WebView webView) {
        this.activity = activity;
        this.webView = webView;
        removeLegacyPocketTtsFiles();
        initializeEngine(googleEngineInstalled());
    }

    public void speak(String rawText) {
        final String text = cleanText(rawText);
        if (text.isEmpty()) {
            showToast("Não encontrei texto para ouvir.");
            return;
        }

        mainHandler.post(() -> {
            if (released || activity.isFinishing()) return;

            requestGeneration++;
            int requestId = requestGeneration;
            TextToSpeech current = engine;
            if (current != null) {
                try {
                    current.stop();
                } catch (Exception ignored) {
                }
            }

            completedChunks = 0;
            expectedChunks = 0;
            pendingText = text;
            setPageSpeaking(true);

            if (engineReady) {
                speakPendingText(requestId);
            } else {
                showToast("Preparando a voz em português…");
            }
        });
    }

    public void stop() {
        mainHandler.post(() -> {
            requestGeneration++;
            pendingText = null;
            TextToSpeech current = engine;
            if (current != null) {
                try {
                    current.stop();
                } catch (Exception ignored) {
                }
            }
            setPageSpeaking(false);
        });
    }

    public void release() {
        released = true;
        requestGeneration++;
        pendingText = null;
        TextToSpeech current = engine;
        engine = null;
        engineReady = false;
        if (current != null) {
            try {
                current.stop();
            } catch (Exception ignored) {
            }
            try {
                current.shutdown();
            } catch (Exception ignored) {
            }
        }
        mainHandler.removeCallbacksAndMessages(null);
    }

    private void initializeEngine(boolean preferGoogle) {
        if (released) return;
        tryingGoogleEngine = preferGoogle;

        try {
            TextToSpeech.OnInitListener listener = status ->
                    mainHandler.post(() -> onEngineInitialized(status, preferGoogle));
            if (preferGoogle) {
                engine = new TextToSpeech(activity, listener, GOOGLE_TTS_PACKAGE);
            } else {
                engine = new TextToSpeech(activity, listener);
            }
        } catch (Exception error) {
            if (preferGoogle && !defaultFallbackTried) {
                defaultFallbackTried = true;
                initializeEngine(false);
            } else {
                showToast("Não foi possível iniciar o serviço de voz do Android.");
                setPageSpeaking(false);
            }
        }
    }

    private void onEngineInitialized(int status, boolean wasGoogleEngine) {
        if (released || activity.isFinishing()) return;

        if (status != TextToSpeech.SUCCESS) {
            if (wasGoogleEngine && !defaultFallbackTried) {
                defaultFallbackTried = true;
                TextToSpeech failed = engine;
                engine = null;
                if (failed != null) {
                    try {
                        failed.shutdown();
                    } catch (Exception ignored) {
                    }
                }
                initializeEngine(false);
                return;
            }
            engineReady = false;
            showToast("O mecanismo de voz não iniciou. Verifique o mecanismo de texto para voz nas configurações do Android.");
            setPageSpeaking(false);
            return;
        }

        TextToSpeech current = engine;
        if (current == null) return;

        try {
            current.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build());
            current.setSpeechRate(1.0f);
            current.setPitch(1.0f);

            Locale portuguese = Locale.forLanguageTag("pt-BR");
            int languageStatus = current.setLanguage(portuguese);
            if (languageStatus == TextToSpeech.LANG_MISSING_DATA
                    || languageStatus == TextToSpeech.LANG_NOT_SUPPORTED) {
                engineReady = false;
                showToast("A voz Português (Brasil) não está instalada. Abrindo as opções de voz…");
                openVoiceDataInstaller();
                setPageSpeaking(false);
                return;
            }

            selectBrazilianVoice(current, portuguese);
            current.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                @Override public void onStart(String utteranceId) {
                    // Audio playback runs inside the system TTS service.
                }

                @Override public void onDone(String utteranceId) {
                    mainHandler.post(() -> onUtteranceFinished(utteranceId, false));
                }

                @Override public void onError(String utteranceId) {
                    mainHandler.post(() -> onUtteranceFinished(utteranceId, true));
                }

                @Override public void onError(String utteranceId, int errorCode) {
                    mainHandler.post(() -> onUtteranceFinished(utteranceId, true));
                }

                @Override public void onStop(String utteranceId, boolean interrupted) {
                    mainHandler.post(() -> onUtteranceFinished(utteranceId, true));
                }
            });
            engineReady = true;

            if (pendingText != null) {
                speakPendingText(requestGeneration);
            }
        } catch (Exception error) {
            engineReady = false;
            showToast("Não foi possível preparar a voz Português (Brasil).");
            setPageSpeaking(false);
        }
    }

    private void selectBrazilianVoice(TextToSpeech current, Locale portuguese) {
        try {
            Set<Voice> voices = current.getVoices();
            if (voices == null) return;

            Voice best = null;
            for (Voice voice : voices) {
                Locale locale = voice.getLocale();
                if (locale == null || !"pt".equalsIgnoreCase(locale.getLanguage())) continue;

                boolean brazil = "BR".equalsIgnoreCase(locale.getCountry());
                boolean networkOnly = voice.getFeatures() != null
                        && voice.getFeatures().contains(TextToSpeech.Engine.KEY_FEATURE_NETWORK_SYNTHESIS);
                if (!brazil) continue;

                if (best == null
                        || (best.getFeatures() != null
                            && best.getFeatures().contains(TextToSpeech.Engine.KEY_FEATURE_NETWORK_SYNTHESIS)
                            && !networkOnly)
                        || voice.getQuality() > best.getQuality()) {
                    best = voice;
                }
            }

            if (best != null) current.setVoice(best);
            else current.setLanguage(portuguese);
        } catch (Exception ignored) {
            // Keep the engine's default pt-BR voice selected by setLanguage().
        }
    }

    private void speakPendingText(int requestId) {
        if (released || !engineReady || requestId != requestGeneration || pendingText == null) return;

        TextToSpeech current = engine;
        if (current == null) return;

        List<String> chunks = splitText(pendingText, MAX_CHUNK_CHARS);
        pendingText = null;
        expectedChunks = chunks.size();
        completedChunks = 0;

        if (chunks.isEmpty()) {
            setPageSpeaking(false);
            return;
        }

        for (int i = 0; i < chunks.size(); i++) {
            if (released || requestId != requestGeneration) return;
            String utteranceId = "aimode-" + requestId + "-" + i;
            int queueMode = i == 0 ? TextToSpeech.QUEUE_FLUSH : TextToSpeech.QUEUE_ADD;
            int result;
            try {
                result = current.speak(chunks.get(i), queueMode, null, utteranceId);
            } catch (Exception error) {
                result = TextToSpeech.ERROR;
            }

            if (result != TextToSpeech.SUCCESS) {
                requestGeneration++;
                try {
                    current.stop();
                } catch (Exception ignored) {
                }
                showToast("O serviço de voz recusou o texto. Tente novamente.");
                setPageSpeaking(false);
                return;
            }
        }
    }

    private void onUtteranceFinished(String utteranceId, boolean failed) {
        if (released || utteranceId == null) return;
        String prefix = "aimode-" + requestGeneration + "-";
        if (!utteranceId.startsWith(prefix)) return;

        if (failed) {
            requestGeneration++;
            TextToSpeech current = engine;
            if (current != null) {
                try {
                    current.stop();
                } catch (Exception ignored) {
                }
            }
            showToast("O mecanismo de voz não conseguiu reproduzir esta resposta.");
            setPageSpeaking(false);
            return;
        }

        completedChunks++;
        if (completedChunks >= expectedChunks) {
            setPageSpeaking(false);
        }
    }

    private List<String> splitText(String text, int maxChars) {
        List<String> chunks = new ArrayList<>();
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(text.length(), start + maxChars);
            if (end < text.length()) {
                int sentenceBreak = Math.max(
                        Math.max(text.lastIndexOf(". ", end), text.lastIndexOf("! ", end)),
                        Math.max(text.lastIndexOf("? ", end), text.lastIndexOf("\n", end)));
                if (sentenceBreak >= start + maxChars / 2) {
                    end = sentenceBreak + 1;
                } else {
                    int space = text.lastIndexOf(' ', end);
                    if (space >= start + maxChars / 2) end = space;
                }
            }

            // Do not split a UTF-16 surrogate pair.
            if (end < text.length() && end > start
                    && Character.isHighSurrogate(text.charAt(end - 1))
                    && Character.isLowSurrogate(text.charAt(end))) {
                end--;
            }

            String part = text.substring(start, end).trim();
            if (!part.isEmpty()) chunks.add(part);
            start = end;
            while (start < text.length() && Character.isWhitespace(text.charAt(start))) start++;
        }
        return chunks;
    }

    private boolean googleEngineInstalled() {
        try {
            activity.getPackageManager().getPackageInfo(GOOGLE_TTS_PACKAGE, 0);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private void openVoiceDataInstaller() {
        try {
            Intent intent = new Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            activity.startActivity(intent);
        } catch (Exception ignored) {
            showToast("Instale uma voz Português (Brasil) em Configurações > Texto para voz.");
        }
    }

    private void removeLegacyPocketTtsFiles() {
        try {
            File oldModel = new File(activity.getFilesDir(), "pocket_tts_pt_int8.litertlm");
            if (oldModel.isFile()) oldModel.delete();
            File partial = new File(activity.getFilesDir(), "pocket_tts_pt_int8.litertlm.download");
            if (partial.isFile()) partial.delete();
        } catch (Exception ignored) {
        }
    }

    private void setPageSpeaking(boolean speaking) {
        if (webView == null || released) return;
        String script = "if(window.__aimodeSetSpeechState){window.__aimodeSetSpeechState("
                + speaking + ");}";
        webView.post(() -> {
            try {
                webView.evaluateJavascript(script, null);
            } catch (Exception ignored) {
            }
        });
    }

    private void showToast(String message) {
        mainHandler.post(() -> {
            if (!released && !activity.isFinishing()) {
                Toast.makeText(activity, message, Toast.LENGTH_LONG).show();
            }
        });
    }

    private String cleanText(String raw) {
        if (raw == null) return "";
        String text = raw
                .replaceAll("(?s)\u0060\u0060\u0060.*?\u0060\u0060\u0060", " ")
                .replaceAll("!\\[([^\\]]*)\\]\\([^)]*\\)", "$1")
                .replaceAll("\\[([^\\]]+)\\]\\([^)]*\\)", "$1")
                .replaceAll("(?m)^\\s{0,3}#{1,6}\\s*", "")
                .replaceAll("(?m)^\\s*[-*_]{3,}\\s*$", " ")
                .replaceAll("[*_~\\u0060]", "")
                .replaceAll("<[^>]+>", " ")
                .replaceAll("\\s+", " ")
                .trim();
        if (text.length() > MAX_TOTAL_CHARS) {
            text = text.substring(0, MAX_TOTAL_CHARS).trim() + "…";
        }
        return text;
    }
}
