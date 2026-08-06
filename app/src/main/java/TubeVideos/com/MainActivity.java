package TubeVideos.com;

import android.app.Dialog;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

import com.tubemusic.app.R;

import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private int rewardSeconds = 0;
    private TextView tvTimer;
    
    // Handler y Runnable para el descuento automático en tiempo real
    private final Handler timerHandler = new Handler(Looper.getMainLooper());
    private final Runnable timerRunnable = new Runnable() {
        @Override
        public void run() {
            if (rewardSeconds > 0) {
                rewardSeconds--;
                updateTimerText();
                saveSeconds();
            }
            // Repetir cada 1000 milisegundos (1 segundo)
            timerHandler.postDelayed(this, 1000); 
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        // 1. Restaurar tiempo guardado anteriormente
        SharedPreferences prefs = getSharedPreferences("TubeMusicPrefs", MODE_PRIVATE);
        rewardSeconds = prefs.getInt("rewardSeconds", 0);

        // 2. Vincular elementos del XML
        tvTimer = findViewById(R.id.tvTimer);
        View btnMenu = findViewById(R.id.btnMenu);
        
        // Vistas opcionales si existen en main.xml
        View btnDownload = findViewById(R.id.btnDownload);

        updateTimerText();

        // 3. Abrir ventana de anuncios al presionar la llave/herramienta o el reloj
        if (btnMenu != null) {
            btnMenu.setOnClickListener(v -> showAdsDialog());
        }
        if (tvTimer != null) {
            tvTimer.setOnClickListener(v -> showAdsDialog());
        }

        // 4. Validar tiempo disponible al presionar DESCARGAR MP3
        if (btnDownload != null) {
            btnDownload.setOnClickListener(v -> {
                if (rewardSeconds <= 0) {
                    Toast.makeText(MainActivity.this, "¡Sin tiempo disponible! Ve un anuncio para obtener más tiempo.", Toast.LENGTH_LONG).show();
                    showAdsDialog();
                } else {
                    Toast.makeText(MainActivity.this, "Procesando descarga...", Toast.LENGTH_SHORT).show();
                }
            });
        }

        // 5. Iniciar la cuenta regresiva en vivo
        timerHandler.postDelayed(timerRunnable, 1000);
    }

    // Desplegar la ventana flotante de recompensas
    private void showAdsDialog() {
        Dialog dialog = new Dialog(this);
        dialog.setContentView(R.layout.dialog_ads);

        int[] buttonIds = {
            R.id.btnAd5s, 
            R.id.btnAd15s, 
            R.id.btnAd30s, 
            R.id.btnAd45s, 
            R.id.btnAd60s
        };
        int[] secondsToAdd = {300, 1800, 3600, 5400, 7200};

        for (int i = 0; i < buttonIds.length; i++) {
            View btn = dialog.findViewById(buttonIds[i]);
            if (btn != null) {
                final int seconds = secondsToAdd[i];
                btn.setOnClickListener(v -> {
                    rewardSeconds += seconds;
                    updateTimerText();
                    saveSeconds();
                    Toast.makeText(MainActivity.this, "¡Tiempo sumado correctamente!", Toast.LENGTH_SHORT).show();
                    dialog.dismiss();
                });
            }
        }

        dialog.show();
    }

    // Actualizar el texto del contador en formato HH:MM:SS
    private void updateTimerText() {
        if (tvTimer != null) {
            long hours = rewardSeconds / 3600;
            long minutes = (rewardSeconds % 3600) / 60;
            long seconds = rewardSeconds % 60;
            tvTimer.setText(String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds));
        }
    }

    // Guardar los segundos restantes en almacenamiento interno
    private void saveSeconds() {
        SharedPreferences prefs = getSharedPreferences("TubeMusicPrefs", MODE_PRIVATE);
        prefs.edit().putInt("rewardSeconds", rewardSeconds).apply();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Detener el temporizador para evitar consumo inútil de batería y fugas de memoria
        timerHandler.removeCallbacks(timerRunnable);
    }
                              }
