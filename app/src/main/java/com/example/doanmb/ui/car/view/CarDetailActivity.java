package com.example.doanmb.ui.car.view;

import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.core.widget.NestedScrollView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.PagerSnapHelper;
import androidx.recyclerview.widget.RecyclerView;

import com.example.doanmb.R;
import com.example.doanmb.core.service.OrderReminderService;
import com.example.doanmb.ui.car.adapter.CarImageAdapter;
import com.example.doanmb.data.repository.WalletRepository;
import com.example.doanmb.data.model.Car;
import com.example.doanmb.core.helper.ChatNotificationHelper;
import com.example.doanmb.data.repository.FavoriteRepository;
import com.example.doanmb.ui.chat.view.ChatDetailActivity;
import com.example.doanmb.ui.auth.view.LoginActivity;
import com.example.doanmb.core.util.ImageLoader;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CarDetailActivity extends AppCompatActivity {

    private ImageView ivCarDetail;
    private RecyclerView rvCarImages;
    private LinearLayout layoutImageDots;
    private CarImageAdapter imageAdapter;
    private TextView tvCarName, tvCarPrice, tvCarInfo, tvDetailTitle;
    private TextView tvCarTypeBadge, tvCarFuelBadge, tvCarConditionBadge;
    private TextView tvSellerName, tvSellerPhone, tvOwnerNote;

    // Form mua xe
    private LinearLayout layoutBuyForm;
    private EditText etBuyerNote;
    private Button btnSendRequest, btnCallSeller, btnChatSeller;

    // Form thuê xe
    private LinearLayout layoutRentForm;
    private EditText etRenterName, etRenterPhone, etRenterCCCD;
    private EditText etRentStartDate, etRentDays, etRenterNote;
    private Button btnSendRentRequest, btnCallRentSeller, btnChatRentSeller;
    private android.widget.RadioGroup rgPaymentMethod;
    private TextView tvDepositInfo;
    private String sellerName = "";
    private long walletBalance = 0L;

    // Đặt theo ngày / theo chuyến (xe có tài xế)
    private com.google.android.material.button.MaterialButtonToggleGroup toggleBookMode;
    private View layoutDayFields, layoutTripFields;
    private Button btnPickOnMap;
    private TextView tvTripSummary;
    private long pricePerDay = 0L;
    private long pricePerKm  = 0L;
    private boolean tripMode = false;
    private String tripPickup = "", tripDest = "";
    private double tripDistanceKm = 0d;

    private final androidx.activity.result.ActivityResultLauncher<Intent> mapLauncher =
            registerForActivityResult(
                    new androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        if (result.getResultCode() != RESULT_OK || result.getData() == null) return;
                        Intent d = result.getData();
                        tripPickup = d.getStringExtra(MapPickerActivity.RESULT_PICKUP);
                        tripDest = d.getStringExtra(MapPickerActivity.RESULT_DEST);
                        tripDistanceKm = d.getDoubleExtra(MapPickerActivity.RESULT_DISTANCE_KM, 0d);
                        updateTripSummary();
                    });

    private TextView tvReportCar;
    private ImageView btnMenuDetail;
    private FirebaseFirestore db;
    private String sellerPhone = "";
    private Car car;
    private String carId, sellerId, carType;
    private String carStatus = "";
    private String statusBeforeHide = "";

    private NestedScrollView detailScroll;
    private View imageHero, headerDetail, btnBackFloat, floatTopBar;
    private ImageView btnFavoriteFloat, ivFavoriteDetail, btnMenuFloat;
    private boolean isFav = false;
    private boolean statusBarDarkIcons = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_car_detail);

        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        getWindow().setStatusBarColor(android.graphics.Color.TRANSPARENT);

        db = FirebaseFirestore.getInstance();
        initViews();
        setupDetailHeader();

        car = (Car) getIntent().getSerializableExtra("CAR_DATA");
        carId = getIntent().getStringExtra("CAR_ID");
        sellerId = getIntent().getStringExtra("SELLER_ID");
        carType = getIntent().getStringExtra("CAR_TYPE");

        String pickupTime = getIntent().getStringExtra("PICKUP_TIME");
        if (pickupTime != null && !pickupTime.isEmpty() && etRentStartDate != null) {
            etRentStartDate.setText(pickupTime);
        }

        tvCarFuelBadge.setVisibility(View.GONE);
        tvCarConditionBadge.setVisibility(View.GONE);

        if (car != null) {
            List<String> coverImages = new ArrayList<>();
            if (car.getImageUrl() != null && !car.getImageUrl().isEmpty()) {
                coverImages.add(car.getImageUrl());
            }
            showImages(coverImages);
            tvCarName.setText(car.getName());
            if (tvDetailTitle != null) tvDetailTitle.setText(car.getName());
            tvCarPrice.setText(car.getPrice());
            tvCarInfo.setText(car.getInfo());
        }

        if ((carId == null || carId.isEmpty()) && car != null && car.getId() != null) {
            carId = car.getId();
        }
        if ((sellerId == null || sellerId.isEmpty()) && car != null && car.getSellerId() != null) {
            sellerId = car.getSellerId();
        }

        if (carId != null && !carId.isEmpty()) {
            loadCarDetail(carId);
        }

        if (sellerId != null && !sellerId.isEmpty()) {
            loadSellerInfo(sellerId);
        } else {
            tvSellerName.setText("👤  Chưa có thông tin");
            tvSellerPhone.setText("📞  Chưa có thông tin");
            setupByType(carType);
        }

        setupButtons();
        setupRentDepositUi();
        setupBookModeListeners();
        loadFavoriteState();

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Chi tiết xe");
        }
    }

    private void initViews() {
        ivCarDetail       = findViewById(R.id.ivCarDetail);
        rvCarImages       = findViewById(R.id.rv_car_images);
        layoutImageDots   = findViewById(R.id.layout_image_dots);
        setupImagePager();
        tvCarName         = findViewById(R.id.tvCarNameDetail);
        tvCarPrice        = findViewById(R.id.tvCarPriceDetail);
        tvCarInfo         = findViewById(R.id.tvCarInfoDetail);
        tvCarTypeBadge    = findViewById(R.id.tvCarTypeBadge);
        tvCarFuelBadge    = findViewById(R.id.tvCarFuelBadge);
        tvCarConditionBadge = findViewById(R.id.tvCarConditionBadge);
        tvSellerName = findViewById(R.id.tvSellerName);
        tvSellerPhone = findViewById(R.id.tvSellerPhone);
        tvReportCar = findViewById(R.id.tv_report_car);
        tvOwnerNote = findViewById(R.id.tvOwnerNote);
        btnMenuDetail = findViewById(R.id.btn_menu_detail);

        layoutBuyForm = findViewById(R.id.layoutBuyForm);
        etBuyerNote = findViewById(R.id.etBuyerNote);
        btnSendRequest = findViewById(R.id.btnSendRequest);
        btnCallSeller = findViewById(R.id.btnCallSeller);
        btnChatSeller = findViewById(R.id.btnChatSeller);

        layoutRentForm = findViewById(R.id.layoutRentForm);
        etRenterName = findViewById(R.id.etRenterName);
        etRenterPhone = findViewById(R.id.etRenterPhone);
        etRenterCCCD = findViewById(R.id.etRenterCCCD);
        etRentStartDate = findViewById(R.id.etRentStartDate);
        etRentDays = findViewById(R.id.etRentDays);
        etRenterNote = findViewById(R.id.etRenterNote);
        btnSendRentRequest = findViewById(R.id.btnSendRentRequest);
        btnCallRentSeller  = findViewById(R.id.btnCallRentSeller);
        btnChatRentSeller = findViewById(R.id.btnChatRentSeller);
        rgPaymentMethod = findViewById(R.id.rg_payment_method);
        tvDepositInfo   = findViewById(R.id.tv_deposit_info);

        toggleBookMode  = findViewById(R.id.toggle_book_mode);
        layoutDayFields = findViewById(R.id.layout_day_fields);
        layoutTripFields= findViewById(R.id.layout_trip_fields);
        btnPickOnMap    = findViewById(R.id.btnPickOnMap);
        tvTripSummary   = findViewById(R.id.tvTripSummary);
    }

    private void setupDetailHeader() {
        detailScroll     = findViewById(R.id.detail_scroll);
        imageHero        = findViewById(R.id.image_hero);
        headerDetail     = findViewById(R.id.header_detail);
        btnBackFloat     = findViewById(R.id.btn_back_float);
        floatTopBar      = findViewById(R.id.float_top_bar);
        btnFavoriteFloat = findViewById(R.id.btn_favorite_float);
        btnMenuFloat     = findViewById(R.id.btn_menu_float);
        ivFavoriteDetail = findViewById(R.id.iv_favorite_detail);
        tvDetailTitle    = findViewById(R.id.tv_detail_title);

        View btnBack = findViewById(R.id.btn_back_detail);
        if (btnBack != null) btnBack.setOnClickListener(v -> finish());
        if (btnBackFloat != null) btnBackFloat.setOnClickListener(v -> finish());

        if (btnFavoriteFloat != null) btnFavoriteFloat.setOnClickListener(v -> toggleFavorite());
        if (ivFavoriteDetail != null) ivFavoriteDetail.setOnClickListener(v -> toggleFavorite());
        updateFavoriteIcons();

        if (headerDetail != null) {
            final int baseTop = headerDetail.getPaddingTop();
            ViewCompat.setOnApplyWindowInsetsListener(headerDetail, (v, insets) -> {
                int top = insets.getInsets(WindowInsetsCompat.Type.systemBars()).top;
                v.setPadding(v.getPaddingLeft(), baseTop + top, v.getPaddingRight(), v.getPaddingBottom());
                return insets;
            });
        }

        if (floatTopBar != null) {
            final int baseTopPad = floatTopBar.getPaddingTop();
            ViewCompat.setOnApplyWindowInsetsListener(floatTopBar, (v, insets) -> {
                int top = insets.getInsets(WindowInsetsCompat.Type.systemBars()).top;
                v.setPadding(v.getPaddingLeft(), baseTopPad + top, v.getPaddingRight(), v.getPaddingBottom());
                return insets;
            });
        }

        if (detailScroll != null) {
            final int basePad = detailScroll.getPaddingBottom();
            ViewCompat.setOnApplyWindowInsetsListener(detailScroll, (v, insets) -> {
                int bottom = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom;
                v.setPadding(v.getPaddingLeft(), v.getPaddingTop(), v.getPaddingRight(), basePad + bottom);
                return insets;
            });
        }

        if (headerDetail != null) headerDetail.setVisibility(View.INVISIBLE);
        setStatusBarDarkIcons(false);

        if (detailScroll != null) {
            detailScroll.setOnScrollChangeListener((NestedScrollView.OnScrollChangeListener)
                    (v, x, y, ox, oy) -> updateDetailHeader(y));
        }
    }

    private void updateDetailHeader(int scrollY) {
        if (headerDetail == null || imageHero == null || floatTopBar == null) return;

        int trigger = imageHero.getHeight() - headerDetail.getHeight();
        if (trigger <= 0) trigger = dp(180);

        float p = clamp01(scrollY / (float) trigger);

        headerDetail.setAlpha(p);
        headerDetail.setVisibility(p <= 0.01f ? View.INVISIBLE : View.VISIBLE);

        floatTopBar.setAlpha(1f - p);
        floatTopBar.setVisibility(p >= 0.99f ? View.INVISIBLE : View.VISIBLE);

        setStatusBarDarkIcons(p >= 0.5f);
    }

    private void updateFavoriteIcons() {
        int icon = isFav ? R.drawable.ic_heart_filled : R.drawable.ic_heart_outline;
        if (btnFavoriteFloat != null) {
            btnFavoriteFloat.setImageResource(icon);
            btnFavoriteFloat.clearColorFilter();
        }
        if (ivFavoriteDetail != null) {
            ivFavoriteDetail.setImageResource(icon);
            if (isFav) ivFavoriteDetail.clearColorFilter();
            else ivFavoriteDetail.setColorFilter(0xFF1A1A2E);
        }
    }

    private void loadFavoriteState() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null || carId == null || carId.isEmpty()) return;
        FavoriteRepository.contains(user.getUid(), carId, fav -> {
            isFav = fav;
            updateFavoriteIcons();
        });
    }

    private void toggleFavorite() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            Toast.makeText(this, "Đăng nhập để lưu xe yêu thích", Toast.LENGTH_SHORT).show();
            return;
        }
        if (carId == null || carId.isEmpty()) return;
        isFav = !isFav;
        if (isFav) FavoriteRepository.add(user.getUid(), carId);
        else FavoriteRepository.remove(user.getUid(), carId);
        updateFavoriteIcons();
    }

    private void setStatusBarDarkIcons(boolean dark) {
        if (statusBarDarkIcons == dark) return;
        statusBarDarkIcons = dark;
        WindowInsetsControllerCompat c =
                WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        c.setAppearanceLightStatusBars(dark);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private float clamp01(float value) {
        if (value < 0f) return 0f;
        if (value > 1f) return 1f;
        return value;
    }

    private void setupImagePager() {
        if (rvCarImages == null) return;
        imageAdapter = new CarImageAdapter();
        rvCarImages.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        rvCarImages.setAdapter(imageAdapter);
        new PagerSnapHelper().attachToRecyclerView(rvCarImages);

        rvCarImages.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
                LinearLayoutManager lm = (LinearLayoutManager) rv.getLayoutManager();
                if (lm == null) return;
                int pos = lm.findFirstCompletelyVisibleItemPosition();
                if (pos == RecyclerView.NO_POSITION) pos = lm.findFirstVisibleItemPosition();
                updateDots(pos);
            }
        });
    }

    private void showImages(List<String> images) {
        if (imageAdapter == null) return;
        imageAdapter.setImages(images);
        buildDots(images.size());
        updateDots(0);
    }

    private void buildDots(int count) {
        if (layoutImageDots == null) return;
        layoutImageDots.removeAllViews();
        if (count <= 1) return;

        int size = Math.round(7 * getResources().getDisplayMetrics().density);
        int margin = Math.round(3 * getResources().getDisplayMetrics().density);
        for (int i = 0; i < count; i++) {
            View dot = new View(this);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
            lp.setMargins(margin, 0, margin, 0);
            dot.setLayoutParams(lp);
            dot.setBackgroundResource(R.drawable.bg_image_dot);
            layoutImageDots.addView(dot);
        }
    }

    private void updateDots(int activePos) {
        if (layoutImageDots == null) return;
        for (int i = 0; i < layoutImageDots.getChildCount(); i++) {
            View dot = layoutImageDots.getChildAt(i);
            dot.setAlpha(i == activePos ? 1f : 0.4f);
            dot.setScaleX(i == activePos ? 1.25f : 1f);
            dot.setScaleY(i == activePos ? 1.25f : 1f);
        }
    }

    private static List<String> extractImageUrls(Object raw) {
        List<String> urls = new ArrayList<>();
        if (raw instanceof List) {
            for (Object item : (List<?>) raw) {
                if (item != null && !item.toString().isEmpty()) urls.add(item.toString());
            }
        }
        return urls;
    }

    private void setupButtons() {
        btnSendRequest.setOnClickListener(v -> sendBuyRequest());
        btnCallSeller.setOnClickListener(v -> callSeller());
        btnChatSeller.setOnClickListener(v -> openChat());

        btnSendRentRequest.setOnClickListener(v -> sendRentRequest());
        btnCallRentSeller.setOnClickListener(v -> callSeller());
        btnChatRentSeller.setOnClickListener(v -> openChat());
    }

    private void setupRentDepositUi() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            db.collection("users").document(user.getUid()).get()
                    .addOnSuccessListener(doc -> {
                        Double b = doc.getDouble("balance");
                        walletBalance = b != null ? Math.round(b) : 0L;
                        updateDepositInfo();
                    });
        }
        if (etRentDays != null) {
            etRentDays.addTextChangedListener(new android.text.TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
                @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
                @Override public void afterTextChanged(android.text.Editable s) { updateDepositInfo(); }
            });
        }
    }

    private void setupBookModeListeners() {
        if (toggleBookMode != null) {
            toggleBookMode.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
                if (!isChecked) return;
                tripMode = checkedId == R.id.btn_book_trip;
                applyBookMode();
            });
        }
        if (btnPickOnMap != null) {
            btnPickOnMap.setOnClickListener(v ->
                    mapLauncher.launch(new Intent(this, MapPickerActivity.class)));
        }
    }

    private void configureBookMode(boolean driver) {
        boolean allowTrip = driver && pricePerKm > 0;
        if (toggleBookMode != null) {
            toggleBookMode.setVisibility(allowTrip ? View.VISIBLE : View.GONE);
            if (allowTrip && toggleBookMode.getCheckedButtonId() == View.NO_ID) {
                toggleBookMode.check(R.id.btn_book_day);
            }
        }
        tripMode = false;
        applyBookMode();
    }

    private void applyBookMode() {
        if (layoutDayFields != null) layoutDayFields.setVisibility(tripMode ? View.GONE : View.VISIBLE);
        if (layoutTripFields != null) layoutTripFields.setVisibility(tripMode ? View.VISIBLE : View.GONE);
        if (btnSendRentRequest != null) {
            btnSendRentRequest.setText(tripMode ? "ĐẶT CHUYẾN" : "GỬI YÊU CẦU THUÊ XE");
        }
        if (tripMode) updateTripSummary();
        else updateDepositInfo();
    }

    private void updateTripSummary() {
        if (tvTripSummary == null) return;
        if (tripDistanceKm <= 0 || tripPickup == null || tripDest == null) {
            tvTripSummary.setText("Chưa chọn điểm đón/đến.");
            return;
        }
        long total = Math.round(tripDistanceKm * pricePerKm);
        tvTripSummary.setText("🟢 Đón: " + tripPickup
                + "\n🔴 Đến: " + tripDest
                + "\nQuãng đường: " + tripDistanceKm + " km × " + money(pricePerKm) + " đ/km"
                + "\nTổng tiền chuyến: " + money(total) + " đ (thanh toán tiền mặt)");
    }

    private void updateDepositInfo() {
        if (tvDepositInfo == null) return;
        long pricePerDay = parseMoney(car != null ? car.getPrice() : null);
        int days = parseDays(etRentDays != null ? etRentDays.getText().toString() : "");

        if (pricePerDay <= 0 || days <= 0) {
            tvDepositInfo.setText("Nhập số ngày thuê để xem tiền cọc.\nSố dư ví: " + money(walletBalance) + " đ");
            return;
        }

        long total = pricePerDay * days;
        StringBuilder sb = new StringBuilder();
        sb.append("Tổng tiền thuê (").append(days).append(" ngày): ").append(money(total)).append(" đ\n");
        if (WalletRepository.requiresDeposit(days)) {
            long deposit = WalletRepository.deposit(total);
            sb.append("Đặt cọc giữ xe (50%, trừ vào ví): ").append(money(deposit)).append(" đ\n");
            sb.append("Số dư ví hiện tại: ").append(money(walletBalance)).append(" đ");
            if (walletBalance < deposit) sb.append("\n⚠️ Số dư không đủ — vui lòng nhờ admin nạp tiền.");
        } else {
            sb.append("Đơn ngắn ngày: không cần đặt cọc, thanh toán khi nhận xe.\n");
            sb.append("Số dư ví: ").append(money(walletBalance)).append(" đ");
        }
        tvDepositInfo.setText(sb.toString());
    }

    private static long parseMoney(String s) {
        if (s == null) return 0;
        String d = s.replaceAll("[^0-9]", "");
        if (d.isEmpty()) return 0;
        try { return Long.parseLong(d); } catch (NumberFormatException e) { return 0; }
    }

    private static int parseDays(String s) {
        if (s == null) return 0;
        String d = s.replaceAll("[^0-9]", "");
        if (d.isEmpty()) return 0;
        try { return Integer.parseInt(d); } catch (NumberFormatException e) { return 0; }
    }

    private static String money(long amount) {
        return java.text.NumberFormat.getInstance(new java.util.Locale("vi", "VN")).format(amount);
    }

    private void openChat() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            Toast.makeText(this, "Vui lòng đăng nhập để nhắn tin!", Toast.LENGTH_SHORT).show();
            startActivity(new Intent(this, LoginActivity.class));
            return;
        }

        if (sellerId == null || sellerId.isEmpty()) {
            Toast.makeText(this, "Không tìm thấy thông tin người bán", Toast.LENGTH_SHORT).show();
            return;
        }

        String roomId = user.getUid() + "_" + sellerId + "_" + (carId != null ? carId : "unknown");

        Map<String, Object> roomData = new HashMap<>();
        List<String> participants = new ArrayList<>();
        participants.add(user.getUid());
        participants.add(sellerId);

        roomData.put("participants", participants);
        roomData.put("carId", carId != null ? carId : "");
        roomData.put("carName", car != null ? car.getName() : "Xe");
        roomData.put("carPrice", car != null ? car.getPrice() : "");
        roomData.put("carImage", car != null ? car.getImageUrl() : "");
        roomData.put("carType", carType != null ? carType : "sale");
        roomData.put("buyerId", user.getUid());
        roomData.put("sellerId", sellerId);

        db.collection("chat_rooms").document(roomId)
                .set(roomData, SetOptions.merge())
                .addOnSuccessListener(aVoid -> {
                    Intent intent = new Intent(this, ChatDetailActivity.class);
                    intent.putExtra("ROOM_ID", roomId);
                    intent.putExtra("PARTNER_ID", sellerId);
                    intent.putExtra("PARTNER_NAME", tvSellerName.getText().toString().replace("👤  ", ""));
                    intent.putExtra("CAR_DATA", car);
                    startActivity(intent);
                });
    }

    private void callSeller() {
        if (!sellerPhone.isEmpty()) {
            Intent intent = new Intent(Intent.ACTION_DIAL);
            intent.setData(Uri.parse("tel:" + sellerPhone));
            startActivity(intent);
        } else {
            Toast.makeText(this, "Không có số điện thoại người bán!", Toast.LENGTH_SHORT).show();
        }
    }

    private void loadCarDetail(String id) {
        db.collection("cars").document(id).get()
                .addOnSuccessListener(doc -> {
                    if (!doc.exists()) return;

                    String fuel      = doc.getString("fuel");
                    String condition = doc.getString("condition");
                    String type      = doc.getString("type");
                    String sPhone    = doc.getString("sellerPhone");
                    String sName     = doc.getString("sellerName");
                    String imageUrl  = doc.getString("imageUrl");

                    Long ppd = doc.getLong("pricePerDay");
                    Long ppk = doc.getLong("pricePerKm");
                    pricePerDay = ppd != null ? ppd : parseMoney(doc.getString("price"));
                    pricePerKm  = ppk != null ? ppk : 0L;

                    if (type != null) carType = type;
                    String status = doc.getString("status");
                    carStatus = status != null ? status : "";
                    String prevStatus = doc.getString("statusBeforeHide");
                    statusBeforeHide = prevStatus != null ? prevStatus : "";

                    List<String> imgs = extractImageUrls(doc.get("imageUrls"));
                    if (imgs.isEmpty() && imageUrl != null && !imageUrl.isEmpty()) {
                        imgs.add(imageUrl);
                    }
                    if (!imgs.isEmpty()) {
                        showImages(imgs);
                        for (String u : imgs) ImageLoader.preload(getApplicationContext(), u);
                    }

                    if (fuel != null && !fuel.isEmpty()) {
                        tvCarFuelBadge.setText(fuel);
                        tvCarFuelBadge.setVisibility(View.VISIBLE);
                    }

                    if (condition != null && !condition.isEmpty()) {
                        if (condition.contains("mới 100") || condition.equalsIgnoreCase("Xe mới 100%"))
                            tvCarConditionBadge.setText("Xe mới");
                        else
                            tvCarConditionBadge.setText("Xe cũ");
                        tvCarConditionBadge.setVisibility(View.VISIBLE);
                    }

                    if (sName != null && !sName.isEmpty()) {
                        sellerName = sName;
                        tvSellerName.setText("👤  " + sName);
                    }
                    if (sPhone != null && !sPhone.isEmpty()) {
                        sellerPhone = sPhone;
                        tvSellerPhone.setText("📞  " + sellerPhone);
                    }

                    setupByType(type != null ? type : carType);
                });
    }

    private void loadSellerInfo(String uid) {
        db.collection("users").document(uid).get()
                .addOnSuccessListener(doc -> {
                    if (!doc.exists()) return;
                    String name  = doc.getString("name");
                    String phone = doc.getString("phone");
                    if (name != null && !name.isEmpty()) sellerName = name;
                    if (phone != null && !phone.isEmpty()) sellerPhone = phone;
                    tvSellerName.setText("👤  " + (name != null ? name : "Không rõ"));
                    tvSellerPhone.setText("📞  " + (sellerPhone.isEmpty() ? "Không rõ" : sellerPhone));
                });
    }

    private void setupByType(String type) {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        boolean isOwner = currentUser != null && currentUser.getUid().equals(sellerId);

        boolean driver = isDriverType(type);
        boolean rental = isRentalType(type);

        if (isOwner) {
            layoutBuyForm.setVisibility(View.GONE);
            layoutRentForm.setVisibility(View.GONE);
            if (tvReportCar != null) tvReportCar.setVisibility(View.GONE);
            applyTypeBadge(driver, rental);
            if (tvOwnerNote != null) tvOwnerNote.setVisibility(View.VISIBLE);
            if (btnMenuDetail != null) {
                btnMenuDetail.setVisibility(View.VISIBLE);
                btnMenuDetail.setOnClickListener(v -> showOwnerMenu(btnMenuDetail));
            }
            if (btnMenuFloat != null) {
                btnMenuFloat.setVisibility(View.VISIBLE);
                btnMenuFloat.setOnClickListener(v -> showOwnerMenu(btnMenuFloat));
            }
            return;
        }

        if (btnMenuDetail != null) btnMenuDetail.setVisibility(View.GONE);
        if (btnMenuFloat != null) btnMenuFloat.setVisibility(View.GONE);
        if (tvOwnerNote != null) tvOwnerNote.setVisibility(View.GONE);
        if (tvReportCar != null) {
            tvReportCar.setVisibility(View.VISIBLE);
            tvReportCar.setOnClickListener(v -> showReportDialog());
        }

        applyTypeBadge(driver, rental);

        if (driver || rental) {
            layoutBuyForm.setVisibility(View.GONE);
            layoutRentForm.setVisibility(View.VISIBLE);
            configureBookMode(driver);
        } else {
            layoutBuyForm.setVisibility(View.VISIBLE);
            layoutRentForm.setVisibility(View.GONE);
        }
    }

    private void applyTypeBadge(boolean driver, boolean rental) {
        if (driver) {
            tvCarTypeBadge.setText("Có tài xế");
            tvCarTypeBadge.setBackgroundColor(0xFF00897B);
        } else if (rental) {
            tvCarTypeBadge.setText("Cho Thuê");
            tvCarTypeBadge.setBackgroundColor(0xFF1976D2);
        } else {
            tvCarTypeBadge.setText("Cần Bán");
            tvCarTypeBadge.setBackgroundColor(0xFF4CAF50);
        }
    }

    private static boolean isDriverType(String type) {
        if (type == null) return false;
        String t = type.toLowerCase();
        return t.contains("driver") || t.contains("tai xe") || t.contains("tài xế");
    }

    private static boolean isRentalType(String type) {
        if (type == null) return false;
        String t = type.toLowerCase();
        return t.contains("rental") || t.contains("rent") || t.contains("thue") || t.contains("thuê") || t.contains("tu lai");
    }

    private void showOwnerMenu(View anchor) {
        boolean isHidden = "hidden".equals(carStatus);
        androidx.appcompat.widget.PopupMenu menu =
                new androidx.appcompat.widget.PopupMenu(this, anchor);
        menu.getMenu().add(0, 1, 0, "✏️  Chỉnh sửa bài viết");
        menu.getMenu().add(0, 2, 1, isHidden ? "👁  Hiện bài viết" : "🙈  Ẩn bài viết");
        menu.getMenu().add(0, 3, 2, "🗑  Xóa bài viết");
        menu.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case 1: showEditPostDialog(); return true;
                case 2: toggleHidePost();     return true;
                case 3: confirmDeletePost();  return true;
                default: return false;
            }
        });
        menu.show();
    }

    private void showEditPostDialog() {
        if (carId == null || carId.isEmpty()) return;

        int pad = Math.round(20 * getResources().getDisplayMetrics().density);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(pad, pad / 2, pad, 0);

        final EditText etName = new EditText(this);
        etName.setHint("Tiêu đề tin");
        etName.setText(tvCarName.getText());
        layout.addView(etName);

        final EditText etPrice = new EditText(this);
        etPrice.setHint("Giá");
        etPrice.setText(tvCarPrice.getText());
        layout.addView(etPrice);

        final EditText etInfo = new EditText(this);
        etInfo.setHint("Thông tin / mô tả");
        etInfo.setText(tvCarInfo.getText());
        layout.addView(etInfo);

        new AlertDialog.Builder(this)
                .setTitle("Chỉnh sửa bài viết")
                .setView(layout)
                .setPositiveButton("Lưu", (dialog, which) -> {
                    String name = etName.getText().toString().trim();
                    String price = etPrice.getText().toString().trim();
                    String info = etInfo.getText().toString().trim();
                    if (name.isEmpty() || price.isEmpty()) {
                        Toast.makeText(this, "Tiêu đề và giá không được để trống!", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    Map<String, Object> update = new HashMap<>();
                    update.put("name", name);
                    update.put("price", price);
                    update.put("info", info);
                    db.collection("cars").document(carId).update(update)
                            .addOnSuccessListener(aVoid -> {
                                tvCarName.setText(name);
                                if (tvDetailTitle != null) tvDetailTitle.setText(name);
                                tvCarPrice.setText(price);
                                tvCarInfo.setText(info);
                                Toast.makeText(this, "✅ Đã cập nhật bài viết!", Toast.LENGTH_SHORT).show();
                            })
                            .addOnFailureListener(e ->
                                    Toast.makeText(this, "Lỗi: " + e.getMessage(), Toast.LENGTH_SHORT).show());
                })
                .setNegativeButton("Hủy", null)
                .show();
    }

    private void toggleHidePost() {
        if (carId == null || carId.isEmpty()) return;

        boolean isHidden = "hidden".equals(carStatus);
        Map<String, Object> update = new HashMap<>();
        if (isHidden) {
            String restored = statusBeforeHide.isEmpty() ? "active" : statusBeforeHide;
            update.put("status", restored);
            update.put("statusBeforeHide", com.google.firebase.firestore.FieldValue.delete());
            db.collection("cars").document(carId).update(update)
                    .addOnSuccessListener(aVoid -> {
                        carStatus = restored;
                        statusBeforeHide = "";
                        Toast.makeText(this, "✅ Bài viết đã hiển thị trở lại!", Toast.LENGTH_SHORT).show();
                    })
                    .addOnFailureListener(e ->
                            Toast.makeText(this, "Lỗi: " + e.getMessage(), Toast.LENGTH_SHORT).show());
        } else {
            update.put("status", "hidden");
            update.put("statusBeforeHide", carStatus);
            db.collection("cars").document(carId).update(update)
                    .addOnSuccessListener(aVoid -> {
                        statusBeforeHide = carStatus;
                        carStatus = "hidden";
                        Toast.makeText(this, "🙈 Đã ẩn bài viết khỏi danh sách!", Toast.LENGTH_SHORT).show();
                    })
                    .addOnFailureListener(e ->
                            Toast.makeText(this, "Lỗi: " + e.getMessage(), Toast.LENGTH_SHORT).show());
        }
    }

    private void confirmDeletePost() {
        if (carId == null || carId.isEmpty()) return;
        new AlertDialog.Builder(this)
                .setTitle("Xóa bài viết")
                .setMessage("Bạn có chắc muốn xóa bài viết này? Hành động không thể hoàn tác.")
                .setPositiveButton("Xóa", (dialog, which) ->
                        db.collection("cars").document(carId).delete()
                                .addOnSuccessListener(aVoid -> {
                                    Toast.makeText(this, "🗑 Đã xóa bài viết!", Toast.LENGTH_LONG).show();
                                    finish();
                                })
                                .addOnFailureListener(e ->
                                        Toast.makeText(this, "Lỗi: " + e.getMessage(), Toast.LENGTH_SHORT).show()))
                .setNegativeButton("Hủy", null)
                .show();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  GỬI ĐƠN MUA XE — có thêm thông báo cho người bán
    // ═══════════════════════════════════════════════════════════════════════════
    private void sendBuyRequest() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            Toast.makeText(this, "Vui lòng đăng nhập để gửi yêu cầu!", Toast.LENGTH_SHORT).show();
            startActivity(new Intent(this, LoginActivity.class));
            return;
        }

        // Kiểm tra đã có order pending cho xe này chưa → chặn spam
        db.collection("orders")
                .whereEqualTo("buyerId", user.getUid())
                .whereEqualTo("carId",   carId != null ? carId : "")
                .whereEqualTo("status",  "pending")
                .limit(1)
                .get()
                .addOnSuccessListener(snap -> {
                    if (!snap.isEmpty()) {
                        Toast.makeText(this,
                                "⏳ Đã gửi yêu cầu. Vui lòng chờ người bán phản hồi.",
                                Toast.LENGTH_LONG).show();
                        return;
                    }
                    doSendBuyRequest(user);
                });
    }

    private void doSendBuyRequest(FirebaseUser user) {
        Map<String, Object> order = new HashMap<>();
        order.put("buyerId",  user.getUid());
        order.put("sellerId", sellerId != null ? sellerId : "");
        order.put("carId",    carId != null ? carId : "");
        order.put("carName",  car != null ? car.getName() : "");
        order.put("carPrice", car != null ? car.getPrice() : "");
        order.put("type",     "Mua xe");
        order.put("note",     etBuyerNote.getText().toString().trim());
        order.put("status",   "pending");
        order.put("createdAt", com.google.firebase.Timestamp.now());

        db.collection("orders").add(order)
                .addOnSuccessListener(ref -> {
                    Toast.makeText(this, "✅ Gửi yêu cầu thành công!", Toast.LENGTH_LONG).show();
                    etBuyerNote.setText("");
                    notifySellerOrderSent(user, ref.getId());
                    // ── Lên lịch nhắc nhở người bán mỗi 10 phút ──────────────────
                    String buyerName = user.getDisplayName();
                    if (buyerName == null || buyerName.isEmpty()) buyerName = "Khách hàng";
                    OrderReminderService.schedule(
                            CarDetailActivity.this,
                            ref.getId(),
                            sellerId != null ? sellerId : "",
                            user.getUid(),
                            buyerName,
                            car != null ? car.getName() : "",
                            carId != null ? carId : ""
                    );
                });
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  GỬI ĐƠN THUÊ XE — có thêm thông báo cho người cho thuê
    // ═══════════════════════════════════════════════════════════════════════════
    private void sendRentRequest() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            Toast.makeText(this, "Vui lòng đăng nhập!", Toast.LENGTH_SHORT).show();
            return;
        }

        if (tripMode) { sendTripRequest(user); return; }

        // Kiểm tra đã có order pending cho xe này chưa → chặn spam
        db.collection("orders")
                .whereEqualTo("buyerId", user.getUid())
                .whereEqualTo("carId",   carId != null ? carId : "")
                .whereEqualTo("status",  "pending")
                .limit(1)
                .get()
                .addOnSuccessListener(snap -> {
                    if (!snap.isEmpty()) {
                        Toast.makeText(this,
                                "⏳ Đã gửi yêu cầu. Vui lòng chờ người cho thuê phản hồi.",
                                Toast.LENGTH_LONG).show();
                        return;
                    }
                    doSendRentRequest(user);
                });
    }

    private void doSendRentRequest(FirebaseUser user) {

        String renterName  = etRenterName.getText().toString().trim();
        String renterPhone = etRenterPhone.getText().toString().trim();
        String renterCccd  = etRenterCCCD.getText().toString().trim();
        int    days        = parseDays(etRentDays.getText().toString());

        if (renterName.isEmpty() || renterPhone.isEmpty() || renterCccd.isEmpty()) {
            Toast.makeText(this, "Vui lòng nhập đầy đủ thông tin người thuê", Toast.LENGTH_SHORT).show();
            return;
        }
        if (days <= 0) {
            Toast.makeText(this, "Vui lòng nhập số ngày thuê hợp lệ", Toast.LENGTH_SHORT).show();
            return;
        }

        long pricePerDay   = parseMoney(car != null ? car.getPrice() : null);
        long total         = pricePerDay * days;
        boolean needDeposit= WalletRepository.requiresDeposit(days);
        long deposit       = needDeposit ? WalletRepository.deposit(total) : 0L;
        String payMethod   = rgPaymentMethod != null
                && rgPaymentMethod.getCheckedRadioButtonId() == R.id.rb_pay_transfer ? "transfer" : "cash";

        if (needDeposit && walletBalance < deposit) {
            Toast.makeText(this, "Số dư ví không đủ để đặt cọc " + money(deposit)
                    + " đ. Vui lòng nhờ admin nạp tiền.", Toast.LENGTH_LONG).show();
            return;
        }

        Map<String, Object> order = new HashMap<>();
        order.put("buyerId",      user.getUid());
        order.put("renterName",   renterName);
        order.put("renterPhone",  renterPhone);
        order.put("renterCccd",   renterCccd);
        order.put("sellerId",     sellerId != null ? sellerId : "");
        order.put("sellerName",   sellerName);
        order.put("carId",        carId != null ? carId : "");
        order.put("carName",      car != null ? car.getName() : "");
        order.put("carPrice",     car != null ? car.getPrice() : "");
        order.put("type",         isDriverType(carType) ? "Có tài xế" : "Thuê xe");
        order.put("days",         String.valueOf(days));
        order.put("startDate",    etRentStartDate.getText().toString().trim());
        order.put("note",         etRenterNote.getText().toString().trim());
        order.put("totalAmount",  total);
        order.put("depositAmount", deposit);
        order.put("paymentMethod", payMethod);
        order.put("depositStatus", needDeposit ? "held" : "none");
        order.put("status",       "pending");
        order.put("createdAt",    com.google.firebase.Timestamp.now());

        btnSendRentRequest.setEnabled(false);
        db.collection("orders").add(order)
                .addOnSuccessListener(ref -> {
                    if (!needDeposit) {
                        btnSendRentRequest.setEnabled(true);
                        Toast.makeText(this, "✅ Gửi yêu cầu thuê xe thành công!", Toast.LENGTH_LONG).show();

                        // ── Thông báo cho người cho thuê ─────────────────────
                        notifySellerOrderSent(user, ref.getId());
                        OrderReminderService.schedule(
                                CarDetailActivity.this,
                                ref.getId(),
                                sellerId != null ? sellerId : "",
                                user.getUid(),
                                renterName,
                                car != null ? car.getName() : "",
                                carId != null ? carId : ""
                        );
                        // ─────────────────────────────────────────────────────
                        return;
                    }
                    // Giữ cọc: trừ tiền ví khách
                    WalletRepository.holdDeposit(user.getUid(), deposit, ref.getId(),
                            new WalletRepository.Callback() {
                                @Override public void onSuccess() {
                                    walletBalance -= deposit;
                                    updateDepositInfo();
                                    btnSendRentRequest.setEnabled(true);
                                    Toast.makeText(CarDetailActivity.this,
                                            "✅ Đặt xe thành công! Đã giữ cọc " + money(deposit) + " đ.",
                                            Toast.LENGTH_LONG).show();

                                    // ── Thông báo cho người cho thuê ─────────
                                    notifySellerOrderSent(user, ref.getId());
                                    OrderReminderService.schedule(
                                            CarDetailActivity.this,
                                            ref.getId(),
                                            sellerId != null ? sellerId : "",
                                            user.getUid(),
                                            renterName,
                                            car != null ? car.getName() : "",
                                            carId != null ? carId : ""
                                    );
                                    // ─────────────────────────────────────────
                                }
                                @Override public void onError(String msg) {
                                    ref.delete();
                                    btnSendRentRequest.setEnabled(true);
                                    Toast.makeText(CarDetailActivity.this,
                                            "Không giữ được cọc: " + msg, Toast.LENGTH_LONG).show();
                                }
                            });
                })
                .addOnFailureListener(e -> {
                    btnSendRentRequest.setEnabled(true);
                    Toast.makeText(this, "Lỗi tạo đơn: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    /** Gửi đơn đặt "theo chuyến" (quãng đường) cho tài xế của bài đăng này. */
    private void sendTripRequest(FirebaseUser user) {
        String renterName  = etRenterName.getText().toString().trim();
        String renterPhone = etRenterPhone.getText().toString().trim();

        if (renterName.isEmpty() || renterPhone.isEmpty()) {
            Toast.makeText(this, "Vui lòng nhập họ tên và số điện thoại", Toast.LENGTH_SHORT).show();
            return;
        }
        if (tripDistanceKm <= 0 || tripPickup == null || tripDest == null) {
            Toast.makeText(this, "Hãy chọn điểm đón & đến trên bản đồ", Toast.LENGTH_SHORT).show();
            return;
        }
        if (pricePerKm <= 0) {
            Toast.makeText(this, "Tài xế này không nhận đặt theo chuyến", Toast.LENGTH_SHORT).show();
            return;
        }

        long total = Math.round(tripDistanceKm * pricePerKm);
        String tripNote = "Chuyến: " + tripPickup + " → " + tripDest
                + " (" + tripDistanceKm + " km). Tổng: " + money(total) + " đ."
                + (etRenterNote.getText().toString().trim().isEmpty()
                ? "" : " Ghi chú: " + etRenterNote.getText().toString().trim());

        Map<String, Object> order = new HashMap<>();
        order.put("buyerId",      user.getUid());
        order.put("renterName",   renterName);
        order.put("renterPhone",  renterPhone);
        order.put("sellerId",     sellerId != null ? sellerId : "");
        order.put("sellerName",   sellerName);
        order.put("carId",        carId != null ? carId : "");
        order.put("carName",      car != null ? car.getName() : "");
        order.put("carPrice",     money(pricePerKm) + " đ/km");
        order.put("type",         "Có tài xế");
        order.put("rentMode",     "distance");
        order.put("pickup",       tripPickup);
        order.put("destination",  tripDest);
        order.put("distanceKm",   tripDistanceKm);
        order.put("note",         tripNote);
        order.put("totalAmount",  total);
        order.put("depositAmount", 0L);
        order.put("paymentMethod", "cash");
        order.put("depositStatus", "none");
        order.put("status",       "pending");
        order.put("createdAt",    com.google.firebase.Timestamp.now());

        btnSendRentRequest.setEnabled(false);
        db.collection("orders").add(order)
                .addOnSuccessListener(ref -> {
                    btnSendRentRequest.setEnabled(true);
                    Toast.makeText(this, "✅ Đã gửi yêu cầu đặt chuyến! Chờ tài xế xác nhận.",
                            Toast.LENGTH_LONG).show();

                    // ── Thông báo cho tài xế ─────────────────────────────────
                    notifySellerOrderSent(user, ref.getId());
                    // ─────────────────────────────────────────────────────────
                })
                .addOnFailureListener(e -> {
                    btnSendRentRequest.setEnabled(true);
                    Toast.makeText(this, "Lỗi tạo đơn: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  HELPER — lấy tên customer từ Firestore rồi gửi thông báo cho người bán
    // ═══════════════════════════════════════════════════════════════════════════
    private void notifySellerOrderSent(FirebaseUser buyer, String orderId) {
        if (sellerId == null || sellerId.isEmpty()) return;

        db.collection("users").document(buyer.getUid()).get()
                .addOnSuccessListener(snap -> {
                    String buyerName = snap.getString("name");
                    if (buyerName == null || buyerName.isEmpty()) buyerName = "Khách hàng";

                    ChatNotificationHelper.sendOrderNotification(
                            CarDetailActivity.this,
                            sellerId,
                            buyer.getUid(),
                            buyerName,
                            car != null ? car.getName() : "",
                            carId != null ? carId : "",   // ← thêm carId
                            "order_sent",
                            orderId
                    );
                });
    }

    private void showReportDialog() {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) return;

        final String[] reasons = {"Thông tin sai lệch", "Xe không tồn tại", "Giá bất hợp lý", "Lừa đảo"};
        new AlertDialog.Builder(this)
                .setTitle("Báo cáo tin đăng")
                .setItems(reasons, (dialog, which) -> submitReport(currentUser, reasons[which]))
                .setNegativeButton("Hủy", null)
                .show();
    }

    private void submitReport(FirebaseUser currentUser, String reason) {
        Map<String, Object> report = new HashMap<>();
        report.put("reporterId", currentUser.getUid());
        report.put("targetId", carId);
        report.put("reason", reason);
        report.put("status", "pending");
        report.put("createdAt", com.google.firebase.Timestamp.now());

        db.collection("reports").add(report)
                .addOnSuccessListener(ref -> Toast.makeText(this, "✅ Đã gửi báo cáo!", Toast.LENGTH_LONG).show());
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}