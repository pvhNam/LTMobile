package com.example.doanmb.ui.admin.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.doanmb.ui.admin.util.RevenueCalculator;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.Arrays;
import java.util.Calendar;

/**
 * ViewModel trang Tổng quan admin: tổng doanh thu nền tảng (hoa hồng + phí đăng
 * bài), biểu đồ doanh thu 6 tháng gần nhất và số hồ sơ tài xế đang chờ duyệt.
 * Toàn bộ tính toán nằm ở đây; View chỉ render số liệu lên card/biểu đồ.
 */
public class AdminOverviewViewModel extends ViewModel {

    /** Dữ liệu biểu đồ 6 tháng: doanh thu (đơn vị nghìn VNĐ) + nhãn tháng. */
    public static class ChartData {
        public final float[] valuesK;   // đã chia 1.000 để trục Y gọn
        public final String[] labels;
        public ChartData(float[] valuesK, String[] labels) { this.valuesK = valuesK; this.labels = labels; }
    }

    private final MutableLiveData<Long> totalRevenue = new MutableLiveData<>();
    private final MutableLiveData<Integer> driverPendingCount = new MutableLiveData<>();
    private final MutableLiveData<ChartData> chartData = new MutableLiveData<>();

    public LiveData<Long> getTotalRevenue() { return totalRevenue; }
    public LiveData<Integer> getDriverPendingCount() { return driverPendingCount; }
    public LiveData<ChartData> getChartData() { return chartData; }

    private static FirebaseFirestore db() { return FirebaseFirestore.getInstance(); }

    public void refresh() {
        loadRevenue();
        loadDriverPendingCount();
        loadChartData();
    }

    /** Doanh thu = hoa hồng đơn đã chốt (confirmed + completed) + phí đăng bài. */
    private void loadRevenue() {
        db().collection("orders").whereIn("status", Arrays.asList("confirmed", "completed")).get()
                .addOnSuccessListener(orderSnap -> {
                    long commission = 0;
                    for (QueryDocumentSnapshot doc : orderSnap) commission += RevenueCalculator.commission(doc);
                    final long commissionFinal = commission;

                    db().collection("cars").get().addOnSuccessListener(carSnap -> {
                        int carCount = 0;
                        for (QueryDocumentSnapshot c : carSnap) {
                            if (!"rejected".equals(c.getString("status"))) carCount++;
                        }
                        long postingFee = carCount * RevenueCalculator.POSTING_FEE;
                        totalRevenue.setValue(commissionFinal + postingFee);
                    });
                });
    }

    private void loadDriverPendingCount() {
        db().collection("users").whereEqualTo("driverStatus", "pending").get()
                .addOnSuccessListener(snap -> driverPendingCount.setValue(snap.size()));
    }

    private void loadChartData() {
        db().collection("orders").whereIn("status", Arrays.asList("confirmed", "completed")).get()
                .addOnSuccessListener(snapshots -> {
                    Calendar now = Calendar.getInstance();
                    long[] monthRevenue = new long[6];
                    String[] labels = new String[6];
                    String[] monthNames = {"T1","T2","T3","T4","T5","T6","T7","T8","T9","T10","T11","T12"};

                    for (int i = 5; i >= 0; i--) {
                        Calendar c = (Calendar) now.clone();
                        c.add(Calendar.MONTH, -i);
                        labels[5 - i] = monthNames[c.get(Calendar.MONTH)];
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
                                monthRevenue[5 - i] += RevenueCalculator.commission(doc);
                                break;
                            }
                        }
                    }

                    float[] valuesK = new float[6];
                    for (int i = 0; i < 6; i++) valuesK[i] = monthRevenue[i] / 1_000f;
                    chartData.setValue(new ChartData(valuesK, labels));
                });
    }
}
