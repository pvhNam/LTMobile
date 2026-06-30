package com.example.doanmb.ui.driver.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.doanmb.data.repository.DriverRepository;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;
import java.util.List;

/**
 * ViewModel màn "Chuyến" của tài xế: tải 1 lần toàn bộ đơn (trừ pending), phân loại
 * accepted/running/history tại client; đổi tab chỉ đổ danh sách tương ứng (không gọi lại Firestore).
 */
public class DriverTripsViewModel extends ViewModel {

    /** POJO đơn giản map trực tiếp từ Firestore (tách rời class Trip cũ). */
    public static class OrderItem {
        public String orderId, status, carName, carPrice,
                renterName, renterPhone, note, type, carId, buyerId;
        public Timestamp createdAt;

        static OrderItem from(QueryDocumentSnapshot d) {
            OrderItem o  = new OrderItem();
            o.orderId    = d.getId();
            o.status     = d.getString("status");
            o.carName    = d.getString("carName");
            o.carPrice   = d.getString("carPrice");
            o.renterName = d.getString("renterName");
            o.renterPhone= d.getString("renterPhone");
            o.note       = d.getString("note");
            o.type       = d.getString("type");
            o.carId      = d.getString("carId");
            o.buyerId    = d.getString("buyerId");
            o.createdAt  = d.getTimestamp("createdAt");
            return o;
        }
    }

    private final MutableLiveData<String> driverName = new MutableLiveData<>();
    private final MutableLiveData<String> avatarUrl  = new MutableLiveData<>();
    private final MutableLiveData<List<OrderItem>> shown = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<String>  message      = new MutableLiveData<>();
    private final MutableLiveData<Integer> selectTabEvent = new MutableLiveData<>();

    public LiveData<String> getDriverName() { return driverName; }
    public LiveData<String> getAvatarUrl()  { return avatarUrl; }
    public LiveData<List<OrderItem>> getShown() { return shown; }
    public LiveData<String>  getMessage()      { return message; }
    public LiveData<Integer> getSelectTabEvent() { return selectTabEvent; }

    private final String uid;
    private int currentTab = 0;

    private final List<OrderItem> accepted = new ArrayList<>();
    private final List<OrderItem> running  = new ArrayList<>();
    private final List<OrderItem> history  = new ArrayList<>();

    public DriverTripsViewModel() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        uid = user != null ? user.getUid() : "";
    }

    /** Tải header (tên/avatar) + toàn bộ đơn. */
    public void loadAll() {
        if (uid.isEmpty()) return;
        DriverRepository.loadUserBrief(uid, (name, avatar) -> {
            driverName.setValue(name);
            avatarUrl.setValue(avatar);
        });
        loadOrders();
    }

    public void loadOrders() {
        DriverRepository.loadAllDriverOrders(uid, new DriverRepository.OnSnapshot() {
            @Override public void onLoaded(QuerySnapshot snap) {
                accepted.clear(); running.clear(); history.clear();
                for (QueryDocumentSnapshot d : snap) {
                    String st = d.getString("status");
                    if (st == null || "pending".equals(st)) continue; // pending → Driver Home
                    OrderItem o = OrderItem.from(d);
                    switch (st) {
                        case "accepted":    accepted.add(o); break;
                        case "in_progress": running.add(o);  break;
                        case "completed":
                        case "rejected":
                        case "cancelled":   history.add(o);  break;
                    }
                }
                renderTab();
            }
            @Override public void onError(String msg) { }
        });
    }

    public void setTab(int tab) { currentTab = tab; renderTab(); }

    private void renderTab() {
        List<OrderItem> list = new ArrayList<>();
        switch (currentTab) {
            case 0: list.addAll(accepted); break;
            case 1: list.addAll(running);  break;
            case 2: list.addAll(history);  break;
            default: /* Đặt trước: chưa có dữ liệu */ break;
        }
        shown.setValue(list);
    }

    public void startOrder(OrderItem o) {
        DriverRepository.startOrder(o.orderId, new DriverRepository.OnResult() {
            @Override public void onSuccess() {
                message.setValue("🚗 Đã bắt đầu chuyến!");
                loadOrders();
                selectTabEvent.setValue(1);
            }
            @Override public void onError(String msg) { message.setValue("Lỗi: " + msg); }
        });
    }

    public void completeOrder(OrderItem o) {
        DriverRepository.completeOrder(o.orderId, new DriverRepository.OnResult() {
            @Override public void onSuccess() {
                message.setValue("🏁 Đã hoàn thành chuyến!");
                // Mở khoá đánh giá: tạo thông báo "Mời bạn đánh giá tài xế" cho khách.
                DriverRepository.createReviewNotification(o.buyerId, uid, o.orderId, o.carId);
                loadOrders();
                selectTabEvent.setValue(2);
            }
            @Override public void onError(String msg) { message.setValue("Lỗi: " + msg); }
        });
    }
}
