package com.example.doanmb.ui.admin.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.doanmb.R;

import java.util.List;
import java.util.Map;

public class ReportAdminAdapter extends RecyclerView.Adapter<ReportAdminAdapter.ViewHolder> {

    public interface OnReportActionListener {
        void onResolve(String reportId);
        void onDismiss(String reportId);
    }

    private List<Map<String, Object>> reports;
    private List<String> reportIds;
    private OnReportActionListener listener;

    public ReportAdminAdapter(List<Map<String, Object>> reports, List<String> reportIds, OnReportActionListener listener) {
        this.reports = reports;
        this.reportIds = reportIds;
        this.listener = listener;
    }

    public void updateList(List<Map<String, Object>> newReports, List<String> newIds) {
        this.reports = newReports;
        this.reportIds = newIds;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_report_admin, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Map<String, Object> report = reports.get(position);
        String reportId = reportIds.get(position);

        String carName = getStr(report, "targetName", "Xe không xác định");
        String reason = getStr(report, "reason", "Không rõ lý do");
        String description = getStr(report, "description", "");
        String reporterName = getStr(report, "reporterName", "Ẩn danh");
        String status = getStr(report, "status", "pending");

        holder.tvCarName.setText(carName);
        holder.tvReason.setText(reason);
        holder.tvReportBy.setText("Người báo cáo: " + reporterName);

        if (!description.isEmpty()) {
            holder.tvDescription.setText(description);
            holder.tvDescription.setVisibility(View.VISIBLE);
        } else {
            holder.tvDescription.setVisibility(View.GONE);
        }

        applyStatusStyle(holder.tvStatus, status);

        // Ẩn nút xử lý nếu đã xử lý
        boolean isActionable = "pending".equals(status);
        holder.layoutActions.setVisibility(isActionable ? View.VISIBLE : View.GONE);

        if (isActionable) {
            holder.btnResolve.setOnClickListener(v -> { if (listener != null) listener.onResolve(reportId); });
            holder.btnDismiss.setOnClickListener(v -> { if (listener != null) listener.onDismiss(reportId); });
        }
    }

    private void applyStatusStyle(TextView tv, String status) {
        switch (status) {
            case "pending":
                tv.setText("Chờ xử lý");
                tv.setBackgroundColor(0xFFFFF3E0);
                tv.setTextColor(0xFFE65100);
                break;
            case "resolved":
                tv.setText("Đã xử lý");
                tv.setBackgroundColor(0xFFE8F5E9);
                tv.setTextColor(0xFF2E7D32);
                break;
            case "dismissed":
                tv.setText("Bỏ qua");
                tv.setBackgroundColor(0xFFEEEEEE);
                tv.setTextColor(0xFF757575);
                break;
        }
    }

    private String getStr(Map<String, Object> map, String key, String def) {
        Object v = map.get(key);
        return (v != null) ? v.toString() : def;
    }

    @Override
    public int getItemCount() { return reports.size(); }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvCarName, tvReason, tvDescription, tvReportBy, tvStatus;
        LinearLayout layoutActions;
        Button btnResolve, btnDismiss;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvCarName = itemView.findViewById(R.id.tv_report_car_name);
            tvReason = itemView.findViewById(R.id.tv_report_reason);
            tvDescription = itemView.findViewById(R.id.tv_report_description);
            tvReportBy = itemView.findViewById(R.id.tv_report_by);
            tvStatus = itemView.findViewById(R.id.tv_report_status);
            layoutActions = itemView.findViewById(R.id.layout_report_actions);
            btnResolve = itemView.findViewById(R.id.btn_resolve_report);
            btnDismiss = itemView.findViewById(R.id.btn_dismiss_report);
        }
    }
}
