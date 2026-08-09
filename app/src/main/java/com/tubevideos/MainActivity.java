package com.tubevideos;

import android.app.DownloadManager;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;

public class MainActivity extends AppCompatActivity {

    private EditText etYoutubeUrl;
    private Button btnDownload;
    private TextView tvTimer;
    private ImageView btnSettings;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        etYoutubeUrl = findViewById(R.id.etYoutubeUrl);
        btnDownload = findViewById(R.id.btnDownload);
        tvTimer = findViewById(R.id.tvTimer);
        btnSettings = findViewById(R.id.btnSettings);

        // Botón de descargar MP3
        btnDownload.setOnClickListener(v -> {
            String url = etYoutubeUrl.getText().toString().trim();
            if (!url.isEmpty()) {
                // Mensaje idéntico al de tu captura de pantalla
                Toast.makeText(this, "Iniciando descarga en segundo plano...", Toast.LENGTH_SHORT).show();
                btnDownload.setEnabled(false);
                consultarApiYDescargar(url);
            } else {
                Toast.makeText(this, "Ingresa un enlace", Toast.LENGTH_SHORT).show();
            }
        });

        // Evento para el ícono de la llave inglesa (Para abrir tu menú de tiempo)
        btnSettings.setOnClickListener(v -> {
            // Aquí puedes lanzar el Intent hacia tu actividad de "Conseguir Tiempo"
            Toast.makeText(this, "Abrir menú de tiempo...", Toast.LENGTH_SHORT).show();
        });
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
                            iniciarDescargaConManager(audioDownloadUrl, safeFileName);
                            etYoutubeUrl.setText(""); 
                            btnDownload.setEnabled(true);
                        });
                    }
                } else {
                    runOnUiThread(() -> btnDownload.setEnabled(true));
                }
                connection.disconnect();
            } catch (Exception e) {
                Log.e("TubeMusic", "Error de red", e);
                runOnUiThread(() -> btnDownload.setEnabled(true));
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
            request.addRequestHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
            request.setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI | DownloadManager.Request.NETWORK_MOBILE);
            request.setAllowedOverRoaming(true);

            DownloadManager manager = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
            if (manager != null) {
                manager.enqueue(request);
            }
        } catch (Exception e) {
            Log.e("TubeMusic", "Error DownloadManager", e);
        }
    }
                        }
