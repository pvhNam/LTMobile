package com.example.doanmb.ui.driver.view;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import com.google.android.material.switchmaterial.SwitchMaterial;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.example.doanmb.R;
import com.example.doanmb.core.helper.ChatNotificationHelper;
import com.example.doanmb.core.util.ImageLoader;
import com.example.doanmb.ui.driver.viewmodel.DriverHomeViewModel;
import de.hdodenhof.circleimageview.CircleImageView;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

public class DriverHomeFragment extends Fragment {

    private static final String COL_ORDERS  = "orders";

    private SwitchMaterial switchAvailable;
    private TextView  tvAvailableLabel;
    private TextView  tvAvailableSub;
    private View      dotAvailable;

    private TextView  tvLatestCarName, tvLatestRenter, tvLatestDate, tvLatestNote;
    private View      layoutNoteBox;
    private Button    btnAcceptLatest, btnRejectLatest;

    // Tổng quan hôm nay
    private TextView  tvOverviewRevenue, tvOverviewTrips, tvOverviewRating, tvOverviewOnline;

    // db chỉ dùng để chuẩn bị dữ liệu gửi FCM cho khách (cần Context) — giống ManageFragment.
    private FirebaseFirestore db;
    private String uid;
    private DriverHomeViewModel viewModel;
    private boolean suppressSwitch = false;

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        View v = inflater.inflate(R.layout.fragment_driver_home, container, false);

        db  = FirebaseFirestore.getInstance();
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        uid = user != null ? user.getUid() : "";

        switchAvailable  = v.findViewById(R.id.switch_available);
        tvAvailableLabel = v.findViewById(R.id.tv_available_label);
        tvAvailableSub   = v.findViewById(R.id.tv_available_sub);
        dotAvailable     = v.findViewById(R.id.dot_available);
        tvLatestCarName  = v.findViewById(R.id.tv_latest_car_name);
        tvLatestRenter   = v.findViewById(R.id.tv_latest_renter);
        tvLatestDate     = v.findViewById(R.id.tv_latest_date);
        tvLatestNote     = v.findViewById(R.id.tv_latest_note);
        layoutNoteBox    = v.findViewById(R.id.layout_note_box);
        btnAcceptLatest  = v.findViewById(R.id.btn_accept_latest);
        btnRejectLatest  = v.findViewById(R.id.btn_reject_latest);

        tvOverviewRevenue = v.findViewById(R.id.tv_overview_revenue);
        tvOverviewTrips   = v.findViewById(R.id.tv_overview_trips);
        tvOverviewRating  = v.findViewById(R.id.tv_overview_rating);
        tvOverviewOnline  = v.findViewById(R.id.tv_overview_online);

        viewModel = new ViewModelProvider(this).get(DriverHomeViewModel.class);

        if (btnAcceptLatest != null) btnAcceptLatest.setOnClickListener(x -> viewModel.acceptLatest());
        if (btnRejectLatest != null) btnRejectLatest.setOnClickListener(x -> viewModel.rejectLatest());

        View btnNearestPrev = v.findViewById(R.id.btn_nearest_prev);
        View btnNearestNext = v.findViewById(R.id.btn_nearest_next);
        if (btnNearestPrev != null) btnNearestPrev.setOnClickListener(x -> viewModel.showPrev());
        if (btnNearestNext != null) btnNearestNext.setOnClickListener(x -> viewModel.showNext());

        if (switchAvailable != null) {
            switchAvailable.setOnCheckedChangeListener((btn, isChecked) -> {
                if (suppressSwitch) return;
                viewModel.setAvailable(isChecked);
            });
        }

        observeViewModel();
        viewModel.loadDriverInfo();
        return v;
    }

    @Override public void onResume() { super.onResume(); viewModel.onResume(); }
    @Override public void onPause()  { super.onPause();  viewModel.onPause(); }

    private void observeViewModel() {
        viewModel.getAvatarUrl().observe(getViewLifecycleOwner(), avatarUrl -> {
            CircleImageView av = getView() != null ? getView().findViewById(R.id.iv_home_avatar) : null;
            if (av != null && avatarUrl != null && !avatarUrl.isEmpty())
                ImageLoader.loadAvatar(av, avatarUrl, R.drawable.ic_driver_avatar_placeholder);
        });

        viewModel.getAvailable().observe(getViewLifecycleOwner(), avail -> {
            suppressSwitch = true;
            applyAvailableUi(Boolean.TRUE.equals(avail));
            suppressSwitch = false;
        });

        viewModel.getStats().observe(getViewLifecycleOwner(), s -> {
            if (s == null) return;
            if (tvOverviewRevenue != null) tvOverviewRevenue.setText(s.revenue);
            if (tvOverviewTrips   != null) tvOverviewTrips.setText(s.trips);
            if (tvOverviewRating  != null) tvOverviewRating.setText(s.rating);
            if (tvOverviewOnline  != null) tvOverviewOnline.setText(s.online);
        });

        viewModel.getCurrentPending().observe(getViewLifecycleOwner(), doc -> {
            if (doc == null) showNoTripUi();
            else showLatestOrder(doc);
        });

        viewModel.getNavInfo().observe(getViewLifecycleOwner(), this::updateNearestNav);

        viewModel.getMessage().observe(getViewLifecycleOwner(), msg -> {
            if (msg != null && !msg.isEmpty())
                Toast.makeText(getContext(), msg, Toast.LENGTH_SHORT).show();
        });

        viewModel.getNotifyCustomer().observe(getViewLifecycleOwner(), ev -> {
            if (ev == null) return;
            notifyCustomer(ev.orderId, ev.type);
        });
    }

    private void applyAvailableUi(boolean available) {
        if (switchAvailable  != null) switchAvailable.setChecked(available);
        if (tvAvailableLabel != null)
            tvAvailableLabel.setText(available ? "Đang nhận chuyến" : "Tạm dừng nhận chuyến");
        if (tvAvailableSub != null)
            tvAvailableSub.setText(available
                    ? "Hệ thống sẽ gửi chuyến mới cho bạn"
                    : "Bật để bắt đầu nhận chuyến");
        if (dotAvailable != null) {
            int color = androidx.core.content.ContextCompat.getColor(
                    requireContext(),
                    available ? R.color.driver_green : R.color.driver_text_secondary);
            dotAvailable.setBackgroundTintList(android.content.res.ColorStateList.valueOf(color));
        }
    }

    private void showLatestOrder(DocumentSnapshot d) {
        View cardNearest = getView() != null ? getView().findViewById(R.id.card_nearest) : null;
        View tvNoNearest = getView() != null ? getView().findViewById(R.id.tv_no_nearest) : null;
        if (cardNearest != null) cardNearest.setVisibility(View.VISIBLE);
        if (tvNoNearest != null) tvNoNearest.setVisibility(View.GONE);

        java.text.SimpleDateFormat fmt =
                new java.text.SimpleDateFormat("dd/MM HH:mm", java.util.Locale.getDefault());

        String carName = d.getString("carName");
        String price   = d.getString("carPrice");

        setText(tvLatestCarName, carName);
        TextView tvNCartype = getView() != null ? getView().findViewById(R.id.tv_n_cartype) : null;
        String type = d.getString("type");
        if (tvNCartype != null)
            tvNCartype.setText(type != null && !type.isEmpty() ? type : "Có tài xế");

        TextView tvNPrice = getView() != null ? getView().findViewById(R.id.tv_n_price) : null;
        if (tvNPrice != null) setText(tvNPrice, price);

        String rn = d.getString("renterName");
        String rp = d.getString("renterPhone");
        setText(tvLatestRenter, (rn != null ? rn : "") +
                (rp != null && !rp.isEmpty() ? "  |  " + rp : ""));

        String note = d.getString("note");
        boolean hasNote = note != null && !note.isEmpty();
        if (layoutNoteBox != null) layoutNoteBox.setVisibility(hasNote ? View.VISIBLE : View.GONE);
        if (hasNote) setText(tvLatestNote, note);

        Timestamp ts = d.getTimestamp("createdAt");
        if (tvLatestDate != null && ts != null)
            tvLatestDate.setText("📅 " + fmt.format(ts.toDate()));
    }

    private void showNoTripUi() {
        View cardNearest = getView() != null ? getView().findViewById(R.id.card_nearest) : null;
        View tvNoNearest = getView() != null ? getView().findViewById(R.id.tv_no_nearest) : null;
        View nav         = getView() != null ? getView().findViewById(R.id.layout_nearest_nav) : null;
        if (cardNearest != null) cardNearest.setVisibility(View.GONE);
        if (tvNoNearest != null) tvNoNearest.setVisibility(View.VISIBLE);
        if (nav != null) nav.setVisibility(View.GONE);
    }

    private void updateNearestNav(DriverHomeViewModel.NavInfo info) {
        if (getView() == null || info == null) return;
        View nav      = getView().findViewById(R.id.layout_nearest_nav);
        TextView count= getView().findViewById(R.id.tv_nearest_count);
        View prev     = getView().findViewById(R.id.btn_nearest_prev);
        View next     = getView().findViewById(R.id.btn_nearest_next);
        int n = info.total;
        if (nav != null)   nav.setVisibility(n > 1 ? View.VISIBLE : View.GONE);
        if (count != null) count.setText((info.index + 1) + "/" + n);
        if (prev != null) {
            boolean can = info.index > 0;
            prev.setEnabled(can);
            prev.setAlpha(can ? 1f : 0.35f);
        }
        if (next != null) {
            boolean can = info.index < n - 1;
            next.setEnabled(can);
            next.setAlpha(can ? 1f : 0.35f);
        }
    }

    // ── Gửi FCM cho khách (cần Context) — đọc lại đơn + tên tài xế rồi gửi ──────
    private void notifyCustomer(String orderId, String type) {
        db.collection(COL_ORDERS).document(orderId).get()
                .addOnSuccessListener(snap -> {
                    if (snap == null || !snap.exists() || !isAdded()) return;
                    String buyerId = snap.getString("buyerId");
                    String carName = snap.getString("carName");
                    String carId   = snap.getString("carId");
                    if (buyerId == null || buyerId.isEmpty()) return;

                    db.collection("users").document(uid).get()
                            .addOnSuccessListener(userSnap -> {
                                if (!isAdded()) return;
                                String myName = userSnap.getString("name");
                                if (myName == null || myName.isEmpty()) myName = "Tài xế";
                                ChatNotificationHelper.sendOrderNotification(
                                        requireContext(), buyerId, uid, myName,
                                        carName != null ? carName : "",
                                        carId   != null ? carId   : "",
                                        type, orderId);
                            });
                });
    }

    private static void setText(TextView tv, String text) {
        if (tv != null) tv.setText(text != null ? text : "");
    }
}
