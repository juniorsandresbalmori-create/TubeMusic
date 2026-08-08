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

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    // IP pública de tu VPS Debian asignada
    private static final String SERVER_IP = "190.114.254.167";
    private static final String SERVER_PORT = "8000";

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
            logDev("🚀 [INICIO] Solicitud recibida: " + rawUrl);

            if (rawUrl == null || rawUrl.trim().isEmpty()) {
                logDev("❌ [ERROR] La URL introducida está vacía.");
                runOnUiThread(() -> {
                    webView.evaluateJavascript("ocultarCargando();", null);
                    mostrarToast("Introduce un enlace válido.");
                });
                return;
            }

            runOnUiThread(() -> 
                webView.evaluateJavascript("mostrarCargando('Conectando a servidor propio...');", null)
            );

            Executors.newSingleThreadExecutor().execute(() -> {
                boolean exito = false;

                try {
                    String encodedUrl = URLEncoder.encode(rawUrl.trim(), "UTF-8");
                    String apiUrl = "http://" + SERVER_IP + ":" + SERVER_PORT + "/extract?url=" + encodedUrl;

                    logDev("🌐 [MI SERVIDOR] Consultando API en: " + apiUrl);

                    URL url = new URL(apiUrl);
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("GET");
                    conn.setConnectTimeout(15000);
                    conn.setReadTimeout(15000);

                    int responseCode = conn.getResponseCode();
                    logDev("📡 [MI SERVIDOR] Código HTTP: " + responseCode);

                    if (responseCode == 200) {
                        BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                        StringBuilder response = new StringBuilder();
                        String line;
                        while ((line = in.readLine()) != null) {
                            response.append(line);
                        }
                        in.close();

                        JSONObject json = new JSONObject(response.toString());
                        String status = json.optString("status", "");
                        String titulo = json.optString("title", "Audio_YouTube");
                        String streamUrl = json.optString("url", "");

                        if ("ok".equals(status) && !streamUrl.isEmpty()) {
                            logDev("🎵 [MI SERVIDOR] Audio extraído con éxito: " + titulo);
                            iniciarDescargaNativa(streamUrl, titulo);
                            exito = true;
                        } else {
                            logDev("❌ [ERROR SERVIDOR] Respuesta no válida o sin URL de audio.");
                        }
                    } else {
                        logDev("❌ [ERROR SERVIDOR] El servidor devolvió código: " + responseCode);
                    }

                } catch (Exception e) {
                    logDev("💥 [EXCEPCIÓN CONEXIÓN] " + e.getLocalizedMessage());
                }

                final boolean resultadoFinal = exito;
                runOnUiThread(() -> {
                    webView.evaluateJavascript("ocultarCargando();", null);
                    if (!resultadoFinal) {
                        logDev("❌ [ERROR FINAL] No se pudo obtener el audio desde el servidor.");
                        mostrarToast("Error al procesar el enlace. Revisa la consola.");
                    }
                });
            });
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
                    logDev("🎉 [OK] Descarga iniciada correctamente.");
                    mostrarToast("¡Descarga iniciada!");
                } else {
                    logDev("❌ [ERROR] DownloadManager no está disponible en el sistema.");
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
