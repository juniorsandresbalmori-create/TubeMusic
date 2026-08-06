package TubeVideos.com;

import android.app.Dialog;
import android.app.DownloadManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.rewarded.RewardedAd;
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback;
import com.tubemusic.app.R;

import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private int rewardSeconds = 0;
    private TextView tvTimer;
    private EditText etUrl;
    private RewardedAd rewardedAd;

    private final Handler timerHandler = new Handler(Looper.getMainLooper());
    private final Runnable timerRunnable = new Runnable() {
        @Override
        public void run() {
            if (rewardSeconds > 0) {
                rewardSeconds--;
                updateTimerText();
                saveSeconds();
            }
            timerHandler.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        MobileAds.initialize(this, initializationStatus -> {});
        loadRewardedAd();

        SharedPreferences prefs = getSharedPreferences("TubeMusicPrefs", MODE_PRIVATE);
        rewardSeconds = prefs.getInt("rewardSeconds", 0);

        tvTimer = findViewById(R.id.tvTimer);
        etUrl = findViewById(R.id.etUrl);
        View btnMenu = findViewById(R.id.btnMenu);
        View btnDownload = findViewById(R.id.btnDownload);

        updateTimerText();

        if (btnMenu != null) btnMenu.setOnClickListener(v -> showAdsDialog());
        if (tvTimer != null) tvTimer.setOnClickListener(v -> showAdsDialog());

        if (btnDownload != null) {
            btnDownload.setOnClickListener(v -> {
                String url = etUrl.getText() != null ? etUrl.getText().toString().trim() : "";

                if (rewardSeconds <= 0) {
                    Toast.makeText(MainActivity.this, "¡Sin tiempo disponible! Ve un anuncio.", Toast.LENGTH_LONG).show();
                    showAdsDialog();
                } else if (url.isEmpty()) {
                    Toast.makeText(MainActivity.this, "Ingresa o pega un enlace primero.", Toast.LENGTH_SHORT).show();
                } else {
                    startDownload(url);
                }
            });
        }

        timerHandler.postDelayed(timerRunnable, 1000);
    }

    private void startDownload(String downloadUrl) {
        if (!downloadUrl.startsWith("http://") && !downloadUrl.startsWith("https://")) {
            downloadUrl = "https://" + downloadUrl;
        }

        try {
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(downloadUrl));
            request.setTitle("TubeMusic MP3");
            request.setDescription("Descargando archivo de audio...");
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "TubeMusic_" + System.currentTimeMillis() + ".mp3");

            DownloadManager manager = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
            if (manager != null) {
                manager.enqueue(request);
                Toast.makeText(this, "Descarga iniciada en segundo plano", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Toast.makeText(this, "Error al procesar la URL de descarga", Toast.LENGTH_SHORT).show();
        }
    }

    private void loadRewardedAd() {
        AdRequest adRequest = new AdRequest.Builder().build();
        RewardedAd.load(this, "ca-app-pub-3940256099942544/5224354917",
            adRequest, new RewardedAdLoadCallback() {
                @Override
                public void onAdFailedToLoad(@NonNull LoadAdError loadAdError) {
                    rewardedAd = null;
                }
                @Override
                public void onAdLoaded(@NonNull RewardedAd ad) {
                    rewardedAd = ad;
                }
            });
    }

    private void showAdsDialog() {
        Dialog dialog = new Dialog(this);
        dialog.setContentView(R.layout.dialog_ads);

        int[] buttonIds = {R.id.btnAd5s, R.id.btnAd15s, R.id.btnAd30s, R.id.btnAd45s, R.id.btnAd60s};
        int[] secondsToAdd = {300, 1200, 3600, 5400, 7200};

        for (int i = 0; i < buttonIds.length; i++) {
            View btn = dialog.findViewById(buttonIds[i]);
            if (btn != null) {
                final int seconds = secondsToAdd[i];
                btn.setOnClickListener(v -> {
                    dialog.dismiss();
                    showAdAndReward(seconds);
                });
            }
        }
        dialog.show();
    }

    private void showAdAndReward(int secondsGranted) {
        if (rewardedAd != null) {
            rewardedAd.show(this, rewardItem -> {
                rewardSeconds += secondsGranted;
                updateTimerText();
                saveSeconds();
                Toast.makeText(MainActivity.this, "¡Recompensa otorgada!", Toast.LENGTH_SHORT).show();
                loadRewardedAd();
            });
        } else {
            Toast.makeText(this, "Cargando anuncio... Inténtalo de nuevo.", Toast.LENGTH_SHORT).show();
            loadRewardedAd();
        }
    }

    private void updateTimerText() {
        if (tvTimer != null) {
            long hours = rewardSeconds / 3600;
            long minutes = (rewardSeconds % 3600) / 60;
            long seconds = rewardSeconds % 60;
            tvTimer.setText(String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds));
        }
    }

    private void saveSeconds() {
        SharedPreferences prefs = getSharedPreferences("TubeMusicPrefs", MODE_PRIVATE);
        prefs.edit().putInt("rewardSeconds", rewardSeconds).apply();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        timerHandler.removeCallbacks(timerRunnable);
    }
                               }
