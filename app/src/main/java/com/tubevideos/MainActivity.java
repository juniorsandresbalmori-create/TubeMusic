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
import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private WebView webView;
    // La IP de tu VPS
    private static final String SERVER_URL = "http://45.236.130.86:5000/download";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        webView = findViewById(R.id.webView);

        WebSettings webSettings = webView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setDomStorageEnabled(true);

        // Exponer la interfaz 'AndroidBridge' para ser llamada desde JavaScript (index.html)
        webView.addJavascriptInterface(new WebAppInterface(this), "AndroidBridge");
        webView.setWebViewClient(new WebViewClient());

        // Cargar la interfaz HTML local
        webView.loadUrl("file:///android_asset/index.html");
    }

    // Clase puente de comunicación JS <-> Java
    public class WebAppInterface {
        Context mContext;

        WebAppInterface(Context c) {
            mContext = c;
        }

        @JavascriptInterface
        public void startDownload(String youtubeUrl) {
            if (youtubeUrl == null || youtubeUrl.trim().isEmpty()) {
                runOnUiThread(() -> Toast.makeText(mContext, "Por favor ingresa una URL válida", Toast.LENGTH_SHORT).show());
                return;
            }

            runOnUiThread(() -> Toast.makeText(mContext, "Obteniendo enlace de la VPS...", Toast.LENGTH_SHORT).show());

            // Petición a la VPS en hilo secundario
            Executors.newSingleThreadExecutor().execute(() -> {
                try {
                    URL url = new URL(SERVER_URL);
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("POST");
                    conn.setRequestProperty("Content-Type", "application/json; utf-8");
                    conn.setRequestProperty("Accept", "application/json");
                    conn.setConnectTimeout(10000);
                    conn.setReadTimeout(15000);
                    conn.setDoOutput(true);

                    String jsonInputString = "{\"url\": \"" + youtubeUrl + "\"}";

                    try (OutputStream os = conn.getOutputStream()) {
                        byte[] input = jsonInputString.getBytes("utf-8");
                        os.write(input, 0, input.length);
                    }

                    int responseCode = conn.getResponseCode();

                    if (responseCode == HttpURLConnection.HTTP_OK) {
                        BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), "utf-8"));
                        StringBuilder response = new StringBuilder();
                        String responseLine;
                        while ((responseLine = br.readLine()) != null) {
                            response.append(responseLine.trim());
                        }

                        JSONObject jsonResponse = new JSONObject(response.toString());
                        String status = jsonResponse.optString("status");

                        if ("success".equals(status)) {
                            String streamUrl = jsonResponse.getString("stream_url");
                            String title = jsonResponse.optString("title", "Audio");
                            String ext = jsonResponse.optString("ext", "webm");

                            // Iniciar descarga nativa en el dispositivo
                            descargarEnDispositivo(streamUrl, title, ext);
                        } else {
                            mostrarError("Error al procesar la URL en la VPS");
                        }
                    } else {
                        mostrarError("Error de servidor: HTTP " + responseCode);
                    }

                } catch (Exception e) {
                    e.printStackTrace();
                    mostrarError("Error de conexión: " + e.getMessage());
                }
            });
        }
    }

    private void descargarEnDispositivo(String downloadUrl, String title, String extension) {
        String nombreLimpio = title.replaceAll("[\\\\/:*?\"<>|]", "_");
        String nombreArchivo = nombreLimpio + "." + extension;

        DownloadManager.Request request = new DownloadManager.Request(Uri.parse(downloadUrl));
        request.setTitle(title);
        request.setDescription("Descargando canción...");
        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
        request.setDestinationInExternalPublicDir(Environment.DIRECTORY_MUSIC, nombreArchivo);

        DownloadManager manager = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
        if (manager != null) {
            manager.enqueue(request);
            runOnUiThread(() -> Toast.makeText(MainActivity.this, "Descarga iniciada: " + title, Toast.LENGTH_LONG).show());
        }
    }

    private void mostrarError(String mensaje) {
        runOnUiThread(() -> Toast.makeText(MainActivity.this, mensaje, Toast.LENGTH_LONG).show());
    }
                                                   }
