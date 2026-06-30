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
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.doanmb.R;
import com.example.doanmb.ui.admin.adapter.ReportAdminAdapter;
import com.example.doanmb.ui.admin.util.AdminTab;
import com.example.doanmb.ui.admin.viewmodel.AdminDocList;
import com.example.doanmb.ui.admin.viewmodel.AdminReportsViewModel;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Xử lý khiếu nại: lọc theo tab (chờ xử lý / tất cả), đánh dấu xử lý/bỏ qua, xoá bài bị báo cáo qua {@link AdminReportsViewModel}. */
public class AdminReportsFragment extends Fragment {

    private RecyclerView rvReports;
    private TextView tvReportCount, tvEmpty;
    private Button btnTabPending, btnTabAll;
    private ReportAdminAdapter adapter;
    private final List<Map<String, Object>> reportList = new ArrayList<>();
    private final List<String> reportIds = new ArrayList<>();
    private AdminReportsViewModel viewModel;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_admin_reports, container, false);
        viewModel = new ViewModelProvider(this).get(AdminReportsViewModel.class);

        view.findViewById(R.id.btn_reports_back).setOnClickListener(v -> {
            if (getParentFragmentManager().getBackStackEntryCount() > 0)
                getParentFragmentManager().popBackStack();
        });

        rvReports = view.findViewById(R.id.rv_reports);
        tvReportCount = view.findViewById(R.id.tv_report_count);
        tvEmpty = view.findViewById(R.id.tv_empty_reports);
        btnTabPending = view.findViewById(R.id.btn_tab_report_pending);
        btnTabAll = view.findViewById(R.id.btn_tab_report_all);

        adapter = new ReportAdminAdapter(reportList, reportIds, new ReportAdminAdapter.OnReportActionListener() {
            @Override public void onResolve(String reportId) { viewModel.updateStatus(reportId, "resolved"); }
            @Override public void onDismiss(String reportId) { viewModel.updateStatus(reportId, "dismissed"); }
            @Override public void onDeletePost(String reportId, String targetId) { confirmDeletePost(reportId, targetId); }
        });
        rvReports.setLayoutManager(new LinearLayoutManager(getContext()));
        rvReports.setAdapter(adapter);

        btnTabPending.setOnClickListener(v -> viewModel.setTab(true));
        btnTabAll.setOnClickListener(v -> viewModel.setTab(false));

        viewModel.getReports().observe(getViewLifecycleOwner(), this::render);
        viewModel.getMessage().observe(getViewLifecycleOwner(), msg -> {
            if (msg != null) Toast.makeText(getContext(), msg, Toast.LENGTH_SHORT).show();
        });

        viewModel.load();
        return view;
    }

    private void render(AdminDocList list) {
        boolean pending = viewModel.isShowingPending();
        AdminTab.select(pending ? btnTabPending : btnTabAll, btnTabPending, btnTabAll);

        reportList.clear(); reportList.addAll(list.data);
        reportIds.clear(); reportIds.addAll(list.ids);
        adapter.updateList(reportList, reportIds);

        tvReportCount.setText(pending ? list.size() + " chờ xử lý" : list.size() + " khiếu nại");
        tvEmpty.setText(pending ? "Không có khiếu nại nào chờ xử lý" : "Chưa có khiếu nại nào");
        tvEmpty.setVisibility(list.isEmpty() ? View.VISIBLE : View.GONE);
        rvReports.setVisibility(list.isEmpty() ? View.GONE : View.VISIBLE);
    }

    /** Hỏi xác nhận trước khi xóa hẳn bài đăng bị báo cáo. */
    private void confirmDeletePost(String reportId, String targetId) {
        if (targetId == null || targetId.isEmpty()) {
            Toast.makeText(getContext(), "Không tìm thấy bài đăng để xóa", Toast.LENGTH_SHORT).show();
            return;
        }
        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle("Xóa bài đăng")
                .setMessage("Bạn có chắc muốn xóa hẳn bài đăng bị báo cáo này? Thao tác không thể hoàn tác.")
                .setPositiveButton("Xóa", (d, w) -> viewModel.deletePost(reportId, targetId))
                .setNegativeButton("Hủy", null)
                .show();
    }

    @Override
    public void onResume() {
        super.onResume();
        viewModel.load();
    }
}
