package com.example.doanmb.ui.profile.view;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.cardview.widget.CardView;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.fragment.app.Fragment;
import com.example.doanmb.ui.car.view.ReviewActivity;
import com.example.doanmb.ui.driver.view.DriverDashboardActivity;
import com.example.doanmb.ui.driver.view.DriverRegisterActivity;
import com.example.doanmb.ui.profile.view.ChangePasswordActivity;
import com.example.doanmb.core.util.EdgeToEdgeUtil;
import com.example.doanmb.core.util.ImageLoader;
import com.example.doanmb.R;
import com.example.doanmb.ui.car.view.FavoriteCarsActivity;
import com.example.doanmb.ui.auth.view.LoginActivity;
import com.example.doanmb.ui.auth.view.RegisterActivity;
import com.example.doanmb.core.helper.CloudinaryHelper;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;
import android.app.DatePickerDialog;

import java.util.Calendar;

public class ProfileFragment extends Fragment {

    private LinearLayout layoutNotLoggedIn;
    private FrameLayout layoutLoggedIn;

    // 3 Sub-screens quản lý layer giao diện
    private ScrollView layoutMainProfile;
    private ConstraintLayout layoutAccountSettings;
    private RelativeLayout layoutPersonalInfo;

    // Views màn 1
    private CardView cardUserProfile;
    private TextView tvProfileNameMain, tvPhoneVerifiedBadge, tvWalletBalance;
    private ImageView ivAvatarMain,ivVerifiedIcon,ivFavouriteCar,ivRegRentCar,ivLocation;
    private Button btnLogin, btnRegister, btnLogout, btnSwitchDriver;
    private RelativeLayout menuAboutUs,menuGifts,menuReferral, menuPrivacy, menuSupportCenter,menuReview;
    // Views màn 2
    private ImageView ivAvatarSettings;
    private CardView ivChangeAvatarTrigger;
    private RelativeLayout menuPersonalInfoClick;
    private RelativeLayout menuFavoriteCars;
    private RelativeLayout menuRegisterDriver;
    private RelativeLayout menuSecurity;
    private TextView tvDriverStatusHint;
    private TextView tvRegisterDriverLabel;
    // Trạng thái hồ sơ tài xế của user hiện tại: "" / pending / approved / rejected
    private String driverStatus = "";
    private ImageView btnBackToMain; // Đã đổi sang ImageView để tránh lỗi theme

    // Views màn 3
    private ImageView btnBackToSettings;
    private EditText edtInfoName, edtInfoDob, edtInfoGender, edtInfoPhone, edtInfoCccd;
    private Button btnSavePersonalInfo;

    private FirebaseFirestore db;
    private String currentUserId = "";

    private final ActivityResultLauncher<String> pickImageLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) uploadAvatar(uri);
            });

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_profile, container, false);
        db = FirebaseFirestore.getInstance();
        initViews(view);
        setupListeners();
        return view;

    }

    private void initViews(View view) {
        layoutNotLoggedIn = view.findViewById(R.id.layout_not_logged_in);
        layoutLoggedIn = view.findViewById(R.id.layout_logged_in);

        layoutMainProfile = view.findViewById(R.id.layout_main_profile);
        layoutAccountSettings = view.findViewById(R.id.layout_account_settings);
        layoutPersonalInfo = view.findViewById(R.id.layout_personal_info);

        btnLogin = view.findViewById(R.id.btn_login);
        btnRegister = view.findViewById(R.id.btn_register);
        btnLogout = view.findViewById(R.id.btn_logout);
        btnSwitchDriver = view.findViewById(R.id.btn_switch_driver);
        cardUserProfile = view.findViewById(R.id.card_user_profile);
        tvProfileNameMain = view.findViewById(R.id.tv_profile_name_main);
        tvPhoneVerifiedBadge = view.findViewById(R.id.tv_phone_verified_badge);
        tvWalletBalance = view.findViewById(R.id.tv_wallet_balance);
        ivAvatarMain = view.findViewById(R.id.iv_avatar_main);

        ivAvatarSettings = view.findViewById(R.id.iv_avatar_settings);
        ivChangeAvatarTrigger = view.findViewById(R.id.iv_change_avatar_trigger);
        menuPersonalInfoClick = view.findViewById(R.id.menu_personal_info);
        menuFavoriteCars = view.findViewById(R.id.menu_favorite_cars);
        menuRegisterDriver = view.findViewById(R.id.menu_register_driver);
        tvDriverStatusHint = view.findViewById(R.id.tv_driver_status_hint);
        tvRegisterDriverLabel = view.findViewById(R.id.tv_register_driver_label);
        btnBackToMain = view.findViewById(R.id.btn_back_to_main);
        // phần đổi mật khẩu
        menuSecurity = view.findViewById(R.id.menu_security);

        // Header "Thông tin cá nhân" nằm dưới thanh trạng thái trên mọi máy
        EdgeToEdgeUtil.applyHeaderAndScroll(null, view.findViewById(R.id.top_bar_info));
        // Nút back màn "Cài đặt tài khoản" (nổi trên banner) cũng đẩy xuống dưới thanh trạng thái
        if (btnBackToMain != null) {
            android.view.ViewGroup.MarginLayoutParams lp =
                    (android.view.ViewGroup.MarginLayoutParams) btnBackToMain.getLayoutParams();
            lp.topMargin += EdgeToEdgeUtil.statusBarHeight(view.getContext());
            btnBackToMain.setLayoutParams(lp);
        }

        btnBackToSettings = view.findViewById(R.id.btn_back_to_settings);
        edtInfoName = view.findViewById(R.id.edt_info_name);
        edtInfoDob = view.findViewById(R.id.edt_info_dob);
        edtInfoGender = view.findViewById(R.id.edt_info_gender);
        edtInfoPhone = view.findViewById(R.id.edt_info_phone);
        edtInfoCccd = view.findViewById(R.id.edt_info_cccd);
        btnSavePersonalInfo = view.findViewById(R.id.btn_save_personal_info);
        ivVerifiedIcon = view.findViewById(R.id.iv_verified_icon);
        // phần tĩnh của trang profile
        menuAboutUs = view.findViewById(R.id.menu_about_us);
        menuGifts = view.findViewById(R.id.menu_gifts);
        menuReferral = view.findViewById(R.id.menu_referral);
        menuPrivacy = view.findViewById(R.id.menu_privacy);
        menuSupportCenter = view.findViewById(R.id.menu_support_center);
        menuReview= view.findViewById(R.id.menu_review);
    }

    private void setupListeners() {
        btnLogin.setOnClickListener(v -> startActivity(new Intent(getActivity(), LoginActivity.class)));
        btnRegister.setOnClickListener(v -> startActivity(new Intent(getActivity(), RegisterActivity.class)));
        btnLogout.setOnClickListener(v -> {
            // Xóa FCM token khỏi Firestore trước khi logout
            // → tránh máy này nhận thông báo của tài khoản cũ khi đã đổi tài khoản
            com.google.firebase.auth.FirebaseUser currentUser =
                    FirebaseAuth.getInstance().getCurrentUser();
            if (currentUser != null) {
                FirebaseFirestore.getInstance()
                        .collection("users")
                        .document(currentUser.getUid())
                        .update("fcmToken", "")
                        .addOnCompleteListener(task -> {
                            FirebaseAuth.getInstance().signOut();
                            switchSubScreen(1);
                            checkLoginStatus();
                        });
            } else {
                FirebaseAuth.getInstance().signOut();
                switchSubScreen(1);
                checkLoginStatus();
            }
        });

        cardUserProfile.setOnClickListener(v -> switchSubScreen(2));
        if (tvWalletBalance != null) {
            tvWalletBalance.setOnClickListener(v ->
                    startActivity(new Intent(getActivity(), WalletActivity.class)));
        }
        btnBackToMain.setOnClickListener(v -> switchSubScreen(1));
        menuPersonalInfoClick.setOnClickListener(v -> switchSubScreen(3));
        if (menuFavoriteCars != null) {
            menuFavoriteCars.setOnClickListener(v ->
                    startActivity(new Intent(getActivity(), FavoriteCarsActivity.class)));
        }
        // Đã được duyệt làm tài xế → chuyển sang giao diện tài xế, chưa thì mở form đăng ký
        menuRegisterDriver.setOnClickListener(v -> {
            if ("approved".equals(driverStatus)) {
                openDriverDashboard();
            } else {
                startActivity(new Intent(getActivity(),
                        DriverRegisterActivity.class));
            }
        });
        btnSwitchDriver.setOnClickListener(v -> openDriverDashboard());
        btnBackToSettings.setOnClickListener(v -> switchSubScreen(2));

        ivChangeAvatarTrigger.setOnClickListener(v -> pickImageLauncher.launch("image/*"));
        btnSavePersonalInfo.setOnClickListener(v -> saveUserInformationToFirestore());
        edtInfoDob.setOnClickListener(v -> {
            Calendar calendar = Calendar.getInstance();
            int year = calendar.get(Calendar.YEAR);
            int month = calendar.get(Calendar.MONTH);
            int day = calendar.get(Calendar.DAY_OF_MONTH);

            DatePickerDialog datePickerDialog = new DatePickerDialog(
                    getContext(),
                    (view1, selectedYear, selectedMonth, selectedDay) -> {
                        // Tự động thêm số 0 nếu ngày hoặc tháng < 10 (Ví dụ: 26/08/2005)
                        String formattedDate = String.format("%02d/%02d/%04d", selectedDay, selectedMonth + 1, selectedYear);
                        edtInfoDob.setText(formattedDate);
                    },
                    year, month, day
            );
            datePickerDialog.show();
        });
        if (menuAboutUs != null) {
            menuAboutUs.setOnClickListener(v -> {
                Intent intent = new Intent(getActivity(), AboutUsActivity.class);
                startActivity(intent);
            });
        }
        if (menuGifts != null) {
            menuGifts.setOnClickListener(v -> startActivity(new Intent(getActivity(), GiftsActivity.class)));
        }
        if (menuReferral != null) {
            menuReferral.setOnClickListener(v -> startActivity(new Intent(getActivity(), ReferralActivity.class)));
        }
        if (menuPrivacy != null) {
            menuPrivacy.setOnClickListener(v -> startActivity(new Intent(getActivity(), PrivacyActivity.class)));
        }
        if (menuSupportCenter != null) {
            menuSupportCenter.setOnClickListener(v -> startActivity(new Intent(getActivity(), SupportActivity.class)));
        }
        if (menuReview != null) {
            menuReview.setOnClickListener(v -> startActivity(new Intent(getActivity(), ReviewActivity.class)));
        }
        // logic phần chọn giới tính
        if (edtInfoGender != null) {
            edtInfoGender.setOnClickListener(v -> {
                String[] genders = {"Nam", "Nữ"};

                // Lấy giới tính đang hiển thị hiện tại để chọn sẵn trong danh sách
                int checkedItem = edtInfoGender.getText().toString().trim().equals("Nữ") ? 1 : 0;

                new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                        .setTitle("Chọn giới tính")
                        .setSingleChoiceItems(genders, checkedItem, (dialog, which) -> {
                            edtInfoGender.setText(genders[which]);
                            dialog.dismiss();
                        })
                        .create()
                        .show();
            });
        }
        // logic phần đổi mật khâu
        if (menuSecurity != null) {
            menuSecurity.setOnClickListener(v -> {
                startActivity(new Intent(getActivity(), ChangePasswordActivity.class));
            });
        }
    }

    private void openDriverDashboard() {
        startActivity(new Intent(getActivity(),
                DriverDashboardActivity.class));
        if (getActivity() != null) getActivity().finish();
    }

    private void switchSubScreen(int screenId) {
        layoutMainProfile.setVisibility(screenId == 1 ? View.VISIBLE : View.GONE);
        layoutAccountSettings.setVisibility(screenId == 2 ? View.VISIBLE : View.GONE);
        layoutPersonalInfo.setVisibility(screenId == 3 ? View.VISIBLE : View.GONE);
    }

    @Override
    public void onResume() {
        super.onResume();
        checkLoginStatus();
    }

    private void checkLoginStatus() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            layoutNotLoggedIn.setVisibility(View.GONE);
            layoutLoggedIn.setVisibility(View.VISIBLE);
            currentUserId = user.getUid();
            loadUserInfo(user.getUid());
        } else {
            layoutNotLoggedIn.setVisibility(View.VISIBLE);
            layoutLoggedIn.setVisibility(View.GONE);
        }
    }

    private void loadUserInfo(String userId) {
        db.collection("users").document(userId).get()
                .addOnSuccessListener(requireActivity(), doc -> {
                    if (!isAdded() || !doc.exists()) return;

                    String name = doc.getString("name");
                    String phone = doc.getString("phone");
                    String cccd = doc.getString("cccd");
                    String dob = doc.getString("dob");
                    String gender = doc.getString("gender");
                    String avatarUrl = doc.getString("avatarUrl");
                    Boolean phoneVerified = doc.getBoolean("phoneVerified");
                    String status = doc.getString("driverStatus");
                    driverStatus = status != null ? status : "";

                    // Có tài khoản tài xế đã duyệt → hiện nút chuyển đổi ở màn hình chính
                    if (btnSwitchDriver != null) {
                        btnSwitchDriver.setVisibility(
                                "approved".equals(driverStatus) ? View.VISIBLE : View.GONE);
                    }

                    if (tvRegisterDriverLabel != null) {
                        tvRegisterDriverLabel.setText("approved".equals(driverStatus)
                                ? "Chuyển sang chế độ tài xế"
                                : "Đăng ký làm tài xế");
                    }

                    if (tvDriverStatusHint != null) {
                        if ("pending".equals(driverStatus)) {
                            tvDriverStatusHint.setText("Chờ duyệt");
                            tvDriverStatusHint.setTextSize(13);
                            tvDriverStatusHint.setTextColor(android.graphics.Color.parseColor("#EF6C00"));
                        } else if ("approved".equals(driverStatus)) {
                            tvDriverStatusHint.setText("Đã duyệt");
                            tvDriverStatusHint.setTextSize(13);
                            tvDriverStatusHint.setTextColor(android.graphics.Color.parseColor("#2E7D32"));
                        } else if ("rejected".equals(driverStatus)) {
                            tvDriverStatusHint.setText("Bị từ chối");
                            tvDriverStatusHint.setTextSize(13);
                            tvDriverStatusHint.setTextColor(android.graphics.Color.parseColor("#C62828"));
                        } else {
                            tvDriverStatusHint.setText("›");
                            tvDriverStatusHint.setTextSize(24);
                            tvDriverStatusHint.setTextColor(android.graphics.Color.parseColor("#2A70DE"));
                        }
                    }

                    tvProfileNameMain.setText(name != null ? name : "Chưa đặt tên");
                    if (tvWalletBalance != null) {
                        Double balance = doc.getDouble("balance");
                        long bal = balance != null ? Math.round(balance) : 0L;
                        tvWalletBalance.setText("Số dư ví: "
                                + java.text.NumberFormat.getInstance(new java.util.Locale("vi", "VN")).format(bal) + " đ");
                    }
                    if (phoneVerified != null && phoneVerified) {
                        tvPhoneVerifiedBadge.setText("Đã xác thực");
                        tvPhoneVerifiedBadge.setTextColor(android.graphics.Color.parseColor("#4CAF50"));
                        ivVerifiedIcon.setVisibility(View.VISIBLE); // Hiện icon tích xanh xịn sò lên
                    } else {
                        tvPhoneVerifiedBadge.setText("Chưa xác thực số điện thoại");
                        tvPhoneVerifiedBadge.setTextColor(android.graphics.Color.parseColor("#E53935"));
                        ivVerifiedIcon.setVisibility(View.GONE); // Ẩn icon đi khi chưa xác thực
                    }

                    edtInfoName.setText(name != null ? name : "");
                    edtInfoPhone.setText(phone != null ? phone : "");
                    if (edtInfoCccd != null) edtInfoCccd.setText(cccd != null ? cccd : "");
                    edtInfoDob.setText(dob != null ? dob : "");
                    edtInfoGender.setText(gender != null ? gender : "Nam");

                    if (avatarUrl != null && !avatarUrl.isEmpty()) {
                        if (ivAvatarMain != null) {
                            ImageLoader.loadAvatar(ivAvatarMain, avatarUrl);
                        }
                        if (ivAvatarSettings != null) {
                            ImageLoader.loadAvatar(ivAvatarSettings, avatarUrl);
                        }
                    }
                });
    }

    private void uploadAvatar(Uri uri) {
        if (currentUserId.isEmpty()) return;

        Toast.makeText(getContext(), "Đang tải ảnh lên...", Toast.LENGTH_SHORT).show();

        CloudinaryHelper.uploadImage(requireContext(), uri, new CloudinaryHelper.OnUploadCallback() {
            @Override
            public void onSuccess(String imageUrl) {
                if (!isAdded() || getActivity() == null) return;

                db.collection("users").document(currentUserId)
                        .update("avatarUrl", imageUrl)
                        .addOnSuccessListener(requireActivity(), unused -> {
                            if (!isAdded()) return;

                            ImageLoader.loadAvatar(ivAvatarMain, imageUrl);
                            ImageLoader.loadAvatar(ivAvatarSettings, imageUrl);
                            Toast.makeText(getContext(), "✅ Cập nhật ảnh đại diện thành công!", Toast.LENGTH_SHORT).show();
                        });
            }

            @Override
            public void onFailure(String error) {
                if (!isAdded()) return;
                Toast.makeText(getContext(), "Lỗi: " + error, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void saveUserInformationToFirestore() {
        if (currentUserId.isEmpty()) return;

        String updatedName = edtInfoName.getText().toString().trim();
        String updatedDob = edtInfoDob.getText().toString().trim();
        String updatedGender = edtInfoGender.getText().toString().trim();
        String updatedPhone = edtInfoPhone.getText().toString().trim();
        String updatedCccd = edtInfoCccd != null ? edtInfoCccd.getText().toString().trim() : "";

        if (updatedName.isEmpty()) {
            Toast.makeText(getContext(), "Họ và tên không được để trống", Toast.LENGTH_SHORT).show();
            return;
        }
// --- validate sđt---
        if (updatedPhone.isEmpty()) {
            Toast.makeText(getContext(), "Số điện thoại không được để trống", Toast.LENGTH_SHORT).show();
            return;
        }

        // Regex kiểm tra số điện thoại VN: Bắt đầu bằng số 0, tiếp theo là 9 chữ số (Tổng cộng 10 số)
        String phoneRegex = "^0[0-9]{9}$";
        if (!updatedPhone.matches(phoneRegex)) {
            Toast.makeText(getContext(), "Số điện thoại không hợp lệ! Vui lòng nhập đủ 10 chữ số bắt đầu bằng số 0", Toast.LENGTH_LONG).show();
            return;
        }

        // CCCD không bắt buộc, nhưng nếu nhập thì phải là 9 số (CMND) hoặc 12 số (CCCD)
        if (!updatedCccd.isEmpty() && !updatedCccd.matches("^[0-9]{9}$|^[0-9]{12}$")) {
            Toast.makeText(getContext(), "Số CCCD/CMND không hợp lệ! Cần 9 hoặc 12 chữ số", Toast.LENGTH_LONG).show();
            return;
        }
        Map<String, Object> updateData = new HashMap<>();
        updateData.put("name", updatedName);
        updateData.put("dob", updatedDob);
        updateData.put("gender", updatedGender);
        updateData.put("phone", updatedPhone);
        updateData.put("cccd", updatedCccd);

        Toast.makeText(getContext(), "Đang lưu thay đổi...", Toast.LENGTH_SHORT).show();

        db.collection("users").document(currentUserId)
                .update(updateData)
                .addOnSuccessListener(unused -> {
                    Toast.makeText(getContext(), "Lưu thông tin thành công!", Toast.LENGTH_SHORT).show();
                    loadUserInfo(currentUserId);
                    switchSubScreen(2);
                })
                .addOnFailureListener(e -> Toast.makeText(getContext(), "Lỗi: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }
}