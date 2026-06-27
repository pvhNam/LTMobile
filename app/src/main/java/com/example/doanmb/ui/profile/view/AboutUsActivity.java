package com.example.doanmb.ui.profile.view;

import android.os.Bundle;
import android.widget.ImageView;
import androidx.appcompat.app.AppCompatActivity;
import com.example.doanmb.R;
import com.example.doanmb.core.util.EdgeToEdgeUtil;

public class AboutUsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) { // Đã sửa lại chuẩn ở dòng này
        super.onCreate(savedInstanceState);
        EdgeToEdgeUtil.enable(this, true);
        setContentView(R.layout.activity_about_us);
        EdgeToEdgeUtil.padContentForSystemBars(this, 0xFFFFFFFF);

        // Bắt sự kiện nút quay lại
        ImageView btnBack = findViewById(R.id.btn_back_about);
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> finish());
        }
    }
}