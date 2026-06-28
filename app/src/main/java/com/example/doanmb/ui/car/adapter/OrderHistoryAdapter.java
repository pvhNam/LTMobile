package com.example.doanmb.ui.car.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentActivity;
import androidx.recyclerview.widget.RecyclerView;

import com.example.doanmb.R;
import com.example.doanmb.ui.car.view.ReviewDialogFragment;
import com.google.firebase.Timestamp;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class OrderHistoryAdapter extends RecyclerView.Adapter<OrderHistoryAdapter.OrderViewHolder> {

    private List<Map<String, Object>> orderList;

    public OrderHistoryAdapter(List<Map<String, Object>> orderList) {
        this.orderList = orderList;
    }

    @NonNull
    @Override
    public OrderViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_order_history, parent, false);
        return new OrderViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull OrderViewHolder holder, int position) {
        Map<String, Object> order = orderList.get(position);

        String type       = (String) order.get("type");
        String status     = (String) order.get("status");
        String carName    = (String) order.get("carName");
        String carPrice   = (String) order.get("carPrice");
        String note       = (String) order.get("note");
        Object createdAt  = order.get("createdAt");
        String orderId    = (String) order.get("orderId");
        // Với xe có tài xế: driverId = sellerId (người đăng bài = tài xế)
        String driverIdField = (String) order.get("driverId");
        String driverId   = (driverIdField != null && !driverIdField.isEmpty())
                ? driverIdField
                : (String) order.get("sellerId");
        String carId      = (String) order.get("carId");

        Boolean canReview = (Boolean) order.get("canReview");
        Boolean reviewed  = (Boolean) order.get("reviewed");

        // Loại đơn
        holder.tvOrderType.setText(type != null ? type : "Yêu cầu");
        if ("Thuê xe".equals(type)) {
            holder.tvOrderType.setBackgroundColor(0xFF1976D2);
        } else {
            holder.tvOrderType.setBackgroundColor(0xFF4CAF50);
        }

        // Tên xe + giá
        holder.tvOrderCarName.setText(carName != null ? carName : "Không rõ");
        holder.tvOrderCarPrice.setText(carPrice != null ? carPrice : "");

        // Trạng thái
        if ("accepted".equals(status)) {
            holder.tvOrderStatus.setText("✅ Tài xế đã nhận");
            holder.tvOrderStatus.setTextColor(0xFF4CAF50);
            holder.layoutStatusMessage.setVisibility(View.VISIBLE);
            holder.tvStatusMessage.setText("🎉 Tài xế đã chấp nhận yêu cầu! Chuyến sắp bắt đầu.");
            holder.tvStatusMessage.setTextColor(0xFF4CAF50);
        } else if ("in_progress".equals(status)) {
            holder.tvOrderStatus.setText("🚗 Đang thực hiện");
            holder.tvOrderStatus.setTextColor(0xFF1976D2);
            holder.layoutStatusMessage.setVisibility(View.VISIBLE);
            holder.tvStatusMessage.setText("🚗 Tài xế đang thực hiện chuyến. Vui lòng chờ!");
            holder.tvStatusMessage.setTextColor(0xFF1976D2);
        } else if ("completed".equals(status)) {
            holder.tvOrderStatus.setText("🏁 Hoàn thành");
            holder.tvOrderStatus.setTextColor(0xFF1565C0);
            holder.layoutStatusMessage.setVisibility(View.VISIBLE);
            holder.tvStatusMessage.setText("🏁 Đơn đã hoàn thành. Cảm ơn bạn đã sử dụng dịch vụ!");
            holder.tvStatusMessage.setTextColor(0xFF1565C0);
        } else if ("cancelled".equals(status)) {
            holder.tvOrderStatus.setText("↩️ Đã hủy");
            holder.tvOrderStatus.setTextColor(0xFFF44336);
            holder.layoutStatusMessage.setVisibility(View.VISIBLE);
            holder.tvStatusMessage.setText("↩️ Đơn đã hủy. Nếu có đặt cọc, tiền đã được hoàn về ví của bạn.");
            holder.tvStatusMessage.setTextColor(0xFFF44336);
        } else if ("confirmed".equals(status)) {
            holder.tvOrderStatus.setText("✅ Đã xác nhận");
            holder.tvOrderStatus.setTextColor(0xFF4CAF50);
            holder.layoutStatusMessage.setVisibility(View.VISIBLE);
            holder.tvStatusMessage.setText("🎉 Người bán đã xác nhận yêu cầu của bạn! Họ sẽ liên hệ bạn sớm.");
            holder.tvStatusMessage.setTextColor(0xFF4CAF50);
        } else if ("rejected".equals(status)) {
            holder.tvOrderStatus.setText("❌ Đã từ chối");
            holder.tvOrderStatus.setTextColor(0xFFF44336);
            holder.layoutStatusMessage.setVisibility(View.VISIBLE);
            holder.tvStatusMessage.setText("😔 Rất tiếc, người bán đã từ chối yêu cầu này. Bạn có thể tìm xe khác.");
            holder.tvStatusMessage.setTextColor(0xFFF44336);
        } else if ("holding".equals(status)) {
            holder.tvOrderStatus.setText("⏳ Đang giữ chỗ");
            holder.tvOrderStatus.setTextColor(0xFFFF9800);
            holder.layoutStatusMessage.setVisibility(View.VISIBLE);
            holder.tvStatusMessage.setText("⏳ Yêu cầu đang được giữ chỗ, chờ người bán xác nhận...");
            holder.tvStatusMessage.setTextColor(0xFFFF9800);
        } else {
            holder.tvOrderStatus.setText("⏳ Chờ xác nhận");
            holder.tvOrderStatus.setTextColor(0xFFFF9800);
            holder.layoutStatusMessage.setVisibility(View.VISIBLE);
            holder.tvStatusMessage.setText("⏳ Yêu cầu đã gửi, đang chờ người bán phản hồi...");
            holder.tvStatusMessage.setTextColor(0xFFFF9800);
        }

        // Ngày gửi
        if (createdAt instanceof Timestamp) {
            Date date = ((Timestamp) createdAt).toDate();
            String formatted = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(date);
            holder.tvOrderDate.setText("📅  Ngày gửi: " + formatted);
        } else {
            holder.tvOrderDate.setText("📅  Ngày gửi: --");
        }

        // Ghi chú
        if (note != null && !note.isEmpty()) {
            holder.tvOrderNote.setVisibility(View.VISIBLE);
            holder.tvOrderNote.setText("💬  " + note);
        } else {
            holder.tvOrderNote.setVisibility(View.GONE);
        }

        // Nút "Đánh giá tài xế": hiện khi completed + canReview=true + reviewed=false
        boolean showReviewBtn = "completed".equals(status)
                && Boolean.TRUE.equals(canReview)
                && !Boolean.TRUE.equals(reviewed);

        if (holder.btnReviewDriver != null) {
            holder.btnReviewDriver.setVisibility(showReviewBtn ? View.VISIBLE : View.GONE);

            if (showReviewBtn) {
                final String finalOrderId  = orderId;
                final String finalDriverId = driverId;
                final String finalCarId    = carId;

                holder.btnReviewDriver.setOnClickListener(v -> {
                    if (!(v.getContext() instanceof FragmentActivity)) return;
                    FragmentActivity activity = (FragmentActivity) v.getContext();
                    ReviewDialogFragment dialog = ReviewDialogFragment.newInstance(
                            finalOrderId  != null ? finalOrderId  : "",
                            finalDriverId != null ? finalDriverId : "",
                            finalCarId    != null ? finalCarId    : ""
                    );
                    dialog.show(activity.getSupportFragmentManager(), "review_dialog");
                });
            }
        }
    }

    @Override
    public int getItemCount() { return orderList.size(); }

    public void updateList(List<Map<String, Object>> newList) {
        this.orderList = newList;
        notifyDataSetChanged();
    }

    public static class OrderViewHolder extends RecyclerView.ViewHolder {
        TextView tvOrderType, tvOrderStatus, tvOrderCarName, tvOrderCarPrice;
        TextView tvOrderDate, tvOrderNote, tvStatusMessage;
        LinearLayout layoutStatusMessage;
        Button btnReviewDriver;

        public OrderViewHolder(@NonNull View itemView) {
            super(itemView);
            tvOrderType       = itemView.findViewById(R.id.tvOrderType);
            tvOrderStatus     = itemView.findViewById(R.id.tvOrderStatus);
            tvOrderCarName    = itemView.findViewById(R.id.tvOrderCarName);
            tvOrderCarPrice   = itemView.findViewById(R.id.tvOrderCarPrice);
            tvOrderDate       = itemView.findViewById(R.id.tvOrderDate);
            tvOrderNote       = itemView.findViewById(R.id.tvOrderNote);
            tvStatusMessage   = itemView.findViewById(R.id.tvStatusMessage);
            layoutStatusMessage = itemView.findViewById(R.id.layoutStatusMessage);
            btnReviewDriver   = itemView.findViewById(R.id.btn_review_driver);
        }
    }
}