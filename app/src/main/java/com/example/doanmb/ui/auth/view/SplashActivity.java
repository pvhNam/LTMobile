package com.example.doanmb.ui.auth.view;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.example.doanmb.R;
import com.example.doanmb.ui.admin.view.AdminDashboardActivity;
import com.example.doanmb.ui.auth.viewmodel.AuthDestination;
import com.example.doanmb.ui.auth.viewmodel.SplashViewModel;
import com.example.doanmb.ui.driver.view.DriverDashboardActivity;
import com.example.doanmb.ui.home.view.MainActivity;

public class SplashActivity extends AppCompatActivity {

    private Button btnNext;
    private SplashViewModel viewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getSupportActionBar() != null) getSupportActionBar().hide();
        setContentView(R.layout.activity_splash);

        viewModel = new ViewModelProvider(this).get(SplashViewModel.class);
        btnNext = findViewById(R.id.btn_next);

        viewModel.getDestination().observe(this, this::navigateTo);

        if (viewModel.isLoggedIn()) {
            // Đã đăng nhập → tự chuyển sau 1.5 giây
            btnNext.setVisibility(View.GONE);
            new Handler(Looper.getMainLooper()).postDelayed(
                    () -> viewModel.resolveDestination(), 1500);
        } else {
            // Chưa đăng nhập → hiện nút Tiếp theo
            btnNext.setVisibility(View.VISIBLE);
            btnNext.setOnClickListener(v -> {
                startActivity(new Intent(this, LoginActivity.class));
                finish();
            });
        }
    }

    private void navigateTo(AuthDestination destination) {
        if (destination == null) return;
        Intent intent;
        switch (destination) {
            case ADMIN:
                intent = new Intent(this, AdminDashboardActivity.class);
                break;
            case DRIVER:
                intent = new Intent(this, DriverDashboardActivity.class);
                break;
            default:
                intent = new Intent(this, MainActivity.class);
                break;
        }
        startActivity(intent);
        finish();
    }
}