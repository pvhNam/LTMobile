package com.example.doanmb.ui.admin.view;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.bumptech.glide.Glide;
import com.example.doanmb.R;
import com.example.doanmb.data.model.User;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class AdminDriverDetailFragment extends Fragment {

    private static final String ARG_UID = "uid";
    private static final SimpleDateFormat SDF =
            new SimpleDateFormat("HH:mm  dd/MM/yyyy", Locale.getDefault());

    private String uid;
    private User user;
    private FirebaseFirestore db;

    public static AdminDriverDetailFragment newInstance(String uid) {
        AdminDriverDetailFragment f = new AdminDriverDetailFragment();
        Bundle args = new Bundle();
        args.putString(ARG_UID, uid);
        f.setArguments(args);
        return f;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        uid = getArguments() != null ? getArguments().getString(ARG_UID) : "";
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_admin_driver_detail, container, false);
        db = FirebaseFirestore.getInstance();

        // Ẩn header "ADMIN" + thanh nav của dashboard để màn chi tiết hiển thị toàn phần
        setAdminChrome(false);

        view.findViewById(R.id.btn_dd_back).setOnClickListener(v -> {
            if (getParentFragmentManager().getBackStackEntryCount() > 0) {
                getParentFragmentManager().popBackStack();
            }
        });

        Button btnApprove = view.findViewById(R.id.btn_dd_approve);
        Button btnReject  = view.findViewById(R.id.btn_dd_reject);
        btnApprove.setOnClickListener(v -> approveDriver(btnApprove, btnReject));
        btnReject.setOnClickListener(v -> rejectDriver(btnApprove, btnReject));

        loadUser(view);
        return view;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        // Trả lại header + thanh nav khi rời màn chi tiết
        setAdminChrome(true);
    }

    /** Ẩn/hiện header "ADMIN" và thanh điều hướng dưới của AdminDashboard. */
    private void setAdminChrome(boolean show) {
        if (getActivity() == null) return;
        View header = getActivity().findViewById(R.id.admin_header);
        View nav    = getActivity().findViewById(R.id.admin_bottom_nav_container);
        int vis = show ? View.VISIBLE : View.GONE;
        if (header != null) header.setVisibility(vis);
        if (nav != null) nav.setVisibility(vis);
    }

    private void loadUser(View view) {
        if (uid.isEmpty()) return;
        db.collection("users").document(uid).get().addOnSuccessListener(doc -> {
            if (!isAdded() || !doc.exists()) return;
            user = doc.toObject(User.class);
            if (user == null) return;
            user.setUid(doc.getId());

            TextView tvName     = view.findViewById(R.id.tv_dd_name);
            TextView tvPhone    = view.findViewById(R.id.tv_dd_phone);
            TextView tvApplied  = view.findViewById(R.id.tv_dd_applied_at);
            TextView tvCccd     = view.findViewById(R.id.tv_dd_cccd);
            TextView tvLicense  = view.findViewById(R.id.tv_dd_license);
            TextView tvCarType  = view.findViewById(R.id.tv_dd_cartype);
            ImageView ivCccd    = view.findViewById(R.id.iv_dd_cccd);
            ImageView ivLicense = view.findViewById(R.id.iv_dd_license);

            tvName.setText(safe(user.getName(), "--"));
            tvPhone.setText("SĐT: " + safe(user.getPhone(), "--"));
            tvApplied.setText(user.getAppliedAt() != null
                    ? "Gửi lúc: " + SDF.format(user.getAppliedAt().toDate())
                    : "Gửi lúc: --");
            tvCccd.setText("Số CCCD: " + safe(user.getCccd(), "--"));
            tvLicense.setText("Số bằng lái: " + safe(user.getLicenseNumber(), "--"));
            tvCarType.setText("Loại xe: " + safe(user.getDriverCarType(), "--"));

            loadImg(ivCccd, user.getCccdImageUrl());
            loadImg(ivLicense, user.getLicenseImageUrl());
        });
    }

    private void approveDriver(Button btnApprove, Button btnReject) {
        if (uid.isEmpty()) return;
        btnApprove.setEnabled(false);
        btnReject.setEnabled(false);
        db.collection("users").document(uid)
                .update("driverStatus", "approved", "isDriver", true)
                .addOnSuccessListener(v -> {
                    if (!isAdded()) return;
                    sendNotification(uid,
                            "Đăng ký tài xế được duyệt ✅",
                            "Hồ sơ của bạn đã được Admin duyệt. Đăng xuất và đăng nhập lại để vào giao diện tài xế.");
                    Toast.makeText(getContext(), "Đã duyệt tài xế!", Toast.LENGTH_SHORT).show();
                    if (getParentFragmentManager().getBackStackEntryCount() > 0) {
                        getParentFragmentManager().popBackStack();
                    }
                })
                .addOnFailureListener(e -> {
                    if (!isAdded()) return;
                    btnApprove.setEnabled(true);
                    btnReject.setEnabled(true);
                    Toast.makeText(getContext(), "Lỗi: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    private void rejectDriver(Button btnApprove, Button btnReject) {
        if (uid.isEmpty()) return;
        btnApprove.setEnabled(false);
        btnReject.setEnabled(false);
        db.collection("users").document(uid)
                .update("driverStatus", "rejected", "isDriver", false)
                .addOnSuccessListener(v -> {
                    if (!isAdded()) return;
                    sendNotification(uid,
                            "Đăng ký tài xế bị từ chối ❌",
                            "Hồ sơ của bạn chưa đáp ứng yêu cầu. Vui lòng cập nhật và gửi lại.");
                    Toast.makeText(getContext(), "Đã từ chối hồ sơ.", Toast.LENGTH_SHORT).show();
                    if (getParentFragmentManager().getBackStackEntryCount() > 0) {
                        getParentFragmentManager().popBackStack();
                    }
                })
                .addOnFailureListener(e -> {
                    if (!isAdded()) return;
                    btnApprove.setEnabled(true);
                    btnReject.setEnabled(true);
                    Toast.makeText(getContext(), "Lỗi: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    private void sendNotification(String userId, String title, String body) {
        Map<String, Object> notif = new HashMap<>();
        notif.put("userId", userId);
        notif.put("title", title);
        notif.put("body", body);
        notif.put("createdAt", Timestamp.now());
        notif.put("read", false);
        db.collection("notifications").add(notif);
    }

    private void loadImg(ImageView iv, String url) {
        if (url != null && !url.isEmpty()) {
            Glide.with(this).load(url).into(iv);
        }
    }

    private String safe(String val, String def) {
        return val != null && !val.isEmpty() ? val : def;
    }
}
