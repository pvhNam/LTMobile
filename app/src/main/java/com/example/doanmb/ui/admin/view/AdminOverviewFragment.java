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

import com.example.doanmb.R;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

public class AdminOverviewFragment extends Fragment {

    private TextView tvTotalRevenue, tvDriverPendingCount;
    private View btnViewUsers, btnViewCars, btnViewDriverApproval;
    private BarChart barChart;
    private FirebaseFirestore db;

    public interface OnQuickNavListener {
        void navigateTo(int itemId);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_admin_overview, container, false);
        db = FirebaseFirestore.getInstance();

        tvTotalRevenue      = view.findViewById(R.id.tv_total_revenue);
        tvDriverPendingCount = view.findViewById(R.id.tv_driver_pending_count);

        btnViewUsers         = view.findViewById(R.id.btn_view_users);
        btnViewCars          = view.findViewById(R.id.btn_view_cars);
        btnViewDriverApproval = view.findViewById(R.id.btn_view_driver_approval);
        View btnViewOrders   = view.findViewById(R.id.btn_view_orders);
        View btnViewTopup    = view.findViewById(R.id.btn_view_topup_history);
        barChart = view.findViewById(R.id.bar_chart_revenue);

        setupChart();
        loadRevenue();
        loadDriverPendingCount();

        view.findViewById(R.id.tv_view_revenue_detail).setOnClickListener(v ->
                startActivity(new Intent(getActivity(), AdminRevenueDetailActivity.class)));

        btnViewUsers.setOnClickListener(v -> navigate(R.id.nav_admin_users));
        btnViewCars.setOnClickListener(v -> navigate(R.id.nav_admin_cars));
        btnViewOrders.setOnClickListener(v -> navigate(R.id.nav_admin_orders));
        btnViewDriverApproval.setOnClickListener(v -> navigate(R.id.nav_admin_driver_approval));
        btnViewTopup.setOnClickListener(v ->
                startActivity(new Intent(getActivity(), AdminTopupHistoryActivity.class)));

        return view;
    }

    private void navigate(int itemId) {
        if (getActivity() instanceof OnQuickNavListener) {
            ((OnQuickNavListener) getActivity()).navigateTo(itemId);
        }
    }

    private static final double SALE_COMMISSION   = 0.03;   // 3% giá xe
    private static final double RENTAL_COMMISSION = 0.15;   // 15% phí thuê
    private static final long   POSTING_FEE       = 200_000; // 200K/bài đăng

    private void loadRevenue() {
        db.collection("orders").whereEqualTo("status", "confirmed").get()
                .addOnSuccessListener(orderSnap -> {
                    if (!isAdded()) return;

                    long commissionRevenue = 0;

                    for (QueryDocumentSnapshot doc : orderSnap) {
                        String type        = doc.getString("type");
                        long   pricePerUnit = parsePrice(doc.getString("carPrice"));
                        long   commission;

                        if ("Thuê xe".equals(type)) {
                            long days = 1;
                            String daysStr = doc.getString("days");
                            if (daysStr != null && !daysStr.isEmpty()) {
                                try {
                                    long d = Long.parseLong(daysStr.replaceAll("[^0-9]", ""));
                                    if (d > 0) days = d;
                                } catch (NumberFormatException ignored) {}
                            }
                            commission = (long)(pricePerUnit * days * RENTAL_COMMISSION);
                        } else {
                            commission = (long)(pricePerUnit * SALE_COMMISSION);
                        }

                        commissionRevenue += commission;
                    }

                    final long commissionFinal = commissionRevenue;

                    // Chain: lấy số bài đăng để tính phí
                    db.collection("cars").get().addOnSuccessListener(carSnap -> {
                        if (!isAdded()) return;
                        int carCount = carSnap.size();
                        long postingFee   = carCount * POSTING_FEE;
                        long grandTotal   = commissionFinal + postingFee;

                        tvTotalRevenue.setText(formatRevenue(grandTotal));
                    });
                });
    }

    private long parsePrice(String priceStr) {
        if (priceStr == null || priceStr.isEmpty()) return 0;
        String digits = priceStr.replaceAll("[^0-9]", "");
        if (digits.isEmpty()) return 0;
        try {
            return Long.parseLong(digits);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private String formatRevenue(long amount) {
        if (amount == 0) return "0 VNĐ";
        if (amount >= 1_000_000_000L) {
            return String.format("%.1f tỷ", amount / 1_000_000_000.0);
        } else if (amount >= 1_000_000L) {
            return String.format("%.0f triệu", amount / 1_000_000.0);
        } else {
            return (amount / 1_000) + "K VNĐ";
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

        loadChartData();
    }

    private void loadDriverPendingCount() {
        db.collection("users").whereEqualTo("driverStatus", "pending").get()
                .addOnSuccessListener(snap -> {
                    if (!isAdded() || tvDriverPendingCount == null) return;
                    int count = snap.size();
                    if (count == 0) {
                        tvDriverPendingCount.setText("Không có hồ sơ đang chờ");
                    } else {
                        tvDriverPendingCount.setText(count + " hồ sơ đang chờ duyệt");
                        tvDriverPendingCount.setTextColor(0xFFC62828);
                    }
                });
    }

    private void loadChartData() {
        db.collection("orders").whereEqualTo("status", "confirmed").get()
                .addOnSuccessListener(snapshots -> {
                    if (!isAdded()) return;

                    Calendar now = Calendar.getInstance();
                    // Xây mảng 6 tháng gần nhất (index 0 = cũ nhất)
                    long[] monthRevenue = new long[6];
                    String[] monthLabels = new String[6];
                    String[] monthNames = {"T1","T2","T3","T4","T5","T6","T7","T8","T9","T10","T11","T12"};

                    for (int i = 5; i >= 0; i--) {
                        Calendar c = (Calendar) now.clone();
                        c.add(Calendar.MONTH, -i);
                        monthLabels[5 - i] = monthNames[c.get(Calendar.MONTH)];
                    }

                    for (QueryDocumentSnapshot doc : snapshots) {
                        Timestamp ts = doc.getTimestamp("createdAt");
                        if (ts == null) continue;

                        Calendar orderCal = Calendar.getInstance();
                        orderCal.setTimeInMillis(ts.toDate().getTime());

                        for (int i = 5; i >= 0; i--) {
                            Calendar c = (Calendar) now.clone();
                            c.add(Calendar.MONTH, -i);
                            if (orderCal.get(Calendar.MONTH) == c.get(Calendar.MONTH)
                                    && orderCal.get(Calendar.YEAR) == c.get(Calendar.YEAR)) {

                                String type = doc.getString("type");
                                long price  = parsePrice(doc.getString("carPrice"));
                                long commission;
                                if ("Thuê xe".equals(type)) {
                                    long days = 1;
                                    try {
                                        String d = doc.getString("days");
                                        if (d != null) days = Math.max(1, Long.parseLong(d.replaceAll("[^0-9]","")));
                                    } catch (Exception ignored) {}
                                    commission = (long)(price * days * RENTAL_COMMISSION);
                                } else {
                                    commission = (long)(price * SALE_COMMISSION);
                                }
                                monthRevenue[5 - i] += commission;
                                break;
                            }
                        }
                    }

                    List<BarEntry> entries = new ArrayList<>();
                    for (int i = 0; i < 6; i++) {
                        // Đổi sang triệu để trục Y gọn
                        entries.add(new BarEntry(i, monthRevenue[i] / 1_000f));
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

                    barChart.getXAxis().setValueFormatter(new IndexAxisValueFormatter(monthLabels));
                    barChart.getXAxis().setLabelCount(6);
                    barChart.setData(barData);
                    barChart.animateY(600);
                    barChart.invalidate();
                });
    }
}
