package com.example.doanmb.ui.auth.viewmodel;

import android.util.Patterns;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

public class LoginViewModel extends ViewModel {

    private final FirebaseAuth mAuth = FirebaseAuth.getInstance();
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    // Thông báo dạng text để View hiển thị Toast.
    private final MutableLiveData<String> message = new MutableLiveData<>();
    // Đích điều hướng sau khi đăng nhập thành công (ADMIN / DRIVER / MAIN).
    private final MutableLiveData<AuthDestination> destination = new MutableLiveData<>();

    /** LiveData thông báo (thành công / lỗi) để View quan sát và hiện Toast. */
    public LiveData<String> getMessage() {
        return message;
    }

    /** LiveData đích điều hướng; View quan sát để mở đúng màn theo vai trò. */
    public LiveData<AuthDestination> getDestination() {
        return destination;
    }

    /**
     * Đăng nhập linh hoạt bằng email HOẶC số điện thoại.
     * - Nếu chuỗi nhập chứa "@" → coi là email, kiểm tra định dạng rồi đăng nhập trực tiếp.
     * - Nếu không có "@" → coi là SĐT, tra ngược email đã đăng ký trong Firestore rồi đăng nhập;
     *   không tìm thấy thì dùng tạm "{sđt}@doanmb.com" để tương thích dữ liệu cũ.
     *
     * @param loginInput email hoặc số điện thoại người dùng nhập
     * @param password   mật khẩu
     */
    public void login(String loginInput, String password) {
        loginInput = loginInput.trim();
        password = password.trim();

        if (loginInput.isEmpty() || password.isEmpty()) {
            message.setValue("Vui lòng nhập đầy đủ thông tin!");
            return;
        }

        if (loginInput.contains("@")) {
            if (!Patterns.EMAIL_ADDRESS.matcher(loginInput).matches()) {
                message.setValue("Email không hợp lệ!");
                return;
            }
            signIn(loginInput, password);
            return;
        }

        // tìm email theo sđt
        String input = loginInput;
        String pass = password;
        db.collection("users")
                .whereEqualTo("phone", input)
                .limit(1)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    if (!querySnapshot.isEmpty()) {
                        String email = querySnapshot.getDocuments().get(0).getString("email");
                        if (email == null || email.trim().isEmpty()) {
                            email = input + "@doanmb.com";
                        }
                        signIn(email, pass);
                    } else {
                        signIn(input + "@doanmb.com", pass);
                    }
                })
                .addOnFailureListener(e -> message.setValue("không tìm thấy tài khoản!"));
    }

    /**
     * Xác thực với Firebase Auth bằng email + mật khẩu; thành công thì đọc vai trò
     * trong Firestore và phát đích điều hướng tương ứng. Sai thông tin → báo lỗi.
     */
    private void signIn(String email, String password) {
        mAuth.signInWithEmailAndPassword(email, password)
                .addOnSuccessListener(authResult -> {
                    FirebaseUser user = authResult.getUser();
                    if (user == null) {
                        return;
                    }
                    db.collection("users").document(user.getUid()).get()
                            .addOnSuccessListener(doc -> {
                                String role = doc.getString("role");
                                boolean isDriver = Boolean.TRUE.equals(doc.getBoolean("isDriver"));
                                message.setValue("Đăng nhập thành công!");
                                if ("ADMIN".equals(role)) {
                                    destination.setValue(AuthDestination.ADMIN);
                                } else if (isDriver) {
                                    destination.setValue(AuthDestination.DRIVER);
                                } else {
                                    destination.setValue(AuthDestination.MAIN);
                                }
                            });
                })
                .addOnFailureListener(e ->
                        message.setValue("Sai email/số điện thoại hoặc mật khẩu!"));
    }
}