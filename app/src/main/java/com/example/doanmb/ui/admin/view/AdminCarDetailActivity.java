package com.example.doanmb.ui.admin.view;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.PagerSnapHelper;
import androidx.recyclerview.widget.RecyclerView;

import com.example.doanmb.R;
import com.example.doanmb.ui.car.adapter.CarImageAdapter;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Màn admin xem chi tiết 1 bài đăng xe + duyệt / từ chối / xóa. */
public class AdminCarDetailActivity extends AppCompatActivity {

    public static final String EXTRA_CAR_ID = "CAR_ID";

    private TextView tvName, tvPrice, tvType, tvStatus, tvDate, tvInfo, tvSeller, tvPhone;
    private Button btnApprove, btnReject, btnDelete;
    private CarImageAdapter imageAdapter;

    private FirebaseFirestore db;
    private String carId;
    private String sellerId = "";
    private String carName  = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_car_detail);
        if (getSupportActionBar() != null) getSupportActionBar().hide();

        db = FirebaseFirestore.getInstance();
        carId = getIntent().getStringExtra(EXTRA_CAR_ID);

        tvName     = findViewById(R.id.tv_admin_detail_name);
        tvPrice    = findViewById(R.id.tv_admin_detail_price);
        tvType     = findViewById(R.id.tv_admin_detail_type);
        tvStatus   = findViewById(R.id.tv_admin_detail_status);
        tvDate     = findViewById(R.id.tv_admin_detail_date);
        tvInfo     = findViewById(R.id.tv_admin_detail_info);
        tvSeller   = findViewById(R.id.tv_admin_detail_seller);
        tvPhone    = findViewById(R.id.tv_admin_detail_phone);
        btnApprove = findViewById(R.id.btn_admin_detail_approve);
        btnReject  = findViewById(R.id.btn_admin_detail_reject);
        btnDelete  = findViewById(R.id.btn_admin_detail_delete);

        RecyclerView rvImages = findViewById(R.id.rv_admin_car_images);
        imageAdapter = new CarImageAdapter();
        rvImages.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        rvImages.setAdapter(imageAdapter);
        new PagerSnapHelper().attachToRecyclerView(rvImages);

        findViewById(R.id.btn_admin_detail_back).setOnClickListener(v -> finish());

        if (carId == null || carId.isEmpty()) {
            Toast.makeText(this, "Không tìm thấy bài đăng", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        loadCar();
    }

    private void loadCar() {
        db.collection("cars").document(carId).get()
                .addOnSuccessListener(this::bindCar)
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Lỗi tải bài đăng: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    finish();
                });
    }

    private void bindCar(DocumentSnapshot doc) {
        if (!doc.exists()) {
            Toast.makeText(this, "Bài đăng không còn tồn tại", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        String name   = str(doc.getString("name"), "Không tên");
        String price   = doc.getString("price");
        String info    = doc.getString("info");
        String type    = str(doc.getString("type"), "sale");
        String status  = str(doc.getString("status"), "");
        String seller  = doc.getString("sellerName");
        String phone   = doc.getString("sellerPhone");

        carName  = name;
        sellerId = str(doc.getString("sellerId"), str(doc.getString("userId"), ""));

        tvName.setText(name);
        tvPrice.setText(price == null || price.isEmpty() ? "Chưa có giá" : price);
        tvInfo.setText(info == null || info.isEmpty() ? "(Không có mô tả)" : info);
        tvSeller.setText("👤  " + (seller == null || seller.isEmpty() ? "Ẩn danh" : seller));
        tvPhone.setText("📞  " + (phone == null || phone.isEmpty() ? "Chưa có" : phone));

        boolean isSale = "sale".equals(type);
        tvType.setText(isSale ? "Bán" : "Cho thuê");
        tvType.setBackgroundColor(isSale ? 0xFF1565C0 : 0xFF2E7D32);
        applyStatusStyle(status);

        Timestamp createdAt = doc.getTimestamp("createdAt");
        if (createdAt != null) {
            tvDate.setText("Ngày đăng: "
                    + new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(createdAt.toDate()));
        } else {
            tvDate.setText("Ngày đăng: --");
        }

        // Ảnh: ưu tiên danh sách imageUrls, fallback ảnh đại diện imageUrl
        List<String> images = extractImageUrls(doc.get("imageUrls"));
        String cover = doc.getString("imageUrl");
        if (images.isEmpty() && cover != null && !cover.isEmpty()) images.add(cover);
        imageAdapter.setImages(images);

        // Nút Duyệt / Từ chối chỉ hiện khi bài đang chờ duyệt (pending hoặc chưa có status)
        boolean isPending = status.isEmpty() || "pending".equals(status);
        btnApprove.setVisibility(isPending ? View.VISIBLE : View.GONE);
        btnReject.setVisibility(isPending ? View.VISIBLE : View.GONE);
        btnApprove.setOnClickListener(v -> updateStatus("active", "✅ Đã duyệt xe"));
        btnReject.setOnClickListener(v -> rejectCar());
        btnDelete.setOnClickListener(v -> confirmDelete(name));
    }

    /** Từ chối bài + gửi thông báo cho người đăng (giống luồng duyệt tài xế). */
    private void rejectCar() {
        db.collection("cars").document(carId)
                .update("status", "rejected")
                .addOnSuccessListener(v -> {
                    sendNotification(sellerId,
                            "Bài đăng bị từ chối ❌",
                            "Tin đăng \"" + carName + "\" của bạn chưa đạt yêu cầu và đã bị từ chối. "
                                    + "Vui lòng kiểm tra lại và đăng lại.");
                    Toast.makeText(this, "❌ Đã từ chối xe", Toast.LENGTH_SHORT).show();
                    finish();
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Lỗi: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    /** Tạo thông báo trong collection "notifications" cho người dùng. */
    private void sendNotification(String userId, String title, String body) {
        if (userId == null || userId.isEmpty()) return;
        Map<String, Object> notif = new HashMap<>();
        notif.put("userId", userId);
        notif.put("title", title);
        notif.put("body", body);
        notif.put("createdAt", Timestamp.now());
        notif.put("read", false);
        db.collection("notifications").add(notif);
    }

    private void updateStatus(String newStatus, String successMsg) {
        db.collection("cars").document(carId)
                .update("status", newStatus)
                .addOnSuccessListener(v -> {
                    Toast.makeText(this, successMsg, Toast.LENGTH_SHORT).show();
                    finish();
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Lỗi: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    private void confirmDelete(String name) {
        new AlertDialog.Builder(this)
                .setTitle("Xóa xe")
                .setMessage("Bạn có chắc muốn xóa tin đăng \"" + name + "\" không?\nHành động này không thể hoàn tác.")
                .setPositiveButton("Xóa", (d, w) -> db.collection("cars").document(carId).delete()
                        .addOnSuccessListener(v -> {
                            Toast.makeText(this, "🗑️ Đã xóa xe khỏi hệ thống", Toast.LENGTH_SHORT).show();
                            finish();
                        })
                        .addOnFailureListener(e ->
                                Toast.makeText(this, "Lỗi: " + e.getMessage(), Toast.LENGTH_SHORT).show()))
                .setNegativeButton("Hủy", null)
                .show();
    }

    private void applyStatusStyle(String status) {
        switch (status) {
            case "active":
                tvStatus.setText("Đang bán");
                tvStatus.setBackgroundColor(0xFFE8F5E9);
                tvStatus.setTextColor(0xFF2E7D32);
                break;
            case "sold":
                tvStatus.setText("Đã bán");
                tvStatus.setBackgroundColor(0xFFEEEEEE);
                tvStatus.setTextColor(0xFF757575);
                break;
            case "holding":
                tvStatus.setText("Đặt cọc");
                tvStatus.setBackgroundColor(0xFFE3F2FD);
                tvStatus.setTextColor(0xFF1565C0);
                break;
            case "rejected":
                tvStatus.setText("Từ chối");
                tvStatus.setBackgroundColor(0xFFFFCDD2);
                tvStatus.setTextColor(0xFFC62828);
                break;
            case "hidden":
                tvStatus.setText("Đã ẩn");
                tvStatus.setBackgroundColor(0xFFEEEEEE);
                tvStatus.setTextColor(0xFF757575);
                break;
            default:
                tvStatus.setText("Chờ duyệt");
                tvStatus.setBackgroundColor(0xFFFFF3E0);
                tvStatus.setTextColor(0xFFE65100);
                break;
        }
    }

    /** Chuyển field "imageUrls" (List) của Firestore thành List<String> an toàn. */
    private static List<String> extractImageUrls(Object raw) {
        List<String> urls = new ArrayList<>();
        if (raw instanceof List) {
            for (Object item : (List<?>) raw) {
                if (item != null && !item.toString().isEmpty()) urls.add(item.toString());
            }
        }
        return urls;
    }

    private static String str(String value, String def) {
        return (value != null && !value.isEmpty()) ? value : def;
    }
}
