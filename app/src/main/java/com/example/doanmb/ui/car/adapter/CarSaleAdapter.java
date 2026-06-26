package com.example.doanmb.ui.car.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.doanmb.R;
import com.example.doanmb.core.util.ImageLoader;
import com.example.doanmb.data.model.Car;

import java.util.List;

public class CarSaleAdapter extends RecyclerView.Adapter<CarSaleAdapter.CarSaleViewHolder> {

    private List<Car> carList;
    private OnItemClickListener listener; // Thêm Listener

    // Tạo Interface để xử lý Click
    public interface OnItemClickListener {
        void onItemClick(Car car);
    }

    // Yêu thích (tim ở góc card)
    private java.util.Set<String> favoriteIds = java.util.Collections.emptySet();
    private OnFavoriteToggle favoriteListener;

    public interface OnFavoriteToggle {
        void onToggle(Car car, boolean makeFavorite);
    }

    public void setFavoriteListener(OnFavoriteToggle l) { this.favoriteListener = l; }

    public void setFavoriteIds(java.util.Set<String> ids) {
        this.favoriteIds = ids != null ? ids : java.util.Collections.emptySet();
        // Chỉ báo cập nhật phần tim (payload) → KHÔNG tải lại ảnh
        notifyItemRangeChanged(0, getItemCount(), "fav");
    }

    // Cập nhật Constructor
    public CarSaleAdapter(List<Car> carList, OnItemClickListener listener) {
        this.carList = carList;
        this.listener = listener;
    }

    @NonNull
    @Override
    public CarSaleViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_car_sale, parent, false);
        return new CarSaleViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull CarSaleViewHolder holder, int position) {
        Car car = carList.get(position);
        holder.tvName.setText(car.getName());
        holder.tvPrice.setText(car.getPrice());
        holder.tvInfo.setText(car.getInfo());

        String imageUrl = car.getImageUrl();
        if (imageUrl != null && !imageUrl.isEmpty()) {
            ImageLoader.loadCard(holder.ivImage, imageUrl, android.R.drawable.ic_menu_gallery);
        } else {
            holder.ivImage.setImageResource(android.R.drawable.ic_menu_gallery);
        }

        // Bắt sự kiện click vào nút "XEM CHI TIẾT" hoặc toàn bộ Item
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onItemClick(car);
            }
        });

        // Bắt sự kiện click vào nút bấm cụ thể
        holder.btnViewDetails.setOnClickListener(v -> {
            if (listener != null) {
                listener.onItemClick(car);
            }
        });

        // Tim yêu thích
        bindFavorite(holder, car);
    }

    /** Bind lại chỉ phần tim (khi đổi trạng thái yêu thích) — không tải lại ảnh. */
    @Override
    public void onBindViewHolder(@NonNull CarSaleViewHolder holder, int position, @NonNull List<Object> payloads) {
        if (!payloads.isEmpty()) {
            bindFavorite(holder, carList.get(position));
            return;
        }
        super.onBindViewHolder(holder, position, payloads);
    }

    private void bindFavorite(CarSaleViewHolder holder, Car car) {
        boolean fav = car.getId() != null && favoriteIds.contains(car.getId());
        holder.ivFavorite.setImageResource(fav ? R.drawable.ic_heart_filled : R.drawable.ic_heart_outline);
        holder.ivFavorite.setOnClickListener(v -> {
            if (favoriteListener != null) favoriteListener.onToggle(car, !fav);
        });
    }

    @Override
    public int getItemCount() {
        return carList.size();
    }

    public static class CarSaleViewHolder extends RecyclerView.ViewHolder {
        ImageView ivImage, ivFavorite;
        TextView tvName, tvPrice, tvInfo;
        Button btnViewDetails; // Khai báo thêm nút bấm

        public CarSaleViewHolder(@NonNull View itemView) {
            super(itemView);
            ivImage = itemView.findViewById(R.id.ivCarSale);
            ivFavorite = itemView.findViewById(R.id.iv_favorite_sale);
            tvName = itemView.findViewById(R.id.tvCarSaleName);
            tvPrice = itemView.findViewById(R.id.tvCarSalePrice);
            tvInfo = itemView.findViewById(R.id.tvCarSaleInfo);
            btnViewDetails = itemView.findViewById(R.id.btnViewDetails); // Ánh xạ nút
        }
    }
    // Thêm hàm này để cập nhật lại danh sách khi tìm kiếm
    public void filterList(List<Car> filteredList) {
        this.carList = filteredList;
        notifyDataSetChanged();
    }
}