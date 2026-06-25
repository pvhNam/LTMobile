package com.example.doanmb.ui.admin.view;

import android.app.AlertDialog;
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

import com.example.doanmb.R;
import com.example.doanmb.core.helper.WalletHelper;
import com.example.doanmb.ui.admin.adapter.OrderAdminAdapter;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AdminOrdersFragment extends Fragment {

    private static final int TAB_PENDING   = 0;
    private static final int TAB_CONFIRMED = 1;
    private static final int TAB_ALL       = 2;

    private RecyclerView rvOrders;
    private TextView tvCount, tvEmpty;
    private Button btnTabPending, btnTabConfirmed, btnTabAll;
    private OrderAdminAdapter adapter;
    private final List<Map<String, Object>> orderList = new ArrayList<>();
    private final List<String> orderIds = new ArrayList<>();
    private FirebaseFirestore db;
    private int currentTab = TAB_PENDING;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_admin_orders, container, false);
        db = FirebaseFirestore.getInstance();

        rvOrders       = view.findViewById(R.id.rv_admin_orders);
        tvCount        = view.findViewById(R.id.tv_admin_order_count);
        tvEmpty        = view.findViewById(R.id.tv_admin_empty_orders);
        btnTabPending  = view.findViewById(R.id.btn_order_tab_pending);
        btnTabConfirmed= view.findViewById(R.id.btn_order_tab_confirmed);
        btnTabAll      = view.findViewById(R.id.btn_order_tab_all);

        adapter = new OrderAdminAdapter(orderList, orderIds, new OrderAdminAdapter.OnOrderActionListener() {
            @Override public void onConfirm(String orderId)  { confirmOrder(orderId); }
            @Override public void onComplete(String orderId) { askCompleteOrder(orderId); }
            @Override public void onCancel(String orderId)   { askCancelOrder(orderId); }
        });
        rvOrders.setLayoutManager(new LinearLayoutManager(getContext()));
        rvOrders.setAdapter(adapter);

        btnTabPending.setOnClickListener(v  -> switchTab(TAB_PENDING));
        btnTabConfirmed.setOnClickListener(v-> switchTab(TAB_CONFIRMED));
        btnTabAll.setOnClickListener(v      -> switchTab(TAB_ALL));

        applyTabStyle(TAB_PENDING);
        loadOrders();
        return view;
    }

    private void switchTab(int tab) {
        currentTab = tab;
        applyTabStyle(tab);
        loadOrders();
    }

    private void applyTabStyle(int active) {
        int activeColor  = 0xFFC62828;
        int activeText   = 0xFFFFFFFF;
        int inactiveColor= 0xFFFFCDD2;
        int inactiveText = 0xFFC62828;

        int[] colors = {inactiveColor, inactiveColor, inactiveColor};
        int[] texts  = {inactiveText,  inactiveText,  inactiveText};
        colors[active] = activeColor;
        texts[active]  = activeText;

        btnTabPending.setBackgroundTintList(android.content.res.ColorStateList.valueOf(colors[0]));
        btnTabPending.setTextColor(texts[0]);
        btnTabConfirmed.setBackgroundTintList(android.content.res.ColorStateList.valueOf(colors[1]));
        btnTabConfirmed.setTextColor(texts[1]);
        btnTabAll.setBackgroundTintList(android.content.res.ColorStateList.valueOf(colors[2]));
        btnTabAll.setTextColor(texts[2]);
    }

    private void loadOrders() {
        com.google.firebase.firestore.Query query = db.collection("orders");
        if (currentTab == TAB_PENDING) {
            query = query.whereEqualTo("status", "pending");
        } else if (currentTab == TAB_CONFIRMED) {
            query = query.whereEqualTo("status", "confirmed");
        }
        query.get().addOnSuccessListener(this::processDocs);
    }

    private void processDocs(QuerySnapshot snapshots) {
        if (!isAdded()) return;
        orderList.clear();
        orderIds.clear();
        for (QueryDocumentSnapshot doc : snapshots) {
            orderList.add(doc.getData());
            orderIds.add(doc.getId());
        }
        adapter.updateList(orderList, orderIds);
        String[] labels = {"chờ xác nhận", "đã xác nhận", "tổng đơn"};
        tvCount.setText(orderList.size() + " " + labels[currentTab]);
        tvEmpty.setVisibility(orderList.isEmpty() ? View.VISIBLE : View.GONE);
        rvOrders.setVisibility(orderList.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private void confirmOrder(String orderId) {
        Map<String, Object> update = new HashMap<>();
        update.put("status", "confirmed");
        db.collection("orders").document(orderId).update(update)
                .addOnSuccessListener(v -> {
                    if (!isAdded()) return;
                    Toast.makeText(getContext(), "✅ Đã xác nhận đơn hàng", Toast.LENGTH_SHORT).show();
                    loadOrders();
                })
                .addOnFailureListener(e -> Toast.makeText(getContext(), "Lỗi: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    private void askCompleteOrder(String orderId) {
        new AlertDialog.Builder(requireContext())
                .setTitle("Hoàn thành đơn")
                .setMessage("Xác nhận đơn đã hoàn thành?\nApp sẽ trừ 15% hoa hồng từ tiền cọc và trả 85% còn lại về ví chủ xe/tài xế.")
                .setPositiveButton("Hoàn thành", (d, w) -> completeOrder(orderId))
                .setNegativeButton("Đóng", null)
                .show();
    }

    private void completeOrder(String orderId) {
        db.collection("orders").document(orderId).get().addOnSuccessListener(doc -> {
            if (!isAdded() || !doc.exists()) return;
            String depositStatus = doc.getString("depositStatus");
            String sellerId       = doc.getString("sellerId");
            Long   deposit        = doc.getLong("depositAmount");

            // Đơn không có cọc giữ qua ví -> chỉ đánh dấu hoàn thành
            if (!"held".equals(depositStatus) || sellerId == null || sellerId.isEmpty()
                    || deposit == null || deposit <= 0) {
                markCompleted(orderId, null);
                return;
            }

            WalletHelper.settle(sellerId, deposit, orderId,
                    new WalletHelper.Callback() {
                        @Override public void onSuccess() { markCompleted(orderId, "settled"); }
                        @Override public void onError(String message) {
                            if (!isAdded()) return;
                            Toast.makeText(getContext(), "Lỗi chia tiền: " + message, Toast.LENGTH_SHORT).show();
                        }
                    });
        });
    }

    private void markCompleted(String orderId, String newDepositStatus) {
        Map<String, Object> update = new HashMap<>();
        update.put("status", "completed");
        if (newDepositStatus != null) update.put("depositStatus", newDepositStatus);
        db.collection("orders").document(orderId).update(update)
                .addOnSuccessListener(v -> {
                    if (!isAdded()) return;
                    Toast.makeText(getContext(), "✅ Đơn đã hoàn thành & chia tiền", Toast.LENGTH_SHORT).show();
                    loadOrders();
                });
    }

    private void askCancelOrder(String orderId) {
        new AlertDialog.Builder(requireContext())
                .setTitle("Hủy đơn hàng")
                .setMessage("Bạn có chắc muốn hủy đơn hàng này không?\nHành động này sẽ trả xe về trạng thái đang bán.")
                .setPositiveButton("Hủy đơn", (d, w) -> cancelOrder(orderId))
                .setNegativeButton("Không", null)
                .show();
    }

    private void cancelOrder(String orderId) {
        // Lấy carId trong đơn để reset trạng thái xe về active
        db.collection("orders").document(orderId).get()
                .addOnSuccessListener(doc -> {
                    String carId          = doc.getString("carId");
                    String buyerId        = doc.getString("buyerId");
                    String depositStatus  = doc.getString("depositStatus");
                    Long   deposit        = doc.getLong("depositAmount");

                    Map<String, Object> orderUpdate = new HashMap<>();
                    orderUpdate.put("status", "cancelled");

                    if (carId != null && !carId.isEmpty()) {
                        Map<String, Object> carUpdate = new HashMap<>();
                        carUpdate.put("status", "active");
                        db.collection("cars").document(carId).update(carUpdate);
                    }

                    // Còn giữ cọc -> hoàn lại 100% vào ví khách
                    if ("held".equals(depositStatus) && buyerId != null && deposit != null && deposit > 0) {
                        orderUpdate.put("depositStatus", "refunded");
                        WalletHelper.refund(buyerId, deposit, orderId, null);
                    }

                    db.collection("orders").document(orderId).update(orderUpdate);

                    if (!isAdded()) return;
                    Toast.makeText(getContext(), "Đã hủy đơn hàng" +
                            ("held".equals(depositStatus) ? " & hoàn cọc cho khách" : ""), Toast.LENGTH_SHORT).show();
                    loadOrders();
                });
    }

    @Override
    public void onResume() {
        super.onResume();
        loadOrders();
    }
}
