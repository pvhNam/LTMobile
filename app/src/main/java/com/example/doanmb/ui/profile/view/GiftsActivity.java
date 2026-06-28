package com.example.doanmb.ui.profile.view;
import android.os.Bundle;
import android.widget.ImageView;
import androidx.appcompat.app.AppCompatActivity;
import com.example.doanmb.R;
import com.example.doanmb.core.util.EdgeToEdgeUtil;

public class GiftsActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdgeUtil.enable(this, true);
        setContentView(R.layout.activity_gifts);
        EdgeToEdgeUtil.padContentForSystemBars(this, 0xFFFFFFFF);
        ImageView btnBack = findViewById(R.id.btn_back);
        if (btnBack != null) btnBack.setOnClickListener(v -> finish());
    }
}