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

import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.ServiceList;
import org.schabi.newpipe.extractor.stream.AudioStream;
import org.schabi.newpipe.extractor.stream.StreamExtractor;

import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private EditText etYoutubeUrl;
    private Button btnDownload;
    private ProgressBar progressBar;
    private AdView adView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Inicializar NewPipeExtractor con nuestro descargador
        try {
            NewPipe.init(DownloaderImpl.getInstance());
        } catch (Exception e) {
            e.printStackTrace();
        }

        // Vincular elementos de la UI
        etYoutubeUrl = findViewById(R.id.etYoutubeUrl);
        btnDownload = findViewById(R.id.btnDownload);
        progressBar = findViewById(R.id.progressBar);
        adView = findViewById(R.id.adView);

        // Inicializar AdMob
        MobileAds.initialize(this, initializationStatus -> {});
        AdRequest adRequest = new AdRequest.Builder().build();
        adView.loadAd(adRequest);

        // Botón de conversión / descarga
        btnDownload.setOnClickListener(v -> {
            String youtubeUrl = etYoutubeUrl.getText().toString().trim();
            if (youtubeUrl.isEmpty()) {
                Toast.makeText(MainActivity.this, "Por favor ingresa una URL válida", Toast.LENGTH_SHORT).show();
                return;
            }
            extraerYDescargar(youtubeUrl);
        });
    }

    private void extraerYDescargar(String youtubeUrl) {
        // Mostrar barra de carga y bloquear botón
        runOnUiThread(() -> {
            progressBar.setVisibility(View.VISIBLE);
            btnDownload.setEnabled(false);
            Toast.makeText(MainActivity.this, "Extrayendo audio localmente...", Toast.LENGTH_SHORT).show();
        });

        // Ejecutar extracción en segundo plano
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                // Obtener el extractor de YouTube
                StreamExtractor extractor = ServiceList.YouTube.getStreamExtractor(youtubeUrl);
                extractor.fetchPage();

                String title = extractor.getName();
                
                // Buscar el mejor stream de audio disponible
                AudioStream bestAudio = null;
                for (AudioStream audioStream : extractor.getAudioStreams()) {
                    if (bestAudio == null || audioStream.getAverageBitrate() > bestAudio.getAverageBitrate()) {
                        bestAudio = audioStream;
                    }
                }

                if (bestAudio != null) {
                    String streamUrl = bestAudio.getContent();
                    String ext = bestAudio.getFormat().getName();
                    if (ext == null || ext.isEmpty()) {
                        ext = "m4a"; // Extensión por defecto para audio de YouTube
                    }

                    // Iniciar descarga nativa en el dispositivo
                    descargarEnDispositivo(streamUrl, title, ext);
                } else {
                    mostrarError("No se encontraron streams de audio disponibles.");
                }

            } catch (Exception e) {
                e.printStackTrace();
                mostrarError("Error al extraer: " + e.getMessage());
            } finally {
                // Restaurar interfaz
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
        request.setDescription("Descargando con TubeMusic...");
        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
        request.setDestinationInExternalPublicDir(Environment.DIRECTORY_MUSIC, nombreArchivo);

        DownloadManager manager = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
        if (manager != null) {
            manager.enqueue(request);
            runOnUiThread(() -> {
                Toast.makeText(MainActivity.this, "¡Descarga iniciada con éxito!", Toast.LENGTH_LONG).show();
                etYoutubeUrl.setText("");
            });
        }
    }

    private void mostrarError(String mensaje) {
        runOnUiThread(() -> Toast.makeText(MainActivity.this, mensaje, Toast.LENGTH_LONG).show());
    }
                               }
