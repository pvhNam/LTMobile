package com.example.doanmb.data.repository;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

/**
 * Gom toàn bộ thao tác tiền của app vào một chỗ để dễ kiểm soát và đối soát.
 *
 * Mô hình tiền:
 *  - Mỗi user có field "balance" (VNĐ) trong collection "users".
 *  - Hoa hồng của app cộng dồn vào doc "app_wallet/main".balance.
 *  - Mọi lần tiền dịch chuyển đều ghi 1 document vào collection "transactions"
 *    để admin đối soát.
 *
 * Quy tắc chia tiền (đặt cọc giữ chuyến/xe):
 *  - Khách đặt cọc {@link #DEPOSIT_RATE} (50%) tổng đơn -> giữ trong app.
 *  - Khi hoàn thành: app giữ {@link #COMMISSION_RATE} (15%) tiền cọc làm hoa hồng,
 *    trả phần còn lại (85%) về ví chủ xe / tài xế.
 *  - Nếu huỷ trước khi hoàn thành: hoàn 100% tiền cọc về ví khách.
 */
public final class WalletRepository {

    public static final double DEPOSIT_RATE    = 0.50; // % tổng đơn phải đặt cọc qua ví
    public static final double COMMISSION_RATE = 0.15; // % hoa hồng app lấy trên tiền cọc
    public static final int    LONG_BOOKING_MIN_DAYS = 2; // Từ mấy ngày trở lên thì bắt buộc đặt cọc

    private static final String COL_USERS        = "users";
    private static final String COL_TRANSACTIONS = "transactions";
    private static final String COL_APP_WALLET   = "app_wallet";
    private static final String APP_WALLET_DOC   = "main";

    // Loại giao dịch (ghi vào transactions.type)
    public static final String TYPE_TOPUP      = "topup";      // admin nạp cho user
    public static final String TYPE_HOLD       = "hold";       // giữ cọc (trừ ví khách)
    public static final String TYPE_PAYOUT     = "payout";     // trả tiền về ví tài xế/chủ xe
    public static final String TYPE_COMMISSION = "commission"; // hoa hồng về app_wallet
    public static final String TYPE_REFUND     = "refund";     // hoàn cọc về ví khách

    /** Callback đơn giản cho mọi thao tác ví. */
    public interface Callback {
        void onSuccess();
        void onError(String message);
    }

    private WalletRepository() {}

    private static FirebaseFirestore db() { return FirebaseFirestore.getInstance(); }

    private static DocumentReference appWallet() {
        return db().collection(COL_APP_WALLET).document(APP_WALLET_DOC);
    }

    // ── Tính nhanh (dùng cho UI hiển thị trước) ─────────────────────────────

    /** Đơn thuê từ {@link #LONG_BOOKING_MIN_DAYS} ngày trở lên mới phải đặt cọc qua ví. */
    public static boolean requiresDeposit(int days) { return days >= LONG_BOOKING_MIN_DAYS; }

    public static long deposit(long totalAmount)    { return Math.round(totalAmount * DEPOSIT_RATE); }
    public static long commission(long depositAmount){ return Math.round(depositAmount * COMMISSION_RATE); }
    public static long payout(long depositAmount)    { return depositAmount - commission(depositAmount); }

    // ── Nạp tiền (admin -> user) ────────────────────────────────────────────

    /** Admin cộng tiền thẳng vào ví user (dùng để test). */
    public static void topUp(@NonNull String userId, long amount, @Nullable Callback cb) {
        if (amount <= 0) { fail(cb, "Số tiền nạp phải lớn hơn 0"); return; }

        DocumentReference userRef = db().collection(COL_USERS).document(userId);
        db().runTransaction(tr -> {
            tr.update(userRef, "balance", FieldValue.increment(amount));
            tr.set(db().collection(COL_TRANSACTIONS).document(),
                    log(TYPE_TOPUP, amount, null, userId, null, "Admin nạp tiền"));
            return null;
        }).addOnSuccessListener(v -> ok(cb))
          .addOnFailureListener(e -> fail(cb, e.getMessage()));
    }

    // ── Giữ cọc (trừ ví khách) ──────────────────────────────────────────────

    /**
     * Trừ tiền cọc khỏi ví khách để giữ đơn. Báo lỗi nếu số dư không đủ.
     * Tiền cọc được "giữ" trong app cho tới khi hoàn thành hoặc huỷ.
     */
    public static void holdDeposit(@NonNull String customerId, long depositAmount,
                                   @Nullable String orderId, @Nullable Callback cb) {
        if (depositAmount <= 0) { fail(cb, "Tiền cọc không hợp lệ"); return; }

        DocumentReference userRef = db().collection(COL_USERS).document(customerId);
        db().runTransaction(tr -> {
            DocumentSnapshot snap = tr.get(userRef);
            long balance = readBalance(snap);
            if (balance < depositAmount) {
                throw new IllegalStateException("Số dư ví không đủ để đặt cọc");
            }
            tr.update(userRef, "balance", FieldValue.increment(-depositAmount));
            tr.set(db().collection(COL_TRANSACTIONS).document(),
                    log(TYPE_HOLD, depositAmount, customerId, null, orderId, "Giữ cọc đơn"));
            return null;
        }).addOnSuccessListener(v -> ok(cb))
          .addOnFailureListener(e -> fail(cb, e.getMessage()));
    }

    // ── Chia tiền khi hoàn thành ─────────────────────────────────────────────

    /**
     * Khi đơn hoàn thành: lấy hoa hồng cho app, trả phần còn lại của tiền cọc
     * về ví tài xế / chủ xe.
     */
    public static void settle(@NonNull String driverId, long depositAmount,
                              @Nullable String orderId, @Nullable Callback cb) {
        if (depositAmount <= 0) { fail(cb, "Tiền cọc không hợp lệ"); return; }

        long commission = commission(depositAmount);
        long payout     = depositAmount - commission;

        DocumentReference driverRef = db().collection(COL_USERS).document(driverId);
        db().runTransaction(tr -> {
            tr.update(driverRef, "balance", FieldValue.increment(payout));
            tr.set(appWallet(), single("balance", FieldValue.increment(commission)),
                    com.google.firebase.firestore.SetOptions.merge());
            tr.set(db().collection(COL_TRANSACTIONS).document(),
                    log(TYPE_PAYOUT, payout, null, driverId, orderId, "Trả tiền hoàn thành đơn"));
            tr.set(db().collection(COL_TRANSACTIONS).document(),
                    log(TYPE_COMMISSION, commission, null, null, orderId, "Hoa hồng app"));
            return null;
        }).addOnSuccessListener(v -> ok(cb))
          .addOnFailureListener(e -> fail(cb, e.getMessage()));
    }

    // ── Hoàn cọc (huỷ đơn) ───────────────────────────────────────────────────

    /** Hoàn 100% tiền cọc về ví khách khi đơn bị huỷ trước lúc hoàn thành. */
    public static void refund(@NonNull String customerId, long depositAmount,
                              @Nullable String orderId, @Nullable Callback cb) {
        if (depositAmount <= 0) { ok(cb); return; }

        DocumentReference userRef = db().collection(COL_USERS).document(customerId);
        db().runTransaction(tr -> {
            tr.update(userRef, "balance", FieldValue.increment(depositAmount));
            tr.set(db().collection(COL_TRANSACTIONS).document(),
                    log(TYPE_REFUND, depositAmount, null, customerId, orderId, "Hoàn cọc huỷ đơn"));
            return null;
        }).addOnSuccessListener(v -> ok(cb))
          .addOnFailureListener(e -> fail(cb, e.getMessage()));
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private static long readBalance(DocumentSnapshot snap) {
        Double b = snap.getDouble("balance");
        return b != null ? Math.round(b) : 0L;
    }

    private static Map<String, Object> log(String type, long amount, String fromUserId,
                                           String toUserId, String orderId, String note) {
        Map<String, Object> m = new HashMap<>();
        m.put("type", type);
        m.put("amount", amount);
        m.put("fromUserId", fromUserId);
        m.put("toUserId", toUserId);
        m.put("orderId", orderId);
        m.put("note", note);
        m.put("createdAt", Timestamp.now());
        return m;
    }

    private static Map<String, Object> single(String key, Object value) {
        Map<String, Object> m = new HashMap<>();
        m.put(key, value);
        return m;
    }

    private static void ok(@Nullable Callback cb)              { if (cb != null) cb.onSuccess(); }
    private static void fail(@Nullable Callback cb, String e)  { if (cb != null) cb.onError(e != null ? e : "Lỗi không xác định"); }
}
