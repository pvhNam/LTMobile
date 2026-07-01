package com.example.doanmb.ui.driver.view;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.doanmb.R;
import com.example.doanmb.ui.driver.adapter.TripAdapter;
import com.example.doanmb.ui.driver.viewmodel.DriverTripsViewModel;
import com.example.doanmb.ui.driver.viewmodel.DriverTripsViewModel.OrderItem;
import com.example.doanmb.data.model.Trip;
import com.google.android.material.tabs.TabLayout;

import java.util.ArrayList;
import java.util.List;

import de.hdodenhof.circleimageview.CircleImageView;

public class DriverTripsFragment extends Fragment implements TripAdapter.OnTripActionListener {

    private class OrderDriverAdapter extends RecyclerView.Adapter<OrderDriverAdapter.VH> {
        private final List<OrderItem> list;
        OrderDriverAdapter(List<OrderItem> list) { this.list = list; }

        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_driver_order, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int pos) {
            OrderItem o = list.get(pos);
            java.text.SimpleDateFormat fmt =
                    new java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.getDefault());

            setText(h.tvCarName,  o.carName);
            setText(h.tvPrice,    o.carPrice);
            setText(h.tvStatus,   labelFor(o.status));
            styleStatusPill(h.tvStatus, o.status);
            setText(h.tvRenter,
                    (o.renterName  != null ? o.renterName  : "")
                            + (o.renterPhone != null && !o.renterPhone.isEmpty() ? "  |  " + o.renterPhone : ""));

            if (h.tvNote != null) {
                boolean hasNote = o.note != null && !o.note.isEmpty();
                h.tvNote.setVisibility(hasNote ? View.VISIBLE : View.GONE);
                if (hasNote) h.tvNote.setText(o.note);
            }
            if (h.tvDate != null && o.createdAt != null)
                h.tvDate.setText("📅 " + fmt.format(o.createdAt.toDate()));

            boolean isAccepted   = "accepted".equals(o.status);
            boolean isInProgress = "in_progress".equals(o.status);

            setVisible(h.btnStart,    isAccepted);
            setVisible(h.btnComplete, isInProgress);

            if (h.btnStart    != null) h.btnStart.setOnClickListener(v    -> viewModel.startOrder(o));
            if (h.btnComplete != null) h.btnComplete.setOnClickListener(v -> viewModel.completeOrder(o));
        }

        @Override public int getItemCount() { return list.size(); }

        class VH extends RecyclerView.ViewHolder {
            TextView tvCarName, tvRenter, tvPrice, tvNote, tvDate, tvStatus;
            Button   btnStart, btnComplete;
            VH(@NonNull View v) {
                super(v);
                tvCarName   = v.findViewById(R.id.tv_do_car_name);
                tvRenter    = v.findViewById(R.id.tv_do_renter);
                tvPrice     = v.findViewById(R.id.tv_do_price);
                tvNote      = v.findViewById(R.id.tv_do_note);
                tvDate      = v.findViewById(R.id.tv_do_date);
                tvStatus    = v.findViewById(R.id.tv_do_status);
                btnStart    = v.findViewById(R.id.btn_do_start);
                btnComplete = v.findViewById(R.id.btn_do_complete);
            }
        }
    }

    private TextView     tvName, tvSectionTitle, tvCountdown, tvEmpty;
    private CircleImageView ivAvatar;
    private TabLayout    tabs;
    private RecyclerView rv;
    private OrderDriverAdapter orderAdapter;

    private final List<OrderItem> shown = new ArrayList<>();
    private DriverTripsViewModel viewModel;

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_driver_trips, container, false);

        tvName         = v.findViewById(R.id.tv_dh_name);
        ivAvatar       = v.findViewById(R.id.iv_dh_avatar);
        tvSectionTitle = v.findViewById(R.id.tv_section_title);
        tvCountdown    = v.findViewById(R.id.tv_countdown);
        tvEmpty        = v.findViewById(R.id.tv_empty);
        tabs           = v.findViewById(R.id.tab_trips);
        rv             = v.findViewById(R.id.rv_trips);

        orderAdapter = new OrderDriverAdapter(shown);
        rv.setLayoutManager(new LinearLayoutManager(getContext()));
        rv.setAdapter(orderAdapter);

        tabs.addTab(tabs.newTab().setText("Chuyến mới"));
        tabs.addTab(tabs.newTab().setText("Đang chạy"));
        tabs.addTab(tabs.newTab().setText("Lịch sử"));
        tabs.addTab(tabs.newTab().setText("Đặt trước"));
        tabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override public void onTabSelected(TabLayout.Tab tab) {
                applyTabChrome(tab.getPosition());
                viewModel.setTab(tab.getPosition());
            }
            @Override public void onTabUnselected(TabLayout.Tab tab) {}
            @Override public void onTabReselected(TabLayout.Tab tab) {}
        });

        safeClick(v, R.id.row_running,   () -> selectTab(1));
        safeClick(v, R.id.row_history,   () -> selectTab(2));
        safeClick(v, R.id.row_scheduled, () -> selectTab(3));

        viewModel = new ViewModelProvider(this).get(DriverTripsViewModel.class);
        observeViewModel();
        applyTabChrome(0);

        return v;
    }

    @Override public void onResume() {
        super.onResume();
        viewModel.loadAll();
    }

    private void observeViewModel() {
        viewModel.getDriverName().observe(getViewLifecycleOwner(), name -> {
            if (tvName != null) tvName.setText(name != null && !name.isEmpty() ? name : "Tài xế");
        });
        viewModel.getAvatarUrl().observe(getViewLifecycleOwner(), avatar -> {
            if (avatar != null && !avatar.isEmpty() && ivAvatar != null && isAdded())
                Glide.with(this).load(avatar).into(ivAvatar);
        });
        viewModel.getShown().observe(getViewLifecycleOwner(), list -> {
            shown.clear();
            if (list != null) shown.addAll(list);
            orderAdapter.notifyDataSetChanged();
            if (tvEmpty != null)
                tvEmpty.setVisibility(shown.isEmpty() ? View.VISIBLE : View.GONE);
        });
        viewModel.getMessage().observe(getViewLifecycleOwner(), msg -> {
            if (msg != null && !msg.isEmpty())
                Toast.makeText(getContext(), msg, Toast.LENGTH_SHORT).show();
        });
        viewModel.getSelectTabEvent().observe(getViewLifecycleOwner(), this::selectTab);
    }

    /** Đặt tiêu đề khu vực + text rỗng theo tab (thuần UI). */
    private void applyTabChrome(int tab) {
        if (tvCountdown != null) tvCountdown.setVisibility(View.GONE);
        switch (tab) {
            case 0:
                if (tvSectionTitle != null) tvSectionTitle.setText("Chuyến mới");
                if (tvEmpty        != null) tvEmpty.setText("Chưa có chuyến đã nhận");
                break;
            case 1:
                if (tvSectionTitle != null) tvSectionTitle.setText("Đang chạy");
                if (tvEmpty        != null) tvEmpty.setText("Không có chuyến đang chạy");
                break;
            case 2:
                if (tvSectionTitle != null) tvSectionTitle.setText("Lịch sử chuyến");
                if (tvEmpty        != null) tvEmpty.setText("Chưa có lịch sử chuyến");
                break;
            default:
                if (tvSectionTitle != null) tvSectionTitle.setText("Chuyến đặt trước");
                if (tvEmpty        != null) tvEmpty.setText("Chưa có chuyến đặt trước");
        }
    }

    @Override public void onPrimary(Trip trip) { /* xử lý ở Driver Home */ }
    @Override public void onSkip(Trip trip)    { /* xử lý ở Driver Home */ }

    private void selectTab(int i) {
        if (tabs == null) return;
        TabLayout.Tab tab = tabs.getTabAt(i);
        if (tab != null) tab.select();
    }

    private static String labelFor(String s) {
        if (s == null) return "";
        switch (s) {
            case "accepted":    return "Chờ bắt đầu";
            case "in_progress": return "Đang chạy";
            case "completed":   return "Hoàn thành";
            case "rejected":    return "Đã từ chối";
            case "cancelled":   return "Đã hủy";
            default:            return s;
        }
    }

    /** Tô màu pill trạng thái theo từng trạng thái đơn. */
    private static void styleStatusPill(TextView tv, String status) {
        if (tv == null) return;
        int bgRes, fgRes;
        switch (status == null ? "" : status) {
            case "in_progress":
            case "completed":
                bgRes = R.color.driver_green_bg;   fgRes = R.color.driver_green;   break;
            case "rejected":
            case "cancelled":
                bgRes = R.color.driver_red_bg;     fgRes = R.color.driver_red;     break;
            case "accepted":
            default:
                bgRes = R.color.driver_surface_blue; fgRes = R.color.driver_primary;
        }
        android.content.Context c = tv.getContext();
        tv.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                androidx.core.content.ContextCompat.getColor(c, bgRes)));
        tv.setTextColor(androidx.core.content.ContextCompat.getColor(c, fgRes));
    }

    private static void setText(TextView tv, String text) {
        if (tv != null) tv.setText(text != null ? text : "");
    }

    private static void setVisible(View v, boolean show) {
        if (v != null) v.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    private static void safeClick(View root, int id, Runnable action) {
        View v = root.findViewById(id);
        if (v != null) v.setOnClickListener(x -> action.run());
    }
}
