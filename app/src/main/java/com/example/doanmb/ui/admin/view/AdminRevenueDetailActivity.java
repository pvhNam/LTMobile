package com.example.doanmb.ui.admin.view;

import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.doanmb.R;
import com.example.doanmb.ui.admin.adapter.RevenueItemAdapter;
import com.example.doanmb.ui.admin.util.AdminFormat;
import com.example.doanmb.ui.admin.util.AdminTab;
import com.example.doanmb.ui.admin.util.RevenueCalculator;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class AdminRevenueDetailActivity extends AppCompatActivity {

    private TextView tvGrandTotal, tvCommissionTotal, tvPostingTotal, tvEmpty, tvMonth, tvMonthReset;
    private ProgressBar progressBar;
    private RecyclerView rvRevenue;
    private Button btnTabCommission, btnTabPosting;
    private View filterMonthCard;
    private RevenueItemAdapter adapter;
    private FirebaseFirestore db;

    private final List<QueryDocumentSnapshot> orderDocs = new ArrayList<>();
    private final List<QueryDocumentSnapshot> carDocs   = new ArrayList<>();
    private int loadedCount = 0;
    private int currentTab  = 0;

    // Bộ lọc theo khoảng ngày: filterAll = true nghĩa là không lọc (tất cả thời gian)
    private boolean filterAll   = true;
    private long    filterStart = 0; // mốc đầu khoảng (local, 00:00:00.000)
    private long    filterEnd   = 0; // mốc cuối khoảng (local, 23:59:59.999)

    // Trạng thái lịch tùy chỉnh khi đang mở hộp thoại chọn ngày
    private Calendar pickerMonth;        // tháng đang hiển thị (ngày 1)
    private long     pickerStart = -1;   // ngày đầu đã chọn (local 00:00), -1 = chưa chọn
    private long     pickerEnd   = -1;   // ngày cuối đã chọn (local 00:00), -1 = chưa chọn

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
        tvMonth           = findViewById(R.id.tv_revenue_month);
        tvMonthReset      = findViewById(R.id.tv_revenue_month_reset);
        progressBar       = findViewById(R.id.progress_revenue);
        rvRevenue         = findViewById(R.id.rv_revenue_detail);
        btnTabCommission  = findViewById(R.id.btn_rev_tab_commission);
        btnTabPosting     = findViewById(R.id.btn_rev_tab_posting);
        filterMonthCard   = findViewById(R.id.filter_month_card);

        adapter = new RevenueItemAdapter();
        rvRevenue.setLayoutManager(new LinearLayoutManager(this));
        rvRevenue.setAdapter(adapter);

        findViewById(R.id.btn_revenue_back).setOnClickListener(v -> finish());
        btnTabCommission.setOnClickListener(v -> switchTab(0));
        btnTabPosting.setOnClickListener(v -> switchTab(1));
        filterMonthCard.setOnClickListener(v -> showRangePicker());
        tvMonthReset.setOnClickListener(v -> resetMonthFilter());

        applyTabStyle(0);
        loadData();
    }

    // ── Bộ lọc theo khoảng ngày ────────────────────────────────────────────

    /** Mở 1 lịch tùy chỉnh: hiển thị đúng 1 tháng, có mũi tên chuyển tháng, chọn ngày A -> ngày B. */
    private void showRangePicker() {
        pickerMonth = Calendar.getInstance();
        if (!filterAll) {
            pickerStart = startOfDay(filterStart);
            pickerEnd   = startOfDay(filterEnd);
            pickerMonth.setTimeInMillis(filterStart);
        } else {
            pickerStart = -1;
            pickerEnd   = -1;
        }
        pickerMonth.set(Calendar.DAY_OF_MONTH, 1);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(16);
        root.setPadding(pad, pad, pad, dp(4));

        // Header: ‹  Tháng MM/yyyy  ›
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        TextView btnPrev = navArrow("‹");
        TextView tvTitle = new TextView(this);
        tvTitle.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        tvTitle.setGravity(Gravity.CENTER);
        tvTitle.setTextColor(0xFF212121);
        tvTitle.setTextSize(16);
        tvTitle.setTypeface(tvTitle.getTypeface(), android.graphics.Typeface.BOLD);
        TextView btnNext = navArrow("›");

        header.addView(btnPrev);
        header.addView(tvTitle);
        header.addView(btnNext);
        root.addView(header);

        // Hàng thứ trong tuần (T2..CN)
        LinearLayout week = new LinearLayout(this);
        week.setOrientation(LinearLayout.HORIZONTAL);
        week.setPadding(0, dp(8), 0, dp(4));
        for (String w : new String[]{"T2", "T3", "T4", "T5", "T6", "T7", "CN"}) {
            TextView t = new TextView(this);
            t.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            t.setGravity(Gravity.CENTER);
            t.setText(w);
            t.setTextColor(0xFF9E9E9E);
            t.setTextSize(12);
            week.addView(t);
        }
        root.addView(week);

        // Lưới ngày
        LinearLayout grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        root.addView(grid);

        btnPrev.setOnClickListener(v -> { pickerMonth.add(Calendar.MONTH, -1); renderCalendar(tvTitle, grid); });
        btnNext.setOnClickListener(v -> { pickerMonth.add(Calendar.MONTH, 1);  renderCalendar(tvTitle, grid); });

        renderCalendar(tvTitle, grid);

        new AlertDialog.Builder(this)
                .setView(root)
                .setNegativeButton("Hủy", null)
                .setPositiveButton("OK", (d, w) -> {
                    if (pickerStart == -1) return;
                    long endSel = pickerEnd == -1 ? pickerStart : pickerEnd;
                    filterAll   = false;
                    filterStart = pickerStart;          // đã là đầu ngày
                    filterEnd   = endOfDay(endSel);
                    updateMonthLabel();
                    applyFilter();
                })
                .show();
    }

    private TextView navArrow(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(0xFFC62828);
        t.setTextSize(24);
        t.setPadding(dp(16), dp(4), dp(16), dp(4));
        t.setClickable(true);
        t.setFocusable(true);
        return t;
    }

    /** Vẽ lại lưới ngày của tháng đang hiển thị, tô màu ngày đã chọn / nằm trong khoảng. */
    private void renderCalendar(TextView title, LinearLayout grid) {
        title.setText(new SimpleDateFormat("'Tháng' MM/yyyy", Locale.getDefault()).format(pickerMonth.getTime()));
        grid.removeAllViews();

        Calendar c = (Calendar) pickerMonth.clone();
        c.set(Calendar.DAY_OF_MONTH, 1);
        int offset      = (c.get(Calendar.DAY_OF_WEEK) + 5) % 7; // T2 = 0 ... CN = 6
        int daysInMonth = c.getActualMaximum(Calendar.DAY_OF_MONTH);
        long today      = startOfDay(System.currentTimeMillis());

        int day = 1;
        for (int row = 0; row < 6 && day <= daysInMonth; row++) {
            LinearLayout r = new LinearLayout(this);
            r.setOrientation(LinearLayout.HORIZONTAL);
            for (int col = 0; col < 7; col++) {
                TextView cell = new TextView(this);
                cell.setLayoutParams(new LinearLayout.LayoutParams(0, dp(40), 1f));
                cell.setGravity(Gravity.CENTER);
                cell.setTextSize(14);

                int index = row * 7 + col;
                if (index < offset || day > daysInMonth) {
                    cell.setText("");
                } else {
                    Calendar cellCal = (Calendar) pickerMonth.clone();
                    cellCal.set(Calendar.DAY_OF_MONTH, day);
                    long cellMillis = startOfDay(cellCal.getTimeInMillis());
                    cell.setText(String.valueOf(day));

                    boolean future  = cellMillis > today;
                    boolean isEdge  = cellMillis == pickerStart || cellMillis == pickerEnd;
                    boolean inRange = pickerStart != -1 && pickerEnd != -1
                            && cellMillis > pickerStart && cellMillis < pickerEnd;

                    if (future) {
                        cell.setTextColor(0xFFCCCCCC);
                    } else if (isEdge) {
                        cell.setBackgroundColor(0xFFC62828);
                        cell.setTextColor(0xFFFFFFFF);
                    } else if (inRange) {
                        cell.setBackgroundColor(0xFFFFCDD2);
                        cell.setTextColor(0xFFC62828);
                    } else {
                        cell.setTextColor(0xFF212121);
                    }

                    if (!future) {
                        cell.setOnClickListener(v -> {
                            onPickDay(cellMillis);
                            renderCalendar(title, grid);
                        });
                    }
                    day++;
                }
                r.addView(cell);
            }
            grid.addView(r);
        }
    }

    /** Bấm 1 ngày: lần 1 đặt ngày đầu; lần 2 đặt ngày cuối (tự đổi chỗ nếu bấm ngày trước ngày đầu). */
    private void onPickDay(long millis) {
        if (pickerStart == -1 || pickerEnd != -1) {
            pickerStart = millis;
            pickerEnd   = -1;
        } else if (millis < pickerStart) {
            pickerEnd   = pickerStart;
            pickerStart = millis;
        } else {
            pickerEnd   = millis;
        }
    }

    private int dp(int v) {
        return Math.round(getResources().getDisplayMetrics().density * v);
    }

    private void resetMonthFilter() {
        filterAll = true;
        updateMonthLabel();
        applyFilter();
    }

    private void updateMonthLabel() {
        if (filterAll) {
            tvMonth.setText("Tất cả thời gian");
            tvMonthReset.setVisibility(View.GONE);
        } else {
            SimpleDateFormat f = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
            tvMonth.setText(f.format(filterStart) + " – " + f.format(filterEnd));
            tvMonthReset.setVisibility(View.VISIBLE);
        }
    }

    /** Đầu ngày (00:00:00.000) theo giờ máy của mốc thời gian đã cho. */
    private long startOfDay(long millis) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(millis);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    /** Cuối ngày (23:59:59.999) theo giờ máy của mốc thời gian đã cho. */
    private long endOfDay(long millis) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(millis);
        c.set(Calendar.HOUR_OF_DAY, 23);
        c.set(Calendar.MINUTE, 59);
        c.set(Calendar.SECOND, 59);
        c.set(Calendar.MILLISECOND, 999);
        return c.getTimeInMillis();
    }

    /** Doc có createdAt nằm trong khoảng đang lọc? (filterAll = nhận hết, bao gồm cả 2 đầu mút) */
    private boolean inSelectedRange(QueryDocumentSnapshot doc) {
        if (filterAll) return true;
        Timestamp ts = doc.getTimestamp("createdAt");
        if (ts == null) return false;
        long t = ts.toDate().getTime();
        return t >= filterStart && t <= filterEnd;
    }

    // ── Tải dữ liệu song song ──────────────────────────────────────────────

    private void loadData() {
        progressBar.setVisibility(View.VISIBLE);
        rvRevenue.setVisibility(View.GONE);
        tvEmpty.setVisibility(View.GONE);
        loadedCount = 0;

        // Doanh thu = đơn đã chốt: gồm cả "confirmed" lẫn "completed"
        db.collection("orders").whereIn("status", Arrays.asList("confirmed", "completed")).get()
                .addOnSuccessListener(snap -> {
                    orderDocs.clear();
                    for (QueryDocumentSnapshot doc : snap) orderDocs.add(doc);
                    onDatasetReady();
                })
                .addOnFailureListener(this::onLoadFailed);

        db.collection("cars").get()
                .addOnSuccessListener(snap -> {
                    carDocs.clear();
                    for (QueryDocumentSnapshot doc : snap) carDocs.add(doc);
                    onDatasetReady();
                })
                .addOnFailureListener(this::onLoadFailed);
    }

    private void onLoadFailed(Exception e) {
        progressBar.setVisibility(View.GONE);
        tvEmpty.setVisibility(View.VISIBLE);
        tvEmpty.setText("Lỗi tải dữ liệu doanh thu");
        Toast.makeText(this, "Lỗi: " + e.getMessage(), Toast.LENGTH_SHORT).show();
    }

    private void onDatasetReady() {
        loadedCount++;
        if (loadedCount < 2) return;

        progressBar.setVisibility(View.GONE);
        applyFilter();
    }

    /** Tính lại tổng + danh sách theo khoảng ngày đang lọc. */
    private void applyFilter() {
        long commissionTotal = 0;
        for (QueryDocumentSnapshot doc : orderDocs) {
            if (inSelectedRange(doc)) commissionTotal += RevenueCalculator.commission(doc);
        }
        long postingCount = 0;
        for (QueryDocumentSnapshot doc : carDocs) {
            // Xe bị từ chối không thu phí đăng bài
            if (inSelectedRange(doc) && !"rejected".equals(doc.getString("status"))) postingCount++;
        }
        long postingTotal = postingCount * RevenueCalculator.POSTING_FEE;
        long grandTotal   = commissionTotal + postingTotal;

        tvGrandTotal.setText(AdminFormat.money(grandTotal));
        tvCommissionTotal.setText(AdminFormat.money(commissionTotal));
        tvPostingTotal.setText(AdminFormat.money(postingTotal));

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
        // Lọc theo khoảng + sắp xếp giảm dần theo ngày (client-side)
        List<QueryDocumentSnapshot> sorted = new ArrayList<>();
        for (QueryDocumentSnapshot doc : orderDocs) {
            if (inSelectedRange(doc)) sorted.add(doc);
        }
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
                    AdminFormat.money(RevenueCalculator.commission(doc)),
                    dateStr,
                    isRental ? "Thuê" : "Mua",
                    isRental ? 1 : 0
            ));
        }
        return items;
    }

    // ── Build danh sách phí đăng bài ───────────────────────────────────────

    private List<Object> buildPostingItems() {
        // Lọc theo khoảng
        List<QueryDocumentSnapshot> filtered = new ArrayList<>();
        for (QueryDocumentSnapshot doc : carDocs) {
            if (inSelectedRange(doc) && !"rejected".equals(doc.getString("status"))) filtered.add(doc);
        }

        List<Object> items = new ArrayList<>();
        // Header duy nhất
        if (!filtered.isEmpty()) {
            items.add((filterAll ? "Tất cả bài đăng" : "Bài đăng trong khoảng")
                    + "  (" + filtered.size() + " bài)");
        }

        for (QueryDocumentSnapshot doc : filtered) {
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

    private String statusLabel(String status) {
        if ("active".equals(status))   return "Đang bán";
        if ("sold".equals(status))     return "Đã bán";
        if ("rejected".equals(status)) return "Từ chối";
        if ("holding".equals(status))  return "Đặt cọc";
        return "Chờ duyệt";
    }

    private void applyTabStyle(int active) {
        AdminTab.select(active == 0 ? btnTabCommission : btnTabPosting,
                btnTabCommission, btnTabPosting);
    }
}
