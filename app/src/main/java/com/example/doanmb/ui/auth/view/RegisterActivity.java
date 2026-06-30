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
import com.example.doanmb.ui.auth.viewmodel.RegisterViewModel;

public class RegisterActivity extends AppCompatActivity {

    private EditText etName, etPhone, etEmail, etPassword, etConfirm;
    private Button btnRegister;
    private TextView tvBackToLogin;

    private RegisterViewModel viewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        viewModel = new ViewModelProvider(this).get(RegisterViewModel.class);

        etName = findViewById(R.id.et_register_name);
        etPhone = findViewById(R.id.et_register_phone);
        etEmail = findViewById(R.id.et_register_email);
        etPassword = findViewById(R.id.et_register_password);
        etConfirm = findViewById(R.id.et_register_confirm_password);
        btnRegister = findViewById(R.id.btn_do_register);
        tvBackToLogin = findViewById(R.id.tv_back_to_login);

        btnRegister.setOnClickListener(v -> viewModel.register(
                etName.getText().toString(),
                etPhone.getText().toString(),
                etEmail.getText().toString(),
                etPassword.getText().toString(),
                etConfirm.getText().toString()));

        tvBackToLogin.setOnClickListener(v -> startActivity(new Intent(this, LoginActivity.class)));

        observeViewModel();
    }

    /** observeViewModel: hiện Toast thông báo và đóng màn khi đăng ký thành công. */
    private void observeViewModel() {
        viewModel.getMessage().observe(this, msg -> {
            if (msg != null) Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
        });
        viewModel.getRegisterSuccess().observe(this, success -> {
            if (Boolean.TRUE.equals(success)) finish();
        });
    }
}