package com.example.doanmb.ui.car.viewmodel;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.doanmb.data.model.UserVoucher;
import com.example.doanmb.data.repository.CarRepository;
import com.example.doanmb.data.repository.OrderRepository;
import com.example.doanmb.data.repository.VoucherRepository;
import com.example.doanmb.data.repository.WalletRepository;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;

import java.util.HashMap;
import java.util.List;
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
        public long discount; // Số tiền đã giảm nhờ voucher (0 nếu không áp dụng)
        public boolean completed;
    }

    private final MutableLiveData<Invoice> invoice = new MutableLiveData<>();
    private final MutableLiveData<String> message = new MutableLiveData<>();
    private final MutableLiveData<Boolean> payEnabled = new MutableLiveData<>(true);
    private final MutableLiveData<Boolean> finishEvent = new MutableLiveData<>();
    private final MutableLiveData<Long> launchVnpay = new MutableLiveData<>();
    private final MutableLiveData<List<UserVoucher>> availableVouchers = new MutableLiveData<>();
    private final MutableLiveData<UserVoucher> selectedVoucherLive = new MutableLiveData<>();

    private String orderId;
    private String sellerId;
    private String carId;
    private long rentalTotal;     // Tiền thuê + phạt, CHƯA trừ voucher
    private long invoiceTotal;    // Số tiền thực phải trả, ĐÃ trừ voucher (nếu có)
    private String paymentMethod = "cash"; // cash | vnpay | wallet
    private String myUid;
    private UserVoucher selectedVoucher;

    public LiveData<Invoice> getInvoice() { return invoice; }
    public LiveData<String> getMessage() { return message; }
    public LiveData<Boolean> getPayEnabled() { return payEnabled; }
    public LiveData<Boolean> getFinishEvent() { return finishEvent; }
    public LiveData<Long> getLaunchVnpay() { return launchVnpay; }
    public LiveData<List<UserVoucher>> getAvailableVouchers() { return availableVouchers; }
    public LiveData<UserVoucher> getSelectedVoucher() { return selectedVoucherLive; }

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
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        myUid = user != null ? user.getUid() : null;

        OrderRepository.loadOrder(orderId, new OrderRepository.OnOrderLoaded() {
            @Override public void onLoaded(DocumentSnapshot doc) {
                if (doc == null || !doc.exists()) {
                    message.setValue("Hóa đơn không tồn tại");
                    finishEvent.setValue(true);
                    return;
                }
                String carName   = doc.getString("carName");
                String ownerName = doc.getString("sellerName");
                sellerId      = doc.getString("sellerId");
                carId         = doc.getString("carId");
                long rental   = getLong(doc.getLong("totalAmount"));
                long penalty  = getLong(doc.getLong("penaltyAmount"));
                long lateDays = getLong(doc.getLong("lateDays"));
                Long total    = doc.getLong("invoiceTotal");
                rentalTotal   = total != null ? total : rental + penalty;
                invoiceTotal  = rentalTotal;
                String reason = doc.getString("invoiceReason");
                boolean completed = "completed".equals(doc.getString("status"));
                String method = doc.getString("paymentMethod");
                if (method != null && !method.isEmpty()) paymentMethod = method;

                Invoice inv = new Invoice();
                inv.carName = carName; inv.ownerName = ownerName;
                inv.rental = rental; inv.penalty = penalty; inv.lateDays = lateDays;
                inv.invoiceTotal = invoiceTotal; inv.discount = 0;
                inv.reason = reason; inv.completed = completed; inv.paymentMethod = paymentMethod;
                invoice.setValue(inv);

                // Nếu đơn thiếu sellerId → lấy chủ xe từ document xe để tiền tới đúng người.
                if ((sellerId == null || sellerId.isEmpty()) && carId != null && !carId.isEmpty()) {
                    CarRepository.loadOwner(carId, owner -> { if (owner != null) sellerId = owner; });
                }

                // Chỉ cần nạp voucher khả dụng khi hóa đơn còn chờ thanh toán.
                if (!completed && myUid != null) loadAvailableVouchers();
            }
            @Override public void onError(String msg) {
                message.setValue("Lỗi tải hóa đơn: " + msg);
                finishEvent.setValue(true);
            }
        });
    }

    /** Tải các voucher trong ví của khách còn dùng được, áp dụng được cho tổng tiền hóa đơn. */
    private void loadAvailableVouchers() {
        VoucherRepository.loadMyVouchers(myUid, new VoucherRepository.UserVoucherListCallback() {
            @Override public void onLoaded(List<UserVoucher> list) {
                List<UserVoucher> usable = new java.util.ArrayList<>();
                for (UserVoucher uv : list) {
                    if (uv.isAvailable() && uv.computeDiscount(rentalTotal) > 0) usable.add(uv);
                }
                availableVouchers.setValue(usable);
            }
            @Override public void onError(String msg) {
                // Không chặn luồng thanh toán nếu lỗi tải voucher — chỉ đơn giản là không có voucher để chọn.
                availableVouchers.setValue(new java.util.ArrayList<>());
            }
        });
    }

    /** Người dùng chọn 1 voucher để áp dụng giảm giá cho hóa đơn. */
    public void selectVoucher(@Nullable UserVoucher voucher) {
        selectedVoucher = voucher;
        selectedVoucherLive.setValue(voucher);
        recomputeTotal();
    }

    private void recomputeTotal() {
        long discount = selectedVoucher != null ? selectedVoucher.computeDiscount(rentalTotal) : 0L;
        invoiceTotal = Math.max(0, rentalTotal - discount);

        Invoice cur = invoice.getValue();
        if (cur != null) {
            cur.invoiceTotal = invoiceTotal;
            cur.discount = discount;
            invoice.setValue(cur);
        }
    }

    /** Bấm nút thanh toán. */
    public void pay() {
        if (myUid == null) {
            FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
            myUid = user != null ? user.getUid() : null;
        }
        if (myUid == null) { message.setValue("Vui lòng đăng nhập"); return; }
        payEnabled.setValue(false);

        // Tiền mặt: trả trực tiếp cho chủ xe, app không chuyển tiền → chỉ ghi nhận hoàn tất.
        if ("cash".equals(paymentMethod)) {
            markCompleted("✅ Đã xác nhận thanh toán tiền mặt cho chủ xe.");
            return;
        }
        // VNPay / Ví cần uid chủ xe để chuyển tiền (lấy từ xe nếu đơn thiếu sellerId).
        resolveOwnerThen(this::doPay);
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

    private void doPay() {
        if ("vnpay".equals(paymentMethod)) {
            launchVnpay.setValue(invoiceTotal); // View mở VnpayPaymentActivity (đã áp voucher)
            return;
        }
        // Mặc định: thanh toán bằng ví → trừ ví khách, chia 85% chủ xe / 15% app.
        // invoiceTotal ở đây đã trừ voucher (nếu có chọn).
        WalletRepository.payInvoice(myUid, sellerId, invoiceTotal, orderId,
                new WalletRepository.Callback() {
                    @Override public void onSuccess() {
                        markCompleted("✅ Thanh toán thành công! Tiền đã chuyển cho chủ xe.");
                    }
                    @Override public void onError(String msg) {
                        payEnabled.setValue(true);
                        message.setValue("❌ " + (msg != null ? msg : "Thanh toán thất bại"));
                    }
                });
    }

    /** VNPay báo trả thành công → chia 85% chủ xe / 15% app (tiền vào từ VNPay). */
    public void onVnpayPaid() {
        WalletRepository.payInvoiceExternal(sellerId, invoiceTotal, orderId,
                new WalletRepository.Callback() {
                    @Override public void onSuccess() {
                        markCompleted("✅ Thanh toán VNPay thành công! Tiền đã chuyển cho chủ xe.");
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
    private void markCompleted(String successMessage) {
        Map<String, Object> up = new HashMap<>();
        up.put("status", "completed");
        up.put("invoiceStatus", "paid");
        up.put("paidAt", Timestamp.now());
        if (selectedVoucher != null) {
            up.put("voucherId", selectedVoucher.getId());
            up.put("voucherDiscount", rentalTotal - invoiceTotal);
        }
        OrderRepository.updateFields(orderId, up);
        if (carId != null && !carId.isEmpty()) CarRepository.setStatus(carId, "active", null);

        // Đánh dấu voucher đã chọn là đã dùng cho đơn này (không chặn luồng nếu lỗi).
        if (selectedVoucher != null && selectedVoucher.getId() != null) {
            VoucherRepository.markUsed(selectedVoucher.getId(), orderId, null);
        }
        // Cộng điểm thưởng cho khách vì đã thanh toán xong đơn (không chặn luồng nếu lỗi).
        if (myUid != null) {
            VoucherRepository.addPoints(myUid, VoucherRepository.POINTS_PER_ORDER, null);
        }

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