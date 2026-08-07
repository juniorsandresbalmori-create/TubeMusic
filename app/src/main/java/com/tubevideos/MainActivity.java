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

import com.yausername.ffmpeg.FFmpeg;
import com.yausername.youtubedl_android.YoutubeDL;
import com.yausername.youtubedl_android.YoutubeDLException;
import com.yausername.youtubedl_android.YoutubeDLRequest;
import com.yausername.youtubedl_android.mapper.VideoInfo;

import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private WebView webView;
    private RewardedAd rewardedAd;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Inicialización del motor local yt-dlp y FFmpeg
        try {
            YoutubeDL.getInstance().init(getApplicationContext());
            FFmpeg.getInstance().init(getApplicationContext());
        } catch (YoutubeDLException e) {
            e.printStackTrace();
        }

        // Solicitud emergente de permisos de almacenamiento
        solicitarPermisosAlmacenamiento();

        // Configuración de AdMob y WebView
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
                webView.evaluateJavascript("mostrarCargando('Procesando video localmente...');", null)
            );

            Executors.newSingleThreadExecutor().execute(() -> {
                try {
                    // Petición local usando la red directa del dispositivo
                    YoutubeDLRequest request = new YoutubeDLRequest(videoUrl);
                    request.addOption("-f", "bestaudio[ext=m4a]/bestaudio");

                    VideoInfo streamInfo = YoutubeDL.getInstance().getInfo(request);
                    String directAudioUrl = streamInfo.getUrl();
                    String titulo = streamInfo.getTitle();

                    runOnUiThread(() -> webView.evaluateJavascript("ocultarCargando();", null));

                    if (directAudioUrl != null && !directAudioUrl.isEmpty()) {
                        iniciarDescargaNativa(directAudioUrl, titulo);
                    } else {
                        mostrarToast("No se pudo obtener la URL de audio.");
                    }

                } catch (Exception e) {
                    e.printStackTrace();
                    runOnUiThread(() -> {
                        webView.evaluateJavascript("ocultarCargando();", null);
                        mostrarToast("Error en extracción: " + e.getLocalizedMessage());
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
            }
