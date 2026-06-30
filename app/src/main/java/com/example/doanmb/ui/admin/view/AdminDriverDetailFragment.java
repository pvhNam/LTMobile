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
import androidx.lifecycle.ViewModelProvider;

import com.bumptech.glide.Glide;
import com.example.doanmb.R;
import com.example.doanmb.ui.admin.viewmodel.AdminDriverDetailViewModel;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;

import java.text.SimpleDateFormat;
import java.util.Locale;

public class AdminDriverDetailFragment extends Fragment {

    private static final String ARG_UID = "uid";
    private static final SimpleDateFormat SDF =
            new SimpleDateFormat("HH:mm  dd/MM/yyyy", Locale.getDefault());

    private String uid;
    private View rootView;
    private Button btnApprove, btnReject;
    private AdminDriverDetailViewModel viewModel;

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
        rootView = inflater.inflate(R.layout.fragment_admin_driver_detail, container, false);
        viewModel = new ViewModelProvider(this).get(AdminDriverDetailViewModel.class);

        // Ẩn header "ADMIN" + thanh nav của dashboard để màn chi tiết hiển thị toàn phần
        setAdminChrome(false);

        rootView.findViewById(R.id.btn_dd_back).setOnClickListener(v -> {
            if (getParentFragmentManager().getBackStackEntryCount() > 0) {
                getParentFragmentManager().popBackStack();
            }
        });

        btnApprove = rootView.findViewById(R.id.btn_dd_approve);
        btnReject  = rootView.findViewById(R.id.btn_dd_reject);
        btnApprove.setOnClickListener(v -> { setButtonsEnabled(false); viewModel.approve(); });
        btnReject.setOnClickListener(v -> { setButtonsEnabled(false); viewModel.reject(); });

        viewModel.getUser().observe(getViewLifecycleOwner(), this::bindUser);
        viewModel.getMessage().observe(getViewLifecycleOwner(), msg -> {
            if (msg != null) Toast.makeText(getContext(), msg, Toast.LENGTH_SHORT).show();
        });
        viewModel.getActionFailed().observe(getViewLifecycleOwner(), failed -> {
            if (Boolean.TRUE.equals(failed)) setButtonsEnabled(true);
        });
        viewModel.getDone().observe(getViewLifecycleOwner(), done -> {
            if (Boolean.TRUE.equals(done) && getParentFragmentManager().getBackStackEntryCount() > 0) {
                getParentFragmentManager().popBackStack();
            }
        });

        viewModel.start(uid);
        return rootView;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        // Trả lại header + thanh nav khi rời màn chi tiết
        setAdminChrome(true);
    }

    private void setButtonsEnabled(boolean enabled) {
        if (btnApprove != null) btnApprove.setEnabled(enabled);
        if (btnReject != null) btnReject.setEnabled(enabled);
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

    // Đọc field trực tiếp thay vì toObject(User.class) — tránh crash nếu một field
    // bị lưu sai kiểu trong Firestore.
    private void bindUser(DocumentSnapshot doc) {
        if (doc == null) return;

        TextView tvName     = rootView.findViewById(R.id.tv_dd_name);
        TextView tvPhone    = rootView.findViewById(R.id.tv_dd_phone);
        TextView tvApplied  = rootView.findViewById(R.id.tv_dd_applied_at);
        TextView tvCccd     = rootView.findViewById(R.id.tv_dd_cccd);
        TextView tvLicense  = rootView.findViewById(R.id.tv_dd_license);
        TextView tvCarType  = rootView.findViewById(R.id.tv_dd_cartype);
        ImageView ivCccd    = rootView.findViewById(R.id.iv_dd_cccd);
        ImageView ivLicense = rootView.findViewById(R.id.iv_dd_license);

        Object appliedRaw = doc.get("appliedAt");
        Timestamp applied = appliedRaw instanceof Timestamp ? (Timestamp) appliedRaw : null;

        tvName.setText(safe(getStr(doc, "name"), "--"));
        tvPhone.setText("SĐT: " + safe(getStr(doc, "phone"), "--"));
        tvApplied.setText(applied != null ? "Gửi lúc: " + SDF.format(applied.toDate()) : "Gửi lúc: --");
        tvCccd.setText("Số CCCD: " + safe(getStr(doc, "cccd"), "--"));
        tvLicense.setText("Số bằng lái: " + safe(getStr(doc, "licenseNumber"), "--"));
        tvCarType.setText("Loại xe: " + safe(getStr(doc, "driverCarType"), "--"));

        loadImg(ivCccd, getStr(doc, "cccdImageUrl"));
        loadImg(ivLicense, getStr(doc, "licenseImageUrl"));
    }

    private void loadImg(ImageView iv, String url) {
        if (url != null && !url.isEmpty()) {
            Glide.with(this).load(url).into(iv);
        }
    }

    private String safe(String val, String def) {
        return val != null && !val.isEmpty() ? val : def;
    }

    /** Lấy field dạng String an toàn — trả null nếu thiếu hoặc sai kiểu, không ném exception. */
    private String getStr(DocumentSnapshot doc, String key) {
        Object v = doc.get(key);
        return v instanceof String ? (String) v : null;
    }
}
