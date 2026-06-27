package com.example.doanmb.ui.car.view;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.doanmb.R;
import com.example.doanmb.core.util.EdgeToEdgeUtil;
import com.example.doanmb.data.repository.WalletRepository;
import com.example.doanmb.ui.profile.view.VnpayPaymentActivity;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** Màn hóa đơn thuê xe: hiện chi tiết (tiền thuê + phạt trễ + lý do) và cho khách thanh toán. */
public class InvoiceActivity extends AppCompatActivity {

    private FirebaseFirestore db;
    private String orderId;
    private String sellerId;
    private String carId;
    private long invoiceTotal;
    private String paymentMethod = "cash"; // cash | vnpay | wallet

    private TextView tvCar, tvOwner, tvRental, tvPenalty, tvTotal, tvReason, tvLateLabel;
    private MaterialButton btnPay;

    // Nhận kết quả thanh toán VNPay
    private final ActivityResultLauncher<Intent> vnpayLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                Intent data = result.getData();
                if (result.getResultCode() == RESULT_OK && data != null
                        && data.getBooleanExtra(VnpayPaymentActivity.EXTRA_SUCCESS, false)) {
                    onVnpayPaid();
                } else {
                    btnPay.setEnabled(true);
                    Toast.makeText(this, "Thanh toán VNPay chưa hoàn tất hoặc đã huỷ", Toast.LENGTH_SHORT).show();
                }
            });

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdgeUtil.enable(this, true);
        setContentView(R.layout.activity_invoice);
        EdgeToEdgeUtil.padContentForSystemBars(this, 0xFFFFFFFF);

        db = FirebaseFirestore.getInstance();
        orderId = getIntent().getStringExtra("ORDER_ID");

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

        if (orderId == null || orderId.isEmpty()) {
            Toast.makeText(this, "Không tìm thấy hóa đơn", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        loadInvoice();
    }

    private void loadInvoice() {
        db.collection("orders").document(orderId).get().addOnSuccessListener(doc -> {
            if (doc == null || !doc.exists()) {
                Toast.makeText(this, "Hóa đơn không tồn tại", Toast.LENGTH_SHORT).show();
                finish();
                return;
            }
            String carName   = doc.getString("carName");
            String ownerName = doc.getString("sellerName");
            sellerId         = doc.getString("sellerId");
            carId            = doc.getString("carId");
            long rental      = getLong(doc.getLong("totalAmount"));
            long penalty     = getLong(doc.getLong("penaltyAmount"));
            long lateDays    = getLong(doc.getLong("lateDays"));
            Long total       = doc.getLong("invoiceTotal");
            invoiceTotal     = total != null ? total : rental + penalty;
            String reason    = doc.getString("invoiceReason");
            String status    = doc.getString("status");
            String method    = doc.getString("paymentMethod");
            if (method != null && !method.isEmpty()) paymentMethod = method;

            tvCar.setText(carName != null ? carName : "Xe");
            tvOwner.setText("Chủ xe: " + (ownerName != null && !ownerName.isEmpty() ? ownerName : "—"));
            tvRental.setText(money(rental));
            tvLateLabel.setText(lateDays > 0 ? "Phí phạt trễ (" + lateDays + " ngày)" : "Phí phạt trễ");
            tvPenalty.setText(money(penalty));
            tvTotal.setText(money(invoiceTotal));
            tvReason.setText(reason != null && !reason.isEmpty() ? reason
                    : "Thanh toán tiền thuê xe khi kết thúc chuyến.");

            // Nếu đơn thiếu sellerId → lấy chủ xe từ document xe để tiền tới đúng người
            if ((sellerId == null || sellerId.isEmpty()) && carId != null && !carId.isEmpty()) {
                db.collection("cars").document(carId).get().addOnSuccessListener(carDoc -> {
                    if (carDoc != null && carDoc.exists()) {
                        String owner = carDoc.getString("sellerId");
                        if (owner == null || owner.isEmpty()) owner = carDoc.getString("userId");
                        if (owner != null && !owner.isEmpty()) sellerId = owner;
                    }
                });
            }

            if ("completed".equals(status)) {
                btnPay.setEnabled(false);
                btnPay.setText("Đã thanh toán ✓");
            } else {
                btnPay.setText(payButtonLabel());
                btnPay.setOnClickListener(v -> pay());
            }
        }).addOnFailureListener(e -> {
            Toast.makeText(this, "Lỗi tải hóa đơn: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            finish();
        });
    }

    private void pay() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) { Toast.makeText(this, "Vui lòng đăng nhập", Toast.LENGTH_SHORT).show(); return; }
        btnPay.setEnabled(false);

        // Tiền mặt: trả trực tiếp cho chủ xe, app không chuyển tiền → chỉ ghi nhận hoàn tất.
        if ("cash".equals(paymentMethod)) {
            markCompleted(user.getUid(), "✅ Đã xác nhận thanh toán tiền mặt cho chủ xe.");
            return;
        }

        // VNPay / Ví đều cần uid chủ xe để chuyển tiền (lấy từ xe nếu đơn thiếu sellerId).
        resolveOwnerThen(() -> doPay(user));
    }

    /** Bảo đảm có uid chủ xe rồi mới chạy {@code next} (tránh chuyển tiền sai người). */
    private void resolveOwnerThen(Runnable next) {
        if (sellerId != null && !sellerId.isEmpty()) { next.run(); return; }
        if (carId == null || carId.isEmpty()) {
            btnPay.setEnabled(true);
            Toast.makeText(this, "Thiếu thông tin chủ xe", Toast.LENGTH_SHORT).show();
            return;
        }
        db.collection("cars").document(carId).get().addOnSuccessListener(carDoc -> {
            String owner = carDoc != null && carDoc.exists() ? carDoc.getString("sellerId") : null;
            if (owner == null || owner.isEmpty()) owner = carDoc != null ? carDoc.getString("userId") : null;
            if (owner != null && !owner.isEmpty()) {
                sellerId = owner;
                next.run();
            } else {
                btnPay.setEnabled(true);
                Toast.makeText(this, "Không xác định được chủ xe để chuyển tiền", Toast.LENGTH_SHORT).show();
            }
        }).addOnFailureListener(e -> {
            btnPay.setEnabled(true);
            Toast.makeText(this, "Lỗi: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        });
    }

    private void doPay(FirebaseUser user) {
        if ("vnpay".equals(paymentMethod)) {
            Intent i = new Intent(this, VnpayPaymentActivity.class);
            i.putExtra(VnpayPaymentActivity.EXTRA_AMOUNT, invoiceTotal);
            vnpayLauncher.launch(i);
            return;
        }

        // Mặc định: thanh toán bằng ví → trừ ví khách, chia 85% chủ xe / 15% app.
        WalletRepository.payInvoice(user.getUid(), sellerId, invoiceTotal, orderId,
                new WalletRepository.Callback() {
                    @Override public void onSuccess() {
                        markCompleted(user.getUid(), "✅ Thanh toán thành công! Tiền đã chuyển cho chủ xe.");
                    }
                    @Override public void onError(String message) {
                        btnPay.setEnabled(true);
                        Toast.makeText(InvoiceActivity.this,
                                "❌ " + (message != null ? message : "Thanh toán thất bại"),
                                Toast.LENGTH_LONG).show();
                    }
                });
    }

    /** VNPay báo trả thành công → chia 85% chủ xe / 15% app (tiền vào từ VNPay, không trừ ví khách). */
    private void onVnpayPaid() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        final String myUid = user != null ? user.getUid() : "";
        WalletRepository.payInvoiceExternal(sellerId, invoiceTotal, orderId,
                new WalletRepository.Callback() {
                    @Override public void onSuccess() {
                        markCompleted(myUid, "✅ Thanh toán VNPay thành công! Tiền đã chuyển cho chủ xe.");
                    }
                    @Override public void onError(String message) {
                        btnPay.setEnabled(true);
                        Toast.makeText(InvoiceActivity.this,
                                "❌ " + (message != null ? message : "Cập nhật thất bại"),
                                Toast.LENGTH_LONG).show();
                    }
                });
    }

    /** Đánh dấu đơn đã hoàn tất, mở lại xe để cho thuê tiếp và báo chủ xe. */
    private void markCompleted(String myUid, String successMessage) {
        Map<String, Object> up = new HashMap<>();
        up.put("status", "completed");
        up.put("invoiceStatus", "paid");
        up.put("paidAt", Timestamp.now());
        db.collection("orders").document(orderId).update(up);
        // Xe thuê xong → kích hoạt lại để hiện trong danh sách, cho thuê tiếp
        if (carId != null && !carId.isEmpty()) {
            db.collection("cars").document(carId).update("status", "active");
        }
        notifyOwnerPaid(myUid);
        Toast.makeText(this, successMessage, Toast.LENGTH_LONG).show();
        btnPay.setText("Đã thanh toán ✓");
        finish();
    }

    /** Nhãn nút thanh toán theo phương thức đã chọn lúc đặt thuê. */
    private String payButtonLabel() {
        switch (paymentMethod) {
            case "vnpay":  return "Chuyển khoản VNPay " + money(invoiceTotal);
            case "wallet": return "Thanh toán bằng ví " + money(invoiceTotal);
            default:        return "Xác nhận đã trả tiền mặt " + money(invoiceTotal);
        }
    }

    /** Báo cho chủ xe biết khách đã thanh toán hóa đơn. */
    private void notifyOwnerPaid(String myUid) {
        if (sellerId == null || sellerId.isEmpty()) return;
        Map<String, Object> n = new HashMap<>();
        n.put("userId", sellerId);
        n.put("senderId", myUid);
        n.put("type", "invoice_paid");
        n.put("title", "Khách đã thanh toán");
        n.put("body", "Hóa đơn thuê xe đã được thanh toán: " + money(invoiceTotal));
        n.put("orderId", orderId);
        n.put("read", false);
        n.put("createdAt", Timestamp.now());
        db.collection("notifications").add(n);
    }

    private static long getLong(Long v) { return v != null ? v : 0L; }

    private String money(long v) {
        return String.format(Locale.US, "%,d", v).replace(',', '.') + " đ";
    }
}
