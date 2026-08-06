package TubeVideos.com;

import android.os.Bundle;
import android.view.View;
import androidx.appcompat.app.AppCompatActivity;

// Importante: importa la clase R generada por Gradle
import com.tubemusic.app.R;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    private int rewardSeconds = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main); // Asegúrate de que tu layout se llame main.xml o cámbialo por R.layout.activity_main
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
