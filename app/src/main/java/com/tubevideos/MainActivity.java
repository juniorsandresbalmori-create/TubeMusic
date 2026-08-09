package com.tubevideos;

import android.app.DownloadManager;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Aquí ejecutas la consulta cuando el usuario presione el botón o pegue la URL
        // Ejemplo de prueba:
        // consultarApiYDescargar("https://youtu.be/-Egz1KL7540?si=KKfnGhYVzqe9V88B");
    }

    private void consultarApiYDescargar(String youtubeUrlString) {
        new Thread(() -> {
            try {
                String encodedUrl = URLEncoder.encode(youtubeUrlString, "UTF-8");
                String apiEndpoint = "http://190.114.254.167:8000/extract?url=" + encodedUrl;

                Log.d("TubeMusic", "Consultando API en: " + apiEndpoint);

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
                    String title = jsonResponse.optString("title", "audio_descargado");
                    String audioDownloadUrl = jsonResponse.optString("url", "");

                    if (!audioDownloadUrl.isEmpty()) {
                        String safeFileName = title.replaceAll("[^a-zA-Z0-9.-]", "_") + ".m4a";
                        runOnUiThread(() -> iniciarDescargaConManager(audioDownloadUrl, safeFileName));
                    } else {
                        Log.e("TubeMusic", "Error: El JSON recibido no contiene la URL de descarga.");
                    }
                } else {
                    Log.e("TubeMusic", "Error HTTP en el servidor: " + responseCode);
                }
                connection.disconnect();

            } catch (Exception e) {
                Log.e("TubeMusic", "Excepción al conectar con el servidor", e);
            }
        }).start();
    }

    private void iniciarDescargaConManager(String audioUrl, String fileName) {
        try {
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(audioUrl));

            request.setTitle(fileName);
            request.setDescription("Descargando música con TubeMusic...");
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName);

            // User-Agent de navegador para evitar bloqueo HTTP 403 de YouTube
            request.addRequestHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");

            request.setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI | DownloadManager.Request.NETWORK_MOBILE);
            request.setAllowedOverRoaming(true);

            DownloadManager manager = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
            if (manager != null) {
                manager.enqueue(request);
                Log.d("TubeMusic", "🎉 [OK] Descarga enviada a DownloadManager: " + fileName);
            }
        } catch (Exception e) {
            Log.e("TubeMusic", "Error al iniciar DownloadManager", e);
        }
    }
                                              }
