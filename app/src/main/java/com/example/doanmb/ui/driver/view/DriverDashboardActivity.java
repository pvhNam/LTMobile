package com.example.doanmb.ui.driver.view;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import com.example.doanmb.R;
import com.example.doanmb.ui.auth.view.LoginActivity;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

/**
 * Khung giao diện tài xế: chỉ chứa khu vực fragment + thanh điều hướng 5 tab.
 * Mỗi tab (Trang chủ / Chuyến / Bản đồ / Thu nhập / Cá nhân) tự vẽ header xanh
 * của riêng mình theo thiết kế driver1-5.
 */
public class DriverDashboardActivity extends AppCompatActivity {

    private BottomNavigationView bottomNav;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_driver_dashboard);
        if (getSupportActionBar() != null) getSupportActionBar().hide();

        // Thanh trạng thái cùng tông xanh với header của các trang
        getWindow().setStatusBarColor(0xFF2E6BF0);
        getWindow().setNavigationBarColor(0xFFFFFFFF);

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            startActivity(new Intent(this, LoginActivity.class)
                    .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
            finish();
            return;
        }

        bottomNav = findViewById(R.id.driver_bottom_nav);
        bottomNav.setOnItemSelectedListener(item -> {
            switchFragment(item.getItemId());
            return true;
        });

        bottomNav.setSelectedItemId(R.id.nav_driver_home);
    }

    private void switchFragment(int itemId) {
        // Mỗi lần đổi tab thì xoá form đăng bài (nếu đang mở) khỏi back stack
        getSupportFragmentManager().popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE);

        Fragment fragment;
        if (itemId == R.id.nav_driver_trips) {
            fragment = new DriverTripsFragment();
        } else if (itemId == R.id.nav_driver_map) {
            fragment = new DriverMapFragment();
        } else if (itemId == R.id.nav_driver_earnings) {
            fragment = new DriverEarningsFragment();
        } else if (itemId == R.id.nav_driver_account) {
            fragment = new DriverProfileFragment();
        } else {
            fragment = new DriverHomeFragment();
        }
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.driver_fragment_container, fragment)
                .commit();
    }

    /** Chuyển sang tab Cá nhân (bấm avatar ở trang chủ). */
    public void openProfileTab() {
        if (bottomNav != null) bottomNav.setSelectedItemId(R.id.nav_driver_account);
    }

    /** Mở form "Đăng kí cho thuê xe" từ trang Cá nhân (driver5). */
    public void openPostForm() {
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.driver_fragment_container, new DriverPostFragment())
                .addToBackStack("post")
                .commit();
    }
}
