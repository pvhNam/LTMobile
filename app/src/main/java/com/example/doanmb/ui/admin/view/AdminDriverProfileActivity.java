package com.example.doanmb.ui.admin.view;

import android.content.Intent;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.doanmb.R;
import com.example.doanmb.core.util.ImageLoader;
import com.example.doanmb.data.repository.CarRepository;
import com.example.doanmb.ui.admin.util.AdminFormat;
import com.example.doanmb.ui.media.view.FullscreenImageActivity;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.Locale;

/** Màn admin xem chi tiết 1 tài xế: hồ sơ, giấy tờ, rating, số chuyến, thu nhập. */
public class AdminDriverProfileActivity extends AppCompatActivity {

    public static final String EXTRA_USER_ID = "USER_ID";

    private ImageView ivAvatar, ivCccd, ivLicense;
    private TextView tvName, tvOnline, tvPhone, tvEmail, tvBalance;
    private TextView tvRating, tvTrips, tvIncome, tvCccd, tvLicense, tvCarType;

    private FirebaseFirestore db;
    private String userId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_driver_profile);
        if (getSupportActionBar() != null) getSupportActionBar().hide();

        db = FirebaseFirestore.getInstance();
        userId = getIntent().getStringExtra(EXTRA_USER_ID);

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

        loadUser();
        loadTripStats();
        CarRepository.loadDriverRating(userId, (avg, count) ->
                tvRating.setText(count > 0
                        ? String.format(Locale.US, "%.1f ⭐ (%d)", avg, count)
                        : "Chưa có"));
    }

    private void loadUser() {
        db.collection("users").document(userId).get()
                .addOnSuccessListener(this::bindUser)
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Lỗi tải tài xế: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    finish();
                });
    }

    private void bindUser(DocumentSnapshot doc) {
        if (!doc.exists()) {
            Toast.makeText(this, "Tài xế không còn tồn tại", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

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

    /**
     * Chuyến của tài xế được lưu dưới dạng order có sellerId == tài xế (xem DriverTripsFragment).
     * Đếm order đã hoàn thành + cộng dồn giá trị làm thu nhập.
     */
    private void loadTripStats() {
        db.collection("orders").whereEqualTo("sellerId", userId).get()
                .addOnSuccessListener(snap -> {
                    int completed = 0;
                    long income = 0;
                    for (QueryDocumentSnapshot o : snap) {
                        if (!"completed".equals(o.getString("status"))) continue;
                        completed++;
                        income += orderAmount(o);
                    }
                    tvTrips.setText(String.valueOf(completed));
                    tvIncome.setText(AdminFormat.money(income));
                })
                .addOnFailureListener(e -> {
                    tvTrips.setText("0");
                    tvIncome.setText(AdminFormat.money(0));
                });
    }

    /** Giá trị 1 đơn: ưu tiên totalAmount (số), fallback parse carPrice dạng chuỗi. */
    private long orderAmount(DocumentSnapshot o) {
        Object total = o.get("totalAmount");
        if (total instanceof Number) return ((Number) total).longValue();
        String cp = o.getString("carPrice");
        if (cp != null) {
            String d = cp.replaceAll("[^0-9]", "");
            if (!d.isEmpty()) {
                try { return Long.parseLong(d); } catch (NumberFormatException ignored) {}
            }
        }
        return 0;
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
