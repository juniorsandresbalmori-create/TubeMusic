package com.tubevideos;

import android.app.DownloadManager;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;

public class MainActivity extends AppCompatActivity {

    private WebView webView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        webView = findViewById(R.id.webView);

        // Configurar WebView
        WebSettings webSettings = webView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setDomStorageEnabled(true);

        webView.setWebViewClient(new WebViewClient());
        webView.setWebChromeClient(new WebChromeClient());

        // Conectar el JavascriptInterface con el nombre exacto que usaste en HTML
        webView.addJavascriptInterface(new WebAppInterface(this), "AndroidBridge");

        // Cargar tu index.html desde la carpeta assets (app/src/main/assets/index.html)
        webView.loadUrl("file:///android_asset/index.html");
    }

    // Clase que hace de puente entre Javascript y Java
    public class WebAppInterface {
        Context mContext;

        WebAppInterface(Context c) {
            mContext = c;
        }

        // Método invocado desde JS: window.AndroidBridge.procesarDescarga(url)
        @JavascriptInterface
        public void procesarDescarga(String url) {
            runOnUiThread(() -> {
                enviarComandoJS("mostrarCargando('Conectando al VPS...')");
                enviarLogDev("Iniciando conexión con VPS para: " + url);
            });
            consultarApiYDescargar(url);
        }

        // Método invocado desde JS: window.AndroidBridge.verAnuncioPorTiempo(segundos)
        @JavascriptInterface
        public void verAnuncioPorTiempo(int segundos) {
            runOnUiThread(() -> {
                enviarLogDev("Preparando anuncio de AdMob...");
                Toast.makeText(mContext, "Lógica de AdMob pendiente", Toast.LENGTH_SHORT).show();
                
                // Simular que el anuncio se vio con éxito y devolver el tiempo al JS
                enviarComandoJS("sumarTiempo(" + segundos + ")");
            });
        }
    }

    // Comunicación desde Java hacia la consola JS de tu diseño
    private void enviarComandoJS(String comando) {
        if (webView != null) {
            webView.evaluateJavascript("javascript:" + comando, null);
        }
    }

    private void enviarLogDev(String mensaje) {
        runOnUiThread(() -> enviarComandoJS("agregarLogDev('" + mensaje + "')"));
    }

    private void consultarApiYDescargar(String youtubeUrlString) {
        new Thread(() -> {
            try {
                String encodedUrl = URLEncoder.encode(youtubeUrlString, "UTF-8");
                String apiEndpoint = "http://190.114.254.167:8000/extract?url=" + encodedUrl;

                URL url = new URL(apiEndpoint);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(15000);
                connection.setReadTimeout(15000);

                int responseCode = connection.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                    reader.close();

                    JSONObject jsonResponse = new JSONObject(response.toString());
                    String title = jsonResponse.optString("title", "audio_tubemusic");
                    String audioDownloadUrl = jsonResponse.optString("url", "");

                    if (!audioDownloadUrl.isEmpty()) {
                        String safeFileName = title.replaceAll("[^a-zA-Z0-9.-]", "_") + ".m4a";
                        
                        runOnUiThread(() -> {
                            enviarLogDev("¡URL de audio extraída con éxito!");
                            iniciarDescargaConManager(audioDownloadUrl, safeFileName);
                            enviarComandoJS("ocultarCargando()");
                        });
                    }
                } else {
                    runOnUiThread(() -> {
                        enviarLogDev("❌ Error en el VPS HTTP: " + responseCode);
                        enviarComandoJS("ocultarCargando()");
                    });
                }
                connection.disconnect();
            } catch (Exception e) {
                Log.e("TubeMusic", "Error de red", e);
                runOnUiThread(() -> {
                    enviarLogDev("❌ Error de conexión con el servidor");
                    enviarComandoJS("ocultarCargando()");
                });
            }
        }).start();
    }

    private void iniciarDescargaConManager(String audioUrl, String fileName) {
        try {
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(audioUrl));
            request.setTitle(fileName);
            request.setDescription("Descargando MP3...");
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName);
            
            // User-Agent obligatorio
            request.addRequestHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
            request.setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI | DownloadManager.Request.NETWORK_MOBILE);
            request.setAllowedOverRoaming(true);

            DownloadManager manager = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
            if (manager != null) {
                long downloadId = manager.enqueue(request);
                enviarLogDev("📥 Descarga encolada. Supervisando...");
                
                // Hilo vigilante para atrapar el error exacto y mandarlo a tu consola HTML
                new Thread(() -> {
                    boolean supervisando = true;
                    while (supervisando) {
                        DownloadManager.Query q = new DownloadManager.Query();
                        q.setFilterById(downloadId);
                        android.database.Cursor cursor = manager.query(q);
                        
                        if (cursor != null && cursor.moveToFirst()) {
                            int statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS);
                            int reasonIndex = cursor.getColumnIndex(DownloadManager.COLUMN_REASON);
                            
                            if (statusIndex >= 0 && reasonIndex >= 0) {
                                int status = cursor.getInt(statusIndex);
                                if (status == DownloadManager.STATUS_SUCCESSFUL) {
                                    runOnUiThread(() -> enviarLogDev("✅ ¡Descarga completada con éxito!"));
                                    supervisando = false;
                                } else if (status == DownloadManager.STATUS_FAILED) {
                                    int reason = cursor.getInt(reasonIndex);
                                    runOnUiThread(() -> enviarLogDev("❌ El sistema bloqueó la descarga. Código de error: " + reason));
                                    supervisando = false;
                                }
                            }
                        }
                        if (cursor != null) cursor.close();
                        
                        try { Thread.sleep(1000); } catch (Exception e) { /* ignorar */ }
                    }
                }).start();
            }
        } catch (Exception e) {
            Log.e("TubeMusic", "Error DownloadManager", e);
            enviarLogDev("❌ Error fatal interno: " + e.getMessage());
        }
    }

