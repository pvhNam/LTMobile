package com.example.doanmb.ui.auth.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

public class SplashViewModel extends ViewModel {

    private final FirebaseAuth mAuth = FirebaseAuth.getInstance();
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    // Đích điều hướng sau màn splash (ADMIN / DRIVER / MAIN).
    private final MutableLiveData<AuthDestination> destination = new MutableLiveData<>();

    /** LiveData đích điều hướng; View quan sát để mở đúng màn theo vai trò. */
    public LiveData<AuthDestination> getDestination() {
        return destination;
    }

    /** @return true nếu đã có phiên đăng nhập (không cần đăng nhập lại). */
    public boolean isLoggedIn() {
        return mAuth.getCurrentUser() != null;
    }

    /**
     * Đọc vai trò của người dùng hiện tại trong Firestore rồi phát đích điều hướng.
     * Không có phiên đăng nhập thì bỏ qua; lỗi đọc dữ liệu thì mặc định về màn chính (MAIN).
     */
    public void resolveDestination() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) {
            return;
        }
        db.collection("users").document(currentUser.getUid()).get()
                .addOnSuccessListener(doc -> {
                    String role = doc.getString("role");
                    boolean isDriver = Boolean.TRUE.equals(doc.getBoolean("isDriver"));
                    if ("ADMIN".equals(role)) {
                        destination.setValue(AuthDestination.ADMIN);
                    } else if (isDriver) {
                        destination.setValue(AuthDestination.DRIVER);
                    } else {
                        destination.setValue(AuthDestination.MAIN);
                    }
                })
                .addOnFailureListener(e -> destination.setValue(AuthDestination.MAIN));
    }
}