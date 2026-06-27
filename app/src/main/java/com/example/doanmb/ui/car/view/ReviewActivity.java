package com.example.doanmb.ui.car.view;

import android.os.Bundle;
import android.widget.ImageView;
import androidx.appcompat.app.AppCompatActivity;
import com.example.doanmb.R;
import com.example.doanmb.core.util.EdgeToEdgeUtil;

public class ReviewActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdgeUtil.enable(this, true);
        setContentView(R.layout.activity_review);
        EdgeToEdgeUtil.padContentForSystemBars(this, 0xFFFFFFFF);

        // Bắt sự kiện nút quay lại ở toolbar
        ImageView btnBack = findViewById(R.id.btn_back);
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> finish());
        }
    }
}