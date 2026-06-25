package com.example.doanmb.ui.car.adapter;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.doanmb.R;
import com.example.doanmb.data.model.Car;
import com.example.doanmb.core.helper.ImageLoader;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;

public class ProfileCarAdapter extends RecyclerView.Adapter<ProfileCarAdapter.ViewHolder> {

    private List<Car> carList;
    private OnItemClickListener listener;

    public interface OnItemClickListener {
        void onItemClick(Car car);
    }

    public ProfileCarAdapter(List<Car> carList) {
        this(carList, null);
    }

    public ProfileCarAdapter(List<Car> carList, OnItemClickListener listener) {
        this.carList = carList;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_car_profile, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Car car = carList.get(position);
        holder.tvName.setText(car.getName());
        holder.tvPrice.setText(car.getPrice() != null ? car.getPrice() : "");
        holder.tvInfo.setText(car.getInfo() != null ? car.getInfo() : "");

        String imageUrl = car.getImageUrl(); // cần thêm field này vào Car.java
        if (imageUrl != null && !imageUrl.isEmpty()) {
            ImageLoader.loadCard(holder.ivImage, imageUrl, android.R.drawable.ic_menu_gallery);
        } else {
            holder.ivImage.setImageResource(android.R.drawable.ic_menu_gallery);
        }

        String type = normalizeText(car.getType());
        if (isSaleType(type)) {
            holder.tvType.setText("Cần bán");
            holder.tvType.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#1976D2")));
        } else if (isDriverType(type)) {
            holder.tvType.setText("Có tài xế");
            holder.tvType.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#00897B")));
        } else if (isRentalType(type)) {
            holder.tvType.setText("Cho thuê");
            holder.tvType.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#4CAF50")));
        } else {
            holder.tvType.setText("Khác");
            holder.tvType.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#757575")));
        }

        holder.itemView.setOnClickListener(listener != null ? v -> listener.onItemClick(car) : null);
    }

    @Override
    public int getItemCount() {
        return carList.size();
    }

    public void updateList(List<Car> newList) {
        this.carList = newList;
        notifyDataSetChanged();
    }

    private static boolean isSaleType(String type) {
        return type.contains("sale") || type.contains("ban") || type.contains("mua");
    }

    private static boolean isRentalType(String type) {
        return type.contains("rental") || type.contains("rent") || type.contains("thue") || type.contains("tu lai");
    }

    private static boolean isDriverType(String type) {
        return type.contains("driver") || type.contains("tai xe") || type.contains("co tai xe");
    }

    private static String normalizeText(String value) {
        if (value == null) return "";
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFD);
        return normalized.replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT).trim();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvName, tvPrice, tvInfo, tvType;
        ImageView ivImage;;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            ivImage = itemView.findViewById(R.id.iv_car_profile);
            tvName = itemView.findViewById(R.id.tv_car_profile_name);
            tvPrice = itemView.findViewById(R.id.tv_car_profile_price);
            tvInfo = itemView.findViewById(R.id.tv_car_profile_info);
            tvType = itemView.findViewById(R.id.tv_car_profile_type);
        }
    }
}
