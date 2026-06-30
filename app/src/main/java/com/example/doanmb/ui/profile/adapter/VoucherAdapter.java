package com.example.doanmb.ui.profile.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.doanmb.R;
import com.example.doanmb.data.model.Voucher;

import java.util.List;

/** Hiển thị danh mục voucher có thể đổi bằng điểm thưởng. */
public class VoucherAdapter extends RecyclerView.Adapter<VoucherAdapter.VoucherViewHolder> {

    /** Callback khi người dùng bấm nút "Đổi" trên một voucher. */
    public interface OnRedeemClick {
        void onRedeem(Voucher voucher);
    }

    private final List<Voucher> items;
    private final long currentPoints;
    private final OnRedeemClick listener;

    public VoucherAdapter(List<Voucher> items, long currentPoints, OnRedeemClick listener) {
        this.items = items;
        this.currentPoints = currentPoints;
        this.listener = listener;
    }

    @NonNull
    @Override
    public VoucherViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_voucher_catalog, parent, false);
        return new VoucherViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VoucherViewHolder h, int position) {
        Voucher voucher = items.get(position);

        h.tvTitle.setText(voucher.getTitle() != null ? voucher.getTitle() : voucher.shortLabel());
        h.tvDesc.setText(voucher.getDescription() != null && !voucher.getDescription().isEmpty()
                ? voucher.getDescription() : voucher.shortLabel());
        h.tvPoints.setText("⭐ " + voucher.getPointsCost() + " điểm");

        boolean enough = currentPoints >= voucher.getPointsCost();
        h.btnRedeem.setEnabled(enough);
        h.btnRedeem.setAlpha(enough ? 1f : 0.5f);
        h.btnRedeem.setText(enough ? "Đổi" : "Thiếu điểm");
        h.btnRedeem.setOnClickListener(v -> {
            if (listener != null && enough) listener.onRedeem(voucher);
        });
    }

    @Override
    public int getItemCount() { return items.size(); }

    static class VoucherViewHolder extends RecyclerView.ViewHolder {
        TextView tvTitle, tvDesc, tvPoints;
        Button btnRedeem;
        VoucherViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTitle    = itemView.findViewById(R.id.tv_voucher_title);
            tvDesc     = itemView.findViewById(R.id.tv_voucher_desc);
            tvPoints   = itemView.findViewById(R.id.tv_voucher_points);
            btnRedeem  = itemView.findViewById(R.id.btn_redeem);
        }
    }
}