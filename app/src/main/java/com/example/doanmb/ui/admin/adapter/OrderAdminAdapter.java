package com.example.doanmb.ui.admin.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.doanmb.R;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class OrderAdminAdapter extends RecyclerView.Adapter<OrderAdminAdapter.ViewHolder> {

    public interface OnOrderActionListener {
        void onConfirm(String orderId);
        void onComplete(String orderId);
        void onCancel(String orderId);
    }

    private List<Map<String, Object>> orders;
    private List<String> orderIds;
    private OnOrderActionListener listener;

    /** Cache tên user theo id để khỏi tra Firestore lặp lại khi cuộn danh sách. */
    private static final Map<String, String> NAME_CACHE = new HashMap<>();

    public OrderAdminAdapter(List<Map<String, Object>> orders, List<String> orderIds) {
        this.orders = orders;
        this.orderIds = orderIds;
    }

    public OrderAdminAdapter(List<Map<String, Object>> orders, List<String> orderIds, OnOrderActionListener listener) {
        this.orders = orders;
        this.orderIds = orderIds;
        this.listener = listener;
    }

    public void updateList(List<Map<String, Object>> newOrders, List<String> newIds) {
        this.orders = newOrders;
        this.orderIds = newIds;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_order_admin, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Map<String, Object> order = orders.get(position);
        String orderId = orderIds.get(position);
        Context ctx = holder.itemView.getContext();

        holder.itemView.setOnClickListener(v -> {
            android.content.Intent i = new android.content.Intent(ctx,
                    com.example.doanmb.ui.activity.AdminOrderDetailActivity.class);
            i.putExtra(com.example.doanmb.ui.activity.AdminOrderDetailActivity.EXTRA_ORDER_ID, orderId);
            ctx.startActivity(i);
        });

        String carName   = getStr(order, "carName",     "Xe không xác định");
        String buyerName = getStr(order, "renterName",  getStr(order, "buyerName", null));
        String sellerName= getStr(order, "sellerName",  null);
        String status    = getStr(order, "status",      "pending");

        holder.tvCarName.setText(carName);
        // Đơn cũ/đơn "Mua xe" chỉ lưu id -> tra tên từ collection "users"
        bindName(holder.tvBuyer,  buyerName,  getStr(order, "buyerId",  null), false);
        bindName(holder.tvSeller, sellerName, getStr(order, "sellerId", null), true);
        applyStatusStyle(holder.tvStatus, status);

        boolean isPending   = "pending".equals(status);
        boolean isConfirmed = "confirmed".equals(status);
        if (listener != null && (isPending || isConfirmed)) {
            holder.layoutActions.setVisibility(View.VISIBLE);
            if (isPending) {
                holder.btnConfirm.setText("Xác nhận");
                holder.btnConfirm.setOnClickListener(v -> listener.onConfirm(orderId));
            } else {
                holder.btnConfirm.setText("Hoàn thành");
                holder.btnConfirm.setOnClickListener(v -> listener.onComplete(orderId));
            }
            holder.btnCancel.setOnClickListener(v -> listener.onCancel(orderId));
        } else {
            holder.layoutActions.setVisibility(View.GONE);
        }
    }

    private void applyStatusStyle(TextView tv, String status) {
        switch (status) {
            case "pending":
                tv.setText("Chờ xác nhận");
                tv.setBackgroundColor(0xFFFFF3E0);
                tv.setTextColor(0xFFE65100);
                break;
            case "confirmed":
                tv.setText("Đã xác nhận");
                tv.setBackgroundColor(0xFFE8F5E9);
                tv.setTextColor(0xFF2E7D32);
                break;
            case "completed":
                tv.setText("Hoàn thành");
                tv.setBackgroundColor(0xFFE3F2FD);
                tv.setTextColor(0xFF1565C0);
                break;
            case "rejected":
            case "cancelled":
                tv.setText("Đã hủy");
                tv.setBackgroundColor(0xFFFFCDD2);
                tv.setTextColor(0xFFC62828);
                break;
            default:
                tv.setText(status);
                tv.setBackgroundColor(0xFFEEEEEE);
                tv.setTextColor(0xFF757575);
        }
    }

    private String getStr(Map<String, Object> map, String key, String def) {
        Object v = map.get(key);
        return (v != null && !v.toString().isEmpty()) ? v.toString() : def;
    }

    /**
     * Hiển thị tên người giao dịch. Nếu đơn đã có sẵn tên thì dùng luôn, ngược lại
     * tra tên trong collection "users" theo id (không hiển thị id thô).
     * Dùng setTag để tránh sai tên do RecyclerView tái sử dụng view khi cuộn.
     */
    private void bindName(TextView tv, String name, String userId, boolean truncate) {
        if (name != null && !name.isEmpty()) {
            tv.setTag(null);
            tv.setText(truncate ? truncate(name) : name);
            return;
        }
        if (userId == null || userId.isEmpty()) {
            tv.setTag(null);
            tv.setText("--");
            return;
        }
        String cached = NAME_CACHE.get(userId);
        if (cached != null) {
            tv.setTag(null);
            tv.setText(truncate ? truncate(cached) : cached);
            return;
        }
        tv.setTag(userId);
        tv.setText("Đang tải…");
        FirebaseFirestore.getInstance().collection("users").document(userId).get()
                .addOnSuccessListener(u -> {
                    String fetched = u.exists() ? u.getString("name") : null;
                    if (fetched == null || fetched.isEmpty()) fetched = "Ẩn danh";
                    NAME_CACHE.put(userId, fetched);
                    if (userId.equals(tv.getTag())) {
                        tv.setText(truncate ? truncate(fetched) : fetched);
                    }
                })
                .addOnFailureListener(e -> {
                    if (userId.equals(tv.getTag())) tv.setText("Ẩn danh");
                });
    }

    private static String truncate(String s) {
        return s.length() > 20 ? s.substring(0, 20) + "…" : s;
    }

    @Override
    public int getItemCount() { return orders.size(); }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvCarName, tvBuyer, tvSeller, tvStatus;
        LinearLayout layoutActions;
        Button btnConfirm, btnCancel;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvCarName      = itemView.findViewById(R.id.tv_order_car_name);
            tvBuyer        = itemView.findViewById(R.id.tv_order_buyer);
            tvSeller       = itemView.findViewById(R.id.tv_order_seller);
            tvStatus       = itemView.findViewById(R.id.tv_order_status);
            layoutActions  = itemView.findViewById(R.id.layout_order_actions);
            btnConfirm     = itemView.findViewById(R.id.btn_confirm_order);
            btnCancel      = itemView.findViewById(R.id.btn_cancel_order);
        }
    }
}
