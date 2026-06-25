package com.example.doanmb.ui.media.view;

import android.os.Bundle;
import android.view.WindowManager;
import android.widget.ImageView;

import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.example.doanmb.R;

/**
 * Hiển thị ảnh toàn màn hình với hỗ trợ pinch-to-zoom.
 * Gọi bằng: Intent + extra "IMAGE_URL" (String)
 */
public class FullscreenImageActivity extends AppCompatActivity {

    public static final String EXTRA_IMAGE_URL = "IMAGE_URL";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Ẩn status bar, hiển thị toàn màn hình
        getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN
        );

        setContentView(R.layout.activity_fullscreen_image);

        if (getSupportActionBar() != null) getSupportActionBar().hide();

        String imageUrl = getIntent().getStringExtra(EXTRA_IMAGE_URL);

        com.github.chrisbanes.photoview.PhotoView photoView = findViewById(R.id.photo_view);
        ImageView btnClose = findViewById(R.id.btn_close_fullscreen);

        // Load ảnh chất lượng gốc (không resize)
        if (imageUrl != null && !imageUrl.isEmpty()) {
            // Nếu là Cloudinary, bỏ transform để lấy ảnh gốc
            String originalUrl = imageUrl.contains("cloudinary.com")
                    ? imageUrl.replaceAll("/upload/[^/]+/", "/upload/")
                    : imageUrl;

            Glide.with(this)
                    .load(originalUrl)
                    .placeholder(android.R.drawable.ic_menu_gallery)
                    .error(android.R.drawable.ic_menu_gallery)
                    .into(photoView);
        }

        btnClose.setOnClickListener(v -> finish());

        // Bấm vào vùng tối (ngoài ảnh) để đóng
        photoView.setOnViewTapListener((view, x, y) -> {
            // single tap → đóng (optional, comment out nếu muốn chỉ dùng nút X)
        });
    }
}