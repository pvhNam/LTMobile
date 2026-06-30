package com.example.doanmb.ui.admin.view;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;
import com.github.mikephil.charting.formatter.ValueFormatter;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.example.doanmb.R;
import com.example.doanmb.ui.admin.util.AdminFormat;
import com.example.doanmb.ui.admin.viewmodel.AdminOverviewViewModel;

import java.util.ArrayList;
import java.util.List;

/** Trang chủ admin: tổng doanh thu, biểu đồ 6 tháng, hồ sơ tài xế chờ duyệt và các nút truy cập nhanh. */
public class AdminOverviewFragment extends Fragment {

    private TextView tvTotalRevenue, tvDriverPendingCount;
    private BarChart barChart;
    private AdminOverviewViewModel viewModel;

    public interface OnQuickNavListener {
        void navigateTo(int itemId);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_admin_overview, container, false);
        viewModel = new ViewModelProvider(this).get(AdminOverviewViewModel.class);

        tvTotalRevenue       = view.findViewById(R.id.tv_total_revenue);
        tvDriverPendingCount = view.findViewById(R.id.tv_driver_pending_count);

        View btnViewCars     = view.findViewById(R.id.btn_view_cars);
        View btnViewDriverApproval = view.findViewById(R.id.btn_view_driver_approval);
        View btnViewOrders   = view.findViewById(R.id.btn_view_orders);
        View btnViewReports  = view.findViewById(R.id.btn_view_reports);
        View btnViewVouchers = view.findViewById(R.id.btn_view_vouchers);
        barChart = view.findViewById(R.id.bar_chart_revenue);

        setupChart();

        view.findViewById(R.id.tv_view_revenue_detail).setOnClickListener(v ->
                startActivity(new Intent(getActivity(), AdminRevenueDetailActivity.class)));

        btnViewCars.setOnClickListener(v -> navigate(R.id.nav_admin_cars));
        btnViewOrders.setOnClickListener(v -> navigate(R.id.nav_admin_orders));
        btnViewReports.setOnClickListener(v -> navigate(R.id.nav_admin_reports));
        btnViewVouchers.setOnClickListener(v -> navigate(R.id.nav_admin_vouchers));
        btnViewDriverApproval.setOnClickListener(v -> navigate(R.id.nav_admin_driver_approval));

        observeViewModel();
        viewModel.refresh();
        return view;
    }

    private void observeViewModel() {
        viewModel.getTotalRevenue().observe(getViewLifecycleOwner(), total -> {
            if (total != null) tvTotalRevenue.setText(AdminFormat.money(total));
        });
        viewModel.getDriverPendingCount().observe(getViewLifecycleOwner(), count -> {
            if (count == null) return;
            if (count == 0) {
                tvDriverPendingCount.setText("Không có hồ sơ đang chờ");
            } else {
                tvDriverPendingCount.setText(count + " hồ sơ đang chờ duyệt");
                tvDriverPendingCount.setTextColor(0xFFC62828);
            }
        });
        viewModel.getChartData().observe(getViewLifecycleOwner(), this::renderChart);
    }

    private void navigate(int itemId) {
        if (getActivity() instanceof OnQuickNavListener) {
            ((OnQuickNavListener) getActivity()).navigateTo(itemId);
        }
    }

    private void setupChart() {
        barChart.setDrawGridBackground(false);
        barChart.getDescription().setEnabled(false);
        barChart.getLegend().setEnabled(false);
        barChart.setTouchEnabled(false);
        barChart.setExtraBottomOffset(8f);

        XAxis xAxis = barChart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setDrawGridLines(false);
        xAxis.setGranularity(1f);
        xAxis.setTextColor(0xFF757575);
        xAxis.setTextSize(11f);

        YAxis left = barChart.getAxisLeft();
        left.setDrawGridLines(true);
        left.setGridColor(0xFFEEEEEE);
        left.setTextColor(0xFF757575);
        left.setTextSize(10f);
        left.setAxisMinimum(0f);
        left.setValueFormatter(new ValueFormatter() {
            @Override public String getFormattedValue(float value) {
                if (value >= 1_000f) return String.format("%.0ftr", value / 1_000f);
                return String.format("%.0f", value);
            }
        });

        barChart.getAxisRight().setEnabled(false);
    }

    private void renderChart(AdminOverviewViewModel.ChartData data) {
        if (data == null) return;

        List<BarEntry> entries = new ArrayList<>();
        for (int i = 0; i < data.valuesK.length; i++) {
            entries.add(new BarEntry(i, data.valuesK[i]));
        }

        BarDataSet dataSet = new BarDataSet(entries, "Doanh thu");
        dataSet.setColor(0xFFC62828);
        dataSet.setValueTextColor(0xFF212121);
        dataSet.setValueTextSize(9f);
        dataSet.setValueFormatter(new ValueFormatter() {
            @Override public String getFormattedValue(float value) {
                if (value == 0) return "";
                if (value >= 1_000f) return String.format("%.1ftr", value / 1_000f);
                return String.format("%.0fK", value);
            }
        });

        BarData barData = new BarData(dataSet);
        barData.setBarWidth(0.55f);

        barChart.getXAxis().setValueFormatter(new IndexAxisValueFormatter(data.labels));
        barChart.getXAxis().setLabelCount(data.labels.length);
        barChart.setData(barData);
        barChart.animateY(600);
        barChart.invalidate();
    }
}
