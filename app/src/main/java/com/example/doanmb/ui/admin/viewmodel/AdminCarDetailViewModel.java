package com.example.doanmb.ui.admin.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.HashMap;
import java.util.Map;

/**
 * ViewModel màn admin xem chi tiết 1 bài đăng xe: tải xe, duyệt / từ chối
 * (kèm gửi thông báo cho người đăng) và xoá (chặn khi còn đơn đang xử lý).
 */
public class AdminCarDetailViewModel extends ViewModel {

    private final MutableLiveData<DocumentSnapshot> car = new MutableLiveData<>();
    private final MutableLiveData<String> message = new MutableLiveData<>();
    private final MutableLiveData<Boolean> finishEvent = new MutableLiveData<>();

    private String carId;
    private String sellerId = "";
    private String carName  = "";

    public LiveData<DocumentSnapshot> getCar() { return car; }
    public LiveData<String> getMessage() { return message; }
    public LiveData<Boolean> getFinishEvent() { return finishEvent; }

    private static FirebaseFirestore db() { return FirebaseFirestore.getInstance(); }

    public void start(String carId) {
        if (this.carId != null) return; // đã tải
        this.carId = carId;
        load();
    }

    private void load() {
        db().collection("cars").document(carId).get()
                .addOnSuccessListener(doc -> {
                    if (!doc.exists()) {
                        message.setValue("Bài đăng không còn tồn tại");
                        finishEvent.setValue(true);
                        return;
                    }
                    carName  = str(doc.getString("name"), "Không tên");
                    sellerId = str(doc.getString("sellerId"), str(doc.getString("userId"), ""));
                    car.setValue(doc);
                })
                .addOnFailureListener(e -> {
                    message.setValue("Lỗi tải bài đăng: " + e.getMessage());
                    finishEvent.setValue(true);
                });
    }

    public void approve() {
        db().collection("cars").document(carId).update("status", "active")
                .addOnSuccessListener(v -> {
                    sendNotification(sellerId, "Bài đăng được duyệt ✅",
                            "Tin đăng \"" + carName + "\" của bạn đã được duyệt và hiển thị tới người dùng.");
                    message.setValue("✅ Đã duyệt xe");
                    finishEvent.setValue(true);
                })
                .addOnFailureListener(e -> message.setValue("Lỗi: " + e.getMessage()));
    }

    public void reject() {
        db().collection("cars").document(carId).update("status", "rejected")
                .addOnSuccessListener(v -> {
                    sendNotification(sellerId, "Bài đăng bị từ chối ❌",
                            "Tin đăng \"" + carName + "\" của bạn chưa đạt yêu cầu và đã bị từ chối. "
                                    + "Vui lòng kiểm tra lại và đăng lại.");
                    message.setValue("❌ Đã từ chối xe");
                    finishEvent.setValue(true);
                })
                .addOnFailureListener(e -> message.setValue("Lỗi: " + e.getMessage()));
    }

    /** Chặn xoá khi xe còn đơn đang xử lý (pending/confirmed) để tránh đơn mồ côi. */
    public void delete() {
        db().collection("orders").whereEqualTo("carId", carId).get()
                .addOnSuccessListener(snap -> {
                    int active = 0;
                    for (QueryDocumentSnapshot doc : snap) {
                        String st = doc.getString("status");
                        if ("pending".equals(st) || "confirmed".equals(st)) active++;
                    }
                    if (active > 0) {
                        message.setValue("Không thể xóa: xe còn " + active + " đơn đang xử lý");
                        return;
                    }
                    db().collection("cars").document(carId).delete()
                            .addOnSuccessListener(v -> {
                                message.setValue("🗑️ Đã xóa xe khỏi hệ thống");
                                finishEvent.setValue(true);
                            })
                            .addOnFailureListener(e -> message.setValue("Lỗi: " + e.getMessage()));
                })
                .addOnFailureListener(e -> message.setValue("Lỗi kiểm tra đơn: " + e.getMessage()));
    }

    /** Tạo thông báo trong collection "notifications" cho người dùng. */
    private void sendNotification(String userId, String title, String body) {
        if (userId == null || userId.isEmpty()) return;
        Map<String, Object> notif = new HashMap<>();
        notif.put("userId", userId);
        notif.put("title", title);
        notif.put("body", body);
        notif.put("createdAt", Timestamp.now());
        notif.put("read", false);
        db().collection("notifications").add(notif);
    }

    private static String str(String value, String def) {
        return (value != null && !value.isEmpty()) ? value : def;
    }
}
