package com.example.doanmb.ui.admin.view;

import android.content.Intent;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.example.doanmb.R;
import com.example.doanmb.core.util.ImageLoader;
import com.example.doanmb.ui.admin.util.AdminFormat;
import com.example.doanmb.ui.admin.viewmodel.AdminDriverProfileViewModel;
import com.example.doanmb.ui.media.view.FullscreenImageActivity;
import com.google.firebase.firestore.DocumentSnapshot;

import java.util.Locale;

/** Màn admin xem chi tiết 1 tài xế: hồ sơ, giấy tờ, rating, số chuyến, thu nhập. */
public class AdminDriverProfileActivity extends AppCompatActivity {

    public static final String EXTRA_USER_ID = "USER_ID";

    private ImageView ivAvatar, ivCccd, ivLicense;
    private TextView tvName, tvOnline, tvPhone, tvEmail, tvBalance;
    private TextView tvRating, tvTrips, tvIncome, tvCccd, tvLicense, tvCarType;

    private AdminDriverProfileViewModel viewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_driver_profile);
        if (getSupportActionBar() != null) getSupportActionBar().hide();

        viewModel = new ViewModelProvider(this).get(AdminDriverProfileViewModel.class);
        String userId = getIntent().getStringExtra(EXTRA_USER_ID);

        ivAvatar  = findViewById(R.id.iv_adp_avatar);
        ivCccd    = findViewById(R.id.iv_adp_cccd);
        ivLicense = findViewById(R.id.iv_adp_license);
        tvName    = findViewById(R.id.tv_adp_name);
        tvOnline  = findViewById(R.id.tv_adp_online);
        tvPhone   = findViewById(R.id.tv_adp_phone);
        tvEmail   = findViewById(R.id.tv_adp_email);
        tvBalance = findViewById(R.id.tv_adp_balance);
        tvRating  = findViewById(R.id.tv_adp_rating);
        tvTrips   = findViewById(R.id.tv_adp_trips);
        tvIncome  = findViewById(R.id.tv_adp_income);
        tvCccd    = findViewById(R.id.tv_adp_cccd);
        tvLicense = findViewById(R.id.tv_adp_license);
        tvCarType = findViewById(R.id.tv_adp_cartype);

        findViewById(R.id.btn_adp_back).setOnClickListener(v -> finish());

        if (userId == null || userId.isEmpty()) {
            Toast.makeText(this, "Không tìm thấy tài xế", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        viewModel.getUser().observe(this, this::bindUser);
        viewModel.getTrips().observe(this, t -> tvTrips.setText(String.valueOf(t)));
        viewModel.getIncome().observe(this, in -> tvIncome.setText(AdminFormat.money(in != null ? in : 0L)));
        viewModel.getRating().observe(this, r -> tvRating.setText(r != null && r.count > 0
                ? String.format(Locale.US, "%.1f ⭐ (%d)", r.avg, r.count)
                : "Chưa có"));
        viewModel.getMessage().observe(this, msg -> {
            if (msg != null) Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
        });
        viewModel.getFinishEvent().observe(this, f -> {
            if (Boolean.TRUE.equals(f)) finish();
        });

        viewModel.start(userId);
    }

    private void bindUser(DocumentSnapshot doc) {
        if (doc == null) return;

        tvName.setText(str(doc.getString("name"), "Không có tên"));

        boolean online = Boolean.TRUE.equals(doc.getBoolean("driverOnline"));
        tvOnline.setText(online ? "🟢  Đang online" : "⚪  Offline");
        tvOnline.setTextColor(online ? 0xFF2E7D32 : 0xFF757575);

        tvPhone.setText("📞  " + str(doc.getString("phone"), "Chưa có"));
        tvEmail.setText("✉️  " + str(doc.getString("email"), "Chưa có"));

        Double bal = doc.getDouble("balance");
        tvBalance.setText(AdminFormat.money(bal != null ? Math.round(bal) : 0L));

        tvCccd.setText("Số CCCD: " + str(doc.getString("cccd"), "--"));
        tvLicense.setText("Số bằng lái: " + str(doc.getString("licenseNumber"), "--"));
        tvCarType.setText("Loại xe: " + str(doc.getString("driverCarType"), "--"));

        String avatarUrl = doc.getString("avatarUrl");
        if (avatarUrl != null && !avatarUrl.isEmpty()) {
            ImageLoader.loadAvatar(ivAvatar, avatarUrl, android.R.drawable.ic_menu_myplaces);
        } else {
            ivAvatar.setImageResource(android.R.drawable.ic_menu_myplaces);
        }

        loadDocImage(ivCccd, doc.getString("cccdImageUrl"));
        loadDocImage(ivLicense, doc.getString("licenseImageUrl"));
    }

    /** Hiện ảnh giấy tờ, bấm để xem toàn màn hình. */
    private void loadDocImage(ImageView iv, String url) {
        if (url == null || url.isEmpty()) {
            iv.setImageResource(android.R.drawable.ic_menu_report_image);
            iv.setOnClickListener(null);
            return;
        }
        ImageLoader.loadDetail(iv, url, android.R.drawable.ic_menu_report_image);
        iv.setOnClickListener(v -> {
            Intent i = new Intent(this, FullscreenImageActivity.class);
            i.putExtra(FullscreenImageActivity.EXTRA_IMAGE_URL, url);
            startActivity(i);
        });
    }

    private static String str(String value, String def) {
        return (value != null && !value.isEmpty()) ? value : def;
    }
}
