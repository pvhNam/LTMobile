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

    private final MutableLiveData<String> message = new MutableLiveData<>();
    private final MutableLiveData<AuthDestination> destination = new MutableLiveData<>();

    public LiveData<String> getMessage() {
        return message;
    }

    public LiveData<AuthDestination> getDestination() {
        return destination;
    }

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