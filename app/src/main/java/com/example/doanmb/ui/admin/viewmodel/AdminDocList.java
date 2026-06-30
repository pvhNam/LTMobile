package com.example.doanmb.ui.admin.viewmodel;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Kết quả tải một danh sách document admin: dữ liệu thô ({@code data}) đi kèm
 * id song song ({@code ids}) để truyền thẳng vào các adapter admin vốn nhận
 * (List&lt;Map&gt;, List&lt;String&gt;). Gói chung trong một LiveData để View chỉ phải
 * observe 1 lần thay vì hai list rời rạc dễ lệch nhau.
 */
public class AdminDocList {

    public final List<Map<String, Object>> data;
    public final List<String> ids;

    public AdminDocList(List<Map<String, Object>> data, List<String> ids) {
        this.data = data;
        this.ids = ids;
    }

    public static AdminDocList empty() {
        return new AdminDocList(new ArrayList<>(), new ArrayList<>());
    }

    public int size() { return data.size(); }

    public boolean isEmpty() { return data.isEmpty(); }
}
