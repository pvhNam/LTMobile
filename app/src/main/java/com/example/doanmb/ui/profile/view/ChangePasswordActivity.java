package com.example.doanmb.ui.profile.view;

import android.os.Bundle;
import android.text.InputType;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.example.doanmb.R;
import com.example.doanmb.core.util.EdgeToEdgeUtil;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.EmailAuthProvider;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class ChangePasswordActivity extends AppCompatActivity {

    private EditText edtOldPassword, edtNewPassword, edtConfirmPassword;
    private ImageView imgToggleOld, imgToggleNew, imgToggleConfirm;
    private Button btnUpdatePassword;
    private ImageView btnBack;
    private FirebaseAuth mAuth;

    // Biến lưu trạng thái ẩn hiện của từng ô nhập liệu
    private boolean isOldVisible = false;
    private boolean isNewVisible = false;
    private boolean isConfirmVisible = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdgeUtil.enable(this, true);
        setContentView(R.layout.activity_change_password);
        EdgeToEdgeUtil.applyHeaderAndScroll(null, findViewById(R.id.header_bar));

        mAuth = FirebaseAuth.getInstance();

        // Ánh xạ các trường dữ liệu
        btnBack = findViewById(R.id.btn_back_security);
        edtOldPassword = findViewById(R.id.edt_old_password);
        edtNewPassword = findViewById(R.id.edt_new_password);
        edtConfirmPassword = findViewById(R.id.edt_confirm_password);
        btnUpdatePassword = findViewById(R.id.btn_update_password);

        // Ánh xạ các nút bấm ẩn/hiện con mắt
        imgToggleOld = findViewById(R.id.img_toggle_old_password);
        imgToggleNew = findViewById(R.id.img_toggle_new_password);
        imgToggleConfirm = findViewById(R.id.img_toggle_confirm_password);

        // Xử lý nút quay lại
        btnBack.setOnClickListener(v -> finish());

        // Cấu hình sự kiện ẩn hiện cho từng ô mật khẩu
        setupPasswordVisibilityToggles();

        // Nút bấm thực hiện đổi mật khẩu
        btnUpdatePassword.setOnClickListener(v -> handleChangePassword());
    }

    private void setupPasswordVisibilityToggles() {
        // 1. Ô Mật khẩu hiện tại
        imgToggleOld.setOnClickListener(v -> {
            isOldVisible = !isOldVisible;
            togglePasswordVisibility(edtOldPassword, imgToggleOld, isOldVisible);
        });

        // 2. Ô Mật khẩu mới
        imgToggleNew.setOnClickListener(v -> {
            isNewVisible = !isNewVisible;
            togglePasswordVisibility(edtNewPassword, imgToggleNew, isNewVisible);
        });

        // 3. Ô Xác nhận mật khẩu mới
        imgToggleConfirm.setOnClickListener(v -> {
            isConfirmVisible = !isConfirmVisible;
            togglePasswordVisibility(edtConfirmPassword, imgToggleConfirm, isConfirmVisible);
        });
    }

    private void togglePasswordVisibility(EditText editText, ImageView imageView, boolean isVisible) {
        if (isVisible) {
            // Hiện mật khẩu dưới dạng chữ thường công khai
            editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
            imageView.setAlpha(1.0f); // Làm sáng icon mắt lên khi đang hiện chữ
        } else {
            // Ẩn mật khẩu thành các dấu chấm đen tròn bảo mật
            editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
            imageView.setAlpha(0.4f); // Làm mờ nhẹ icon mắt báo hiệu đang ẩn chữ
        }
        // Giữ nguyên con trỏ chuột ở cuối dòng văn bản khi đổi trạng thái nhập liệu
        editText.setSelection(editText.getText().length());
    }

    private void handleChangePassword() {
        String oldPassword = edtOldPassword.getText().toString().trim();
        String newPassword = edtNewPassword.getText().toString().trim();
        String confirmPassword = edtConfirmPassword.getText().toString().trim();

        if (oldPassword.isEmpty() || newPassword.isEmpty() || confirmPassword.isEmpty()) {
            Toast.makeText(this, "Vui lòng nhập đầy đủ thông tin!", Toast.LENGTH_SHORT).show();
            return;
        }

        if (newPassword.length() < 6) {
            Toast.makeText(this, "Mật khẩu mới phải có ít nhất 6 ký tự!", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!newPassword.equals(confirmPassword)) {
            Toast.makeText(this, "Mật khẩu xác nhận không trùng khớp!", Toast.LENGTH_SHORT).show();
            return;
        }

        FirebaseUser user = mAuth.getCurrentUser();
        if (user != null && user.getEmail() != null) {
            btnUpdatePassword.setEnabled(false);
            btnUpdatePassword.setText("Đang xử lý...");

            AuthCredential credential = EmailAuthProvider.getCredential(user.getEmail(), oldPassword);

            user.reauthenticate(credential).addOnCompleteListener(task -> {
                if (task.isSuccessful()) {
                    user.updatePassword(newPassword).addOnCompleteListener(updateTask -> {
                        btnUpdatePassword.setEnabled(true);
                        btnUpdatePassword.setText("Cập nhật mật khẩu");

                        if (updateTask.isSuccessful()) {
                            Toast.makeText(ChangePasswordActivity.this, "Đổi mật khẩu thành công!", Toast.LENGTH_SHORT).show();
                            finish();
                        } else {
                            Toast.makeText(ChangePasswordActivity.this, "Lỗi: " + updateTask.getException().getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    });
                } else {
                    btnUpdatePassword.setEnabled(true);
                    btnUpdatePassword.setText("Cập nhật mật khẩu");
                    Toast.makeText(ChangePasswordActivity.this, "Mật khẩu hiện tại không chính xác!", Toast.LENGTH_SHORT).show();
                }
            });
        }
    }
}
