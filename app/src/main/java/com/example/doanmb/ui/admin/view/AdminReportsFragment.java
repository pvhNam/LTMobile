package com.example.doanmb.ui.admin.view;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.doanmb.R;
import com.example.doanmb.ui.admin.adapter.ReportAdminAdapter;
import com.example.doanmb.ui.admin.util.AdminTab;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class AdminReportsFragment extends Fragment {

    private RecyclerView rvReports;
    private TextView tvReportCount, tvEmpty;
    private Button btnTabPending, btnTabAll;
    private ReportAdminAdapter adapter;
    private List<Map<String, Object>> reportList = new ArrayList<>();
    private List<String> reportIds = new ArrayList<>();
    private FirebaseFirestore db;
    private boolean showingPending = true;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_admin_reports, container, false);
        db = FirebaseFirestore.getInstance();

        rvReports = view.findViewById(R.id.rv_reports);
        tvReportCount = view.findViewById(R.id.tv_report_count);
        tvEmpty = view.findViewById(R.id.tv_empty_reports);
        btnTabPending = view.findViewById(R.id.btn_tab_report_pending);
        btnTabAll = view.findViewById(R.id.btn_tab_report_all);

        adapter = new ReportAdminAdapter(reportList, reportIds, new ReportAdminAdapter.OnReportActionListener() {
            @Override
            public void onResolve(String reportId) { updateReportStatus(reportId, "resolved"); }
            @Override
            public void onDismiss(String reportId) { updateReportStatus(reportId, "dismissed"); }
        });
        rvReports.setLayoutManager(new LinearLayoutManager(getContext()));
        rvReports.setAdapter(adapter);

        btnTabPending.setOnClickListener(v -> switchTab(true));
        btnTabAll.setOnClickListener(v -> switchTab(false));

        loadReports();
        return view;
    }

    private void switchTab(boolean pending) {
        showingPending = pending;
        AdminTab.select(pending ? btnTabPending : btnTabAll, btnTabPending, btnTabAll);
        loadReports();
    }

    private void loadReports() {
        if (showingPending) {
            db.collection("reports").whereEqualTo("status", "pending").get()
                    .addOnSuccessListener(this::processDocs);
        } else {
            db.collection("reports").get()
                    .addOnSuccessListener(this::processDocs);
        }
    }

    private void processDocs(com.google.firebase.firestore.QuerySnapshot snapshots) {
        if (!isAdded()) return;
        reportList.clear();
        reportIds.clear();
        for (QueryDocumentSnapshot doc : snapshots) {
            reportList.add(doc.getData());
            reportIds.add(doc.getId());
        }
        adapter.updateList(reportList, reportIds);
        String label = showingPending
                ? reportList.size() + " chờ xử lý"
                : reportList.size() + " khiếu nại";
        tvReportCount.setText(label);
        String emptyMsg = showingPending ? "Không có khiếu nại nào chờ xử lý" : "Chưa có khiếu nại nào";
        tvEmpty.setText(emptyMsg);
        tvEmpty.setVisibility(reportList.isEmpty() ? View.VISIBLE : View.GONE);
        rvReports.setVisibility(reportList.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private void updateReportStatus(String reportId, String newStatus) {
        db.collection("reports").document(reportId)
                .update("status", newStatus)
                .addOnSuccessListener(v -> {
                    if (!isAdded()) return;
                    String msg = "resolved".equals(newStatus) ? "✅ Đã đánh dấu xử lý" : "Đã bỏ qua khiếu nại";
                    Toast.makeText(getContext(), msg, Toast.LENGTH_SHORT).show();
                    loadReports();
                })
                .addOnFailureListener(e -> Toast.makeText(getContext(), "Lỗi: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    @Override
    public void onResume() {
        super.onResume();
        loadReports();
    }
}
