package com.example.doanmb.ui.profile.view;
import android.os.Bundle;
import android.widget.ImageView;
import androidx.appcompat.app.AppCompatActivity;
import com.example.doanmb.R;
import com.example.doanmb.core.util.EdgeToEdgeUtil;

public class PrivacyActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdgeUtil.enable(this, true);
        setContentView(R.layout.activity_privacy);
        EdgeToEdgeUtil.applyHeaderAndScroll(
                findViewById(R.id.privacy_scroll), findViewById(R.id.header_bar));

        ImageView btnBack = findViewById(R.id.btn_back);
        if (btnBack != null) btnBack.setOnClickListener(v -> finish());
    }
}
