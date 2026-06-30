package com.example.doanmb.data.repository;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.doanmb.data.model.Review;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Gom toàn bộ thao tác Firestore với collection "reviews" + đồng bộ điểm tài xế
 * sang "drivers"/"cars". View/ViewModel không truy vấn Firestore trực tiếp nữa.
 *
 * Lưu ý: các truy vấn lấy review theo driverId CỐ TÌNH không dùng orderBy để tránh
 * phải tạo composite index trên Firestore — sort theo createdAt giảm dần ở client.
 */
public final class ReviewRepository {

    private static final String COL_REVIEWS = "reviews";
    private static final String COL_ORDERS  = "orders";
    private static final String COL_DRIVERS = "drivers";
    private static final String COL_CARS    = "cars";
    private static final String COL_USERS   = "users";
    private static final String COL_NOTIFS  = "notifications";

    private ReviewRepository() {}

    private static FirebaseFirestore db() { return FirebaseFirestore.getInstance(); }

    // ── Callbacks ────────────────────────────────────────────────────────────

    /** Kết quả gửi đánh giá: điểm trung bình + số lượt mới (đã tính trong transaction). */
    public interface OnReviewSubmitted {
        void onSuccess(double newAvg, long newCount);
        void onError(String message);
    }

    public interface OnUserBrief { void onLoaded(String name, String avatar); }

    public interface OnReviews { void onLoaded(List<Review> reviews); }

    public interface OnRating { void onLoaded(double avgRating, long reviewCount); }

    // ── Đọc thông tin người đánh giá ──────────────────────────────────────────

    /** Đọc name/avatar của user (người để lại đánh giá). */
    public static void loadUserBrief(@Nullable String uid, @NonNull OnUserBrief cb) {
        if (uid == null || uid.isEmpty()) { cb.onLoaded(null, null); return; }
        db().collection(COL_USERS).document(uid).get()
                .addOnSuccessListener(doc -> cb.onLoaded(
                        doc != null ? doc.getString("name") : null,
                        doc != null ? doc.getString("avatarUrl") : null))
                .addOnFailureListener(e -> cb.onLoaded(null, null));
    }

    // ── Gửi đánh giá (1 transaction atomic) ───────────────────────────────────

    /**
     * Ghi review mới + cập nhật điểm tài xế theo công thức tích luỹ trong 1 transaction.
     * newAvg = (currentAvg * currentCount + rating) / (currentCount + 1)
     */
    public static void submitReview(@NonNull Map<String, Object> reviewData,
                                    @NonNull String orderId,
                                    @NonNull String driverId,
                                    @Nullable String carId,
                                    @Nullable String notificationId,
                                    float rating,
                                    @NonNull OnReviewSubmitted cb) {
        FirebaseFirestore db = db();
        DocumentReference reviewRef = db.collection(COL_REVIEWS).document();
        DocumentReference orderRef  = db.collection(COL_ORDERS).document(orderId);
        DocumentReference driverRef = db.collection(COL_DRIVERS).document(driverId);

        // Transaction chỉ chạy trên lambda nên không return giá trị ra ngoài trực tiếp;
        // dùng mảng 1 phần tử để "mượn" biến ra ngoài callback addOnSuccessListener.
        double[] computedAvg   = {0};
        long[]   computedCount = {0};

        db.runTransaction(tr -> {
            DocumentSnapshot driverSnap = tr.get(driverRef);
            double currentAvg   = driverSnap.contains("avgRating")
                    && driverSnap.getDouble("avgRating") != null ? driverSnap.getDouble("avgRating") : 0.0;
            long   currentCount = driverSnap.contains("reviewCount")
                    && driverSnap.getLong("reviewCount") != null ? driverSnap.getLong("reviewCount") : 0L;

            long   newCount = currentCount + 1;
            double newAvg   = ((currentAvg * currentCount) + rating) / newCount;

            computedAvg[0]   = newAvg;
            computedCount[0] = newCount;

            tr.set(reviewRef, reviewData);
            tr.update(orderRef, "reviewed", true);
            tr.update(driverRef, "avgRating", newAvg, "reviewCount", newCount);

            if (carId != null && !carId.isEmpty()) {
                tr.update(db.collection(COL_CARS).document(carId),
                        "driverRating", newAvg, "driverReviewCount", newCount);
            }

            if (notificationId != null && !notificationId.isEmpty()) {
                tr.update(db.collection(COL_NOTIFS).document(notificationId),
                        "actionCompleted", true, "read", true);
            }
            return null;
        }).addOnSuccessListener(x -> cb.onSuccess(computedAvg[0], computedCount[0]))
          .addOnFailureListener(e -> cb.onError(e.getMessage()));
    }

    /**
     * Đồng bộ phụ: 1 tài xế có thể đăng nhiều xe → cập nhật driverRating/driverReviewCount
     * cho TOÀN BỘ xe có sellerId == driverId để điểm hiển thị nhất quán mọi nơi.
     */
    public static void syncAllDriverCars(@Nullable String driverId, double avg, long count) {
        if (driverId == null || driverId.isEmpty()) return;
        db().collection(COL_CARS).whereEqualTo("sellerId", driverId).get()
                .addOnSuccessListener(carSnaps -> {
                    for (QueryDocumentSnapshot carDoc : carSnaps) {
                        db().collection(COL_CARS).document(carDoc.getId())
                                .update("driverRating", avg, "driverReviewCount", count);
                    }
                });
    }

    // ── Đọc đánh giá / điểm tài xế (trang xem toàn bộ) ────────────────────────

    /** Điểm trung bình + tổng lượt của tài xế (đọc từ drivers/{id}). */
    public static void loadDriverStats(@Nullable String driverId, @NonNull OnRating cb) {
        if (driverId == null || driverId.isEmpty()) { cb.onLoaded(0, 0); return; }
        db().collection(COL_DRIVERS).document(driverId).get()
                .addOnSuccessListener(doc -> {
                    if (doc == null || !doc.exists()) { cb.onLoaded(0, 0); return; }
                    Double avg = doc.getDouble("avgRating");
                    Long count = doc.getLong("reviewCount");
                    cb.onLoaded(avg != null ? avg : 0, count != null ? count : 0);
                })
                .addOnFailureListener(e -> cb.onLoaded(0, 0));
    }

    /** Toàn bộ review của 1 tài xế — sort createdAt desc ở client (tránh composite index). */
    public static void loadReviews(@Nullable String driverId, @NonNull OnReviews cb) {
        if (driverId == null || driverId.isEmpty()) { cb.onLoaded(new ArrayList<>()); return; }
        db().collection(COL_REVIEWS).whereEqualTo("driverId", driverId).get()
                .addOnSuccessListener(snap -> {
                    List<Review> list = new ArrayList<>();
                    for (QueryDocumentSnapshot d : snap) {
                        Review r = d.toObject(Review.class);
                        r.setReviewId(d.getId());
                        list.add(r);
                    }
                    list.sort((a, b) -> {
                        if (a.getCreatedAt() == null && b.getCreatedAt() == null) return 0;
                        if (a.getCreatedAt() == null) return 1;
                        if (b.getCreatedAt() == null) return -1;
                        return b.getCreatedAt().compareTo(a.getCreatedAt());
                    });
                    cb.onLoaded(list);
                })
                .addOnFailureListener(e -> {
                    android.util.Log.e("ReviewRepository", "loadReviews lỗi: " + e.getMessage());
                    cb.onLoaded(new ArrayList<>());
                });
    }
}
