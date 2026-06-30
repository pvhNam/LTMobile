package com.example.doanmb.ui.admin.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

/** ViewModel trang hồ sơ admin: email lấy từ tài khoản đăng nhập, tên từ Firestore. */
public class AdminProfileViewModel extends ViewModel {

    private final MutableLiveData<String> name = new MutableLiveData<>();
    private final MutableLiveData<String> email = new MutableLiveData<>();

    public LiveData<String> getName() { return name; }
    public LiveData<String> getEmail() { return email; }

    public void load() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;
        if (user.getEmail() != null) email.setValue(user.getEmail());

        FirebaseFirestore.getInstance().collection("users").document(user.getUid()).get()
                .addOnSuccessListener(doc -> {
                    if (doc == null) return;
                    String n = doc.getString("name");
                    if (n != null && !n.isEmpty()) name.setValue(n);
                });
    }
}
