package com.example.doanmb.ui.auth.viewmodel;

import android.util.Patterns;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.google.firebase.auth.FirebaseAuth;

public class ForgotPasswordViewModel extends ViewModel {

    private final FirebaseAuth mAuth = FirebaseAuth.getInstance();

    // Thông báo chung để View hiển thị Toast.
    private final MutableLiveData<String> message = new MutableLiveData<>();
    // Lỗi riêng cho ô nhập email (hiển thị ngay dưới EditText).
    private final MutableLiveData<String> emailError = new MutableLiveData<>();
    // Cờ báo đã gửi email đặt lại mật khẩu để View đóng màn.
    private final MutableLiveData<Boolean> resetSent = new MutableLiveData<>();

    /** LiveData thông báo kết quả gửi mail để View quan sát và hiện Toast. */
    public LiveData<String> getMessage() {
        return message;
    }

    /** LiveData lỗi định dạng email để View gắn vào ô nhập. */
    public LiveData<String> getEmailError() {
        return emailError;
    }

    /** LiveData báo đã gửi mail thành công; View quan sát để đóng màn. */
    public LiveData<Boolean> getResetSent() {
        return resetSent;
    }

    /**
     * Gửi email đặt lại mật khẩu qua Firebase Auth.
     * Email sai định dạng → báo {@link #emailError}; gửi xong → phát {@link #resetSent}.
     *
     * @param email địa chỉ email đã đăng ký
     */
    public void resetPassword(String email) {
        email = email.trim();
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            emailError.setValue("Email không hợp lệ!");
            return;
        }
        mAuth.sendPasswordResetEmail(email).addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                message.setValue("Vui lòng kiểm tra email để lấy lại mật khẩu!");
                resetSent.setValue(true);
            } else {
                String msg = task.getException() != null
                        ? task.getException().getMessage() : "không xác định";
                message.setValue("Lỗi: " + msg);
            }
        });
    }
}