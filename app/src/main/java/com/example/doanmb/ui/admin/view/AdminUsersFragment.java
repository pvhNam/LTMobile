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
import com.example.doanmb.ui.admin.adapter.UserAdminAdapter;
import com.example.doanmb.ui.admin.viewmodel.AdminDocList;
import com.example.doanmb.ui.admin.viewmodel.AdminUsersViewModel;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Danh sách toàn bộ người dùng; bấm vào item để xem chi tiết. Dữ liệu từ {@link AdminUsersViewModel}. */
public class AdminUsersFragment extends Fragment {

    private RecyclerView rvUsers;
    private TextView tvUserCount, tvEmpty;
    private UserAdminAdapter adapter;
    private final List<Map<String, Object>> userList = new ArrayList<>();
    private final List<String> userIds = new ArrayList<>();
    private AdminUsersViewModel viewModel;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_admin_users, container, false);
        viewModel = new ViewModelProvider(this).get(AdminUsersViewModel.class);

        rvUsers = view.findViewById(R.id.rv_users);
        tvUserCount = view.findViewById(R.id.tv_user_count);
        tvEmpty = view.findViewById(R.id.tv_empty_users);

        adapter = new UserAdminAdapter(userList, userIds);
        rvUsers.setLayoutManager(new LinearLayoutManager(getContext()));
        rvUsers.setAdapter(adapter);

        viewModel.getUsers().observe(getViewLifecycleOwner(), this::render);
        viewModel.getError().observe(getViewLifecycleOwner(), msg -> {
            if (msg != null) Toast.makeText(getContext(), "Lỗi tải người dùng: " + msg, Toast.LENGTH_SHORT).show();
        });

        viewModel.load();
        return view;
    }

    private void render(AdminDocList list) {
        userList.clear(); userList.addAll(list.data);
        userIds.clear(); userIds.addAll(list.ids);
        adapter.updateList(userList, userIds);
        tvUserCount.setText(list.size() + " người dùng");
        tvEmpty.setVisibility(list.isEmpty() ? View.VISIBLE : View.GONE);
        rvUsers.setVisibility(list.isEmpty() ? View.GONE : View.VISIBLE);
    }

    @Override
    public void onResume() {
        super.onResume();
        viewModel.load();
    }
}
