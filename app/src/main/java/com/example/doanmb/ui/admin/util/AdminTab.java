package com.example.doanmb.ui.admin.util;

import android.content.res.ColorStateList;
import android.widget.Button;

/** Tô màu cụm nút tab (đỏ = đang chọn, hồng nhạt = còn lại) dùng chung cho các màn admin. */
public final class AdminTab {

    private AdminTab() {}

    public static final int ACTIVE_BG     = 0xFFC62828;
    public static final int ACTIVE_TEXT   = 0xFFFFFFFF;
    public static final int INACTIVE_BG   = 0xFFFFCDD2;
    public static final int INACTIVE_TEXT = 0xFFC62828;

    /** Đặt {@code active} là tab đang chọn, các nút còn lại về trạng thái thường. */
    public static void select(Button active, Button... all) {
        for (Button b : all) {
            boolean on = b == active;
            b.setBackgroundTintList(ColorStateList.valueOf(on ? ACTIVE_BG : INACTIVE_BG));
            b.setTextColor(on ? ACTIVE_TEXT : INACTIVE_TEXT);
        }
    }
}
