package com.example.doanmb.ui.auth.view;

import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.example.doanmb.R;
import com.example.doanmb.ui.auth.viewmodel.ForgotPasswordViewModel;

public class ForgotPassActivity extends AppCompatActivity {

    private EditText etEmail;
    private Button btnReset;

    private ForgotPasswordViewModel viewModel;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_forgotpass);

        viewModel = new ViewModelProvider(this).get(ForgotPasswordViewModel.class);

        etEmail = findViewById(R.id.et_email);
        btnReset = findViewById(R.id.btnReset);

        btnReset.setOnClickListener(v -> viewModel.resetPassword(etEmail.getText().toString()));

        observeViewModel();
    }

    /** observeViewModel: gắn lỗi email vào ô nhập, hiện Toast và đóng màn khi đã gửi mail. */
    private void observeViewModel() {
        viewModel.getEmailError().observe(this, error -> {
            if (error != null) etEmail.setError(error);
        });
        viewModel.getMessage().observe(this, msg -> {
            if (msg != null) Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
        });
        viewModel.getResetSent().observe(this, sent -> {
            if (Boolean.TRUE.equals(sent)) finish();
        });
    }
}