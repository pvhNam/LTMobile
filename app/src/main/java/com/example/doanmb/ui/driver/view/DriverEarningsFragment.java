package com.example.doanmb.ui.driver.view;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.bumptech.glide.Glide;
import com.example.doanmb.R;
import com.example.doanmb.data.model.Trip;
import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;
import com.github.mikephil.charting.formatter.ValueFormatter;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.tabs.TabLayout;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import de.hdodenhof.circleimageview.CircleImageView;

/**
 * Tab "Thu nhập" (thiết kế driver4): tổng quan theo kỳ, ví tài xế, biểu đồ
 * thu nhập theo ngày (MPAndroidChart) và tiến độ thưởng.
 */
public class DriverEarningsFragment extends Fragment {

    private TextView tvName, tvRevenue, tvTrips, tvBalance, tvRewardProgress;
    private CircleImageView ivAvatar;
    private BarChart chart;
    private ProgressBar progressReward;

    private FirebaseFirestore db;
    private String uid;
    private int currentPeriod = 0; // 0 hôm nay, 1 tuần, 2 tháng

    /** [completedAtMillis, price] của các chuyến đã hoàn thành. */
    private final List<double[]> completed = new ArrayList<>();

    private static final DecimalFormat MONEY = new DecimalFormat("#,###");

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_driver_earnings, container, false);
        db = FirebaseFirestore.getInstance();
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        uid = user != null ? user.getUid() : "";

        tvName = v.findViewById(R.id.tv_dh_name);
        ivAvatar = v.findViewById(R.id.iv_dh_avatar);
        tvRevenue = v.findViewById(R.id.tv_e_revenue);
        tvTrips = v.findViewById(R.id.tv_e_trips);
        tvBalance = v.findViewById(R.id.tv_e_balance);
        tvRewardProgress = v.findViewById(R.id.tv_reward_progress);
        progressReward = v.findViewById(R.id.progress_reward);
        chart = v.findViewById(R.id.chart_income);

        TabLayout tab = v.findViewById(R.id.tab_period);
        tab.addTab(tab.newTab().setText("Hôm nay"));
        tab.addTab(tab.newTab().setText("Tuần này"));
        tab.addTab(tab.newTab().setText("Tháng này"));
        tab.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override public void onTabSelected(TabLayout.Tab t) { currentPeriod = t.getPosition(); refreshPeriod(); }
            @Override public void onTabUnselected(TabLayout.Tab t) {}
            @Override public void onTabReselected(TabLayout.Tab t) {}
        });

        MaterialButton btnWithdraw = v.findViewById(R.id.btn_withdraw);
        MaterialButton btnTxn = v.findViewById(R.id.btn_txn_history);
        btnWithdraw.setOnClickListener(x ->
                Toast.makeText(getContext(), "Rút tiền: liên hệ admin để duyệt.", Toast.LENGTH_SHORT).show());
        btnTxn.setOnClickListener(x ->
                Toast.makeText(getContext(), "Lịch sử giao dịch đang được phát triển.", Toast.LENGTH_SHORT).show());

        setupChart();
        return v;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (uid.isEmpty()) return;
        db.collection("users").document(uid).get().addOnSuccessListener(doc -> {
            if (!isAdded()) return;
            tvName.setText(doc.getString("name") != null ? doc.getString("name") : "Tài xế");
            String avatar = doc.getString("avatarUrl");
            if (avatar != null && !avatar.isEmpty()) Glide.with(this).load(avatar).into(ivAvatar);
            Double balance = doc.getDouble("balance");
            tvBalance.setText(MONEY.format(balance != null ? balance : 0) + "đ");
        });
        loadCompleted();
    }

    private void loadCompleted() {
        db.collection("trips").whereEqualTo("driverId", uid)
                .whereEqualTo("status", Trip.STATUS_COMPLETED).get()
                .addOnSuccessListener(snap -> {
                    if (!isAdded()) return;
                    completed.clear();
                    for (QueryDocumentSnapshot d : snap) {
                        Timestamp done = d.getTimestamp("completedAt");
                        if (done == null) continue;
                        Double p = d.getDouble("price");
                        completed.add(new double[]{done.toDate().getTime(), p != null ? p : 0});
                    }
                    refreshPeriod();
                    refreshChart();
                    refreshReward();
                });
    }

    /** Doanh thu + số chuyến của kỳ đang chọn. */
    private void refreshPeriod() {
        long start = periodStart(currentPeriod);
        double revenue = 0;
        int trips = 0;
        for (double[] t : completed) {
            if (t[0] >= start) { revenue += t[1]; trips++; }
        }
        tvRevenue.setText(MONEY.format(revenue) + "đ");
        tvTrips.setText(String.valueOf(trips));
    }

    private long periodStart(int period) {
        Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        if (period == 1) c.add(Calendar.DAY_OF_MONTH, -6);      // tuần (7 ngày gần nhất)
        else if (period == 2) c.set(Calendar.DAY_OF_MONTH, 1);  // tháng
        return c.getTimeInMillis();
    }

    /** Tiến độ thưởng = số chuyến 7 ngày gần nhất / 10. */
    private void refreshReward() {
        long start = periodStart(1);
        int weekTrips = 0;
        for (double[] t : completed) if (t[0] >= start) weekTrips++;
        progressReward.setProgress(Math.min(weekTrips, 10));
        tvRewardProgress.setText(weekTrips + " / 10 chuyến");
    }

    private void setupChart() {
        chart.getDescription().setEnabled(false);
        chart.getLegend().setEnabled(false);
        chart.setScaleEnabled(false);
        chart.setDragEnabled(false);
        chart.setPinchZoom(false);
        chart.setDrawGridBackground(false);
        chart.setDrawBorders(false);
        chart.setExtraBottomOffset(6f);

        XAxis x = chart.getXAxis();
        x.setPosition(XAxis.XAxisPosition.BOTTOM);
        x.setDrawGridLines(false);
        x.setDrawAxisLine(false);
        x.setGranularity(1f);
        x.setTextColor(0xFF9AA0A6);

        YAxis left = chart.getAxisLeft();
        left.setAxisMinimum(0f);
        left.setDrawAxisLine(false);
        left.setGridColor(0xFFEEF1F5);
        left.setTextColor(0xFF9AA0A6);
        left.setValueFormatter(new ValueFormatter() {
            @Override public String getFormattedValue(float value) {
                if (value >= 1000) return (int) (value / 1000) + "k";
                return String.valueOf((int) value);
            }
        });
        chart.getAxisRight().setEnabled(false);
    }

    /** Biểu đồ doanh thu 6 ngày gần nhất, cột hôm nay nổi bật. */
    private void refreshChart() {
        SimpleDateFormat fmt = new SimpleDateFormat("dd/MM", Locale.getDefault());
        List<BarEntry> entries = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        List<Integer> colors = new ArrayList<>();

        for (int i = 5; i >= 0; i--) {
            Calendar c = Calendar.getInstance();
            c.add(Calendar.DAY_OF_MONTH, -i);
            c.set(Calendar.HOUR_OF_DAY, 0);
            c.set(Calendar.MINUTE, 0);
            c.set(Calendar.SECOND, 0);
            c.set(Calendar.MILLISECOND, 0);
            long dayStart = c.getTimeInMillis();
            long dayEnd = dayStart + 24L * 60 * 60 * 1000;

            double sum = 0;
            for (double[] t : completed) if (t[0] >= dayStart && t[0] < dayEnd) sum += t[1];

            int idx = 5 - i;
            entries.add(new BarEntry(idx, (float) sum));
            labels.add(fmt.format(new Date(dayStart)));
            colors.add(i == 0 ? 0xFF2E6BF0 : 0xFFBBD2F7);
        }

        BarDataSet set = new BarDataSet(entries, "Thu nhập");
        set.setColors(colors);
        set.setDrawValues(false);

        BarData data = new BarData(set);
        data.setBarWidth(0.5f);
        chart.setData(data);
        chart.getXAxis().setValueFormatter(new IndexAxisValueFormatter(labels));
        chart.setFitBars(true);
        chart.animateY(600);
        chart.invalidate();
    }
}
