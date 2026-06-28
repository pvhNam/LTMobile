package com.example.doanmb.ui.car.viewmodel;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.doanmb.data.repository.CarRepository;
import com.example.doanmb.data.repository.OrderRepository;
import com.example.doanmb.data.repository.WalletRepository;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * ViewModel màn Hóa đơn thuê: tải chi tiết hóa đơn và xử lý thanh toán
 * (tiền mặt / VNPay / ví) qua {@link OrderRepository} + {@link WalletRepository}.
 */
public class InvoiceViewModel extends ViewModel {

    /** Dữ liệu hiển thị của một hóa đơn. */
    public static class Invoice {
        public String carName, ownerName, reason, paymentMethod;
        public long rental, penalty, lateDays, invoiceTotal;
        public boolean completed;
    }

    private final MutableLiveData<Invoice> invoice = new MutableLiveData<>();
    private final MutableLiveData<String> message = new MutableLiveData<>();
    private final MutableLiveData<Boolean> payEnabled = new MutableLiveData<>(true);
    private final MutableLiveData<Boolean> finishEvent = new MutableLiveData<>();
    private final MutableLiveData<Long> launchVnpay = new MutableLiveData<>();

    private String orderId;
    private String sellerId;
    private String carId;
    private long invoiceTotal;
    private String paymentMethod = "cash"; // cash | vnpay | wallet

    public LiveData<Invoice> getInvoice() { return invoice; }
    public LiveData<String> getMessage() { return message; }
    public LiveData<Boolean> getPayEnabled() { return payEnabled; }
    public LiveData<Boolean> getFinishEvent() { return finishEvent; }
    public LiveData<Long> getLaunchVnpay() { return launchVnpay; }

    public void clearLaunchVnpay() { launchVnpay.setValue(null); }

    public long getInvoiceTotal() { return invoiceTotal; }

    /** Tải hóa đơn theo mã đơn. Gọi 1 lần khi mở màn. */
    public void start(String orderId) {
        if (this.orderId != null) return; // đã tải
        this.orderId = orderId;
        if (orderId == null || orderId.isEmpty()) {
            message.setValue("Không tìm thấy hóa đơn");
            finishEvent.setValue(true);
            return;
        }
        loadInvoice();
    }

    private void loadInvoice() {
        OrderRepository.loadOrder(orderId, new OrderRepository.OnOrderLoaded() {
            @Override public void onLoaded(DocumentSnapshot doc) {
                if (doc == null || !doc.exists()) {
                    message.setValue("Hóa đơn không tồn tại");
                    finishEvent.setValue(true);
                    return;
                }
                Invoice inv = new Invoice();
                inv.carName   = doc.getString("carName");
                inv.ownerName = doc.getString("sellerName");
                sellerId      = doc.getString("sellerId");
                carId         = doc.getString("carId");
                inv.rental    = getLong(doc.getLong("totalAmount"));
                inv.penalty   = getLong(doc.getLong("penaltyAmount"));
                inv.lateDays  = getLong(doc.getLong("lateDays"));
                Long total    = doc.getLong("invoiceTotal");
                invoiceTotal  = total != null ? total : inv.rental + inv.penalty;
                inv.invoiceTotal = invoiceTotal;
                inv.reason    = doc.getString("invoiceReason");
                inv.completed = "completed".equals(doc.getString("status"));
                String method = doc.getString("paymentMethod");
                if (method != null && !method.isEmpty()) paymentMethod = method;
                inv.paymentMethod = paymentMethod;

                invoice.setValue(inv);

                // Nếu đơn thiếu sellerId → lấy chủ xe từ document xe để tiền tới đúng người.
                if ((sellerId == null || sellerId.isEmpty()) && carId != null && !carId.isEmpty()) {
                    CarRepository.loadOwner(carId, owner -> { if (owner != null) sellerId = owner; });
                }
            }
            @Override public void onError(String msg) {
                message.setValue("Lỗi tải hóa đơn: " + msg);
                finishEvent.setValue(true);
            }
        });
    }

    /** Bấm nút thanh toán. */
    public void pay() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) { message.setValue("Vui lòng đăng nhập"); return; }
        payEnabled.setValue(false);

        // Tiền mặt: trả trực tiếp cho chủ xe, app không chuyển tiền → chỉ ghi nhận hoàn tất.
        if ("cash".equals(paymentMethod)) {
            markCompleted(user.getUid(), "✅ Đã xác nhận thanh toán tiền mặt cho chủ xe.");
            return;
        }
        // VNPay / Ví cần uid chủ xe để chuyển tiền (lấy từ xe nếu đơn thiếu sellerId).
        final String uid = user.getUid();
        resolveOwnerThen(() -> doPay(uid));
    }

    /** Bảo đảm có uid chủ xe rồi mới chạy {@code next} (tránh chuyển tiền sai người). */
    private void resolveOwnerThen(Runnable next) {
        if (sellerId != null && !sellerId.isEmpty()) { next.run(); return; }
        if (carId == null || carId.isEmpty()) {
            payEnabled.setValue(true);
            message.setValue("Thiếu thông tin chủ xe");
            return;
        }
        CarRepository.loadOwner(carId, owner -> {
            if (owner != null) { sellerId = owner; next.run(); }
            else {
                payEnabled.setValue(true);
                message.setValue("Không xác định được chủ xe để chuyển tiền");
            }
        });
    }

    private void doPay(String uid) {
        if ("vnpay".equals(paymentMethod)) {
            launchVnpay.setValue(invoiceTotal); // View mở VnpayPaymentActivity
            return;
        }
        // Mặc định: thanh toán bằng ví → trừ ví khách, chia 85% chủ xe / 15% app.
        WalletRepository.payInvoice(uid, sellerId, invoiceTotal, orderId,
                new WalletRepository.Callback() {
                    @Override public void onSuccess() {
                        markCompleted(uid, "✅ Thanh toán thành công! Tiền đã chuyển cho chủ xe.");
                    }
                    @Override public void onError(String msg) {
                        payEnabled.setValue(true);
                        message.setValue("❌ " + (msg != null ? msg : "Thanh toán thất bại"));
                    }
                });
    }

    /** VNPay báo trả thành công → chia 85% chủ xe / 15% app (tiền vào từ VNPay). */
    public void onVnpayPaid() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        final String myUid = user != null ? user.getUid() : "";
        WalletRepository.payInvoiceExternal(sellerId, invoiceTotal, orderId,
                new WalletRepository.Callback() {
                    @Override public void onSuccess() {
                        markCompleted(myUid, "✅ Thanh toán VNPay thành công! Tiền đã chuyển cho chủ xe.");
                    }
                    @Override public void onError(String msg) {
                        payEnabled.setValue(true);
                        message.setValue("❌ " + (msg != null ? msg : "Cập nhật thất bại"));
                    }
                });
    }

    public void onVnpayCancelled() {
        payEnabled.setValue(true);
        message.setValue("Thanh toán VNPay chưa hoàn tất hoặc đã huỷ");
    }

    /** Đánh dấu đơn đã hoàn tất, mở lại xe để cho thuê tiếp và báo chủ xe. */
    private void markCompleted(String myUid, String successMessage) {
        Map<String, Object> up = new HashMap<>();
        up.put("status", "completed");
        up.put("invoiceStatus", "paid");
        up.put("paidAt", Timestamp.now());
        OrderRepository.updateFields(orderId, up);
        if (carId != null && !carId.isEmpty()) CarRepository.setStatus(carId, "active", null);
        // Báo cho chủ xe biết khách đã thanh toán.
        OrderRepository.writeNotification(sellerId, myUid, "invoice_paid",
                "Khách đã thanh toán",
                "Hóa đơn thuê xe đã được thanh toán: " + money(invoiceTotal), orderId);
        message.setValue(successMessage);
        finishEvent.setValue(true);
    }

    private static long getLong(Long v) { return v != null ? v : 0L; }

    private static String money(long v) {
        return String.format(Locale.US, "%,d", v).replace(',', '.') + " đ";
    }
}
