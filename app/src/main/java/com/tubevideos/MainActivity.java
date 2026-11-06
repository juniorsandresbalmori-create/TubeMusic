package com.tubevideos;

import android.Manifest;
import android.app.DownloadManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.text.TextUtils;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {
    private static final int REQUEST_STORAGE_PERMISSION = 4102;

    private TextInputEditText etMediaUrl;
    private MaterialButton btnDownload;
    private ProgressBar progressBar;
    private TextView tvStatus;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private AudioSourceResolver audioSourceResolver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        etMediaUrl = findViewById(R.id.etMediaUrl);
        btnDownload = findViewById(R.id.btnDownload);
        progressBar = findViewById(R.id.progressBar);
        tvStatus = findViewById(R.id.tvStatus);

        // Replace this placeholder with the separately implemented provider.
        audioSourceResolver = new PendingAudioSourceResolver();

        btnDownload.setOnClickListener(view -> beginResolve());
    }

    private void beginResolve() {
        String input = etMediaUrl.getText() == null ? "" : etMediaUrl.getText().toString().trim();
        if (TextUtils.isEmpty(input)) {
            etMediaUrl.setError(getString(R.string.error_empty_url));
            return;
        }

        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P
                && ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    REQUEST_STORAGE_PERMISSION);
            showStatus(getString(R.string.storage_permission));
            return;
        }

        setBusy(true);
        audioSourceResolver.resolve(input, new AudioSourceResolver.Callback() {
            @Override
            public void onResolved(ResolvedAudio audio) {
                runOnUiThread(() -> enqueueDownload(audio));
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    setBusy(false);
                    showStatus(TextUtils.isEmpty(message)
                            ? getString(R.string.error_provider_pending) : message);
                });
            }
        });
    }

    private void enqueueDownload(ResolvedAudio audio) {
        if (audio == null || audio.getDownloadUrl() == null) {
            setBusy(false);
            showStatus(getString(R.string.error_invalid_media_url));
            return;
        }

        Uri uri;
        try {
            uri = Uri.parse(audio.getDownloadUrl());
        } catch (Exception exception) {
            setBusy(false);
            showStatus(getString(R.string.error_invalid_media_url));
            return;
        }

        if (!"https".equalsIgnoreCase(uri.getScheme()) || TextUtils.isEmpty(uri.getHost())) {
            setBusy(false);
            showStatus(getString(R.string.error_invalid_media_url));
            return;
        }

        try {
            String title = TextUtils.isEmpty(audio.getTitle()) ? "TubeMusic audio" : audio.getTitle();
            String extension = normalizeExtension(audio.getFileExtension());
            String filename = sanitizeFilename(title) + "." + extension;

            DownloadManager.Request request = new DownloadManager.Request(uri);
            request.setTitle(title);
            request.setDescription("TubeMusic · " + filename);
            request.setNotificationVisibility(
                    DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.setAllowedOverMetered(true);
            request.setAllowedOverRoaming(false);
            if (!TextUtils.isEmpty(audio.getMimeType())) {
                request.setMimeType(audio.getMimeType());
            }
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_MUSIC, filename);

            DownloadManager manager = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
            if (manager == null) {
                throw new IllegalStateException("DownloadManager no disponible");
            }
            manager.enqueue(request);
            etMediaUrl.setText("");
            showStatus(getString(R.string.download_queued));
            Toast.makeText(this, R.string.download_queued, Toast.LENGTH_LONG).show();
        } catch (Exception exception) {
            showStatus(getString(R.string.download_error));
        } finally {
            setBusy(false);
        }
    }

    private String normalizeExtension(String extension) {
        if (TextUtils.isEmpty(extension)) return "m4a";
        String clean = extension.toLowerCase(Locale.ROOT).replace(".", "");
        if (!clean.matches("[a-z0-9]{2,5}")) return "m4a";
        return clean;
    }

    private String sanitizeFilename(String title) {
        String clean = title.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
        if (clean.isEmpty()) clean = "TubeMusic audio";
        return clean.length() > 100 ? clean.substring(0, 100) : clean;
    }

    private void setBusy(boolean busy) {
        btnDownload.setEnabled(!busy);
        progressBar.setVisibility(busy ? View.VISIBLE : View.GONE);
    }

    private void showStatus(String message) {
        tvStatus.setText(message);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_STORAGE_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                showStatus("Permiso concedido. Tocá Preparar descarga nuevamente.");
            } else {
                showStatus(getString(R.string.storage_permission));
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdown();
    }
}
