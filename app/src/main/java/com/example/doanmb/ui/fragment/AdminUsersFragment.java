package com.example.doanmb.ui.fragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.doanmb.R;
import com.example.doanmb.adapter.UserAdminAdapter;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class AdminUsersFragment extends Fragment {

    private RecyclerView rvUsers;
    private TextView tvUserCount, tvEmpty;
    private UserAdminAdapter adapter;
    private List<Map<String, Object>> userList = new ArrayList<>();
    private List<String> userIds = new ArrayList<>();
    private FirebaseFirestore db;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_admin_users, container, false);
        db = FirebaseFirestore.getInstance();

        rvUsers = view.findViewById(R.id.rv_users);
        tvUserCount = view.findViewById(R.id.tv_user_count);
        tvEmpty = view.findViewById(R.id.tv_empty_users);

        adapter = new UserAdminAdapter(userList, userIds);
        rvUsers.setLayoutManager(new LinearLayoutManager(getContext()));
        rvUsers.setAdapter(adapter);

        loadUsers();
        return view;
    }

    private void loadUsers() {
        db.collection("users").get().addOnSuccessListener(snapshots -> {
            if (!isAdded()) return;
            userList.clear();
            userIds.clear();
            for (QueryDocumentSnapshot doc : snapshots) {
                userList.add(doc.getData());
                userIds.add(doc.getId());
            }
            adapter.updateList(userList, userIds);
            tvUserCount.setText(userList.size() + " người dùng");
            tvEmpty.setVisibility(userList.isEmpty() ? View.VISIBLE : View.GONE);
            rvUsers.setVisibility(userList.isEmpty() ? View.GONE : View.VISIBLE);
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        loadUsers();
    }
}
