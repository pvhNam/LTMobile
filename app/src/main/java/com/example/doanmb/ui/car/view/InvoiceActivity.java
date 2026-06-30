package com.example.doanmb.ui.car.view;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.example.doanmb.R;
import com.example.doanmb.core.util.EdgeToEdgeUtil;
import com.example.doanmb.data.model.UserVoucher;
import com.example.doanmb.ui.car.viewmodel.InvoiceViewModel;
import com.example.doanmb.ui.profile.view.VnpayPaymentActivity;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Màn hóa đơn thuê xe: hiện chi tiết (tiền thuê + phạt trễ + lý do), cho khách chọn voucher giảm giá và thanh toán. */
public class InvoiceActivity extends AppCompatActivity {

    private TextView tvCar, tvOwner, tvPayer, tvRental, tvPenalty, tvTotal, tvReason, tvLateLabel;
    private TextView tvDiscount, tvVoucherLabel, tvPrepaid, tvRemaining;
    private TextView tvCode, tvDate, tvStatus, tvPhone, tvPriceDay, tvPeriod, tvMethod;
    private TextView tvExtend, tvExtendLabel;
    private LinearLayout rowChooseVoucher, rowDiscount, rowPrepaid, rowExtend;
    private MaterialButton btnPay;

    private InvoiceViewModel viewModel;
    private List<UserVoucher> currentVouchers = new ArrayList<>();

    // Nhận kết quả thanh toán VNPay
    private final ActivityResultLauncher<Intent> vnpayLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                Intent data = result.getData();
                if (result.getResultCode() == RESULT_OK && data != null
                        && data.getBooleanExtra(VnpayPaymentActivity.EXTRA_SUCCESS, false)) {
                    viewModel.onVnpayPaid();
                } else {
                    viewModel.onVnpayCancelled();
                }
            });

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdgeUtil.enable(this, true);
        setContentView(R.layout.activity_invoice);
        EdgeToEdgeUtil.padContentForSystemBars(this, 0xFFFFFFFF);

        ImageView btnBack = findViewById(R.id.btn_back);
        if (btnBack != null) btnBack.setOnClickListener(v -> finish());

        tvCar      = findViewById(R.id.tv_invoice_car);
        tvOwner    = findViewById(R.id.tv_invoice_owner);
        tvPayer    = findViewById(R.id.tv_invoice_payer);
        tvRental   = findViewById(R.id.tv_invoice_rental);
        tvPenalty  = findViewById(R.id.tv_invoice_penalty);
        tvTotal    = findViewById(R.id.tv_invoice_total);
        tvReason   = findViewById(R.id.tv_invoice_reason);
        tvLateLabel= findViewById(R.id.tv_invoice_late_label);
        btnPay     = findViewById(R.id.btn_pay_invoice);
        tvDiscount       = findViewById(R.id.tv_invoice_discount);
        tvVoucherLabel   = findViewById(R.id.tv_invoice_voucher_label);
        tvPrepaid        = findViewById(R.id.tv_invoice_prepaid);
        tvRemaining      = findViewById(R.id.tv_invoice_remaining);
        tvCode           = findViewById(R.id.tv_invoice_code);
        tvDate           = findViewById(R.id.tv_invoice_date);
        tvStatus         = findViewById(R.id.tv_invoice_status);
        tvPhone          = findViewById(R.id.tv_invoice_phone);
        tvPriceDay       = findViewById(R.id.tv_invoice_price_day);
        tvPeriod         = findViewById(R.id.tv_invoice_period);
        tvMethod         = findViewById(R.id.tv_invoice_method);
        tvExtend         = findViewById(R.id.tv_invoice_extend);
        tvExtendLabel    = findViewById(R.id.tv_invoice_extend_label);
        rowChooseVoucher = findViewById(R.id.row_choose_voucher);
        rowDiscount      = findViewById(R.id.row_invoice_discount);
        rowPrepaid       = findViewById(R.id.row_invoice_prepaid);
        rowExtend        = findViewById(R.id.row_invoice_extend);

        if (rowChooseVoucher != null) {
            rowChooseVoucher.setOnClickListener(v -> showVoucherPicker());
        }

        viewModel = new ViewModelProvider(this).get(InvoiceViewModel.class);
        observeViewModel();
        viewModel.start(getIntent().getStringExtra("ORDER_ID"));
    }

    private void observeViewModel() {
        viewModel.getInvoice().observe(this, this::render);
        viewModel.getMessage().observe(this, msg -> {
            if (msg != null) Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
        });
        viewModel.getPayEnabled().observe(this, enabled -> {
            if (enabled != null) btnPay.setEnabled(enabled);
        });
        viewModel.getFinishEvent().observe(this, finish -> {
            if (Boolean.TRUE.equals(finish)) finish();
        });
        viewModel.getLaunchVnpay().observe(this, amount -> {
            if (amount == null || amount <= 0) return;
            Intent i = new Intent(this, VnpayPaymentActivity.class);
            i.putExtra(VnpayPaymentActivity.EXTRA_AMOUNT, amount);
            vnpayLauncher.launch(i);
            viewModel.clearLaunchVnpay();
        });
        viewModel.getAvailableVouchers().observe(this, list -> {
            currentVouchers = list != null ? list : new ArrayList<>();
            if (rowChooseVoucher != null) {
                rowChooseVoucher.setVisibility(currentVouchers.isEmpty() ? View.GONE : View.VISIBLE);
            }
        });
        viewModel.getSelectedVoucher().observe(this, voucher -> {
            if (tvVoucherLabel != null) {
                tvVoucherLabel.setText(voucher != null ? voucher.shortLabel() + " ✓" : "Chọn voucher ›");
            }
        });
    }

    private void render(InvoiceViewModel.Invoice inv) {
        if (inv == null) return;
        tvCar.setText(inv.carName != null ? inv.carName : "Xe");
        tvOwner.setText(inv.ownerName != null && !inv.ownerName.isEmpty() ? inv.ownerName : "—");
        if (tvPayer != null) tvPayer.setText(inv.payerName != null && !inv.payerName.isEmpty() ? inv.payerName : "—");
        if (tvPhone != null) tvPhone.setText(inv.payerPhone != null && !inv.payerPhone.isEmpty() ? inv.payerPhone : "—");
        if (tvCode != null) tvCode.setText("Mã hóa đơn: #" + (inv.code != null ? inv.code : "------"));
        if (tvDate != null) tvDate.setText("Ngày lập: " + (inv.issuedAt != null ? inv.issuedAt : "—"));
        if (tvPriceDay != null) tvPriceDay.setText(inv.pricePerDayText != null ? inv.pricePerDayText : "—");
        if (tvPeriod != null) tvPeriod.setText(inv.periodText != null ? inv.periodText : "—");
        if (tvMethod != null) tvMethod.setText(inv.methodLabel != null ? inv.methodLabel : "—");
        if (tvStatus != null) {
            if (inv.completed) {
                tvStatus.setText("✓ ĐÃ THANH TOÁN");
                tvStatus.setTextColor(0xFF2E7D32);
            } else {
                tvStatus.setText("● CHƯA THANH TOÁN");
                tvStatus.setTextColor(0xFFE67700);
            }
        }
        tvRental.setText(money(inv.rental));
        if (rowExtend != null) rowExtend.setVisibility(inv.extendAmount > 0 ? View.VISIBLE : View.GONE);
        if (tvExtendLabel != null) {
            tvExtendLabel.setText(inv.extendDays > 0
                    ? "Tiền gia hạn thêm (" + inv.extendDays + " ngày)" : "Tiền gia hạn thêm");
        }
        if (tvExtend != null) tvExtend.setText(money(inv.extendAmount));
        tvLateLabel.setText(inv.lateDays > 0 ? "Phí phạt trễ (" + inv.lateDays + " ngày)" : "Phí phạt trễ");
        tvPenalty.setText(money(inv.penalty));
        tvTotal.setText(money(inv.invoiceTotal));
        tvReason.setText(inv.reason != null && !inv.reason.isEmpty() ? inv.reason
                : "Thanh toán tiền thuê xe khi kết thúc chuyến.");

        if (rowDiscount != null) {
            rowDiscount.setVisibility(inv.discount > 0 ? View.VISIBLE : View.GONE);
        }
        if (tvDiscount != null && inv.discount > 0) {
            tvDiscount.setText("-" + money(inv.discount));
        }

        // Tiền cọc đã trả trước (trừ vào tổng) → chỉ hiện khi đơn có cọc.
        if (rowPrepaid != null) {
            rowPrepaid.setVisibility(inv.prepaid > 0 ? View.VISIBLE : View.GONE);
        }
        if (tvPrepaid != null) {
            tvPrepaid.setText("-" + money(inv.prepaid));
        }
        if (tvRemaining != null) {
            tvRemaining.setText(money(inv.remaining));
        }

        if (inv.completed) {
            btnPay.setEnabled(false);
            btnPay.setText("Đã thanh toán ✓");
            if (rowChooseVoucher != null) rowChooseVoucher.setVisibility(View.GONE);
        } else {
            btnPay.setText(payButtonLabel(inv));
            btnPay.setOnClickListener(v -> viewModel.pay());
        }
    }

    /** Mở hộp thoại chọn 1 voucher trong ví để áp dụng cho hóa đơn này (hoặc bỏ áp dụng). */
    private void showVoucherPicker() {
        if (currentVouchers.isEmpty()) return;

        String[] labels = new String[currentVouchers.size() + 1];
        labels[0] = "Không dùng voucher";
        for (int i = 0; i < currentVouchers.size(); i++) {
            UserVoucher uv = currentVouchers.get(i);
            String title = uv.getTitle() != null ? uv.getTitle() : uv.shortLabel();
            labels[i + 1] = title + " — " + uv.shortLabel();
        }

        new AlertDialog.Builder(this)
                .setTitle("Chọn voucher giảm giá")
                .setItems(labels, (dialog, which) -> {
                    if (which == 0) {
                        viewModel.selectVoucher(null);
                    } else {
                        viewModel.selectVoucher(currentVouchers.get(which - 1));
                    }
                })
                .setNegativeButton("Đóng", null)
                .show();
    }

    /** Nhãn nút thanh toán theo phương thức đã chọn lúc đặt thuê — thu phần CÒN LẠI. */
    private String payButtonLabel(InvoiceViewModel.Invoice inv) {
        switch (inv.paymentMethod != null ? inv.paymentMethod : "cash") {
            case "vnpay":  return "Chuyển khoản VNPay " + money(inv.remaining);
            case "wallet": return "Thanh toán bằng ví " + money(inv.remaining);
            default:        return "Xác nhận đã trả tiền mặt " + money(inv.remaining);
        }
    }

    private String money(long v) {
        return String.format(Locale.US, "%,d", v).replace(',', '.') + " đ";
    }
}