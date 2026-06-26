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

        adapter = new DriverApprovalAdapter(pendingList, new DriverApprovalAdapter.OnDecisionListener() {
            @Override public void onApprove(User u) {}
            @Override public void onReject(User u) {}
        });
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
                        User u = doc.toObject(User.class);
                        u.setUid(doc.getId());
                        pendingList.add(u);
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


}
