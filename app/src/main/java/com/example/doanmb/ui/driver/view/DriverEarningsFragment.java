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
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
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

    private TextView tvName, tvRevenue, tvTrips, tvBalance, tvRewardProgress, tvChartTitle, tvOnline;
    private CircleImageView ivAvatar;
    private BarChart chart;
    private ProgressBar progressReward;

    private FirebaseFirestore db;
    private String uid;
    private int currentPeriod = 0; // 0 hôm nay, 1 tuần, 2 tháng
    private ListenerRegistration earningsListener;
    private ListenerRegistration driverListener;
    private DocumentSnapshot lastDriverDoc;

    // Cập nhật lại số giờ online mỗi phút khi đang mở màn hình
    private final android.os.Handler ticker =
            new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable onlineTick = new Runnable() {
        @Override public void run() {
            updateOnlineText();
            ticker.postDelayed(this, 60_000);
        }
    };

    /** [completedAtMillis, amount] của các chuyến đã hoàn thành. */
    private final List<double[]> completed = new ArrayList<>();

    private static final DecimalFormat MONEY = new DecimalFormat("#,###");
    private static final long DAY_MS    = 24L * 60 * 60 * 1000;
    private static final int  HIGHLIGHT = 0xFF2E6BF0; // cột kỳ hiện tại
    private static final int  NORMAL    = 0xFFBBD2F7; // cột các kỳ trước

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
        tvChartTitle = v.findViewById(R.id.tv_chart_title);
        tvOnline = v.findViewById(R.id.tv_e_online);
        chart = v.findViewById(R.id.chart_income);

        TabLayout tab = v.findViewById(R.id.tab_period);
        tab.addTab(tab.newTab().setText("Hôm nay"));
        tab.addTab(tab.newTab().setText("Tuần này"));
        tab.addTab(tab.newTab().setText("Tháng này"));
        tab.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override public void onTabSelected(TabLayout.Tab t) {
                currentPeriod = t.getPosition();
                refreshPeriod();
                refreshChart();
            }
            @Override public void onTabUnselected(TabLayout.Tab t) {}
            @Override public void onTabReselected(TabLayout.Tab t) {}
        });

        MaterialButton btnWithdraw = v.findViewById(R.id.btn_withdraw);
        MaterialButton btnTxn = v.findViewById(R.id.btn_txn_history);
        btnWithdraw.setOnClickListener(x ->
                Toast.makeText(getContext(), "Rút tiền đang được phát triển", Toast.LENGTH_SHORT).show());
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
        startEarningsListener();
        startDriverListener();
        ticker.removeCallbacks(onlineTick);
        ticker.postDelayed(onlineTick, 60_000);
    }

    @Override
    public void onPause() {
        super.onPause();
        stopEarningsListener();
        stopDriverListener();
        ticker.removeCallbacks(onlineTick);
    }

    /** Lắng nghe real-time doc tài xế để cập nhật số giờ online. */
    private void startDriverListener() {
        if (uid.isEmpty()) return;
        stopDriverListener();
        driverListener = db.collection("drivers").document(uid)
                .addSnapshotListener((doc, e) -> {
                    if (!isAdded() || e != null || doc == null) return;
                    lastDriverDoc = doc;
                    updateOnlineText();
                });
    }

    private void stopDriverListener() {
        if (driverListener != null) {
            driverListener.remove();
            driverListener = null;
        }
    }

    private void updateOnlineText() {
        if (tvOnline == null || lastDriverDoc == null) return;
        tvOnline.setText(formatOnline(onlineSecondsToday(lastDriverDoc)));
    }

    /** Tổng số giây online trong hôm nay (gồm cả phiên đang mở). */
    private static long onlineSecondsToday(DocumentSnapshot doc) {
        String today = todayKey();
        String day   = doc.getString("onlineDay");
        Long   stored= doc.getLong("onlineSecondsToday");
        long   base  = today.equals(day) && stored != null ? stored : 0;
        Timestamp since = doc.getTimestamp("onlineSince");
        boolean online  = Boolean.TRUE.equals(doc.getBoolean("isAvailable"));
        if (online && since != null) {
            long elapsed = (System.currentTimeMillis() - since.toDate().getTime()) / 1000;
            if (elapsed < 0) elapsed = 0;
            base = today.equals(day) ? base + elapsed : elapsed;
        }
        return base;
    }

    private static String todayKey() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
    }

    private static String formatOnline(long seconds) {
        long mins = seconds / 60;
        long h = mins / 60;
        long m = mins % 60;
        if (h > 0) return m > 0 ? h + "h " + m + "m" : h + "h";
        return m + "m";
    }

    /** Lắng nghe real-time các đơn đã hoàn thành của tài xế. */
    private void startEarningsListener() {
        if (uid.isEmpty()) return;
        stopEarningsListener();
        earningsListener = db.collection("orders")
                .whereEqualTo("sellerId", uid)
                .whereEqualTo("status", "completed")
                .addSnapshotListener((snap, e) -> {
                    if (!isAdded() || e != null || snap == null) return;
                    completed.clear();
                    for (QueryDocumentSnapshot d : snap) {
                        Timestamp done = d.getTimestamp("completedAt");
                        if (done == null) continue;
                        // Dùng totalAmount (số) — KHÔNG parse carPrice vì bài tài xế lưu chuỗi
                        // "X đ/ngày · Y đ/km" → parse sẽ dính 2 số thành số khổng lồ.
                        Long ta = d.getLong("totalAmount");
                        long amount = ta != null ? ta : 0L;
                        completed.add(new double[]{done.toDate().getTime(), amount});
                    }
                    refreshPeriod();
                    refreshChart();
                    refreshReward();
                });
    }

    private void stopEarningsListener() {
        if (earningsListener != null) {
            earningsListener.remove();
            earningsListener = null;
        }
    }

    private static long parsePrice(String s) {
        if (s == null) return 0;
        String d = s.replaceAll("[^0-9]", "");
        if (d.isEmpty()) return 0;
        try { return Long.parseLong(d); } catch (NumberFormatException e) { return 0; }
    }

    private static long parseDays(String s) {
        if (s == null || s.isEmpty()) return 1;
        try {
            long d = Long.parseLong(s.replaceAll("[^0-9]", ""));
            return d > 0 ? d : 1;
        } catch (NumberFormatException e) { return 1; }
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
        zeroTime(c);
        if (period == 1) startOfWeek(c);                        // đầu tuần này
        else if (period == 2) c.set(Calendar.DAY_OF_MONTH, 1);  // đầu tháng này
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

    /** Vẽ biểu đồ theo kỳ đang chọn: ngày / tuần / tháng. */
    private void refreshChart() {
        switch (currentPeriod) {
            case 1:  buildWeeklyChart();  break;
            case 2:  buildMonthlyChart(); break;
            default: buildDailyChart();   break;
        }
    }

    /** 7 ngày gần nhất, mỗi cột = 1 ngày, hôm nay nổi bật. */
    private void buildDailyChart() {
        if (tvChartTitle != null) tvChartTitle.setText("Thu nhập theo ngày");
        SimpleDateFormat fmt = new SimpleDateFormat("dd/MM", Locale.getDefault());
        List<BarEntry> entries = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        List<Integer> colors = new ArrayList<>();

        for (int i = 6; i >= 0; i--) {
            Calendar c = Calendar.getInstance();
            c.add(Calendar.DAY_OF_MONTH, -i);
            zeroTime(c);
            long start = c.getTimeInMillis();
            entries.add(new BarEntry(6 - i, (float) sumBetween(start, start + DAY_MS)));
            labels.add(fmt.format(new Date(start)));
            colors.add(i == 0 ? HIGHLIGHT : NORMAL);
        }
        applyChart(entries, labels, colors);
    }

    /** 6 tuần gần nhất, mỗi cột = 1 tuần, tuần này nổi bật. */
    private void buildWeeklyChart() {
        if (tvChartTitle != null) tvChartTitle.setText("Thu nhập theo tuần");
        SimpleDateFormat fmt = new SimpleDateFormat("dd/MM", Locale.getDefault());
        List<BarEntry> entries = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        List<Integer> colors = new ArrayList<>();

        for (int w = 5; w >= 0; w--) {
            Calendar c = Calendar.getInstance();
            startOfWeek(c);
            c.add(Calendar.DAY_OF_MONTH, -7 * w);
            long start = c.getTimeInMillis();
            entries.add(new BarEntry(5 - w, (float) sumBetween(start, start + 7L * DAY_MS)));
            labels.add(fmt.format(new Date(start)));
            colors.add(w == 0 ? HIGHLIGHT : NORMAL);
        }
        applyChart(entries, labels, colors);
    }

    /** 6 tháng gần nhất, mỗi cột = 1 tháng, tháng này nổi bật. */
    private void buildMonthlyChart() {
        if (tvChartTitle != null) tvChartTitle.setText("Thu nhập theo tháng");
        SimpleDateFormat fmt = new SimpleDateFormat("MM/yy", Locale.getDefault());
        List<BarEntry> entries = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        List<Integer> colors = new ArrayList<>();

        for (int m = 5; m >= 0; m--) {
            Calendar c = Calendar.getInstance();
            c.set(Calendar.DAY_OF_MONTH, 1);
            zeroTime(c);
            c.add(Calendar.MONTH, -m);
            long start = c.getTimeInMillis();
            Calendar e = (Calendar) c.clone();
            e.add(Calendar.MONTH, 1);
            entries.add(new BarEntry(5 - m, (float) sumBetween(start, e.getTimeInMillis())));
            labels.add(fmt.format(new Date(start)));
            colors.add(m == 0 ? HIGHLIGHT : NORMAL);
        }
        applyChart(entries, labels, colors);
    }

    private double sumBetween(long start, long end) {
        double sum = 0;
        for (double[] t : completed) if (t[0] >= start && t[0] < end) sum += t[1];
        return sum;
    }

    private void applyChart(List<BarEntry> entries, List<String> labels, List<Integer> colors) {
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

    private static void zeroTime(Calendar c) {
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
    }

    private static void startOfWeek(Calendar c) {
        zeroTime(c);
        c.setFirstDayOfWeek(Calendar.MONDAY);
        c.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY);
    }
}
