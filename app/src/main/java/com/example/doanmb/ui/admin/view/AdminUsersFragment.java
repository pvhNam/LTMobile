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
import com.example.doanmb.core.helper.WalletHelper;
import com.example.doanmb.ui.admin.adapter.UserAdminAdapter;
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

        adapter = new UserAdminAdapter(userList, userIds, this::changeUserRole, this::topUpUser);
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

    private void changeUserRole(String userId, String newRole) {
        db.collection("users").document(userId)
                .update("role", newRole)
                .addOnSuccessListener(v -> {
                    if (!isAdded()) return;
                    Toast.makeText(getContext(), "Đã đổi quyền thành " + newRole, Toast.LENGTH_SHORT).show();
                    loadUsers();
                })
                .addOnFailureListener(e -> {
                    if (!isAdded()) return;
                    Toast.makeText(getContext(), "Lỗi: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    private void topUpUser(String userId, String userName, long amount) {
        WalletHelper.topUp(userId, amount, new WalletHelper.Callback() {
            @Override
            public void onSuccess() {
                if (!isAdded()) return;
                Toast.makeText(getContext(),
                        "✅ Đã nạp " + amount + " đ cho " + userName, Toast.LENGTH_SHORT).show();
                loadUsers();
            }
            @Override
            public void onError(String message) {
                if (!isAdded()) return;
                Toast.makeText(getContext(), "Lỗi nạp tiền: " + message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        loadUsers();
    }
}
