package com.tubevideos;

import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.yausername.ffmpeg.FFmpeg;
import com.yausername.youtubedl_android.YoutubeDL;
import com.yausername.youtubedl_android.YoutubeDLRequest;

import java.io.File;
import java.io.FileInputStream;
import java.io.OutputStream;
import java.util.UUID;

import kotlin.Unit;
import kotlin.jvm.functions.Function3;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "TubeMusic";
    private WebView webView;
    private File workDir;
    private boolean isEngineReady = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        webView = findViewById(R.id.webView);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            WebView.setWebContentsDebuggingEnabled(true);
        }

        WebSettings ws = webView.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setAllowFileAccess(true);
        ws.setAllowContentAccess(true);

        webView.setWebViewClient(new WebViewClient());
        webView.setWebChromeClient(new WebChromeClient());
        webView.addJavascriptInterface(new WebAppInterface(this), "AndroidBridge");

        workDir = new File(getExternalFilesDir(null), "audio");
        if (!workDir.exists()) workDir.mkdirs();

        webView.loadUrl("file:///android_asset/index.html");
        
        // Se ejecuta la inicialización para permitir que la WebView cargue primero la consola Dev
        webView.postDelayed(this::inicializarMotor, 500);
    }

    private void logDev(String nivel, String mensaje) {
        Log.i(TAG, "[" + nivel + "] " + mensaje);
        runOnUiThread(() -> enviarComandoJS("agregarDevLog('" + nivel + "', '" + escapeJs(mensaje) + "')"));
    }

    private void inicializarMotor() {
        new Thread(() -> {
            try {
                logDev("INFO", "Probando acceso a directorio de trabajo: " + workDir.getAbsolutePath());
                
                logDev("INFO", "Iniciando FFmpeg...");
                FFmpeg.getInstance().init(getApplicationContext());
                logDev("SUCCESS", "FFmpeg inicializado correctamente.");

                logDev("INFO", "Iniciando YoutubeDL...");
                YoutubeDL.getInstance().init(getApplicationContext());
                logDev("SUCCESS", "YoutubeDL inicializado correctamente.");

                isEngineReady = true;
                runOnUiThread(() -> enviarComandoJS("motorListo()"));
            } catch (Exception e) {
                isEngineReady = false;
                String stackTrace = Log.getStackTraceString(e);
                logDev("ERROR", "Fallo critico en inicializacion:\n" + stackTrace);
                runOnUiThread(() -> enviarComandoJS("errorDescarga('Error de inicialización: " + escapeJs(e.getMessage()) + "')"));
            }
        }).start();
    }

    public class WebAppInterface {
        Context mContext;

        WebAppInterface(Context c) {
            mContext = c;
        }

        @JavascriptInterface
        public void iniciarDescarga(String url) {
            if (!isEngineReady) {
                logDev("WARN", "Intento de descarga con motor inactivo.");
                runOnUiThread(() -> enviarComandoJS("errorDescarga('El motor aún no está listo...')"));
                return;
            }
            logDev("INFO", "Solicitando descarga: " + url);
            runOnUiThread(() -> enviarComandoJS("actualizarProgreso(0, 'Preparando descarga...')"));
            procesarDescargaLocal(url);
        }

        @JavascriptInterface
        public void reintentarInit() {
            logDev("INFO", "Reintentando inicializacion manual...");
            inicializarMotor();
        }

        @JavascriptInterface
        public void mostrarToast(String msg) {
            runOnUiThread(() -> toast(msg));
        }
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    private void enviarComandoJS(String comando) {
        if (webView != null) {
            webView.evaluateJavascript("javascript:" + comando, null);
        }
    }

    private static String escapeJs(String s) {
        if (s == null) return "error desconocido";
        return s.replace("\\", "\\\\")
                .replace("'", "\\'")
                .replace("\n", "\\n")
                .replace("\r", "");
    }

    private void procesarDescargaLocal(String youtubeUrl) {
        new Thread(() -> {
            String processId = UUID.randomUUID().toString();

            try {
                String plantilla = new File(workDir, processId + ".%(ext)s").getAbsolutePath();

                YoutubeDLRequest request = new YoutubeDLRequest(youtubeUrl);
                request.addOption("-f", "bestaudio");
                request.addOption("--extract-audio");
                request.addOption("--audio-format", "mp3");
                request.addOption("--audio-quality", "0");
                request.addOption("--no-playlist");
                request.addOption("-o", plantilla);

                logDev("INFO", "Ejecutando orden yt-dlp...");
                YoutubeDL.getInstance().execute(request, processId, new Function3<Float, Long, String, Unit>() {
                    @Override
                    public Unit invoke(Float progress, Long etaInSeconds, String line) {
                        int pct = (int) (progress * 100);
                        if (line != null && !line.trim().isEmpty()) {
                            logDev("YTDL", line);
                        }
                        runOnUiThread(() -> enviarComandoJS(
                                "actualizarProgreso(" + pct + ", 'Descargando y convirtiendo... " + pct + "%')"));
                        return Unit.INSTANCE;
                    }
                });

                File outFile = null;
                File[] candidatos = workDir.listFiles((d, n) -> n.startsWith(processId) && n.endsWith(".mp3"));
                if (candidatos != null && candidatos.length > 0) {
                    outFile = candidatos[0];
                }

                if (outFile == null || !outFile.exists()) {
                    throw new Exception("El motor local no generó ningún MP3");
                }

                logDev("SUCCESS", "Archivo procesado: " + outFile.getAbsolutePath());
                publicarEnMediaStore(outFile, processId);

                runOnUiThread(() -> {
                    enviarComandoJS("actualizarProgreso(100, 'Listo')");
                    enviarComandoJS("descargaCompletada('Guardado en Música - " + processId + ".mp3')");
                });

            } catch (Exception e) {
                logDev("ERROR", "Fallo en ejecucion yt-dlp:\n" + Log.getStackTraceString(e));
                runOnUiThread(() -> enviarComandoJS("errorDescarga('" + escapeJs(e.getMessage()) + "')"));
            }
        }).start();
    }

    private void publicarEnMediaStore(File mp3, String titulo) {
        Uri collection;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
        } else {
            collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
        }

        ContentValues values = new ContentValues();
        values.put(MediaStore.Audio.Media.DISPLAY_NAME, titulo + ".mp3");
        values.put(MediaStore.Audio.Media.MIME_TYPE, "audio/mpeg");
        values.put(MediaStore.Audio.Media.IS_MUSIC, 1);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.put(MediaStore.Audio.Media.RELATIVE_PATH, Environment.DIRECTORY_MUSIC);
        }

        Uri itemUri = getContentResolver().insert(collection, values);
        if (itemUri == null) {
            mp3.delete();
            return;
        }

        try (OutputStream os = getContentResolver().openOutputStream(itemUri);
             FileInputStream fis = new FileInputStream(mp3)) {
            byte[] buffer = new byte[8192];
            int len;
            while ((len = fis.read(buffer)) > 0) {
                os.write(buffer, 0, len);
            }
            logDev("SUCCESS", "Copiado a MediaStore correctamente.");
        } catch (Exception e) {
            logDev("ERROR", "Fallo al copiar a MediaStore:\n" + Log.getStackTraceString(e));
        } finally {
            mp3.delete();
        }
    }
        }
