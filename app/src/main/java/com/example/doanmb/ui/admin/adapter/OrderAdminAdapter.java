package com.example.doanmb.ui.admin.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.doanmb.R;

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

        String carName   = getStr(order, "carName",     "Xe không xác định");
        String buyerName = getStr(order, "buyerName",   getStr(order, "renterName", getStr(order, "buyerId", "--")));
        String sellerName= getStr(order, "sellerName",  getStr(order, "sellerId", "--"));
        String type      = getStr(order, "type",        "Mua xe");
        String status    = getStr(order, "status",      "pending");

        holder.tvCarName.setText(carName);
        holder.tvBuyer.setText(buyerName);
        holder.tvSeller.setText(sellerName.length() > 20 ? sellerName.substring(0, 20) + "…" : sellerName);
        holder.tvType.setText(type);
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
        return (v != null) ? v.toString() : def;
    }

    @Override
    public int getItemCount() { return orders.size(); }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvCarName, tvBuyer, tvSeller, tvType, tvStatus;
        LinearLayout layoutActions;
        Button btnConfirm, btnCancel;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvCarName      = itemView.findViewById(R.id.tv_order_car_name);
            tvBuyer        = itemView.findViewById(R.id.tv_order_buyer);
            tvSeller       = itemView.findViewById(R.id.tv_order_seller);
            tvType         = itemView.findViewById(R.id.tv_order_type);
            tvStatus       = itemView.findViewById(R.id.tv_order_status);
            layoutActions  = itemView.findViewById(R.id.layout_order_actions);
            btnConfirm     = itemView.findViewById(R.id.btn_confirm_order);
            btnCancel      = itemView.findViewById(R.id.btn_cancel_order);
        }
    }
}
