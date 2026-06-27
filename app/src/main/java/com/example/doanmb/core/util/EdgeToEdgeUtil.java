package com.example.doanmb.core.util;

import android.app.Activity;
import android.content.Context;
import android.content.res.Resources;
import android.util.TypedValue;
import android.view.View;

import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsControllerCompat;

/**
 * Đồng bộ cách chừa thanh trạng thái / thanh điều hướng trên MỌI máy (edge-to-edge).
 *
 * Cách làm tối giản, KHÔNG phụ thuộc WindowInsets (vốn dễ không tới được view con nằm
 * trong ScrollView, hoặc trả về 0 làm reset padding): đọc thẳng chiều cao thanh trạng
 * thái THẬT của thiết bị từ tài nguyên hệ thống ("status_bar_height") rồi set padding
 * ngay lập tức. Mỗi máy có giá trị riêng nên không bị lệch như số dp cố định.
 */
public final class EdgeToEdgeUtil {

    private EdgeToEdgeUtil() {}

    /** Bật edge-to-edge cho Activity. lightStatusBarIcons=true → icon thanh trạng thái màu tối (hợp nền sáng). */
    public static void enable(Activity activity, boolean lightStatusBarIcons) {
        WindowCompat.setDecorFitsSystemWindows(activity.getWindow(), false);
        WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(
                activity.getWindow(), activity.getWindow().getDecorView());
        controller.setAppearanceLightStatusBars(lightStatusBarIcons);
    }

    /**
     * Cách CHẮC CHẮN NHẤT: đệm thẳng vào khung nội dung gốc của Activity (android.R.id.content)
     * — view này LUÔN tồn tại nên không thể null, không phụ thuộc id trong layout hay merge resource.
     * Toàn bộ màn hình sẽ tụt xuống dưới thanh trạng thái và lên trên thanh điều hướng.
     * Trả về chiều cao thanh trạng thái (px) đã áp — để debug.
     */
    public static int padContentForSystemBars(Activity activity) {
        View content = activity.findViewById(android.R.id.content);
        if (content == null) return 0;
        int statusBar = systemDimen(content.getContext(), "status_bar_height", 24);
        int navBar = systemDimen(content.getContext(), "navigation_bar_height", 0);
        content.setPadding(content.getPaddingLeft(), content.getPaddingTop() + statusBar,
                content.getPaddingRight(), content.getPaddingBottom() + navBar);
        return statusBar;
    }

    /**
     * Như trên, nhưng tô màu vùng thanh trạng thái (và thanh điều hướng) bằng barColor để
     * LIỀN MẠCH với header — vd header trắng thì truyền 0xFFFFFFFF, nhìn như header phủ kín
     * lên đỉnh, đồng bộ với các trang khác. nội dung (ScrollView) vẫn che phần giữa bằng nền riêng.
     */
    public static int padContentForSystemBars(Activity activity, int barColor) {
        View content = activity.findViewById(android.R.id.content);
        if (content != null) content.setBackgroundColor(barColor);
        return padContentForSystemBars(activity);
    }

    /**
     * Cộng chiều cao thanh trạng thái thật vào paddingTop của header và chiều cao thanh
     * điều hướng vào paddingBottom của vùng cuộn. Áp NGAY, luôn chạy.
     * scrollRoot nên có clipToPadding=false để padding đáy không cắt nội dung cuối.
     */
    public static void applyHeaderAndScroll(final View scrollRoot, final View header) {
        if (header != null) {
            int statusBar = systemDimen(header.getContext(), "status_bar_height", 24);
            header.setPadding(header.getPaddingLeft(), header.getPaddingTop() + statusBar,
                    header.getPaddingRight(), header.getPaddingBottom());
        }
        if (scrollRoot != null) {
            int navBar = systemDimen(scrollRoot.getContext(), "navigation_bar_height", 0);
            scrollRoot.setPadding(scrollRoot.getPaddingLeft(), scrollRoot.getPaddingTop(),
                    scrollRoot.getPaddingRight(), scrollRoot.getPaddingBottom() + navBar);
        }
    }

    /** Đọc một dimen của hệ thống Android (vd status_bar_height). Trả về fallbackDp (px) nếu không có. */
    private static int systemDimen(Context ctx, String name, int fallbackDp) {
        Resources res = ctx.getResources();
        int id = res.getIdentifier(name, "dimen", "android");
        if (id > 0) return res.getDimensionPixelSize(id);
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, fallbackDp,
                res.getDisplayMetrics());
    }
}
