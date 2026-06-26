package com.example.doanmb.ui.driver.adapter;

import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.doanmb.R;
import com.example.doanmb.data.model.Trip;
import com.google.android.material.button.MaterialButton;

import java.text.DecimalFormat;
import java.util.List;

/**
 * Danh sách chuyến cho tài xế (thẻ kiểu yêu cầu chuyến - thiết kế driver2).
 * Nút đổi theo trạng thái: Nhận chuyến + Bỏ qua / Hoàn thành chuyến / (ẩn, chỉ nhãn trạng thái).
 */
public class TripAdapter extends RecyclerView.Adapter<TripAdapter.VH> {

    public interface OnTripActionListener {
        /** Nút chính: Nhận chuyến (waiting) hoặc Hoàn thành (running). */
        void onPrimary(Trip trip);
        /** Nút phụ "Bỏ qua" (chỉ với chuyến đang chờ). */
        default void onSkip(Trip trip) {}
    }

    private final List<Trip> trips;
    private final OnTripActionListener listener;
    private static final DecimalFormat MONEY = new DecimalFormat("#,###");

    public TripAdapter(List<Trip> trips, OnTripActionListener listener) {
        this.trips = trips;
        this.listener = listener;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_trip, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        Trip t = trips.get(position);

        h.tvCustomer.setText(t.getCustomerName() != null && !t.getCustomerName().isEmpty()
                ? t.getCustomerName() : "Khách hàng");
        h.tvPickup.setText(safe(t.getPickup()));

        if (Trip.MODE_DISTANCE.equals(t.getRentMode())) {
            h.tvDest.setText(safe(t.getDestination()));
            if (t.getDistanceKm() > 0) {
                int mins = Math.max(5, (int) Math.round(t.getDistanceKm() * 2.5));
                h.tvMeta.setText(t.getDistanceKm() + " km · " + mins + " phút");
            } else {
                h.tvMeta.setText("Theo quãng đường");
            }
        } else {
            String unit = Trip.MODE_MONTH.equals(t.getRentMode()) ? "tháng" : "ngày";
            h.tvDest.setText("Thuê " + t.getDuration() + " " + unit);
            h.tvMeta.setText(t.rentModeLabel());
        }

        h.tvPrice.setText(MONEY.format(t.getPrice()) + "đ");

        String status = t.getStatus() != null ? t.getStatus() : Trip.STATUS_WAITING;
        switch (status) {
            case Trip.STATUS_WAITING:
                h.tvBadge.setVisibility(View.VISIBLE);
                h.tvStatus.setVisibility(View.GONE);
                h.btnPrimary.setVisibility(View.VISIBLE);
                h.btnPrimary.setText("Nhận chuyến");
                tint(h.btnPrimary, 0xFF2E6BF0);
                h.btnSecondary.setVisibility(View.VISIBLE);
                break;
            case Trip.STATUS_RUNNING:
                h.tvBadge.setVisibility(View.GONE);
                pill(h.tvStatus, "Đang chạy", 0xFFEAF1FE, 0xFF2E6BF0);
                h.btnPrimary.setVisibility(View.VISIBLE);
                h.btnPrimary.setText("Hoàn thành chuyến");
                tint(h.btnPrimary, 0xFF16A34A);
                h.btnSecondary.setVisibility(View.GONE);
                break;
            case Trip.STATUS_COMPLETED:
                h.tvBadge.setVisibility(View.GONE);
                pill(h.tvStatus, "Hoàn thành", 0xFFE6F6EC, 0xFF16A34A);
                h.btnPrimary.setVisibility(View.GONE);
                h.btnSecondary.setVisibility(View.GONE);
                break;
            default:
                h.tvBadge.setVisibility(View.GONE);
                pill(h.tvStatus, "Đã huỷ", 0xFFFDECEC, 0xFFEF4444);
                h.btnPrimary.setVisibility(View.GONE);
                h.btnSecondary.setVisibility(View.GONE);
        }

        h.btnPrimary.setOnClickListener(v -> { if (listener != null) listener.onPrimary(t); });
        h.btnSecondary.setOnClickListener(v -> { if (listener != null) listener.onSkip(t); });
    }

    private void tint(MaterialButton b, int color) {
        b.setBackgroundTintList(ColorStateList.valueOf(color));
    }

    private void pill(TextView tv, String text, int bg, int fg) {
        tv.setVisibility(View.VISIBLE);
        tv.setText(text);
        tv.setBackgroundTintList(ColorStateList.valueOf(bg));
        tv.setTextColor(fg);
    }

    private String safe(String s) { return s != null && !s.isEmpty() ? s : "--"; }

    @Override
    public int getItemCount() { return trips.size(); }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvCustomer, tvRating, tvBadge, tvStatus, tvPickup, tvDest, tvMeta, tvPrice;
        MaterialButton btnPrimary, btnSecondary;

        VH(@NonNull View v) {
            super(v);
            tvCustomer = v.findViewById(R.id.tv_trip_customer);
            tvRating = v.findViewById(R.id.tv_trip_rating);
            tvBadge = v.findViewById(R.id.tv_trip_badge);
            tvStatus = v.findViewById(R.id.tv_trip_status);
            tvPickup = v.findViewById(R.id.tv_trip_pickup);
            tvDest = v.findViewById(R.id.tv_trip_dest);
            tvMeta = v.findViewById(R.id.tv_trip_meta);
            tvPrice = v.findViewById(R.id.tv_trip_price);
            btnPrimary = v.findViewById(R.id.btn_trip_primary);
            btnSecondary = v.findViewById(R.id.btn_trip_secondary);
        }
    }
}
