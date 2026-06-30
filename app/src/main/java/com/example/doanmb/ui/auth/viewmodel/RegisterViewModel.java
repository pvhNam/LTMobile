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

    // Thông báo dạng text để View hiển thị Toast.
    private final MutableLiveData<String> message = new MutableLiveData<>();
    // Cờ báo đăng ký thành công để View đóng màn.
    private final MutableLiveData<Boolean> registerSuccess = new MutableLiveData<>();

    /** LiveData thông báo (thành công / lỗi) để View quan sát và hiện Toast. */
    public LiveData<String> getMessage() {
        return message;
    }

    /** LiveData báo đăng ký thành công; View quan sát để đóng màn. */
    public LiveData<Boolean> getRegisterSuccess() {
        return registerSuccess;
    }

    /**
     * Đăng ký tài khoản mới: validate dữ liệu, tạo tài khoản trên Firebase Auth,
     * rồi ghi hồ sơ vào Firestore {@code users/{uid}} với vai trò mặc định "CUSTOMER".
     *
     * @param name     họ tên
     * @param phone    số điện thoại
     * @param email    email đăng nhập
     * @param password mật khẩu
     * @param confirm  nhập lại mật khẩu (phải khớp password)
     */
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

        // Lambda yêu cầu biến effectively-final nên sao chép lại các giá trị đã trim.
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

                    // Tạo hồ sơ người dùng lưu vào Firestore, gắn vai trò mặc định CUSTOMER.
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