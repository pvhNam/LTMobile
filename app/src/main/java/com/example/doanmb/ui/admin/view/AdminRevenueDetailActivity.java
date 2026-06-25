package com.example.doanmb.ui.admin.view;

import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.doanmb.R;
import com.example.doanmb.ui.admin.adapter.RevenueItemAdapter;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class AdminRevenueDetailActivity extends AppCompatActivity {

    private static final double SALE_COMMISSION   = 0.03;
    private static final double RENTAL_COMMISSION = 0.15;
    private static final long   POSTING_FEE       = 200_000L;

    private TextView tvGrandTotal, tvCommissionTotal, tvPostingTotal, tvEmpty;
    private ProgressBar progressBar;
    private RecyclerView rvRevenue;
    private Button btnTabCommission, btnTabPosting;
    private RevenueItemAdapter adapter;
    private FirebaseFirestore db;

    private final List<QueryDocumentSnapshot> orderDocs = new ArrayList<>();
    private final List<QueryDocumentSnapshot> carDocs   = new ArrayList<>();
    private int loadedCount = 0;
    private int currentTab  = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_revenue_detail);
        if (getSupportActionBar() != null) getSupportActionBar().hide();

        db = FirebaseFirestore.getInstance();

        tvGrandTotal      = findViewById(R.id.tv_revenue_grand_total);
        tvCommissionTotal = findViewById(R.id.tv_commission_total);
        tvPostingTotal    = findViewById(R.id.tv_posting_total);
        tvEmpty           = findViewById(R.id.tv_revenue_empty);
        progressBar       = findViewById(R.id.progress_revenue);
        rvRevenue         = findViewById(R.id.rv_revenue_detail);
        btnTabCommission  = findViewById(R.id.btn_rev_tab_commission);
        btnTabPosting     = findViewById(R.id.btn_rev_tab_posting);

        adapter = new RevenueItemAdapter();
        rvRevenue.setLayoutManager(new LinearLayoutManager(this));
        rvRevenue.setAdapter(adapter);

        findViewById(R.id.btn_revenue_back).setOnClickListener(v -> finish());
        btnTabCommission.setOnClickListener(v -> switchTab(0));
        btnTabPosting.setOnClickListener(v -> switchTab(1));

        applyTabStyle(0);
        loadData();
    }

    // ── Tải dữ liệu song song ──────────────────────────────────────────────

    private void loadData() {
        progressBar.setVisibility(View.VISIBLE);
        rvRevenue.setVisibility(View.GONE);
        tvEmpty.setVisibility(View.GONE);
        loadedCount = 0;

        db.collection("orders").whereEqualTo("status", "confirmed").get()
                .addOnSuccessListener(snap -> {
                    orderDocs.clear();
                    for (QueryDocumentSnapshot doc : snap) orderDocs.add(doc);
                    onDatasetReady();
                });

        db.collection("cars").get()
                .addOnSuccessListener(snap -> {
                    carDocs.clear();
                    for (QueryDocumentSnapshot doc : snap) carDocs.add(doc);
                    onDatasetReady();
                });
    }

    private void onDatasetReady() {
        loadedCount++;
        if (loadedCount < 2) return;

        // Tính tổng
        long commissionTotal = 0;
        for (QueryDocumentSnapshot doc : orderDocs) commissionTotal += calcCommission(doc);
        long postingTotal = carDocs.size() * POSTING_FEE;
        long grandTotal   = commissionTotal + postingTotal;

        tvGrandTotal.setText(fmt(grandTotal));
        tvCommissionTotal.setText(fmt(commissionTotal));
        tvPostingTotal.setText(fmt(postingTotal));

        progressBar.setVisibility(View.GONE);
        renderTab();
    }

    // ── Chuyển tab ─────────────────────────────────────────────────────────

    private void switchTab(int tab) {
        currentTab = tab;
        applyTabStyle(tab);
        renderTab();
    }

    private void renderTab() {
        List<Object> items = currentTab == 0 ? buildCommissionItems() : buildPostingItems();
        if (items.isEmpty()) {
            tvEmpty.setVisibility(View.VISIBLE);
            rvRevenue.setVisibility(View.GONE);
        } else {
            tvEmpty.setVisibility(View.GONE);
            rvRevenue.setVisibility(View.VISIBLE);
            adapter.setItems(items);
        }
    }

    // ── Build danh sách hoa hồng (nhóm theo tháng) ─────────────────────────

    private List<Object> buildCommissionItems() {
        // Sắp xếp giảm dần theo ngày (client-side)
        List<QueryDocumentSnapshot> sorted = new ArrayList<>(orderDocs);
        sorted.sort((a, b) -> {
            Timestamp ta = a.getTimestamp("createdAt");
            Timestamp tb = b.getTimestamp("createdAt");
            if (ta == null && tb == null) return 0;
            if (ta == null) return 1;
            if (tb == null) return -1;
            return Long.compare(tb.getSeconds(), ta.getSeconds());
        });

        List<Object> items = new ArrayList<>();
        SimpleDateFormat monthFmt = new SimpleDateFormat("MM/yyyy", Locale.getDefault());
        SimpleDateFormat dateFmt  = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
        String lastMonth = "";

        for (QueryDocumentSnapshot doc : sorted) {
            Timestamp ts    = doc.getTimestamp("createdAt");
            String monthStr = ts != null ? "Tháng " + monthFmt.format(ts.toDate()) : "Không rõ ngày";

            if (!monthStr.equals(lastMonth)) {
                items.add(monthStr);
                lastMonth = monthStr;
            }

            String type      = doc.getString("type");
            String carName   = doc.getString("carName");
            String buyer     = doc.getString("buyerName");
            if (buyer == null) buyer = doc.getString("renterName");
            if (buyer == null) buyer = "--";
            String dateStr   = ts != null ? dateFmt.format(ts.toDate()) : "--";
            boolean isRental = "Thuê xe".equals(type);

            items.add(new RevenueItemAdapter.Entry(
                    carName != null ? carName : "Xe không tên",
                    (isRental ? "Người thuê: " : "Người mua: ") + buyer,
                    fmt(calcCommission(doc)),
                    dateStr,
                    isRental ? "Thuê" : "Mua",
                    isRental ? 1 : 0
            ));
        }
        return items;
    }

    // ── Build danh sách phí đăng bài ───────────────────────────────────────

    private List<Object> buildPostingItems() {
        List<Object> items = new ArrayList<>();
        // Header duy nhất
        if (!carDocs.isEmpty()) items.add("Tất cả bài đăng  (" + carDocs.size() + " bài)");

        for (QueryDocumentSnapshot doc : carDocs) {
            String carName    = doc.getString("name");
            String sellerName = doc.getString("sellerName");
            String status     = doc.getString("status");
            if (sellerName == null) sellerName = "--";

            items.add(new RevenueItemAdapter.Entry(
                    carName != null ? carName : "Xe không tên",
                    "Người đăng: " + sellerName,
                    "200.000 VNĐ",
                    statusLabel(status),
                    "Đăng",
                    2
            ));
        }
        return items;
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private long calcCommission(QueryDocumentSnapshot doc) {
        String type  = doc.getString("type");
        long   price = parsePrice(doc.getString("carPrice"));
        if ("Thuê xe".equals(type)) {
            long days = 1;
            String daysStr = doc.getString("days");
            if (daysStr != null) {
                try {
                    long d = Long.parseLong(daysStr.replaceAll("[^0-9]", ""));
                    if (d > 0) days = d;
                } catch (NumberFormatException ignored) {}
            }
            return (long)(price * days * RENTAL_COMMISSION);
        }
        return (long)(price * SALE_COMMISSION);
    }

    private String statusLabel(String status) {
        if ("active".equals(status))   return "Đang bán";
        if ("sold".equals(status))     return "Đã bán";
        if ("rejected".equals(status)) return "Từ chối";
        if ("holding".equals(status))  return "Đặt cọc";
        return "Chờ duyệt";
    }

    private long parsePrice(String s) {
        if (s == null || s.isEmpty()) return 0;
        String d = s.replaceAll("[^0-9]", "");
        if (d.isEmpty()) return 0;
        try { return Long.parseLong(d); } catch (NumberFormatException e) { return 0; }
    }

    private String fmt(long amount) {
        if (amount == 0) return "0 VNĐ";
        if (amount >= 1_000_000_000L) return String.format("%.1f tỷ", amount / 1_000_000_000.0);
        if (amount >= 1_000_000L)     return String.format("%.0f triệu", amount / 1_000_000.0);
        return (amount / 1_000) + "K VNĐ";
    }

    private void applyTabStyle(int active) {
        int on  = 0xFFC62828, onText  = 0xFFFFFFFF;
        int off = 0xFFFFCDD2, offText = 0xFFC62828;
        if (active == 0) {
            btnTabCommission.setBackgroundTintList(ColorStateList.valueOf(on));
            btnTabCommission.setTextColor(onText);
            btnTabPosting.setBackgroundTintList(ColorStateList.valueOf(off));
            btnTabPosting.setTextColor(offText);
        } else {
            btnTabPosting.setBackgroundTintList(ColorStateList.valueOf(on));
            btnTabPosting.setTextColor(onText);
            btnTabCommission.setBackgroundTintList(ColorStateList.valueOf(off));
            btnTabCommission.setTextColor(offText);
        }
    }
}
