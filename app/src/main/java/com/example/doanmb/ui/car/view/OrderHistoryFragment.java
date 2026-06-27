package com.example.doanmb.ui.car.view;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.doanmb.R;
import com.example.doanmb.ui.car.adapter.OrderHistoryAdapter;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Fragment lịch sử đơn hàng của khách hàng (customer).
 *
 * - Load tất cả orders có buyerId == currentUser.uid
 * - Map đủ field cần thiết: canReview, reviewed, driverId/sellerId, orderId, carId
 *   để OrderHistoryAdapter hiển thị nút "Đánh giá tài xế" đúng điều kiện.
 * - Bộ lọc nhanh: Tất cả / Chờ xác nhận
 *
 * Dùng trong tab "Đơn hàng" của màn hình customer (MainActivity hoặc ProfileFragment).
 *
 * Cách thêm vào navigation:
 *   getChildFragmentManager().beginTransaction()
 *       .replace(R.id.container, new OrderHistoryFragment())
 *       .commit();
 */
public class OrderHistoryFragment extends Fragment {

    private RecyclerView rvOrders;
    private ProgressBar progressOrders;
    private View layoutEmpty;
    private TextView tvFilterAll, tvFilterPending;

    private OrderHistoryAdapter adapter;
    private final List<Map<String, Object>> allOrders    = new ArrayList<>();
    private final List<Map<String, Object>> shownOrders  = new ArrayList<>();

    private FirebaseFirestore db;
    private String uid;

    // Bộ lọc hiện tại: null = tất cả, "pending" = chờ xác nhận
    private String activeFilter = null;

    // ─── lifecycle ────────────────────────────────────────────────────────────

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_order_history, container, false);

        db  = FirebaseFirestore.getInstance();
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        uid = user != null ? user.getUid() : "";

        rvOrders       = v.findViewById(R.id.rv_order_history);
        progressOrders = v.findViewById(R.id.progress_orders);
        layoutEmpty    = v.findViewById(R.id.layout_empty_orders);
        tvFilterAll    = v.findViewById(R.id.tv_filter_all);
        tvFilterPending= v.findViewById(R.id.tv_filter_pending);

        adapter = new OrderHistoryAdapter(shownOrders);
        rvOrders.setLayoutManager(new LinearLayoutManager(getContext()));
        rvOrders.setAdapter(adapter);

        tvFilterAll.setOnClickListener(x -> setFilter(null));
        tvFilterPending.setOnClickListener(x -> setFilter("pending"));

        return v;
    }

    @Override
    public void onResume() {
        super.onResume();
        // Reload mỗi khi quay lại (sau khi đánh giá xong, reviewed = true → ẩn nút)
        loadOrders();
    }

    // ─── load data ────────────────────────────────────────────────────────────

    private void loadOrders() {
        if (uid.isEmpty()) {
            showEmpty();
            return;
        }

        showLoading();

        db.collection("orders")
                .whereEqualTo("buyerId", uid)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener(snap -> {
                    if (!isAdded()) return;
                    allOrders.clear();

                    for (QueryDocumentSnapshot d : snap) {
                        Map<String, Object> map = new HashMap<>();

                        // ── Các field hiển thị cơ bản ──────────────────────
                        map.put("orderId",    d.getId());
                        map.put("type",       d.getString("type"));
                        map.put("status",     d.getString("status"));
                        map.put("carName",    d.getString("carName"));
                        map.put("carPrice",   d.getString("carPrice"));
                        map.put("note",       d.getString("note"));
                        map.put("createdAt",  d.getTimestamp("createdAt"));
                        map.put("carId",      d.getString("carId"));

                        // ── Field quan trọng cho nút đánh giá ──────────────
                        // canReview = true khi driver bấm "Hoàn thành chuyến"
                        Boolean canReview = d.getBoolean("canReview");
                        map.put("canReview", canReview != null && canReview);

                        // reviewed = true sau khi customer đã đánh giá
                        Boolean reviewed = d.getBoolean("reviewed");
                        map.put("reviewed", reviewed != null && reviewed);

                        // driverId: ưu tiên field driverId, fallback về sellerId
                        // (với xe có tài xế, sellerId chính là uid của driver)
                        String driverId = d.getString("driverId");
                        if (driverId == null || driverId.isEmpty()) {
                            driverId = d.getString("sellerId");
                        }
                        map.put("driverId", driverId != null ? driverId : "");

                        allOrders.add(map);
                    }

                    applyFilter();
                })
                .addOnFailureListener(e -> {
                    if (!isAdded()) return;
                    showEmpty();
                });
    }

    // ─── filter ───────────────────────────────────────────────────────────────

    private void setFilter(@Nullable String filter) {
        activeFilter = filter;
        updateFilterChips();
        applyFilter();
    }

    private void updateFilterChips() {
        boolean allSelected = activeFilter == null;
        // chip "Tất cả"
        tvFilterAll.setBackgroundTintList(
                android.content.res.ColorStateList.valueOf(
                        allSelected ? 0xFF1976D2 : 0xFFEEEEEE));
        tvFilterAll.setTextColor(allSelected ? 0xFFFFFFFF : 0xFF555555);
        // chip "Chờ xác nhận"
        boolean pendingSelected = "pending".equals(activeFilter);
        tvFilterPending.setBackgroundTintList(
                android.content.res.ColorStateList.valueOf(
                        pendingSelected ? 0xFFFF9800 : 0xFFEEEEEE));
        tvFilterPending.setTextColor(pendingSelected ? 0xFFFFFFFF : 0xFF555555);
    }

    private void applyFilter() {
        shownOrders.clear();
        for (Map<String, Object> order : allOrders) {
            if (activeFilter == null) {
                shownOrders.add(order);
            } else {
                String status = (String) order.get("status");
                if (activeFilter.equals(status)) {
                    shownOrders.add(order);
                }
            }
        }

        adapter.notifyDataSetChanged();

        if (shownOrders.isEmpty()) {
            showEmpty();
        } else {
            progressOrders.setVisibility(View.GONE);
            layoutEmpty.setVisibility(View.GONE);
            rvOrders.setVisibility(View.VISIBLE);
        }
    }

    // ─── UI helpers ───────────────────────────────────────────────────────────

    private void showLoading() {
        progressOrders.setVisibility(View.VISIBLE);
        layoutEmpty.setVisibility(View.GONE);
        rvOrders.setVisibility(View.GONE);
    }

    private void showEmpty() {
        progressOrders.setVisibility(View.GONE);
        rvOrders.setVisibility(View.GONE);
        layoutEmpty.setVisibility(View.VISIBLE);
    }
}