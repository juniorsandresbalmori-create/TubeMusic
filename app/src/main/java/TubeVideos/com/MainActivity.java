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

        // Buscar el texto del contador si existe en main.xml
        tvTimer = findViewById(R.id.tvTimer);
        if (tvTimer == null) {
            tvTimer = findViewById(R.id.timer);
        }

        // Configurar el botón del menú superior / botón de anuncios para abrir el diálogo
        View btnMenu = findViewById(R.id.btnMenu);
        if (btnMenu == null) btnMenu = findViewById(R.id.ic_menu);
        if (btnMenu == null) btnMenu = findViewById(R.id.btn_ads);

        if (btnMenu != null) {
            btnMenu.setOnClickListener(v -> showAdsDialog());
        }

        // También permitir abrir el diálogo al tocar el texto del contador
        if (tvTimer != null) {
            tvTimer.setOnClickListener(v -> showAdsDialog());
        }
    }

    // Método para desplegar la ventana con los botones de recompensas
    private void showAdsDialog() {
        Dialog dialog = new Dialog(this);
        dialog.setContentView(R.layout.dialog_ads);

        // Configurar escuchadores para los botones dentro de dialog_ads.xml
        int[] buttonIds = {R.id.btnAd5s, R.id.btnAd15s, R.id.btnAd30s, R.id.btnAd45s, R.id.btnAd60s};
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

    // Actualiza el texto visual en formato HH:mm:ss
    private void updateTimerText() {
        if (tvTimer != null) {
            long hours = rewardSeconds / 3600;
            long minutes = (rewardSeconds % 3600) / 60;
            long seconds = rewardSeconds % 60;
            tvTimer.setText(String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds));
        }
    }
}
