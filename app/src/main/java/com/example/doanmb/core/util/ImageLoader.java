package com.example.doanmb.core.util;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.widget.ImageView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.RequestBuilder;
import com.bumptech.glide.load.engine.DiskCacheStrategy;

/**
 * Tải ảnh dùng chung toàn app.
 * - Giữ NGUYÊN chất lượng ảnh gốc (không hạ RGB_565, không nén lại).
 * - Cache đĩa (DiskCacheStrategy.ALL) + cache bộ nhớ mặc định → lần sau hiện tức thì,
 *   không tải lại nhiều lần.
 * - dontAnimate(): khi bind lại item (vd đổi trạng thái tim) ảnh không nhấp nháy.
 */
public final class ImageLoader {

    private ImageLoader() {}

    /** Ảnh card/danh sách (cắt đầy khung). */
    public static void loadCard(ImageView iv, String url, int placeholderRes) {
        Glide.with(iv.getContext())
                .load(url)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .placeholder(placeholderRes)
                .error(placeholderRes)
                .centerCrop()
                .dontAnimate()
                .into(iv);
    }

    /** Ảnh chi tiết/gallery (theo scaleType của ImageView). */
    public static void loadDetail(ImageView iv, String url, int placeholderRes) {
        Glide.with(iv.getContext())
                .load(url)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .placeholder(placeholderRes)
                .dontAnimate()
                .into(iv);
    }

    /** Avatar tròn (có ảnh chờ). */
    public static void loadAvatar(ImageView iv, String url, int placeholderRes) {
        avatar(iv, url, placeholderRes);
    }

    /** Avatar tròn (không cần ảnh chờ). */
    public static void loadAvatar(ImageView iv, String url) {
        avatar(iv, url, 0);
    }

    /**
     * Tải sẵn ảnh vào cache đĩa để khi hiển thị không phải chờ mạng (giữ NGUYÊN chất lượng gốc).
     * Nên truyền application context để preload không bị hủy khi màn hình đóng.
     */
    public static void preload(Context ctx, String url) {
        if (ctx == null || url == null || url.isEmpty()) return;
        Glide.with(ctx).load(url).diskCacheStrategy(DiskCacheStrategy.ALL).preload();
    }

    private static void avatar(ImageView iv, String url, int placeholderRes) {
        RequestBuilder<Drawable> rb = Glide.with(iv.getContext())
                .load(url)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .circleCrop()
                .dontAnimate();
        if (placeholderRes != 0) rb = rb.placeholder(placeholderRes).error(placeholderRes);
        rb.into(iv);
    }
}
