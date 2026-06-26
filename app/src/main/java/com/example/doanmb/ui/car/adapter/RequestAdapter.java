package com.example.doanmb.ui.car.adapter;

import android.content.res.ColorStateList;
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

public class RequestAdapter extends RecyclerView.Adapter<RequestAdapter.RequestViewHolder> {

    public interface OnActionListener {
        void onConfirm(String orderId, String carId, Map<String, Object> order);
        void onReject(String orderId, String carId);
    }

    private List<Map<String, Object>> orderList;
    private List<String> orderIds;
    private OnActionListener listener;

    public RequestAdapter(List<Map<String, Object>> orderList, List<String> orderIds, OnActionListener listener) {
        this.orderList = orderList;
        this.orderIds = orderIds;
        this.listener = listener;
    }

    @NonNull
    @Override
    public RequestViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_request, parent, false);
        return new RequestViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull RequestViewHolder holder, int position) {
        Map<String, Object> order = orderList.get(position);
        String orderId = orderIds.get(position);
        String status = (String) order.get("status");
        String type = (String) order.get("type");
        String carName = (String) order.get("carName");
        String carPrice = (String) order.get("carPrice");
        String carId = (String) order.get("carId");
        String renterName = (String) order.get("renterName");
        String renterPhone = (String) order.get("renterPhone");
        String cccd = (String) order.get("cccd");
        String startDate = (String) order.get("startDate");
        String days = (String) order.get("days");
        String note = (String) order.get("note");
        String buyerId = (String) order.get("buyerId");

        // Loại yêu cầu
        holder.tvRequestType.setText(type != null ? type : "Yêu cầu");
        if ("Thuê xe".equals(type)) {
            holder.tvRequestType.setBackgroundTintList(ColorStateList.valueOf(0xFF1976D2));
        } else {
            holder.tvRequestType.setBackgroundTintList(ColorStateList.valueOf(0xFF4CAF50));
        }

        // Tên xe + giá
        holder.tvRequestCarName.setText(carName != null ? carName : "Không rõ");
        holder.tvRequestCarPrice.setText(carPrice != null ? carPrice : "");

        // Trạng thái
        if ("confirmed".equals(status)) {
            holder.tvRequestStatus.setText("✅ Đã xác nhận");
            holder.tvRequestStatus.setTextColor(0xFF4CAF50);
            holder.layoutActionButtons.setVisibility(View.GONE);
            holder.tvDoneLabel.setVisibility(View.VISIBLE);
        } else if ("rejected".equals(status)) {
            holder.tvRequestStatus.setText("❌ Đã từ chối");
            holder.tvRequestStatus.setTextColor(0xFFF44336);
            holder.layoutActionButtons.setVisibility(View.GONE);
            holder.tvDoneLabel.setVisibility(View.VISIBLE);
        } else if ("holding".equals(status)) {
            holder.tvRequestStatus.setText("⏳ Đang giữ chỗ (15 phút)");
            holder.tvRequestStatus.setTextColor(0xFFFF9800);
            holder.layoutActionButtons.setVisibility(View.VISIBLE);
            holder.tvDoneLabel.setVisibility(View.GONE);
        } else {
            holder.tvRequestStatus.setText("⏳ Chờ xác nhận");
            holder.tvRequestStatus.setTextColor(0xFFFF9800);
            holder.layoutActionButtons.setVisibility(View.VISIBLE);
            holder.tvDoneLabel.setVisibility(View.GONE);
        }

        // Thông tin người mua - load từ Firestore nếu có buyerId
        if (renterName != null && !renterName.isEmpty()) {
            holder.tvBuyerInfo.setText("👤  Người thuê: " + renterName);
            holder.tvBuyerPhone.setText("📞  " + (renterPhone != null ? renterPhone : ""));
        } else {
            holder.tvBuyerInfo.setText("👤  Người mua: " + (buyerId != null ? buyerId.substring(0, 8) + "..." : "Không rõ"));
            holder.tvBuyerPhone.setText("📞  Xem thông tin trong hệ thống");
        }

        // CCCD + ngày thuê (chỉ hiện khi thuê xe)
        if ("Thuê xe".equals(type)) {
            if (cccd != null && !cccd.isEmpty()) {
                holder.tvBuyerCCCD.setVisibility(View.VISIBLE);
                holder.tvBuyerCCCD.setText("🪪  CCCD: " + cccd);
            }
            if (startDate != null && !startDate.isEmpty()) {
                holder.tvRentInfo.setVisibility(View.VISIBLE);
                holder.tvRentInfo.setText("📅  Thuê từ: " + startDate + " | " + (days != null ? days : "?") + " ngày");
            }
        } else {
            holder.tvBuyerCCCD.setVisibility(View.GONE);
            holder.tvRentInfo.setVisibility(View.GONE);
        }

        // Ghi chú
        if (note != null && !note.isEmpty()) {
            holder.tvRequestNote.setVisibility(View.VISIBLE);
            holder.tvRequestNote.setText("💬  " + note);
        } else {
            holder.tvRequestNote.setVisibility(View.GONE);
        }

        // Nút xác nhận
        holder.btnConfirm.setOnClickListener(v -> {
            if (listener != null) listener.onConfirm(orderId, carId != null ? carId : "", order);
        });

        // Nút từ chối
        holder.btnReject.setOnClickListener(v -> {
            if (listener != null) listener.onReject(orderId, carId != null ? carId : "");
        });
    }

    @Override
    public int getItemCount() { return orderList.size(); }

    public void updateList(List<Map<String, Object>> newList, List<String> newIds) {
        this.orderList = newList;
        this.orderIds = newIds;
        notifyDataSetChanged();
    }

    public static class RequestViewHolder extends RecyclerView.ViewHolder {
        TextView tvRequestType, tvRequestStatus, tvRequestTime;
        TextView tvRequestCarName, tvRequestCarPrice;
        TextView tvBuyerInfo, tvBuyerPhone, tvBuyerCCCD, tvRentInfo, tvRequestNote;
        LinearLayout layoutActionButtons;
        Button btnConfirm, btnReject;
        TextView tvDoneLabel;

        public RequestViewHolder(@NonNull View itemView) {
            super(itemView);
            tvRequestType = itemView.findViewById(R.id.tvRequestType);
            tvRequestStatus = itemView.findViewById(R.id.tvRequestStatus);
            tvRequestTime = itemView.findViewById(R.id.tvRequestTime);
            tvRequestCarName = itemView.findViewById(R.id.tvRequestCarName);
            tvRequestCarPrice = itemView.findViewById(R.id.tvRequestCarPrice);
            tvBuyerInfo = itemView.findViewById(R.id.tvBuyerInfo);
            tvBuyerPhone = itemView.findViewById(R.id.tvBuyerPhone);
            tvBuyerCCCD = itemView.findViewById(R.id.tvBuyerCCCD);
            tvRentInfo = itemView.findViewById(R.id.tvRentInfo);
            tvRequestNote = itemView.findViewById(R.id.tvRequestNote);
            layoutActionButtons = itemView.findViewById(R.id.layoutActionButtons);
            btnConfirm = itemView.findViewById(R.id.btnConfirm);
            btnReject = itemView.findViewById(R.id.btnReject);
            tvDoneLabel = itemView.findViewById(R.id.tvDoneLabel);
        }
    }
}