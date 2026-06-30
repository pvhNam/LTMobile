package com.example.doanmb.ui.admin.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.doanmb.R;
import com.example.doanmb.data.model.User;

import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;

public class DriverApprovalAdapter extends RecyclerView.Adapter<DriverApprovalAdapter.VH> {

    public interface OnItemClickListener {
        void onClick(User u);
    }

    private final List<User> pending;
    private OnItemClickListener clickListener;

    private static final SimpleDateFormat SDF =
            new SimpleDateFormat("HH:mm  dd/MM/yyyy", Locale.getDefault());

    public DriverApprovalAdapter(List<User> pending) {
        this.pending = pending;
    }

    public void setOnItemClickListener(OnItemClickListener listener) {
        this.clickListener = listener;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_driver_pending, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        User u = pending.get(position);
        h.tvName.setText(u.getName() != null ? u.getName() : "(không tên)");

        if (u.getAppliedAt() != null) {
            h.tvAppliedAt.setText("Gửi lúc: " + SDF.format(u.getAppliedAt().toDate()));
        } else {
            h.tvAppliedAt.setText("Gửi lúc: --");
        }

        h.itemView.setOnClickListener(v -> {
            if (clickListener != null) clickListener.onClick(u);
        });
    }

    @Override
    public int getItemCount() { return pending.size(); }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvName, tvAppliedAt;

        VH(@NonNull View v) {
            super(v);
            tvName      = v.findViewById(R.id.tv_dp_name);
            tvAppliedAt = v.findViewById(R.id.tv_dp_applied_at);
        }
    }
}
