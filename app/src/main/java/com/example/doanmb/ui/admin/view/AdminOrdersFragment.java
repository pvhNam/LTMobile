package com.example.doanmb.ui.admin.view;

import android.app.AlertDialog;
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
import com.example.doanmb.ui.admin.adapter.OrderAdminAdapter;
import com.example.doanmb.ui.admin.util.AdminTab;
import com.example.doanmb.ui.admin.viewmodel.AdminDocList;
import com.example.doanmb.ui.admin.viewmodel.AdminOrdersViewModel;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Quản lý đơn hàng theo tab (chờ / đã xác nhận / tất cả): xác nhận, hoàn thành, huỷ qua {@link AdminOrdersViewModel}. */
public class AdminOrdersFragment extends Fragment {

    private RecyclerView rvOrders;
    private TextView tvCount, tvEmpty;
    private Button btnTabPending, btnTabConfirmed, btnTabAll;
    private OrderAdminAdapter adapter;
    private final List<Map<String, Object>> orderList = new ArrayList<>();
    private final List<String> orderIds = new ArrayList<>();
    private AdminOrdersViewModel viewModel;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_admin_orders, container, false);
        viewModel = new ViewModelProvider(this).get(AdminOrdersViewModel.class);

        view.findViewById(R.id.btn_orders_back).setOnClickListener(v -> {
            if (getParentFragmentManager().getBackStackEntryCount() > 0)
                getParentFragmentManager().popBackStack();
        });

        rvOrders       = view.findViewById(R.id.rv_admin_orders);
        tvCount        = view.findViewById(R.id.tv_admin_order_count);
        tvEmpty        = view.findViewById(R.id.tv_admin_empty_orders);
        btnTabPending  = view.findViewById(R.id.btn_order_tab_pending);
        btnTabConfirmed= view.findViewById(R.id.btn_order_tab_confirmed);
        btnTabAll      = view.findViewById(R.id.btn_order_tab_all);

        adapter = new OrderAdminAdapter(orderList, orderIds, new OrderAdminAdapter.OnOrderActionListener() {
            @Override public void onConfirm(String orderId)  { viewModel.confirmOrder(orderId); }
            @Override public void onComplete(String orderId) { askCompleteOrder(orderId); }
            @Override public void onCancel(String orderId)   { askCancelOrder(orderId); }
        });
        rvOrders.setLayoutManager(new LinearLayoutManager(getContext()));
        rvOrders.setAdapter(adapter);

        btnTabPending.setOnClickListener(v  -> viewModel.setTab(AdminOrdersViewModel.TAB_PENDING));
        btnTabConfirmed.setOnClickListener(v-> viewModel.setTab(AdminOrdersViewModel.TAB_CONFIRMED));
        btnTabAll.setOnClickListener(v      -> viewModel.setTab(AdminOrdersViewModel.TAB_ALL));

        viewModel.getOrders().observe(getViewLifecycleOwner(), this::render);
        viewModel.getMessage().observe(getViewLifecycleOwner(), msg -> {
            if (msg != null) Toast.makeText(getContext(), msg, Toast.LENGTH_SHORT).show();
        });

        viewModel.load();
        return view;
    }

    private void render(AdminDocList list) {
        int tab = viewModel.getCurrentTab();
        Button[] tabs = {btnTabPending, btnTabConfirmed, btnTabAll};
        AdminTab.select(tabs[tab], tabs);

        orderList.clear(); orderList.addAll(list.data);
        orderIds.clear(); orderIds.addAll(list.ids);
        adapter.updateList(orderList, orderIds);

        String[] labels = {"chờ xác nhận", "đã xác nhận", "tổng đơn"};
        tvCount.setText(list.size() + " " + labels[tab]);
        tvEmpty.setVisibility(list.isEmpty() ? View.VISIBLE : View.GONE);
        rvOrders.setVisibility(list.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private void askCompleteOrder(String orderId) {
        new AlertDialog.Builder(requireContext())
                .setTitle("Hoàn thành đơn")
                .setMessage("Xác nhận đơn đã hoàn thành?\nApp sẽ trừ 15% hoa hồng từ tiền cọc và trả 85% còn lại về ví chủ xe/tài xế.")
                .setPositiveButton("Hoàn thành", (d, w) -> viewModel.completeOrder(orderId))
                .setNegativeButton("Đóng", null)
                .show();
    }

    private void askCancelOrder(String orderId) {
        new AlertDialog.Builder(requireContext())
                .setTitle("Hủy đơn hàng")
                .setMessage("Bạn có chắc muốn hủy đơn hàng này không?\nHành động này sẽ trả xe về trạng thái đang bán.")
                .setPositiveButton("Hủy đơn", (d, w) -> viewModel.cancelOrder(orderId))
                .setNegativeButton("Không", null)
                .show();
    }

    @Override
    public void onResume() {
        super.onResume();
        viewModel.load();
    }
}
