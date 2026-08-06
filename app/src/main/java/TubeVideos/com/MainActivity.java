package TubeVideos.com;

import android.os.Bundle;
import android.view.View;
import androidx.appcompat.app.AppCompatActivity;

// Importante para enlazar los IDs del layout XML
import com.tubemusic.app.R;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    private int rewardSeconds = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Carga la interfaz visual (main.xml)
        try {
            setContentView(R.layout.main);
        } catch (Exception e) {
            try {
                setContentView(R.layout.activity_main);
            } catch (Exception ex) {
                // Previene que colapse si el layout se llama diferente
            }
        }
    }

    @Override
    public void onClick(View v) {
        int id = v.getId();

        if (id == R.id.btnAd5s) {
            rewardSeconds = 300;  // 5m
        } else if (id == R.id.btnAd15s) {
            rewardSeconds = 1800; // 30m
        } else if (id == R.id.btnAd30s) {
            rewardSeconds = 3600; // 1h
        } else if (id == R.id.btnAd45s) {
            rewardSeconds = 5400; // 1h 30m
        } else if (id == R.id.btnAd60s) {
            rewardSeconds = 7200; // 2h
        }
    }
}
