package com.example.doanmb.ui.driver.view;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.bumptech.glide.Glide;
import com.example.doanmb.R;
import com.example.doanmb.data.model.Trip;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import de.hdodenhof.circleimageview.CircleImageView;

/**
 * Trang chủ tài xế (thiết kế driver1): công tắc nhận chuyến, bản đồ, thẻ "chuyến
 * gần nhất" (nhận/bỏ qua) và tổng quan hôm nay.
 */
public class DriverHomeFragment extends Fragment {

    private SwitchMaterial switchReceive;
    private MaterialButton btnToggle, btnSkip, btnAccept;
    private TextView tvReceiveState, tvNoNearest;
    private View cardNearest;
    private TextView tvCarType, tvPickup, tvDest, tvMeta, tvPrice;
    private CircleImageView ivHomeAvatar;
    private TextView tvHomeName;

    private FirebaseFirestore db;
    private String uid, driverName, driverCarType;
    private boolean online = true;

    private final List<Trip> waiting = new ArrayList<>();
    private int index = 0;

    private static final DecimalFormat MONEY = new DecimalFormat("#,###");

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_driver_home, container, false);
        db = FirebaseFirestore.getInstance();
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        uid = user != null ? user.getUid() : "";

        switchReceive = v.findViewById(R.id.switch_receive);
        btnToggle = v.findViewById(R.id.btn_toggle_receive);
        tvReceiveState = v.findViewById(R.id.tv_receive_state);
        cardNearest = v.findViewById(R.id.card_nearest);
        tvNoNearest = v.findViewById(R.id.tv_no_nearest);
        tvCarType = v.findViewById(R.id.tv_n_cartype);
        tvPickup = v.findViewById(R.id.tv_n_pickup);
        tvDest = v.findViewById(R.id.tv_n_dest);
        tvMeta = v.findViewById(R.id.tv_n_meta);
        tvPrice = v.findViewById(R.id.tv_n_price);
        btnSkip = v.findViewById(R.id.btn_n_skip);
        btnAccept = v.findViewById(R.id.btn_n_accept);
        ivHomeAvatar = v.findViewById(R.id.iv_home_avatar);
        tvHomeName = v.findViewById(R.id.tv_home_name);

        ivHomeAvatar.setOnClickListener(view -> {
            if (getActivity() instanceof DriverDashboardActivity) {
                ((DriverDashboardActivity) getActivity()).openProfileTab();
            }
        });

        // 4 ô tổng quan
        bindStat(v.findViewById(R.id.stat_revenue), R.drawable.ic_money,
                R.color.driver_primary, "0đ", "Doanh thu");
        bindStat(v.findViewById(R.id.stat_trips), R.drawable.ic_dnav_trips,
                R.color.driver_primary, "0", "Chuyến");
        bindStat(v.findViewById(R.id.stat_rating), R.drawable.ic_star,
                R.color.driver_orange, "4.9", "Đánh giá");
        bindStat(v.findViewById(R.id.stat_online), R.drawable.ic_clock,
                R.color.driver_primary, "8h 30m", "Online");

        switchReceive.setOnClickListener(view -> setOnline(switchReceive.isChecked()));
        btnToggle.setOnClickListener(view -> setOnline(!online));
        btnSkip.setOnClickListener(view -> { index++; showNearest(); });
        btnAccept.setOnClickListener(view -> acceptCurrent());

        return v;
    }

    @Override
    public void onResume() {
        super.onResume();
        loadProfileThenData();
    }

    private void loadProfileThenData() {
        if (uid.isEmpty()) return;
        db.collection("users").document(uid).get().addOnSuccessListener(doc -> {
            if (!isAdded()) return;
            driverName = doc.getString("name");
            driverCarType = doc.getString("driverCarType");
            Boolean on = doc.getBoolean("driverOnline");
            online = on == null || on;

            tvHomeName.setText(driverName != null && !driverName.isEmpty() ? driverName : "Tài xế");
            String avatar = doc.getString("avatarUrl");
            if (avatar != null && !avatar.isEmpty()) Glide.with(this).load(avatar).into(ivHomeAvatar);

            applyOnlineUi();
            loadWaiting();
            loadTodayStats();
        });
    }

    /** Cập nhật trạng thái online trên Firestore + giao diện. */
    private void setOnline(boolean value) {
        online = value;
        applyOnlineUi();
        if (!uid.isEmpty()) db.collection("users").document(uid).update("driverOnline", value);
        loadWaiting();
    }

    private void applyOnlineUi() {
        switchReceive.setChecked(online);
        tvReceiveState.setText(online ? "Đang nhận chuyến" : "Đã tắt nhận chuyến");
        btnToggle.setText(online ? "Tắt nhận chuyến" : "Bật nhận chuyến");
    }

    /** Đơn đang chờ (lọc theo loại xe của tài xế). */
    private void loadWaiting() {
        waiting.clear();
        index = 0;
        if (!online) { showNearest(); return; }
        db.collection("trips").whereEqualTo("status", Trip.STATUS_WAITING).get()
                .addOnSuccessListener(snap -> {
                    if (!isAdded()) return;
                    waiting.clear();
                    for (QueryDocumentSnapshot d : snap) {
                        Trip t = d.toObject(Trip.class);
                        t.setId(d.getId());
                        if (driverCarType == null || driverCarType.isEmpty()
                                || driverCarType.equals(t.getCarType())) {
                            waiting.add(t);
                        }
                    }
                    showNearest();
                });
    }

    private void showNearest() {
        boolean has = online && index >= 0 && index < waiting.size();
        cardNearest.setVisibility(has ? View.VISIBLE : View.GONE);
        tvNoNearest.setVisibility(has ? View.GONE : View.VISIBLE);
        if (!has) {
            tvNoNearest.setText(online
                    ? "Chưa có chuyến nào đang chờ"
                    : "Bạn đang offline — bật nhận chuyến để xem chuyến mới");
            return;
        }
        Trip t = waiting.get(index);
        tvCarType.setText(t.getCarType() != null ? t.getCarType() : "Chuyến xe");
        tvPickup.setText(safe(t.getPickup()));
        if (Trip.MODE_DISTANCE.equals(t.getRentMode())) {
            tvDest.setText(safe(t.getDestination()));
            tvMeta.setText(t.getDistanceKm() > 0 ? t.getDistanceKm() + " km" : "Theo quãng đường");
        } else {
            String unit = Trip.MODE_MONTH.equals(t.getRentMode()) ? "tháng" : "ngày";
            tvDest.setText("Thuê " + t.getDuration() + " " + unit);
            tvMeta.setText(t.rentModeLabel());
        }
        tvPrice.setText(MONEY.format(t.getPrice()) + "đ");
    }

    /** Nhận chuyến đang hiển thị (transaction tránh nhận trùng). */
    private void acceptCurrent() {
        if (index < 0 || index >= waiting.size()) return;
        Trip trip = waiting.get(index);
        if (trip.getId() == null) return;
        db.runTransaction(tr -> {
            DocumentSnapshot snap = tr.get(db.collection("trips").document(trip.getId()));
            if (!Trip.STATUS_WAITING.equals(snap.getString("status"))) {
                throw new com.google.firebase.firestore.FirebaseFirestoreException("Đơn đã được nhận",
                        com.google.firebase.firestore.FirebaseFirestoreException.Code.ABORTED);
            }
            tr.update(db.collection("trips").document(trip.getId()),
                    "status", Trip.STATUS_RUNNING,
                    "driverId", uid,
                    "driverName", driverName != null ? driverName : "",
                    "acceptedAt", Timestamp.now());
            return null;
        }).addOnSuccessListener(x -> {
            if (!isAdded()) return;
            Toast.makeText(getContext(), "✅ Đã nhận chuyến! Xem ở tab Chuyến.", Toast.LENGTH_SHORT).show();
            loadWaiting();
            loadTodayStats();
        }).addOnFailureListener(e -> {
            if (!isAdded()) return;
            Toast.makeText(getContext(), "Không nhận được: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            loadWaiting();
        });
    }

    /** Doanh thu & số chuyến hoàn thành hôm nay. */
    private void loadTodayStats() {
        db.collection("trips").whereEqualTo("driverId", uid)
                .whereEqualTo("status", Trip.STATUS_COMPLETED).get()
                .addOnSuccessListener(snap -> {
                    if (!isAdded()) return;
                    Date start = startOfToday();
                    int count = 0;
                    double revenue = 0;
                    for (QueryDocumentSnapshot d : snap) {
                        Timestamp done = d.getTimestamp("completedAt");
                        if (done != null && done.toDate().after(start)) {
                            count++;
                            Double p = d.getDouble("price");
                            if (p != null) revenue += p;
                        }
                    }
                    setStatValue(getView(), R.id.stat_revenue, MONEY.format(revenue) + "đ");
                    setStatValue(getView(), R.id.stat_trips, String.valueOf(count));
                });
    }

    private void bindStat(View tile, int icon, int iconColor, String value, String label) {
        ImageView iv = tile.findViewById(R.id.iv_stat);
        iv.setImageResource(icon);
        iv.setColorFilter(ContextCompat.getColor(requireContext(), iconColor));
        ((TextView) tile.findViewById(R.id.tv_stat_value)).setText(value);
        ((TextView) tile.findViewById(R.id.tv_stat_label)).setText(label);
    }

    private void setStatValue(View root, int tileId, String value) {
        if (root == null) return;
        View tile = root.findViewById(tileId);
        if (tile != null) ((TextView) tile.findViewById(R.id.tv_stat_value)).setText(value);
    }

    private Date startOfToday() {
        Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTime();
    }

    private String safe(String s) { return s != null && !s.isEmpty() ? s : "--"; }
}
