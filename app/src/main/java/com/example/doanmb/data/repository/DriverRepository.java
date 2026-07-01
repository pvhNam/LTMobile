package com.example.doanmb.data.repository;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.SetOptions;

import java.util.HashMap;
import java.util.Map;

/**
 * Gom thao tác Firestore của module tài xế: collection "drivers" (trạng thái nhận chuyến,
 * thời lượng online) và các đơn của tài xế trong "orders" (nhận/từ chối/bắt đầu/hoàn thành).
 * View/ViewModel không truy vấn Firestore trực tiếp nữa.
 */
public final class DriverRepository {

    private static final String COL_ORDERS  = "orders";
    private static final String COL_DRIVERS = "drivers";
    private static final String COL_USERS   = "users";
    private static final String COL_NOTIFS  = "notifications";

    private DriverRepository() {}

    private static FirebaseFirestore db() { return FirebaseFirestore.getInstance(); }

    // ── Callbacks ────────────────────────────────────────────────────────────

    public interface OnResult { void onSuccess(); void onError(String message); }

    public interface OnDoc { void onLoaded(DocumentSnapshot doc); void onError(String message); }

    public interface OnSnapshot { void onLoaded(QuerySnapshot snap); void onError(String message); }

    public interface OnUserBrief { void onLoaded(String name, String avatar); }

    // ── Hồ sơ tài xế ──────────────────────────────────────────────────────────

    /** Đọc name/avatar của user (dùng cho header trang tài xế). */
    public static void loadUserBrief(@Nullable String uid, @NonNull OnUserBrief cb) {
        if (uid == null || uid.isEmpty()) { cb.onLoaded(null, null); return; }
        db().collection(COL_USERS).document(uid).get()
                .addOnSuccessListener(doc -> cb.onLoaded(
                        doc != null ? doc.getString("name") : null,
                        doc != null ? doc.getString("avatarUrl") : null))
                .addOnFailureListener(e -> cb.onLoaded(null, null));
    }

    /** Đọc document drivers/{uid} (avgRating, online…). */
    public static void loadDriverDoc(@Nullable String uid, @NonNull OnDoc cb) {
        if (uid == null || uid.isEmpty()) { cb.onError("Thiếu mã tài xế"); return; }
        db().collection(COL_DRIVERS).document(uid).get()
                .addOnSuccessListener(cb::onLoaded)
                .addOnFailureListener(e -> cb.onError(e.getMessage()));
    }

    // ── Trạng thái nhận chuyến + thời lượng online ────────────────────────────

    /**
     * Ghi trạng thái nhận chuyến + cộng dồn thời gian online theo ngày.
     * Bật → mở phiên mới (onlineSince=now); Tắt → cộng dồn phiên vừa rồi.
     */
    public static void saveAvailability(@Nullable String uid, boolean available, @Nullable OnResult cb) {
        if (uid == null || uid.isEmpty()) { if (cb != null) cb.onError("Thiếu mã tài xế"); return; }
        DocumentReference ref = db().collection(COL_DRIVERS).document(uid);
        final String today = todayKey();
        final long now = System.currentTimeMillis();
        db().runTransaction(tr -> {
            DocumentSnapshot doc = tr.get(ref);
            String day    = doc.getString("onlineDay");
            Long   stored = doc.getLong("onlineSecondsToday");
            long   base   = today.equals(day) && stored != null ? stored : 0;
            Timestamp since = doc.getTimestamp("onlineSince");

            Map<String, Object> upd = new HashMap<>();
            upd.put("isAvailable", available);
            upd.put("onlineDay", today);
            if (available) {
                upd.put("onlineSecondsToday", base);
                upd.put("onlineSince", Timestamp.now());
            } else {
                long elapsed = since != null ? (now - since.toDate().getTime()) / 1000 : 0;
                if (elapsed < 0) elapsed = 0;
                upd.put("onlineSecondsToday", base + elapsed);
                upd.put("onlineSince", null);
            }
            tr.set(ref, upd, SetOptions.merge());
            return null;
        }).addOnSuccessListener(x -> { if (cb != null) cb.onSuccess(); })
          .addOnFailureListener(e -> { if (cb != null) cb.onError(e.getMessage()); });
    }

    /** Mở phiên online nếu chưa có phiên nào trong hôm nay. */
    public static void beginOnlineSession(@Nullable String uid) {
        if (uid == null || uid.isEmpty()) return;
        DocumentReference ref = db().collection(COL_DRIVERS).document(uid);
        final String today = todayKey();
        db().runTransaction(tr -> {
            DocumentSnapshot doc = tr.get(ref);
            String day = doc.getString("onlineDay");
            if (doc.getTimestamp("onlineSince") != null && today.equals(day)) {
                return null; // đã có phiên trong hôm nay
            }
            Long stored = doc.getLong("onlineSecondsToday");
            long base = today.equals(day) && stored != null ? stored : 0;
            Map<String, Object> upd = new HashMap<>();
            upd.put("onlineDay", today);
            upd.put("onlineSecondsToday", base);
            upd.put("onlineSince", Timestamp.now());
            tr.set(ref, upd, SetOptions.merge());
            return null;
        });
    }

    private static String todayKey() {
        return new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                .format(new java.util.Date());
    }

    // ── Đơn của tài xế ─────────────────────────────────────────────────────────

    /** Đơn đã hoàn thành (tính doanh thu / số chuyến hôm nay ở ViewModel). */
    public static void loadCompletedOrders(@Nullable String uid, @NonNull OnSnapshot cb) {
        if (uid == null || uid.isEmpty()) { cb.onError("Thiếu mã tài xế"); return; }
        db().collection(COL_ORDERS)
                .whereEqualTo("sellerId", uid)
                .whereEqualTo("status", "completed")
                .get()
                .addOnSuccessListener(cb::onLoaded)
                .addOnFailureListener(e -> cb.onError(e.getMessage()));
    }

    /** Lắng nghe đơn pending theo thời gian thực (card "Chuyến gần nhất"). */
    public static ListenerRegistration listenPendingOrders(
            @NonNull String uid, @NonNull EventListener<QuerySnapshot> listener) {
        return db().collection(COL_ORDERS)
                .whereEqualTo("sellerId", uid)
                .whereEqualTo("status", "pending")
                .addSnapshotListener(listener);
    }

    /** Tất cả đơn của tài xế (phân loại tab ở ViewModel). */
    public static void loadAllDriverOrders(@Nullable String uid, @NonNull OnSnapshot cb) {
        if (uid == null || uid.isEmpty()) { cb.onError("Thiếu mã tài xế"); return; }
        db().collection(COL_ORDERS)
                .whereEqualTo("sellerId", uid)
                .get()
                .addOnSuccessListener(cb::onLoaded)
                .addOnFailureListener(e -> cb.onError(e.getMessage()));
    }

    /**
     * Nhận chuyến — dùng Transaction tránh race condition: nếu đơn không còn "pending"
     * thì abort (lần bấm thứ 2 / thiết bị thứ 2 sẽ thất bại với "Đơn đã được xử lý").
     */
    public static void acceptOrder(@NonNull String orderId, @Nullable String driverName,
                                   @NonNull OnResult cb) {
        DocumentReference ref = db().collection(COL_ORDERS).document(orderId);
        db().runTransaction(tr -> {
            DocumentSnapshot snap = tr.get(ref);
            if (!"pending".equals(snap.getString("status"))) {
                throw new FirebaseFirestoreException(
                        "Đơn đã được xử lý", FirebaseFirestoreException.Code.ABORTED);
            }
            tr.update(ref,
                    "status",     "accepted",
                    "driverName", driverName != null ? driverName : "",
                    "acceptedAt", Timestamp.now());
            return null;
        }).addOnSuccessListener(x -> cb.onSuccess())
          .addOnFailureListener(e -> cb.onError(e.getMessage()));
    }

    /** Từ chối chuyến (không có tranh chấp nên không cần transaction). */
    public static void rejectOrder(@NonNull String orderId, @NonNull OnResult cb) {
        db().collection(COL_ORDERS).document(orderId)
                .update("status", "rejected", "rejectedAt", Timestamp.now())
                .addOnSuccessListener(x -> cb.onSuccess())
                .addOnFailureListener(e -> cb.onError(e.getMessage()));
    }

    /** Bắt đầu chuyến: accepted → in_progress. */
    public static void startOrder(@NonNull String orderId, @NonNull OnResult cb) {
        db().collection(COL_ORDERS).document(orderId)
                .update("status", "in_progress", "startedAt", Timestamp.now())
                .addOnSuccessListener(x -> cb.onSuccess())
                .addOnFailureListener(e -> cb.onError(e.getMessage()));
    }

    /** Hoàn thành chuyến: mở khoá đánh giá (canReview=true, reviewed=false). */
    public static void completeOrder(@NonNull String orderId, @NonNull OnResult cb) {
        db().collection(COL_ORDERS).document(orderId)
                .update(
                        "status",      "completed",
                        "completedAt", Timestamp.now(),
                        "canReview",   true,
                        "reviewed",    false)
                .addOnSuccessListener(x -> cb.onSuccess())
                .addOnFailureListener(e -> cb.onError(e.getMessage()));
    }

    /** Tạo thông báo "Mời bạn đánh giá tài xế" cho khách sau khi hoàn thành chuyến. */
    public static void createReviewNotification(@Nullable String buyerId, @Nullable String driverId,
                                                @NonNull String orderId, @Nullable String carId) {
        if (buyerId == null || buyerId.isEmpty()) return;
        Map<String, Object> notif = new HashMap<>();
        notif.put("userId",          buyerId);   // receiverId = Customer
        notif.put("senderId",        driverId != null ? driverId : "");
        notif.put("orderId",         orderId);
        notif.put("carId",           carId != null ? carId : "");
        notif.put("driverId",        driverId != null ? driverId : "");
        notif.put("type",            "review_driver");
        notif.put("title",           "Tài xế đã hoàn thành chuyến xe!");
        notif.put("body",            "Mời bạn đánh giá tài xế.");
        notif.put("read",            false);
        notif.put("actionCompleted", false);
        notif.put("createdAt",       Timestamp.now());
        db().collection(COL_NOTIFS).add(notif);
    }
}
