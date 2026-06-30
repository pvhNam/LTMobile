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
        public String carName, ownerName, payerName, payerPhone, reason, paymentMethod;
        public String code;           // Mã hóa đơn (rút gọn từ orderId)
        public String issuedAt;       // Ngày giờ lập hóa đơn
        public String periodText;     // Thời gian thuê: dd/MM → dd/MM (n ngày)
        public String pricePerDayText;// Đơn giá thuê / ngày
        public String methodLabel;    // Nhãn phương thức thanh toán
        public long rental, penalty, lateDays, invoiceTotal;
        public long extendDays, extendAmount; // Gia hạn thêm (ngày · tiền)
        public long discount;  // Số tiền đã giảm nhờ voucher (0 nếu không áp dụng)
        public long prepaid;   // Tiền cọc đã trả trước khi đặt (0 nếu đơn không cọc)
        public long remaining; // Số tiền còn lại phải trả = tổng - đã trả trước
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
    private String buyerId;        // Khách đặt đơn
    private String renterName;     // Tên người thuê (người thanh toán) hiện trên hóa đơn
    private long depositAmount;    // Cọc 50% đã trả trước khi đặt (0 nếu đơn không cọc)
    private String depositStatus;  // held | none | refunded | settled
    private long rentalTotal;     // Tiền thuê + phạt, CHƯA trừ voucher
    private long invoiceTotal;    // Tổng tiền phải trả, ĐÃ trừ voucher (chưa trừ cọc trả trước)
    private long prepaid;         // Cọc đã trả trước (trừ vào tổng)
    private long remaining;       // Số tiền còn lại khách phải trả nốt = invoiceTotal - prepaid
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
                buyerId       = doc.getString("buyerId");
                renterName    = doc.getString("renterName");
                depositAmount = getLong(doc.getLong("depositAmount"));
                depositStatus = doc.getString("depositStatus");
                long rental   = getLong(doc.getLong("totalAmount"));
                long penalty  = getLong(doc.getLong("penaltyAmount"));
                long lateDays = getLong(doc.getLong("lateDays"));
                long extendDays   = getLong(doc.getLong("extendDays"));
                long extendAmount = getLong(doc.getLong("extendAmount"));
                Long total    = doc.getLong("invoiceTotal");
                rentalTotal   = total != null ? total : rental + extendAmount + penalty;
                invoiceTotal  = rentalTotal;
                prepaid       = displayPrepaid();
                remaining     = Math.max(0, invoiceTotal - prepaid);
                String reason = doc.getString("invoiceReason");
                boolean completed = "completed".equals(doc.getString("status"));
                String method = doc.getString("paymentMethod");
                if (method != null && !method.isEmpty()) paymentMethod = method;

                // Thông tin để dựng "phiếu" hóa đơn
                String renterPhone = doc.getString("renterPhone");
                int days           = parseIntSafe(doc.getString("days"));
                String startDate   = doc.getString("startDate");
                long pricePerDay   = days > 0 ? rental / days : rental;
                com.google.firebase.Timestamp issuedTs = doc.getTimestamp("paidAt");
                if (issuedTs == null) issuedTs = doc.getTimestamp("returnedAt");
                if (issuedTs == null) issuedTs = doc.getTimestamp("createdAt");

                Invoice inv = new Invoice();
                inv.carName = carName; inv.ownerName = ownerName;
                inv.payerName = (renterName != null && !renterName.isEmpty()) ? renterName : "Khách thuê";
                inv.payerPhone = (renterPhone != null && !renterPhone.isEmpty()) ? renterPhone : "—";
                inv.code = shortCode(orderId);
                inv.issuedAt = fmtDateTime(issuedTs != null ? issuedTs : com.google.firebase.Timestamp.now());
                inv.periodText = buildPeriod(startDate, days);
                inv.pricePerDayText = days > 0 ? (money(pricePerDay) + " /ngày × " + days + " ngày") : "—";
                inv.methodLabel = methodLabel(paymentMethod);
                inv.rental = rental; inv.penalty = penalty; inv.lateDays = lateDays;
                inv.extendDays = extendDays; inv.extendAmount = extendAmount;
                inv.invoiceTotal = invoiceTotal; inv.discount = 0;
                inv.prepaid = prepaid; inv.remaining = remaining;
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
        prepaid      = displayPrepaid();
        remaining    = Math.max(0, invoiceTotal - prepaid);

        Invoice cur = invoice.getValue();
        if (cur != null) {
            cur.invoiceTotal = invoiceTotal;
            cur.discount = discount;
            cur.prepaid = prepaid;
            cur.remaining = remaining;
            invoice.setValue(cur);
        }
    }

    /** Cọc hiển thị là "đã trả trước" khi đơn từng giữ cọc (đang giữ hoặc đã chia cho chủ xe). */
    private long displayPrepaid() {
        boolean hasDeposit = depositAmount > 0
                && ("held".equals(depositStatus) || "settled".equals(depositStatus));
        return hasDeposit ? depositAmount : 0L;
    }

    /** Bấm nút thanh toán. */
    public void pay() {
        if (myUid == null) {
            FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
            myUid = user != null ? user.getUid() : null;
        }
        if (myUid == null) { message.setValue("Vui lòng đăng nhập"); return; }
        payEnabled.setValue(false);

        // Tiền mặt: khách trả phần CÒN LẠI ngoài đời cho chủ xe; app chỉ chia phần cọc
        // (đã trả trước) cho chủ xe nếu có. Không cọc → chỉ ghi nhận hoàn tất.
        if ("cash".equals(paymentMethod)) {
            if ("held".equals(depositStatus) && depositAmount > 0) {
                resolveOwnerThen(() -> settleDepositThen(() ->
                        markCompleted("✅ Đã xác nhận. Khách trả tiền mặt phần còn lại cho chủ xe.")));
            } else {
                markCompleted("✅ Đã xác nhận thanh toán tiền mặt cho chủ xe.");
            }
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
        // Đủ tiền từ cọc trả trước (phần còn lại = 0) → chỉ chia cọc cho chủ xe & hoàn tất.
        if (remaining <= 0) {
            settleDepositThen(() -> markCompleted("✅ Thanh toán hoàn tất bằng tiền cọc đã trả trước."));
            return;
        }
        if ("vnpay".equals(paymentMethod)) {
            launchVnpay.setValue(remaining); // VNPay chỉ thu phần CÒN LẠI (đã áp voucher)
            return;
        }
        // Ví: trừ phần CÒN LẠI từ ví khách, chia 85/15; rồi chia tiếp phần cọc trả trước.
        WalletRepository.payInvoice(myUid, sellerId, remaining, orderId,
                new WalletRepository.Callback() {
                    @Override public void onSuccess() {
                        settleDepositThen(() ->
                                markCompleted("✅ Thanh toán thành công! Tiền đã chuyển cho chủ xe."));
                    }
                    @Override public void onError(String msg) {
                        payEnabled.setValue(true);
                        message.setValue("❌ " + (msg != null ? msg : "Thanh toán thất bại"));
                    }
                });
    }

    /** VNPay báo trả phần còn lại thành công → chia cho chủ xe; rồi chia tiếp phần cọc. */
    public void onVnpayPaid() {
        Runnable afterRemaining = () -> settleDepositThen(() ->
                markCompleted("✅ Thanh toán VNPay thành công! Tiền đã chuyển cho chủ xe."));
        if (remaining <= 0) { afterRemaining.run(); return; }
        WalletRepository.payInvoiceExternal(sellerId, remaining, orderId,
                new WalletRepository.Callback() {
                    @Override public void onSuccess() { afterRemaining.run(); }
                    @Override public void onError(String msg) {
                        payEnabled.setValue(true);
                        message.setValue("❌ " + (msg != null ? msg : "Cập nhật thất bại"));
                    }
                });
    }

    /** Chia phần cọc đã trả trước cho chủ xe (85% chủ xe / 15% app) rồi chạy {@code next}. */
    private void settleDepositThen(Runnable next) {
        if ("held".equals(depositStatus) && depositAmount > 0 && sellerId != null && !sellerId.isEmpty()) {
            WalletRepository.settle(sellerId, depositAmount, orderId, new WalletRepository.Callback() {
                @Override public void onSuccess() { depositStatus = "settled"; next.run(); }
                @Override public void onError(String msg) {
                    payEnabled.setValue(true);
                    message.setValue("❌ Lỗi chia tiền cọc: " + (msg != null ? msg : ""));
                }
            });
        } else {
            next.run();
        }
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
        // Cọc 50% là tiền TRẢ TRƯỚC: đã được chia cho chủ xe (settleDepositThen) trước khi
        // tới đây → đánh dấu "settled" để không xử lý lại.
        if ("settled".equals(depositStatus)) {
            up.put("depositStatus", "settled");
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

    // ── Helpers dựng "phiếu" hóa đơn ──────────────────────────────────────────

    /** Mã hóa đơn rút gọn từ orderId (6 ký tự đầu, in hoa). */
    private static String shortCode(String orderId) {
        if (orderId == null || orderId.isEmpty()) return "------";
        String s = orderId.length() >= 6 ? orderId.substring(0, 6) : orderId;
        return s.toUpperCase(Locale.US);
    }

    private static String methodLabel(String method) {
        if ("vnpay".equals(method))  return "Chuyển khoản VNPay";
        if ("wallet".equals(method)) return "Ví CarVIA";
        return "Tiền mặt";
    }

    private static String fmtDateTime(com.google.firebase.Timestamp ts) {
        if (ts == null) return "—";
        return new java.text.SimpleDateFormat("dd/MM/yyyy  HH:mm", Locale.US).format(ts.toDate());
    }

    /** Thời gian thuê: "dd/MM/yyyy → dd/MM/yyyy (n ngày)" hoặc "n ngày" nếu thiếu ngày bắt đầu. */
    private static String buildPeriod(String startDate, int days) {
        long startMs = parseStartMillis(startDate);
        if (startMs > 0 && days > 0) {
            long endMs = startMs + (long) days * 86_400_000L;
            java.text.SimpleDateFormat f = new java.text.SimpleDateFormat("dd/MM/yyyy", Locale.US);
            return f.format(new java.util.Date(startMs)) + " → "
                    + f.format(new java.util.Date(endMs)) + "  (" + days + " ngày)";
        }
        return days > 0 ? (days + " ngày") : "—";
    }

    private static long parseStartMillis(String s) {
        if (s == null || s.trim().isEmpty()) return 0L;
        String[] fmts = {"dd/MM/yyyy", "yyyy-MM-dd", "dd-MM-yyyy"};
        for (String f : fmts) {
            try {
                java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat(f, Locale.US);
                sdf.setLenient(false);
                java.util.Date dt = sdf.parse(s.trim());
                if (dt != null) return dt.getTime();
            } catch (Exception ignore) { }
        }
        return 0L;
    }

    private static int parseIntSafe(String s) {
        if (s == null) return 0;
        try { return Integer.parseInt(s.trim().replaceAll("[^0-9]", "")); } catch (Exception e) { return 0; }
    }
}