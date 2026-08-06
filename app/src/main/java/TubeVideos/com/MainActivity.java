package TubeVideos.com;

import android.os.Bundle;
import android.view.View;
import androidx.appcompat.app.AppCompatActivity;

// Importa la clase R generada para tus recursos
import com.tubemusic.app.R;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    private int rewardSeconds = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Carga tu diseño main.xml
        setContentView(R.layout.main);
    }

    @Override
    public void onClick(View v) {
        int id = v.getId();

        if (id == R.id.btnAd5s) {
            rewardSeconds = 300;  // 5m
        } else if (id == R.id.btnAd15s) {
            rewardSeconds = 1800; // 30m
        } else if (id ==
                   
