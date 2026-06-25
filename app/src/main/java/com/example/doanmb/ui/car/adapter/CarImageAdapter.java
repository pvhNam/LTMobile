package com.example.doanmb.ui.car.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.doanmb.R;
import com.example.doanmb.core.helper.ImageLoader;

import java.util.ArrayList;
import java.util.List;

/** Hiển thị danh sách ảnh xe dạng trang vuốt ngang trong màn chi tiết. */
public class CarImageAdapter extends RecyclerView.Adapter<CarImageAdapter.ImageViewHolder> {

    private final List<String> imageUrls = new ArrayList<>();

    public void setImages(List<String> urls) {
        imageUrls.clear();
        if (urls != null) imageUrls.addAll(urls);
        notifyDataSetChanged();
    }

    public int getImageCount() {
        return imageUrls.size();
    }

    @NonNull
    @Override
    public ImageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_car_image, parent, false);
        return new ImageViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ImageViewHolder holder, int position) {
        String url = imageUrls.get(position);
        if (url != null && !url.isEmpty()) {
            ImageLoader.loadDetail(holder.image, url, android.R.drawable.ic_menu_gallery);
        } else {
            holder.image.setImageResource(android.R.drawable.ic_menu_gallery);
        }
    }

    @Override
    public int getItemCount() {
        return imageUrls.size();
    }

    static class ImageViewHolder extends RecyclerView.ViewHolder {
        final ImageView image;

        ImageViewHolder(@NonNull View itemView) {
            super(itemView);
            image = itemView.findViewById(R.id.iv_car_page);
        }
    }
}
