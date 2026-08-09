import android.app.DownloadManager;
import android.content.Context;
import android.net.Uri;
import android.os.Environment;
import android.util.Log;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;

// ... dentro de tu clase MainActivity ...

/**
 * 1. Realiza la petición HTTP a tu VPS para extraer los datos del video (Título y URL de descarga)
 */
private void consultarApiYDescargar(String youtubeUrlString) {
    // Las peticiones de red NUNCA deben ir en el hilo principal (UI Thread)
    new Thread(() -> {
        try {
            // Codificar la URL de YouTube de forma segura para la consulta GET
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
                // Leer la respuesta JSON enviada por tu servidor Python
                BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();

                // Parsear el JSON obtenido
                JSONObject jsonResponse = new JSONObject(response.toString());
                
                // Extraer el título y el enlace directo de audio 
                // (Asegúrate de que en tu Python las claves sean "title" y "url")
                String title = jsonResponse.optString("title", "audio_descargado");
                String audioDownloadUrl = jsonResponse.optString("url", "");

                if (!audioDownloadUrl.isEmpty()) {
                    // Limpiar caracteres extraños del título para evitar errores al guardar el archivo
                    String safeFileName = title.replaceAll("[^a-zA-Z0-9.-]", "_") + ".m4a";

                    // Volver al hilo principal para invocar el DownloadManager del sistema
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

/**
 * 2. Configura el DownloadManager incluyendo el User-Agent clave para evitar el bloqueo de YouTube
 */
private void iniciarDescargaConManager(String audioUrl, String fileName) {
    try {
        DownloadManager.Request request = new DownloadManager.Request(Uri.parse(audioUrl));

        // Configuración básica de la notificación
        request.setTitle(fileName);
        request.setDescription("Descargando música con TubeMusic...");
        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);

        // Guardar directamente en la carpeta pública de Descargas del dispositivo
        request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName);

        // =========================================================================
        // 🔑 CLAVE CRUCIAL: Engaña a YouTube enviando un User-Agent de navegador web
        // =========================================================================
        request.addRequestHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");

        // Permitir descarga tanto por Wi-Fi como por Red Móvil
        request.setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI | DownloadManager.Request.NETWORK_MOBILE);
        request.setAllowedOverRoaming(true);

        // Encolar la descarga en el sistema Android
        DownloadManager manager = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
        if (manager != null) {
            manager.enqueue(request);
            Log.d("TubeMusic", "🎉 [OK] Descarga enviada a DownloadManager: " + fileName);
        }
    } catch (Exception e) {
        Log.e("TubeMusic", "Error al iniciar DownloadManager", e);
    }
                        }
