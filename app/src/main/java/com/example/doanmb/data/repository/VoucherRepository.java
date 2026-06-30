package com.example.doanmb.data.repository;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.doanmb.data.model.UserVoucher;
import com.example.doanmb.data.model.Voucher;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;

/**
 * Gom toàn bộ thao tác liên quan tới "Quà tặng": điểm thưởng, danh mục voucher
 * có thể đổi và ví voucher của từng người dùng.
 *
 * Mô hình dữ liệu:
 *  - Mỗi user có field "points" (điểm thưởng) trong collection "users".
 *  - Danh mục quà tặng nằm trong collection "vouchers" (admin quản lý).
 *  - Khi user đổi quà, một bản ghi được tạo trong collection "user_vouchers"
 *    (bản sao thông tin giảm giá, độc lập với catalog gốc) — đây chính là
 *    "ví voucher" của user.
 *  - Việc đổi điểm + tạo voucher chạy trong 1 Firestore transaction để tránh
 *    đổi được nhiều lần khi điểm không đủ (race condition).
 *
 * Cách tích điểm: mỗi đơn thuê xe thanh toán thành công cộng {@link #POINTS_PER_ORDER} điểm.
 */
public final class VoucherRepository {

    private static final String COL_USERS         = "users";
    private static final String COL_VOUCHERS      = "vouchers";
    private static final String COL_USER_VOUCHERS = "user_vouchers";

    /** Số điểm cộng thêm mỗi khi một đơn thuê xe được thanh toán xong. */
    public static final long POINTS_PER_ORDER = 10;

    /** Callback đơn giản cho thao tác không trả dữ liệu. */
    public interface Callback {
        void onSuccess();
        void onError(String message);
    }

    /** Callback trả về số điểm thưởng hiện tại. */
    public interface PointsCallback {
        void onLoaded(long points);
        void onError(String message);
    }

    /** Callback trả về danh sách voucher có thể đổi (catalog). */
    public interface VoucherListCallback {
        void onLoaded(List<Voucher> vouchers);
        void onError(String message);
    }

    /** Callback trả về danh sách voucher trong ví của user. */
    public interface UserVoucherListCallback {
        void onLoaded(List<UserVoucher> vouchers);
        void onError(String message);
    }

    private VoucherRepository() {}

    private static FirebaseFirestore db() { return FirebaseFirestore.getInstance(); }

    // ── Điểm thưởng ──────────────────────────────────────────────────────────

    /** Lấy số điểm thưởng hiện tại của user (field "points" trong users/{uid}). */
    public static void loadPoints(@Nullable String userId, @NonNull PointsCallback cb) {
        if (userId == null || userId.isEmpty()) { cb.onError("Thiếu thông tin người dùng"); return; }
        db().collection(COL_USERS).document(userId).get()
                .addOnSuccessListener(doc -> cb.onLoaded(readPoints(doc)))
                .addOnFailureListener(e -> cb.onError(e.getMessage()));
    }

    /** Cộng điểm thưởng cho user, dùng sau khi một đơn thuê xe thanh toán thành công. */
    public static void addPoints(@NonNull String userId, long amount, @Nullable Callback cb) {
        if (amount <= 0) { ok(cb); return; }
        db().collection(COL_USERS).document(userId)
                .update("points", FieldValue.increment(amount))
                .addOnSuccessListener(v -> ok(cb))
                .addOnFailureListener(e -> fail(cb, e.getMessage()));
    }

    // ── Danh mục voucher (catalog) ───────────────────────────────────────────

    /** Tải danh sách voucher còn mở để đổi, sắp theo số điểm cần từ thấp tới cao. */
    public static void loadCatalog(@NonNull VoucherListCallback cb) {
        db().collection(COL_VOUCHERS)
                .whereEqualTo("active", true)
                .get()
                .addOnSuccessListener(snap -> {
                    List<Voucher> list = new ArrayList<>();
                    for (QueryDocumentSnapshot d : snap) {
                        Voucher v = d.toObject(Voucher.class);
                        v.setId(d.getId());
                        if (v.isRedeemable()) list.add(v);
                    }
                    Collections.sort(list, (a, b) -> Integer.compare(a.getPointsCost(), b.getPointsCost()));
                    cb.onLoaded(list);
                })
                .addOnFailureListener(e -> cb.onError(e.getMessage()));
    }

    // ── Quản lý catalog (admin) ──────────────────────────────────────────────

    /** Tải TẤT CẢ voucher trong catalog (cả đang tắt), mới-rẻ điểm lên trước. Dùng cho màn admin. */
    public static void loadAllVouchers(@NonNull VoucherListCallback cb) {
        db().collection(COL_VOUCHERS)
                .get()
                .addOnSuccessListener(snap -> {
                    List<Voucher> list = new ArrayList<>();
                    for (QueryDocumentSnapshot d : snap) {
                        Voucher v = d.toObject(Voucher.class);
                        v.setId(d.getId());
                        list.add(v);
                    }
                    // Còn mở lên trước, trong cùng nhóm thì điểm thấp lên trước.
                    Collections.sort(list, (a, b) -> {
                        if (a.isActive() != b.isActive()) return a.isActive() ? -1 : 1;
                        return Integer.compare(a.getPointsCost(), b.getPointsCost());
                    });
                    cb.onLoaded(list);
                })
                .addOnFailureListener(e -> cb.onError(e.getMessage()));
    }

    /**
     * Tạo mới (khi {@code voucher.getId()} null/rỗng) hoặc cập nhật một voucher trong catalog.
     * Chỉ ghi các field thuộc về catalog, không đụng tới ví voucher của user đã đổi.
     */
    public static void saveVoucher(@NonNull Voucher voucher, @Nullable Callback cb) {
        java.util.Map<String, Object> m = new java.util.HashMap<>();
        m.put("title", voucher.getTitle());
        m.put("description", voucher.getDescription());
        m.put("discountType", voucher.getDiscountType());
        m.put("discountValue", voucher.getDiscountValue());
        m.put("maxDiscount", voucher.getMaxDiscount());
        m.put("minOrderAmount", voucher.getMinOrderAmount());
        m.put("pointsCost", voucher.getPointsCost());
        m.put("quantity", voucher.getQuantity());
        m.put("active", voucher.isActive());
        m.put("validDays", voucher.getValidDays());

        boolean isNew = voucher.getId() == null || voucher.getId().isEmpty();
        DocumentReference ref = isNew
                ? db().collection(COL_VOUCHERS).document()
                : db().collection(COL_VOUCHERS).document(voucher.getId());
        ref.set(m, com.google.firebase.firestore.SetOptions.merge())
                .addOnSuccessListener(v -> ok(cb))
                .addOnFailureListener(e -> fail(cb, e.getMessage()));
    }

    /** Bật/tắt một voucher trong catalog (admin tạm ngừng cho đổi mà không xoá). */
    public static void setVoucherActive(@NonNull String voucherId, boolean active, @Nullable Callback cb) {
        db().collection(COL_VOUCHERS).document(voucherId)
                .update("active", active)
                .addOnSuccessListener(v -> ok(cb))
                .addOnFailureListener(e -> fail(cb, e.getMessage()));
    }

    /**
     * Xoá hẳn một voucher khỏi catalog. Không ảnh hưởng các {@link UserVoucher} đã đổi
     * (chúng giữ bản sao thông tin giảm giá độc lập).
     */
    public static void deleteVoucher(@NonNull String voucherId, @Nullable Callback cb) {
        db().collection(COL_VOUCHERS).document(voucherId)
                .delete()
                .addOnSuccessListener(v -> ok(cb))
                .addOnFailureListener(e -> fail(cb, e.getMessage()));
    }

    // ── Ví voucher của user ──────────────────────────────────────────────────

    /** Tải toàn bộ voucher user đã đổi, mới nhất lên đầu. */
    public static void loadMyVouchers(@Nullable String userId, @NonNull UserVoucherListCallback cb) {
        if (userId == null || userId.isEmpty()) { cb.onError("Thiếu thông tin người dùng"); return; }
        db().collection(COL_USER_VOUCHERS)
                .whereEqualTo("userId", userId)
                .get()
                .addOnSuccessListener(snap -> {
                    List<UserVoucher> list = new ArrayList<>();
                    for (QueryDocumentSnapshot d : snap) {
                        UserVoucher uv = d.toObject(UserVoucher.class);
                        uv.setId(d.getId());
                        list.add(uv);
                    }
                    Collections.sort(list, (a, b) -> {
                        long ta = a.getRedeemedAt() != null ? a.getRedeemedAt().toDate().getTime() : 0L;
                        long tb = b.getRedeemedAt() != null ? b.getRedeemedAt().toDate().getTime() : 0L;
                        return Long.compare(tb, ta);
                    });
                    cb.onLoaded(list);
                })
                .addOnFailureListener(e -> cb.onError(e.getMessage()));
    }

    /**
     * Đổi một voucher trong catalog bằng điểm thưởng: trừ điểm của user, trừ
     * (nếu có giới hạn) số lượng kho và tạo voucher mới trong ví user. Toàn bộ
     * thực hiện trong 1 transaction để tránh đổi vượt điểm khi bấm nhiều lần.
     */
    public static void redeem(@NonNull String userId, @NonNull Voucher voucher, @Nullable Callback cb) {
        if (voucher.getId() == null || voucher.getId().isEmpty()) {
            fail(cb, "Quà tặng không hợp lệ");
            return;
        }
        DocumentReference userRef    = db().collection(COL_USERS).document(userId);
        DocumentReference voucherRef = db().collection(COL_VOUCHERS).document(voucher.getId());

        db().runTransaction(tr -> {
                    DocumentSnapshot userSnap    = tr.get(userRef);
                    DocumentSnapshot voucherSnap = tr.get(voucherRef);

                    long currentPoints = readPoints(userSnap);
                    if (currentPoints < voucher.getPointsCost()) {
                        throw new IllegalStateException("Bạn chưa đủ điểm để đổi quà này");
                    }

                    Boolean active = voucherSnap.getBoolean("active");
                    Long quantity  = voucherSnap.getLong("quantity");
                    if (active != null && !active) {
                        throw new IllegalStateException("Quà tặng này đã đóng, không thể đổi");
                    }
                    if (quantity != null && quantity == 0) {
                        throw new IllegalStateException("Quà tặng này đã hết lượt đổi");
                    }

                    tr.update(userRef, "points", FieldValue.increment(-voucher.getPointsCost()));
                    if (quantity != null && quantity > 0) {
                        tr.update(voucherRef, "quantity", FieldValue.increment(-1));
                    }
                    tr.set(db().collection(COL_USER_VOUCHERS).document(), buildUserVoucher(userId, voucher));
                    return null;
                }).addOnSuccessListener(v -> ok(cb))
                .addOnFailureListener(e -> fail(cb, e.getMessage()));
    }

    /**
     * Đánh dấu một voucher trong ví đã được dùng cho đơn {@code orderId}.
     * Gọi khi khách xác nhận áp dụng voucher lúc thanh toán hóa đơn.
     */
    public static void markUsed(@NonNull String userVoucherId, @NonNull String orderId, @Nullable Callback cb) {
        java.util.Map<String, Object> up = new java.util.HashMap<>();
        up.put("used", true);
        up.put("orderId", orderId);
        up.put("usedAt", Timestamp.now());
        db().collection(COL_USER_VOUCHERS).document(userVoucherId)
                .update(up)
                .addOnSuccessListener(v -> ok(cb))
                .addOnFailureListener(e -> fail(cb, e.getMessage()));
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private static java.util.Map<String, Object> buildUserVoucher(String userId, Voucher v) {
        java.util.Map<String, Object> m = new java.util.HashMap<>();
        m.put("userId", userId);
        m.put("voucherId", v.getId());
        m.put("title", v.getTitle());
        m.put("description", v.getDescription());
        m.put("discountType", v.getDiscountType());
        m.put("discountValue", v.getDiscountValue());
        m.put("maxDiscount", v.getMaxDiscount());
        m.put("minOrderAmount", v.getMinOrderAmount());
        m.put("used", false);
        m.put("orderId", null);
        m.put("redeemedAt", Timestamp.now());
        m.put("usedAt", null);
        if (v.getValidDays() > 0) {
            Calendar cal = Calendar.getInstance();
            cal.add(Calendar.DAY_OF_YEAR, v.getValidDays());
            m.put("expiresAt", new Timestamp(cal.getTime()));
        } else {
            m.put("expiresAt", null);
        }
        return m;
    }

    private static long readPoints(DocumentSnapshot snap) {
        Long p = snap.getLong("points");
        return p != null ? p : 0L;
    }

    private static void ok(@Nullable Callback cb)             { if (cb != null) cb.onSuccess(); }
    private static void fail(@Nullable Callback cb, String e) { if (cb != null) cb.onError(e != null ? e : "Lỗi không xác định"); }
}