package com.example.doanmb.ui.driver.view;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import com.google.android.material.switchmaterial.SwitchMaterial;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.doanmb.R;
import com.example.doanmb.core.util.ImageLoader;
import de.hdodenhof.circleimageview.CircleImageView;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QueryDocumentSnapshot;

public class DriverHomeFragment extends Fragment {

    private static final String COL_ORDERS  = "orders";
    private static final String COL_DRIVERS = "drivers";

    private SwitchMaterial switchAvailable;
    private TextView  tvAvailableLabel;
    private TextView  tvAvailableSub;
    private View      dotAvailable;

    private TextView     tvLatestCarName, tvLatestRenter,
            tvLatestDate,    tvLatestNote;
    private View         layoutNoteBox;
    private Button       btnAcceptLatest, btnRejectLatest;

    // Tổng quan hôm nay
    private TextView     tvOverviewRevenue, tvOverviewTrips,
            tvOverviewRating, tvOverviewOnline;

    private FirebaseFirestore db;
    private String uid;
    private String latestOrderId;
    private String driverName;
    private ListenerRegistration pendingListener;

    // Danh sách đơn đang chờ + vị trí đang xem (để điều hướng qua lại)
    private final java.util.List<DocumentSnapshot> pendingDocs = new java.util.ArrayList<>();
    private int pendingIndex = 0;

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        View v = inflater.inflate(R.layout.fragment_driver_home, container, false);

        db  = FirebaseFirestore.getInstance();
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        uid = user != null ? user.getUid() : "";

        CircleImageView ivAvatar = v.findViewById(R.id.iv_home_avatar);
        switchAvailable  = v.findViewById(R.id.switch_available);
        tvAvailableLabel = v.findViewById(R.id.tv_available_label);
        tvAvailableSub   = v.findViewById(R.id.tv_available_sub);
        dotAvailable     = v.findViewById(R.id.dot_available);
        tvLatestCarName  = v.findViewById(R.id.tv_latest_car_name);
        tvLatestRenter   = v.findViewById(R.id.tv_latest_renter);
        tvLatestDate     = v.findViewById(R.id.tv_latest_date);
        tvLatestNote     = v.findViewById(R.id.tv_latest_note);
        layoutNoteBox    = v.findViewById(R.id.layout_note_box);
        btnAcceptLatest  = v.findViewById(R.id.btn_accept_latest);
        btnRejectLatest  = v.findViewById(R.id.btn_reject_latest);

        tvOverviewRevenue = v.findViewById(R.id.tv_overview_revenue);
        tvOverviewTrips   = v.findViewById(R.id.tv_overview_trips);
        tvOverviewRating  = v.findViewById(R.id.tv_overview_rating);
        tvOverviewOnline  = v.findViewById(R.id.tv_overview_online);

        if (btnAcceptLatest != null)
            btnAcceptLatest.setOnClickListener(x -> acceptLatest());
        if (btnRejectLatest != null)
            btnRejectLatest.setOnClickListener(x -> rejectLatest());

        View btnNearestPrev = v.findViewById(R.id.btn_nearest_prev);
        View btnNearestNext = v.findViewById(R.id.btn_nearest_next);
        if (btnNearestPrev != null)
            btnNearestPrev.setOnClickListener(x -> showOrderAt(pendingIndex - 1));
        if (btnNearestNext != null)
            btnNearestNext.setOnClickListener(x -> showOrderAt(pendingIndex + 1));

        loadDriverInfo();
        return v;
    }

    @Override public void onResume() {
        super.onResume();
        startPendingListener();
        loadStats();
    }

    @Override public void onPause() {
        super.onPause();
        stopPendingListener();
    }

    private void loadDriverInfo() {
        if (uid.isEmpty()) return;
        db.collection("users").document(uid).get().addOnSuccessListener(doc -> {
            if (!isAdded()) return;
            driverName = doc.getString("name");
            // Load avatar thật của user
            String avatarUrl = doc.getString("avatarUrl");
            CircleImageView av = getView() != null ? getView().findViewById(R.id.iv_home_avatar) : null;
            if (av != null && avatarUrl != null && !avatarUrl.isEmpty()) {
                ImageLoader.loadAvatar(av, avatarUrl, R.drawable.ic_driver_avatar_placeholder);
            }
        });

        db.collection(COL_DRIVERS).document(uid).get().addOnSuccessListener(doc -> {
            if (!isAdded() || switchAvailable == null) return;
            boolean available = !doc.exists() || !doc.contains("isAvailable")
                    || Boolean.TRUE.equals(doc.getBoolean("isAvailable"));
            applyAvailableUi(available);
            // Mở app khi đang ở trạng thái nhận chuyến => bắt đầu tính giờ online
            if (available) beginOnlineSession();

            switchAvailable.setOnCheckedChangeListener((btn, isChecked) -> {
                applyAvailableUi(isChecked);
                saveAvailability(isChecked);
                if (isChecked) {
                    startPendingListener();
                } else {
                    stopPendingListener();
                    showNoTrip();
                }
            });
        });
    }

    private void applyAvailableUi(boolean available) {
        if (switchAvailable  != null) switchAvailable.setChecked(available);
        if (tvAvailableLabel != null)
            tvAvailableLabel.setText(available ? "Đang nhận chuyến" : "Tạm dừng nhận chuyến");
        if (tvAvailableSub != null)
            tvAvailableSub.setText(available
                    ? "Hệ thống sẽ gửi chuyến mới cho bạn"
                    : "Bật để bắt đầu nhận chuyến");
        if (dotAvailable != null) {
            int color = androidx.core.content.ContextCompat.getColor(
                    requireContext(),
                    available ? R.color.driver_green : R.color.driver_text_secondary);
            dotAvailable.setBackgroundTintList(android.content.res.ColorStateList.valueOf(color));
        }
    }

    private void saveAvailability(boolean available) {
        if (uid.isEmpty()) return;
        DocumentReference ref = db.collection(COL_DRIVERS).document(uid);
        final String today = todayKey();
        final long now = System.currentTimeMillis();
        db.runTransaction(tr -> {
            DocumentSnapshot doc = tr.get(ref);
            String day   = doc.getString("onlineDay");
            Long   stored= doc.getLong("onlineSecondsToday");
            long   base  = today.equals(day) && stored != null ? stored : 0;
            Timestamp since = doc.getTimestamp("onlineSince");

            java.util.Map<String, Object> upd = new java.util.HashMap<>();
            upd.put("isAvailable", available);
            upd.put("onlineDay", today);
            if (available) {
                // Bật nhận chuyến => mở phiên online mới
                upd.put("onlineSecondsToday", base);
                upd.put("onlineSince", Timestamp.now());
            } else {
                // Tắt => cộng dồn thời gian phiên vừa rồi
                long elapsed = since != null ? (now - since.toDate().getTime()) / 1000 : 0;
                if (elapsed < 0) elapsed = 0;
                upd.put("onlineSecondsToday", base + elapsed);
                upd.put("onlineSince", null);
            }
            tr.set(ref, upd, com.google.firebase.firestore.SetOptions.merge());
            return null;
        }).addOnSuccessListener(x -> { if (isAdded()) loadStats(); })
          .addOnFailureListener(e -> {
            if (!isAdded()) return;
            Toast.makeText(getContext(), "Lỗi cập nhật trạng thái: " + e.getMessage(),
                    Toast.LENGTH_SHORT).show();
          });
    }

    /** Mở phiên online nếu chưa có phiên nào trong hôm nay. */
    private void beginOnlineSession() {
        if (uid.isEmpty()) return;
        DocumentReference ref = db.collection(COL_DRIVERS).document(uid);
        final String today = todayKey();
        db.runTransaction(tr -> {
            DocumentSnapshot doc = tr.get(ref);
            String day = doc.getString("onlineDay");
            if (doc.getTimestamp("onlineSince") != null && today.equals(day)) {
                return null; // đã có phiên trong hôm nay
            }
            Long stored = doc.getLong("onlineSecondsToday");
            long base = today.equals(day) && stored != null ? stored : 0;
            java.util.Map<String, Object> upd = new java.util.HashMap<>();
            upd.put("onlineDay", today);
            upd.put("onlineSecondsToday", base);
            upd.put("onlineSince", Timestamp.now());
            tr.set(ref, upd, com.google.firebase.firestore.SetOptions.merge());
            return null;
        });
    }

    /** Nạp dữ liệu cho 4 khung "Tổng quan hôm nay". */
    private void loadStats() {
        if (uid.isEmpty()) return;

        // Đánh giá trung bình + giờ online (đọc từ doc tài xế)
        db.collection(COL_DRIVERS).document(uid).get().addOnSuccessListener(doc -> {
            if (!isAdded() || doc == null) return;
            Double avg = doc.getDouble("avgRating");
            if (tvOverviewRating != null)
                tvOverviewRating.setText(String.format(java.util.Locale.getDefault(),
                        "%.1f", avg != null ? avg : 0.0));
            if (tvOverviewOnline != null)
                tvOverviewOnline.setText(formatOnline(onlineSecondsToday(doc)));
        });

        // Doanh thu + số chuyến hoàn thành trong hôm nay
        db.collection(COL_ORDERS)
                .whereEqualTo("sellerId", uid)
                .whereEqualTo("status", "completed")
                .get()
                .addOnSuccessListener(snap -> {
                    if (!isAdded() || snap == null) return;
                    long revenue = 0;
                    int  trips   = 0;
                    for (QueryDocumentSnapshot d : snap) {
                        if (!isToday(d.getTimestamp("completedAt"))) continue;
                        // Dùng totalAmount (số) — KHÔNG parse carPrice vì bài tài xế lưu chuỗi
                        // "X đ/ngày · Y đ/km" → parse sẽ dính 2 số thành số khổng lồ.
                        Long ta = d.getLong("totalAmount");
                        revenue += ta != null ? ta : 0L;
                        trips++;
                    }
                    if (tvOverviewRevenue != null) tvOverviewRevenue.setText(formatMoney(revenue));
                    if (tvOverviewTrips   != null) tvOverviewTrips.setText(String.valueOf(trips));
                });
    }

    /** Tổng số giây online trong hôm nay (gồm cả phiên đang mở). */
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
        return new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
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

    private static long parsePrice(String s) {
        if (s == null) return 0;
        String d = s.replaceAll("[^0-9]", "");
        if (d.isEmpty()) return 0;
        try { return Long.parseLong(d); } catch (NumberFormatException e) { return 0; }
    }

    private static long parseDays(String s) {
        if (s == null || s.isEmpty()) return 1;
        try {
            long d = Long.parseLong(s.replaceAll("[^0-9]", ""));
            return d > 0 ? d : 1;
        } catch (NumberFormatException e) { return 1; }
    }

    private static String formatMoney(long amount) {
        if (amount <= 0) return "0₫";
        if (amount >= 1_000_000)
            return String.format(java.util.Locale.getDefault(), "%.1ftr", amount / 1_000_000.0);
        if (amount >= 1_000)
            return (amount / 1_000) + "K";
        return amount + "₫";
    }

    private static String formatOnline(long seconds) {
        long mins = seconds / 60;
        long h = mins / 60;
        long m = mins % 60;
        if (h > 0) return m > 0 ? h + "h" + m + "m" : h + "h";
        return m + "m";
    }

    private void startPendingListener() {
        if (uid.isEmpty()) return;
        stopPendingListener();
        pendingListener = db.collection(COL_ORDERS)
                .whereEqualTo("sellerId", uid)
                .whereEqualTo("status", "pending")
                .addSnapshotListener((snap, e) -> {
                    if (!isAdded()) return;
                    if (e != null || snap == null || snap.isEmpty()) {
                        showNoTrip();
                        return;
                    }
                    // Giữ nguyên đơn đang xem nếu vẫn còn trong danh sách mới
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

    /** Hiển thị đơn tại vị trí i và cập nhật thanh điều hướng. */
    private void showOrderAt(int i) {
        if (pendingDocs.isEmpty()) { showNoTrip(); return; }
        if (i < 0) i = 0;
        if (i >= pendingDocs.size()) i = pendingDocs.size() - 1;
        pendingIndex = i;
        DocumentSnapshot d = pendingDocs.get(i);
        latestOrderId = d.getId();
        showLatestOrder(d);
        updateNearestNav();
    }

    private void updateNearestNav() {
        if (getView() == null) return;
        View nav      = getView().findViewById(R.id.layout_nearest_nav);
        TextView count= getView().findViewById(R.id.tv_nearest_count);
        View prev     = getView().findViewById(R.id.btn_nearest_prev);
        View next     = getView().findViewById(R.id.btn_nearest_next);
        int n = pendingDocs.size();
        if (nav != null)   nav.setVisibility(n > 1 ? View.VISIBLE : View.GONE);
        if (count != null) count.setText((pendingIndex + 1) + "/" + n);
        if (prev != null) {
            boolean can = pendingIndex > 0;
            prev.setEnabled(can);
            prev.setAlpha(can ? 1f : 0.35f);
        }
        if (next != null) {
            boolean can = pendingIndex < n - 1;
            next.setEnabled(can);
            next.setAlpha(can ? 1f : 0.35f);
        }
    }

    private void stopPendingListener() {
        if (pendingListener != null) {
            pendingListener.remove();
            pendingListener = null;
        }
    }

    private void showLatestOrder(DocumentSnapshot d) {
        View cardNearest = getView() != null ? getView().findViewById(R.id.card_nearest) : null;
        View tvNoNearest = getView() != null ? getView().findViewById(R.id.tv_no_nearest) : null;
        if (cardNearest != null) cardNearest.setVisibility(View.VISIBLE);
        if (tvNoNearest != null) tvNoNearest.setVisibility(View.GONE);

        java.text.SimpleDateFormat fmt =
                new java.text.SimpleDateFormat("dd/MM HH:mm", java.util.Locale.getDefault());

        String carName = d.getString("carName");
        String price   = d.getString("carPrice");

        setText(tvLatestCarName, carName);
        TextView tvNCartype = getView() != null ? getView().findViewById(R.id.tv_n_cartype) : null;
        String type = d.getString("type");
        if (tvNCartype != null)
            tvNCartype.setText(type != null && !type.isEmpty() ? type : "Có tài xế");

        TextView tvNPrice = getView() != null ? getView().findViewById(R.id.tv_n_price) : null;
        if (tvNPrice != null) setText(tvNPrice, price);

        String rn = d.getString("renterName");
        String rp = d.getString("renterPhone");
        setText(tvLatestRenter, (rn != null ? rn : "") +
                (rp != null && !rp.isEmpty() ? "  |  " + rp : ""));

        String note = d.getString("note");
        boolean hasNote = note != null && !note.isEmpty();
        if (layoutNoteBox != null) layoutNoteBox.setVisibility(hasNote ? View.VISIBLE : View.GONE);
        if (hasNote) setText(tvLatestNote, note);

        Timestamp ts = d.getTimestamp("createdAt");
        if (tvLatestDate != null && ts != null)
            tvLatestDate.setText("📅 " + fmt.format(ts.toDate()));
    }

    private void showNoTrip() {
        latestOrderId = null;
        pendingDocs.clear();
        pendingIndex = 0;
        View cardNearest = getView() != null ? getView().findViewById(R.id.card_nearest) : null;
        View tvNoNearest = getView() != null ? getView().findViewById(R.id.tv_no_nearest) : null;
        View nav         = getView() != null ? getView().findViewById(R.id.layout_nearest_nav) : null;
        if (cardNearest != null) cardNearest.setVisibility(View.GONE);
        if (tvNoNearest != null) tvNoNearest.setVisibility(View.VISIBLE);
        if (nav != null) nav.setVisibility(View.GONE);
    }

    private void acceptLatest() {
        if (latestOrderId == null) return;
        String savedOrderId = latestOrderId;
        DocumentReference ref = db.collection(COL_ORDERS).document(savedOrderId);
        db.runTransaction(tr -> {
            DocumentSnapshot snap = tr.get(ref);
            if (!"pending".equals(snap.getString("status"))) {
                throw new com.google.firebase.firestore.FirebaseFirestoreException(
                        "Đơn đã được xử lý",
                        com.google.firebase.firestore.FirebaseFirestoreException.Code.ABORTED);
            }
            tr.update(ref,
                    "status",     "accepted",
                    "driverName", driverName != null ? driverName : "",
                    "acceptedAt", com.google.firebase.Timestamp.now());
            return null;
        }).addOnSuccessListener(x -> {
            if (!isAdded()) return;
            Toast.makeText(getContext(), "✅ Đã nhận chuyến!", Toast.LENGTH_SHORT).show();
            notifyCustomerAccepted(savedOrderId);
        }).addOnFailureListener(e -> {
            if (!isAdded()) return;
            Toast.makeText(getContext(), "Lỗi: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        });
    }

    private void notifyCustomerAccepted(String orderId) {
        db.collection(COL_ORDERS).document(orderId).get()
                .addOnSuccessListener(snap -> {
                    if (snap == null || !snap.exists()) return;
                    String buyerId = snap.getString("buyerId");
                    String carName = snap.getString("carName");
                    String carId   = snap.getString("carId");
                    if (buyerId == null || buyerId.isEmpty()) return;

                    db.collection("users").document(uid).get()
                            .addOnSuccessListener(userSnap -> {
                                String myName = userSnap.getString("name");
                                if (myName == null || myName.isEmpty()) myName = "Tài xế";
                                com.example.doanmb.core.helper.ChatNotificationHelper
                                        .sendOrderNotification(
                                                requireContext(),
                                                buyerId,
                                                uid,
                                                myName,
                                                carName != null ? carName : "",
                                                carId   != null ? carId   : "",
                                                "order_confirmed",
                                                orderId
                                        );
                            });
                });
    }

    private void rejectLatest() {
        if (latestOrderId == null) return;
        String savedOrderId = latestOrderId;
        db.collection(COL_ORDERS).document(savedOrderId)
                .update("status", "rejected", "rejectedAt", com.google.firebase.Timestamp.now())
                .addOnSuccessListener(x -> {
                    if (!isAdded()) return;
                    Toast.makeText(getContext(), "Đã từ chối chuyến.", Toast.LENGTH_SHORT).show();
                    notifyCustomerRejected(savedOrderId);
                })
                .addOnFailureListener(e -> {
                    if (!isAdded()) return;
                    Toast.makeText(getContext(), "Lỗi: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    private void notifyCustomerRejected(String orderId) {
        db.collection(COL_ORDERS).document(orderId).get()
                .addOnSuccessListener(snap -> {
                    if (snap == null || !snap.exists()) return;
                    String buyerId  = snap.getString("buyerId");
                    String carName  = snap.getString("carName");
                    String carId    = snap.getString("carId");
                    if (buyerId == null || buyerId.isEmpty()) return;

                    db.collection("users").document(uid).get()
                            .addOnSuccessListener(userSnap -> {
                                String myName = userSnap.getString("name");
                                if (myName == null || myName.isEmpty()) myName = "Tài xế";
                                com.example.doanmb.core.helper.ChatNotificationHelper
                                        .sendOrderNotification(
                                                requireContext(),
                                                buyerId,
                                                uid,
                                                myName,
                                                carName != null ? carName : "",
                                                carId   != null ? carId   : "",
                                                "order_rejected",
                                                orderId
                                        );
                            });
                });
    }

    private static void setText(TextView tv, String text) {
        if (tv != null) tv.setText(text != null ? text : "");
    }
}