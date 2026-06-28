package com.example.doanmb.ui.driver.view;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.bumptech.glide.Glide;
import com.example.doanmb.ui.home.view.MainActivity;
import com.example.doanmb.R;
import com.example.doanmb.ui.car.view.FavoriteCarsActivity;
import com.example.doanmb.ui.car.view.ReviewActivity;
import com.example.doanmb.ui.auth.view.LoginActivity;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import de.hdodenhof.circleimageview.CircleImageView;

/** Tab "Cá nhân" của tài xế (thiết kế driver5): hồ sơ + menu + đăng xuất. */
public class DriverProfileFragment extends Fragment {

    private TextView tvName;
    private CircleImageView ivAvatar;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_driver_profile, container, false);

        tvName = v.findViewById(R.id.tv_dp_name);
        ivAvatar = v.findViewById(R.id.iv_dp_avatar);

        v.findViewById(R.id.row_register).setOnClickListener(x -> {
            if (getActivity() instanceof DriverDashboardActivity) {
                ((DriverDashboardActivity) getActivity()).openPostForm();
            }
        });
        v.findViewById(R.id.row_customer_mode).setOnClickListener(x -> switchToUserMode());
        v.findViewById(R.id.row_favorites).setOnClickListener(x ->
                startActivity(new Intent(getActivity(), FavoriteCarsActivity.class)));

        // Mở màn hình Bài đánh giá của tài xế
        v.findViewById(R.id.row_reviews).setOnClickListener(x -> openReviews());

        View.OnClickListener soon = x ->
                Toast.makeText(getContext(), "Tính năng đang được phát triển", Toast.LENGTH_SHORT).show();
        v.findViewById(R.id.row_location).setOnClickListener(soon);
        v.findViewById(R.id.row_gifts).setOnClickListener(soon);
        v.findViewById(R.id.row_refer).setOnClickListener(soon);
        v.findViewById(R.id.row_privacy).setOnClickListener(soon);
        v.findViewById(R.id.row_support).setOnClickListener(soon);
        v.findViewById(R.id.card_profile).setOnClickListener(soon);

        v.findViewById(R.id.btn_dp_logout).setOnClickListener(x -> logout());

        loadInfo();
        return v;
    }

    private void openReviews() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;
        Intent intent = new Intent(getActivity(), ReviewActivity.class);
        intent.putExtra(ReviewActivity.EXTRA_DRIVER_ID, user.getUid());
        startActivity(intent);
    }

    private void loadInfo() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;
        FirebaseFirestore.getInstance().collection("users").document(user.getUid()).get()
                .addOnSuccessListener(doc -> {
                    if (!isAdded() || !doc.exists()) return;
                    String name = doc.getString("name");
                    tvName.setText(name != null && !name.isEmpty() ? name : "Tài xế");
                    String avatar = doc.getString("avatarUrl");
                    if (avatar != null && !avatar.isEmpty()) Glide.with(this).load(avatar).into(ivAvatar);
                });
    }

    /** Quay về giao diện khách hàng, vẫn giữ đăng nhập. */
    private void switchToUserMode() {
        Intent intent = new Intent(getActivity(), MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        if (getActivity() != null) getActivity().finish();
    }

    private void logout() {
        FirebaseAuth.getInstance().signOut();
        Intent intent = new Intent(getActivity(), LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        if (getActivity() != null) getActivity().finish();
    }
}