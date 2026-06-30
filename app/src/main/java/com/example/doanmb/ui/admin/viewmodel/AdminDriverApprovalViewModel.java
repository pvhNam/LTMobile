package com.example.doanmb.ui.admin.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.doanmb.data.model.User;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.List;

/** ViewModel danh sách hồ sơ tài xế đang chờ duyệt (driverStatus == "pending"). */
public class AdminDriverApprovalViewModel extends ViewModel {

    private final MutableLiveData<List<User>> pending = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<String> error = new MutableLiveData<>();

    public LiveData<List<User>> getPending() { return pending; }
    public LiveData<String> getError() { return error; }

    public void load() {
        FirebaseFirestore.getInstance().collection("users")
                .whereEqualTo("driverStatus", "pending")
                .get()
                .addOnSuccessListener(snap -> {
                    List<User> list = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : snap) {
                        User u = parseUser(doc);
                        if (u != null) list.add(u);
                    }
                    pending.setValue(list);
                })
                .addOnFailureListener(e -> error.setValue(e.getMessage()));
    }

    /**
     * Đọc 1 hồ sơ tài xế an toàn. Tránh toObject(User.class) vì nó ném exception
     * (gây sập cả màn) nếu một field bị lưu sai kiểu trong Firestore. Trả null để
     * bỏ qua doc lỗi thay vì làm sập danh sách.
     */
    private User parseUser(QueryDocumentSnapshot doc) {
        try {
            User u = new User();
            u.setUid(doc.getId());
            u.setName(getStr(doc, "name"));
            u.setPhone(getStr(doc, "phone"));
            u.setCccd(getStr(doc, "cccd"));
            u.setLicenseNumber(getStr(doc, "licenseNumber"));
            u.setCccdImageUrl(getStr(doc, "cccdImageUrl"));
            u.setLicenseImageUrl(getStr(doc, "licenseImageUrl"));
            u.setDriverCarType(getStr(doc, "driverCarType"));
            u.setDriverStatus(getStr(doc, "driverStatus"));
            Object applied = doc.get("appliedAt");
            if (applied instanceof Timestamp) u.setAppliedAt((Timestamp) applied);
            return u;
        } catch (Exception e) {
            return null;
        }
    }

    private String getStr(QueryDocumentSnapshot doc, String key) {
        Object v = doc.get(key);
        return v instanceof String ? (String) v : null;
    }
}
