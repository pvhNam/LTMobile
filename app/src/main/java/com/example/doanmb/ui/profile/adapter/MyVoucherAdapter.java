package com.example.doanmb.ui.profile.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.doanmb.R;
import com.example.doanmb.data.model.UserVoucher;
import com.google.firebase.Timestamp;

import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;

/** Hiển thị danh sách voucher mà người dùng đã đổi (ví voucher). */
public class MyVoucherAdapter extends RecyclerView.Adapter<MyVoucherAdapter.MyVoucherViewHolder> {

    private static final int COLOR_AVAILABLE = 0xFF0E8C91; // teal: còn dùng được
    private static final int COLOR_USED      = 0xFF9E9E9E; // xám: đã dùng
    private static final int COLOR_EXPIRED   = 0xFFE53935; // đỏ: hết hạn

    private final List<UserVoucher> items;

    public MyVoucherAdapter(List<UserVoucher> items) {
        this.items = items;
    }

    @NonNull
    @Override
    public MyVoucherViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_my_voucher, parent, false);
        return new MyVoucherViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull MyVoucherViewHolder h, int position) {
        UserVoucher uv = items.get(position);

        h.tvTitle.setText(uv.getTitle() != null ? uv.getTitle() : uv.shortLabel());

        Timestamp exp = uv.getExpiresAt();
        String expText = exp != null
                ? "Hạn dùng: " + new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(exp.toDate())
                : "Không giới hạn ngày dùng";
        h.tvStatus.setText(expText);

        if (uv.isUsed()) {
            h.tvBadge.setText("Đã dùng");
            h.tvBadge.setTextColor(COLOR_USED);
            h.itemView.setAlpha(0.6f);
        } else if (uv.isExpired()) {
            h.tvBadge.setText("Hết hạn");
            h.tvBadge.setTextColor(COLOR_EXPIRED);
            h.itemView.setAlpha(0.6f);
        } else {
            h.tvBadge.setText("Còn dùng");
            h.tvBadge.setTextColor(COLOR_AVAILABLE);
            h.itemView.setAlpha(1f);
        }
    }

    @Override
    public int getItemCount() { return items.size(); }

    static class MyVoucherViewHolder extends RecyclerView.ViewHolder {
        TextView tvTitle, tvStatus, tvBadge;
        MyVoucherViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTitle  = itemView.findViewById(R.id.tv_my_voucher_title);
            tvStatus = itemView.findViewById(R.id.tv_my_voucher_status);
            tvBadge  = itemView.findViewById(R.id.tv_my_voucher_badge);
        }
    }
}