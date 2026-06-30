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
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.doanmb.R;
import com.example.doanmb.ui.admin.adapter.CarAdminAdapter;
import com.example.doanmb.ui.admin.util.AdminTab;
import com.example.doanmb.ui.admin.viewmodel.AdminCarsViewModel;
import com.example.doanmb.ui.admin.viewmodel.AdminDocList;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Quản lý tin đăng xe: lọc theo tab (chờ duyệt / tất cả), duyệt – từ chối – xoá qua {@link AdminCarsViewModel}. */
public class AdminCarsFragment extends Fragment {

    private RecyclerView rvCars;
    private TextView tvCarCount, tvEmpty;
    private Button btnTabPending, btnTabAll;
    private CarAdminAdapter adapter;
    private final List<Map<String, Object>> carList = new ArrayList<>();
    private final List<String> carIds = new ArrayList<>();
    private AdminCarsViewModel viewModel;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_admin_cars, container, false);
        viewModel = new ViewModelProvider(this).get(AdminCarsViewModel.class);

        view.findViewById(R.id.btn_cars_back).setOnClickListener(v -> {
            if (getParentFragmentManager().getBackStackEntryCount() > 0)
                getParentFragmentManager().popBackStack();
        });

        rvCars = view.findViewById(R.id.rv_cars_admin);
        tvCarCount = view.findViewById(R.id.tv_car_count);
        tvEmpty = view.findViewById(R.id.tv_empty_cars);
        btnTabPending = view.findViewById(R.id.btn_tab_pending);
        btnTabAll = view.findViewById(R.id.btn_tab_all);

        // Adapter dùng chung cho cả 2 tab — nút chỉ hiện khi xe ở trạng thái pending
        adapter = new CarAdminAdapter(carList, carIds, new CarAdminAdapter.OnCarActionListener() {
            @Override public void onApprove(String carId) { viewModel.approveCar(carId); }
            @Override public void onReject(String carId)  { viewModel.rejectCar(carId); }
            @Override public void onDelete(String carId)  { viewModel.deleteCar(carId); }
            @Override public void onOpenDetail(String carId) { openDetail(carId); }
        });
        rvCars.setLayoutManager(new LinearLayoutManager(getContext()));
        rvCars.setAdapter(adapter);

        btnTabPending.setOnClickListener(v -> viewModel.setTab(true));
        btnTabAll.setOnClickListener(v -> viewModel.setTab(false));

        viewModel.getCars().observe(getViewLifecycleOwner(), this::render);
        viewModel.getMessage().observe(getViewLifecycleOwner(), msg -> {
            if (msg != null) Toast.makeText(getContext(), msg, Toast.LENGTH_SHORT).show();
        });

        viewModel.load();
        return view;
    }

    private void render(AdminDocList list) {
        boolean pending = viewModel.isShowingPending();
        AdminTab.select(pending ? btnTabPending : btnTabAll, btnTabPending, btnTabAll);

        carList.clear(); carList.addAll(list.data);
        carIds.clear(); carIds.addAll(list.ids);
        adapter.updateList(carList, carIds);

        tvCarCount.setText(pending ? list.size() + " xe chờ duyệt" : list.size() + " xe");
        tvEmpty.setText(pending ? "Không có xe nào chờ duyệt ✓" : "Không có xe nào");
        tvEmpty.setVisibility(list.isEmpty() ? View.VISIBLE : View.GONE);
        rvCars.setVisibility(list.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private void openDetail(String carId) {
        android.content.Intent intent = new android.content.Intent(getContext(), AdminCarDetailActivity.class);
        intent.putExtra(AdminCarDetailActivity.EXTRA_CAR_ID, carId);
        startActivity(intent);
    }

    @Override
    public void onResume() {
        super.onResume();
        viewModel.load();
    }
}
