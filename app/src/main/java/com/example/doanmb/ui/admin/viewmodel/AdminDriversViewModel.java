package com.example.doanmb.ui.admin.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * ViewModel danh sách tài xế đã duyệt. Tải tất cả users rồi lọc client-side:
 * tài xế = isDriver==true HOẶC driverStatus=="approved" (tài khoản cũ có thể chỉ
 * có driverStatus mà thiếu cờ isDriver).
 */
public class AdminDriversViewModel extends ViewModel {

    private final MutableLiveData<AdminDocList> drivers = new MutableLiveData<>(AdminDocList.empty());
    private final MutableLiveData<String> error = new MutableLiveData<>();

    public LiveData<AdminDocList> getDrivers() { return drivers; }
    public LiveData<String> getError() { return error; }

    public void load() {
        FirebaseFirestore.getInstance().collection("users").get()
                .addOnSuccessListener(snap -> {
                    List<Map<String, Object>> data = new ArrayList<>();
                    List<String> ids = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : snap) {
                        boolean isDriver = Boolean.TRUE.equals(doc.getBoolean("isDriver"));
                        boolean approved = "approved".equals(doc.getString("driverStatus"));
                        if (!isDriver && !approved) continue;
                        data.add(doc.getData());
                        ids.add(doc.getId());
                    }
                    drivers.setValue(new AdminDocList(data, ids));
                })
                .addOnFailureListener(e -> error.setValue(e.getMessage()));
    }
}
