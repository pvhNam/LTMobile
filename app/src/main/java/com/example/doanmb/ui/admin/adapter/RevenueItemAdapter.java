package com.example.doanmb.ui.admin.adapter;

import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.doanmb.R;

import java.util.ArrayList;
import java.util.List;

public class RevenueItemAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int VIEW_HEADER = 0;
    private static final int VIEW_ENTRY  = 1;

    public static class Entry {
        public final String title, subtitle, amount, detail, badge;
        public final int type; // 0=sale, 1=rental, 2=posting

        public Entry(String title, String subtitle, String amount,
                     String detail, String badge, int type) {
            this.title    = title;
            this.subtitle = subtitle;
            this.amount   = amount;
            this.detail   = detail;
            this.badge    = badge;
            this.type     = type;
        }
    }

    private List<Object> items = new ArrayList<>();

    public void setItems(List<Object> items) {
        this.items = items;
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        return items.get(position) instanceof String ? VIEW_HEADER : VIEW_ENTRY;
    }

    @Override
    public int getItemCount() { return items.size(); }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inf = LayoutInflater.from(parent.getContext());
        if (viewType == VIEW_HEADER) {
            return new HeaderHolder(inf.inflate(R.layout.item_revenue_header, parent, false));
        }
        return new EntryHolder(inf.inflate(R.layout.item_revenue_entry, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof HeaderHolder) {
            ((HeaderHolder) holder).tvLabel.setText((String) items.get(position));
            return;
        }

        EntryHolder h = (EntryHolder) holder;
        Entry e = (Entry) items.get(position);

        h.tvTitle.setText(e.title);
        h.tvSubtitle.setText(e.subtitle);
        h.tvAmount.setText(e.amount);
        h.tvDetail.setText(e.detail);
        h.tvBadge.setText(e.badge);

        int badgeBg, badgeText;
        if (e.type == 0)      { badgeBg = 0xFFE3F2FD; badgeText = 0xFF0D47A1; } // xanh - mua
        else if (e.type == 1) { badgeBg = 0xFFFFF3E0; badgeText = 0xFFE65100; } // cam - thuê
        else                  { badgeBg = 0xFFFFCDD2; badgeText = 0xFFC62828; } // đỏ - đăng

        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(8f);
        bg.setColor(badgeBg);
        h.tvBadge.setBackground(bg);
        h.tvBadge.setTextColor(badgeText);
    }

    static class HeaderHolder extends RecyclerView.ViewHolder {
        TextView tvLabel;
        HeaderHolder(@NonNull View v) {
            super(v);
            tvLabel = v.findViewById(R.id.tv_revenue_header_label);
        }
    }

    static class EntryHolder extends RecyclerView.ViewHolder {
        TextView tvBadge, tvTitle, tvSubtitle, tvAmount, tvDetail;
        EntryHolder(@NonNull View v) {
            super(v);
            tvBadge    = v.findViewById(R.id.tv_rev_badge);
            tvTitle    = v.findViewById(R.id.tv_rev_title);
            tvSubtitle = v.findViewById(R.id.tv_rev_subtitle);
            tvAmount   = v.findViewById(R.id.tv_rev_amount);
            tvDetail   = v.findViewById(R.id.tv_rev_detail);
        }
    }
}
