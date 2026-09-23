package com.tubevideos;

import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
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

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    // Endpoint en tu VPS de Ubuntu
    private static final String VPS_DOWNLOAD_URL = "http://45.236.130.86:5000/download";

    private EditText etYoutubeUrl;
    private Button btnDownload;
    private ProgressBar progressBar;
    private AdView adView;

    // Manejo de hilos en segundo plano para peticiones de red
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 1. Vinculación de vistas
        etYoutubeUrl = findViewById(R.id.etYoutubeUrl);
        btnDownload = findViewById(R.id.btnDownload);
        progressBar = findViewById(R.id.progressBar);
        adView = findViewById(R.id.adView);

        // 2. Inicialización de Google AdMob
        MobileAds.initialize(this, initializationStatus -> {});
        if (adView != null) {
            AdRequest adRequest = new AdRequest.Builder().build();
            adView.loadAd(adRequest);
        }

        // 3. Listener del botón de descarga
        btnDownload.setOnClickListener(v -> {
            String url = etYoutubeUrl.getText().toString().trim();
            if (url.isEmpty()) {
                Toast.makeText(MainActivity.this, "Por favor, ingresa un enlace de YouTube", Toast.LENGTH_SHORT).show();
            } else {
                startAudioDownload(url);
            }
        });
    }

    private void startAudioDownload(String videoUrl) {
        // Bloquear interfaz y mostrar indicador de progreso
        progressBar.setVisibility(View.VISIBLE);
        btnDownload.setEnabled(false);

        executor.execute(() -> {
            boolean success = false;
            String message;

            try {
                // Configurar conexión HTTP POST hacia la VPS
                URL url = new URL(VPS_DOWNLOAD_URL);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json; utf-8");
                conn.setRequestProperty("Accept", "audio/mpeg");
                conn.setDoOutput(true);
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(120000); // 2 min para permitir la conversión en la VPS

                // Cuerpo JSON de la petición
                JSONObject jsonParam = new JSONObject();
                jsonParam.put("url", videoUrl);

                try (OutputStream os = conn.getOutputStream()) {
                    byte[] input = jsonParam.toString().getBytes("utf-8");
                    os.write(input, 0, input.length);
                }

                int responseCode = conn.getResponseCode();

                if (responseCode == HttpURLConnection.HTTP_OK) {
                    // Guardar el flujo de audio devuelto en la carpeta 'Download' pública del dispositivo
                    File downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                    if (!downloadDir.exists()) {
                        downloadDir.mkdirs();
                    }

                    String fileName = "TubeMusic_" + System.currentTimeMillis() + ".mp3";
                    File outputFile = new File(downloadDir, fileName);

                    try (InputStream inputStream = conn.getInputStream();
                         FileOutputStream outputStream = new FileOutputStream(outputFile)) {

                        byte[] buffer = new byte[4096];
                        int bytesRead;
                        while ((bytesRead = inputStream.read(buffer)) != -1) {
                            outputStream.write(buffer, 0, bytesRead);
                        }
                    }

                    success = true;
                    message = "Descarga completada: " + fileName;
                } else {
                    message = "Error del servidor VPS (Código HTTP: " + responseCode + ")";
                }

                conn.disconnect();

            } catch (Exception e) {
                message = "Error de red: " + e.getLocalizedMessage();
            }

            // Actualizar elementos de interfaz en el hilo principal
            final boolean finalSuccess = success;
            final String finalMessage = message;

            mainHandler.post(() -> {
                progressBar.setVisibility(View.GONE);
                btnDownload.setEnabled(true);
                Toast.makeText(MainActivity.this, finalMessage, Toast.LENGTH_LONG).show();

                if (finalSuccess) {
                    etYoutubeUrl.setText("");
                }
            });
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdown();
    }
    }
