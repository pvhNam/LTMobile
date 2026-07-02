package com.example.doanmb.ui.car.view;

import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
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
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.PagerSnapHelper;
import androidx.recyclerview.widget.RecyclerView;

import com.example.doanmb.R;
import com.example.doanmb.core.service.OrderReminderService;
import com.example.doanmb.ui.car.adapter.CarImageAdapter;
import com.example.doanmb.ui.car.adapter.ReviewAdapter;
import com.example.doanmb.ui.car.viewmodel.CarDetailViewModel;
import com.example.doanmb.data.model.Car;
import com.example.doanmb.data.model.Review;
import com.example.doanmb.data.repository.WalletRepository;
import com.example.doanmb.core.helper.ChatNotificationHelper;
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
    private EditText etBuyerName, etBuyerPhone, etBuyerCCCD, etBuyerNote;
    private Button btnSendRequest, btnCallSeller, btnChatSeller;
    private TextView tvBuyAutofillHint, tvRentAutofillHint;

    // Form thuê xe
    private LinearLayout layoutRentForm;
    private EditText etRenterName, etRenterPhone, etRenterCCCD;
    private EditText etRentStartDate, etRentDays, etRenterNote;
    private Button btnSendRentRequest, btnCallRentSeller, btnChatRentSeller;
    private TextView tvDepositInfo;
    private long walletBalance = 0L;
    private boolean datePickerShowing = false; // chống mở 2 hộp thoại lịch cùng lúc
    private com.google.android.material.button.MaterialButtonToggleGroup togglePaymentMethod;
    private TextView tvPaymentMethodHint;
    private String paymentMethod = "cash";

    private Button btnOpenRentSheet;
    private View layoutRentContact;
    private View sheetRent;
    private View rentScrim;
    private boolean rentSheetOpen = false;

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
    private double tripPickupLat = 0d, tripPickupLng = 0d, tripDestLat = 0d, tripDestLng = 0d;

    // Đánh giá tài xế
    private View layoutReviewSection;
    private TextView tvDetailAvgRating, tvDetailReviewsEmpty;
    private RecyclerView rvDetailReviews;
    private ReviewAdapter reviewAdapter;
    private final List<Review> reviewList = new ArrayList<>();
    private TextView tvReviewLoadingMore;
    private TextView tvReviewNoMore;
    private String driverIdForReviews;

    private final androidx.activity.result.ActivityResultLauncher<Intent> mapLauncher =
            registerForActivityResult(
                    new androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        if (result.getResultCode() != RESULT_OK || result.getData() == null) return;
                        Intent d = result.getData();
                        tripPickup = d.getStringExtra(MapPickerActivity.RESULT_PICKUP);
                        tripDest = d.getStringExtra(MapPickerActivity.RESULT_DEST);
                        tripDistanceKm = d.getDoubleExtra(MapPickerActivity.RESULT_DISTANCE_KM, 0d);
                        tripPickupLat = d.getDoubleExtra(MapPickerActivity.RESULT_PICKUP_LAT, 0d);
                        tripPickupLng = d.getDoubleExtra(MapPickerActivity.RESULT_PICKUP_LNG, 0d);
                        tripDestLat = d.getDoubleExtra(MapPickerActivity.RESULT_DEST_LAT, 0d);
                        tripDestLng = d.getDoubleExtra(MapPickerActivity.RESULT_DEST_LNG, 0d);
                        updateTripSummary();
                    });

    private TextView tvReportCar;
    private ImageView btnMenuDetail;
    private String sellerPhone = "";
    private Car car;
    private String carId, sellerId, carType;

    private CarDetailViewModel viewModel;

    private NestedScrollView detailScroll;
    private View imageHero, headerDetail, btnBackFloat, floatTopBar;
    private ImageView btnFavoriteFloat, ivFavoriteDetail, btnMenuFloat, btnReportFloat;
    private boolean isFav = false;
    private boolean statusBarDarkIcons = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_car_detail);

        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        getWindow().setStatusBarColor(android.graphics.Color.TRANSPARENT);

        viewModel = new ViewModelProvider(this).get(CarDetailViewModel.class);

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

        setupButtons();
        setupRentDepositUi();
        setupBookModeListeners();

        observeViewModel();
        viewModel.init(car, carId, sellerId, carType);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Chi tiết xe");
        }
    }

    private void observeViewModel() {
        viewModel.getDetail().observe(this, d -> {
            if (d == null) return;
            pricePerDay = d.pricePerDay;
            pricePerKm  = d.pricePerKm;
            if (!d.images.isEmpty()) {
                showImages(d.images);
                for (String u : d.images) ImageLoader.preload(getApplicationContext(), u);
            }
            if (d.fuel != null && !d.fuel.isEmpty()) {
                tvCarFuelBadge.setText(d.fuel);
                tvCarFuelBadge.setVisibility(View.VISIBLE);
            }
            if (d.condition != null && !d.condition.isEmpty()) {
                if (d.condition.contains("mới 100") || d.condition.equalsIgnoreCase("Xe mới 100%"))
                    tvCarConditionBadge.setText("Xe mới");
                else
                    tvCarConditionBadge.setText("Xe cũ");
                tvCarConditionBadge.setVisibility(View.VISIBLE);
            }
        });

        viewModel.getContact().observe(this, c -> {
            if (c == null) return;
            tvSellerName.setText("👤  " + (c.name != null && !c.name.isEmpty() ? c.name : "Chưa có thông tin"));
            if (c.phone != null && !c.phone.isEmpty()) {
                sellerPhone = c.phone;
                tvSellerPhone.setText("📞  " + c.phone);
            } else {
                tvSellerPhone.setText("📞  Chưa có thông tin");
            }
        });

        viewModel.getTypeInfo().observe(this, t -> {
            if (t != null) setupByType(t.type, t.isOwner);
        });

        viewModel.getWalletBalance().observe(this, b -> {
            walletBalance = b != null ? b : 0L;
            if (togglePaymentMethod != null) applyPaymentMethod(togglePaymentMethod.getCheckedButtonId());
            updateDepositInfo();
        });

        viewModel.getIsFavorite().observe(this, fav -> {
            isFav = Boolean.TRUE.equals(fav);
            updateFavoriteIcons();
        });

        // Hồ sơ người dùng → tự điền form thuê/mua (chỉ điền ô đang trống, vẫn sửa được)
        viewModel.getUserProfile().observe(this, p -> {
            if (p == null) return;
            boolean rentFilled = prefillIfEmpty(etRenterName, p.name)
                    | prefillIfEmpty(etRenterPhone, p.phone)
                    | prefillIfEmpty(etRenterCCCD, p.cccd);
            boolean buyFilled = prefillIfEmpty(etBuyerName, p.name)
                    | prefillIfEmpty(etBuyerPhone, p.phone)
                    | prefillIfEmpty(etBuyerCCCD, p.cccd);
            if (rentFilled && tvRentAutofillHint != null) tvRentAutofillHint.setVisibility(View.VISIBLE);
            if (buyFilled && tvBuyAutofillHint != null) tvBuyAutofillHint.setVisibility(View.VISIBLE);
        });

        viewModel.getShowReviews().observe(this, show -> {
            if (layoutReviewSection != null)
                layoutReviewSection.setVisibility(Boolean.TRUE.equals(show) ? View.VISIBLE : View.GONE);
        });
        viewModel.getReviews().observe(this, list -> {
            reviewList.clear();
            if (list != null) reviewList.addAll(list);
            reviewAdapter.notifyDataSetChanged();
            if (tvDetailReviewsEmpty != null)
                tvDetailReviewsEmpty.setVisibility(reviewList.isEmpty() ? View.VISIBLE : View.GONE);
        });

        viewModel.getReviewLoadingMore().observe(this, loading -> {
            if (tvReviewLoadingMore != null)
                tvReviewLoadingMore.setVisibility(
                        Boolean.TRUE.equals(loading) ? View.VISIBLE : View.GONE);
        });

        viewModel.getReviewNoMore().observe(this, noMore -> {
            if (tvReviewNoMore != null)
                tvReviewNoMore.setVisibility(
                        Boolean.TRUE.equals(noMore) ? View.VISIBLE : View.GONE);
        });
        viewModel.getRatingText().observe(this, txt -> {
            if (tvDetailAvgRating != null && txt != null) tvDetailAvgRating.setText(txt);
        });

        viewModel.getMessage().observe(this, msg -> {
            if (msg != null) Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
        });

        viewModel.getNeedLogin().observe(this, need -> {
            if (Boolean.TRUE.equals(need)) {
                Toast.makeText(this, "Vui lòng đăng nhập để gửi yêu cầu!", Toast.LENGTH_SHORT).show();
                startActivity(new Intent(this, LoginActivity.class));
            }
        });

        viewModel.getSendEnabled().observe(this, enabled -> {
            boolean e = !Boolean.FALSE.equals(enabled);
            if (btnSendRequest != null) btnSendRequest.setEnabled(e);
            if (btnSendRentRequest != null) btnSendRentRequest.setEnabled(e);
        });

        viewModel.getOrderSent().observe(this, this::onOrderSent);

        viewModel.getPostEdited().observe(this, p -> {
            if (p == null) return;
            tvCarName.setText(p.name);
            if (tvDetailTitle != null) tvDetailTitle.setText(p.name);
            tvCarPrice.setText(p.price);
            tvCarInfo.setText(p.info);
        });

        viewModel.getFinishEvent().observe(this, f -> {
            if (Boolean.TRUE.equals(f)) finish();
        });
    }

    private void onOrderSent(CarDetailViewModel.OrderSent e) {
        if (e == null) return;
        if (e.kind == CarDetailViewModel.Kind.BUY) {
            etBuyerNote.setText("");
        } else {
            closeRentSheet();
        }
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) notifySellerOrderSent(user, e.orderId);
        if (e.scheduleReminder && user != null) {
            OrderReminderService.schedule(
                    this,
                    e.orderId,
                    sellerId != null ? sellerId : "",
                    user.getUid(),
                    e.customerName,
                    car != null ? car.getName() : "",
                    carId != null ? carId : ""
            );
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
        etBuyerName = findViewById(R.id.etBuyerName);
        etBuyerPhone = findViewById(R.id.etBuyerPhone);
        etBuyerCCCD = findViewById(R.id.etBuyerCCCD);
        etBuyerNote = findViewById(R.id.etBuyerNote);
        tvBuyAutofillHint = findViewById(R.id.tv_buy_autofill_hint);
        tvRentAutofillHint = findViewById(R.id.tv_rent_autofill_hint);
        btnSendRequest = findViewById(R.id.btnSendRequest);
        btnCallSeller = findViewById(R.id.btnCallSeller);
        btnChatSeller = findViewById(R.id.btnChatSeller);

        layoutRentForm = findViewById(R.id.layoutRentForm);
        etRenterName = findViewById(R.id.etRenterName);
        etRenterPhone = findViewById(R.id.etRenterPhone);
        etRenterCCCD = findViewById(R.id.etRenterCCCD);
        etRentStartDate = findViewById(R.id.etRentStartDate);
        setupStartDatePicker();
        etRentDays = findViewById(R.id.etRentDays);
        etRenterNote = findViewById(R.id.etRenterNote);
        btnSendRentRequest = findViewById(R.id.btnSendRentRequest);
        btnCallRentSeller  = findViewById(R.id.btnCallRentSeller);
        btnChatRentSeller = findViewById(R.id.btnChatRentSeller);
        tvDepositInfo   = findViewById(R.id.tv_deposit_info);

        toggleBookMode  = findViewById(R.id.toggle_book_mode);
        layoutDayFields = findViewById(R.id.layout_day_fields);
        layoutTripFields= findViewById(R.id.layout_trip_fields);
        btnPickOnMap    = findViewById(R.id.btnPickOnMap);
        tvTripSummary   = findViewById(R.id.tvTripSummary);

        // Đánh giá
        layoutReviewSection  = findViewById(R.id.layout_review_section);
        tvDetailAvgRating    = findViewById(R.id.tv_detail_avg_rating);
        rvDetailReviews      = findViewById(R.id.rv_detail_reviews);
        tvDetailReviewsEmpty = findViewById(R.id.tv_detail_reviews_empty);
        reviewAdapter = new ReviewAdapter(reviewList);
        if (rvDetailReviews != null) {
            rvDetailReviews.setLayoutManager(new LinearLayoutManager(this));
            rvDetailReviews.setAdapter(reviewAdapter);
            rvDetailReviews.setOnTouchListener((v, event) -> {
                v.getParent().requestDisallowInterceptTouchEvent(true);
                return false;
            });

            rvDetailReviews.addOnScrollListener(new androidx.recyclerview.widget.RecyclerView.OnScrollListener() {
                @Override
                public void onScrolled(@NonNull androidx.recyclerview.widget.RecyclerView rv, int dx, int dy) {
                    if (dy <= 0 || driverIdForReviews == null) return;
                    androidx.recyclerview.widget.LinearLayoutManager lm =
                            (androidx.recyclerview.widget.LinearLayoutManager) rv.getLayoutManager();
                    if (lm == null) return;
                    int last = lm.findLastVisibleItemPosition();
                    int total = lm.getItemCount();
                    if (last >= total - 2) viewModel.loadMoreReviews(driverIdForReviews);
                }
            });
        }

        togglePaymentMethod = findViewById(R.id.toggle_payment_method);
        tvPaymentMethodHint = findViewById(R.id.tv_payment_method_hint);
        setupPaymentMethod();

        btnOpenRentSheet  = findViewById(R.id.btnOpenRentSheet);
        layoutRentContact = findViewById(R.id.layoutRentContact);
        sheetRent         = findViewById(R.id.sheet_rent);
        rentScrim         = findViewById(R.id.rent_scrim);
        View btnCloseRentSheet = findViewById(R.id.btn_close_rent_sheet);
        tvReviewLoadingMore = findViewById(R.id.tv_review_loading_more);
        tvReviewNoMore      = findViewById(R.id.tv_review_no_more);
        if (btnOpenRentSheet != null) btnOpenRentSheet.setOnClickListener(v -> openRentSheet());
        if (btnCloseRentSheet != null) btnCloseRentSheet.setOnClickListener(v -> closeRentSheet());
        if (rentScrim != null) rentScrim.setOnClickListener(v -> closeRentSheet());
    }

    private void openRentSheet() {
        if (sheetRent == null || rentSheetOpen) return;
        rentSheetOpen = true;

        int offscreen = getResources().getDisplayMetrics().heightPixels;
        sheetRent.setTranslationY(offscreen);
        sheetRent.setVisibility(View.VISIBLE);
        sheetRent.post(() -> {
            sheetRent.setTranslationY(sheetRent.getHeight());
            sheetRent.animate().translationY(0f).setDuration(300)
                    .setInterpolator(new android.view.animation.DecelerateInterpolator()).start();
        });

        if (rentScrim != null) {
            rentScrim.setAlpha(0f);
            rentScrim.setVisibility(View.VISIBLE);
            rentScrim.animate().alpha(1f).setDuration(300).start();
        }
    }

    private void closeRentSheet() {
        rentSheetOpen = false;
        if (sheetRent == null || sheetRent.getVisibility() != View.VISIBLE) return;

        float target = sheetRent.getHeight() > 0
                ? sheetRent.getHeight()
                : getResources().getDisplayMetrics().heightPixels;
        sheetRent.animate().translationY(target).setDuration(220)
                .setInterpolator(new android.view.animation.AccelerateInterpolator())
                .withEndAction(() -> sheetRent.setVisibility(View.GONE)).start();

        if (rentScrim != null) {
            rentScrim.animate().alpha(0f).setDuration(220)
                    .withEndAction(() -> rentScrim.setVisibility(View.GONE)).start();
        }
    }

    private boolean isRentSheetOpen() {
        return rentSheetOpen;
    }

    @Override
    public void onBackPressed() {
        if (isRentSheetOpen()) { closeRentSheet(); return; }
        super.onBackPressed();
    }

    private void setupPaymentMethod() {
        if (togglePaymentMethod == null) return;
        if (togglePaymentMethod.getCheckedButtonId() == View.NO_ID) {
            togglePaymentMethod.check(R.id.btn_pay_cash);
        }
        applyPaymentMethod(togglePaymentMethod.getCheckedButtonId());
        togglePaymentMethod.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (isChecked) applyPaymentMethod(checkedId);
        });
    }

    private void applyPaymentMethod(int checkedId) {
        if (checkedId == R.id.btn_pay_vnpay) {
            paymentMethod = "vnpay";
        } else if (checkedId == R.id.btn_pay_wallet) {
            paymentMethod = "wallet";
        } else {
            paymentMethod = "cash";
        }
        if (tvPaymentMethodHint == null) return;
        switch (paymentMethod) {
            case "vnpay":
                tvPaymentMethodHint.setText("Chuyển khoản qua VNPay khi thanh toán hóa đơn lúc trả xe.");
                break;
            case "wallet":
                tvPaymentMethodHint.setText("Trừ trực tiếp vào số dư ví trong app khi thanh toán hóa đơn ("
                        + money(walletBalance) + " đ khả dụng).");
                break;
            default:
                tvPaymentMethodHint.setText("Trả tiền mặt cho chủ xe khi kết thúc chuyến.");
        }
    }

    private void setupDetailHeader() {
        detailScroll     = findViewById(R.id.detail_scroll);
        imageHero        = findViewById(R.id.image_hero);
        headerDetail     = findViewById(R.id.header_detail);
        btnBackFloat     = findViewById(R.id.btn_back_float);
        floatTopBar      = findViewById(R.id.float_top_bar);
        btnFavoriteFloat = findViewById(R.id.btn_favorite_float);
        btnReportFloat   = findViewById(R.id.btn_report_float);
        btnMenuFloat     = findViewById(R.id.btn_menu_float);
        ivFavoriteDetail = findViewById(R.id.iv_favorite_detail);
        tvDetailTitle    = findViewById(R.id.tv_detail_title);

        View btnBack = findViewById(R.id.btn_back_detail);
        if (btnBack != null) btnBack.setOnClickListener(v -> finish());
        if (btnBackFloat != null) btnBackFloat.setOnClickListener(v -> finish());

        if (btnFavoriteFloat != null) btnFavoriteFloat.setOnClickListener(v -> viewModel.toggleFavorite());
        if (ivFavoriteDetail != null) ivFavoriteDetail.setOnClickListener(v -> viewModel.toggleFavorite());
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

        if (detailScroll != null) {
            detailScroll.setOnScrollChangeListener(
                    (NestedScrollView.OnScrollChangeListener) (v, scrollX, scrollY, oldX, oldY) -> {
                        updateDetailHeader(scrollY);

                    });
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

    /** Điền value vào ô nếu ô đang trống. Trả về true nếu có điền. */
    private boolean prefillIfEmpty(EditText et, String value) {
        if (et == null || value == null || value.isEmpty()) return false;
        if (et.getText() != null && !et.getText().toString().trim().isEmpty()) return false;
        et.setText(value);
        return true;
    }

    private void setupButtons() {
        btnSendRequest.setOnClickListener(v -> {
            CarDetailViewModel.BuyForm form = new CarDetailViewModel.BuyForm();
            form.buyerName  = etBuyerName != null ? etBuyerName.getText().toString().trim() : "";
            form.buyerPhone = etBuyerPhone != null ? etBuyerPhone.getText().toString().trim() : "";
            form.buyerCccd  = etBuyerCCCD != null ? etBuyerCCCD.getText().toString().trim() : "";
            form.note       = etBuyerNote.getText().toString();
            viewModel.sendBuyRequest(form);
        });
        btnCallSeller.setOnClickListener(v -> callSeller());
        btnChatSeller.setOnClickListener(v -> openChat());

        btnSendRentRequest.setOnClickListener(v -> showRentalTermsThenSend());
        btnCallRentSeller.setOnClickListener(v -> callSeller());
        btnChatRentSeller.setOnClickListener(v -> openChat());
    }

    private void setupRentDepositUi() {
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
        // Dùng giá/ngày đã load (đúng cho cả xe thường lẫn tài xế). KHÔNG parse car.getPrice()
        // vì bài tài xế lưu chuỗi gồm cả giá ngày + giá km → parse sẽ ra số khổng lồ.
        long pricePerDayLocal = pricePerDay;
        int days = parseDays(etRentDays != null ? etRentDays.getText().toString() : "");

        if (pricePerDayLocal <= 0 || days <= 0) {
            tvDepositInfo.setText("Nhập số ngày thuê để xem tiền cọc.\nSố dư ví: " + money(walletBalance) + " đ");
            return;
        }

        long total = pricePerDayLocal * days;
        StringBuilder sb = new StringBuilder();
        sb.append("Tổng tiền thuê (").append(days).append(" ngày): ").append(money(total)).append(" đ\n");
        if (WalletRepository.requiresDeposit(days)) {
            long deposit = WalletRepository.deposit(total);
            long rest = total - deposit;
            sb.append("Trả trước 50% khi đặt (trừ vào ví): ").append(money(deposit)).append(" đ\n");
            sb.append("Còn lại trả khi trả xe: ").append(money(rest)).append(" đ\n");
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

        FirebaseFirestore.getInstance().collection("chat_rooms").document(roomId)
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

    private void setupByType(String type, boolean isOwner) {
        boolean driver = CarDetailViewModel.isDriverType(type);
        boolean rental = CarDetailViewModel.isRentalType(type);

        if (isOwner) {
            layoutBuyForm.setVisibility(View.GONE);
            if (btnOpenRentSheet != null) btnOpenRentSheet.setVisibility(View.GONE);
            if (layoutRentContact != null) layoutRentContact.setVisibility(View.GONE);
            closeRentSheet();
            if (tvReportCar != null) tvReportCar.setVisibility(View.GONE);
            if (btnReportFloat != null) btnReportFloat.setVisibility(View.GONE);
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
        // Báo cáo tin: dùng icon tròn nổi trên ảnh (bên trái icon yêu thích)
        if (tvReportCar != null) tvReportCar.setVisibility(View.GONE);
        if (btnReportFloat != null) {
            btnReportFloat.setVisibility(View.VISIBLE);
            btnReportFloat.setOnClickListener(v -> showReportDialog());
        }

        applyTypeBadge(driver, rental);

        if (driver || rental) {
            layoutBuyForm.setVisibility(View.GONE);
            if (btnOpenRentSheet != null) btnOpenRentSheet.setVisibility(View.VISIBLE);
            if (layoutRentContact != null) layoutRentContact.setVisibility(View.VISIBLE);
            configureBookMode(driver);
        } else {
            layoutBuyForm.setVisibility(View.VISIBLE);
            if (btnOpenRentSheet != null) btnOpenRentSheet.setVisibility(View.GONE);
            if (layoutRentContact != null) layoutRentContact.setVisibility(View.GONE);
            closeRentSheet();
        }
        if (driver && sellerId != null && !sellerId.isEmpty()) {
            driverIdForReviews = sellerId;
            viewModel.loadFirstReviews(sellerId);
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

    private void showOwnerMenu(View anchor) {
        boolean isHidden = viewModel.isHidden();
        androidx.appcompat.widget.PopupMenu menu =
                new androidx.appcompat.widget.PopupMenu(this, anchor);
        menu.getMenu().add(0, 1, 0, "✏️  Chỉnh sửa bài viết");
        menu.getMenu().add(0, 2, 1, isHidden ? "👁  Hiện bài viết" : "🙈  Ẩn bài viết");
        menu.getMenu().add(0, 3, 2, "🗑  Xóa bài viết");
        menu.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case 1: showEditPostDialog(); return true;
                case 2: viewModel.toggleHidePost(); return true;
                case 3: confirmDeletePost();  return true;
                default: return false;
            }
        });
        menu.show();
    }

    private void showEditPostDialog() {
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
                .setPositiveButton("Lưu", (dialog, which) ->
                        viewModel.editPost(
                                etName.getText().toString().trim(),
                                etPrice.getText().toString().trim(),
                                etInfo.getText().toString().trim()))
                .setNegativeButton("Hủy", null)
                .show();
    }

    private void confirmDeletePost() {
        new AlertDialog.Builder(this)
                .setTitle("Xóa bài viết")
                .setMessage("Bạn có chắc muốn xóa bài viết này? Hành động không thể hoàn tác.")
                .setPositiveButton("Xóa", (dialog, which) -> viewModel.deletePost())
                .setNegativeButton("Hủy", null)
                .show();
    }

    private void setupStartDatePicker() {
        if (etRentStartDate == null) return;
        etRentStartDate.setFocusable(false);
        etRentStartDate.setFocusableInTouchMode(false);
        etRentStartDate.setOnClickListener(v -> showStartDatePicker());
    }

    private void showStartDatePicker() {
        if (etRentStartDate == null) return;
        if (datePickerShowing) return;
        datePickerShowing = true;

        final java.text.SimpleDateFormat sdf =
                new java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.US);
        java.util.Calendar c = java.util.Calendar.getInstance();
        String cur = etRentStartDate.getText().toString().trim();
        try {
            java.util.Date d = sdf.parse(cur);
            if (d != null) c.setTime(d);
        } catch (Exception ignore) { }
        DatePickerDialog dlg = new DatePickerDialog(this, (picker, year, month, day) -> {
            java.util.Calendar picked = java.util.Calendar.getInstance();
            picked.set(java.util.Calendar.YEAR, year);
            picked.set(java.util.Calendar.MONTH, month);
            picked.set(java.util.Calendar.DAY_OF_MONTH, day);
            etRentStartDate.setText(sdf.format(picked.getTime()));
        }, c.get(java.util.Calendar.YEAR), c.get(java.util.Calendar.MONTH),
                c.get(java.util.Calendar.DAY_OF_MONTH));
        dlg.getDatePicker().setMinDate(System.currentTimeMillis() - 1000);
        dlg.setOnDismissListener(d -> datePickerShowing = false);
        dlg.show();
    }

    private void showRentalTermsThenSend() {
        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            Toast.makeText(this, "Vui lòng đăng nhập để thuê xe!", Toast.LENGTH_SHORT).show();
            return;
        }

        View view = getLayoutInflater().inflate(R.layout.dialog_rental_terms, null);
        TextView tvCountdown = view.findViewById(R.id.tv_terms_countdown);
        CheckBox cbAgree     = view.findViewById(R.id.cb_terms_agree);
        Button btnCancel     = view.findViewById(R.id.btn_terms_cancel);
        Button btnConfirm    = view.findViewById(R.id.btn_terms_confirm);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(view)
                .setCancelable(false)
                .create();

        Window w = dialog.getWindow();
        if (w != null) {
            try {
                w.setBackgroundDrawableResource(android.R.color.transparent);
                w.setLayout(WindowManager.LayoutParams.MATCH_PARENT,
                        WindowManager.LayoutParams.WRAP_CONTENT);
                w.setDimAmount(0.3f);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    w.setBackgroundBlurRadius(24);
                    w.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND);
                    WindowManager.LayoutParams lp = w.getAttributes();
                    lp.setBlurBehindRadius(12);
                    w.setAttributes(lp);
                }
            } catch (Throwable ignore) {
            }
        }

        cbAgree.setEnabled(false);
        btnCancel.setEnabled(false);
        btnConfirm.setEnabled(false);
        btnCancel.setAlpha(0.5f);
        btnConfirm.setAlpha(0.5f);

        final CountDownTimer timer = new CountDownTimer(3_000, 1_000) {
            @Override public void onTick(long ms) {
                tvCountdown.setText("⏳ Vui lòng đọc kỹ điều khoản… còn " + (ms / 1000 + 1) + " giây");
            }
            @Override public void onFinish() {
                tvCountdown.setText("✅ Bạn có thể tích đồng ý để tiếp tục");
                tvCountdown.setTextColor(0xFF0E8C91);
                cbAgree.setEnabled(true);
                btnCancel.setEnabled(true);
                btnCancel.setAlpha(1f);
            }
        }.start();

        cbAgree.setOnCheckedChangeListener((b, checked) -> {
            btnConfirm.setEnabled(checked);
            btnConfirm.setAlpha(checked ? 1f : 0.5f);
        });

        btnCancel.setOnClickListener(v -> { timer.cancel(); dialog.dismiss(); });
        btnConfirm.setOnClickListener(v -> {
            timer.cancel();
            dialog.dismiss();
            submitRentRequest();
        });

        dialog.show();
    }

    private void submitRentRequest() {
        CarDetailViewModel.RentForm form = new CarDetailViewModel.RentForm();
        form.renterName  = etRenterName.getText().toString().trim();
        form.renterPhone = etRenterPhone.getText().toString().trim();
        form.renterCccd  = etRenterCCCD.getText().toString().trim();
        form.startDate   = etRentStartDate.getText().toString().trim();
        form.note        = etRenterNote.getText().toString().trim();
        form.days        = parseDays(etRentDays.getText().toString());
        form.tripMode    = tripMode;
        form.pickup      = tripPickup;
        form.dest        = tripDest;
        form.distanceKm  = tripDistanceKm;
        form.pickupLat   = tripPickupLat;
        form.pickupLng   = tripPickupLng;
        form.destLat     = tripDestLat;
        form.destLng     = tripDestLng;
        form.paymentMethod = paymentMethod;
        viewModel.sendRentRequest(form);
    }

    private void notifySellerOrderSent(FirebaseUser buyer, String orderId) {
        if (sellerId == null || sellerId.isEmpty()) return;

        FirebaseFirestore.getInstance().collection("users").document(buyer.getUid()).get()
                .addOnSuccessListener(snap -> {
                    String buyerName = snap.getString("name");
                    if (buyerName == null || buyerName.isEmpty()) buyerName = "Khách hàng";

                    if (CarDetailViewModel.isDriverType(carType)) {
                        ChatNotificationHelper.sendFcmOnlyOrderNotification(
                                CarDetailActivity.this,
                                sellerId,
                                buyer.getUid(),
                                buyerName,
                                car != null ? car.getName() : "",
                                carId != null ? carId : "",
                                "order_sent",
                                orderId
                        );
                    } else {
                        ChatNotificationHelper.sendOrderNotification(
                                CarDetailActivity.this,
                                sellerId,
                                buyer.getUid(),
                                buyerName,
                                car != null ? car.getName() : "",
                                carId != null ? carId : "",
                                "order_sent",
                                orderId
                        );
                    }
                });
    }

    private void showReportDialog() {
        if (FirebaseAuth.getInstance().getCurrentUser() == null) return;

        final String[] reasons = {"Thông tin sai lệch", "Xe không tồn tại", "Giá bất hợp lý", "Lừa đảo"};
        new AlertDialog.Builder(this)
                .setTitle("Báo cáo tin đăng")
                .setItems(reasons, (dialog, which) -> viewModel.submitReport(reasons[which]))
                .setNegativeButton("Hủy", null)
                .show();
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}