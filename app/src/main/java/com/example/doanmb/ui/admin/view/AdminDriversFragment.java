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
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.doanmb.R;
import com.example.doanmb.ui.admin.adapter.DriverAdminAdapter;
import com.example.doanmb.ui.admin.viewmodel.AdminDocList;
import com.example.doanmb.ui.admin.viewmodel.AdminDriversViewModel;

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
    private AdminDriversViewModel viewModel;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_admin_drivers, container, false);
        viewModel = new ViewModelProvider(this).get(AdminDriversViewModel.class);

        rvDrivers = view.findViewById(R.id.rv_drivers);
        tvCount = view.findViewById(R.id.tv_drv_count);
        tvEmpty = view.findViewById(R.id.tv_drv_empty);

        // Là tab ở thanh dưới nên không cần nút back.
        view.findViewById(R.id.btn_drv_back).setVisibility(View.GONE);

        adapter = new DriverAdminAdapter(driverList, driverIds, this::openDetail);
        rvDrivers.setLayoutManager(new LinearLayoutManager(getContext()));
        rvDrivers.setAdapter(adapter);

        viewModel.getDrivers().observe(getViewLifecycleOwner(), this::render);
        viewModel.getError().observe(getViewLifecycleOwner(), msg -> {
            if (msg != null) Toast.makeText(getContext(), "Lỗi tải tài xế: " + msg, Toast.LENGTH_SHORT).show();
        });

        viewModel.load();
        return view;
    }

    private void render(AdminDocList list) {
        driverList.clear(); driverList.addAll(list.data);
        driverIds.clear(); driverIds.addAll(list.ids);
        adapter.updateList(driverList, driverIds);
        tvCount.setText(list.size() + " tài xế");
        tvEmpty.setVisibility(list.isEmpty() ? View.VISIBLE : View.GONE);
        rvDrivers.setVisibility(list.isEmpty() ? View.GONE : View.VISIBLE);
    }

    @Override
    public void onResume() {
        super.onResume();
        viewModel.load();
    }

    private void openDetail(String userId) {
        Intent i = new Intent(getActivity(), AdminDriverProfileActivity.class);
        i.putExtra(AdminDriverProfileActivity.EXTRA_USER_ID, userId);
        startActivity(i);
    }
}
