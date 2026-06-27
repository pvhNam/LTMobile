package com.example.doanmb.ui.admin.view;

import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.doanmb.R;
import com.example.doanmb.data.repository.WalletRepository;
import com.example.doanmb.ui.admin.adapter.TopupHistoryAdapter;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Màn admin: lịch sử các lượt người dùng nạp tiền qua VNPay vào ví. */
public class AdminTopupHistoryActivity extends AppCompatActivity {

    private final List<Map<String, Object>> items = new ArrayList<>();
    private TopupHistoryAdapter adapter;
    private TextView tvTotal, tvCount, tvEmpty;
    private ProgressBar progress;
    private RecyclerView rv;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_topup_history);
        if (getSupportActionBar() != null) getSupportActionBar().hide();

        tvTotal  = findViewById(R.id.tv_topup_total);
        tvCount  = findViewById(R.id.tv_topup_count);
        tvEmpty  = findViewById(R.id.tv_topup_empty);
        progress = findViewById(R.id.progress_topup);
        rv       = findViewById(R.id.rv_topup_history);

        View btnBack = findViewById(R.id.btn_topup_back);
        if (btnBack != null) btnBack.setOnClickListener(v -> finish());

        adapter = new TopupHistoryAdapter(items);
        rv.setLayoutManager(new LinearLayoutManager(this));
        rv.setAdapter(adapter);

        load();
    }

    private void load() {
        progress.setVisibility(View.VISIBLE);
        rv.setVisibility(View.GONE);
        tvEmpty.setVisibility(View.GONE);

        FirebaseFirestore.getInstance().collection("transactions")
                .whereEqualTo("type", WalletRepository.TYPE_TOPUP).get()
                .addOnSuccessListener(snap -> {
                    items.clear();
                    long total = 0;
                    for (QueryDocumentSnapshot doc : snap) {
                        Map<String, Object> tx = doc.getData();
                        if (!isVnpay(tx)) continue;   // chỉ lấy lượt nạp VNPay
                        items.add(tx);
                        total += toLong(tx.get("amount"));
                    }
                    // Mới nhất lên đầu (sắp xếp client-side, khỏi cần composite index)
                    Collections.sort(items, (a, b) ->
                            Long.compare(millis(b.get("createdAt")), millis(a.get("createdAt"))));

                    adapter.notifyDataSetChanged();
                    tvTotal.setText(money(total) + " đ");
                    tvCount.setText(items.size() + " lượt nạp");

                    progress.setVisibility(View.GONE);
                    boolean empty = items.isEmpty();
                    tvEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
                    rv.setVisibility(empty ? View.GONE : View.VISIBLE);
                })
                .addOnFailureListener(e -> {
                    progress.setVisibility(View.GONE);
                    Toast.makeText(this, "Lỗi tải lịch sử: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    /** Lượt nạp VNPay: ưu tiên field source; dữ liệu cũ chưa có source thì dò theo note. */
    private boolean isVnpay(Map<String, Object> tx) {
        String source = str(tx.get("source"));
        if (!source.isEmpty()) return WalletRepository.SOURCE_VNPAY.equals(source);
        return str(tx.get("note")).contains("VNPay");
    }

    private static long millis(Object o) {
        return o instanceof Timestamp ? ((Timestamp) o).toDate().getTime() : 0L;
    }

    private static long toLong(Object o) {
        return o instanceof Number ? ((Number) o).longValue() : 0L;
    }

    private static String str(Object o) { return o != null ? o.toString() : ""; }

    private static String money(long v) {
        return String.format(Locale.US, "%,d", v).replace(',', '.');
    }
}
