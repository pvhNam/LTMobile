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
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ReportAdminAdapter extends RecyclerView.Adapter<ReportAdminAdapter.ViewHolder> {

    public interface OnReportActionListener {
        void onResolve(String reportId);
        void onDismiss(String reportId);
        /** Admin chọn xóa hẳn bài đăng bị báo cáo (targetId = carId). */
        void onDeletePost(String reportId, String targetId);
    }

    private List<Map<String, Object>> reports;
    private List<String> reportIds;
    private OnReportActionListener listener;

    /** Cache tên user theo id để khỏi tra Firestore lặp lại khi cuộn danh sách. */
    private static final Map<String, String> NAME_CACHE = new HashMap<>();

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

        // Báo cáo từ chat chỉ lưu id (targetId/reporterId) -> tra tên trong "users".
        // Vẫn ưu tiên targetName/reporterName nếu sau này có ghi sẵn.
        String reason = getStr(report, "reason", "Không rõ lý do");
        String description = getStr(report, "description", "");
        String status = getStr(report, "status", "pending");

        holder.tvReason.setText(reason);
        bindName(holder.tvCarName, "",
                getStr(report, "targetName", null),   getStr(report, "targetId", null),   "Không xác định");
        bindName(holder.tvReportBy, "Người báo cáo: ",
                getStr(report, "reporterName", null), getStr(report, "reporterId", null), "Ẩn danh");

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

        // Nút "Xóa bài đăng" chỉ áp dụng cho report tin đăng xe (targetType == "car")
        String targetType = getStr(report, "targetType", "");
        String targetId   = getStr(report, "targetId", "");
        boolean canDeletePost = isActionable && "car".equals(targetType) && !targetId.isEmpty();
        holder.btnDeletePost.setVisibility(canDeletePost ? View.VISIBLE : View.GONE);

        if (isActionable) {
            holder.btnResolve.setOnClickListener(v -> { if (listener != null) listener.onResolve(reportId); });
            holder.btnDismiss.setOnClickListener(v -> { if (listener != null) listener.onDismiss(reportId); });
            holder.btnDeletePost.setOnClickListener(v -> { if (listener != null) listener.onDeletePost(reportId, targetId); });
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
            default:
                tv.setText(status);
                tv.setBackgroundColor(0xFFEEEEEE);
                tv.setTextColor(0xFF757575);
                break;
        }
    }

    /**
     * Hiển thị tên (đối tượng bị báo cáo / người báo cáo). Ưu tiên tên ghi sẵn,
     * nếu chỉ có id thì tra trong "users". Dùng setTag tránh sai tên khi RecyclerView
     * tái sử dụng view lúc cuộn.
     */
    private void bindName(TextView tv, String prefix, String name, String userId, String fallback) {
        if (name != null && !name.isEmpty()) {
            tv.setTag(null);
            tv.setText(prefix + name);
            return;
        }
        if (userId == null || userId.isEmpty()) {
            tv.setTag(null);
            tv.setText(prefix + fallback);
            return;
        }
        String cached = NAME_CACHE.get(userId);
        if (cached != null) {
            tv.setTag(null);
            tv.setText(prefix + cached);
            return;
        }
        tv.setTag(userId);
        tv.setText(prefix + "Đang tải…");
        FirebaseFirestore.getInstance().collection("users").document(userId).get()
                .addOnSuccessListener(u -> {
                    String fetched = u.exists() ? u.getString("name") : null;
                    if (fetched == null || fetched.isEmpty()) fetched = fallback;
                    NAME_CACHE.put(userId, fetched);
                    if (userId.equals(tv.getTag())) tv.setText(prefix + fetched);
                })
                .addOnFailureListener(e -> {
                    if (userId.equals(tv.getTag())) tv.setText(prefix + fallback);
                });
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
        Button btnResolve, btnDismiss, btnDeletePost;

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
            btnDeletePost = itemView.findViewById(R.id.btn_delete_post_report);
        }
    }
}
