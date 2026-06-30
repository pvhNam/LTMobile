package com.example.doanmb.ui.admin.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.doanmb.data.repository.WalletRepository;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * ViewModel màn quản lý đơn (admin): tải đơn theo tab và xác nhận / hoàn thành
 * (chia tiền qua {@link WalletRepository}) / huỷ (hoàn cọc nếu còn giữ).
 */
public class AdminOrdersViewModel extends ViewModel {

    public static final int TAB_PENDING   = 0;
    public static final int TAB_CONFIRMED = 1;
    public static final int TAB_ALL       = 2;

    private final MutableLiveData<AdminDocList> orders = new MutableLiveData<>(AdminDocList.empty());
    private final MutableLiveData<String> message = new MutableLiveData<>();
    private int currentTab = TAB_PENDING;
    /** Chặn thao tác trùng (double-tap) khi một lệnh ghi/đụng-tiền đang chạy. */
    private boolean processing = false;

    public LiveData<AdminDocList> getOrders() { return orders; }
    public LiveData<String> getMessage() { return message; }
    public int getCurrentTab() { return currentTab; }

    private static FirebaseFirestore db() { return FirebaseFirestore.getInstance(); }

    public void setTab(int tab) {
        currentTab = tab;
        load();
    }

    public void load() {
        com.google.firebase.firestore.Query query = db().collection("orders");
        if (currentTab == TAB_PENDING) {
            query = query.whereEqualTo("status", "pending");
        } else if (currentTab == TAB_CONFIRMED) {
            query = query.whereEqualTo("status", "confirmed");
        }
        query.get()
                .addOnSuccessListener(snap -> {
                    List<Map<String, Object>> data = new ArrayList<>();
                    List<String> ids = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : snap) {
                        data.add(doc.getData());
                        ids.add(doc.getId());
                    }
                    orders.setValue(new AdminDocList(data, ids));
                })
                .addOnFailureListener(e -> message.setValue("Lỗi tải đơn: " + e.getMessage()));
    }

    public void confirmOrder(String orderId) {
        if (processing) return;
        processing = true;
        Map<String, Object> update = new HashMap<>();
        update.put("status", "confirmed");
        db().collection("orders").document(orderId).update(update)
                .addOnSuccessListener(v -> {
                    processing = false;
                    message.setValue("✅ Đã xác nhận đơn hàng");
                    load();
                })
                .addOnFailureListener(e -> {
                    processing = false;
                    message.setValue("Lỗi: " + e.getMessage());
                });
    }

    public void completeOrder(String orderId) {
        if (processing) return;
        processing = true;
        db().collection("orders").document(orderId).get().addOnSuccessListener(doc -> {
            if (!doc.exists()) { processing = false; return; }
            String depositStatus = doc.getString("depositStatus");
            String sellerId       = doc.getString("sellerId");
            Long   deposit        = doc.getLong("depositAmount");
            String status         = doc.getString("status");

            // Chặn chia tiền lặp: chỉ xử lý đơn đang "confirmed"
            if (!"confirmed".equals(status)) {
                processing = false;
                message.setValue("Đơn không ở trạng thái chờ hoàn thành");
                load();
                return;
            }

            // Đơn không có cọc giữ qua ví -> chỉ đánh dấu hoàn thành
            if (!"held".equals(depositStatus) || sellerId == null || sellerId.isEmpty()
                    || deposit == null || deposit <= 0) {
                markCompleted(orderId, null);
                return;
            }

            WalletRepository.settle(sellerId, deposit, orderId, new WalletRepository.Callback() {
                @Override public void onSuccess() { markCompleted(orderId, "settled"); }
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

    private void markCompleted(String orderId, String newDepositStatus) {
        Map<String, Object> update = new HashMap<>();
        update.put("status", "completed");
        if (newDepositStatus != null) update.put("depositStatus", newDepositStatus);
        db().collection("orders").document(orderId).update(update)
                .addOnSuccessListener(v -> {
                    processing = false;
                    message.setValue("✅ Đơn đã hoàn thành & chia tiền");
                    load();
                })
                .addOnFailureListener(e -> {
                    processing = false;
                    message.setValue("Lỗi cập nhật đơn: " + e.getMessage());
                });
    }

    public void cancelOrder(String orderId) {
        if (processing) return;
        processing = true;
        db().collection("orders").document(orderId).get()
                .addOnSuccessListener(doc -> {
                    if (!doc.exists()) { processing = false; return; }
                    String carId         = doc.getString("carId");
                    String buyerId       = doc.getString("buyerId");
                    String depositStatus = doc.getString("depositStatus");
                    String status        = doc.getString("status");
                    Long   deposit       = doc.getLong("depositAmount");

                    // Chỉ huỷ được đơn đang chờ/đã xác nhận; chặn hoàn cọc lặp
                    if (!"pending".equals(status) && !"confirmed".equals(status)) {
                        processing = false;
                        message.setValue("Đơn này không thể hủy");
                        load();
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
                                load();
                            })
                            .addOnFailureListener(e -> {
                                processing = false;
                                message.setValue("Lỗi hủy đơn: " + e.getMessage());
                            });
                })
                .addOnFailureListener(e -> {
                    processing = false;
                    message.setValue("Lỗi: " + e.getMessage());
                });
    }
}
