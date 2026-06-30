package com.example.doanmb.ui.admin.view;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.PagerSnapHelper;
import androidx.recyclerview.widget.RecyclerView;

import com.example.doanmb.R;
import com.example.doanmb.ui.admin.viewmodel.AdminCarDetailViewModel;
import com.example.doanmb.ui.car.adapter.CarImageAdapter;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Màn admin xem chi tiết 1 bài đăng xe + duyệt / từ chối / xóa. */
public class AdminCarDetailActivity extends AppCompatActivity {

    public static final String EXTRA_CAR_ID = "CAR_ID";

    private TextView tvName, tvPrice, tvType, tvStatus, tvDate, tvInfo, tvSeller, tvPhone;
    private Button btnApprove, btnReject, btnDelete;
    private CarImageAdapter imageAdapter;
    private AdminCarDetailViewModel viewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_car_detail);
        if (getSupportActionBar() != null) getSupportActionBar().hide();

        viewModel = new ViewModelProvider(this).get(AdminCarDetailViewModel.class);
        String carId = getIntent().getStringExtra(EXTRA_CAR_ID);

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

        viewModel.getCar().observe(this, this::bindCar);
        viewModel.getMessage().observe(this, msg -> {
            if (msg != null) Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
        });
        viewModel.getFinishEvent().observe(this, finish -> {
            if (Boolean.TRUE.equals(finish)) finish();
        });

        viewModel.start(carId);
    }

    private void bindCar(DocumentSnapshot doc) {
        if (doc == null) return;

        String name   = str(doc.getString("name"), "Không tên");
        String price   = doc.getString("price");
        String info    = doc.getString("info");
        String type    = str(doc.getString("type"), "sale");
        String status  = str(doc.getString("status"), "");
        String seller  = doc.getString("sellerName");
        String phone   = doc.getString("sellerPhone");

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
        btnApprove.setOnClickListener(v -> viewModel.approve());
        btnReject.setOnClickListener(v -> viewModel.reject());
        btnDelete.setOnClickListener(v -> confirmDelete(name));
    }

    private void confirmDelete(String name) {
        new AlertDialog.Builder(this)
                .setTitle("Xóa xe")
                .setMessage("Bạn có chắc muốn xóa tin đăng \"" + name + "\" không?\nHành động này không thể hoàn tác.")
                .setPositiveButton("Xóa", (d, w) -> viewModel.delete())
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
