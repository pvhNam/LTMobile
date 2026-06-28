package com.example.doanmb.ui.car.view;

import android.content.Intent;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.example.doanmb.R;
import com.example.doanmb.core.util.EdgeToEdgeUtil;
import com.example.doanmb.ui.car.viewmodel.InvoiceViewModel;
import com.example.doanmb.ui.profile.view.VnpayPaymentActivity;
import com.google.android.material.button.MaterialButton;

import java.util.Locale;

/** Màn hóa đơn thuê xe: hiện chi tiết (tiền thuê + phạt trễ + lý do) và cho khách thanh toán. */
public class InvoiceActivity extends AppCompatActivity {

    private TextView tvCar, tvOwner, tvRental, tvPenalty, tvTotal, tvReason, tvLateLabel;
    private MaterialButton btnPay;

    private InvoiceViewModel viewModel;

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
        tvRental   = findViewById(R.id.tv_invoice_rental);
        tvPenalty  = findViewById(R.id.tv_invoice_penalty);
        tvTotal    = findViewById(R.id.tv_invoice_total);
        tvReason   = findViewById(R.id.tv_invoice_reason);
        tvLateLabel= findViewById(R.id.tv_invoice_late_label);
        btnPay     = findViewById(R.id.btn_pay_invoice);

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
    }

    private void render(InvoiceViewModel.Invoice inv) {
        if (inv == null) return;
        tvCar.setText(inv.carName != null ? inv.carName : "Xe");
        tvOwner.setText("Chủ xe: " + (inv.ownerName != null && !inv.ownerName.isEmpty() ? inv.ownerName : "—"));
        tvRental.setText(money(inv.rental));
        tvLateLabel.setText(inv.lateDays > 0 ? "Phí phạt trễ (" + inv.lateDays + " ngày)" : "Phí phạt trễ");
        tvPenalty.setText(money(inv.penalty));
        tvTotal.setText(money(inv.invoiceTotal));
        tvReason.setText(inv.reason != null && !inv.reason.isEmpty() ? inv.reason
                : "Thanh toán tiền thuê xe khi kết thúc chuyến.");

        if (inv.completed) {
            btnPay.setEnabled(false);
            btnPay.setText("Đã thanh toán ✓");
        } else {
            btnPay.setText(payButtonLabel(inv));
            btnPay.setOnClickListener(v -> viewModel.pay());
        }
    }

    /** Nhãn nút thanh toán theo phương thức đã chọn lúc đặt thuê. */
    private String payButtonLabel(InvoiceViewModel.Invoice inv) {
        switch (inv.paymentMethod != null ? inv.paymentMethod : "cash") {
            case "vnpay":  return "Chuyển khoản VNPay " + money(inv.invoiceTotal);
            case "wallet": return "Thanh toán bằng ví " + money(inv.invoiceTotal);
            default:        return "Xác nhận đã trả tiền mặt " + money(inv.invoiceTotal);
        }
    }

    private String money(long v) {
        return String.format(Locale.US, "%,d", v).replace(',', '.') + " đ";
    }
}
