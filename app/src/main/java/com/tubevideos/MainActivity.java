package com.tubevideos;

import android.app.DownloadManager;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.MobileAds;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private EditText etYoutubeUrl;
    private Button btnDownload;
    private ProgressBar progressBar;
    private AdView adView;

    // La IP y endpoint de tu VPS
    private static final String SERVER_URL = "http://45.236.130.86:5000/download";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Vincular elementos del XML nativo
        etYoutubeUrl = findViewById(R.id.etYoutubeUrl);
        btnDownload = findViewById(R.id.btnDownload);
        progressBar = findViewById(R.id.progressBar);
        adView = findViewById(R.id.adView);

        // Inicializar el SDK de anuncios (AdMob)
        MobileAds.initialize(this, initializationStatus -> {});
        AdRequest adRequest = new AdRequest.Builder().build();
        adView.loadAd(adRequest);

        // Configurar acción del botón de descarga
        btnDownload.setOnClickListener(v -> {
            String youtubeUrl = etYoutubeUrl.getText().toString().trim();
            if (youtubeUrl.isEmpty()) {
                Toast.makeText(MainActivity.this, "Por favor ingresa una URL válida", Toast.LENGTH_SHORT).show();
                return;
            }
            consultarVpsYDescargar(youtubeUrl);
        });
    }

    private void consultarVpsYDescargar(String youtubeUrl) {
        // Mostrar animación de carga y bloquear el botón mientras procesa
        runOnUiThread(() -> {
            progressBar.setVisibility(View.VISIBLE);
            btnDownload.setEnabled(false);
            Toast.makeText(MainActivity.this, "Consultando con la VPS...", Toast.LENGTH_SHORT).show();
        });

        // Petición HTTP a la VPS en segundo plano
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

                        // Activar la descarga nativa en el dispositivo
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
            } finally {
                // Restaurar la interfaz (ocultar barra y habilitar botón)
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    btnDownload.setEnabled(true);
                });
            }
        });
    }

    private void descargarEnDispositivo(String downloadUrl, String title, String extension) {
        String nombreLimpio = title.replaceAll("[\\\\/:*?\"<>|]", "_");
        String nombreArchivo = nombreLimpio + "." + extension;

        DownloadManager.Request request = new DownloadManager.Request(Uri.parse(downloadUrl));
        request.setTitle(title);
        request.setDescription("Descargando canción de TubeMusic...");
        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
        request.setDestinationInExternalPublicDir(Environment.DIRECTORY_MUSIC, nombreArchivo);

        DownloadManager manager = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
        if (manager != null) {
            manager.enqueue(request);
            runOnUiThread(() -> {
                Toast.makeText(MainActivity.this, "¡Descarga iniciada!", Toast.LENGTH_LONG).show();
                etYoutubeUrl.setText(""); // Limpiar caja de texto
            });
        }
    }

    private void mostrarError(String mensaje) {
        runOnUiThread(() -> Toast.makeText(MainActivity.this, mensaje, Toast.LENGTH_LONG).show());
    }
                    }
