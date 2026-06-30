package com.example.doanmb.data.model;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.Exclude;

/**
 * Một voucher mà người dùng đã đổi, ánh xạ từ document trong collection
 * "user_vouchers". Khi đổi quà, toàn bộ thông tin giảm giá của {@link Voucher}
 * được sao chép sang đây để giữ nguyên giá trị dù sau này admin có sửa/xoá
 * voucher gốc trong catalog.
 */
public class UserVoucher {

    private String id;          // = document id, set thủ công sau khi đọc
    private String userId;
    private String voucherId;   // id của Voucher gốc trong catalog (để tham khảo)
    private String title;
    private String description;
    private String discountType;   // percent | fixed
    private double discountValue;
    private long   maxDiscount;
    private long   minOrderAmount;
    private boolean used;
    private String orderId;        // Đơn đã dùng voucher này (null nếu chưa dùng)
    private Timestamp redeemedAt;
    private Timestamp expiresAt;   // null = không hết hạn
    private Timestamp usedAt;

    public UserVoucher() {}

    @Exclude
    public String getId() { return id; }
    @Exclude
    public void setId(String id) { this.id = id; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getVoucherId() { return voucherId; }
    public void setVoucherId(String voucherId) { this.voucherId = voucherId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getDiscountType() { return discountType; }
    public void setDiscountType(String discountType) { this.discountType = discountType; }

    public double getDiscountValue() { return discountValue; }
    public void setDiscountValue(double discountValue) { this.discountValue = discountValue; }

    public long getMaxDiscount() { return maxDiscount; }
    public void setMaxDiscount(long maxDiscount) { this.maxDiscount = maxDiscount; }

    public long getMinOrderAmount() { return minOrderAmount; }
    public void setMinOrderAmount(long minOrderAmount) { this.minOrderAmount = minOrderAmount; }

    public boolean isUsed() { return used; }
    public void setUsed(boolean used) { this.used = used; }

    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }

    public Timestamp getRedeemedAt() { return redeemedAt; }
    public void setRedeemedAt(Timestamp redeemedAt) { this.redeemedAt = redeemedAt; }

    public Timestamp getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Timestamp expiresAt) { this.expiresAt = expiresAt; }

    public Timestamp getUsedAt() { return usedAt; }
    public void setUsedAt(Timestamp usedAt) { this.usedAt = usedAt; }

    /** true nếu voucher đã hết hạn dùng (so với thời điểm hiện tại). */
    @Exclude
    public boolean isExpired() {
        return expiresAt != null && expiresAt.toDate().getTime() < System.currentTimeMillis();
    }

    /** true nếu voucher còn có thể áp dụng cho một đơn hàng: chưa dùng, chưa hết hạn. */
    @Exclude
    public boolean isAvailable() {
        return !used && !isExpired();
    }

    /** Tính số tiền được giảm cho một đơn có tổng {@code orderAmount}. 0 nếu không đủ điều kiện. */
    @Exclude
    public long computeDiscount(long orderAmount) {
        if (orderAmount < minOrderAmount) return 0;
        long discount;
        if (Voucher.TYPE_PERCENT.equals(discountType)) {
            discount = Math.round(orderAmount * (discountValue / 100.0));
            if (maxDiscount > 0) discount = Math.min(discount, maxDiscount);
        } else {
            discount = Math.round(discountValue);
        }
        return Math.min(discount, orderAmount);
    }

    @Exclude
    public String shortLabel() {
        if (Voucher.TYPE_PERCENT.equals(discountType)) {
            return "Giảm " + (int) discountValue + "%";
        }
        return "Giảm " + formatVnd((long) discountValue);
    }

    private static String formatVnd(long v) {
        return java.text.NumberFormat.getInstance(new java.util.Locale("vi", "VN")).format(v) + " đ";
    }
}