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

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private WebView webView;
    private RewardedAd rewardedAd;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

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

        private void logDev(String mensaje) {
            runOnUiThread(() -> {
                String safeLog = mensaje.replace("'", "\\'").replace("\n", " ");
                webView.evaluateJavascript("if(window.agregarLogDev) { window.agregarLogDev('" + safeLog + "'); }", null);
            });
        }

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
                    Toast.makeText(MainActivity.this, "El anuncio aún no ha cargado.", Toast.LENGTH_SHORT).show();
                    loadRewardedAd();
                }
            });
        }

        @JavascriptInterface
        public void procesarDescarga(String rawUrl) {
            logDev("🚀 [INICIO] Recibida URL: " + rawUrl);
            final String videoId = extraerVideoId(rawUrl);

            if (videoId.isEmpty()) {
                logDev("❌ [ERROR] No se pudo extraer el ID del video.");
                runOnUiThread(() -> {
                    webView.evaluateJavascript("ocultarCargando();", null);
                    mostrarToast("El enlace introducido no es válido.");
                });
                return;
            }

            logDev("📌 [INFO] ID de video extraído: " + videoId);

            runOnUiThread(() -> 
                webView.evaluateJavascript("mostrarCargando('Buscando servidor disponible...');", null)
            );

            Executors.newSingleThreadExecutor().execute(() -> {
                boolean exito = false;

                // 1. MOTOR INVIDIOUS
                logDev("⚡ [MOTOR 1] Probando instancias Invidious...");
                String[] instanciasInvidious = {
                    "https://yewtu.be/api/v1/videos/",
                    "https://inv.tux.pizza/api/v1/videos/",
                    "https://invidious.nerdvpn.de/api/v1/videos/",
                    "https://iv.melmac.space/api/v1/videos/"
                };

                for (String invBase : instanciasInvidious) {
                    logDev("🌐 [Invidious] Probando: " + invBase);
                    if (obtenerAudioDesdeInvidious(invBase + videoId)) {
                        exito = true;
                        break;
                    }
                }

                // 2. MOTOR PIPED (RESPALDO)
                if (!exito) {
                    logDev("⚡ [MOTOR 2] Probando instancias Piped...");
                    String[] instanciasPiped = {
                        "https://pipedapi.adminforge.de/streams/",
                        "https://api.piped.yt/streams/",
                        "https://pipedapi.drgns.space/streams/",
                        "https://piapi.ggtyler.dev/streams/"
                    };

                    for (String apiBase : instanciasPiped) {
                        logDev("🌐 [Piped] Probando: " + apiBase);
                        if (obtenerAudioDesdePiped(apiBase + videoId)) {
                            exito = true;
                            break;
                        }
                    }
                }

                final boolean resultadoFinal = exito;
                runOnUiThread(() -> {
                    webView.evaluateJavascript("ocultarCargando();", null);
                    if (!resultadoFinal) {
                        logDev("❌ [ERROR FINAL] Los servidores disponibles no pudieron procesar el video.");
                        mostrarToast("Error al extraer audio. Revisa la consola.");
                    }
                });
            });
        }

        private String extraerVideoId(String url) {
            if (url == null) return "";
            url = url.trim();

            if (url.contains("youtu.be/")) {
                String[] parts = url.split("youtu.be/");
                if (parts.length > 1) {
                    return parts[1].split("\\?")[0].split("&")[0];
                }
            } else if (url.contains("v=")) {
                String[] parts = url.split("v=");
                if (parts.length > 1) {
                    return parts[1].split("&")[0];
                }
            }
            return "";
        }

        private boolean obtenerAudioDesdeInvidious(String apiUrl) {
            try {
                URL url = new URL(apiUrl);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(8000);
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)");

                int responseCode = conn.getResponseCode();
                logDev("📡 [Invidious] Código HTTP: " + responseCode);

                if (responseCode == 200) {
                    BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = in.readLine()) != null) {
                        response.append(line);
                    }
                    in.close();

                    JSONObject json = new JSONObject(response.toString());
                    String titulo = json.optString("title", "TubeMusic");
                    JSONArray adaptiveFormats = json.optJSONArray("adaptiveFormats");

                    if (adaptiveFormats != null) {
                        for (int i = 0; i < adaptiveFormats.length(); i++) {
                            JSONObject format = adaptiveFormats.getJSONObject(i);
                            String type = format.optString("type", "");
                            if (type.contains("audio/")) {
                                String streamUrl = format.getString("url");
                                logDev("🎵 [Invidious] Audio hallado: " + titulo);
                                iniciarDescargaNativa(streamUrl, titulo);
                                return true;
                            }
                        }
                    }
                }
            } catch (Exception e) {
                logDev("💥 [Invidious Error] " + e.getLocalizedMessage());
            }
            return false;
        }

        private boolean obtenerAudioDesdePiped(String apiUrl) {
            try {
                URL url = new URL(apiUrl);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(8000);
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)");

                int responseCode = conn.getResponseCode();
                logDev("📡 [Piped] Código HTTP: " + responseCode);

                if (responseCode == 200) {
                    BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = in.readLine()) != null) {
                        response.append(line);
                    }
                    in.close();

                    JSONObject json = new JSONObject(response.toString());
                    String titulo = json.optString("title", "TubeMusic");
                    JSONArray audioStreams = json.optJSONArray("audioStreams");

                    if (audioStreams != null && audioStreams.length() > 0) {
                        String directAudioUrl = audioStreams.getJSONObject(0).getString("url");
                        logDev("🎵 [Piped] Audio hallado: " + titulo);
                        if (!directAudioUrl.isEmpty()) {
                            iniciarDescargaNativa(directAudioUrl, titulo);
                            return true;
                        }
                    }
                }
            } catch (Exception e) {
                logDev("💥 [Piped Error] " + e.getLocalizedMessage());
            }
            return false;
        }

        private void iniciarDescargaNativa(String streamUrl, String titulo) {
            try {
                String nombreLimpio = titulo.replaceAll("[^a-zA-Z0-9.-]", "_");
                String nombreArchivo = nombreLimpio + ".m4a";

                logDev("📥 [DESCARGA] Enviando a DownloadManager: " + nombreArchivo);

                DownloadManager.Request request = new DownloadManager.Request(Uri.parse(streamUrl));
                request.setTitle(titulo);
                request.setDescription("Descargando audio...");
                request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, nombreArchivo);

                DownloadManager manager = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
                if (manager != null) {
                    manager.enqueue(request);
                    logDev("🎉 [OK] Descarga iniciada con éxito.");
                    mostrarToast("¡Descarga iniciada!");
                } else {
                    logDev("❌ [ERROR] DownloadManager es NULL.");
                }
            } catch (Exception e) {
                logDev("💥 [EXCEPCIÓN DESCARGA] " + e.getLocalizedMessage());
            }
        }

        private void mostrarToast(String mensaje) {
            runOnUiThread(() -> Toast.makeText(MainActivity.this, mensaje, Toast.LENGTH_SHORT).show());
        }
    }
                       }
