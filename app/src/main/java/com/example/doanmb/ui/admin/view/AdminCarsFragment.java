package com.example.doanmb.ui.admin.view;

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
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.doanmb.R;
import com.example.doanmb.ui.admin.adapter.CarAdminAdapter;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class AdminCarsFragment extends Fragment {

    private RecyclerView rvCars;
    private TextView tvCarCount, tvEmpty;
    private Button btnTabPending, btnTabAll;
    private CarAdminAdapter adapter;
    private final List<Map<String, Object>> carList = new ArrayList<>();
    private final List<String> carIds = new ArrayList<>();
    private FirebaseFirestore db;
    private boolean showingPending = true;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_admin_cars, container, false);
        db = FirebaseFirestore.getInstance();

        rvCars = view.findViewById(R.id.rv_cars_admin);
        tvCarCount = view.findViewById(R.id.tv_car_count);
        tvEmpty = view.findViewById(R.id.tv_empty_cars);
        btnTabPending = view.findViewById(R.id.btn_tab_pending);
        btnTabAll = view.findViewById(R.id.btn_tab_all);

        // Adapter dùng chung cho cả 2 tab — nút chỉ hiện khi xe ở trạng thái pending
        adapter = new CarAdminAdapter(carList, carIds, new CarAdminAdapter.OnCarActionListener() {
            @Override public void onApprove(String carId) { approveCar(carId); }
            @Override public void onReject(String carId)  { rejectCar(carId); }
            @Override public void onDelete(String carId)  { deleteCar(carId); }
            @Override public void onOpenDetail(String carId) { openDetail(carId); }
        });
        rvCars.setLayoutManager(new LinearLayoutManager(getContext()));
        rvCars.setAdapter(adapter);

        btnTabPending.setOnClickListener(v -> switchTab(true));
        btnTabAll.setOnClickListener(v -> switchTab(false));

        applyTabStyle(true);
        loadCars();
        return view;
    }

    private void switchTab(boolean pending) {
        showingPending = pending;
        applyTabStyle(pending);
        loadCars();
    }

    private void applyTabStyle(boolean pendingActive) {
        if (pendingActive) {
            btnTabPending.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFFC62828));
            btnTabPending.setTextColor(0xFFFFFFFF);
            btnTabAll.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFFFFCDD2));
            btnTabAll.setTextColor(0xFFC62828);
        } else {
            btnTabAll.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFFC62828));
            btnTabAll.setTextColor(0xFFFFFFFF);
            btnTabPending.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFFFFCDD2));
            btnTabPending.setTextColor(0xFFC62828);
        }
    }

    private void loadCars() {
        // Load tất cả xe rồi lọc client-side
        // → bắt được cả xe cũ không có field "status" (null = chưa duyệt)
        db.collection("cars").get().addOnSuccessListener(this::processDocs);
    }

    private void processDocs(QuerySnapshot snapshots) {
        if (!isAdded()) return;
        carList.clear();
        carIds.clear();

        // Lọc trước, rồi sắp xếp mới nhất lên đầu theo createdAt
        List<QueryDocumentSnapshot> docs = new ArrayList<>();
        for (QueryDocumentSnapshot doc : snapshots) {
            String status = doc.getString("status");
            if (showingPending) {
                // Chờ duyệt: status == "pending" HOẶC chưa có field status
                if (status == null || "pending".equals(status)) docs.add(doc);
            } else {
                docs.add(doc); // Tất cả xe
            }
        }

        docs.sort((a, b) -> {
            Timestamp ta = a.getTimestamp("createdAt");
            Timestamp tb = b.getTimestamp("createdAt");
            if (ta == null && tb == null) return 0;
            if (ta == null) return 1;   // thiếu ngày -> xuống cuối
            if (tb == null) return -1;
            return tb.compareTo(ta);    // giảm dần: mới nhất trước
        });

        for (QueryDocumentSnapshot doc : docs) {
            carList.add(doc.getData());
            carIds.add(doc.getId());
        }

        adapter.updateList(carList, carIds);

        String label = showingPending
                ? carList.size() + " xe chờ duyệt"
                : carList.size() + " xe";
        tvCarCount.setText(label);

        String emptyMsg = showingPending ? "Không có xe nào chờ duyệt ✓" : "Không có xe nào";
        tvEmpty.setText(emptyMsg);
        tvEmpty.setVisibility(carList.isEmpty() ? View.VISIBLE : View.GONE);
        rvCars.setVisibility(carList.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private void openDetail(String carId) {
        android.content.Intent intent =
                new android.content.Intent(getContext(),
                        AdminCarDetailActivity.class);
        intent.putExtra(AdminCarDetailActivity.EXTRA_CAR_ID, carId);
        startActivity(intent);
    }

    private void approveCar(String carId) {
        db.collection("cars").document(carId)
                .update("status", "active")
                .addOnSuccessListener(v -> {
                    if (!isAdded()) return;
                    Toast.makeText(getContext(), "✅ Đã duyệt xe", Toast.LENGTH_SHORT).show();
                    loadCars();
                })
                .addOnFailureListener(e -> {
                    if (!isAdded()) return;
                    Toast.makeText(getContext(), "Lỗi: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    private void rejectCar(String carId) {
        db.collection("cars").document(carId)
                .update("status", "rejected")
                .addOnSuccessListener(v -> {
                    if (!isAdded()) return;
                    Toast.makeText(getContext(), "❌ Đã từ chối xe", Toast.LENGTH_SHORT).show();
                    loadCars();
                })
                .addOnFailureListener(e -> {
                    if (!isAdded()) return;
                    Toast.makeText(getContext(), "Lỗi: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    private void deleteCar(String carId) {
        db.collection("cars").document(carId)
                .delete()
                .addOnSuccessListener(v -> {
                    if (!isAdded()) return;
                    Toast.makeText(getContext(), "🗑️ Đã xóa xe khỏi hệ thống", Toast.LENGTH_SHORT).show();
                    loadCars();
                })
                .addOnFailureListener(e -> {
                    if (!isAdded()) return;
                    Toast.makeText(getContext(), "Lỗi: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    @Override
    public void onResume() {
        super.onResume();
        loadCars();
    }
}
