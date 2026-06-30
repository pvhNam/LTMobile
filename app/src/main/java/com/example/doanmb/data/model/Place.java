package com.example.doanmb.data.model;

/**
 * Model địa danh hành chính bất biến dùng trong UI (tỉnh/thành hoặc phường/xã).
 * {@code code} là mã đơn vị do API cấp, dùng để truy vấn cấp con.
 */
public class Place {
    public final String name;
    public final int code;

    public Place(String name, int code) {
        this.name = name;
        this.code = code;
    }
}