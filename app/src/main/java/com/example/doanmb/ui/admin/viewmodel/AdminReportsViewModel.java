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
 * ViewModel màn khiếu nại (admin): tải danh sách theo tab (chờ xử lý / tất cả),
 * cập nhật trạng thái khiếu nại và xoá bài đăng bị báo cáo.
 */
public class AdminReportsViewModel extends ViewModel {

    private final MutableLiveData<AdminDocList> reports = new MutableLiveData<>(AdminDocList.empty());
    private final MutableLiveData<String> message = new MutableLiveData<>();
    private boolean showingPending = true;

    public LiveData<AdminDocList> getReports() { return reports; }
    public LiveData<String> getMessage() { return message; }
    public boolean isShowingPending() { return showingPending; }

    private static FirebaseFirestore db() { return FirebaseFirestore.getInstance(); }

    public void setTab(boolean pending) {
        showingPending = pending;
        load();
    }

    public void load() {
        com.google.firebase.firestore.Query q = db().collection("reports");
        if (showingPending) q = q.whereEqualTo("status", "pending");
        q.get()
                .addOnSuccessListener(snap -> {
                    List<Map<String, Object>> data = new ArrayList<>();
                    List<String> ids = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : snap) {
                        data.add(doc.getData());
                        ids.add(doc.getId());
                    }
                    reports.setValue(new AdminDocList(data, ids));
                })
                .addOnFailureListener(e -> message.setValue("Lỗi tải khiếu nại: " + e.getMessage()));
    }

    public void updateStatus(String reportId, String newStatus) {
        db().collection("reports").document(reportId)
                .update("status", newStatus)
                .addOnSuccessListener(v -> {
                    message.setValue("resolved".equals(newStatus) ? "✅ Đã đánh dấu xử lý" : "Đã bỏ qua khiếu nại");
                    load();
                })
                .addOnFailureListener(e -> message.setValue("Lỗi: " + e.getMessage()));
    }

    /** Xoá document trong "cars" rồi đánh dấu khiếu nại đã xử lý. */
    public void deletePost(String reportId, String targetId) {
        if (targetId == null || targetId.isEmpty()) {
            message.setValue("Không tìm thấy bài đăng để xóa");
            return;
        }
        db().collection("cars").document(targetId).delete()
                .addOnSuccessListener(v -> {
                    message.setValue("🗑 Đã xóa bài đăng");
                    updateStatus(reportId, "resolved");
                })
                .addOnFailureListener(e -> message.setValue("Lỗi xóa bài: " + e.getMessage()));
    }
}
