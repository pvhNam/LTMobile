package com.example.doanmb.adapter;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.doanmb.R;
import com.example.doanmb.ui.activity.AdminUserDetailActivity;
import com.example.doanmb.util.ImageLoader;

import java.util.List;
import java.util.Map;

public class UserAdminAdapter extends RecyclerView.Adapter<UserAdminAdapter.ViewHolder> {

    private List<Map<String, Object>> users;
    private List<String> userIds;

    public UserAdminAdapter(List<Map<String, Object>> users, List<String> userIds) {
        this.users = users;
        this.userIds = userIds;
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
        String role = getStr(user, "role", "CUSTOMER");
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

        holder.itemView.setOnClickListener(v -> {
            Intent i = new Intent(ctx, AdminUserDetailActivity.class);
            i.putExtra(AdminUserDetailActivity.EXTRA_USER_ID, userId);
            ctx.startActivity(i);
        });
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
