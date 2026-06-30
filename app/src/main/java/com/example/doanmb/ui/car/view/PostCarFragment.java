package com.example.doanmb.ui.car.view;

import android.Manifest;
import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.bumptech.glide.Glide;
import com.example.doanmb.R;
import com.example.doanmb.data.model.Place;
import com.example.doanmb.core.helper.CloudinaryHelper;
import com.example.doanmb.data.remote.VietnamLocationApi;
import com.example.doanmb.ui.car.viewmodel.PostCarViewModel;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.ArrayList;
import java.util.List;

public class PostCarFragment extends Fragment {

    public interface OnPostCarSubmittedListener {
        void onPostCarSubmitted();
    }

    private EditText etCarName, etCarPrice, etCarInfo, etCarYear, etCarKm, etCarLocation;
    private Spinner spinnerBrand, spinnerType;
    private Button btnSubmitPost, btnPickImage, btnRemoveImage;
    private View layoutUploadPrompt, layoutUploadMedia;
    private ImageView ivPreview;
    private HorizontalScrollView scrollImageThumbs;
    private LinearLayout layoutImageThumbs;
    private TextView tvImageCount;
    private final List<Uri> selectedImageUris = new ArrayList<>();
    private int scrollBasePaddingBottom;

    private PostCarViewModel viewModel;

    private final ActivityResultLauncher<Intent> pickImageLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (!isViewReady()) return;
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    addImagesFromResult(result.getData());
                }
            });

    private final ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (!isViewReady()) return;
                if (isGranted) {
                    openGallery();
                } else {
                    Toast.makeText(requireContext(), "Bạn cần cấp quyền để chọn ảnh!", Toast.LENGTH_SHORT).show();
                }
            });

    private interface OnPlacePicked {
        void pick(Place place);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_post_car, container, false);

        viewModel = new ViewModelProvider(this).get(PostCarViewModel.class);

        initViews(view);
        setupKeyboardInsets(view);
        setupSpinners();
        setupActions();
        observeViewModel();

        return view;
    }

    /** observeViewModel: hiện Toast thông báo và xử lý kết quả lưu (thành công thì xoá form). */
    private void observeViewModel() {
        viewModel.getMessage().observe(getViewLifecycleOwner(), msg -> {
            if (msg != null && isViewReady()) Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show();
        });
        viewModel.getSaveResult().observe(getViewLifecycleOwner(), success -> {
            if (success == null || !isViewReady()) return;
            if (success) {
                clearForm();
                notifyPostSubmitted();
            } else {
                resetButton();
            }
        });
    }

    /** Ánh xạ các view trong layout vào biến thành viên. */
    private void initViews(View view) {
        etCarName = view.findViewById(R.id.et_car_name);
        etCarPrice = view.findViewById(R.id.et_car_price);
        etCarInfo = view.findViewById(R.id.et_car_info);
        etCarYear = view.findViewById(R.id.et_car_year);
        etCarKm = view.findViewById(R.id.et_car_km);
        etCarLocation = view.findViewById(R.id.et_car_location);
        spinnerBrand = view.findViewById(R.id.spinner_brand);
        spinnerType = view.findViewById(R.id.spinner_type);
        btnSubmitPost = view.findViewById(R.id.btn_submit_post);
        btnPickImage = view.findViewById(R.id.btn_pick_image);
        btnRemoveImage = view.findViewById(R.id.btn_remove_image);
        layoutUploadPrompt = view.findViewById(R.id.layout_upload_prompt);
        layoutUploadMedia = view.findViewById(R.id.layout_upload_media);
        ivPreview = view.findViewById(R.id.iv_car_preview);
        scrollImageThumbs = view.findViewById(R.id.scroll_image_thumbs);
        layoutImageThumbs = view.findViewById(R.id.layout_image_thumbs);
        tvImageCount = view.findViewById(R.id.tv_image_count);
    }

    /**
     * Khi bàn phím hiện, đệm thêm chiều cao IME vào đáy ScrollView và cuộn ô đang
     * nhập lên trên bàn phím — đảm bảo "Mô tả thêm"/"Khu vực" không bị che.
     */
    private void setupKeyboardInsets(View root) {
        scrollBasePaddingBottom = root.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            int imeBottom = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom;
            v.setPadding(v.getPaddingLeft(), v.getPaddingTop(), v.getPaddingRight(),
                    scrollBasePaddingBottom + imeBottom);
            if (imeBottom > 0) {
                View focused = v.findFocus();
                if (focused != null) {
                    v.post(() -> focused.requestRectangleOnScreen(
                            new Rect(0, 0, focused.getWidth(), focused.getHeight()), false));
                }
            }
            return insets;
        });
    }

    /** Gắn sự kiện cho nút chọn/xoá ảnh, nút gửi và ô "Khu vực" (mở bộ chọn địa chỉ). */
    private void setupActions() {
        btnPickImage.setOnClickListener(v -> pickImage());
        if (layoutUploadMedia != null) layoutUploadMedia.setOnClickListener(v -> pickImage());
        btnRemoveImage.setOnClickListener(v -> clearSelectedImage());
        btnSubmitPost.setOnClickListener(v -> submitPost());

        // Ô "Khu vực" hoạt động như nút chọn địa chỉ (Tỉnh/Thành → Phường/Xã) lấy từ API,
        // không cho gõ tay để tránh nhập sai định dạng.
        etCarLocation.setFocusable(false);
        etCarLocation.setClickable(true);
        etCarLocation.setOnClickListener(v -> pickProvince());
    }

    /**
     * Biến ô "Khu vực" thành nút chọn địa chỉ: bấm để chọn Tỉnh/Thành → Phường/Xã
     * lấy từ API miễn phí provinces.open-api.vn.
     */
    /** Bước 1: chọn tỉnh/thành phố. */
    private void pickProvince() {
        if (!isViewReady()) return;
        Toast.makeText(requireContext(), "Đang tải danh sách tỉnh/thành...", Toast.LENGTH_SHORT).show();
        VietnamLocationApi.fetchProvinces(new VietnamLocationApi.Callback() {
            @Override
            public void onResult(List<Place> places) {
                if (!isViewReady() || places.isEmpty()) return;
                showPickDialog("Chọn tỉnh / thành phố", places, PostCarFragment.this::pickWard);
            }

            @Override
            public void onError(String message) {
                if (!isViewReady()) return;
                Toast.makeText(requireContext(), "Không tải được danh sách", Toast.LENGTH_SHORT).show();
            }
        });
    }
    /** Bước 2: chọn phường/xã trong tỉnh đã chọn. */
    private void pickWard(Place province) {
        if (!isViewReady()) return;
        VietnamLocationApi.fetchWards(province.code, new VietnamLocationApi.Callback() {
            @Override
            public void onResult(List<Place> wards) {
                if (!isViewReady()) return;
                if (wards.isEmpty()) {
                    etCarLocation.setText(province.name);
                    return;
                }
                showPickDialog("Chọn phường / xã", wards,
                        ward -> etCarLocation.setText(ward.name + ", " + province.name));
            }
            @Override
            public void onError(String message) {
                if (!isViewReady()) return;
                // Không lấy được phường/xã: dùng tạm tên tỉnh để không chặn người dùng.
                etCarLocation.setText(province.name);
            }
        });
    }

    /** Hộp thoại danh sách địa danh chung dùng cho cả bước chọn tỉnh và chọn phường/xã. */
    private void showPickDialog(String title, List<Place> places, OnPlacePicked cb) {
        if (!isViewReady()) return;
        String[] items = new String[places.size()];
        for (int i = 0; i < places.size(); i++) items[i] = places.get(i).name;
        new AlertDialog.Builder(requireContext())
                .setTitle(title)
                .setItems(items, (d, which) -> cb.pick(places.get(which)))
                .setNegativeButton("Huỷ", null)
                .show();
    }
    /** Lấy ảnh từ kết quả gallery (một hoặc nhiều ảnh), bỏ trùng rồi làm mới preview. */
    private void addImagesFromResult(Intent data) {
        ClipData clip = data.getClipData();
        if (clip != null) {
            for (int i = 0; i < clip.getItemCount(); i++) {
                Uri uri = clip.getItemAt(i).getUri();
                if (uri != null && !selectedImageUris.contains(uri)) selectedImageUris.add(uri);
            }
        } else if (data.getData() != null) {
            Uri uri = data.getData();
            if (!selectedImageUris.contains(uri)) selectedImageUris.add(uri);
        }
        refreshImagePreview();
    }

    /** Cập nhật khu vực ảnh: hiện ảnh đầu làm preview, số lượng ảnh và nút xoá; rỗng thì hiện gợi ý tải lên. */
    private void refreshImagePreview() {
        if (!isViewReady()) return;
        boolean hasImages = !selectedImageUris.isEmpty();

        if (hasImages) {
            Glide.with(this).load(selectedImageUris.get(0)).into(ivPreview);
            ivPreview.setVisibility(View.VISIBLE);
            layoutUploadPrompt.setVisibility(View.GONE);
        } else {
            ivPreview.setImageDrawable(null);
            ivPreview.setVisibility(View.GONE);
            layoutUploadPrompt.setVisibility(View.VISIBLE);
        }

        btnRemoveImage.setVisibility(hasImages ? View.VISIBLE : View.GONE);
        tvImageCount.setVisibility(hasImages ? View.VISIBLE : View.GONE);
        tvImageCount.setText("Đã chọn " + selectedImageUris.size() + " ảnh");
        buildThumbnails();
    }

    /** Dựng dải thumbnail cho các ảnh đã chọn, mỗi ảnh kèm nút "✕" để xoá riêng. */
    private void buildThumbnails() {
        layoutImageThumbs.removeAllViews();
        scrollImageThumbs.setVisibility(selectedImageUris.isEmpty() ? View.GONE : View.VISIBLE);

        int size = dp(84);
        int margin = dp(8);
        for (int i = 0; i < selectedImageUris.size(); i++) {
            final Uri uri = selectedImageUris.get(i);

            FrameLayout holder = new FrameLayout(requireContext());
            LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(size, size);
            hp.setMarginEnd(margin);
            holder.setLayoutParams(hp);

            CardView card = new CardView(requireContext());
            card.setLayoutParams(new FrameLayout.LayoutParams(size, size));
            card.setRadius(dp(10));
            card.setCardElevation(0f);

            ImageView img = new ImageView(requireContext());
            img.setLayoutParams(new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            img.setScaleType(ImageView.ScaleType.CENTER_CROP);
            Glide.with(this).load(uri).into(img);
            card.addView(img);
            holder.addView(card);

            TextView remove = new TextView(requireContext());
            int rs = dp(22);
            FrameLayout.LayoutParams rp = new FrameLayout.LayoutParams(rs, rs);
            rp.gravity = Gravity.TOP | Gravity.END;
            remove.setLayoutParams(rp);
            remove.setText("✕");
            remove.setGravity(Gravity.CENTER);
            remove.setTextColor(0xFFFFFFFF);
            remove.setTextSize(12);
            remove.setBackgroundColor(0x99000000);
            remove.setOnClickListener(v -> {
                selectedImageUris.remove(uri);
                refreshImagePreview();
            });
            holder.addView(remove);

            layoutImageThumbs.addView(holder);
        }
    }

    /** Đổi dp sang px theo mật độ màn hình hiện tại. */
    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    /** Nạp dữ liệu cho spinner hãng xe và loại tin (Cần Bán / Cho Thuê). */
    private void setupSpinners() {
        String[] brands = {"Toyota", "Honda", "Mazda", "Kia", "Ford", "Hyundai", "VinFast", "Mitsubishi", "Suzuki", "Nissan", "Mercedes", "BMW", "Audi", "Khác"};
        spinnerBrand.setAdapter(makeAdapter(brands));

        String[] types = {"Cần Bán", "Cho Thuê"};
        spinnerType.setAdapter(makeAdapter(types));

    }

    /** Tạo ArrayAdapter chuẩn cho spinner từ mảng chuỗi. */
    private ArrayAdapter<String> makeAdapter(String[] items) {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_item, items);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        return adapter;
    }

    /** Kiểm tra/ xin quyền đọc ảnh theo phiên bản Android rồi mở gallery. */
    private void pickImage() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED) {
                openGallery();
            } else {
                requestPermissionLauncher.launch(Manifest.permission.READ_MEDIA_IMAGES);
            }
        } else {
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
                openGallery();
            } else {
                requestPermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE);
            }
        }
    }

    /**
     * Thu thập dữ liệu form, validate (bắt buộc tên + giá, phải đăng nhập), ghép phần
     * mô tả đầy đủ, upload ảnh lên Cloudinary (nếu có) rồi gọi ViewModel lưu xe.
     */
    private void submitPost() {
        String name = etCarName.getText().toString().trim();
        String price = etCarPrice.getText().toString().trim();
        String info = etCarInfo.getText().toString().trim();
        String year = etCarYear.getText().toString().trim();
        String km = etCarKm.getText().toString().trim();
        String location = etCarLocation.getText().toString().trim();
        String brand = spinnerBrand.getSelectedItem().toString();
        String typeLabel = spinnerType.getSelectedItem().toString();
        String type = typeLabel.equals("Cần Bán") ? "sale" : "rental";

        if (name.isEmpty() || price.isEmpty()) {
            Toast.makeText(requireContext(), "Vui lòng nhập tên xe và giá!", Toast.LENGTH_SHORT).show();
            return;
        }

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            Toast.makeText(requireContext(), "Vui lòng đăng nhập trước!", Toast.LENGTH_SHORT).show();
            return;
        }

        String fullInfo = brand
                + (year.isEmpty() ? "" : " • " + year)
                + (km.isEmpty() ? "" : " • " + km + " km")
                + (location.isEmpty() ? "" : " • " + location)
                + (info.isEmpty() ? "" : "\n" + info);

        btnSubmitPost.setEnabled(false);
        btnSubmitPost.setText("Đang đăng...");

        if (!selectedImageUris.isEmpty()) {
            CloudinaryHelper.uploadImages(
                    requireContext().getApplicationContext(),
                    new ArrayList<>(selectedImageUris),
                    new CloudinaryHelper.OnMultiUploadCallback() {
                        @Override
                        public void onSuccess(List<String> imageUrls) {
                            viewModel.saveCar(name, price, fullInfo, type, brand,
                                    year, km, location, imageUrls);
                        }

                        @Override
                        public void onFailure(String error) {
                            if (!isViewReady()) return;
                            Toast.makeText(requireContext(), "Lỗi upload ảnh: " + error, Toast.LENGTH_SHORT).show();
                            resetButton();
                        }
                    }
            );
        } else {
            viewModel.saveCar(name, price, fullInfo, type, brand,
                    year, km, location, new ArrayList<>());
        }
    }

    /** Xoá toàn bộ dữ liệu form sau khi đăng tin thành công. */
    private void clearForm() {
        etCarName.setText("");
        etCarPrice.setText("");
        etCarInfo.setText("");
        etCarYear.setText("");
        etCarKm.setText("");
        etCarLocation.setText("");
        spinnerBrand.setSelection(0);
        spinnerType.setSelection(0);
        clearSelectedImage();
        resetButton();
    }

    /** Bỏ chọn toàn bộ ảnh và làm mới preview. */
    private void clearSelectedImage() {
        selectedImageUris.clear();
        refreshImagePreview();
    }

    /** Khôi phục nút gửi về trạng thái bật và nhãn mặc định. */
    private void resetButton() {
        btnSubmitPost.setEnabled(true);
        btnSubmitPost.setText("Gửi yêu cầu đăng tin");
    }

    /** Báo cho Fragment cha (Category) biết đã đăng tin xong để nạp lại danh sách. */
    private void notifyPostSubmitted() {
        Fragment parent = getParentFragment();
        if (parent instanceof OnPostCarSubmittedListener) {
            ((OnPostCarSubmittedListener) parent).onPostCarSubmitted();
        }
    }

    /** Mở gallery cho phép chọn nhiều ảnh. */
    private void openGallery() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        pickImageLauncher.launch(intent);
    }

    /** @return true nếu Fragment còn gắn và view sẵn sàng — tránh thao tác UI sau khi thoát. */
    private boolean isViewReady() {
        return isAdded() && getView() != null && btnSubmitPost != null;
    }

    /** Giải phóng tham chiếu view khi huỷ để tránh rò rỉ bộ nhớ. */
    @Override
    public void onDestroyView() {
        super.onDestroyView();
        etCarName = null;
        etCarPrice = null;
        etCarInfo = null;
        etCarYear = null;
        etCarKm = null;
        etCarLocation = null;
        spinnerBrand = null;
        spinnerType = null;
        btnSubmitPost = null;
        btnPickImage = null;
        btnRemoveImage = null;
        layoutUploadPrompt = null;
        layoutUploadMedia = null;
        ivPreview = null;
        scrollImageThumbs = null;
        layoutImageThumbs = null;
        tvImageCount = null;
    }
}