package com.example.doanmb.ui.profile.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.doanmb.R;
import com.example.doanmb.data.model.Transaction;
import com.google.firebase.Timestamp;

import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;

/** Hiển thị lịch sử giao dịch ví của người dùng hiện tại. */
public class TransactionAdapter extends RecyclerView.Adapter<TransactionAdapter.TxViewHolder> {

    private static final NumberFormat MONEY = NumberFormat.getInstance(new Locale("vi", "VN"));
    private static final int COLOR_IN  = 0xFF2E7D32; // xanh: tiền vào
    private static final int COLOR_OUT = 0xFFE53935; // đỏ: tiền ra

    private final List<Transaction> items;
    private final String uid;

    public TransactionAdapter(List<Transaction> items, String uid) {
        this.items = items;
        this.uid = uid;
    }

    @NonNull
    @Override
    public TxViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_transaction, parent, false);
        return new TxViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull TxViewHolder h, int position) {
        Transaction t = items.get(position);
        boolean incoming = t.isIncoming(uid);

        h.tvIcon.setText(iconFor(t.getType()));
        h.tvTitle.setText(titleFor(t));

        Timestamp ts = t.getCreatedAt();
        if (ts != null) {
            String d = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(ts.toDate());
            h.tvDate.setText("📅 " + d);
        } else {
            h.tvDate.setText("📅 --");
        }

        h.tvAmount.setText((incoming ? "+" : "-") + MONEY.format(t.getAmount()) + " đ");
        h.tvAmount.setTextColor(incoming ? COLOR_IN : COLOR_OUT);
    }

    @Override
    public int getItemCount() { return items.size(); }

    private static String iconFor(String type) {
        if (type == null) return "💰";
        switch (type) {
            case "topup":    return "⬆️";
            case "refund":   return "↩️";
            case "payout":   return "🏦";
            case "hold":     return "🔒";
            case "withdraw": return "🏧";
            default:          return "💰";
        }
    }

    private static String titleFor(Transaction t) {
        String note = t.getNote();
        if (note != null && !note.isEmpty()) return note;
        String type = t.getType();
        if (type == null) return "Giao dịch";
        switch (type) {
            case "topup":    return "Nạp tiền qua VNPay";
            case "refund":   return "Hoàn cọc";
            case "payout":   return "Nhận thanh toán đơn";
            case "hold":     return "Giữ cọc đơn";
            case "withdraw": return "Rút tiền về tài khoản";
            default:          return "Giao dịch";
        }
    }

    static class TxViewHolder extends RecyclerView.ViewHolder {
        TextView tvIcon, tvTitle, tvDate, tvAmount;
        TxViewHolder(@NonNull View itemView) {
            super(itemView);
            tvIcon   = itemView.findViewById(R.id.tv_tx_icon);
            tvTitle  = itemView.findViewById(R.id.tv_tx_title);
            tvDate   = itemView.findViewById(R.id.tv_tx_date);
            tvAmount = itemView.findViewById(R.id.tv_tx_amount);
        }
    }
}
