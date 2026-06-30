package com.example.doanmb.ui.admin.util;

import com.google.firebase.firestore.DocumentSnapshot;

/**
 * Logic tính doanh thu hoa hồng dùng chung cho các màn admin
 * (Tổng quan & Chi tiết doanh thu). Gom về một nơi để hai màn
 * không bị lệch công thức như trước đây.
 */
public final class RevenueCalculator {

    private RevenueCalculator() {}

    public static final double SALE_COMMISSION   = 0.03;     // 3% giá xe khi bán
    public static final double RENTAL_COMMISSION = 0.15;     // 15% phí khi thuê
    public static final long   POSTING_FEE       = 200_000L; // 200K / bài đăng

    /** Đọc số tiền từ chuỗi (bỏ mọi ký tự không phải số). Trả 0 nếu rỗng/sai. */
    public static long parsePrice(String priceStr) {
        if (priceStr == null || priceStr.isEmpty()) return 0;
        String digits = priceStr.replaceAll("[^0-9]", "");
        if (digits.isEmpty()) return 0;
        try {
            return Long.parseLong(digits);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** Hoa hồng app thu được từ một đơn hàng. */
    public static long commission(DocumentSnapshot doc) {
        String type  = doc.getString("type");
        long   price = parsePrice(doc.getString("carPrice"));
        if ("Thuê xe".equals(type)) {
            long days = 1;
            String daysStr = doc.getString("days");
            if (daysStr != null && !daysStr.isEmpty()) {
                try {
                    long d = Long.parseLong(daysStr.replaceAll("[^0-9]", ""));
                    if (d > 0) days = d;
                } catch (NumberFormatException ignored) {}
            }
            return (long) (price * days * RENTAL_COMMISSION);
        }
        return (long) (price * SALE_COMMISSION);
    }
}
