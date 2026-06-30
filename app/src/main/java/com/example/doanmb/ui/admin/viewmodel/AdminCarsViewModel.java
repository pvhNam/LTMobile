package com.example.doanmb.ui.admin.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * ViewModel màn quản lý xe (admin): tải xe theo tab (chờ duyệt / tất cả) và
 * duyệt / từ chối / xoá. Tải tất cả rồi lọc client-side để bắt được cả xe cũ
 * không có field "status" (null = chưa duyệt).
 */
public class AdminCarsViewModel extends ViewModel {

    private final MutableLiveData<AdminDocList> cars = new MutableLiveData<>(AdminDocList.empty());
    private final MutableLiveData<String> message = new MutableLiveData<>();
    private boolean showingPending = true;

    public LiveData<AdminDocList> getCars() { return cars; }
    public LiveData<String> getMessage() { return message; }
    public boolean isShowingPending() { return showingPending; }

    private static FirebaseFirestore db() { return FirebaseFirestore.getInstance(); }

    public void setTab(boolean pending) {
        showingPending = pending;
        load();
    }

    public void load() {
        db().collection("cars").get()
                .addOnSuccessListener(snap -> {
                    List<QueryDocumentSnapshot> docs = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : snap) {
                        String status = doc.getString("status");
                        if (showingPending) {
                            if (status == null || "pending".equals(status)) docs.add(doc);
                        } else {
                            docs.add(doc);
                        }
                    }
                    docs.sort((a, b) -> {
                        Timestamp ta = a.getTimestamp("createdAt");
                        Timestamp tb = b.getTimestamp("createdAt");
                        if (ta == null && tb == null) return 0;
                        if (ta == null) return 1;   // thiếu ngày -> xuống cuối
                        if (tb == null) return -1;
                        return tb.compareTo(ta);    // mới nhất trước
                    });
                    List<Map<String, Object>> data = new ArrayList<>();
                    List<String> ids = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : docs) {
                        data.add(doc.getData());
                        ids.add(doc.getId());
                    }
                    cars.setValue(new AdminDocList(data, ids));
                })
                .addOnFailureListener(e -> message.setValue("Lỗi tải xe: " + e.getMessage()));
    }

    public void approveCar(String carId) {
        db().collection("cars").document(carId).update("status", "active")
                .addOnSuccessListener(v -> { message.setValue("✅ Đã duyệt xe"); load(); })
                .addOnFailureListener(e -> message.setValue("Lỗi: " + e.getMessage()));
    }

    public void rejectCar(String carId) {
        db().collection("cars").document(carId).update("status", "rejected")
                .addOnSuccessListener(v -> { message.setValue("❌ Đã từ chối xe"); load(); })
                .addOnFailureListener(e -> message.setValue("Lỗi: " + e.getMessage()));
    }

    /** Chặn xoá khi xe còn đơn đang xử lý (pending/confirmed) để tránh đơn mồ côi. */
    public void deleteCar(String carId) {
        db().collection("orders").whereEqualTo("carId", carId).get()
                .addOnSuccessListener(snap -> {
                    int active = 0;
                    for (QueryDocumentSnapshot d : snap) {
                        String st = d.getString("status");
                        if ("pending".equals(st) || "confirmed".equals(st)) active++;
                    }
                    if (active > 0) {
                        message.setValue("Không thể xóa: xe còn " + active + " đơn đang xử lý");
                        return;
                    }
                    doDeleteCar(carId);
                })
                .addOnFailureListener(e -> message.setValue("Lỗi kiểm tra đơn: " + e.getMessage()));
    }

    private void doDeleteCar(String carId) {
        db().collection("cars").document(carId).delete()
                .addOnSuccessListener(v -> { message.setValue("🗑️ Đã xóa xe khỏi hệ thống"); load(); })
                .addOnFailureListener(e -> message.setValue("Lỗi: " + e.getMessage()));
    }
}
