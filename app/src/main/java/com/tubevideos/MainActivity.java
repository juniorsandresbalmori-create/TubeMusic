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

        inicializarMotor();

        webView.loadUrl("file:///android_asset/index.html");
    }

    private void inicializarMotor() {
        new Thread(() -> {
            try {
                FFmpeg.getInstance().init(getApplicationContext());
                YoutubeDL.getInstance().init(getApplicationContext());
                isEngineReady = true;
                Log.i(TAG, "Motor local listo");
                runOnUiThread(() -> enviarComandoJS("motorListo()"));
            } catch (Exception e) {
                isEngineReady = false;
                Log.e(TAG, "Error inicializando motor local", e);
                String err = e.getMessage() != null ? e.getMessage() : e.toString();
                runOnUiThread(() -> enviarComandoJS("errorDescarga('Error de inicialización: " + escapeJs(err) + "')"));
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
                runOnUiThread(() -> enviarComandoJS("errorDescarga('El motor aún se está inicializando...')"));
                inicializarMotor();
                return;
            }
            runOnUiThread(() -> enviarComandoJS("actualizarProgreso(0, 'Preparando descarga...')"));
            procesarDescargaLocal(url);
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
        return s.replace("\\", "\\\\").replace("'", "\\'");
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

                YoutubeDL.getInstance().execute(request, processId, new Function3<Float, Long, String, Unit>() {
                    @Override
                    public Unit invoke(Float progress, Long etaInSeconds, String line) {
                        int pct = (int) (progress * 100);
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

                String titulo = processId;
                publicarEnMediaStore(outFile, titulo);

                runOnUiThread(() -> {
                    enviarComandoJS("actualizarProgreso(100, 'Listo')");
                    enviarComandoJS("descargaCompletada('Guardado en Música - " + titulo + ".mp3')");
                });

            } catch (Exception e) {
                Log.e(TAG, "Error en descarga local", e);
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
        } catch (Exception e) {
            Log.e(TAG, "Error copiando a MediaStore", e);
        } finally {
            mp3.delete();
        }
    }
}
