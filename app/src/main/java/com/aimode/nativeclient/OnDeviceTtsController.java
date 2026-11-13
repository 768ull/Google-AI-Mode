package com.aimode.nativeclient;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.os.Handler;
import android.os.Looper;
import android.webkit.WebView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.google.mediapipe.tasks.audio.texttospeech.AudioResult;
import com.google.mediapipe.tasks.audio.texttospeech.TextToSpeech;
import com.google.mediapipe.tasks.audio.texttospeech.TextToSpeechOptions;
import com.google.mediapipe.tasks.core.BaseOptions;
import com.google.mediapipe.tasks.core.Delegate;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * On-device Brazilian Portuguese speech using Google's MediaPipe Text-to-Speech
 * task and the PocketTTS 100M INT8 model. The model is downloaded once and kept
 * in the app-private files directory; inference and playback then work offline.
 */
public final class OnDeviceTtsController {
    private static final String MODEL_NAME = "pocket_tts_pt_int8.litertlm";
    private static final String MODEL_URL =
            "https://huggingface.co/litert-community/pocket-tts/resolve/main/"
                    + MODEL_NAME + "?download=true";
    private static final long MIN_MODEL_BYTES = 110L * 1024L * 1024L;
    private static final long EXPECTED_MODEL_BYTES = 126_000_000L;

    private final Activity activity;
    private final WebView webView;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final AtomicInteger requestGeneration = new AtomicInteger(0);

    private volatile TextToSpeech synthesizer;
    private volatile AudioTrack audioTrack;
    private volatile HttpURLConnection activeConnection;
    private volatile AtomicBoolean downloadCancelled = new AtomicBoolean(false);
    private volatile boolean destroyed;

    private AlertDialog promptDialog;
    private AlertDialog progressDialog;
    private ProgressBar downloadProgress;
    private TextView downloadStatus;

    public OnDeviceTtsController(Activity activity, WebView webView) {
        this.activity = activity;
        this.webView = webView;
    }

    public void speak(String rawText) {
        final String text = cleanText(rawText);
        if (text.isEmpty()) {
            showToast("Não encontrei texto para ouvir.");
            return;
        }

        mainHandler.post(() -> {
            if (destroyed || activity.isFinishing()) return;
            final int requestId = requestGeneration.incrementAndGet();
            cancelActiveDownload();
            stopCurrentOutput();
            setPageSpeaking(true);

            File model = modelFile();
            if (!isValidModel(model)) {
                showDownloadPrompt(text, requestId);
            } else {
                showToast("Preparando a voz IA em português…");
                executeWorker(() -> synthesizeAndPlay(text, requestId));
            }
        });
    }

    public void stop() {
        mainHandler.post(() -> {
            requestGeneration.incrementAndGet();
            cancelActiveDownload();
            stopCurrentOutput();
            dismissDialogs();
            setPageSpeaking(false);
        });
    }

    public void release() {
        destroyed = true;
        requestGeneration.incrementAndGet();
        cancelActiveDownload();
        stopCurrentOutput();
        dismissDialogs();
        TextToSpeech current = synthesizer;
        synthesizer = null;
        if (current != null) {
            try {
                current.close();
            } catch (Exception ignored) {
            }
        }
        worker.shutdownNow();
    }

    private File modelFile() {
        return new File(activity.getFilesDir(), MODEL_NAME);
    }

    private boolean isValidModel(File file) {
        return file.isFile() && file.length() >= MIN_MODEL_BYTES;
    }

    private void showDownloadPrompt(String text, int requestId) {
        if (destroyed || requestId != requestGeneration.get()) return;

        promptDialog = new AlertDialog.Builder(activity)
                .setTitle("Voz IA em português")
                .setMessage("Para ouvir as respostas com a voz neural PocketTTS do Google, "
                        + "o aplicativo precisa baixar o modelo português (Brasil), com cerca "
                        + "de 126 MB. O download acontece uma única vez; depois a voz funciona "
                        + "sem internet. Deseja baixar agora?")
                .setNegativeButton("Agora não", (dialog, which) -> {
                    if (requestId == requestGeneration.get()) {
                        setPageSpeaking(false);
                    }
                })
                .setPositiveButton("Baixar modelo", (dialog, which) ->
                        beginDownload(text, requestId))
                .setOnCancelListener(dialog -> {
                    if (requestId == requestGeneration.get()) {
                        setPageSpeaking(false);
                    }
                })
                .create();
        promptDialog.show();
    }

    private void beginDownload(String text, int requestId) {
        if (destroyed || requestId != requestGeneration.get()) return;
        downloadCancelled = new AtomicBoolean(false);

        LinearLayout layout = new LinearLayout(activity);
        layout.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(22);
        layout.setPadding(pad, dp(8), pad, dp(12));

        downloadStatus = new TextView(activity);
        downloadStatus.setText("Conectando ao repositório do modelo…");
        layout.addView(downloadStatus, new LinearLayout.LayoutParams(-1, -2));

        downloadProgress = new ProgressBar(activity, null,
                android.R.attr.progressBarStyleHorizontal);
        downloadProgress.setMax(100);
        downloadProgress.setIndeterminate(true);
        LinearLayout.LayoutParams progressParams =
                new LinearLayout.LayoutParams(-1, dp(8));
        progressParams.topMargin = dp(14);
        layout.addView(downloadProgress, progressParams);

        progressDialog = new AlertDialog.Builder(activity)
                .setTitle("Baixando modelo de voz")
                .setView(layout)
                .setNegativeButton("Cancelar", (dialog, which) -> stop())
                .setCancelable(false)
                .create();
        progressDialog.show();

        executeWorker(() -> downloadModelAndSpeak(text, requestId));
    }

    private void downloadModelAndSpeak(String text, int requestId) {
        File destination = modelFile();
        File partial = new File(activity.getFilesDir(), MODEL_NAME + ".download");
        HttpURLConnection connection = null;
        try {
            if (isValidModel(destination)) {
                postToMain(() -> dismissProgressDialog());
                synthesizeAndPlay(text, requestId);
                return;
            }

            if (partial.exists()) partial.delete();
            connection = (HttpURLConnection) new URL(MODEL_URL).openConnection();
            activeConnection = connection;
            connection.setInstanceFollowRedirects(true);
            connection.setConnectTimeout(30_000);
            connection.setReadTimeout(90_000);
            connection.setRequestProperty("User-Agent", "Google-AI-Mode-Android/1.0");
            connection.setRequestProperty("Accept", "application/octet-stream");
            connection.connect();

            int response = connection.getResponseCode();
            if (response != HttpURLConnection.HTTP_OK) {
                throw new IllegalStateException("O servidor retornou HTTP " + response + ".");
            }

            long total = connection.getContentLengthLong();
            postToMain(() -> {
                if (downloadProgress != null) downloadProgress.setIndeterminate(false);
                updateDownloadLabel(0, total > 0 ? total : EXPECTED_MODEL_BYTES);
            });

            long downloaded = 0L;
            long lastUiUpdate = 0L;
            byte[] buffer = new byte[64 * 1024];
            try (InputStream input = new BufferedInputStream(connection.getInputStream());
                 BufferedOutputStream output =
                         new BufferedOutputStream(new FileOutputStream(partial))) {
                int count;
                while ((count = input.read(buffer)) != -1) {
                    if (downloadCancelled.get() || requestId != requestGeneration.get()) {
                        throw new InterruptedIOException("Download cancelado.");
                    }
                    output.write(buffer, 0, count);
                    downloaded += count;
                    long now = System.currentTimeMillis();
                    if (now - lastUiUpdate >= 300) {
                        final long current = downloaded;
                        final long expected = total > 0 ? total : EXPECTED_MODEL_BYTES;
                        postToMain(() -> updateDownloadLabel(current, expected));
                        lastUiUpdate = now;
                    }
                }
            }

            if (downloadCancelled.get() || requestId != requestGeneration.get()) {
                throw new InterruptedIOException("Download cancelado.");
            }
            if (partial.length() < MIN_MODEL_BYTES) {
                throw new IllegalStateException("O arquivo baixado está incompleto ("
                        + partial.length() + " bytes).");
            }
            if (destination.exists() && !destination.delete()) {
                throw new IllegalStateException("Não foi possível substituir o modelo anterior.");
            }
            if (!partial.renameTo(destination)) {
                throw new IllegalStateException("Não foi possível salvar o modelo no aparelho.");
            }

            postToMain(() -> {
                dismissProgressDialog();
                showToast("Modelo de voz instalado. Gerando áudio…");
            });
            synthesizeAndPlay(text, requestId);
        } catch (Exception error) {
            partial.delete();
            if (requestId == requestGeneration.get() && !downloadCancelled.get()) {
                String message = error.getMessage() == null
                        ? error.getClass().getSimpleName() : error.getMessage();
                postToMain(() -> {
                    dismissProgressDialog();
                    showToast("Não foi possível baixar o modelo: " + message);
                    setPageSpeaking(false);
                });
            } else {
                postToMain(() -> {
                    dismissProgressDialog();
                    setPageSpeaking(false);
                });
            }
        } finally {
            activeConnection = null;
            if (connection != null) connection.disconnect();
        }
    }

    private void updateDownloadLabel(long current, long total) {
        if (downloadProgress == null || downloadStatus == null) return;
        if (total > 0) {
            int percent = (int) Math.min(100L, (current * 100L) / total);
            downloadProgress.setIndeterminate(false);
            downloadProgress.setProgress(percent);
            downloadStatus.setText(String.format(Locale.getDefault(),
                    "Baixados %.1f MB de aproximadamente %.1f MB (%d%%)",
                    current / 1_000_000.0, total / 1_000_000.0, percent));
        } else {
            downloadProgress.setIndeterminate(true);
            downloadStatus.setText(String.format(Locale.getDefault(),
                    "Baixados %.1f MB…", current / 1_000_000.0));
        }
    }

    private void synthesizeAndPlay(String text, int requestId) {
        if (destroyed || requestId != requestGeneration.get()) return;
        try {
            TextToSpeech engine = synthesizer;
            if (engine == null) {
                BaseOptions baseOptions = BaseOptions.builder()
                        .setModelAssetPath(modelFile().getAbsolutePath())
                        .setDelegate(Delegate.CPU)
                        .build();
                TextToSpeechOptions options = new TextToSpeechOptions(
                        baseOptions,
                        "rafael",
                        "pt-BR",
                        1.0f,
                        0,
                        activity.getCacheDir().getAbsolutePath()
                );
                engine = TextToSpeech.create(options);
                if (destroyed || requestId != requestGeneration.get()) {
                    engine.close();
                    return;
                }
                synthesizer = engine;
            }

            AudioResult result = engine.synthesize(text);
            if (destroyed || requestId != requestGeneration.get()) return;
            playPcm(result.getSamples(), result.getSampleRateHz(), requestId);
        } catch (Exception error) {
            if (!destroyed && requestId == requestGeneration.get()) {
                String message = error.getMessage() == null
                        ? error.getClass().getSimpleName() : error.getMessage();
                postToMain(() -> showToast("Erro ao gerar a voz: " + message));
            }
        } finally {
            if (requestId == requestGeneration.get()) {
                postToMain(() -> setPageSpeaking(false));
            }
        }
    }

    private void playPcm(float[] samples, int sampleRate, int requestId) {
        if (samples == null || samples.length == 0) {
            throw new IllegalStateException("O modelo não retornou áudio.");
        }

        int minimumBuffer = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
        );
        if (minimumBuffer <= 0) minimumBuffer = 16 * 1024;

        AudioTrack output = new AudioTrack.Builder()
                .setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build())
                .setAudioFormat(new AudioFormat.Builder()
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .build())
                .setTransferMode(AudioTrack.MODE_STREAM)
                .setBufferSizeInBytes(Math.max(minimumBuffer, 16 * 1024))
                .build();

        audioTrack = output;
        try {
            if (output.getState() != AudioTrack.STATE_INITIALIZED) {
                throw new IllegalStateException("O áudio não pôde ser inicializado.");
            }
            output.play();
            short[] pcm = new short[4096];

            for (int offset = 0; offset < samples.length; offset += pcm.length) {
                if (destroyed || requestId != requestGeneration.get()) return;
                int count = Math.min(pcm.length, samples.length - offset);
                for (int i = 0; i < count; i++) {
                    float value = Math.max(-1.0f, Math.min(1.0f, samples[offset + i]));
                    pcm[i] = (short) Math.round(value * 32767.0f);
                }
                int written = output.write(pcm, 0, count, AudioTrack.WRITE_BLOCKING);
                if (written < 0) {
                    throw new IllegalStateException("Falha ao reproduzir o áudio (" + written + ").");
                }
            }
            if (requestId == requestGeneration.get()
                    && output.getPlayState() == AudioTrack.PLAYSTATE_PLAYING) {
                output.stop();
            }
        } finally {
            audioTrack = null;
            try {
                output.release();
            } catch (Exception ignored) {
            }
        }
    }

    private void stopCurrentOutput() {
        TextToSpeech current = synthesizer;
        if (current != null) {
            try {
                current.cancel();
            } catch (Exception ignored) {
            }
        }

        AudioTrack currentTrack = audioTrack;
        if (currentTrack != null) {
            try {
                currentTrack.pause();
                currentTrack.flush();
                currentTrack.stop();
            } catch (Exception ignored) {
            }
        }
    }

    private void cancelActiveDownload() {
        downloadCancelled.set(true);
        HttpURLConnection connection = activeConnection;
        if (connection != null) connection.disconnect();
    }

    private void dismissDialogs() {
        dismissProgressDialog();
        if (promptDialog != null) {
            try {
                promptDialog.dismiss();
            } catch (Exception ignored) {
            }
            promptDialog = null;
        }
    }

    private void dismissProgressDialog() {
        if (progressDialog != null) {
            try {
                progressDialog.dismiss();
            } catch (Exception ignored) {
            }
            progressDialog = null;
        }
        downloadProgress = null;
        downloadStatus = null;
    }

    private void setPageSpeaking(boolean speaking) {
        if (webView == null || destroyed) return;
        String script = "if(window.__aimodeSetSpeechState){window.__aimodeSetSpeechState("
                + speaking + ");}";
        webView.post(() -> {
            try {
                webView.evaluateJavascript(script, null);
            } catch (Exception ignored) {
            }
        });
    }

    private void postToMain(Runnable action) {
        mainHandler.post(() -> {
            if (!destroyed && !activity.isFinishing()) action.run();
        });
    }

    private void executeWorker(Runnable action) {
        try {
            worker.execute(action);
        } catch (Exception error) {
            showToast("O serviço de áudio não está disponível.");
            setPageSpeaking(false);
        }
    }

    private void showToast(String message) {
        postToMain(() -> Toast.makeText(activity, message, Toast.LENGTH_LONG).show());
    }

    private int dp(int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }

    private String cleanText(String raw) {
        if (raw == null) return "";
        String text = raw
                .replaceAll("(?s)```.*?```", " ")
                .replaceAll("!\\[([^\\]]*)\\]\\([^)]*\\)", "$1")
                .replaceAll("\\[([^\\]]+)\\]\\([^)]*\\)", "$1")
                .replaceAll("(?m)^\\s{0,3}#{1,6}\\s*", "")
                .replaceAll("(?m)^\\s*[-*_]{3,}\\s*$", " ")
                .replaceAll("[*_~`]", "")
                .replaceAll("<[^>]+>", " ")
                .replaceAll("\\s+", " ")
                .trim();
        if (text.length() > 12000) {
            text = text.substring(0, 12000).trim() + "…";
        }
        return text;
    }
}
