package com.example.doanmb.ui.car.adapter;

import android.content.Context;
import android.content.res.ColorStateList;
import android.os.Build;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.doanmb.R;
import com.google.android.material.button.MaterialButton;

import java.util.List;
import java.util.Map;

import eightbitlab.com.blurview.BlurAlgorithm;
import eightbitlab.com.blurview.BlurView;
import eightbitlab.com.blurview.RenderEffectBlur;
import eightbitlab.com.blurview.RenderScriptBlur;

public class RequestAdapter extends RecyclerView.Adapter<RequestAdapter.RequestViewHolder> {

    public interface OnActionListener {
        // Đơn NHẬN ĐƯỢC (mình là chủ xe)
        void onConfirm(String orderId, String carId, Map<String, Object> order);
        void onReject(String orderId, String carId);
        void onMarkReturned(String orderId, Map<String, Object> order);
        // Đơn MÌNH GỬI ĐI (mình đi thuê)
        void onCancelOwn(String orderId, Map<String, Object> order);
        void onExtend(String orderId, Map<String, Object> order);
        void onViewInvoice(String orderId, Map<String, Object> order);
    }

    private List<Map<String, Object>> orderList;
    private List<String> orderIds;
    private final OnActionListener listener;
    private final String currentUserId;

    public RequestAdapter(List<Map<String, Object>> orderList, List<String> orderIds,
                          String currentUserId, OnActionListener listener) {
        this.orderList = orderList;
        this.orderIds = orderIds;
        this.currentUserId = currentUserId != null ? currentUserId : "";
        this.listener = listener;
    }

    @NonNull
    @Override
    public RequestViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_request, parent, false);
        RequestViewHolder holder = new RequestViewHolder(view);
        setupBlur(holder.blurRequest, parent);
        return holder;
    }

    /** Bật blur thật cho thẻ (Liquid Glass): blur nội dung cửa sổ phía sau thẻ. */
    private void setupBlur(BlurView blurView, ViewGroup parent) {
        if (blurView == null) return;
        View root = parent.getRootView();
        if (!(root instanceof ViewGroup)) return;
        try {
            blurView.setupWith((ViewGroup) root, newBlurAlgorithm(parent.getContext()))
                    .setBlurRadius(16f);
        } catch (Exception ignore) {
            // Nếu thiết bị không hỗ trợ blur → thẻ vẫn hiển thị nền kính tĩnh (bg_glass_card).
        }
    }

    private static BlurAlgorithm newBlurAlgorithm(Context c) {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                ? new RenderEffectBlur()
                : new RenderScriptBlur(c);
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
        if (cccd == null) cccd = (String) order.get("renterCccd");
        String startDate = (String) order.get("startDate");
        String days = (String) order.get("days");
        String note = (String) order.get("note");
        String buyerId = (String) order.get("buyerId");
        String sellerName = (String) order.get("sellerName");
        if (status == null) status = "pending";

        boolean isMine = currentUserId.equals(buyerId);  // đơn MÌNH gửi đi
        boolean isRental = "Thuê xe".equals(type) || "Có tài xế".equals(type);

        // Nhãn loại
        holder.tvRequestType.setText(type != null ? type : "Yêu cầu");
        if (isRental) {
            holder.tvRequestType.setBackgroundTintList(ColorStateList.valueOf(0xFF1976D2));
        } else {
            holder.tvRequestType.setBackgroundTintList(ColorStateList.valueOf(0xFF4CAF50));
        }

        holder.tvRequestCarName.setText(carName != null ? carName : "Không rõ");
        holder.tvRequestCarPrice.setText(carPrice != null ? carPrice : "");

        // Ẩn hết các cụm nút trước, rồi bật theo vai + trạng thái
        holder.layoutActionButtons.setVisibility(View.GONE);
        holder.layoutMyActions.setVisibility(View.GONE);
        holder.btnExtend.setVisibility(View.GONE);
        holder.btnMarkReturned.setVisibility(View.GONE);
        holder.btnViewInvoice.setVisibility(View.GONE);
        holder.tvDoneLabel.setVisibility(View.GONE);

        if (isMine) {
            // ===== ĐƠN MÌNH GỬI ĐI (đi thuê) =====
            holder.tvBuyerInfo.setText("🏠  Chủ xe: " + (sellerName != null && !sellerName.isEmpty() ? sellerName : "—"));
            holder.tvBuyerPhone.setText("📤  Đơn bạn gửi đi");
            holder.tvBuyerCCCD.setVisibility(View.GONE);

            switch (status) {
                case "pending":
                case "holding":
                    holder.tvRequestStatus.setText("⏳ Chờ chủ xe duyệt");
                    holder.tvRequestStatus.setTextColor(0xFFFF9800);
                    holder.layoutMyActions.setVisibility(View.VISIBLE); // chỉ Hủy (chưa cho gia hạn khi chưa duyệt)
                    break;
                case "confirmed":
                    holder.tvRequestStatus.setText("✅ Đang thuê");
                    holder.tvRequestStatus.setTextColor(0xFF4CAF50);
                    holder.layoutMyActions.setVisibility(View.VISIBLE);
                    holder.btnExtend.setVisibility(View.VISIBLE); // đang thuê mới cho gia hạn
                    break;
                case "awaiting_payment":
                    holder.tvRequestStatus.setText("🧾 Cần thanh toán hóa đơn");
                    holder.tvRequestStatus.setTextColor(0xFFF9A825);
                    holder.btnViewInvoice.setVisibility(View.VISIBLE);
                    break;
                case "completed":
                    holder.tvRequestStatus.setText("✅ Hoàn thành");
                    holder.tvRequestStatus.setTextColor(0xFF4CAF50);
                    holder.tvDoneLabel.setVisibility(View.VISIBLE);
                    holder.tvDoneLabel.setText("Đã hoàn thành");
                    break;
                case "rejected":
                    holder.tvRequestStatus.setText("❌ Bị từ chối");
                    holder.tvRequestStatus.setTextColor(0xFFF44336);
                    holder.tvDoneLabel.setVisibility(View.VISIBLE);
                    holder.tvDoneLabel.setText("Đã xử lý");
                    break;
                case "accepted":
                    holder.tvRequestStatus.setText("✅ Tài xế đã nhận");
                    holder.tvRequestStatus.setTextColor(0xFF4CAF50);
                    break;
                case "in_progress":
                    holder.tvRequestStatus.setText("🚗 Đang thực hiện");
                    holder.tvRequestStatus.setTextColor(0xFF1976D2);
                    break;
                case "cancelled":
                    holder.tvRequestStatus.setText("↩️ Đã hủy");
                    holder.tvRequestStatus.setTextColor(0xFFF44336);
                    holder.tvDoneLabel.setVisibility(View.VISIBLE);
                    holder.tvDoneLabel.setText("Đã hủy");
                    break;
                default:
                    holder.tvRequestStatus.setText("⏳ Đang xử lý");
                    holder.tvRequestStatus.setTextColor(0xFFFF9800);
                    break;
            }
        } else {
            // ===== ĐƠN NHẬN ĐƯỢC (mình là chủ xe) =====
            if (renterName != null && !renterName.isEmpty()) {
                holder.tvBuyerInfo.setText("👤  Người thuê: " + renterName);
                holder.tvBuyerPhone.setText("📞  " + (renterPhone != null ? renterPhone : ""));
            } else {
                holder.tvBuyerInfo.setText("👤  Người mua: " + (buyerId != null && buyerId.length() >= 8 ? buyerId.substring(0, 8) + "..." : "Không rõ"));
                holder.tvBuyerPhone.setText("📞  Xem thông tin trong hệ thống");
            }

            switch (status) {
                case "pending":
                case "holding":
                    holder.tvRequestStatus.setText("holding".equals(status) ? "⏳ Đang giữ chỗ" : "⏳ Chờ xác nhận");
                    holder.tvRequestStatus.setTextColor(0xFFFF9800);
                    holder.layoutActionButtons.setVisibility(View.VISIBLE);
                    break;
                case "confirmed":
                    holder.tvRequestStatus.setText("✅ Đã xác nhận");
                    holder.tvRequestStatus.setTextColor(0xFF4CAF50);
                    if (isRental) {
                        holder.btnMarkReturned.setVisibility(View.VISIBLE);
                    } else {
                        holder.tvDoneLabel.setVisibility(View.VISIBLE);
                        holder.tvDoneLabel.setText("Đã xác nhận");
                    }
                    break;
                case "awaiting_payment":
                    holder.tvRequestStatus.setText("🧾 Chờ khách thanh toán");
                    holder.tvRequestStatus.setTextColor(0xFFF9A825);
                    holder.tvDoneLabel.setVisibility(View.VISIBLE);
                    holder.tvDoneLabel.setText("Đã gửi hóa đơn cho khách");
                    break;
                case "completed":
                    holder.tvRequestStatus.setText("✅ Hoàn thành");
                    holder.tvRequestStatus.setTextColor(0xFF4CAF50);
                    holder.tvDoneLabel.setVisibility(View.VISIBLE);
                    holder.tvDoneLabel.setText("Khách đã thanh toán");
                    break;
                case "rejected":
                    holder.tvRequestStatus.setText("❌ Đã từ chối");
                    holder.tvRequestStatus.setTextColor(0xFFF44336);
                    holder.tvDoneLabel.setVisibility(View.VISIBLE);
                    holder.tvDoneLabel.setText("Đã xử lý");
                    break;
                case "accepted":
                    holder.tvRequestStatus.setText("✅ Đã nhận chuyến");
                    holder.tvRequestStatus.setTextColor(0xFF4CAF50);
                    break;
                case "in_progress":
                    holder.tvRequestStatus.setText("🚗 Đang chạy");
                    holder.tvRequestStatus.setTextColor(0xFF1976D2);
                    break;
                case "cancelled":
                    holder.tvRequestStatus.setText("↩️ Đã hủy");
                    holder.tvRequestStatus.setTextColor(0xFFF44336);
                    holder.tvDoneLabel.setVisibility(View.VISIBLE);
                    holder.tvDoneLabel.setText("Đã hủy");
                    break;
                default:
                    holder.tvRequestStatus.setText("⏳ Đang xử lý");
                    holder.tvRequestStatus.setTextColor(0xFFFF9800);
                    break;
            }
        }

        // CCCD + ngày thuê (chỉ hiện khi thuê xe, và chỉ với đơn nhận được)
        if (isRental && !isMine) {
            if (cccd != null && !cccd.isEmpty()) {
                holder.tvBuyerCCCD.setVisibility(View.VISIBLE);
                holder.tvBuyerCCCD.setText("🪪  CCCD: " + cccd);
            }
        }
        if (isRental && startDate != null && !startDate.isEmpty()) {
            holder.tvRentInfo.setVisibility(View.VISIBLE);
            holder.tvRentInfo.setText("📅  Thuê từ: " + startDate + " | " + (days != null ? days : "?") + " ngày");
        } else {
            holder.tvRentInfo.setVisibility(View.GONE);
        }

        if (note != null && !note.isEmpty()) {
            holder.tvRequestNote.setVisibility(View.VISIBLE);
            holder.tvRequestNote.setText("💬  " + note);
        } else {
            holder.tvRequestNote.setVisibility(View.GONE);
        }

        final String fCarId = carId != null ? carId : "";
        holder.btnConfirm.setOnClickListener(v -> { if (listener != null) listener.onConfirm(orderId, fCarId, order); });
        holder.btnReject.setOnClickListener(v -> { if (listener != null) listener.onReject(orderId, fCarId); });
        holder.btnMarkReturned.setOnClickListener(v -> { if (listener != null) listener.onMarkReturned(orderId, order); });
        holder.btnCancelOwn.setOnClickListener(v -> { if (listener != null) listener.onCancelOwn(orderId, order); });
        holder.btnExtend.setOnClickListener(v -> { if (listener != null) listener.onExtend(orderId, order); });
        holder.btnViewInvoice.setOnClickListener(v -> { if (listener != null) listener.onViewInvoice(orderId, order); });
    }

    @Override
    public int getItemCount() { return orderList.size(); }

    public void updateList(List<Map<String, Object>> newList, List<String> newIds) {
        this.orderList = newList;
        this.orderIds = newIds;
        notifyDataSetChanged();
    }

    public static class RequestViewHolder extends RecyclerView.ViewHolder {
        BlurView blurRequest;
        TextView tvRequestType, tvRequestStatus, tvRequestTime;
        TextView tvRequestCarName, tvRequestCarPrice;
        TextView tvBuyerInfo, tvBuyerPhone, tvBuyerCCCD, tvRentInfo, tvRequestNote;
        LinearLayout layoutActionButtons, layoutMyActions;
        MaterialButton btnConfirm, btnReject, btnMarkReturned, btnCancelOwn, btnExtend, btnViewInvoice;
        TextView tvDoneLabel;

        public RequestViewHolder(@NonNull View itemView) {
            super(itemView);
            blurRequest = itemView.findViewById(R.id.blur_request);
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
            layoutMyActions = itemView.findViewById(R.id.layoutMyActions);
            btnConfirm = itemView.findViewById(R.id.btnConfirm);
            btnReject = itemView.findViewById(R.id.btnReject);
            btnMarkReturned = itemView.findViewById(R.id.btnMarkReturned);
            btnCancelOwn = itemView.findViewById(R.id.btnCancelOwn);
            btnExtend = itemView.findViewById(R.id.btnExtend);
            btnViewInvoice = itemView.findViewById(R.id.btnViewInvoice);
            tvDoneLabel = itemView.findViewById(R.id.tvDoneLabel);
        }
    }
}
