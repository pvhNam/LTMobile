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

    private final MutableLiveData<AuthDestination> destination = new MutableLiveData<>();

    public LiveData<AuthDestination> getDestination() {
        return destination;
    }

    public boolean isLoggedIn() {
        return mAuth.getCurrentUser() != null;
    }

    /** lấy vai trò của user hiện tại và điều hướng. */
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