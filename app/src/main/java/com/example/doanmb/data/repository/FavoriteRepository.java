package com.example.doanmb.data.repository;

import androidx.annotation.NonNull;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Quản lý danh sách xe yêu thích của user, lưu tại users/{uid}/favorites/{carId}.
 * Mỗi doc chỉ giữ carId + thời gian; thông tin xe luôn lấy mới từ collection "cars"
 * để xe đã bán/ẩn/xóa tự biến mất khỏi danh sách yêu thích khi hiển thị.
 */
public final class FavoriteRepository {

    private FavoriteRepository() {}

    public interface OnIdsLoaded { void onLoaded(Set<String> carIds); }

    public interface OnResult { void onResult(boolean isFavorite); }

    /** Kiểm tra một xe đã được user yêu thích chưa. */
    public static void contains(String uid, String carId, @NonNull OnResult cb) {
        if (uid == null || carId == null || carId.isEmpty()) { cb.onResult(false); return; }
        col(uid).document(carId).get()
                .addOnSuccessListener(d -> cb.onResult(d.exists()))
                .addOnFailureListener(e -> cb.onResult(false));
    }

    private static CollectionReference col(String uid) {
        return FirebaseFirestore.getInstance()
                .collection("users").document(uid).collection("favorites");
    }

    public static void add(String uid, String carId) {
        if (uid == null || carId == null || carId.isEmpty()) return;
        Map<String, Object> data = new HashMap<>();
        data.put("carId", carId);
        data.put("createdAt", Timestamp.now());
        col(uid).document(carId).set(data);
    }

    public static void remove(String uid, String carId) {
        if (uid == null || carId == null || carId.isEmpty()) return;
        col(uid).document(carId).delete();
    }

    public static void loadIds(String uid, @NonNull OnIdsLoaded cb) {
        Set<String> ids = new HashSet<>();
        if (uid == null) { cb.onLoaded(ids); return; }
        col(uid).get()
                .addOnSuccessListener(snap -> {
                    for (QueryDocumentSnapshot d : snap) ids.add(d.getId());
                    cb.onLoaded(ids);
                })
                .addOnFailureListener(e -> cb.onLoaded(ids));
    }
}
