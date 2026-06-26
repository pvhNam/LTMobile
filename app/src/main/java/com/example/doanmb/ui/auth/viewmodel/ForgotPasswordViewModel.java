package com.example.doanmb.ui.auth.viewmodel;

import android.util.Patterns;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.google.firebase.auth.FirebaseAuth;

public class ForgotPasswordViewModel extends ViewModel {

    private final FirebaseAuth mAuth = FirebaseAuth.getInstance();

    private final MutableLiveData<String> message = new MutableLiveData<>();
    private final MutableLiveData<String> emailError = new MutableLiveData<>();
    private final MutableLiveData<Boolean> resetSent = new MutableLiveData<>();

    public LiveData<String> getMessage() {
        return message;
    }

    public LiveData<String> getEmailError() {
        return emailError;
    }

    /** Đóng màn sau khi gửi mail. */
    public LiveData<Boolean> getResetSent() {
        return resetSent;
    }

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