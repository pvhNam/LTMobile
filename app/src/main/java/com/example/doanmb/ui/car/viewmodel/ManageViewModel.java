package com.example.doanmb.ui.car.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.doanmb.data.model.Car;
import com.example.doanmb.data.repository.CarRepository;
import com.example.doanmb.data.repository.OrderRepository;
import com.example.doanmb.data.repository.WalletRepository;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * ViewModel màn Quản lý: tin đã đăng + yêu cầu (đơn nhận được và đơn mình gửi đi).
 * Lắng nghe đơn theo thời gian thực qua {@link OrderRepository}; các tác vụ cần Context
 * (huỷ lịch nhắc, gửi FCM) được phát ra dưới dạng sự kiện cho View thực hiện.
 */
public class ManageViewModel extends ViewModel {

    /** Một dòng yêu cầu: id đơn + dữ liệu đơn. */
    public static class OrderItem {
        public final String id;
        public final Map<String, Object> data;
        public OrderItem(String id, Map<String, Object> data) { this.id = id; this.data = data; }
    }

    /** Kết quả tính hoá đơn khi khách trả xe (để View dựng hộp thoại xác nhận). */
    public static class ReturnInvoice {
        public long lateDays, penalty, total, invoiceTotal;
        public String reason;
    }

    /** Sự kiện yêu cầu View báo cho người mua/thuê (cần Context). */
    public static class NotifyEvent {
        public final String orderId, type;
        public NotifyEvent(String orderId, String type) { this.orderId = orderId; this.type = type; }
    }

    private final MutableLiveData<List<Car>> myPosts = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<List<OrderItem>> requests = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<String> message = new MutableLiveData<>();
    private final MutableLiveData<String> cancelReminderEvent = new MutableLiveData<>();
    private final MutableLiveData<NotifyEvent> notifyBuyerEvent = new MutableLiveData<>();

    public LiveData<List<Car>> getMyPosts() { return myPosts; }
    public LiveData<List<OrderItem>> getRequests() { return requests; }
    public LiveData<String> getMessage() { return message; }
    public LiveData<String> getCancelReminderEvent() { return cancelReminderEvent; }
    public LiveData<NotifyEvent> getNotifyBuyerEvent() { return notifyBuyerEvent; }

    private final String currentUserId;
    private boolean started = false;

    // Hai nguồn đơn: nhận được (chủ xe) và gửi đi (đi thuê) — gộp chung 1 danh sách.
    private final Map<String, Map<String, Object>> incomingOrders = new LinkedHashMap<>();
    private final Map<String, Map<String, Object>> outgoingOrders = new LinkedHashMap<>();

    private ListenerRegistration requestsListener;
    private ListenerRegistration outgoingListener;
    private boolean usingCarIdFallback = false;

    public ManageViewModel() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        currentUserId = user != null ? user.getUid() : null;
    }

    public boolean isLoggedIn() { return currentUserId != null; }

    /** Gọi 1 lần khi mở màn: tải tin + gắn listener đơn. */
    public void start() {
        if (started || currentUserId == null) return;
        started = true;
        loadMyPosts();
        listenRequests();
    }

    /** Tải lại tin đã đăng (gọi khi onResume). */
    public void loadMyPosts() {
        if (currentUserId == null) return;
        CarRepository.loadMyPosts(currentUserId, new CarRepository.OnCars() {
            @Override public void onLoaded(List<Car> cars) { myPosts.setValue(cars); }
            @Override public void onError(String msg) { /* giữ danh sách cũ */ }
        });
    }

    // ── Lắng nghe đơn theo thời gian thực ─────────────────────────────────────

    private void listenRequests() {
        usingCarIdFallback = false;
        incomingOrders.clear();
        outgoingOrders.clear();

        // (A) Đơn NHẬN ĐƯỢC — theo sellerId (fallback theo carId nếu trống)
        requestsListener = OrderRepository.listenOrdersBySeller(currentUserId, (snapshots, error) -> {
            if (error != null) {
                if (!usingCarIdFallback) { usingCarIdFallback = true; listenByCarId(); }
                return;
            }
            if (snapshots == null) return;
            if (snapshots.isEmpty() && !usingCarIdFallback) {
                usingCarIdFallback = true;
                listenByCarId();
                return;
            }
            if (!usingCarIdFallback) {
                incomingOrders.clear();
                for (QueryDocumentSnapshot doc : snapshots) incomingOrders.put(doc.getId(), doc.getData());
                rebuildRequestList();
            }
        });

        // (B) Đơn MÌNH GỬI ĐI — theo buyerId
        outgoingListener = OrderRepository.listenOrdersByBuyer(currentUserId, (snapshots, error) -> {
            if (error != null || snapshots == null) return;
            outgoingOrders.clear();
            for (QueryDocumentSnapshot doc : snapshots) outgoingOrders.put(doc.getId(), doc.getData());
            rebuildRequestList();
        });
    }

    private void listenByCarId() {
        CarRepository.loadMyCarIds(currentUserId, carIds -> {
            if (carIds.isEmpty()) { rebuildRequestList(); return; }
            if (requestsListener != null) requestsListener.remove();
            requestsListener = OrderRepository.listenOrdersByCarIds(carIds, (orderSnapshots, error) -> {
                if (error != null || orderSnapshots == null) return;
                incomingOrders.clear();
                for (QueryDocumentSnapshot doc : orderSnapshots) incomingOrders.put(doc.getId(), doc.getData());
                rebuildRequestList();
            });
        });
    }

    private void rebuildRequestList() {
        Map<String, Map<String, Object>> merged = new LinkedHashMap<>();
        merged.putAll(incomingOrders);
        merged.putAll(outgoingOrders);

        List<AbstractMap.SimpleEntry<String, Map<String, Object>>> paired = new ArrayList<>();
        for (Map.Entry<String, Map<String, Object>> e : merged.entrySet()) {
            paired.add(new AbstractMap.SimpleEntry<>(e.getKey(), e.getValue()));
        }
        paired.sort((a, b) -> {
            Timestamp ta = (Timestamp) a.getValue().get("createdAt");
            Timestamp tb = (Timestamp) b.getValue().get("createdAt");
            if (ta == null && tb == null) return 0;
            if (ta == null) return 1;
            if (tb == null) return -1;
            return tb.compareTo(ta);
        });
        List<OrderItem> result = new ArrayList<>();
        for (AbstractMap.SimpleEntry<String, Map<String, Object>> entry : paired) {
            result.add(new OrderItem(entry.getKey(), entry.getValue()));
        }
        requests.setValue(result);
    }

    // ── Nghiệp vụ chủ xe ───────────────────────────────────────────────────────

    /** Xác nhận yêu cầu → xe "sold", đơn "confirmed". */
    public void confirmRequest(String orderId, String carId) {
        cancelReminderEvent.setValue(orderId);
        OrderRepository.updateStatus(orderId, "confirmed");
        if (carId != null && !carId.isEmpty()) {
            CarRepository.setStatus(carId, "sold", new CarRepository.OnResult() {
                @Override public void onSuccess() {
                    message.setValue("✅ Đã xác nhận! Xe sẽ được ẩn khỏi danh sách.");
                    loadMyPosts();
                }
                @Override public void onError(String msg) { message.setValue("Lỗi: " + msg); }
            });
        } else {
            message.setValue("✅ Đã xác nhận yêu cầu!");
        }
        notifyBuyerEvent.setValue(new NotifyEvent(orderId, "order_confirmed"));
    }

    /** Từ chối yêu cầu → xe về "active". */
    public void rejectRequest(String orderId, String carId) {
        cancelReminderEvent.setValue(orderId);
        OrderRepository.updateStatus(orderId, "rejected");
        if (carId != null && !carId.isEmpty()) {
            CarRepository.setStatus(carId, "active", new CarRepository.OnResult() {
                @Override public void onSuccess() {
                    message.setValue("Đã từ chối yêu cầu. Xe tiếp tục hiển thị.");
                    loadMyPosts();
                }
                @Override public void onError(String msg) { message.setValue("Lỗi: " + msg); }
            });
        } else {
            message.setValue("Đã từ chối yêu cầu.");
        }
        notifyBuyerEvent.setValue(new NotifyEvent(orderId, "order_rejected"));
    }

    /** Tính hoá đơn khi khách trả xe (phạt trễ nếu có). */
    public ReturnInvoice computeReturnInvoice(Map<String, Object> order) {
        long days        = parseIntSafe((String) order.get("days"));
        long total       = toLong(order.get("totalAmount"));
        long pricePerDay = days > 0 ? total / days : total;
        long lateDays    = computeLateDays(order);
        long penalty     = 0;
        if (lateDays >= 1) {
            penalty = Math.round(1.5 * pricePerDay) + (lateDays - 1) * Math.round(2.0 * pricePerDay);
        }
        ReturnInvoice inv = new ReturnInvoice();
        inv.lateDays = lateDays;
        inv.penalty = penalty;
        inv.total = total;
        inv.invoiceTotal = total + penalty;
        inv.reason = lateDays > 0
                ? "Trả xe trễ " + lateDays + " ngày. Hóa đơn gồm tiền thuê và phí phạt (150% ngày đầu, 200% các ngày sau)."
                : "Thanh toán tiền thuê xe khi kết thúc chuyến.";
        return inv;
    }

    /** Chủ xe gửi hoá đơn cho khách (sau khi xác nhận trong hộp thoại). */
    public void sendInvoice(String orderId, Map<String, Object> order, ReturnInvoice inv) {
        Map<String, Object> up = new HashMap<>();
        up.put("status", "awaiting_payment");
        up.put("returnedAt", Timestamp.now());
        up.put("lateDays", inv.lateDays);
        up.put("penaltyAmount", inv.penalty);
        up.put("invoiceTotal", inv.invoiceTotal);
        up.put("invoiceStatus", "unpaid");
        up.put("invoiceReason", inv.reason);
        OrderRepository.updateFields(orderId, up);

        String carId = (String) order.get("carId");
        if (carId != null && !carId.isEmpty()) CarRepository.setStatus(carId, "active", null);

        String buyerId = (String) order.get("buyerId");
        OrderRepository.writeNotification(buyerId, currentUserId, "invoice", "Hóa đơn thuê xe",
                "Vui lòng thanh toán " + money(inv.invoiceTotal)
                        + (inv.lateDays > 0 ? (" (trễ " + inv.lateDays + " ngày)") : ""), orderId);
        message.setValue("✅ Đã gửi hóa đơn cho khách.");
    }

    // ── Nghiệp vụ khách ──────────────────────────────────────────────────────────

    public void cancelOwnOrder(String orderId, Map<String, Object> order) {
        OrderRepository.updateStatus(orderId, "rejected");

        // Hoàn cọc nếu đã giữ
        String depositStatus = (String) order.get("depositStatus");
        long deposit  = toLong(order.get("depositAmount"));
        String buyerId = (String) order.get("buyerId");
        if ("held".equals(depositStatus) && deposit > 0 && buyerId != null) {
            WalletRepository.refund(buyerId, deposit, orderId, null);
        }
        // Trả xe về active nếu đang giữ chỗ
        String carId = (String) order.get("carId");
        if (carId != null && !carId.isEmpty()) CarRepository.setStatus(carId, "active", null);

        String sellerId = (String) order.get("sellerId");
        Object carName = order.get("carName");
        OrderRepository.writeNotification(sellerId, currentUserId, "order_rejected", "Khách đã hủy",
                "Khách đã hủy yêu cầu thuê " + (carName != null ? carName : "xe"), orderId);
        message.setValue("Đã hủy yêu cầu.");
    }

    public void extendOrder(String orderId, Map<String, Object> order, int extra) {
        if (extra <= 0) { message.setValue("Số ngày không hợp lệ"); return; }
        int oldDays = parseIntSafe((String) order.get("days"));
        int newDays = oldDays + extra;
        Map<String, Object> up = new HashMap<>();
        up.put("days", String.valueOf(newDays));
        up.put("extendRequested", true);
        OrderRepository.updateFields(orderId, up);

        String sellerId = (String) order.get("sellerId");
        Object carName = order.get("carName");
        OrderRepository.writeNotification(sellerId, currentUserId, "order_sent", "Yêu cầu gia hạn",
                "Khách xin gia hạn thêm " + extra + " ngày (tổng " + newDays + " ngày) cho "
                        + (carName != null ? carName : "xe"), orderId);
        message.setValue("✅ Đã gửi yêu cầu gia hạn cho chủ xe.");
    }

    // ── Helpers (logic tính toán) ─────────────────────────────────────────────────

    /** Số ngày trễ = (hôm nay) - (ngày bắt đầu + số ngày thuê), làm tròn lên; 0 nếu chưa trễ. */
    private long computeLateDays(Map<String, Object> order) {
        int days = parseIntSafe((String) order.get("days"));
        long dueMillis = parseStartMillis(order) + (long) days * 86_400_000L;
        long now = System.currentTimeMillis();
        if (now <= dueMillis) return 0;
        return (now - dueMillis + 86_400_000L - 1) / 86_400_000L;
    }

    private long parseStartMillis(Map<String, Object> order) {
        String s = (String) order.get("startDate");
        if (s != null && !s.trim().isEmpty()) {
            String[] fmts = {"dd/MM/yyyy", "yyyy-MM-dd", "dd-MM-yyyy"};
            for (String f : fmts) {
                try {
                    java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat(f, Locale.US);
                    sdf.setLenient(false);
                    java.util.Date dt = sdf.parse(s.trim());
                    if (dt != null) return dt.getTime();
                } catch (Exception ignore) { }
            }
        }
        Object c = order.get("createdAt");
        if (c instanceof Timestamp) return ((Timestamp) c).toDate().getTime();
        return System.currentTimeMillis();
    }

    public static int parseIntSafe(String s) {
        if (s == null) return 0;
        try { return Integer.parseInt(s.trim().replaceAll("[^0-9]", "")); } catch (Exception e) { return 0; }
    }

    private static long toLong(Object o) {
        if (o instanceof Number) return ((Number) o).longValue();
        if (o instanceof String) {
            try { return Long.parseLong(((String) o).replaceAll("[^0-9]", "")); } catch (Exception e) { return 0; }
        }
        return 0;
    }

    private static String money(long v) {
        return String.format(Locale.US, "%,d", v).replace(',', '.') + " đ";
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        if (requestsListener != null) { requestsListener.remove(); requestsListener = null; }
        if (outgoingListener != null) { outgoingListener.remove(); outgoingListener = null; }
    }
}
