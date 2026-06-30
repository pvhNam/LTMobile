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
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.doanmb.R;
import com.example.doanmb.ui.admin.adapter.DriverApprovalAdapter;
import com.example.doanmb.ui.admin.viewmodel.AdminDriverApprovalViewModel;
import com.example.doanmb.data.model.User;

import java.util.ArrayList;
import java.util.List;

/** Danh sách hồ sơ tài xế chờ duyệt; bấm vào item mở màn duyệt chi tiết. Dữ liệu từ {@link AdminDriverApprovalViewModel}. */
public class AdminDriverApprovalFragment extends Fragment {

    private RecyclerView rvDriverApproval;
    private TextView tvEmpty, tvPendingCount;
    private DriverApprovalAdapter adapter;
    private final List<User> pendingList = new ArrayList<>();
    private AdminDriverApprovalViewModel viewModel;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_admin_driver_approval, container, false);
        viewModel = new ViewModelProvider(this).get(AdminDriverApprovalViewModel.class);

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

        viewModel.getPending().observe(getViewLifecycleOwner(), this::render);
        viewModel.getError().observe(getViewLifecycleOwner(), msg -> {
            if (msg != null) Toast.makeText(getContext(), "Lỗi tải dữ liệu: " + msg, Toast.LENGTH_SHORT).show();
        });

        viewModel.load();
        return view;
    }

    private void render(List<User> list) {
        pendingList.clear();
        pendingList.addAll(list);
        adapter.notifyDataSetChanged();
        int count = pendingList.size();
        tvPendingCount.setText(count + " hồ sơ đang chờ duyệt");
        tvEmpty.setVisibility(count == 0 ? View.VISIBLE : View.GONE);
        rvDriverApproval.setVisibility(count == 0 ? View.GONE : View.VISIBLE);
    }

    @Override
    public void onResume() {
        super.onResume();
        viewModel.load();
    }
}
