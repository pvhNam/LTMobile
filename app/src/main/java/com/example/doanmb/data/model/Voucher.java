package com.example.doanmb.data.model;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.Exclude;

/**
 * Một loại voucher giảm giá có thể đổi bằng điểm thưởng, ánh xạ từ document
 * trong collection "vouchers". Đây là "catalog" (định nghĩa quà tặng) — khi
 * người dùng đổi quà, một bản sao thông tin được lưu vào ví của họ dưới dạng
 * {@link UserVoucher}.
 */
public class Voucher {

    public static final String TYPE_PERCENT = "percent"; // giảm theo %
    public static final String TYPE_FIXED   = "fixed";    // giảm số tiền cố định (VNĐ)

    private String id;            // = document id, set thủ công sau khi đọc
    private String title;         // Tên hiển thị, vd "Giảm 10% tối đa 50k"
    private String description;   // Mô tả ngắn
    private String discountType;  // percent | fixed
    private double discountValue; // % (0-100) hoặc số tiền cố định (VNĐ)
    private long   maxDiscount;   // Giảm tối đa (VNĐ) — chỉ áp dụng khi discountType = percent, 0 = không giới hạn
    private long   minOrderAmount;// Đơn tối thiểu để áp dụng (VNĐ)
    private int    pointsCost;    // Số điểm cần để đổi
    private int    quantity;      // Số lượng còn lại trong kho, -1 = không giới hạn
    private boolean active;       // Còn cho đổi hay không (admin tắt/mở)
    private int    validDays;     // Số ngày hiệu lực kể từ lúc đổi

    public Voucher() {}

    @Exclude
    public String getId() { return id; }
    @Exclude
    public void setId(String id) { this.id = id; }

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

    public int getPointsCost() { return pointsCost; }
    public void setPointsCost(int pointsCost) { this.pointsCost = pointsCost; }

    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public int getValidDays() { return validDays; }
    public void setValidDays(int validDays) { this.validDays = validDays; }

    /** true nếu voucher còn có thể đổi: đang mở và còn hàng. */
    @Exclude
    public boolean isRedeemable() {
        if (!active) return false;
        if (quantity == 0) return false;
        return true;
    }

    /** Tính số tiền được giảm cho một đơn có tổng {@code orderAmount}. 0 nếu không đủ điều kiện. */
    @Exclude
    public long computeDiscount(long orderAmount) {
        if (orderAmount < minOrderAmount) return 0;
        long discount;
        if (TYPE_PERCENT.equals(discountType)) {
            discount = Math.round(orderAmount * (discountValue / 100.0));
            if (maxDiscount > 0) discount = Math.min(discount, maxDiscount);
        } else {
            discount = Math.round(discountValue);
        }
        return Math.min(discount, orderAmount);
    }

    /** Nhãn ngắn hiển thị mức giảm, vd "Giảm 10%" hoặc "Giảm 50.000 đ". */
    @Exclude
    public String shortLabel() {
        if (TYPE_PERCENT.equals(discountType)) {
            String cap = maxDiscount > 0 ? " (tối đa " + formatVnd(maxDiscount) + ")" : "";
            return "Giảm " + (int) discountValue + "%" + cap;
        }
        return "Giảm " + formatVnd((long) discountValue);
    }

    private static String formatVnd(long v) {
        return java.text.NumberFormat.getInstance(new java.util.Locale("vi", "VN")).format(v) + " đ";
    }
}