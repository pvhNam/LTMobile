package com.example.doanmb.ui.admin.util;

import java.text.NumberFormat;
import java.util.Locale;

/** Định dạng hiển thị dùng chung trong khu vực admin. */
public final class AdminFormat {

    private AdminFormat() {}

    private static final NumberFormat VN =
            NumberFormat.getInstance(new Locale("vi", "VN"));

    /** Số tiền đầy đủ có dấu phân nhóm, vd 1.900.000 đ — không làm tròn gây sai lệch. */
    public static String money(long amount) {
        return VN.format(amount) + " đ";
    }
}
