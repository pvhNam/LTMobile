package com.example.doanmb.data.repository;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Gom toàn bộ thao tác Firestore với collection "orders" (đơn mua / thuê / chuyến)
 * và việc ghi "notifications" liên quan đến đơn. View/ViewModel không truy vấn
 * Firestore trực tiếp nữa mà gọi qua repository này.
 */
public final class OrderRepository {

    private static final String COL_ORDERS = "orders";
    private static final String COL_NOTIFS = "notifications";

    private OrderRepository() {}

    private static FirebaseFirestore db() { return FirebaseFirestore.getInstance(); }

    // ── Callbacks ────────────────────────────────────────────────────────────

    public interface OnCreated { void onCreated(String orderId); void onError(String message); }

    public interface OnResult { void onSuccess(); void onError(String message); }

    public interface OnOrderLoaded { void onLoaded(DocumentSnapshot doc); void onError(String message); }

    public interface OnPending { void onResult(boolean hasPending); }

    // ── Tạo / xoá / cập nhật đơn ──────────────────────────────────────────────

    /** Kiểm tra user đã có đơn đang chờ (pending) cho xe này chưa → chặn spam. */
    public static void hasPendingOrder(@Nullable String buyerId, @Nullable String carId,
                                       @NonNull OnPending cb) {
        db().collection(COL_ORDERS)
                .whereEqualTo("buyerId", buyerId != null ? buyerId : "")
                .whereEqualTo("carId", carId != null ? carId : "")
                .whereEqualTo("status", "pending")
                .limit(1)
                .get()
                .addOnSuccessListener(snap -> cb.onResult(!snap.isEmpty()))
                .addOnFailureListener(e -> cb.onResult(false));
    }

    public static void createOrder(@NonNull Map<String, Object> order, @NonNull OnCreated cb) {
        db().collection(COL_ORDERS).add(order)
                .addOnSuccessListener(ref -> cb.onCreated(ref.getId()))
                .addOnFailureListener(e -> cb.onError(e.getMessage()));
    }

    public static void deleteOrder(@Nullable String orderId) {
        if (orderId == null || orderId.isEmpty()) return;
        db().collection(COL_ORDERS).document(orderId).delete();
    }

    public static void updateStatus(@Nullable String orderId, @NonNull String status) {
        if (orderId == null || orderId.isEmpty()) return;
        db().collection(COL_ORDERS).document(orderId).update("status", status);
    }

    /** Cập nhật nhiều field tuỳ ý của đơn. */
    public static void updateFields(@Nullable String orderId, @NonNull Map<String, Object> fields) {
        if (orderId == null || orderId.isEmpty()) return;
        db().collection(COL_ORDERS).document(orderId).update(fields);
    }

    public static void updateFields(@Nullable String orderId, @NonNull Map<String, Object> fields,
                                    @Nullable OnResult cb) {
        if (orderId == null || orderId.isEmpty()) { if (cb != null) cb.onError("Thiếu mã đơn"); return; }
        db().collection(COL_ORDERS).document(orderId).update(fields)
                .addOnSuccessListener(v -> { if (cb != null) cb.onSuccess(); })
                .addOnFailureListener(e -> { if (cb != null) cb.onError(e.getMessage()); });
    }

    public static void loadOrder(@Nullable String orderId, @NonNull OnOrderLoaded cb) {
        if (orderId == null || orderId.isEmpty()) { cb.onError("Thiếu mã đơn"); return; }
        db().collection(COL_ORDERS).document(orderId).get()
                .addOnSuccessListener(cb::onLoaded)
                .addOnFailureListener(e -> cb.onError(e.getMessage()));
    }

    // ── Lắng nghe đơn theo thời gian thực (màn Quản lý) ───────────────────────

    /** Đơn NHẬN ĐƯỢC: theo sellerId. */
    public static ListenerRegistration listenOrdersBySeller(
            String sellerId, @NonNull EventListener<QuerySnapshot> listener) {
        return db().collection(COL_ORDERS).whereEqualTo("sellerId", sellerId)
                .addSnapshotListener(listener);
    }

    /** Đơn MÌNH GỬI ĐI: theo buyerId. */
    public static ListenerRegistration listenOrdersByBuyer(
            String buyerId, @NonNull EventListener<QuerySnapshot> listener) {
        return db().collection(COL_ORDERS).whereEqualTo("buyerId", buyerId)
                .addSnapshotListener(listener);
    }

    /** Fallback: đơn nhận được theo danh sách carId của mình (khi sellerId trống). */
    public static ListenerRegistration listenOrdersByCarIds(
            List<String> carIds, @NonNull EventListener<QuerySnapshot> listener) {
        return db().collection(COL_ORDERS).whereIn("carId", carIds)
                .addSnapshotListener(listener);
    }

    // ── Thông báo liên quan đơn ───────────────────────────────────────────────

    /** Ghi 1 document vào collection "notifications". */
    public static void writeNotification(@Nullable String userId, @Nullable String senderId,
                                         String type, String title, String body,
                                         @Nullable String orderId) {
        if (userId == null || userId.isEmpty()) return;
        Map<String, Object> n = new HashMap<>();
        n.put("userId", userId);
        n.put("senderId", senderId != null ? senderId : "");
        n.put("type", type);
        n.put("title", title);
        n.put("body", body);
        n.put("orderId", orderId);
        n.put("read", false);
        n.put("createdAt", Timestamp.now());
        db().collection(COL_NOTIFS).add(n);
    }
}
