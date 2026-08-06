package TubeVideos.com;

import android.app.Activity;
import android.app.Dialog;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {

    private TextView tvTimer;
    private ImageView btnMenu;
    private Button btnDownload;
    private EditText etSearch;

    private long timeRemainingInMillis = 0;
    private CountDownTimer countDownTimer;

    // Control de estado de la descarga
    private boolean isProcessing = false;
    private boolean isPausedForTime = false;
    private int currentProgressPercent = 0;
    private Handler downloadHandler = new Handler();
    private Runnable downloadRunnable;
    private String currentQuery = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        tvTimer = (TextView) findViewById(R.id.tvTimer);
        btnMenu = (ImageView) findViewById(R.id.btnMenu);
        btnDownload = (Button) findViewById(R.id.btnDownload);
        etSearch = (EditText) findViewById(R.id.etSearch);

        btnMenu.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					showFullScreenAdsDialog();
				}
			});

        btnDownload.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					processDownloadRequest();
				}
			});
    }

    private void showFullScreenAdsDialog() {
        final Dialog adsDialog = new Dialog(this, android.R.style.Theme_NoTitleBar_Fullscreen);
        adsDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        adsDialog.setContentView(R.layout.dialog_ads);

        if (adsDialog.getWindow() != null) {
            adsDialog.getWindow().setLayout(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT
            );
        }

        Button btn5s = (Button) adsDialog.findViewById(R.id.btnAd5s);
        Button btn15s = (Button) adsDialog.findViewById(R.id.btnAd15s);
        Button btn30s = (Button) adsDialog.findViewById(R.id.btnAd30s);
        Button btn45s = (Button) adsDialog.findViewById(R.id.btnAd45s);
        Button btn60s = (Button) adsDialog.findViewById(R.id.btnAd60s);
        Button btnClose = (Button) adsDialog.findViewById(R.id.btnCloseAds);

        View.OnClickListener adClickListener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int rewardSeconds = 0;
                int id = v.getId();

                if (id == R.id.btnAd5s) rewardSeconds = 300;       // 5m
                else if (id == R.id.btnAd15s) rewardSeconds = 1800;  // 30m
                else if (id == R.id.btnAd30s) rewardSeconds = 3600;  // 1h
                else if (id == R.id.btnAd45s) rewardSeconds = 5400;  // 1h 30m
                else if (id == R.id.btnAd60s) rewardSeconds = 7200;  // 2h

                adsDialog.dismiss();
                simulateAdReward(rewardSeconds);
            }
        };

        btn5s.setOnClickListener(adClickListener);
        btn15s.setOnClickListener(adClickListener);
        btn30s.setOnClickListener(adClickListener);
        btn45s.setOnClickListener(adClickListener);
        btn60s.setOnClickListener(adClickListener);

        btnClose.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					adsDialog.dismiss();
				}
			});

        adsDialog.show();
    }

    private void simulateAdReward(final int secondsToAdd) {
        Toast.makeText(this, "Cargando anuncio...", Toast.LENGTH_SHORT).show();

        new Handler().postDelayed(new Runnable() {
				@Override
				public void run() {
					addTimeTokens(secondsToAdd * 1000L);
					Toast.makeText(MainActivity.this, "¡Anuncio visto! +" + (secondsToAdd / 60) + " min cargados", Toast.LENGTH_LONG).show();

					if (isPausedForTime) {
						resumeDownloadProcess();
					}
				}
			}, 1500);
    }

    private void processDownloadRequest() {
        String query = etSearch.getText().toString().trim();

        if (query.isEmpty()) {
            Toast.makeText(this, "Ingresá un enlace o búsqueda", Toast.LENGTH_SHORT).show();
            return;
        }

        if (timeRemainingInMillis <= 0) {
            Toast.makeText(this, "Sin tiempo disponible. Reclamá más en el menú.", Toast.LENGTH_LONG).show();
            return;
        }

        if (isProcessing) {
            Toast.makeText(this, "Ya hay una descarga en proceso...", Toast.LENGTH_SHORT).show();
            return;
        }

        startConversionProcess(query);
    }

    private void startConversionProcess(final String query) {
        isProcessing = true;
        isPausedForTime = false;
        currentProgressPercent = 0;
        currentQuery = query;

        btnDownload.setEnabled(false);
        Toast.makeText(this, "Iniciando descarga: " + query, Toast.LENGTH_SHORT).show();

        runDownloadLoop();
    }

    private void runDownloadLoop() {
        downloadRunnable = new Runnable() {
            @Override
            public void run() {
                if (isPausedForTime) {
                    return;
                }

                if (timeRemainingInMillis <= 0) {
                    pauseDownloadProcess();
                    return;
                }

                currentProgressPercent += 10;

                if (currentProgressPercent < 100) {
                    Toast.makeText(MainActivity.this, "Procesando " + currentQuery + ": " + currentProgressPercent + "%", Toast.LENGTH_SHORT).show();
                    downloadHandler.postDelayed(downloadRunnable, 1500);
                } else {
                    Toast.makeText(MainActivity.this, "¡Descarga y conversión completadas al 100%!", Toast.LENGTH_LONG).show();
                    isProcessing = false;
                    isPausedForTime = false;
                    btnDownload.setEnabled(true);
                    etSearch.setText("");
                }
            }
        };

        downloadHandler.postDelayed(downloadRunnable, 1000);
    }

    private void pauseDownloadProcess() {
        isPausedForTime = true;
        downloadHandler.removeCallbacks(downloadRunnable);
        Toast.makeText(this, "⚠️ Tiempo agotado. Descarga pausada al " + currentProgressPercent + "%. Conseguí más tiempo para reanudar.", Toast.LENGTH_LONG).show();
    }

    private void resumeDownloadProcess() {
        isPausedForTime = false;
        Toast.makeText(this, "▶️ Tiempo añadido. Reanudando descarga desde " + currentProgressPercent + "%...", Toast.LENGTH_SHORT).show();
        runDownloadLoop();
    }

    private void addTimeTokens(long extraTimeMs) {
        timeRemainingInMillis += extraTimeMs;

        if (countDownTimer != null) {
            countDownTimer.cancel();
        }

        countDownTimer = new CountDownTimer(timeRemainingInMillis, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                timeRemainingInMillis = millisUntilFinished;
                updateTimerUI();
            }

            @Override
            public void onFinish() {
                timeRemainingInMillis = 0;
                updateTimerUI();

                if (isProcessing && !isPausedForTime) {
                    pauseDownloadProcess();
                } else {
                    Toast.makeText(MainActivity.this, "Tiempo agotado", Toast.LENGTH_SHORT).show();
                }
            }
        }.start();
    }

    private void updateTimerUI() {
        int hours = (int) (timeRemainingInMillis / 1000) / 3600;
        int minutes = (int) ((timeRemainingInMillis / 1000) % 3600) / 60;
        int seconds = (int) (timeRemainingInMillis / 1000) % 60;

        String timeLeftFormatted = String.format("%02d:%02d:%02d", hours, minutes, seconds);
        tvTimer.setText(timeLeftFormatted);
    }
    }
                                                 
