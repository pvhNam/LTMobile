package com.example.doanmb.ui.admin.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.doanmb.R;
import com.example.doanmb.core.util.ImageLoader;
import com.example.doanmb.data.repository.CarRepository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Danh sách tài xế đã duyệt cho admin: tên, loại xe, online, rating. */
public class DriverAdminAdapter extends RecyclerView.Adapter<DriverAdminAdapter.ViewHolder> {

    public interface OnDriverClick {
        void onClick(String userId);
    }

    private List<Map<String, Object>> drivers;
    private List<String> driverIds;
    private final OnDriverClick clickListener;

    /** Cache rating theo id để khỏi tra Firestore lặp khi cuộn. */
    private static final Map<String, String> RATING_CACHE = new HashMap<>();

    public DriverAdminAdapter(List<Map<String, Object>> drivers, List<String> driverIds, OnDriverClick clickListener) {
        this.drivers = drivers;
        this.driverIds = driverIds;
        this.clickListener = clickListener;
    }

    public void updateList(List<Map<String, Object>> newDrivers, List<String> newIds) {
        this.drivers = newDrivers;
        this.driverIds = newIds;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_driver_admin, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Map<String, Object> driver = drivers.get(position);
        String userId = driverIds.get(position);

        String name      = getStr(driver, "name", "(không tên)");
        String carType   = getStr(driver, "driverCarType", "--");
        String avatarUrl = getStr(driver, "avatarUrl", "");
        boolean online   = Boolean.TRUE.equals(driver.get("driverOnline"));

        holder.tvName.setText(name);
        holder.tvCarType.setText("Loại xe: " + carType);

        if (online) {
            holder.tvOnline.setText("🟢 Online");
            holder.tvOnline.setBackgroundColor(0xFFE8F5E9);
            holder.tvOnline.setTextColor(0xFF2E7D32);
        } else {
            holder.tvOnline.setText("Offline");
            holder.tvOnline.setBackgroundColor(0xFFEEEEEE);
            holder.tvOnline.setTextColor(0xFF757575);
        }

        if (!avatarUrl.isEmpty()) {
            ImageLoader.loadAvatar(holder.ivAvatar, avatarUrl, android.R.drawable.ic_menu_myplaces);
        } else {
            holder.ivAvatar.setImageResource(android.R.drawable.ic_menu_myplaces);
        }

        bindRating(holder.tvRating, userId);

        holder.itemView.setOnClickListener(v -> {
            if (clickListener != null) clickListener.onClick(userId);
        });
    }

    /** Tải rating tài xế (drivers/{id}); dùng setTag chống sai dữ liệu khi recycle. */
    private void bindRating(TextView tv, String userId) {
        String cached = RATING_CACHE.get(userId);
        if (cached != null) {
            tv.setTag(null);
            tv.setText(cached);
            return;
        }
        tv.setTag(userId);
        tv.setText("⭐ …");
        CarRepository.loadDriverRating(userId, (avg, count) -> {
            String text = count > 0
                    ? String.format(java.util.Locale.US, "⭐ %.1f (%d)", avg, count)
                    : "⭐ Chưa có đánh giá";
            RATING_CACHE.put(userId, text);
            if (userId.equals(tv.getTag())) tv.setText(text);
        });
    }

    private String getStr(Map<String, Object> map, String key, String def) {
        Object v = map.get(key);
        return (v != null && !v.toString().isEmpty()) ? v.toString() : def;
    }

    @Override
    public int getItemCount() { return drivers.size(); }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView ivAvatar;
        TextView tvName, tvCarType, tvRating, tvOnline;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            ivAvatar  = itemView.findViewById(R.id.iv_drv_avatar);
            tvName    = itemView.findViewById(R.id.tv_drv_name);
            tvCarType = itemView.findViewById(R.id.tv_drv_cartype);
            tvRating  = itemView.findViewById(R.id.tv_drv_rating);
            tvOnline  = itemView.findViewById(R.id.tv_drv_online);
        }
    }
}
