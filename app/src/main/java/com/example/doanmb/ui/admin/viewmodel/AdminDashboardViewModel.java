package com.example.doanmb.ui.admin.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

/** ViewModel khung Dashboard admin: chỉ tải tên admin để hiển thị ở header. */
public class AdminDashboardViewModel extends ViewModel {

    private final MutableLiveData<String> adminName = new MutableLiveData<>();

    public LiveData<String> getAdminName() { return adminName; }

    public void loadAdminName() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;
        FirebaseFirestore.getInstance().collection("users").document(user.getUid()).get()
                .addOnSuccessListener(doc -> {
                    if (doc != null && doc.getString("name") != null) {
                        adminName.setValue(doc.getString("name"));
                    }
                });
    }
}
