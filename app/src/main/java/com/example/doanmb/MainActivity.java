package com.example.doanmb;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.widget.NestedScrollView;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.doanmb.adapter.BannerAdapter;
import com.example.doanmb.ui.activity.CarDetailActivity;
import com.example.doanmb.ui.activity.ChatDetailActivity;
import com.example.doanmb.ui.fragment.CategoryFragment;
import com.example.doanmb.ui.fragment.ManageFragment;
import com.example.doanmb.ui.fragment.MessagesFragment;
import com.example.doanmb.ui.fragment.ProfileFragment;
import com.example.doanmb.adapter.CarRentalAdapter;
import com.example.doanmb.adapter.CarSaleAdapter;
import com.example.doanmb.model.Car;
import com.example.doanmb.util.FavoriteHelper;
import com.example.doanmb.util.ImageLoader;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import android.os.Build;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import eightbitlab.com.blurview.BlurView;
import eightbitlab.com.blurview.BlurAlgorithm;
import eightbitlab.com.blurview.RenderEffectBlur;
import eightbitlab.com.blurview.RenderScriptBlur;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import com.example.doanmb.service.CarviaMessagingService;
import com.example.doanmb.util.ChatNotificationHelper;
import com.google.firebase.messaging.FirebaseMessaging;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;


public class MainActivity extends AppCompatActivity {

    private RecyclerView rvFeaturedSales, rvRentalCars, rvBanners;
    private AutoCompleteTextView etSearch;
    private TextView tvGreeting;
    private BottomNavigationView bottomNavigationView;
    private BlurView blurNav;
    private FrameLayout fragmentContainer;
    private View homeLayout;
    private SwipeRefreshLayout swipeRefreshLayout;
    private NestedScrollView nestedScroll;

    // Quick action buttons
    private LinearLayout btnBuyCar, btnDriver, btnPoliceCar, btnPayment;

    private CarSaleAdapter carSaleAdapter;
    private CarRentalAdapter carRentalAdapter;

    // Id các xe đã yêu thích của user hiện tại (để hiển thị tim đỏ)
    private final java.util.Set<String> favoriteIds = new java.util.HashSet<>();

    private List<Car> saleCarList   = new ArrayList<>();
    private List<Car> rentalCarList = new ArrayList<>();

    private FirebaseFirestore db;

    // Điều hướng Back: lưu lịch sử các tab đã đi qua để quay lại tab trước đó
    private int currentNavId = R.id.nav_home;
    private boolean isHandlingBack = false;
    private final Deque<Integer> navBackStack = new ArrayDeque<>();

    // Giữ lại fragment đã mở của từng tab để khi quay lại không phải dựng/tải lại từ đầu (tránh giật)
    private final java.util.Map<Integer, Fragment> fragmentCache = new java.util.HashMap<>();
    private Fragment activeFragment = null;

    // Điều hướng Back (áp dụng cho cả vuốt lẫn nút back):
    // - Còn điều hướng được → quay về tab/trang trước đó
    // - Ở Home và hết lịch sử → nhấn lần đầu hiện thông báo, nhấn lần nữa trong 2 giây để thoát app
    private long lastBackPressTime = 0L;
    private static final long EXIT_CONFIRM_WINDOW_MS = 2000L;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        db = FirebaseFirestore.getInstance();

        requestNotificationPermission();

        // Warm up FCM access token ngay khi mở app → tránh delay lần đầu gửi notification
        ChatNotificationHelper.warmUpAccessToken(this);

        // Cập nhật FCM token lên Firestore ngay khi mở app
        // → đảm bảo token luôn mới nhất, không cần chờ vào ChatDetailActivity
        FirebaseMessaging.getInstance().getToken().addOnSuccessListener(token -> {
            if (token != null) CarviaMessagingService.saveFcmToken(this, token);
        });

        initViews();
        setupBanners();
        loadUserGreeting();
        setupRecyclerViews();
        loadCarsFromFirestore();
        setupSearch();
        setupBottomNavigation();
        handleNotificationIntent(getIntent());
        setupSwipeRefresh();
        setupQuickActions();
        setupBackNavigation();
        setupGlassNav();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleNotificationIntent(intent);
    }

    public void openManageRequestsTab() {
        Fragment old = fragmentCache.get(R.id.nav_manage);
        if (old != null) getSupportFragmentManager().beginTransaction().remove(old).commitNow();

        ManageFragment fragment = new ManageFragment();
        Bundle args = new Bundle();
        // Cập nhật key thành "showRequests" theo đúng yêu cầu của bạn
        args.putBoolean("showRequests", true);
        fragment.setArguments(args);

        fragmentCache.put(R.id.nav_manage, fragment);
        getSupportFragmentManager().beginTransaction()
                .add(R.id.fragment_container, fragment).commitNow();

        bottomNavigationView.setSelectedItemId(R.id.nav_manage);
    }

    private void handleNotificationIntent(Intent intent) {
        if (intent == null) return;

        // Giữ lại logic cũ phòng trường hợp app đang dùng
        String openTab = intent.getStringExtra("OPEN_TAB");
        if ("manage".equals(openTab)) {
            openManageRequestsTab();
        } else if ("messages".equals(openTab)) {
            bottomNavigationView.setSelectedItemId(R.id.nav_messages);
            String roomId = intent.getStringExtra("ROOM_ID");
            if (roomId != null && !roomId.isEmpty()) {
                Intent i = new Intent(this, ChatDetailActivity.class);
                i.putExtra("ROOM_ID", roomId);
                startActivity(i);
            }
        }

        String navigateTo = intent.getStringExtra("navigateTo");
        if ("manage_requests".equals(navigateTo)) {
            // Gọi hàm có sẵn, hàm này đã xử lý việc chọn bottom nav và truyền Bundle
            openManageRequestsTab();
        }

    }

    /**
     * Thanh menu Liquid Glass: BlurView blur thật nội dung cuộn phía sau, bo viên thuốc,
     * nổi trên thanh điều hướng hệ thống (vị trí ban đầu).
     */
    private void setupGlassNav() {
        if (blurNav == null) return;
        ViewGroup root = findViewById(R.id.main_root);
        BlurAlgorithm algorithm = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                ? new RenderEffectBlur()
                : new RenderScriptBlur(this);
        blurNav.setupWith(root, algorithm)
                .setFrameClearDrawable(root.getBackground())
                .setBlurRadius(18f);
        blurNav.setOutlineProvider(ViewOutlineProvider.BACKGROUND);
        blurNav.setClipToOutline(true);

        // Nâng viên thuốc lên trên thanh điều hướng hệ thống (gesture/3 nút)
        final int baseMarginBottom = ((ViewGroup.MarginLayoutParams) blurNav.getLayoutParams()).bottomMargin;
        ViewCompat.setOnApplyWindowInsetsListener(blurNav, (v, insets) -> {
            int navBarHeight = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom;
            ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) v.getLayoutParams();
            lp.bottomMargin = baseMarginBottom + navBarHeight;
            v.setLayoutParams(lp);
            return insets;
        });
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) { // API 33+
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.POST_NOTIFICATIONS},
                        1001);
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Cập nhật trạng thái tim (kể cả khi vừa bỏ thích ở màn "Xe yêu thích")
        loadFavorites();
    }

    /**
     * Điều hướng Back (cả vuốt lẫn nút đều xử lý như nhau):
     * - Còn lịch sử tab → quay lại tab vừa rời đi.
     * - Đang ở tab khác Home (hết lịch sử) → quay về Home.
     * - Đang ở Home và hết lịch sử → nhấn lần đầu hiện "Nhấn quay lại lần nữa để thoát",
     *   nhấn lần nữa trong 2 giây sẽ thoát app.
     */
    private void setupBackNavigation() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (!navBackStack.isEmpty()) {
                    int prev = navBackStack.pop();
                    isHandlingBack = true;
                    bottomNavigationView.setSelectedItemId(prev);
                    isHandlingBack = false;
                    lastBackPressTime = 0L; // còn điều hướng được thì huỷ trạng thái chờ thoát
                    return;
                }

                if (currentNavId != R.id.nav_home) {
                    isHandlingBack = true;
                    bottomNavigationView.setSelectedItemId(R.id.nav_home);
                    isHandlingBack = false;
                    lastBackPressTime = 0L;
                    return;
                }

                // Ở Home, hết lịch sử → nhấn 2 lần để thoát
                long now = System.currentTimeMillis();
                if (now - lastBackPressTime < EXIT_CONFIRM_WINDOW_MS) {
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                } else {
                    lastBackPressTime = now;
                    Toast.makeText(MainActivity.this, "Nhấn quay lại lần nữa để thoát", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private void initViews() {
        rvFeaturedSales      = findViewById(R.id.rv_featured_sales);
        rvRentalCars         = findViewById(R.id.rv_new_rentals);
        rvBanners            = findViewById(R.id.rv_banners);
        etSearch             = findViewById(R.id.et_search);
        tvGreeting           = findViewById(R.id.tv_greeting);
        bottomNavigationView = findViewById(R.id.bottom_navigation);
        blurNav              = findViewById(R.id.blur_nav);
        fragmentContainer    = findViewById(R.id.fragment_container);
        homeLayout           = findViewById(R.id.home_layout);
        swipeRefreshLayout   = findViewById(R.id.swipe_refresh);
        nestedScroll         = findViewById(R.id.nested_scroll);
        // Chừa đáy danh sách cho thanh menu + thanh điều hướng hệ thống (tránh che nội dung cuối)
        final int baseScrollPadBottom = nestedScroll.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(nestedScroll, (v, insets) -> {
            int navBarHeight = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom;
            v.setPadding(v.getPaddingLeft(), v.getPaddingTop(), v.getPaddingRight(),
                    baseScrollPadBottom + navBarHeight);
            return insets;
        });

        // Quick action buttons
        btnBuyCar    = findViewById(R.id.btn_buy_car);
        btnDriver    = findViewById(R.id.btn_driver);
        btnPoliceCar = findViewById(R.id.btn_police_car);
        btnPayment   = findViewById(R.id.btn_payment);
    }

    private void setupQuickActions() {
        View.OnClickListener openCategory = v -> {
            pulseView(v);
            bottomNavigationView.setSelectedItemId(R.id.nav_cataloge);
        };

        if (btnBuyCar != null) btnBuyCar.setOnClickListener(openCategory);
        if (btnDriver != null) btnDriver.setOnClickListener(openCategory);
        if (btnPoliceCar != null) btnPoliceCar.setOnClickListener(openCategory);
        if (btnPayment != null) btnPayment.setOnClickListener(openCategory);
    }

    private void pulseView(View view) {
        view.animate().cancel();
        view.animate()
                .scaleX(0.94f)
                .scaleY(0.94f)
                .setDuration(70)
                .withEndAction(() -> view.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(120)
                        .setInterpolator(new DecelerateInterpolator())
                        .start())
                .start();
    }

    private void setupBanners() {
        List<Integer> bannerList = new ArrayList<>();
        bannerList.add(R.drawable.banner_1);
        bannerList.add(R.drawable.banner_2);
        bannerList.add(R.drawable.banner_3);
        bannerList.add(R.drawable.banner_4);
        BannerAdapter bannerAdapter = new BannerAdapter(bannerList);
        rvBanners.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        rvBanners.setHasFixedSize(true);
        rvBanners.setAdapter(bannerAdapter);
    }

    private void loadUserGreeting() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;

        de.hdodenhof.circleimageview.CircleImageView imgAvatar = findViewById(R.id.img_avatar);

        // Bấm avatar → mở trang cá nhân
        imgAvatar.setOnClickListener(v -> {
            if (bottomNavigationView != null) bottomNavigationView.setSelectedItemId(R.id.nav_account);
        });

        FirebaseFirestore.getInstance()
                .collection("users")
                .document(user.getUid())
                .get()
                .addOnSuccessListener(doc -> {
                    String fullName = doc.getString("fullName");
                    if (fullName == null || fullName.isEmpty()) fullName = doc.getString("name");
                    if (fullName == null || fullName.isEmpty()) fullName = user.getDisplayName();
                    if (fullName != null && !fullName.isEmpty()) {
                        tvGreeting.setText("Hi, " + fullName);
                    }

                    String photoUrl = doc.getString("photoUrl");
                    if (photoUrl == null || photoUrl.isEmpty()) photoUrl = doc.getString("avatarUrl");
                    if ((photoUrl == null || photoUrl.isEmpty()) && user.getPhotoUrl() != null) {
                        photoUrl = user.getPhotoUrl().toString();
                    }

                    if (photoUrl != null && !photoUrl.isEmpty()) {
                        ImageLoader.loadAvatar(imgAvatar, photoUrl, R.mipmap.ic_launcher_round);
                    }
                })
                .addOnFailureListener(e -> {
                    String name = user.getDisplayName();
                    if (name != null && !name.isEmpty()) tvGreeting.setText("Hi, " + name);
                    if (user.getPhotoUrl() != null) {
                        ImageLoader.loadAvatar(imgAvatar, user.getPhotoUrl().toString(), R.mipmap.ic_launcher_round);
                    }
                });
    }

    private void setupSwipeRefresh() {
        swipeRefreshLayout.setColorSchemeResources(android.R.color.holo_blue_bright);
        swipeRefreshLayout.setOnRefreshListener(() -> {
            saleCarList.clear();
            rentalCarList.clear();
            loadCarsFromFirestore();
        });
    }

    private void setupRecyclerViews() {
        rvFeaturedSales.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        rvRentalCars.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        // Card có kích thước cố định → bỏ đo lại layout mỗi lần đổi dữ liệu, cuộn mượt hơn
        rvFeaturedSales.setHasFixedSize(true);
        rvRentalCars.setHasFixedSize(true);
        // Giữ nhiều view ngoài màn hình hơn để cuộn ngang khỏi bind/tải lại
        rvFeaturedSales.setItemViewCacheSize(8);
        rvRentalCars.setItemViewCacheSize(8);

        carSaleAdapter = new CarSaleAdapter(saleCarList, car -> openCarDetail(car));
        carSaleAdapter.setFavoriteListener(this::toggleFavorite);
        carSaleAdapter.setFavoriteIds(favoriteIds);
        rvFeaturedSales.setAdapter(carSaleAdapter);

        carRentalAdapter = new CarRentalAdapter(rentalCarList, car -> openCarDetail(car));
        carRentalAdapter.setFavoriteListener(this::toggleFavorite);
        carRentalAdapter.setFavoriteIds(favoriteIds);
        rvRentalCars.setAdapter(carRentalAdapter);
    }

    /** Nạp id các xe đã yêu thích để hiển thị trạng thái tim trên card. */
    private void loadFavorites() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            favoriteIds.clear();
        } else {
            FavoriteHelper.loadIds(user.getUid(), ids -> {
                favoriteIds.clear();
                favoriteIds.addAll(ids);
                if (carSaleAdapter != null) carSaleAdapter.setFavoriteIds(favoriteIds);
                if (carRentalAdapter != null) carRentalAdapter.setFavoriteIds(favoriteIds);
            });
            return;
        }
        if (carSaleAdapter != null) carSaleAdapter.setFavoriteIds(favoriteIds);
        if (carRentalAdapter != null) carRentalAdapter.setFavoriteIds(favoriteIds);
    }

    /** Bấm tim: thêm/bỏ xe khỏi danh sách yêu thích. */
    private void toggleFavorite(Car car, boolean makeFavorite) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            Toast.makeText(this, "Đăng nhập để lưu xe yêu thích", Toast.LENGTH_SHORT).show();
            return;
        }
        String id = car.getId();
        if (id == null || id.isEmpty()) return;
        if (makeFavorite) {
            favoriteIds.add(id);
            FavoriteHelper.add(user.getUid(), id);
        } else {
            favoriteIds.remove(id);
            FavoriteHelper.remove(user.getUid(), id);
        }
        if (carSaleAdapter != null) carSaleAdapter.setFavoriteIds(favoriteIds);
        if (carRentalAdapter != null) carRentalAdapter.setFavoriteIds(favoriteIds);
    }

    private void openCarDetail(Car car) {
        Intent intent = new Intent(MainActivity.this, CarDetailActivity.class);
        intent.putExtra("CAR_DATA", car);
        intent.putExtra("CAR_ID", car.getId());
        intent.putExtra("SELLER_ID", car.getSellerId());
        intent.putExtra("CAR_TYPE", car.getType());
        startActivity(intent);
    }

    private void loadCarsFromFirestore() {
        db.collection("cars").get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    saleCarList.clear();
                    rentalCarList.clear();

                    for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                        String name     = doc.getString("name");
                        String price    = doc.getString("price");
                        String info     = doc.getString("info");
                        String type     = doc.getString("type");
                        String sellerId = doc.getString("sellerId");
                        String status   = doc.getString("status");
                        String imageUrl = doc.getString("imageUrl");

                        if (name == null) continue;
                        // Chỉ hiện xe đã được admin duyệt (active) hoặc đang đặt cọc (holding);
                        // ẩn pending/rejected/sold/hidden và xe chưa có trạng thái (null)
                        if (!"active".equals(status) && !"holding".equals(status)) continue;

                        Car car = new Car(name,
                                price    != null ? price    : "",
                                info     != null ? info     : "",
                                android.R.drawable.ic_menu_gallery);
                        car.setId(doc.getId());
                        car.setType(type     != null ? type     : "");
                        car.setImageUrl(imageUrl != null ? imageUrl : "");
                        car.setSellerId(sellerId != null ? sellerId : "");

                        if ("holding".equals(status)) {
                            Car holdingCar = new Car(
                                    "⏳ " + name,
                                    price != null ? price : "",
                                    "Đang có người đặt • " + (info != null ? info : ""),
                                    android.R.drawable.ic_menu_gallery);
                            holdingCar.setId(doc.getId());
                            holdingCar.setType(type     != null ? type     : "");
                            holdingCar.setImageUrl(imageUrl != null ? imageUrl : "");
                            holdingCar.setSellerId(sellerId != null ? sellerId : "");
                            car = holdingCar;
                        }

                        String normalizedType = type != null ? type.toLowerCase().trim() : "";
                        if (normalizedType.equals("sale"))        saleCarList.add(car);
                        else if (normalizedType.equals("rental")) rentalCarList.add(car);
                    }

                    carSaleAdapter.filterList(saleCarList);
                    carRentalAdapter.filterList(rentalCarList);
                    preloadCarImages();
                    setupAutoComplete();
                    if (swipeRefreshLayout != null) swipeRefreshLayout.setRefreshing(false);
                })
                .addOnFailureListener(e -> {
                    loadDummyData();
                    if (swipeRefreshLayout != null) swipeRefreshLayout.setRefreshing(false);
                });
    }

    /** Tải sẵn ảnh các xe vào cache để cuộn ngang/quay lại hiện tức thì (không đổi chất lượng). */
    private void preloadCarImages() {
        for (Car c : saleCarList)   ImageLoader.preload(getApplicationContext(), c.getImageUrl());
        for (Car c : rentalCarList) ImageLoader.preload(getApplicationContext(), c.getImageUrl());
    }

    private void loadDummyData() {
        Car c1 = new Car("Toyota Vios 2020", "450.000.000 VNĐ", "TP.HCM • 2020 • Tự động", android.R.drawable.ic_menu_gallery);
        c1.setType("sale");
        saleCarList.add(c1);

        Car c2 = new Car("Hyundai Accent 2022", "800.000đ / ngày", "Quận 1, TP.HCM • Tự động", android.R.drawable.ic_menu_gallery);
        c2.setType("rental");
        rentalCarList.add(c2);

        carSaleAdapter.filterList(saleCarList);
        carRentalAdapter.filterList(rentalCarList);
    }

    private void setupAutoComplete() {
        List<String> carNames = new ArrayList<>();
        for (Car car : saleCarList)   carNames.add(car.getName());
        for (Car car : rentalCarList) carNames.add(car.getName());
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this, android.R.layout.simple_dropdown_item_1line, carNames);
        etSearch.setAdapter(adapter);
    }

    private void setupSearch() {
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) { filter(s.toString()); }
        });
    }

    private void filter(String text) {
        List<Car> filteredSale = new ArrayList<>();
        for (Car car : saleCarList)
            if (car.getName().toLowerCase().contains(text.toLowerCase()))
                filteredSale.add(car);
        carSaleAdapter.filterList(filteredSale);

        List<Car> filteredRental = new ArrayList<>();
        for (Car car : rentalCarList)
            if (car.getName().toLowerCase().contains(text.toLowerCase()))
                filteredRental.add(car);
        carRentalAdapter.filterList(filteredRental);
    }

    private void setupBottomNavigation() {
        if (bottomNavigationView == null) return;
        bottomNavigationView.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();
            // Ghi nhận tab đang đứng vào lịch sử để Back có thể quay lại (bỏ qua khi đang xử lý Back)
            if (!isHandlingBack && itemId != currentNavId) {
                navBackStack.push(currentNavId);
            }
            int prevNavId = currentNavId;
            currentNavId = itemId;
            // Hướng trượt: sang tab bên phải (+1) thì trang mới vào từ phải, bên trái (-1) thì vào từ trái
            int dir = Integer.signum(navIndex(itemId) - navIndex(prevNavId));

            if (itemId == R.id.nav_home) {
                goHome(prevNavId, dir);
                return true;
            } else if (itemId == R.id.nav_cataloge
                    || itemId == R.id.nav_manage
                    || itemId == R.id.nav_messages
                    || itemId == R.id.nav_account) {
                showFragment(itemId, prevNavId, dir);
                return true;
            }
            return false;
        });
    }

    /** Vị trí của tab trên thanh menu, dùng để xác định chiều trượt trái/phải. */
    private int navIndex(int itemId) {
        if (itemId == R.id.nav_home)     return 0;
        if (itemId == R.id.nav_cataloge) return 1;
        if (itemId == R.id.nav_manage)   return 2;
        if (itemId == R.id.nav_messages) return 3;
        if (itemId == R.id.nav_account)  return 4;
        return 0;
    }

    /** Quay về Home: trượt màn hình Home vào, đẩy fragment đang mở trượt ra cùng chiều. */
    private void goHome(int prevNavId, int dir) {
        homeLayout.setVisibility(View.VISIBLE);
        if (prevNavId != R.id.nav_home) {
            slideViewIn(homeLayout, dir);
            hideActiveFragment(dir);
        }
        // Reset scroll về đầu khi về Home
        if (nestedScroll != null) nestedScroll.smoothScrollTo(0, 0);
    }

    /**
     * Hiện tab dạng fragment với hiệu ứng trượt ngang theo chiều di chuyển.
     * Fragment được giữ lại trong cache: lần đầu mới tạo & add, các lần sau chỉ show/hide
     * nên không phải dựng layout hay tải lại dữ liệu → chuyển tab mượt, không giật.
     */
    private void showFragment(int itemId, int prevNavId, int dir) {
        fragmentContainer.setVisibility(View.VISIBLE);
        // Từ Home chuyển sang tab: trượt Home ra rồi ẩn đi
        if (prevNavId == R.id.nav_home) {
            slideViewOut(homeLayout, dir);
        }

        int enter = dir >= 0 ? R.anim.nav_slide_in_right : R.anim.nav_slide_in_left;
        int exit  = dir >= 0 ? R.anim.nav_slide_out_left : R.anim.nav_slide_out_right;

        Fragment target = fragmentCache.get(itemId);
        FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
        ft.setCustomAnimations(enter, exit);

        if (target == null) {
            target = createFragment(itemId);
            fragmentCache.put(itemId, target);
            ft.add(R.id.fragment_container, target);
        } else {
            ft.show(target);
        }

        if (activeFragment != null && activeFragment != target) {
            ft.hide(activeFragment);
        }
        activeFragment = target;
        ft.commit();
    }

    private Fragment createFragment(int itemId) {
        if (itemId == R.id.nav_cataloge) return new CategoryFragment();
        if (itemId == R.id.nav_manage)   return new ManageFragment();
        if (itemId == R.id.nav_messages) return new MessagesFragment();
        return new ProfileFragment();
    }

    /** Ẩn fragment đang hiện với hiệu ứng trượt ra (khi về Home). */
    private void hideActiveFragment(int dir) {
        if (activeFragment == null) return;
        int enter = dir >= 0 ? R.anim.nav_slide_in_right : R.anim.nav_slide_in_left;
        int exit  = dir >= 0 ? R.anim.nav_slide_out_left : R.anim.nav_slide_out_right;
        getSupportFragmentManager().beginTransaction()
                .setCustomAnimations(enter, exit)
                .hide(activeFragment)
                .commit();
        activeFragment = null;
    }

    private static final int SLIDE_DURATION_MS = 280;


    /** Trượt một View vào màn hình: dir>0 vào từ phải, dir<0 vào từ trái. */
    private void slideViewIn(View v, int dir) {
        int w = v.getWidth();
        if (w <= 0) w = getResources().getDisplayMetrics().widthPixels;
        v.animate().cancel();
        v.setTranslationX(dir * w);
        v.setAlpha(0.6f);
        v.animate()
                .translationX(0f).alpha(1f)
                .setDuration(SLIDE_DURATION_MS)
                .setInterpolator(new DecelerateInterpolator())
                .start();
    }

    /** Trượt một View ra khỏi màn hình rồi ẩn: dir>0 ra bên trái, dir<0 ra bên phải. */
    private void slideViewOut(View v, int dir) {
        int w = v.getWidth();
        if (w <= 0) w = getResources().getDisplayMetrics().widthPixels;
        v.animate().cancel();
        v.animate()
                .translationX(-dir * w).alpha(0.6f)
                .setDuration(SLIDE_DURATION_MS)
                .setInterpolator(new DecelerateInterpolator())
                .withEndAction(() -> {
                    v.setVisibility(View.GONE);
                    v.setTranslationX(0f);
                    v.setAlpha(1f);
                })
                .start();
    }
}