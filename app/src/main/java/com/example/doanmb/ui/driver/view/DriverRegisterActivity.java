package com.example.doanmb.ui.driver.view;

import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.example.doanmb.R;
import com.example.doanmb.core.helper.CloudinaryHelper;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import com.google.firebase.Timestamp;
import java.util.HashMap;
import java.util.Map;

/**
 * Khách hàng điền CCCD + bằng lái + loại xe để đăng ký làm tài xế.
 * Gửi đăng ký -> users.driverStatus = "pending" (chờ Admin/Staff duyệt).
 */
public class DriverRegisterActivity extends AppCompatActivity {

    private EditText edtCccd, edtLicense;
    private Spinner spinnerCarType;
    private ImageView ivCccdImg, ivLicenseImg;
    private Button btnSubmit;
    private TextView tvStatusBanner;

    private FirebaseFirestore db;
    private String uid;

    private String cccdImageUrl = null;
    private String licenseImageUrl = null;

    // Bộ chọn ảnh dùng chung, biết đang chọn cho ô nào qua biến targetImage
    private int targetImage = 0; // 1 = CCCD, 2 = bằng lái
    private final ActivityResultLauncher<String> pickImageLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) uploadImage(uri, targetImage);
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_driver_register);
        if (getSupportActionBar() != null) getSupportActionBar().hide();

        db = FirebaseFirestore.getInstance();
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) { finish(); return; }
        uid = user.getUid();

        edtCccd = findViewById(R.id.edt_dr_cccd);
        edtLicense = findViewById(R.id.edt_dr_license);
        spinnerCarType = findViewById(R.id.spinner_dr_cartype);
        ivCccdImg = findViewById(R.id.iv_dr_cccd_img);
        ivLicenseImg = findViewById(R.id.iv_dr_license_img);
        btnSubmit = findViewById(R.id.btn_dr_submit);
        tvStatusBanner = findViewById(R.id.tv_dr_status_banner);

        ArrayAdapter<String> carTypeAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item,
                new String[]{"4 chỗ", "7 chỗ", "16 chỗ", "Xe tải"});
        spinnerCarType.setAdapter(carTypeAdapter);

        findViewById(R.id.btn_dr_back).setOnClickListener(v -> finish());
        findViewById(R.id.card_dr_cccd_img).setOnClickListener(v -> { targetImage = 1; pickImageLauncher.launch("image/*"); });
        findViewById(R.id.card_dr_license_img).setOnClickListener(v -> { targetImage = 2; pickImageLauncher.launch("image/*"); });
        btnSubmit.setOnClickListener(v -> submit());

        loadExisting();
    }

    /** Nếu đã đăng ký trước đó, hiển thị lại trạng thái + dữ liệu cũ. */
    private void loadExisting() {
        db.collection("users").document(uid).get().addOnSuccessListener(doc -> {
            if (!doc.exists()) return;
            String status = doc.getString("driverStatus");
            String cccd = doc.getString("cccd");
            String license = doc.getString("licenseNumber");
            cccdImageUrl = doc.getString("cccdImageUrl");
            licenseImageUrl = doc.getString("licenseImageUrl");

            if (cccd != null) edtCccd.setText(cccd);
            if (license != null) edtLicense.setText(license);
            if (cccdImageUrl != null) Glide.with(this).load(cccdImageUrl).into(ivCccdImg);
            if (licenseImageUrl != null) Glide.with(this).load(licenseImageUrl).into(ivLicenseImg);

            if ("pending".equals(status)) {
                tvStatusBanner.setVisibility(View.VISIBLE);
                tvStatusBanner.setText("Hồ sơ của bạn đang chờ Admin duyệt.");
                btnSubmit.setText("Cập nhật hồ sơ");
            } else if ("rejected".equals(status)) {
                tvStatusBanner.setVisibility(View.VISIBLE);
                tvStatusBanner.setText("Hồ sơ bị từ chối. Vui lòng cập nhật và gửi lại.");
            } else if ("approved".equals(status)) {
                tvStatusBanner.setVisibility(View.VISIBLE);
                tvStatusBanner.setText("Bạn đã là tài xế. Đăng xuất và đăng nhập lại để vào trang tài xế.");
            }
        });
    }

    private void uploadImage(Uri uri, int which) {
        Toast.makeText(this, "Đang tải ảnh lên...", Toast.LENGTH_SHORT).show();
        CloudinaryHelper.uploadImage(this, uri, new CloudinaryHelper.OnUploadCallback() {
            @Override
            public void onSuccess(String imageUrl) {
                if (isFinishing()) return;
                if (which == 1) {
                    cccdImageUrl = imageUrl;
                    Glide.with(DriverRegisterActivity.this).load(imageUrl).into(ivCccdImg);
                } else {
                    licenseImageUrl = imageUrl;
                    Glide.with(DriverRegisterActivity.this).load(imageUrl).into(ivLicenseImg);
                }
            }

            @Override
            public void onFailure(String error) {
                if (isFinishing()) return;
                Toast.makeText(DriverRegisterActivity.this, "Lỗi tải ảnh: " + error, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void submit() {
        String cccd = edtCccd.getText().toString().trim();
        String license = edtLicense.getText().toString().trim();
        String carType = spinnerCarType.getSelectedItem() != null
                ? spinnerCarType.getSelectedItem().toString() : "";

        if (cccd.isEmpty() || license.isEmpty()) {
            Toast.makeText(this, "Vui lòng nhập đầy đủ CCCD và số bằng lái", Toast.LENGTH_SHORT).show();
            return;
        }

        Map<String, Object> data = new HashMap<>();
        data.put("cccd", cccd);
        data.put("licenseNumber", license);
        data.put("driverCarType", carType);
        data.put("driverStatus", "pending");
        data.put("appliedAt", Timestamp.now());
        if (cccdImageUrl != null) data.put("cccdImageUrl", cccdImageUrl);
        if (licenseImageUrl != null) data.put("licenseImageUrl", licenseImageUrl);

        btnSubmit.setEnabled(false);
        db.collection("users").document(uid).update(data)
                .addOnSuccessListener(unused -> {
                    Toast.makeText(this, "✅ Đã gửi đăng ký. Chờ Admin duyệt!", Toast.LENGTH_LONG).show();
                    finish();
                })
                .addOnFailureListener(e -> {
                    btnSubmit.setEnabled(true);
                    Toast.makeText(this, "Lỗi: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }
}
