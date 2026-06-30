package com.example.doanmb.ui.admin.view;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.example.doanmb.R;
import com.example.doanmb.ui.admin.viewmodel.AdminProfileViewModel;

/** Trang hồ sơ admin: hiển thị tên + email lấy qua {@link AdminProfileViewModel}. */
public class AdminProfileFragment extends Fragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_admin_profile, container, false);

        TextView tvName = view.findViewById(R.id.tv_admin_profile_name);
        TextView tvEmail = view.findViewById(R.id.tv_admin_profile_email);
        TextView tvInfoName = view.findViewById(R.id.tv_info_name);
        TextView tvInfoEmail = view.findViewById(R.id.tv_info_email);

        AdminProfileViewModel viewModel = new ViewModelProvider(this).get(AdminProfileViewModel.class);

        viewModel.getName().observe(getViewLifecycleOwner(), name -> {
            if (name == null) return;
            tvName.setText(name);
            tvInfoName.setText(name);
        });
        viewModel.getEmail().observe(getViewLifecycleOwner(), email -> {
            if (email == null) return;
            tvEmail.setText(email);
            tvInfoEmail.setText(email);
        });

        viewModel.load();
        return view;
    }
}
