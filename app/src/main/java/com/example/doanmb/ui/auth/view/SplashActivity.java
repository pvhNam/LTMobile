package com.example.doanmb.ui.auth.view;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;

import com.example.doanmb.ui.home.view.MainActivity;
import com.example.doanmb.R;
import com.example.doanmb.ui.admin.view.AdminDashboardActivity;
import com.example.doanmb.ui.driver.view.DriverDashboardActivity;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

public class SplashActivity extends AppCompatActivity {

    private Button btnNext;
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getSupportActionBar() != null) getSupportActionBar().hide();
        setContentView(R.layout.activity_splash);

        mAuth = FirebaseAuth.getInstance();
        db   = FirebaseFirestore.getInstance();
        btnNext = findViewById(R.id.btn_next);

        FirebaseUser currentUser = mAuth.getCurrentUser();

        if (currentUser != null) {
            // Đã đăng nhập → tự chuyển sau 1.5 giây
            btnNext.setVisibility(View.GONE);
            new Handler(Looper.getMainLooper()).postDelayed(
                    () -> navigateByRole(currentUser.getUid()), 1500);
        } else {
            // Chưa đăng nhập → hiện nút Tiếp theo
            btnNext.setVisibility(View.VISIBLE);
            btnNext.setOnClickListener(v -> {
                startActivity(new Intent(this, LoginActivity.class));
                finish();
            });
        }
    }

    private void navigateByRole(String uid) {
        db.collection("users").document(uid).get()
                .addOnSuccessListener((DocumentSnapshot doc) -> {
                    String role = doc.getString("role");
                    boolean isDriver = Boolean.TRUE.equals(doc.getBoolean("isDriver"));
                    Intent intent;
                    if ("ADMIN".equals(role)) intent = new Intent(this, AdminDashboardActivity.class);
                    else if (isDriver)        intent = new Intent(this, DriverDashboardActivity.class);
                    else                      intent = new Intent(this, MainActivity.class);
                    startActivity(intent);
                    finish();
                })
                .addOnFailureListener(e -> {
                    startActivity(new Intent(this, MainActivity.class));
                    finish();
                });
    }
}