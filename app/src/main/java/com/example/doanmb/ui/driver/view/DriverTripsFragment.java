package com.example.doanmb.ui.driver.view;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.doanmb.R;
import com.example.doanmb.ui.driver.adapter.TripAdapter;
import com.example.doanmb.data.model.Trip;
import com.google.android.material.tabs.TabLayout;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.List;

import de.hdodenhof.circleimageview.CircleImageView;

public class DriverTripsFragment extends Fragment implements TripAdapter.OnTripActionListener {

    private static class OrderItem {
        String orderId, status, carName, carPrice,
                renterName, renterPhone, note, type, carId, buyerId;
        Timestamp createdAt;

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

    private class OrderDriverAdapter extends RecyclerView.Adapter<OrderDriverAdapter.VH> {
        private final List<OrderItem> list;
        OrderDriverAdapter(List<OrderItem> list) { this.list = list; }

        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_driver_order, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int pos) {
            OrderItem o = list.get(pos);
            java.text.SimpleDateFormat fmt =
                    new java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.getDefault());

            setText(h.tvCarName,  o.carName);
            setText(h.tvPrice,    o.carPrice);
            setText(h.tvStatus,   labelFor(o.status));
            styleStatusPill(h.tvStatus, o.status);
            setText(h.tvRenter,
                    (o.renterName  != null ? o.renterName  : "")
                            + (o.renterPhone != null && !o.renterPhone.isEmpty() ? "  |  " + o.renterPhone : ""));

            if (h.tvNote != null) {
                boolean hasNote = o.note != null && !o.note.isEmpty();
                h.tvNote.setVisibility(hasNote ? View.VISIBLE : View.GONE);
                if (hasNote) h.tvNote.setText(o.note);
            }
            if (h.tvDate != null && o.createdAt != null)
                h.tvDate.setText("📅 " + fmt.format(o.createdAt.toDate()));

            boolean isAccepted   = "accepted".equals(o.status);
            boolean isInProgress = "in_progress".equals(o.status);

            setVisible(h.btnStart,    isAccepted);
            setVisible(h.btnComplete, isInProgress);

            if (h.btnStart    != null) h.btnStart.setOnClickListener(v    -> startOrder(o));
            if (h.btnComplete != null) h.btnComplete.setOnClickListener(v -> completeOrder(o));
        }

        @Override public int getItemCount() { return list.size(); }

        class VH extends RecyclerView.ViewHolder {
            TextView tvCarName, tvRenter, tvPrice, tvNote, tvDate, tvStatus;
            Button   btnStart, btnComplete;
            VH(@NonNull View v) {
                super(v);
                tvCarName   = v.findViewById(R.id.tv_do_car_name);
                tvRenter    = v.findViewById(R.id.tv_do_renter);
                tvPrice     = v.findViewById(R.id.tv_do_price);
                tvNote      = v.findViewById(R.id.tv_do_note);
                tvDate      = v.findViewById(R.id.tv_do_date);
                tvStatus    = v.findViewById(R.id.tv_do_status);
                btnStart    = v.findViewById(R.id.btn_do_start);
                btnComplete = v.findViewById(R.id.btn_do_complete);
            }
        }
    }

    private TextView     tvName, tvSectionTitle, tvCountdown, tvEmpty;
    private CircleImageView ivAvatar;
    private TabLayout    tabs;
    private RecyclerView rv;
    private OrderDriverAdapter orderAdapter;

    private TripAdapter legacyAdapter;

    private final List<OrderItem> shown    = new ArrayList<>();
    private final List<OrderItem> accepted = new ArrayList<>();
    private final List<OrderItem> running  = new ArrayList<>();
    private final List<OrderItem> history  = new ArrayList<>();

    private FirebaseFirestore db;
    private String uid, driverName;
    private int currentTab = 0;

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_driver_trips, container, false);
        db  = FirebaseFirestore.getInstance();
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        uid = user != null ? user.getUid() : "";

        tvName         = v.findViewById(R.id.tv_dh_name);
        ivAvatar       = v.findViewById(R.id.iv_dh_avatar);
        tvSectionTitle = v.findViewById(R.id.tv_section_title);
        tvCountdown    = v.findViewById(R.id.tv_countdown);
        tvEmpty        = v.findViewById(R.id.tv_empty);
        tabs           = v.findViewById(R.id.tab_trips);
        rv             = v.findViewById(R.id.rv_trips);

        orderAdapter = new OrderDriverAdapter(shown);
        rv.setLayoutManager(new LinearLayoutManager(getContext()));
        rv.setAdapter(orderAdapter);

        tabs.addTab(tabs.newTab().setText("Chuyến mới"));
        tabs.addTab(tabs.newTab().setText("Đang chạy"));
        tabs.addTab(tabs.newTab().setText("Lịch sử"));
        tabs.addTab(tabs.newTab().setText("Đặt trước"));
        tabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override public void onTabSelected(TabLayout.Tab tab)   { currentTab = tab.getPosition(); renderTab(); }
            @Override public void onTabUnselected(TabLayout.Tab tab) {}
            @Override public void onTabReselected(TabLayout.Tab tab) {}
        });

        safeClick(v, R.id.row_running,   () -> selectTab(1));
        safeClick(v, R.id.row_history,   () -> selectTab(2));
        safeClick(v, R.id.row_scheduled, () -> selectTab(3));

        return v;
    }

    @Override public void onResume() {
        super.onResume();
        loadAll();
    }

    private void loadAll() {
        if (uid.isEmpty()) return;
        db.collection("users").document(uid).get().addOnSuccessListener(doc -> {
            if (!isAdded()) return;
            driverName = doc.getString("name");
            if (tvName != null) tvName.setText(driverName != null ? driverName : "Tài xế");
            String avatar = doc.getString("avatarUrl");
            if (avatar != null && !avatar.isEmpty() && ivAvatar != null)
                Glide.with(this).load(avatar).into(ivAvatar);
            loadOrders();
        });
    }

    private void loadOrders() {
        // Lấy tất cả orders có sellerId == uid (trừ pending, đã xử lý ở Driver Home)
        db.collection("orders")
                .whereEqualTo("sellerId", uid)
                .get()
                .addOnSuccessListener(snap -> {
                    if (!isAdded()) return;
                    accepted.clear();
                    running.clear();
                    history.clear();
                    for (QueryDocumentSnapshot d : snap) {
                        String st = d.getString("status");
                        if (st == null || "pending".equals(st)) continue; // pending → Driver Home
                        OrderItem o = OrderItem.from(d);
                        switch (st) {
                            case "accepted":
                                accepted.add(o);
                                break;
                            case "in_progress":
                                running.add(o);
                                break;
                            case "completed":
                            case "rejected":
                            case "cancelled":
                                history.add(o);
                                break;
                        }
                    }
                    renderTab();
                });
    }

    private void renderTab() {
        shown.clear();
        switch (currentTab) {
            case 0:
                shown.addAll(accepted);
                if (tvSectionTitle != null) tvSectionTitle.setText("Chuyến mới");
                if (tvCountdown    != null) tvCountdown.setVisibility(View.GONE);
                if (tvEmpty        != null) tvEmpty.setText("Chưa có chuyến đã nhận");
                break;
            case 1:
                shown.addAll(running);
                if (tvSectionTitle != null) tvSectionTitle.setText("Đang chạy");
                if (tvCountdown    != null) tvCountdown.setVisibility(View.GONE);
                if (tvEmpty        != null) tvEmpty.setText("Không có chuyến đang chạy");
                break;
            case 2:
                shown.addAll(history);
                if (tvSectionTitle != null) tvSectionTitle.setText("Lịch sử chuyến");
                if (tvCountdown    != null) tvCountdown.setVisibility(View.GONE);
                if (tvEmpty        != null) tvEmpty.setText("Chưa có lịch sử chuyến");
                break;
            default:
                if (tvSectionTitle != null) tvSectionTitle.setText("Chuyến đặt trước");
                if (tvCountdown    != null) tvCountdown.setVisibility(View.GONE);
                if (tvEmpty        != null) tvEmpty.setText("Chưa có chuyến đặt trước");
        }
        orderAdapter.notifyDataSetChanged();
        if (tvEmpty != null)
            tvEmpty.setVisibility(shown.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void startOrder(OrderItem o) {
        db.collection("orders").document(o.orderId)
                .update("status", "in_progress", "startedAt", Timestamp.now())
                .addOnSuccessListener(x -> {
                    if (!isAdded()) return;
                    Toast.makeText(getContext(), "🚗 Đã bắt đầu chuyến!", Toast.LENGTH_SHORT).show();
                    loadOrders();
                    selectTab(1);
                })
                .addOnFailureListener(e -> {
                    if (!isAdded()) return;
                    Toast.makeText(getContext(), "Lỗi: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    private void completeOrder(OrderItem o) {
        db.collection("orders").document(o.orderId)
                .update(
                        "status",      "completed",
                        "completedAt", Timestamp.now(),
                        "canReview",   true,
                        "reviewed",    false
                )
                .addOnSuccessListener(x -> {
                    if (!isAdded()) return;
                    Toast.makeText(getContext(), "🏁 Đã hoàn thành chuyến!", Toast.LENGTH_SHORT).show();
                    createReviewNotification(o);
                    loadOrders();
                    selectTab(2);
                })
                .addOnFailureListener(e -> {
                    if (!isAdded()) return;
                    Toast.makeText(getContext(), "Lỗi: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    private void createReviewNotification(OrderItem o) {
        if (o.buyerId == null || o.buyerId.isEmpty()) return;

        java.util.Map<String, Object> notif = new java.util.HashMap<>();
        notif.put("userId",          o.buyerId);          // receiverId = Customer
        notif.put("senderId",        uid);                 // Driver
        notif.put("orderId",         o.orderId);
        notif.put("carId",           o.carId != null ? o.carId : "");
        notif.put("driverId",        uid);
        notif.put("type",            "review_driver");
        notif.put("title",           "Tài xế đã hoàn thành chuyến xe!");
        notif.put("body",            "Mời bạn đánh giá tài xế.");
        notif.put("read",            false);
        notif.put("actionCompleted", false);
        notif.put("createdAt",       Timestamp.now());

        db.collection("notifications").add(notif);
    }

    @Override public void onPrimary(Trip trip) { /* xử lý ở Driver Home */ }
    @Override public void onSkip(Trip trip)    { /* xử lý ở Driver Home */ }

    private void selectTab(int i) {
        if (tabs == null) return;
        TabLayout.Tab tab = tabs.getTabAt(i);
        if (tab != null) tab.select();
    }

    private static String labelFor(String s) {
        if (s == null) return "";
        switch (s) {
            case "accepted":    return "Chờ bắt đầu";
            case "in_progress": return "Đang chạy";
            case "completed":   return "Hoàn thành";
            case "rejected":    return "Đã từ chối";
            case "cancelled":   return "Đã hủy";
            default:            return s;
        }
    }

    /** Tô màu pill trạng thái theo từng trạng thái đơn. */
    private static void styleStatusPill(TextView tv, String status) {
        if (tv == null) return;
        int bgRes, fgRes;
        switch (status == null ? "" : status) {
            case "in_progress":
            case "completed":
                bgRes = R.color.driver_green_bg;   fgRes = R.color.driver_green;   break;
            case "rejected":
            case "cancelled":
                bgRes = R.color.driver_red_bg;     fgRes = R.color.driver_red;     break;
            case "accepted":
            default:
                bgRes = R.color.driver_surface_blue; fgRes = R.color.driver_primary;
        }
        android.content.Context c = tv.getContext();
        tv.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                androidx.core.content.ContextCompat.getColor(c, bgRes)));
        tv.setTextColor(androidx.core.content.ContextCompat.getColor(c, fgRes));
    }

    private static void setText(TextView tv, String text) {
        if (tv != null) tv.setText(text != null ? text : "");
    }

    private static void setVisible(View v, boolean show) {
        if (v != null) v.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    private static void safeClick(View root, int id, Runnable action) {
        View v = root.findViewById(id);
        if (v != null) v.setOnClickListener(x -> action.run());
    }
}