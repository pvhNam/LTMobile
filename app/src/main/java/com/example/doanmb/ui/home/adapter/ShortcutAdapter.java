package com.example.doanmb.ui.home.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.graphics.Color;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.doanmb.R;
import com.example.doanmb.core.util.ImageLoader;

import java.util.List;
import java.util.Map;

public class ShortcutAdapter extends RecyclerView.Adapter<ShortcutAdapter.VH> {
    private List<Map<String, Object>> list;

    // 1. Khai báo Interface để gửi sự kiện click về Fragment
    public interface OnShortcutClickListener {
        void onShortcutClick(String partnerId);
    }
    private final OnShortcutClickListener listener;

    // 2. Biến lưu trạng thái người đang được chọn để lọc
    private String selectedPartnerId = null;

    // Cập nhật Constructor để nhận thêm Listener
    public ShortcutAdapter(List<Map<String, Object>> list, OnShortcutClickListener listener) {
        this.list = list;
        this.listener = listener;
    }

    // Hàm để Fragment gọi vào khi muốn thay đổi trạng thái chọn (Bật/Tắt viền sáng)
    public void setSelectedPartnerId(String partnerId) {
        this.selectedPartnerId = partnerId;
        notifyDataSetChanged(); // Render lại để cập nhật UI
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_shortcut_bubble, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        Map<String, Object> item = list.get(position);
        String name = (String) item.get("partnerName");
        String avatar = (String) item.get("partnerAvatar");
        String partnerId = (String) item.get("partnerId"); // Lấy ID của đối phương

        h.tvName.setText(name != null ? name : "U");
        String initial = name != null && !name.isEmpty() ? String.valueOf(name.charAt(0)).toUpperCase() : "U";
        h.tvAvatar.setText(initial);

        if (avatar != null && !avatar.isEmpty()) {
            h.tvAvatar.setVisibility(View.GONE);
            h.ivAvatar.setVisibility(View.VISIBLE);
            ImageLoader.loadAvatar(h.ivAvatar, avatar);
        } else {
            h.tvAvatar.setVisibility(View.VISIBLE);
            h.ivAvatar.setVisibility(View.GONE);
        }

        // 3. Cập nhật UI thể hiện trạng thái được chọn (Highlight)
        if (partnerId != null && partnerId.equals(selectedPartnerId)) {
            // ĐANG CHỌN: Làm nổi bật (Bạn có thể đổi màu chữ hoặc setBackground cho viền ở đây)
            h.tvName.setTextColor(Color.parseColor("#2F54D4")); // Chữ đổi sang màu xanh
            h.tvName.setTypeface(null, android.graphics.Typeface.BOLD); // In đậm chữ
            h.itemView.setAlpha(1.0f);
        } else {
            // KHÔNG ĐƯỢC CHỌN: Trạng thái bình thường hoặc làm mờ đi nếu đang có người khác được chọn
            h.tvName.setTextColor(Color.parseColor("#1A1A2E")); // Màu chữ đen mặc định
            h.tvName.setTypeface(null, android.graphics.Typeface.NORMAL);
            // Nếu đang có 1 bộ lọc bật, làm mờ các item khác để người dùng tập trung
            h.itemView.setAlpha(selectedPartnerId == null ? 1.0f : 0.4f);
        }

        // 4. Xử lý Click: Không mở Intent nữa, mà báo cáo về Fragment
        h.itemView.setOnClickListener(v -> {
            if (listener != null && partnerId != null) {
                listener.onShortcutClick(partnerId);
            }
        });
    }

    @Override
    public int getItemCount() {
        return list.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvAvatar, tvName;
        ImageView ivAvatar;

        VH(@NonNull View v) {
            super(v);
            tvAvatar = v.findViewById(R.id.tvShortcutAvatar);
            ivAvatar = v.findViewById(R.id.ivShortcutAvatar);
            tvName = v.findViewById(R.id.tvShortcutName);
        }
    }
}