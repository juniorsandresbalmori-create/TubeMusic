package TubeVideos.com;

import android.app.Dialog;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

import com.tubemusic.app.R;

import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private int rewardSeconds = 0;
    private TextView tvTimer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        // Obtener elementos definidos en main.xml
        tvTimer = findViewById(R.id.tvTimer);
        View btnMenu = findViewById(R.id.btnMenu);

        // Abrir la ventana de anuncios al tocar el botón de 3 barras del menú
        if (btnMenu != null) {
            btnMenu.setOnClickListener(v -> showAdsDialog());
        }

        // Abrir también al tocar el temporizador
        if (tvTimer != null) {
            tvTimer.setOnClickListener(v -> showAdsDialog());
        }
    }

    // Método para desplegar la ventana con los botones de anuncios
    private void showAdsDialog() {
        Dialog dialog = new Dialog(this);
        dialog.setContentView(R.layout.dialog_ads);

        // IDs declaradas en dialog_ads.xml
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
                    dialog.dismiss();
                });
            }
        }

        dialog.show();
    }

    // Actualizar el contador en la barra superior
    private void updateTimerText() {
        if (tvTimer != null) {
            long hours = rewardSeconds / 3600;
            long minutes = (rewardSeconds % 3600) / 60;
            long seconds = rewardSeconds % 60;
            tvTimer.setText(String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds));
        }
    }
             }
