package com.example.doanmb.ui.driver.view;

import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.bumptech.glide.Glide;
import com.example.doanmb.R;
import com.example.doanmb.core.helper.CloudinaryHelper;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Tài xế đăng bài cho thuê: "xe có tài xế" (type = driver) hoặc
 * "người lái / lái thuê" (type = driver_only). Cả hai đều lưu vào collection
 * "cars" nên tự hiển thị ở tab "Xe có tài xế" phía người dùng.
 */
public class DriverPostFragment extends Fragment {

    private RadioGroup rgPostType;
    private EditText etTitle, etCarType, etPrice, etPriceKm, etLocation, etInfo;
    private View scrollPreviews;
    private LinearLayout layoutPreviews;
    private Button btnPickImage, btnSubmit;

    private final List<Uri> selectedImages = new ArrayList<>();
    private FirebaseFirestore db;
    private String uid;

    private final ActivityResultLauncher<String> pickImageLauncher =
            registerForActivityResult(new ActivityResultContracts.GetMultipleContents(), uris -> {
                if (uris == null || uris.isEmpty() || !isAdded()) return;
                selectedImages.addAll(uris);
                renderPreviews();
            });

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_driver_post, container, false);

        db = FirebaseFirestore.getInstance();
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        uid = user != null ? user.getUid() : "";

        rgPostType = view.findViewById(R.id.rg_post_type);
        etTitle = view.findViewById(R.id.et_post_title);
        etCarType = view.findViewById(R.id.et_post_cartype);
        etPrice = view.findViewById(R.id.et_post_price);
        etPriceKm = view.findViewById(R.id.et_post_price_km);
        etLocation = view.findViewById(R.id.et_post_location);
        etInfo = view.findViewById(R.id.et_post_info);
        scrollPreviews = view.findViewById(R.id.scroll_post_previews);
        layoutPreviews = view.findViewById(R.id.layout_post_previews);
        btnPickImage = view.findViewById(R.id.btn_post_pick_image);
        btnSubmit = view.findViewById(R.id.btn_post_submit);

        rgPostType.setOnCheckedChangeListener((group, checkedId) ->
                etTitle.setHint(checkedId == R.id.rb_with_car
                        ? "VD: Toyota Vios 2022 kèm tài xế"
                        : "VD: Tài xế 5 năm kinh nghiệm nhận lái thuê"));

        btnPickImage.setOnClickListener(v -> pickImageLauncher.launch("image/*"));
        btnSubmit.setOnClickListener(v -> submitPost());

        prefillCarType();
        return view;
    }

    /** Vẽ lại dải ảnh đã chọn; bấm vào một ảnh để bỏ ảnh đó. */
    private void renderPreviews() {
        if (layoutPreviews == null) return;
        layoutPreviews.removeAllViews();
        scrollPreviews.setVisibility(selectedImages.isEmpty() ? View.GONE : View.VISIBLE);

        int size = dp(110);
        int margin = dp(6);
        for (Uri uri : selectedImages) {
            ImageView iv = new ImageView(requireContext());
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
            lp.setMarginEnd(margin);
            iv.setLayoutParams(lp);
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            iv.setBackgroundColor(0xFFEEEEEE);
            Glide.with(this).load(uri).into(iv);
            iv.setOnClickListener(v -> {
                selectedImages.remove(uri);
                renderPreviews();
            });
            layoutPreviews.addView(iv);
        }
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    /** Điền sẵn loại xe từ hồ sơ tài xế cho đỡ phải gõ lại. */
    private void prefillCarType() {
        if (uid.isEmpty()) return;
        db.collection("users").document(uid).get().addOnSuccessListener(doc -> {
            if (!isAdded()) return;
            String carType = doc.getString("driverCarType");
            if (carType != null && !carType.isEmpty() && etCarType.getText().toString().isEmpty()) {
                etCarType.setText(carType);
            }
        });
    }

    private void submitPost() {
        String title = etTitle.getText().toString().trim();
        String price = etPrice.getText().toString().trim();

        if (title.isEmpty() || price.isEmpty()) {
            Toast.makeText(requireContext(), "Vui lòng nhập tiêu đề và giá!", Toast.LENGTH_SHORT).show();
            return;
        }
        if (uid.isEmpty()) {
            Toast.makeText(requireContext(), "Vui lòng đăng nhập trước!", Toast.LENGTH_SHORT).show();
            return;
        }

        btnSubmit.setEnabled(false);
        btnSubmit.setText("Đang đăng...");

        if (!selectedImages.isEmpty()) {
            CloudinaryHelper.uploadImages(requireContext().getApplicationContext(),
                    new ArrayList<>(selectedImages),
                    new CloudinaryHelper.OnMultiUploadCallback() {
                        @Override
                        public void onSuccess(List<String> imageUrls) {
                            savePost(title, price, imageUrls);
                        }

                        @Override
                        public void onFailure(String error) {
                            if (!isAdded()) return;
                            Toast.makeText(requireContext(), "Lỗi upload ảnh: " + error, Toast.LENGTH_SHORT).show();
                            resetButton();
                        }
                    });
        } else {
            savePost(title, price, new ArrayList<>());
        }
    }

    private void savePost(String title, String price, List<String> imageUrls) {
        boolean withCar = rgPostType.getCheckedRadioButtonId() == R.id.rb_with_car;
        String carType = etCarType.getText().toString().trim();
        String location = etLocation.getText().toString().trim();
        String desc = etInfo.getText().toString().trim();

        // Giá theo ngày (bắt buộc) + giá theo km (tuỳ chọn, cho đặt theo chuyến)
        long pricePerDay = parseMoney(price);
        long pricePerKm  = parseMoney(etPriceKm.getText().toString());
        // Chuỗi giá hiển thị ngoài danh sách
        String priceDisplay = formatMoney(pricePerDay) + " đ/ngày"
                + (pricePerKm > 0 ? "  ·  " + formatMoney(pricePerKm) + " đ/km" : "");

        String info = (withCar ? "Xe có tài xế" : "Lái thuê")
                + (carType.isEmpty() ? "" : " • " + carType)
                + (location.isEmpty() ? "" : " • " + location)
                + (pricePerKm > 0 ? "\nNhận đặt theo chuyến: " + formatMoney(pricePerKm) + " đ/km" : "")
                + (desc.isEmpty() ? "" : "\n" + desc);

        db.collection("users").document(uid).get()
                .addOnSuccessListener(doc -> {
                    if (!isAdded()) return;

                    String sellerName = doc.getString("name");
                    String sellerPhone = doc.getString("phone");

                    String coverUrl = imageUrls.isEmpty() ? "" : imageUrls.get(0);

                    Map<String, Object> post = new HashMap<>();
                    post.put("name", title);
                    post.put("price", priceDisplay);
                    post.put("pricePerDay", pricePerDay);
                    post.put("pricePerKm", pricePerKm);
                    post.put("info", info);
                    post.put("type", withCar ? "driver" : "driver_only");
                    post.put("brand", "");
                    post.put("location", location);
                    post.put("status", "pending");
                    post.put("imageUrl", coverUrl);
                    post.put("imageUrls", imageUrls);
                    post.put("userId", uid);
                    post.put("sellerId", uid);
                    post.put("sellerName", sellerName != null ? sellerName : "");
                    post.put("sellerPhone", sellerPhone != null ? sellerPhone : "");
                    post.put("createdAt", Timestamp.now());

                    db.collection("cars").add(post)
                            .addOnSuccessListener(ref -> {
                                if (!isAdded()) return;
                                Toast.makeText(requireContext(), "✅ Đăng bài thành công!", Toast.LENGTH_LONG).show();
                                clearForm();
                            })
                            .addOnFailureListener(e -> {
                                if (!isAdded()) return;
                                Toast.makeText(requireContext(), "Lỗi: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                                resetButton();
                            });
                })
                .addOnFailureListener(e -> {
                    if (!isAdded()) return;
                    Toast.makeText(requireContext(), "Lỗi lấy thông tin tài xế!", Toast.LENGTH_SHORT).show();
                    resetButton();
                });
    }

    /** Lấy số tiền từ chuỗi nhập (bỏ mọi ký tự không phải số). */
    private static long parseMoney(String s) {
        if (s == null) return 0;
        String d = s.replaceAll("[^0-9]", "");
        if (d.isEmpty()) return 0;
        try { return Long.parseLong(d); } catch (NumberFormatException e) { return 0; }
    }

    private static String formatMoney(long amount) {
        return java.text.NumberFormat.getInstance(new java.util.Locale("vi", "VN")).format(amount);
    }

    private void clearForm() {
        etTitle.setText("");
        etPrice.setText("");
        etPriceKm.setText("");
        etLocation.setText("");
        etInfo.setText("");
        selectedImages.clear();
        renderPreviews();
        resetButton();
    }

    private void resetButton() {
        btnSubmit.setEnabled(true);
        btnSubmit.setText("Đăng bài");
    }
}
