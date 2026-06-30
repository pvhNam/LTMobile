package com.example.doanmb.ui.admin.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.doanmb.data.repository.WalletRepository;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

/**
 * ViewModel màn admin xem chi tiết 1 đơn hàng: tải đơn (kèm phân giải tên khách
 * & chủ xe), xác nhận / hoàn thành (chia tiền) / huỷ (hoàn cọc).
 */
public class AdminOrderDetailViewModel extends ViewModel {

    private final MutableLiveData<DocumentSnapshot> order = new MutableLiveData<>();
    private final MutableLiveData<String> buyerName = new MutableLiveData<>();
    private final MutableLiveData<String> sellerName = new MutableLiveData<>();
    private final MutableLiveData<String> message = new MutableLiveData<>();
    private final MutableLiveData<Boolean> finishEvent = new MutableLiveData<>();

    private String orderId;
    /** Chặn thao tác trùng (double-tap) khi một lệnh ghi/đụng-tiền đang chạy. */
    private boolean processing = false;

    public LiveData<DocumentSnapshot> getOrder() { return order; }
    public LiveData<String> getBuyerName() { return buyerName; }
    public LiveData<String> getSellerName() { return sellerName; }
    public LiveData<String> getMessage() { return message; }
    public LiveData<Boolean> getFinishEvent() { return finishEvent; }

    private static FirebaseFirestore db() { return FirebaseFirestore.getInstance(); }

    public void start(String orderId) {
        if (this.orderId != null) return; // đã tải
        this.orderId = orderId;
        loadOrder();
    }

    private void loadOrder() {
        db().collection("orders").document(orderId).get()
                .addOnSuccessListener(doc -> {
                    if (!doc.exists()) {
                        message.setValue("Đơn hàng không còn tồn tại");
                        finishEvent.setValue(true);
                        return;
                    }
                    order.setValue(doc);
                    // Khách: ưu tiên tên trong đơn, thiếu thì tra theo buyerId.
                    String buyer = firstNonEmpty(doc.getString("renterName"), doc.getString("buyerName"));
                    resolveName(buyer, doc.getString("buyerId"), buyerName);
                    // Chủ xe: ưu tiên sellerName, thiếu thì tra theo sellerId.
                    resolveName(doc.getString("sellerName"), doc.getString("sellerId"), sellerName);
                })
                .addOnFailureListener(e -> {
                    message.setValue("Lỗi tải đơn hàng: " + e.getMessage());
                    finishEvent.setValue(true);
                });
    }

    /** Phân giải tên hiển thị: dùng tên có sẵn, nếu thiếu thì tra trong "users" theo id. */
    private void resolveName(String name, String userId, MutableLiveData<String> out) {
        if (name != null && !name.isEmpty()) { out.setValue(name); return; }
        if (userId == null || userId.isEmpty()) { out.setValue("--"); return; }
        db().collection("users").document(userId).get()
                .addOnSuccessListener(u -> {
                    String fetched = u.exists() ? u.getString("name") : null;
                    out.setValue(fetched != null && !fetched.isEmpty() ? fetched : "Ẩn danh");
                })
                .addOnFailureListener(e -> out.setValue("Ẩn danh"));
    }

    public void confirmOrder() {
        if (processing) return;
        processing = true;
        db().collection("orders").document(orderId).update("status", "confirmed")
                .addOnSuccessListener(v -> {
                    processing = false;
                    message.setValue("✅ Đã xác nhận đơn hàng");
                    loadOrder();
                })
                .addOnFailureListener(e -> {
                    processing = false;
                    message.setValue("Lỗi: " + e.getMessage());
                });
    }

    public void completeOrder() {
        if (processing) return;
        processing = true;
        db().collection("orders").document(orderId).get().addOnSuccessListener(doc -> {
            if (!doc.exists()) { processing = false; return; }
            String depositStatus = doc.getString("depositStatus");
            String sellerId = doc.getString("sellerId");
            Long deposit = doc.getLong("depositAmount");
            String status = doc.getString("status");

            // Chặn chia tiền lặp: chỉ xử lý đơn đang "confirmed"
            if (!"confirmed".equals(status)) {
                processing = false;
                message.setValue("Đơn không ở trạng thái chờ hoàn thành");
                loadOrder();
                return;
            }

            // Đơn không có cọc giữ qua ví -> chỉ đánh dấu hoàn thành
            if (!"held".equals(depositStatus) || sellerId == null || sellerId.isEmpty()
                    || deposit == null || deposit <= 0) {
                markCompleted(null);
                return;
            }

            WalletRepository.settle(sellerId, deposit, orderId, new WalletRepository.Callback() {
                @Override public void onSuccess() { markCompleted("settled"); }
                @Override public void onError(String msg) {
                    processing = false;
                    message.setValue("Lỗi chia tiền: " + msg);
                }
            });
        }).addOnFailureListener(e -> {
            processing = false;
            message.setValue("Lỗi: " + e.getMessage());
        });
    }

    private void markCompleted(String newDepositStatus) {
        Map<String, Object> update = new HashMap<>();
        update.put("status", "completed");
        if (newDepositStatus != null) update.put("depositStatus", newDepositStatus);
        db().collection("orders").document(orderId).update(update)
                .addOnSuccessListener(v -> {
                    processing = false;
                    message.setValue("✅ Đơn đã hoàn thành & chia tiền");
                    loadOrder();
                })
                .addOnFailureListener(e -> {
                    processing = false;
                    message.setValue("Lỗi cập nhật đơn: " + e.getMessage());
                });
    }

    public void cancelOrder() {
        if (processing) return;
        processing = true;
        db().collection("orders").document(orderId).get().addOnSuccessListener(doc -> {
            if (!doc.exists()) { processing = false; return; }
            String carId = doc.getString("carId");
            String buyerId = doc.getString("buyerId");
            String depositStatus = doc.getString("depositStatus");
            String status = doc.getString("status");
            Long deposit = doc.getLong("depositAmount");

            // Chỉ huỷ được đơn đang chờ/đã xác nhận; chặn hoàn cọc lặp
            if (!"pending".equals(status) && !"confirmed".equals(status)) {
                processing = false;
                message.setValue("Đơn này không thể hủy");
                loadOrder();
                return;
            }

            Map<String, Object> orderUpdate = new HashMap<>();
            orderUpdate.put("status", "cancelled");

            if (carId != null && !carId.isEmpty()) {
                Map<String, Object> carUpdate = new HashMap<>();
                carUpdate.put("status", "active");
                db().collection("cars").document(carId).update(carUpdate);
            }

            // Còn giữ cọc -> hoàn lại 100% vào ví khách
            final boolean refunded = "held".equals(depositStatus)
                    && buyerId != null && deposit != null && deposit > 0;
            if (refunded) {
                orderUpdate.put("depositStatus", "refunded");
                WalletRepository.refund(buyerId, deposit, orderId, null);
            }

            db().collection("orders").document(orderId).update(orderUpdate)
                    .addOnSuccessListener(v -> {
                        processing = false;
                        message.setValue("Đã hủy đơn hàng" + (refunded ? " & hoàn cọc cho khách" : ""));
                        loadOrder();
                    })
                    .addOnFailureListener(e -> {
                        processing = false;
                        message.setValue("Lỗi hủy đơn: " + e.getMessage());
                    });
        }).addOnFailureListener(e -> {
            processing = false;
            message.setValue("Lỗi: " + e.getMessage());
        });
    }

    private static String firstNonEmpty(String a, String b) {
        return (a != null && !a.isEmpty()) ? a : b;
    }
}
