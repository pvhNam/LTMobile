package com.example.doanmb.ui.auth.view;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.example.doanmb.R;
import com.example.doanmb.ui.admin.view.AdminDashboardActivity;
import com.example.doanmb.ui.auth.viewmodel.AuthDestination;
import com.example.doanmb.ui.auth.viewmodel.LoginViewModel;
import com.example.doanmb.ui.driver.view.DriverDashboardActivity;
import com.example.doanmb.ui.home.view.MainActivity;

public class LoginActivity extends AppCompatActivity {

    private EditText etEmail, etPassword;
    private Button btnDoLogin;
    private TextView tvGoToRegister, tvforgot;

    private LoginViewModel viewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Dang nhap");
        }

        viewModel = new ViewModelProvider(this).get(LoginViewModel.class);

        etEmail = findViewById(R.id.et_email);
        etPassword = findViewById(R.id.et_password);
        btnDoLogin = findViewById(R.id.btn_do_login);
        tvGoToRegister = findViewById(R.id.tv_go_to_register);
        tvforgot = findViewById(R.id.tv_forgot_password);

        btnDoLogin.setOnClickListener(v ->
                viewModel.login(etEmail.getText().toString(), etPassword.getText().toString()));

        tvGoToRegister.setOnClickListener(v -> {
            startActivity(new Intent(this, RegisterActivity.class));
            finish();
        });

        tvforgot.setOnClickListener(v ->
                startActivity(new Intent(this, ForgotPassActivity.class)));

        observeViewModel();
    }

    /** observeViewModel: hiện Toast thông báo và điều hướng khi đăng nhập thành công. */
    private void observeViewModel() {
        viewModel.getMessage().observe(this, msg -> {
            if (msg != null) Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
        });
        viewModel.getDestination().observe(this, this::navigateTo);
    }

    /** Mở màn tương ứng vai trò (Admin/Tài xế/Khách hàng) rồi đóng màn đăng nhập. */
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

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}