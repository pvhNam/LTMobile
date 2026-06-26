package com.example.doanmb.ui.driver.view;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.doanmb.R;
import com.example.doanmb.ui.driver.adapter.TripAdapter;
import com.example.doanmb.data.model.Trip;
import com.google.android.material.tabs.TabLayout;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.List;

import de.hdodenhof.circleimageview.CircleImageView;

/**
 * Tab "Chuyến" (thiết kế driver2): header chào mừng + tab trạng thái
 * (Chuyến mới / Đang chạy / Lịch sử / Đặt trước) + danh sách + lối tắt.
 */
public class DriverTripsFragment extends Fragment implements TripAdapter.OnTripActionListener {

    private TextView tvName, tvSectionTitle, tvCountdown, tvEmpty;
    private CircleImageView ivAvatar;
    private TabLayout tabs;
    private RecyclerView rv;
    private TripAdapter adapter;

    private final List<Trip> shown = new ArrayList<>();
    private final List<Trip> waiting = new ArrayList<>();
    private final List<Trip> running = new ArrayList<>();
    private final List<Trip> history = new ArrayList<>();

    private FirebaseFirestore db;
    private String uid, driverName, driverCarType;
    private int currentTab = 0;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_driver_trips, container, false);
        db = FirebaseFirestore.getInstance();
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        uid = user != null ? user.getUid() : "";

        tvName = v.findViewById(R.id.tv_dh_name);
        ivAvatar = v.findViewById(R.id.iv_dh_avatar);
        tvSectionTitle = v.findViewById(R.id.tv_section_title);
        tvCountdown = v.findViewById(R.id.tv_countdown);
        tvEmpty = v.findViewById(R.id.tv_empty);
        tabs = v.findViewById(R.id.tab_trips);
        rv = v.findViewById(R.id.rv_trips);

        adapter = new TripAdapter(shown, this);
        rv.setLayoutManager(new LinearLayoutManager(getContext()));
        rv.setAdapter(adapter);

        tabs.addTab(tabs.newTab().setText("Chuyến mới"));
        tabs.addTab(tabs.newTab().setText("Đang chạy"));
        tabs.addTab(tabs.newTab().setText("Lịch sử"));
        tabs.addTab(tabs.newTab().setText("Đặt trước"));
        tabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override public void onTabSelected(TabLayout.Tab tab) { currentTab = tab.getPosition(); renderTab(); }
            @Override public void onTabUnselected(TabLayout.Tab tab) {}
            @Override public void onTabReselected(TabLayout.Tab tab) {}
        });

        v.findViewById(R.id.row_running).setOnClickListener(x -> selectTab(1));
        v.findViewById(R.id.row_history).setOnClickListener(x -> selectTab(2));
        v.findViewById(R.id.row_scheduled).setOnClickListener(x -> selectTab(3));

        return v;
    }

    private void selectTab(int i) {
        TabLayout.Tab tab = tabs.getTabAt(i);
        if (tab != null) tab.select();
    }

    @Override
    public void onResume() {
        super.onResume();
        loadAll();
    }

    private void loadAll() {
        if (uid.isEmpty()) return;
        db.collection("users").document(uid).get().addOnSuccessListener(doc -> {
            if (!isAdded()) return;
            driverName = doc.getString("name");
            driverCarType = doc.getString("driverCarType");
            tvName.setText(driverName != null ? driverName : "Tài xế");
            String avatar = doc.getString("avatarUrl");
            if (avatar != null && !avatar.isEmpty()) Glide.with(this).load(avatar).into(ivAvatar);
            loadTrips();
        });
    }

    private void loadTrips() {
        // Đơn đang chờ (lọc theo loại xe)
        db.collection("trips").whereEqualTo("status", Trip.STATUS_WAITING).get()
                .addOnSuccessListener(snap -> {
                    if (!isAdded()) return;
                    waiting.clear();
                    for (QueryDocumentSnapshot d : snap) {
                        Trip t = d.toObject(Trip.class);
                        t.setId(d.getId());
                        if (driverCarType == null || driverCarType.isEmpty()
                                || driverCarType.equals(t.getCarType())) {
                            waiting.add(t);
                        }
                    }
                    if (currentTab == 0) renderTab();
                });

        // Chuyến của tôi (đang chạy + lịch sử)
        db.collection("trips").whereEqualTo("driverId", uid).get()
                .addOnSuccessListener(snap -> {
                    if (!isAdded()) return;
                    running.clear();
                    history.clear();
                    for (QueryDocumentSnapshot d : snap) {
                        Trip t = d.toObject(Trip.class);
                        t.setId(d.getId());
                        if (Trip.STATUS_RUNNING.equals(t.getStatus())) running.add(t);
                        else if (Trip.STATUS_COMPLETED.equals(t.getStatus())
                                || Trip.STATUS_CANCELLED.equals(t.getStatus())) history.add(t);
                    }
                    if (currentTab != 0) renderTab();
                });
    }

    private void renderTab() {
        shown.clear();
        switch (currentTab) {
            case 0:
                shown.addAll(waiting);
                tvSectionTitle.setText("Yêu cầu mới");
                tvCountdown.setVisibility(View.VISIBLE);
                tvEmpty.setText("Chưa có yêu cầu mới");
                break;
            case 1:
                shown.addAll(running);
                tvSectionTitle.setText("Đang chạy");
                tvCountdown.setVisibility(View.GONE);
                tvEmpty.setText("Không có chuyến đang chạy");
                break;
            case 2:
                shown.addAll(history);
                tvSectionTitle.setText("Lịch sử chuyến");
                tvCountdown.setVisibility(View.GONE);
                tvEmpty.setText("Chưa có chuyến hoàn thành");
                break;
            default:
                tvSectionTitle.setText("Chuyến đặt trước");
                tvCountdown.setVisibility(View.GONE);
                tvEmpty.setText("Chưa có chuyến đặt trước");
        }
        adapter.notifyDataSetChanged();
        tvEmpty.setVisibility(shown.isEmpty() ? View.VISIBLE : View.GONE);
    }

    /** Nút chính: nhận (waiting) hoặc hoàn thành (running). */
    @Override
    public void onPrimary(Trip trip) {
        if (trip.getId() == null) return;
        if (Trip.STATUS_WAITING.equals(trip.getStatus())) accept(trip);
        else if (Trip.STATUS_RUNNING.equals(trip.getStatus())) complete(trip);
    }

    @Override
    public void onSkip(Trip trip) {
        waiting.remove(trip);
        renderTab();
    }

    private void accept(Trip trip) {
        db.runTransaction(tr -> {
            DocumentSnapshot snap = tr.get(db.collection("trips").document(trip.getId()));
            if (!Trip.STATUS_WAITING.equals(snap.getString("status"))) {
                throw new com.google.firebase.firestore.FirebaseFirestoreException("Đơn đã được nhận",
                        com.google.firebase.firestore.FirebaseFirestoreException.Code.ABORTED);
            }
            tr.update(db.collection("trips").document(trip.getId()),
                    "status", Trip.STATUS_RUNNING,
                    "driverId", uid,
                    "driverName", driverName != null ? driverName : "",
                    "acceptedAt", Timestamp.now());
            return null;
        }).addOnSuccessListener(x -> {
            if (!isAdded()) return;
            Toast.makeText(getContext(), "✅ Đã nhận chuyến!", Toast.LENGTH_SHORT).show();
            loadTrips();
            selectTab(1);
        }).addOnFailureListener(e -> {
            if (!isAdded()) return;
            Toast.makeText(getContext(), "Không nhận được: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            loadTrips();
        });
    }

    private void complete(Trip trip) {
        db.collection("trips").document(trip.getId())
                .update("status", Trip.STATUS_COMPLETED, "completedAt", Timestamp.now())
                .addOnSuccessListener(x -> {
                    if (!isAdded()) return;
                    Toast.makeText(getContext(), "✅ Đã hoàn thành chuyến!", Toast.LENGTH_SHORT).show();
                    loadTrips();
                })
                .addOnFailureListener(e -> {
                    if (!isAdded()) return;
                    Toast.makeText(getContext(), "Lỗi: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }
}
