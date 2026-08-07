package com.tubevideos;

import android.app.DownloadManager;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.rewarded.RewardedAd;
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private WebView webView;
    private RewardedAd rewardedAd;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

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

            runOnUiThread(() -> 
                webView.evaluateJavascript("mostrarCargando('Conectando servidor...');", null)
            );

            Executors.newSingleThreadExecutor().execute(() -> {
                // Lista de instancias publicas alternativas
                String[] instancias = {
                    "https://api.cobalt.tools/",
                    "https://cobalt-api.kwiatekmoments.com/",
                    "https://cobalt.yts.rest/",
                    "https://cobalt.streamin.me/"
                };

                boolean exito = false;
                for (String api : instancias) {
                    exito = consultarApiCobalt(api, videoUrl);
                    if (exito) break;
                }

                final boolean resultado = exito;
                runOnUiThread(() -> {
                    webView.evaluateJavascript("ocultarCargando();", null);
                    if (!resultado) {
                        mostrarToast("Servidores de conversión ocupados. Intenta con otro video o reintenta en unos instantes.");
                    }
                });
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

        private boolean consultarApiCobalt(String apiUrl, String videoUrl) {
            try {
                URL url = new URL(apiUrl);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setRequestProperty("Accept", "application/json");
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36");
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(12000);
                conn.setDoOutput(true);

                JSONObject body = new JSONObject();
                body.put("url", videoUrl);
                body.put("downloadMode", "audio");
                body.put("audioFormat", "mp3");

                OutputStream os = conn.getOutputStream();
                os.write(body.toString().getBytes(StandardCharsets.UTF_8));
                os.flush();
                os.close();

                int responseCode = conn.getResponseCode();
                if (responseCode == 200 || responseCode == 201) {
                    BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = in.readLine()) != null) {
                        response.append(line);
                    }
                    in.close();

                    JSONObject jsonResponse = new JSONObject(response.toString());
                    
                    String downloadUrl = jsonResponse.optString("url");
                    if (downloadUrl.isEmpty() && jsonResponse.has("picker")) {
                        downloadUrl = jsonResponse.optJSONArray("picker").getJSONObject(0).optString("url");
                    }

                    if (!downloadUrl.isEmpty()) {
                        iniciarDescargaNativa(downloadUrl);
                        return true;
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            return false;
        }

        private void iniciarDescargaNativa(String streamUrl) {
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(streamUrl));
            request.setTitle("TubeMusic MP3");
            request.setDescription("Descargando audio...");
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "TubeMusic_" + System.currentTimeMillis() + ".mp3");

            DownloadManager manager = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
            if (manager != null) {
                manager.enqueue(request);
                mostrarToast("¡Descarga iniciada! Revisá tus notificaciones.");
            }
        }

        private void mostrarToast(String mensaje) {
            runOnUiThread(() -> Toast.makeText(MainActivity.this, mensaje, Toast.LENGTH_SHORT).show());
        }
    }
                        }
        
