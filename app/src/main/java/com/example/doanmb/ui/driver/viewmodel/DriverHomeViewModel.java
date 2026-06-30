package com.example.doanmb.ui.driver.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.doanmb.data.repository.DriverRepository;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * ViewModel màn Trang chủ tài xế: trạng thái nhận chuyến, đơn pending realtime + điều hướng,
 * 4 ô tổng quan hôm nay. Việc gửi FCM cho khách (cần Context) phát ra dưới dạng event cho View.
 */
public class DriverHomeViewModel extends ViewModel {

    /** Tổng quan hôm nay (đã định dạng sẵn cho View). */
    public static class Stats {
        public final String revenue, trips, rating, online;
        public Stats(String revenue, String trips, String rating, String online) {
            this.revenue = revenue; this.trips = trips; this.rating = rating; this.online = online;
        }
    }

    /** Thanh điều hướng giữa các đơn pending. */
    public static class NavInfo {
        public final int index, total;
        public NavInfo(int index, int total) { this.index = index; this.total = total; }
    }

    /** Sự kiện báo cho khách (cần Context để gửi FCM). */
    public static class NotifyEvent {
        public final String orderId, type;
        public NotifyEvent(String orderId, String type) { this.orderId = orderId; this.type = type; }
    }

    private final MutableLiveData<String>  driverName = new MutableLiveData<>();
    private final MutableLiveData<String>  avatarUrl  = new MutableLiveData<>();
    private final MutableLiveData<Boolean> available  = new MutableLiveData<>(true);
    private final MutableLiveData<Stats>   stats      = new MutableLiveData<>();
    private final MutableLiveData<DocumentSnapshot> currentPending = new MutableLiveData<>();
    private final MutableLiveData<NavInfo> navInfo    = new MutableLiveData<>();
    private final MutableLiveData<String>  message    = new MutableLiveData<>();
    private final MutableLiveData<NotifyEvent> notifyCustomer = new MutableLiveData<>();

    public LiveData<String>  getDriverName() { return driverName; }
    public LiveData<String>  getAvatarUrl()  { return avatarUrl; }
    public LiveData<Boolean> getAvailable()  { return available; }
    public LiveData<Stats>   getStats()      { return stats; }
    public LiveData<DocumentSnapshot> getCurrentPending() { return currentPending; }
    public LiveData<NavInfo> getNavInfo()    { return navInfo; }
    public LiveData<String>  getMessage()    { return message; }
    public LiveData<NotifyEvent> getNotifyCustomer() { return notifyCustomer; }

    private final String uid;
    private String latestOrderId;
    private boolean infoLoaded = false;

    private final List<DocumentSnapshot> pendingDocs = new ArrayList<>();
    private int pendingIndex = 0;
    private ListenerRegistration pendingListener;

    public DriverHomeViewModel() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        uid = user != null ? user.getUid() : "";
    }

    /** Gọi 1 lần khi mở màn: tải hồ sơ + trạng thái nhận chuyến. */
    public void loadDriverInfo() {
        if (infoLoaded || uid.isEmpty()) return;
        infoLoaded = true;
        DriverRepository.loadUserBrief(uid, (name, avatar) -> {
            driverName.setValue(name);
            avatarUrl.setValue(avatar);
        });
        DriverRepository.loadDriverDoc(uid, new DriverRepository.OnDoc() {
            @Override public void onLoaded(DocumentSnapshot doc) {
                boolean avail = doc == null || !doc.exists() || !doc.contains("isAvailable")
                        || Boolean.TRUE.equals(doc.getBoolean("isAvailable"));
                available.setValue(avail);
                if (avail) DriverRepository.beginOnlineSession(uid); // bắt đầu tính giờ online
            }
            @Override public void onError(String msg) { available.setValue(true); }
        });
    }

    /** Người dùng gạt switch nhận chuyến. */
    public void setAvailable(boolean avail) {
        available.setValue(avail);
        DriverRepository.saveAvailability(uid, avail, new DriverRepository.OnResult() {
            @Override public void onSuccess() { loadStats(); }
            @Override public void onError(String msg) { message.setValue("Lỗi cập nhật trạng thái: " + msg); }
        });
        if (avail) startPendingListener();
        else { stopPendingListener(); showNoTrip(); }
    }

    public void onResume() {
        startPendingListener();
        loadStats();
    }

    public void onPause() { stopPendingListener(); }

    /** Nạp 4 ô "Tổng quan hôm nay". */
    public void loadStats() {
        if (uid.isEmpty()) return;
        final String[] rating = {"0.0"};
        final String[] online = {"0m"};

        DriverRepository.loadDriverDoc(uid, new DriverRepository.OnDoc() {
            @Override public void onLoaded(DocumentSnapshot doc) {
                if (doc != null) {
                    Double avg = doc.getDouble("avgRating");
                    rating[0] = String.format(Locale.getDefault(), "%.1f", avg != null ? avg : 0.0);
                    online[0] = formatOnline(onlineSecondsToday(doc));
                }
                publishStatsRatingOnline(rating[0], online[0]);
            }
            @Override public void onError(String msg) { publishStatsRatingOnline(rating[0], online[0]); }
        });

        DriverRepository.loadCompletedOrders(uid, new DriverRepository.OnSnapshot() {
            @Override public void onLoaded(com.google.firebase.firestore.QuerySnapshot snap) {
                long revenue = 0; int trips = 0;
                for (QueryDocumentSnapshot d : snap) {
                    if (!isToday(d.getTimestamp("completedAt"))) continue;
                    Long ta = d.getLong("totalAmount");
                    revenue += ta != null ? ta : 0L;
                    trips++;
                }
                publishStatsRevenueTrips(formatMoney(revenue), String.valueOf(trips));
            }
            @Override public void onError(String msg) { }
        });
    }

    // Hai nguồn stats về bất đồng bộ → gộp giữ lại giá trị mới nhất của phía kia.
    private String lastRating = "0.0", lastOnline = "0m", lastRevenue = "0₫", lastTrips = "0";
    private void publishStatsRatingOnline(String rating, String online) {
        lastRating = rating; lastOnline = online;
        stats.setValue(new Stats(lastRevenue, lastTrips, lastRating, lastOnline));
    }
    private void publishStatsRevenueTrips(String revenue, String trips) {
        lastRevenue = revenue; lastTrips = trips;
        stats.setValue(new Stats(lastRevenue, lastTrips, lastRating, lastOnline));
    }

    // ── Đơn pending realtime ───────────────────────────────────────────────────

    public void startPendingListener() {
        if (uid.isEmpty()) return;
        stopPendingListener();
        pendingListener = DriverRepository.listenPendingOrders(uid, (snap, e) -> {
            if (e != null || snap == null || snap.isEmpty()) { showNoTrip(); return; }
            String keepId = latestOrderId;
            pendingDocs.clear();
            pendingDocs.addAll(snap.getDocuments());
            int idx = 0;
            if (keepId != null) {
                for (int i = 0; i < pendingDocs.size(); i++) {
                    if (keepId.equals(pendingDocs.get(i).getId())) { idx = i; break; }
                }
            }
            showOrderAt(idx);
        });
    }

    public void stopPendingListener() {
        if (pendingListener != null) { pendingListener.remove(); pendingListener = null; }
    }

    public void showOrderAt(int i) {
        if (pendingDocs.isEmpty()) { showNoTrip(); return; }
        if (i < 0) i = 0;
        if (i >= pendingDocs.size()) i = pendingDocs.size() - 1;
        pendingIndex = i;
        DocumentSnapshot d = pendingDocs.get(i);
        latestOrderId = d.getId();
        currentPending.setValue(d);
        navInfo.setValue(new NavInfo(pendingIndex, pendingDocs.size()));
    }

    public void showPrev() { showOrderAt(pendingIndex - 1); }
    public void showNext() { showOrderAt(pendingIndex + 1); }

    private void showNoTrip() {
        latestOrderId = null;
        pendingDocs.clear();
        pendingIndex = 0;
        currentPending.setValue(null);
        navInfo.setValue(new NavInfo(0, 0));
    }

    // ── Nhận / từ chối chuyến ──────────────────────────────────────────────────

    public void acceptLatest() {
        if (latestOrderId == null) return;
        final String orderId = latestOrderId;
        DriverRepository.acceptOrder(orderId, driverName.getValue(), new DriverRepository.OnResult() {
            @Override public void onSuccess() {
                message.setValue("✅ Đã nhận chuyến!");
                notifyCustomer.setValue(new NotifyEvent(orderId, "order_confirmed"));
            }
            @Override public void onError(String msg) { message.setValue("Lỗi: " + msg); }
        });
    }

    public void rejectLatest() {
        if (latestOrderId == null) return;
        final String orderId = latestOrderId;
        DriverRepository.rejectOrder(orderId, new DriverRepository.OnResult() {
            @Override public void onSuccess() {
                message.setValue("Đã từ chối chuyến.");
                notifyCustomer.setValue(new NotifyEvent(orderId, "order_rejected"));
            }
            @Override public void onError(String msg) { message.setValue("Lỗi: " + msg); }
        });
    }

    // ── Helpers tính toán (logic thuần) ────────────────────────────────────────

    private static long onlineSecondsToday(DocumentSnapshot doc) {
        String today = todayKey();
        String day   = doc.getString("onlineDay");
        Long   stored= doc.getLong("onlineSecondsToday");
        long   base  = today.equals(day) && stored != null ? stored : 0;
        Timestamp since = doc.getTimestamp("onlineSince");
        boolean online  = Boolean.TRUE.equals(doc.getBoolean("isAvailable"));
        if (online && since != null) {
            long elapsed = (System.currentTimeMillis() - since.toDate().getTime()) / 1000;
            if (elapsed < 0) elapsed = 0;
            base = today.equals(day) ? base + elapsed : elapsed;
        }
        return base;
    }

    private static String todayKey() {
        return new java.text.SimpleDateFormat("yyyy-MM-dd", Locale.US)
                .format(new java.util.Date());
    }

    private static boolean isToday(Timestamp ts) {
        if (ts == null) return false;
        java.util.Calendar now = java.util.Calendar.getInstance();
        java.util.Calendar c   = java.util.Calendar.getInstance();
        c.setTime(ts.toDate());
        return now.get(java.util.Calendar.YEAR) == c.get(java.util.Calendar.YEAR)
                && now.get(java.util.Calendar.DAY_OF_YEAR) == c.get(java.util.Calendar.DAY_OF_YEAR);
    }

    private static String formatMoney(long amount) {
        if (amount <= 0) return "0₫";
        if (amount >= 1_000_000)
            return String.format(Locale.getDefault(), "%.1ftr", amount / 1_000_000.0);
        if (amount >= 1_000) return (amount / 1_000) + "K";
        return amount + "₫";
    }

    private static String formatOnline(long seconds) {
        long mins = seconds / 60;
        long h = mins / 60;
        long m = mins % 60;
        if (h > 0) return m > 0 ? h + "h" + m + "m" : h + "h";
        return m + "m";
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        stopPendingListener();
    }
}
