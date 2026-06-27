package com.example.doanmb.ui.admin.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.doanmb.R;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Danh sách các lượt nạp tiền (VNPay) cho admin xem: tên người nạp, nguồn, số tiền, thời gian. */
public class TopupHistoryAdapter extends RecyclerView.Adapter<TopupHistoryAdapter.ViewHolder> {

    private final List<Map<String, Object>> items;
    private static final Map<String, String> NAME_CACHE = new HashMap<>();
    private static final SimpleDateFormat DATE_FMT =
            new SimpleDateFormat("HH:mm dd/MM/yyyy", Locale.getDefault());

    public TopupHistoryAdapter(List<Map<String, Object>> items) {
        this.items = items;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_admin_topup, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder h, int position) {
        Map<String, Object> tx = items.get(position);

        long amount = toLong(tx.get("amount"));
        h.tvAmount.setText("+" + money(amount) + " đ");

        String source = str(tx.get("source"));
        String note = str(tx.get("note"));
        boolean vnpay = "vnpay".equals(source) || (source.isEmpty() && note.contains("VNPay"));
        h.tvSource.setText(vnpay ? "VNPay" : (source.isEmpty() ? note : source));

        Object created = tx.get("createdAt");
        if (created instanceof Timestamp) {
            h.tvDate.setText("📅 " + DATE_FMT.format(((Timestamp) created).toDate()));
        } else {
            h.tvDate.setText("📅 --");
        }

        bindName(h.tvName, str(tx.get("toUserId")));
    }

    /** Hiển thị tên người nạp theo toUserId, có cache để không tra Firestore lặp lại khi cuộn. */
    private void bindName(TextView tv, String userId) {
        if (userId.isEmpty()) { tv.setTag(null); tv.setText("Ẩn danh"); return; }
        String cached = NAME_CACHE.get(userId);
        if (cached != null) { tv.setTag(null); tv.setText(cached); return; }

        tv.setTag(userId);
        tv.setText("Đang tải…");
        FirebaseFirestore.getInstance().collection("users").document(userId).get()
                .addOnSuccessListener(u -> {
                    String name = u.exists() ? u.getString("name") : null;
                    if (name == null || name.isEmpty()) name = "Ẩn danh";
                    NAME_CACHE.put(userId, name);
                    if (userId.equals(tv.getTag())) tv.setText(name);
                })
                .addOnFailureListener(e -> {
                    if (userId.equals(tv.getTag())) tv.setText("Ẩn danh");
                });
    }

    @Override
    public int getItemCount() { return items.size(); }

    private static long toLong(Object o) {
        if (o instanceof Number) return ((Number) o).longValue();
        return 0L;
    }

    private static String str(Object o) { return o != null ? o.toString() : ""; }

    private static String money(long v) {
        return String.format(Locale.US, "%,d", v).replace(',', '.');
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvName, tvSource, tvDate, tvAmount;
        ViewHolder(@NonNull View v) {
            super(v);
            tvName   = v.findViewById(R.id.tv_topup_name);
            tvSource = v.findViewById(R.id.tv_topup_source);
            tvDate   = v.findViewById(R.id.tv_topup_date);
            tvAmount = v.findViewById(R.id.tv_topup_amount);
        }
    }
}
