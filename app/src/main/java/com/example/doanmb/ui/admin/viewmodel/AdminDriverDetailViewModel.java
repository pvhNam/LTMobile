package com.example.doanmb.ui.admin.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

/**
 * ViewModel màn duyệt chi tiết hồ sơ tài xế: tải hồ sơ, duyệt / từ chối (kèm gửi
 * thông báo cho người đăng ký).
 */
public class AdminDriverDetailViewModel extends ViewModel {

    private final MutableLiveData<DocumentSnapshot> user = new MutableLiveData<>();
    private final MutableLiveData<String> message = new MutableLiveData<>();
    /** Bật true để View pop back-stack sau khi duyệt/từ chối xong. */
    private final MutableLiveData<Boolean> done = new MutableLiveData<>();
    /** Bật true khi thao tác lỗi để View bật lại nút. */
    private final MutableLiveData<Boolean> actionFailed = new MutableLiveData<>();

    private String uid;

    public LiveData<DocumentSnapshot> getUser() { return user; }
    public LiveData<String> getMessage() { return message; }
    public LiveData<Boolean> getDone() { return done; }
    public LiveData<Boolean> getActionFailed() { return actionFailed; }

    private static FirebaseFirestore db() { return FirebaseFirestore.getInstance(); }

    public void start(String uid) {
        if (this.uid != null) return; // đã tải
        this.uid = uid != null ? uid : "";
        loadUser();
    }

    private void loadUser() {
        if (uid.isEmpty()) return;
        db().collection("users").document(uid).get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) user.setValue(doc);
                });
    }

    public void approve() {
        if (uid.isEmpty()) return;
        db().collection("users").document(uid)
                .update("driverStatus", "approved", "isDriver", true)
                .addOnSuccessListener(v -> {
                    sendNotification(uid, "Đăng ký tài xế được duyệt ✅",
                            "Hồ sơ của bạn đã được Admin duyệt. Đăng xuất và đăng nhập lại để vào giao diện tài xế.");
                    message.setValue("Đã duyệt tài xế!");
                    done.setValue(true);
                })
                .addOnFailureListener(e -> {
                    actionFailed.setValue(true);
                    message.setValue("Lỗi: " + e.getMessage());
                });
    }

    public void reject() {
        if (uid.isEmpty()) return;
        db().collection("users").document(uid)
                .update("driverStatus", "rejected", "isDriver", false)
                .addOnSuccessListener(v -> {
                    sendNotification(uid, "Đăng ký tài xế bị từ chối ❌",
                            "Hồ sơ của bạn chưa đáp ứng yêu cầu. Vui lòng cập nhật và gửi lại.");
                    message.setValue("Đã từ chối hồ sơ.");
                    done.setValue(true);
                })
                .addOnFailureListener(e -> {
                    actionFailed.setValue(true);
                    message.setValue("Lỗi: " + e.getMessage());
                });
    }

    private void sendNotification(String userId, String title, String body) {
        Map<String, Object> notif = new HashMap<>();
        notif.put("userId", userId);
        notif.put("title", title);
        notif.put("body", body);
        notif.put("createdAt", Timestamp.now());
        notif.put("read", false);
        db().collection("notifications").add(notif);
    }
}
