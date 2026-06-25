package com.example.doanmb.ui.auth.view;

import android.content.Intent;
import android.os.Bundle;
import android.util.Patterns;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.doanmb.ui.home.view.MainActivity;
import com.example.doanmb.R;
import com.example.doanmb.ui.admin.view.AdminDashboardActivity;
import com.example.doanmb.ui.driver.view.DriverDashboardActivity;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.auth.FirebaseUser;

public class LoginActivity extends AppCompatActivity {

    private EditText etEmail, etPassword;
    private Button btnDoLogin;
    private TextView tvGoToRegister,tvforgot;
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Dang nhap");
        }

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        etEmail = findViewById(R.id.et_email);
        etPassword = findViewById(R.id.et_password);
        btnDoLogin = findViewById(R.id.btn_do_login);
        tvGoToRegister = findViewById(R.id.tv_go_to_register);
        tvforgot = findViewById(R.id.tv_forgot_password);

        btnDoLogin.setOnClickListener(v -> loginUser());

        tvGoToRegister.setOnClickListener(v -> {
            startActivity(new Intent(this, RegisterActivity.class));
            finish();
        });

        tvforgot.setOnClickListener(v -> {
            startActivity(new Intent(this, ForgotPassActivity.class));
        });
    }

    private void loginUser() {
        String loginInput = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString().trim();

        if (loginInput.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Vui lòng nhập đầy đủ thông tin!", Toast.LENGTH_SHORT).show();
            return;
        }

        if (loginInput.contains("@")) {
            if (!Patterns.EMAIL_ADDRESS.matcher(loginInput).matches()) {
                Toast.makeText(this, "Email khong hop le!", Toast.LENGTH_SHORT).show();
                return;
            }

            signIn(loginInput, password);
            return;
        }
        // tìm kiếm phone trong db
        db.collection("users")
                .whereEqualTo("phone", loginInput)
                .limit(1)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    if (!querySnapshot.isEmpty()) {
                        //lấy email dựa theo sđt
                        String email = querySnapshot.getDocuments().get(0).getString("email");
                        // nếu email không có thì gán email mặc định
                        if (email == null || email.trim().isEmpty()) {
                            email = loginInput + "@doanmb.com";
                        }

                        signIn(email, password);
                    } else {
                        signIn(loginInput + "@doanmb.com", password);
                    }
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Khong tim thay tai khoan!", Toast.LENGTH_SHORT).show()
                );
    }

    private void signIn(String email, String password) {
        mAuth.signInWithEmailAndPassword(email, password)
                .addOnSuccessListener(authResult -> {
                    FirebaseUser user = authResult.getUser();
                    if (user == null) { finish(); return; }
                    db.collection("users").document(user.getUid()).get()
                            .addOnSuccessListener(doc -> {
                                String role = doc.getString("role");
                                Toast.makeText(this, "Đăng nhập thành công!", Toast.LENGTH_SHORT).show();
                                if ("ADMIN".equals(role)) {
                                    startActivity(new Intent(this, AdminDashboardActivity.class));
                                } else if ("DRIVER".equals(role)) {
                                    startActivity(new Intent(this, DriverDashboardActivity.class));
                                } else {
                                    startActivity(new Intent(this, MainActivity.class));
                                }
                                finish();
                            })
                            .addOnFailureListener(e -> finish());
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Sai email/số điện thoại hoặc mật khẩu!", Toast.LENGTH_SHORT).show()
                );
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}
