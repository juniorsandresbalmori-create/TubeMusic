package com.tubevideos;

import android.Manifest;
import android.app.DownloadManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.rewarded.RewardedAd;
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback;

import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.ServiceList;
import org.schabi.newpipe.extractor.downloader.Downloader;
import org.schabi.newpipe.extractor.downloader.Request;
import org.schabi.newpipe.extractor.downloader.Response;
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException;
import org.schabi.newpipe.extractor.stream.AudioStream;
import org.schabi.newpipe.extractor.stream.VideoStream;
import org.schabi.newpipe.extractor.stream.StreamInfo;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private WebView webView;
    private RewardedAd rewardedAd;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        NewPipe.init(AppDownloader.getInstance());

        solicitarPermisosAlmacenamiento();

        MobileAds.initialize(this, initializationStatus -> {});
        loadRewardedAd();

        webView = findViewById(R.id.webView);
        WebSettings webSettings = webView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setDomStorageEnabled(true);

        webView.addJavascriptInterface(new WebAppInterface(), "AndroidBridge");
        webView.setWebViewClient(new WebViewClient());
        webView.loadUrl("file:///android_asset/index.html");
    }

    private void solicitarPermisosAlmacenamiento() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_MEDIA_AUDIO}, 100);
            }
        } else if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{
                    Manifest.permission.WRITE_EXTERNAL_STORAGE, 
                    Manifest.permission.READ_EXTERNAL_STORAGE
                }, 100);
            }
        }
    }

    private void loadRewardedAd() {
        AdRequest adRequest = new AdRequest.Builder().build();
        RewardedAd.load(this, "ca-app-pub-3940256099942544/5224354917",
            adRequest, new RewardedAdLoadCallback() {
                @Override
                public void onAdFailedToLoad(@NonNull LoadAdError loadAdError) {
                    rewardedAd = null;
                }

                @Override
                public void onAdLoaded(@NonNull RewardedAd ad) {
                    rewardedAd = ad;
                }
            });
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    public class WebAppInterface {

        @JavascriptInterface
        public void verAnuncioPorTiempo(final int segundosAgregar) {
            runOnUiThread(() -> {
                if (rewardedAd != null) {
                    rewardedAd.show(MainActivity.this, rewardItem -> {
                        Toast.makeText(MainActivity.this, "¡Tiempo acreditado!", Toast.LENGTH_SHORT).show();
                        webView.evaluateJavascript("sumarTiempo(" + segundosAgregar + ");", null);
                        loadRewardedAd();
                    });
                } else {
                    Toast.makeText(MainActivity.this, "El anuncio aún no ha cargado. Reintentando...", Toast.LENGTH_SHORT).show();
                    loadRewardedAd();
                }
            });
        }

        @JavascriptInterface
        public void procesarDescarga(String rawUrl) {
            final String videoUrl = limpiarUrlYoutube(rawUrl);

            if (videoUrl.isEmpty()) {
                runOnUiThread(() -> {
                    webView.evaluateJavascript("ocultarCargando();", null);
                    mostrarToast("El enlace introducido no es válido.");
                });
                return;
            }

            runOnUiThread(() -> 
                webView.evaluateJavascript("mostrarCargando('Extrayendo audio desde tu red...');", null)
            );

            Executors.newSingleThreadExecutor().execute(() -> {
                try {
                    StreamInfo info = StreamInfo.getInfo(ServiceList.YouTube, videoUrl);
                    String directAudioUrl = "";

                    // 1. Intentar obtener stream de solo audio
                    List<AudioStream> audioStreams = info.getAudioStreams();
                    if (audioStreams != null && !audioStreams.isEmpty()) {
                        directAudioUrl = audioStreams.get(0).getUrl();
                    } 
                    // 2. Respaldo: obtener el enlace de los streams de video (contienen audio)
                    else {
                        List<VideoStream> videoStreams = info.getVideoStreams();
                        if (videoStreams != null && !videoStreams.isEmpty()) {
                            directAudioUrl = videoStreams.get(0).getUrl();
                        }
                    }

                    final String finalDownloadUrl = directAudioUrl;
                    final String titulo = info.getName();

                    runOnUiThread(() -> {
                        webView.evaluateJavascript("ocultarCargando();", null);
                        if (!finalDownloadUrl.isEmpty()) {
                            iniciarDescargaNativa(finalDownloadUrl, titulo);
                        } else {
                            mostrarToast("No se encontró ningún enlace de audio disponible.");
                        }
                    });

                } catch (Exception e) {
                    e.printStackTrace();
                    runOnUiThread(() -> {
                        webView.evaluateJavascript("ocultarCargando();", null);
                        mostrarToast("Error al extraer enlace: " + e.getLocalizedMessage());
                    });
                }
            });
        }

        private String limpiarUrlYoutube(String url) {
            if (url == null) return "";
            url = url.trim();
            if (url.contains("youtu.be/") && url.contains("si=") && !url.contains("?")) {
                url = url.replace("si=", "?si=");
            }
            if (url.contains("?si=")) {
                url = url.split("\\?si=")[0];
            } else if (url.contains("&si=")) {
                url = url.split("&si=")[0];
            }
            return url;
        }

        private void iniciarDescargaNativa(String streamUrl, String titulo) {
            String nombreLimpio = (titulo != null) ? titulo.replaceAll("[^a-zA-Z0-9.-]", "_") : "TubeMusic";
            String nombreArchivo = nombreLimpio + ".m4a";

            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(streamUrl));
            request.setTitle(titulo != null ? titulo : "TubeMusic Audio");
            request.setDescription("Descargando audio...");
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, nombreArchivo);

            DownloadManager manager = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
            if (manager != null) {
                manager.enqueue(request);
                mostrarToast("¡Descarga iniciada!");
            }
        }

        private void mostrarToast(String mensaje) {
            runOnUiThread(() -> Toast.makeText(MainActivity.this, mensaje, Toast.LENGTH_SHORT).show());
        }
    }

    public static class AppDownloader extends Downloader {
        private static AppDownloader instance;

        public static AppDownloader getInstance() {
            if (instance == null) instance = new AppDownloader();
            return instance;
        }

        @Override
        public Response execute(Request request) throws ReCaptchaException, java.io.IOException {
            URL url = new URL(request.url());
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod(request.httpMethod());
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);

            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36");
            conn.setRequestProperty("Accept-Language", "en-US,en;q=0.9");

            for (Map.Entry<String, List<String>> header : request.headers().entrySet()) {
                for (String value : header.getValue()) {
                    conn.setRequestProperty(header.getKey(), value);
                }
            }

            byte[] data = request.dataToSend();
            if (data != null && data.length > 0) {
                conn.setDoOutput(true);
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(data);
                }
            }

            int responseCode = conn.getResponseCode();
            BufferedReader in = new BufferedReader(new InputStreamReader(
                    responseCode >= 400 ? conn.getErrorStream() : conn.getInputStream()));
            StringBuilder responseBody = new StringBuilder();
            String line;
            while ((line = in.readLine()) != null) {
                responseBody.append(line).append("\n");
            }
            in.close();

            return new Response(responseCode, conn.getResponseMessage(), conn.getHeaderFields(), responseBody.toString(), request.url());
        }
    }
        }
