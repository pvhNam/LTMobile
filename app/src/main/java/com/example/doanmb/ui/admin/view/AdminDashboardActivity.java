package com.example.doanmb.ui.admin.view;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.example.doanmb.R;
import com.example.doanmb.ui.admin.viewmodel.AdminDashboardViewModel;
import com.example.doanmb.ui.auth.view.LoginActivity;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.auth.FirebaseAuth;

/**
 * Khung trang quản trị: thanh điều hướng dưới + chứa các fragment admin, và điều
 * hướng nhanh từ Trang chủ. Tên admin ở header lấy qua {@link AdminDashboardViewModel}.
 */
public class AdminDashboardActivity extends AppCompatActivity implements AdminOverviewFragment.OnQuickNavListener {

    private BottomNavigationView bottomNav;
    private TextView tvAdminName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_dashboard);

        if (getSupportActionBar() != null) getSupportActionBar().hide();

        tvAdminName = findViewById(R.id.tv_admin_name);
        bottomNav = findViewById(R.id.admin_bottom_nav);
        Button btnLogout = findViewById(R.id.btn_admin_logout);

        AdminDashboardViewModel viewModel = new ViewModelProvider(this).get(AdminDashboardViewModel.class);
        viewModel.getAdminName().observe(this, name -> {
            if (name != null) tvAdminName.setText(name);
        });
        viewModel.loadAdminName();

        btnLogout.setOnClickListener(v -> logout());

        bottomNav.setOnItemSelectedListener(item -> {
            switchFragment(item.getItemId());
            return true;
        });

        // Mở tab Tổng quan mặc định
        switchFragment(R.id.nav_admin_overview);
        bottomNav.setSelectedItemId(R.id.nav_admin_overview);

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (getSupportFragmentManager().getBackStackEntryCount() > 0) {
                    getSupportFragmentManager().popBackStack();
                    return;
                }
                if (bottomNav.getSelectedItemId() != R.id.nav_admin_overview) {
                    bottomNav.setSelectedItemId(R.id.nav_admin_overview);
                } else {
                    new MaterialAlertDialogBuilder(AdminDashboardActivity.this)
                            .setTitle("Thoát Admin")
                            .setMessage("Bạn có muốn thoát khỏi trang quản trị không?")
                            .setPositiveButton("Thoát", (d, w) -> finish())
                            .setNegativeButton("Ở lại", null)
                            .show();
                }
            }
        });
    }

    private void switchFragment(int itemId) {
        Fragment fragment;
        if (itemId == R.id.nav_admin_users) {
            fragment = new AdminUsersFragment();
        } else if (itemId == R.id.nav_admin_cars) {
            fragment = new AdminCarsFragment();
        } else if (itemId == R.id.nav_admin_orders) {
            fragment = new AdminOrdersFragment();
        } else if (itemId == R.id.nav_admin_reports) {
            fragment = new AdminReportsFragment();
        } else if (itemId == R.id.nav_admin_vouchers) {
            fragment = new AdminVouchersFragment();
        } else if (itemId == R.id.nav_admin_profile) {
            fragment = new AdminProfileFragment();
        } else if (itemId == R.id.nav_admin_driver_approval) {
            fragment = new AdminDriverApprovalFragment();
        } else if (itemId == R.id.nav_admin_drivers) {
            fragment = new AdminDriversFragment();
        } else {
            fragment = new AdminOverviewFragment();
        }

        // Các màn mở từ nút ở Trang chủ (không phải tab) -> đưa vào back-stack để bấm back quay lại Trang chủ.
        boolean addToBackStack = (itemId == R.id.nav_admin_driver_approval
                || itemId == R.id.nav_admin_cars
                || itemId == R.id.nav_admin_orders
                || itemId == R.id.nav_admin_reports
                || itemId == R.id.nav_admin_vouchers);
        androidx.fragment.app.FragmentTransaction tx = getSupportFragmentManager().beginTransaction()
                .replace(R.id.admin_fragment_container, fragment);
        if (addToBackStack) tx.addToBackStack(null);
        tx.commit();
    }

    @Override
    public void navigateTo(int itemId) {
        switchFragment(itemId);
    }

    private void logout() {
        FirebaseAuth.getInstance().signOut();
        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
