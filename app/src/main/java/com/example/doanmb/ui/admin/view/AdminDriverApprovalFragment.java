package com.example.doanmb.ui.admin.view;

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
import com.example.doanmb.ui.admin.adapter.DriverApprovalAdapter;
import com.example.doanmb.data.model.User;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.List;

public class AdminDriverApprovalFragment extends Fragment {

    private RecyclerView rvDriverApproval;
    private TextView tvEmpty, tvPendingCount;
    private DriverApprovalAdapter adapter;
    private final List<User> pendingList = new ArrayList<>();
    private FirebaseFirestore db;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_admin_driver_approval, container, false);
        db = FirebaseFirestore.getInstance();

        rvDriverApproval = view.findViewById(R.id.rv_driver_approval);
        tvEmpty = view.findViewById(R.id.tv_da_empty);
        tvPendingCount = view.findViewById(R.id.tv_da_pending_count);

        view.findViewById(R.id.btn_da_back).setOnClickListener(v -> {
            if (getParentFragmentManager().getBackStackEntryCount() > 0) {
                getParentFragmentManager().popBackStack();
            }
        });

        adapter = new DriverApprovalAdapter(pendingList);
        adapter.setOnItemClickListener(u -> {
            AdminDriverDetailFragment detail = AdminDriverDetailFragment.newInstance(u.getUid());
            getParentFragmentManager().beginTransaction()
                    .replace(R.id.admin_fragment_container, detail)
                    .addToBackStack(null)
                    .commit();
        });

        rvDriverApproval.setLayoutManager(new LinearLayoutManager(getContext()));
        rvDriverApproval.setAdapter(adapter);

        loadPending();
        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        loadPending();
    }

    private void loadPending() {
        db.collection("users")
                .whereEqualTo("driverStatus", "pending")
                .get()
                .addOnSuccessListener(snap -> {
                    if (!isAdded()) return;
                    pendingList.clear();
                    for (QueryDocumentSnapshot doc : snap) {
                        User u = parseUser(doc);
                        if (u != null) pendingList.add(u);
                    }
                    adapter.notifyDataSetChanged();
                    int count = pendingList.size();
                    tvPendingCount.setText(count + " hồ sơ đang chờ duyệt");
                    tvEmpty.setVisibility(count == 0 ? View.VISIBLE : View.GONE);
                    rvDriverApproval.setVisibility(count == 0 ? View.GONE : View.VISIBLE);
                })
                .addOnFailureListener(e -> {
                    if (!isAdded()) return;
                    Toast.makeText(getContext(), "Lỗi tải dữ liệu: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    /**
     * Đọc 1 hồ sơ tài xế từ document một cách an toàn.
     * Tránh dùng toObject(User.class) vì nó ném exception (gây crash cả màn)
     * nếu một field bị lưu sai kiểu trong Firestore (vd appliedAt lưu thành số).
     * Trả về null nếu doc lỗi để bỏ qua, không làm sập danh sách.
     */
    private User parseUser(QueryDocumentSnapshot doc) {
        try {
            User u = new User();
            u.setUid(doc.getId());
            u.setName(getStr(doc, "name"));
            u.setPhone(getStr(doc, "phone"));
            u.setCccd(getStr(doc, "cccd"));
            u.setLicenseNumber(getStr(doc, "licenseNumber"));
            u.setCccdImageUrl(getStr(doc, "cccdImageUrl"));
            u.setLicenseImageUrl(getStr(doc, "licenseImageUrl"));
            u.setDriverCarType(getStr(doc, "driverCarType"));
            u.setDriverStatus(getStr(doc, "driverStatus"));
            Object applied = doc.get("appliedAt");
            if (applied instanceof Timestamp) u.setAppliedAt((Timestamp) applied);
            return u;
        } catch (Exception e) {
            return null;
        }
    }

    /** Lấy field dạng String an toàn — trả "" nếu thiếu hoặc sai kiểu, không ném exception. */
    private String getStr(QueryDocumentSnapshot doc, String key) {
        Object v = doc.get(key);
        return v instanceof String ? (String) v : null;
    }
}
