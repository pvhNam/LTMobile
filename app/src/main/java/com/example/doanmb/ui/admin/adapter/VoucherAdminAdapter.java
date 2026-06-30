package com.example.doanmb.ui.admin.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.doanmb.R;
import com.example.doanmb.data.model.Voucher;
import com.example.doanmb.ui.admin.util.AdminFormat;

import java.util.List;

/** Danh sách voucher trong catalog cho admin: sửa, bật/tắt, xoá. */
public class VoucherAdminAdapter extends RecyclerView.Adapter<VoucherAdminAdapter.ViewHolder> {

    public interface OnVoucherActionListener {
        void onEdit(Voucher voucher);
        void onToggleActive(Voucher voucher);
        void onDelete(Voucher voucher);
    }

    private List<Voucher> vouchers;
    private final OnVoucherActionListener listener;

    public VoucherAdminAdapter(List<Voucher> vouchers, OnVoucherActionListener listener) {
        this.vouchers = vouchers;
        this.listener = listener;
    }

    public void updateList(List<Voucher> newList) {
        this.vouchers = newList;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_voucher_admin, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Voucher vc = vouchers.get(position);

        holder.tvTitle.setText(vc.getTitle() != null ? vc.getTitle() : "(Không tên)");
        holder.tvDiscount.setText(vc.shortLabel());

        String desc = vc.getDescription();
        if (desc != null && !desc.isEmpty()) {
            holder.tvDescription.setText(desc);
            holder.tvDescription.setVisibility(View.VISIBLE);
        } else {
            holder.tvDescription.setVisibility(View.GONE);
        }

        String qty = vc.getQuantity() < 0 ? "Không giới hạn" : vc.getQuantity() + " lượt";
        String minOrder = vc.getMinOrderAmount() > 0 ? "Đơn từ " + AdminFormat.money(vc.getMinOrderAmount()) : "Mọi đơn";
        String valid = vc.getValidDays() > 0 ? "HSD " + vc.getValidDays() + " ngày" : "Không hết hạn";
        holder.tvMeta.setText(vc.getPointsCost() + " điểm  ·  " + qty + "  ·  " + minOrder + "  ·  " + valid);

        // Trạng thái
        if (vc.isActive()) {
            holder.tvStatus.setText("Đang mở");
            holder.tvStatus.setBackgroundColor(0xFFE8F5E9);
            holder.tvStatus.setTextColor(0xFF2E7D32);
            holder.btnToggle.setText("Tạm tắt");
        } else {
            holder.tvStatus.setText("Đã tắt");
            holder.tvStatus.setBackgroundColor(0xFFEEEEEE);
            holder.tvStatus.setTextColor(0xFF757575);
            holder.btnToggle.setText("Mở lại");
        }

        holder.btnEdit.setOnClickListener(v -> { if (listener != null) listener.onEdit(vc); });
        holder.btnToggle.setOnClickListener(v -> { if (listener != null) listener.onToggleActive(vc); });
        holder.btnDelete.setOnClickListener(v -> { if (listener != null) listener.onDelete(vc); });
    }

    @Override
    public int getItemCount() { return vouchers.size(); }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvTitle, tvDiscount, tvDescription, tvMeta, tvStatus;
        Button btnEdit, btnToggle, btnDelete;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTitle = itemView.findViewById(R.id.tv_voucher_title);
            tvDiscount = itemView.findViewById(R.id.tv_voucher_discount);
            tvDescription = itemView.findViewById(R.id.tv_voucher_description);
            tvMeta = itemView.findViewById(R.id.tv_voucher_meta);
            tvStatus = itemView.findViewById(R.id.tv_voucher_status);
            btnEdit = itemView.findViewById(R.id.btn_voucher_edit);
            btnToggle = itemView.findViewById(R.id.btn_voucher_toggle);
            btnDelete = itemView.findViewById(R.id.btn_voucher_delete);
        }
    }
}
