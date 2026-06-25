package com.example.doanmb.ui.admin.adapter;

import android.app.AlertDialog;
import android.content.Context;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.NumberFormat;
import java.util.Locale;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.doanmb.core.helper.ImageLoader;
import com.example.doanmb.R;

import java.util.List;
import java.util.Map;

public class UserAdminAdapter extends RecyclerView.Adapter<UserAdminAdapter.ViewHolder> {

    public interface OnRoleChangeListener {
        void onRoleChange(String userId, String newRole);
    }

    public interface OnTopUpListener {
        void onTopUp(String userId, String userName, long amount);
    }

    private static final NumberFormat MONEY = NumberFormat.getInstance(new Locale("vi", "VN"));

    private List<Map<String, Object>> users;
    private List<String> userIds;
    private OnRoleChangeListener listener;
    private OnTopUpListener topUpListener;

    public UserAdminAdapter(List<Map<String, Object>> users, List<String> userIds,
                            OnRoleChangeListener listener, OnTopUpListener topUpListener) {
        this.users = users;
        this.userIds = userIds;
        this.listener = listener;
        this.topUpListener = topUpListener;
    }

    public void updateList(List<Map<String, Object>> newUsers, List<String> newIds) {
        this.users = newUsers;
        this.userIds = newIds;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_user_admin, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Map<String, Object> user = users.get(position);
        String userId = userIds.get(position);
        Context ctx = holder.itemView.getContext();

        String name = getStr(user, "name", "Không có tên");
        String email = getStr(user, "email", "");
        String phone = getStr(user, "phone", "");
        String role = normalizeRole(getStr(user, "role", "CUSTOMER"));
        String avatarUrl = getStr(user, "avatarUrl", "");

        holder.tvName.setText(name);
        holder.tvEmail.setText(email);
        holder.tvPhone.setText(phone.isEmpty() ? "Chưa có SĐT" : phone);
        holder.tvRole.setText(role);

        applyRoleStyle(ctx, holder.tvRole, role);

        if (!avatarUrl.isEmpty()) {
            ImageLoader.loadAvatar(holder.ivAvatar, avatarUrl, android.R.drawable.ic_menu_myplaces);
        } else {
            holder.ivAvatar.setImageResource(android.R.drawable.ic_menu_myplaces);
        }

        long balance = getLong(user, "balance");
        holder.itemView.setOnClickListener(v -> showActionDialog(ctx, userId, name, role, balance));
    }

    /** Chọn hành động cho user: nạp tiền vào ví hoặc đổi quyền. */
    private void showActionDialog(Context ctx, String userId, String userName, String role, long balance) {
        String[] actions = {
                "Nạp tiền vào ví  (số dư: " + MONEY.format(balance) + " đ)",
                "Đổi quyền  (" + role + ")"
        };
        new AlertDialog.Builder(ctx)
                .setTitle(userName)
                .setItems(actions, (dialog, which) -> {
                    if (which == 0) showTopUpDialog(ctx, userId, userName, balance);
                    else            showRoleDialog(ctx, userId, role);
                })
                .setNegativeButton("Đóng", null)
                .show();
    }

    private void showTopUpDialog(Context ctx, String userId, String userName, long balance) {
        final EditText input = new EditText(ctx);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setHint("Số tiền cần nạp (VNĐ)");
        int pad = (int) (20 * ctx.getResources().getDisplayMetrics().density);
        input.setPadding(pad, pad, pad, pad);

        new AlertDialog.Builder(ctx)
                .setTitle("Nạp tiền cho " + userName)
                .setMessage("Số dư hiện tại: " + MONEY.format(balance) + " đ")
                .setView(input)
                .setPositiveButton("Nạp", (dialog, which) -> {
                    String raw = input.getText().toString().trim().replaceAll("[^0-9]", "");
                    if (raw.isEmpty()) {
                        Toast.makeText(ctx, "Vui lòng nhập số tiền", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    long amount = Long.parseLong(raw);
                    if (amount <= 0) {
                        Toast.makeText(ctx, "Số tiền phải lớn hơn 0", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (topUpListener != null) topUpListener.onTopUp(userId, userName, amount);
                })
                .setNegativeButton("Hủy", null)
                .show();
    }

    private void showRoleDialog(Context ctx, String userId, String currentRole) {
        currentRole = normalizeRole(currentRole);
        String[] roles = {"ADMIN", "DRIVER", "CUSTOMER"};
        int currentIndex = 0;
        for (int i = 0; i < roles.length; i++) {
            if (roles[i].equals(currentRole)) { currentIndex = i; break; }
        }
        final int[] selected = {currentIndex};

        new AlertDialog.Builder(ctx)
                .setTitle("Đổi quyền người dùng")
                .setSingleChoiceItems(roles, currentIndex, (dialog, which) -> selected[0] = which)
                .setPositiveButton("Lưu", (dialog, which) -> {
                    if (listener != null) listener.onRoleChange(userId, roles[selected[0]]);
                })
                .setNegativeButton("Hủy", null)
                .show();
    }

    private void applyRoleStyle(Context ctx, TextView tv, String role) {
        switch (role) {
            case "ADMIN":
                tv.setBackgroundColor(ctx.getColor(R.color.role_admin_bg));
                tv.setTextColor(ctx.getColor(R.color.role_admin_text));
                break;
            case "DRIVER":
                tv.setBackgroundColor(ctx.getColor(R.color.role_driver_bg));
                tv.setTextColor(ctx.getColor(R.color.role_driver_text));
                break;
            default:
                tv.setBackgroundColor(ctx.getColor(R.color.role_customer_bg));
                tv.setTextColor(ctx.getColor(R.color.role_customer_text));
                break;
        }
    }

    private String getStr(Map<String, Object> map, String key, String def) {
        Object v = map.get(key);
        return (v != null) ? v.toString() : def;
    }

    private long getLong(Map<String, Object> map, String key) {
        Object v = map.get(key);
        if (v instanceof Number) return ((Number) v).longValue();
        return 0L;
    }

    private String normalizeRole(String role) {
        return role;
    }

    @Override
    public int getItemCount() { return users.size(); }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView ivAvatar;
        TextView tvName, tvEmail, tvPhone, tvRole;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            ivAvatar = itemView.findViewById(R.id.iv_user_avatar);
            tvName = itemView.findViewById(R.id.tv_user_name);
            tvEmail = itemView.findViewById(R.id.tv_user_email);
            tvPhone = itemView.findViewById(R.id.tv_user_phone);
            tvRole = itemView.findViewById(R.id.tv_user_role);
        }
    }
}
