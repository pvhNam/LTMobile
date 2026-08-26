package com.example.doanmb.ui.profile.view;

import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.doanmb.R;
import com.example.doanmb.core.util.EdgeToEdgeUtil;
import com.example.doanmb.data.model.UserVoucher;
import com.example.doanmb.data.model.Voucher;
import com.example.doanmb.ui.profile.adapter.MyVoucherAdapter;
import com.example.doanmb.ui.profile.adapter.VoucherAdapter;
import com.example.doanmb.ui.profile.viewmodel.GiftsViewModel;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Màn hình Quà tặng: đổi điểm thưởng lấy voucher giảm giá và xem ví voucher
 * (các voucher đã đổi, dùng được khi thanh toán hóa đơn thuê xe).
 */
public class GiftsActivity extends AppCompatActivity {

    private static final NumberFormat NUM = NumberFormat.getInstance(new Locale("vi", "VN"));

    private TextView tvPoints;
    private TextView tabCatalog, tabMyVouchers;
    private View layoutCatalog, layoutMyVouchers;
    private View emptyCatalog, emptyMyVouchers;
    private RecyclerView rvCatalog, rvMyVouchers;

    private final List<Voucher> catalogItems = new ArrayList<>();
    private final List<UserVoucher> myVoucherItems = new ArrayList<>();
    private VoucherAdapter catalogAdapter;
    private MyVoucherAdapter myVoucherAdapter;

    private GiftsViewModel viewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdgeUtil.enable(this, true);
        setContentView(R.layout.activity_gifts);
        EdgeToEdgeUtil.applyHeaderAndScroll(null, findViewById(R.id.header_bar));
        if (getSupportActionBar() != null) getSupportActionBar().hide();

        viewModel = new ViewModelProvider(this).get(GiftsViewModel.class);
        if (!viewModel.isLoggedIn()) {
            Toast.makeText(this, "Vui lòng đăng nhập để xem quà tặng", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        bindViews();
        setupTabs();
        setupLists();
        observeViewModel();

        viewModel.refresh();
    }

    private void bindViews() {
        ImageView btnBack = findViewById(R.id.btn_back);
        if (btnBack != null) btnBack.setOnClickListener(v -> finish());

        tvPoints         = findViewById(R.id.tv_points_value);
        tabCatalog        = findViewById(R.id.tab_catalog);
        tabMyVouchers     = findViewById(R.id.tab_my_vouchers);
        layoutCatalog     = findViewById(R.id.layout_catalog);
        layoutMyVouchers  = findViewById(R.id.layout_my_vouchers);
        emptyCatalog      = findViewById(R.id.layout_catalog_empty);
        emptyMyVouchers   = findViewById(R.id.layout_my_vouchers_empty);
        rvCatalog         = findViewById(R.id.rv_catalog);
        rvMyVouchers      = findViewById(R.id.rv_my_vouchers);
    }

    private void setupTabs() {
        tabCatalog.setOnClickListener(v -> showTab(true));
        tabMyVouchers.setOnClickListener(v -> showTab(false));
    }

    private void showTab(boolean showCatalog) {
        layoutCatalog.setVisibility(showCatalog ? View.VISIBLE : View.GONE);
        layoutMyVouchers.setVisibility(showCatalog ? View.GONE : View.VISIBLE);

        tabCatalog.setBackgroundResource(showCatalog ? R.drawable.bg_glass_button : 0);
        tabCatalog.setTextColor(getColor(showCatalog ? R.color.white : R.color.lg_text_secondary));

        tabMyVouchers.setBackgroundResource(!showCatalog ? R.drawable.bg_glass_button : 0);
        tabMyVouchers.setTextColor(getColor(!showCatalog ? R.color.white : R.color.lg_text_secondary));
    }

    private void setupLists() {
        catalogAdapter = new VoucherAdapter(catalogItems, viewModel.currentPoints(), this::confirmRedeem);
        rvCatalog.setLayoutManager(new LinearLayoutManager(this));
        rvCatalog.setAdapter(catalogAdapter);

        myVoucherAdapter = new MyVoucherAdapter(myVoucherItems);
        rvMyVouchers.setLayoutManager(new LinearLayoutManager(this));
        rvMyVouchers.setAdapter(myVoucherAdapter);
    }

    private void observeViewModel() {
        viewModel.getPoints().observe(this, p -> {
            tvPoints.setText(NUM.format(p != null ? p : 0L) + " điểm");
            // Điểm thay đổi -> nút "Đổi"/"Thiếu điểm" trên danh mục cần render lại.
            refreshCatalogAdapter();
        });

        viewModel.getCatalog().observe(this, list -> {
            catalogItems.clear();
            catalogItems.addAll(list);
            refreshCatalogAdapter();
            boolean empty = catalogItems.isEmpty();
            emptyCatalog.setVisibility(empty ? View.VISIBLE : View.GONE);
            rvCatalog.setVisibility(empty ? View.GONE : View.VISIBLE);
        });

        viewModel.getMyVouchers().observe(this, list -> {
            myVoucherItems.clear();
            myVoucherItems.addAll(list);
            myVoucherAdapter.notifyDataSetChanged();
            boolean empty = myVoucherItems.isEmpty();
            emptyMyVouchers.setVisibility(empty ? View.VISIBLE : View.GONE);
            rvMyVouchers.setVisibility(empty ? View.GONE : View.VISIBLE);
        });

        viewModel.getMessage().observe(this, msg -> {
            if (msg != null) Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
        });
    }

    /** Danh mục cần biết điểm hiện có để bật/tắt nút "Đổi" -> tạo lại adapter khi điểm đổi. */
    private void refreshCatalogAdapter() {
        catalogAdapter = new VoucherAdapter(catalogItems, viewModel.currentPoints(), this::confirmRedeem);
        rvCatalog.setAdapter(catalogAdapter);
    }

    private void confirmRedeem(Voucher voucher) {
        new AlertDialog.Builder(this)
                .setTitle("Đổi quà tặng")
                .setMessage("Đổi \"" + voucher.getTitle() + "\" với " + voucher.getPointsCost()
                        + " điểm?\n\nVoucher sẽ được lưu vào ví voucher của bạn.")
                .setPositiveButton("Đổi ngay", (dialog, which) -> viewModel.redeem(voucher))
                .setNegativeButton("Hủy", null)
                .show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Quay lại từ thanh toán hóa đơn (đã dùng voucher) -> làm mới điểm + ví voucher.
        if (viewModel != null && viewModel.isLoggedIn()) viewModel.refresh();
    }
}
