package com.example.doanmb.ui.admin.adapter;

import android.app.AlertDialog;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.doanmb.R;
import com.example.doanmb.core.helper.ImageLoader;

import java.util.List;
import java.util.Map;

public class CarAdminAdapter extends RecyclerView.Adapter<CarAdminAdapter.ViewHolder> {

    public interface OnCarActionListener {
        void onApprove(String carId);
        void onReject(String carId);
        void onDelete(String carId);
    }

    private List<Map<String, Object>> cars;
    private List<String> carIds;
    private OnCarActionListener listener;

    public CarAdminAdapter(List<Map<String, Object>> cars, List<String> carIds) {
        this.cars = cars;
        this.carIds = carIds;
    }

    public CarAdminAdapter(List<Map<String, Object>> cars, List<String> carIds, OnCarActionListener listener) {
        this.cars = cars;
        this.carIds = carIds;
        this.listener = listener;
    }

    public void updateList(List<Map<String, Object>> newCars, List<String> newIds) {
        this.cars = newCars;
        this.carIds = newIds;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_car_admin, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Map<String, Object> car = cars.get(position);
        String carId = carIds.get(position);
        Context ctx = holder.itemView.getContext();

        String name       = getStr(car, "name", "Không tên");
        String price      = getStr(car, "price", "");
        String sellerName = getStr(car, "sellerName", "");
        String imageUrl   = getStr(car, "imageUrl", "");
        String status     = getStr(car, "status", "");
        String type       = getStr(car, "type", "sale");

        holder.tvName.setText(name);
        holder.tvPrice.setText(price.isEmpty() ? "Chưa có giá" : price);
        holder.tvSeller.setText(sellerName.isEmpty() ? "Ẩn danh" : "Đăng bởi: " + sellerName);
        holder.tvType.setText("sale".equals(type) ? "Bán" : "Cho thuê");
        holder.tvType.setTextColor("sale".equals(type) ? 0xFF1565C0 : 0xFF2E7D32);
        applyStatusStyle(ctx, holder.tvStatus, status);

        if (!imageUrl.isEmpty()) {
            ImageLoader.loadCard(holder.ivThumb, imageUrl, android.R.drawable.ic_menu_gallery);
        } else {
            holder.ivThumb.setImageResource(android.R.drawable.ic_menu_gallery);
        }

        if (listener == null) {
            holder.layoutActions.setVisibility(View.GONE);
            return;
        }

        // Luôn hiện layout actions khi có listener
        holder.layoutActions.setVisibility(View.VISIBLE);

        // Nút Xóa: luôn hiện
        holder.btnDelete.setVisibility(View.VISIBLE);
        holder.btnDelete.setOnClickListener(v ->
                new AlertDialog.Builder(ctx)
                        .setTitle("Xóa xe")
                        .setMessage("Bạn có chắc muốn xóa tin đăng \"" + name + "\" không?\nHành động này không thể hoàn tác.")
                        .setPositiveButton("Xóa", (d, w) -> listener.onDelete(carId))
                        .setNegativeButton("Hủy", null)
                        .show()
        );

        // Nút Duyệt + Từ chối: chỉ hiện khi xe đang pending (hoặc chưa có status)
        boolean isPending = status.isEmpty() || "pending".equals(status);
        holder.btnApprove.setVisibility(isPending ? View.VISIBLE : View.GONE);
        holder.btnReject.setVisibility(isPending ? View.VISIBLE : View.GONE);

        if (isPending) {
            holder.btnApprove.setOnClickListener(v -> listener.onApprove(carId));
            holder.btnReject.setOnClickListener(v -> listener.onReject(carId));
        }
    }

    private void applyStatusStyle(Context ctx, TextView tv, String status) {
        switch (status) {
            case "active":
                tv.setText("Đang bán");
                tv.setBackgroundColor(0xFFE8F5E9);
                tv.setTextColor(0xFF2E7D32);
                break;
            case "sold":
                tv.setText("Đã bán");
                tv.setBackgroundColor(0xFFEEEEEE);
                tv.setTextColor(0xFF757575);
                break;
            case "holding":
                tv.setText("Đặt cọc");
                tv.setBackgroundColor(0xFFE3F2FD);
                tv.setTextColor(0xFF1565C0);
                break;
            case "rejected":
                tv.setText("Từ chối");
                tv.setBackgroundColor(0xFFFFCDD2);
                tv.setTextColor(0xFFC62828);
                break;
            default:
                tv.setText("Chờ duyệt");
                tv.setBackgroundColor(0xFFFFF3E0);
                tv.setTextColor(0xFFE65100);
                break;
        }
    }

    private String getStr(Map<String, Object> map, String key, String def) {
        Object v = map.get(key);
        return (v != null) ? v.toString() : def;
    }

    @Override
    public int getItemCount() { return cars.size(); }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView ivThumb;
        TextView tvName, tvPrice, tvSeller, tvStatus, tvType;
        LinearLayout layoutActions;
        Button btnApprove, btnReject, btnDelete;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            ivThumb       = itemView.findViewById(R.id.iv_car_thumb);
            tvName        = itemView.findViewById(R.id.tv_car_admin_name);
            tvPrice       = itemView.findViewById(R.id.tv_car_admin_price);
            tvSeller      = itemView.findViewById(R.id.tv_car_admin_seller);
            tvStatus      = itemView.findViewById(R.id.tv_car_admin_status);
            tvType        = itemView.findViewById(R.id.tv_car_admin_type);
            layoutActions = itemView.findViewById(R.id.layout_car_actions);
            btnApprove    = itemView.findViewById(R.id.btn_approve_car);
            btnReject     = itemView.findViewById(R.id.btn_reject_car);
            btnDelete     = itemView.findViewById(R.id.btn_delete_car);
        }
    }
}
