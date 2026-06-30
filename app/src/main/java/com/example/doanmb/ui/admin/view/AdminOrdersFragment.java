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
import com.example.doanmb.ui.admin.adapter.OrderAdminAdapter;
import com.example.doanmb.ui.admin.util.AdminTab;
import com.example.doanmb.data.repository.WalletRepository;
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
    /** Chặn thao tác trùng (double-tap) khi một lệnh ghi/đụng-tiền đang chạy. */
    private boolean processing = false;

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
        Button[] tabs = {btnTabPending, btnTabConfirmed, btnTabAll};
        AdminTab.select(tabs[active], tabs);
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
        if (processing) return;
        processing = true;
        Map<String, Object> update = new HashMap<>();
        update.put("status", "confirmed");
        db.collection("orders").document(orderId).update(update)
                .addOnSuccessListener(v -> {
                    processing = false;
                    if (!isAdded()) return;
                    Toast.makeText(getContext(), "✅ Đã xác nhận đơn hàng", Toast.LENGTH_SHORT).show();
                    loadOrders();
                })
                .addOnFailureListener(e -> {
                    processing = false;
                    if (!isAdded()) return;
                    Toast.makeText(getContext(), "Lỗi: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
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
        if (processing) return;
        processing = true;
        db.collection("orders").document(orderId).get().addOnSuccessListener(doc -> {
            if (!isAdded() || !doc.exists()) { processing = false; return; }
            String depositStatus = doc.getString("depositStatus");
            String sellerId       = doc.getString("sellerId");
            Long   deposit        = doc.getLong("depositAmount");
            String status         = doc.getString("status");

            // Chặn chia tiền lặp: chỉ xử lý đơn đang "confirmed"
            if (!"confirmed".equals(status)) {
                processing = false;
                if (isAdded()) Toast.makeText(getContext(), "Đơn không ở trạng thái chờ hoàn thành", Toast.LENGTH_SHORT).show();
                loadOrders();
                return;
            }

            // Đơn không có cọc giữ qua ví -> chỉ đánh dấu hoàn thành
            if (!"held".equals(depositStatus) || sellerId == null || sellerId.isEmpty()
                    || deposit == null || deposit <= 0) {
                markCompleted(orderId, null);
                return;
            }

            WalletRepository.settle(sellerId, deposit, orderId,
                    new WalletRepository.Callback() {
                        @Override public void onSuccess() { markCompleted(orderId, "settled"); }
                        @Override public void onError(String message) {
                            processing = false;
                            if (!isAdded()) return;
                            Toast.makeText(getContext(), "Lỗi chia tiền: " + message, Toast.LENGTH_SHORT).show();
                        }
                    });
        }).addOnFailureListener(e -> {
            processing = false;
            if (isAdded()) Toast.makeText(getContext(), "Lỗi: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        });
    }

    private void markCompleted(String orderId, String newDepositStatus) {
        Map<String, Object> update = new HashMap<>();
        update.put("status", "completed");
        if (newDepositStatus != null) update.put("depositStatus", newDepositStatus);
        db.collection("orders").document(orderId).update(update)
                .addOnSuccessListener(v -> {
                    processing = false;
                    if (!isAdded()) return;
                    Toast.makeText(getContext(), "✅ Đơn đã hoàn thành & chia tiền", Toast.LENGTH_SHORT).show();
                    loadOrders();
                })
                .addOnFailureListener(e -> {
                    processing = false;
                    if (isAdded()) Toast.makeText(getContext(), "Lỗi cập nhật đơn: " + e.getMessage(), Toast.LENGTH_SHORT).show();
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
        if (processing) return;
        processing = true;
        // Lấy carId trong đơn để reset trạng thái xe về active
        db.collection("orders").document(orderId).get()
                .addOnSuccessListener(doc -> {
                    if (!doc.exists()) { processing = false; return; }
                    String carId          = doc.getString("carId");
                    String buyerId        = doc.getString("buyerId");
                    String depositStatus  = doc.getString("depositStatus");
                    String status         = doc.getString("status");
                    Long   deposit        = doc.getLong("depositAmount");

                    // Chỉ hủy được đơn đang chờ/đã xác nhận; chặn hoàn cọc lặp
                    if (!"pending".equals(status) && !"confirmed".equals(status)) {
                        processing = false;
                        if (isAdded()) Toast.makeText(getContext(), "Đơn này không thể hủy", Toast.LENGTH_SHORT).show();
                        loadOrders();
                        return;
                    }

                    Map<String, Object> orderUpdate = new HashMap<>();
                    orderUpdate.put("status", "cancelled");

                    if (carId != null && !carId.isEmpty()) {
                        Map<String, Object> carUpdate = new HashMap<>();
                        carUpdate.put("status", "active");
                        db.collection("cars").document(carId).update(carUpdate);
                    }

                    // Còn giữ cọc -> hoàn lại 100% vào ví khách
                    final boolean refunded = "held".equals(depositStatus)
                            && buyerId != null && deposit != null && deposit > 0;
                    if (refunded) {
                        orderUpdate.put("depositStatus", "refunded");
                        WalletRepository.refund(buyerId, deposit, orderId, null);
                    }

                    db.collection("orders").document(orderId).update(orderUpdate)
                            .addOnSuccessListener(v -> {
                                processing = false;
                                if (!isAdded()) return;
                                Toast.makeText(getContext(), "Đã hủy đơn hàng"
                                        + (refunded ? " & hoàn cọc cho khách" : ""), Toast.LENGTH_SHORT).show();
                                loadOrders();
                            })
                            .addOnFailureListener(e -> {
                                processing = false;
                                if (isAdded()) Toast.makeText(getContext(), "Lỗi hủy đơn: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                            });
                })
                .addOnFailureListener(e -> {
                    processing = false;
                    if (isAdded()) Toast.makeText(getContext(), "Lỗi: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    @Override
    public void onResume() {
        super.onResume();
        loadOrders();
    }
}
