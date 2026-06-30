package com.example.doanmb.ui.admin.view;

import android.content.Intent;
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

import com.example.doanmb.R;
import com.example.doanmb.ui.admin.adapter.DriverAdminAdapter;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Danh sách tài xế đã duyệt (isDriver == true). */
public class AdminDriversFragment extends Fragment {

    private RecyclerView rvDrivers;
    private TextView tvCount, tvEmpty;
    private DriverAdminAdapter adapter;
    private final List<Map<String, Object>> driverList = new ArrayList<>();
    private final List<String> driverIds = new ArrayList<>();
    private FirebaseFirestore db;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_admin_drivers, container, false);
        db = FirebaseFirestore.getInstance();

        rvDrivers = view.findViewById(R.id.rv_drivers);
        tvCount = view.findViewById(R.id.tv_drv_count);
        tvEmpty = view.findViewById(R.id.tv_drv_empty);

        // Là tab ở thanh dưới nên không cần nút back.
        view.findViewById(R.id.btn_drv_back).setVisibility(View.GONE);

        adapter = new DriverAdminAdapter(driverList, driverIds, this::openDetail);
        rvDrivers.setLayoutManager(new LinearLayoutManager(getContext()));
        rvDrivers.setAdapter(adapter);

        loadDrivers();
        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        loadDrivers();
    }

    private void loadDrivers() {
        // Tải tất cả users rồi lọc client-side: tài xế = isDriver==true HOẶC driverStatus=="approved".
        // (Tài khoản duyệt từ trước có thể chỉ có driverStatus mà thiếu cờ isDriver.)
        db.collection("users").get()
                .addOnSuccessListener(snap -> {
                    if (!isAdded()) return;
                    driverList.clear();
                    driverIds.clear();
                    for (QueryDocumentSnapshot doc : snap) {
                        boolean isDriver = Boolean.TRUE.equals(doc.getBoolean("isDriver"));
                        boolean approved = "approved".equals(doc.getString("driverStatus"));
                        if (!isDriver && !approved) continue;
                        driverList.add(doc.getData());
                        driverIds.add(doc.getId());
                    }
                    adapter.updateList(driverList, driverIds);
                    tvCount.setText(driverList.size() + " tài xế");
                    tvEmpty.setVisibility(driverList.isEmpty() ? View.VISIBLE : View.GONE);
                    rvDrivers.setVisibility(driverList.isEmpty() ? View.GONE : View.VISIBLE);
                })
                .addOnFailureListener(e -> {
                    if (!isAdded()) return;
                    Toast.makeText(getContext(), "Lỗi tải tài xế: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    private void openDetail(String userId) {
        Intent i = new Intent(getActivity(), AdminDriverProfileActivity.class);
        i.putExtra(AdminDriverProfileActivity.EXTRA_USER_ID, userId);
        startActivity(i);
    }
}
