package com.example.doanmb.ui.auth.viewmodel;

import android.util.Patterns;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class RegisterViewModel extends ViewModel {

    private final FirebaseAuth mAuth = FirebaseAuth.getInstance();
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    private final MutableLiveData<String> message = new MutableLiveData<>();
    private final MutableLiveData<Boolean> registerSuccess = new MutableLiveData<>();

    public LiveData<String> getMessage() {
        return message;
    }

    /** Đóng màn sau khi đăng ký xog. */
    public LiveData<Boolean> getRegisterSuccess() {
        return registerSuccess;
    }

    public void register(String name, String phone, String email, String password, String confirm) {
        name = name.trim();
        phone = phone.trim();
        email = email.trim();
        password = password.trim();
        confirm = confirm.trim();

        if (name.isEmpty() || phone.isEmpty() || email.isEmpty()
                || password.isEmpty() || confirm.isEmpty()) {
            message.setValue("Vui lòng nhập đầy đủ thông tin!");
            return;
        }

        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            message.setValue("Email đã tồn tại!");
            return;
        }

        if (!password.equals(confirm)) {
            message.setValue("Mật khẩu không hợp lệ!");
            return;
        }

        String finalName = name;
        String finalPhone = phone;
        String finalEmail = email;
        mAuth.createUserWithEmailAndPassword(email, password)
                .addOnSuccessListener(authResult -> {
                    if (authResult.getUser() == null) {
                        message.setValue("Loi dang ky: khong tao duoc tai khoan");
                        return;
                    }
                    String uid = authResult.getUser().getUid();

                    Map<String, Object> user = new HashMap<>();
                    user.put("uid", uid);
                    user.put("name", finalName);
                    user.put("email", finalEmail);
                    user.put("phone", finalPhone);
                    user.put("role", "CUSTOMER");

                    db.collection("users").document(uid).set(user)
                            .addOnSuccessListener(unused -> {
                                message.setValue("Đăng ký thành công!");
                                registerSuccess.setValue(true);
                            })
                            .addOnFailureListener(e ->
                                    message.setValue("Lỗi user: " + e.getMessage()));
                })
                .addOnFailureListener(e ->
                        message.setValue("Lỗi đăng ký: " + e.getMessage()));
    }
}