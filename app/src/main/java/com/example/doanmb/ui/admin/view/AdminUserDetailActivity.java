package com.example.doanmb.ui.admin.view;

import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.doanmb.R;
import com.example.doanmb.ui.media.view.FullscreenImageActivity;
import com.example.doanmb.core.util.ImageLoader;
import com.example.doanmb.data.repository.WalletRepository;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.NumberFormat;
import java.util.Locale;

/** Màn admin xem chi tiết 1 người dùng + nạp tiền / đổi quyền. */
public class AdminUserDetailActivity extends AppCompatActivity {

    public static final String EXTRA_USER_ID = "USER_ID";

    private static final NumberFormat MONEY = NumberFormat.getInstance(new Locale("vi", "VN"));

    private ImageView ivAvatar, ivCccd, ivLicense;
    private TextView tvName, tvRole, tvDriverBadge, tvEmail, tvPhone, tvStatus, tvId, tvBalance;
    private TextView tvCarsCount, tvBuyCount, tvSellCount;
    private TextView tvDriverStatus, tvCccd, tvLicense, tvCarType;
    private View cardDriver;
    private Button btnTopUp, btnRole;

    private FirebaseFirestore db;
    private String userId;
    private String userName = "";
    private String currentRole = "CUSTOMER";
    private long balance = 0L;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_user_detail);
        if (getSupportActionBar() != null) getSupportActionBar().hide();

        db = FirebaseFirestore.getInstance();
        userId = getIntent().getStringExtra(EXTRA_USER_ID);

        ivAvatar      = findViewById(R.id.iv_admin_user_avatar);
        ivCccd        = findViewById(R.id.iv_admin_user_cccd);
        ivLicense     = findViewById(R.id.iv_admin_user_license);
        tvName        = findViewById(R.id.tv_admin_user_name);
        tvRole        = findViewById(R.id.tv_admin_user_role);
        tvDriverBadge = findViewById(R.id.tv_admin_user_driver_badge);
        tvEmail       = findViewById(R.id.tv_admin_user_email);
        tvPhone       = findViewById(R.id.tv_admin_user_phone);
        tvStatus      = findViewById(R.id.tv_admin_user_status);
        tvId          = findViewById(R.id.tv_admin_user_id);
        tvBalance     = findViewById(R.id.tv_admin_user_balance);
        tvCarsCount   = findViewById(R.id.tv_admin_user_cars_count);
        tvBuyCount    = findViewById(R.id.tv_admin_user_buy_count);
        tvSellCount   = findViewById(R.id.tv_admin_user_sell_count);
        tvDriverStatus = findViewById(R.id.tv_admin_user_driver_status);
        tvCccd        = findViewById(R.id.tv_admin_user_cccd);
        tvLicense     = findViewById(R.id.tv_admin_user_license);
        tvCarType     = findViewById(R.id.tv_admin_user_cartype);
        cardDriver    = findViewById(R.id.card_admin_user_driver);
        btnTopUp      = findViewById(R.id.btn_admin_user_topup);
        btnRole       = findViewById(R.id.btn_admin_user_role);

        findViewById(R.id.btn_admin_user_back).setOnClickListener(v -> finish());

        if (userId == null || userId.isEmpty()) {
            Toast.makeText(this, "Không tìm thấy người dùng", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        btnTopUp.setOnClickListener(v -> showTopUpDialog());
        btnRole.setOnClickListener(v -> showRoleDialog());

        tvId.setText("🆔  Mã: " + userId);

        loadUser();
        loadStats();
    }

    private void loadUser() {
        db.collection("users").document(userId).get()
                .addOnSuccessListener(this::bindUser)
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Lỗi tải người dùng: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    finish();
                });
    }

    private void bindUser(DocumentSnapshot doc) {
        if (!doc.exists()) {
            Toast.makeText(this, "Người dùng không còn tồn tại", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        userName = str(doc.getString("name"), "Không có tên");
        currentRole = str(doc.getString("role"), "CUSTOMER");
        balance = getLong(doc, "balance");

        String email = doc.getString("email");
        String phone = doc.getString("phone");
        String avatarUrl = doc.getString("avatarUrl");

        boolean isDriver = Boolean.TRUE.equals(doc.getBoolean("isDriver"));
        boolean driverOnline = Boolean.TRUE.equals(doc.getBoolean("driverOnline"));

        tvName.setText(userName);
        tvEmail.setText("✉️  " + (email == null || email.isEmpty() ? "Chưa có email" : email));
        tvPhone.setText("📞  " + (phone == null || phone.isEmpty() ? "Chưa có SĐT" : phone));
        tvBalance.setText(MONEY.format(balance) + " đ");

        if (isDriver) {
            tvStatus.setText(driverOnline ? "🟢  Tài xế đang online" : "⚪  Tài xế đang offline");
        } else {
            tvStatus.setText("👤  Tài khoản người dùng");
        }

        tvRole.setText(currentRole);
        applyRoleStyle(currentRole);
        tvDriverBadge.setVisibility(isDriver ? View.VISIBLE : View.GONE);

        if (avatarUrl != null && !avatarUrl.isEmpty()) {
            ImageLoader.loadAvatar(ivAvatar, avatarUrl, android.R.drawable.ic_menu_myplaces);
        } else {
            ivAvatar.setImageResource(android.R.drawable.ic_menu_myplaces);
        }

        // Hồ sơ tài xế: chỉ hiện nếu user là tài xế hoặc đã gửi đăng ký
        String driverStatus = doc.getString("driverStatus");
        String cccd = doc.getString("cccd");
        String license = doc.getString("licenseNumber");
        String carType = doc.getString("driverCarType");
        String cccdImg = doc.getString("cccdImageUrl");
        String licenseImg = doc.getString("licenseImageUrl");
        boolean hasDriverInfo = isDriver || (driverStatus != null && !driverStatus.isEmpty())
                || (cccd != null && !cccd.isEmpty());

        if (hasDriverInfo) {
            cardDriver.setVisibility(View.VISIBLE);
            tvCccd.setText("Số CCCD: " + str(cccd, "--"));
            tvLicense.setText("Số bằng lái: " + str(license, "--"));
            tvCarType.setText("Loại xe: " + str(carType, "--"));
            applyDriverStatusStyle(driverStatus);
            loadDocImage(ivCccd, cccdImg);
            loadDocImage(ivLicense, licenseImg);
        } else {
            cardDriver.setVisibility(View.GONE);
        }
    }

    /** Đếm số xe đã đăng, số đơn đã mua / đã bán của user. */
    private void loadStats() {
        db.collection("cars").whereEqualTo("sellerId", userId).get()
                .addOnSuccessListener(s -> tvCarsCount.setText(String.valueOf(s.size())))
                .addOnFailureListener(e -> tvCarsCount.setText("0"));
        db.collection("orders").whereEqualTo("buyerId", userId).get()
                .addOnSuccessListener(s -> tvBuyCount.setText(String.valueOf(s.size())))
                .addOnFailureListener(e -> tvBuyCount.setText("0"));
        db.collection("orders").whereEqualTo("sellerId", userId).get()
                .addOnSuccessListener(s -> tvSellCount.setText(String.valueOf(s.size())))
                .addOnFailureListener(e -> tvSellCount.setText("0"));
    }

    /** Mở ảnh giấy tờ toàn màn hình khi bấm. */
    private void loadDocImage(ImageView iv, String url) {
        if (url == null || url.isEmpty()) {
            iv.setImageResource(android.R.drawable.ic_menu_report_image);
            iv.setOnClickListener(null);
            return;
        }
        ImageLoader.loadDetail(iv, url, android.R.drawable.ic_menu_report_image);
        iv.setOnClickListener(v -> {
            android.content.Intent i = new android.content.Intent(this, FullscreenImageActivity.class);
            i.putExtra(FullscreenImageActivity.EXTRA_IMAGE_URL, url);
            startActivity(i);
        });
    }

    private void applyDriverStatusStyle(String status) {
        String s = status == null ? "" : status;
        switch (s) {
            case "approved":
                tvDriverStatus.setText("Đã duyệt");
                tvDriverStatus.setBackgroundColor(0xFFE8F5E9);
                tvDriverStatus.setTextColor(0xFF2E7D32);
                break;
            case "rejected":
                tvDriverStatus.setText("Từ chối");
                tvDriverStatus.setBackgroundColor(0xFFFFCDD2);
                tvDriverStatus.setTextColor(0xFFC62828);
                break;
            case "pending":
                tvDriverStatus.setText("Chờ duyệt");
                tvDriverStatus.setBackgroundColor(0xFFFFF3E0);
                tvDriverStatus.setTextColor(0xFFE65100);
                break;
            default:
                tvDriverStatus.setText("--");
                tvDriverStatus.setBackgroundColor(0xFFEEEEEE);
                tvDriverStatus.setTextColor(0xFF757575);
                break;
        }
    }

    private void showTopUpDialog() {
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setHint("Số tiền cần nạp (VNĐ)");
        int pad = (int) (20 * getResources().getDisplayMetrics().density);
        input.setPadding(pad, pad, pad, pad);

        new AlertDialog.Builder(this)
                .setTitle("Nạp tiền cho " + userName)
                .setMessage("Số dư hiện tại: " + MONEY.format(balance) + " đ")
                .setView(input)
                .setPositiveButton("Nạp", (dialog, which) -> {
                    String raw = input.getText().toString().trim().replaceAll("[^0-9]", "");
                    if (raw.isEmpty()) {
                        Toast.makeText(this, "Vui lòng nhập số tiền", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    long amount = Long.parseLong(raw);
                    if (amount <= 0) {
                        Toast.makeText(this, "Số tiền phải lớn hơn 0", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    topUp(amount);
                })
                .setNegativeButton("Hủy", null)
                .show();
    }

    private void topUp(long amount) {
        WalletRepository.topUp(userId, amount, new WalletRepository.Callback() {
            @Override
            public void onSuccess() {
                Toast.makeText(AdminUserDetailActivity.this,
                        "✅ Đã nạp " + MONEY.format(amount) + " đ cho " + userName, Toast.LENGTH_SHORT).show();
                loadUser();
            }
            @Override
            public void onError(String message) {
                Toast.makeText(AdminUserDetailActivity.this,
                        "Lỗi nạp tiền: " + message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showRoleDialog() {
        String[] roles = {"ADMIN", "CUSTOMER"};
        int currentIndex = 0;
        for (int i = 0; i < roles.length; i++) {
            if (roles[i].equals(currentRole)) { currentIndex = i; break; }
        }
        final int[] selected = {currentIndex};

        new AlertDialog.Builder(this)
                .setTitle("Đổi quyền người dùng")
                .setSingleChoiceItems(roles, currentIndex, (dialog, which) -> selected[0] = which)
                .setPositiveButton("Lưu", (dialog, which) -> changeRole(roles[selected[0]]))
                .setNegativeButton("Hủy", null)
                .show();
    }

    private void changeRole(String newRole) {
        db.collection("users").document(userId)
                .update("role", newRole)
                .addOnSuccessListener(v -> {
                    Toast.makeText(this, "Đã đổi quyền thành " + newRole, Toast.LENGTH_SHORT).show();
                    loadUser();
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Lỗi: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    private void applyRoleStyle(String role) {
        switch (role) {
            case "ADMIN":
                tvRole.setBackgroundColor(getColor(R.color.role_admin_bg));
                tvRole.setTextColor(getColor(R.color.role_admin_text));
                break;
            case "DRIVER":
                tvRole.setBackgroundColor(getColor(R.color.role_driver_bg));
                tvRole.setTextColor(getColor(R.color.role_driver_text));
                break;
            default:
                tvRole.setBackgroundColor(getColor(R.color.role_customer_bg));
                tvRole.setTextColor(getColor(R.color.role_customer_text));
                break;
        }
    }

    private static long getLong(DocumentSnapshot doc, String key) {
        Object v = doc.get(key);
        if (v instanceof Number) return ((Number) v).longValue();
        return 0L;
    }

    private static String str(String value, String def) {
        return (value != null && !value.isEmpty()) ? value : def;
    }
}
