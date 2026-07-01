package com.example.doanmb.ui.home.view;

import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.cardview.widget.CardView;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.doanmb.R;
import com.example.doanmb.ui.car.adapter.ProfileCarAdapter;
import com.example.doanmb.data.model.Car;
import com.example.doanmb.data.model.Place;
import com.example.doanmb.ui.car.view.CarDetailActivity;
import com.example.doanmb.data.remote.VietnamLocationApi;
import com.example.doanmb.ui.car.view.PostCarFragment;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.text.Normalizer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class CategoryFragment extends Fragment implements PostCarFragment.OnPostCarSubmittedListener {

    private static final String CATEGORY_SALE = "sale";
    private static final String CATEGORY_SELL = "sell";
    private static final String CATEGORY_RENTAL = "rental";
    private static final String CATEGORY_DRIVER = "driver";
    private static final String BRAND_ALL = "all";
    private static final int SORT_NONE = 0;
    private static final int SORT_PRICE_ASC = 1;
    private static final int SORT_PRICE_DESC = 2;
    private static final String[] KNOWN_BRANDS = {"Toyota", "Honda", "Mazda", "Kia", "Ford", "Hyundai", "VinFast", "Khác"};
    private static final String LOCATION_ALL = "Tất cả khu vực";

    private CardView cardBuyCar;
    private CardView cardSellCar;
    private CardView cardSelfDrive;
    private CardView cardWithDriver;
    private LinearLayout tabBuyCarContent;
    private LinearLayout tabSellCarContent;
    private LinearLayout tabSelfDriveContent;
    private LinearLayout tabWithDriverContent;
    private TextView tvTabBuyCar;
    private TextView tvTabSellCar;
    private TextView tvTabSelfDrive;
    private TextView tvTabWithDriver;
    private TextView tvResultTitle;
    private TextView tvCategoryCount;
    private TextView tvEmptyCategory;
    private LinearLayout layoutCategoryBrowseContent;
    private LinearLayout layoutBrandFilters;
    private FrameLayout postCarFragmentContainer;
    private RecyclerView rvCategoryCars;
    private LinearLayout cardSearchForm;
    private TextView tvBrandLabel;
    private View scrollBrandFilters;
    private boolean filtersCollapsed = false;

    private final List<Car> allCars = new ArrayList<>();
    private final List<String> brandFilters = new ArrayList<>();
    private ProfileCarAdapter carAdapter;
    private TextView tvSearchLocation;
    private LinearLayout layoutTimeFilter;
    private TextView tvTimeLabel;
    private TextView tvSearchTime;
    private String currentCategory = CATEGORY_SALE;
    private String currentTitle = "Xe đang bán";
    private String selectedBrand = BRAND_ALL;
    private String selectedLocation = ""; // rỗng = tất cả khu vực
    private int sortMode = SORT_NONE;
    private TextView tvSortPrice;
    private Calendar pickupTime;
    private Calendar returnTime;
    private final SimpleDateFormat timeFmt = new SimpleDateFormat("dd/MM/yyyy", new Locale("vi"));


    private static final List<String> LOCATION_STOPWORDS = java.util.Arrays.asList(
            "thanh", "pho", "tinh", "quan", "huyen", "phuong", "xa", "thi", "viet", "nam");

    private List<Place> provincesCache;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_category, container, false);

        cardBuyCar = view.findViewById(R.id.card_buy_car);
        cardSellCar = view.findViewById(R.id.card_sell_car);
        cardSelfDrive = view.findViewById(R.id.card_self_drive);
        cardWithDriver = view.findViewById(R.id.card_with_driver);
        tabBuyCarContent = view.findViewById(R.id.tab_buy_car_content);
        tabSellCarContent = view.findViewById(R.id.tab_sell_car_content);
        tabSelfDriveContent = view.findViewById(R.id.tab_self_drive_content);
        tabWithDriverContent = view.findViewById(R.id.tab_with_driver_content);
        tvTabBuyCar = view.findViewById(R.id.tv_tab_buy_car);
        tvTabSellCar = view.findViewById(R.id.tv_tab_sell_car);
        tvTabSelfDrive = view.findViewById(R.id.tv_tab_self_drive);
        tvTabWithDriver = view.findViewById(R.id.tv_tab_with_driver);
        tvResultTitle = view.findViewById(R.id.tv_category_result_title);
        tvCategoryCount = view.findViewById(R.id.tv_category_count);
        tvEmptyCategory = view.findViewById(R.id.tv_empty_category);
        layoutCategoryBrowseContent = view.findViewById(R.id.layout_category_browse_content);
        layoutBrandFilters = view.findViewById(R.id.layout_brand_filters);
        postCarFragmentContainer = view.findViewById(R.id.post_car_fragment_container);
        rvCategoryCars = view.findViewById(R.id.rv_category_cars);
        cardSearchForm = view.findViewById(R.id.card_search_form);
        tvBrandLabel = view.findViewById(R.id.tv_brand_label);
        scrollBrandFilters = view.findViewById(R.id.scroll_brand_filters);

        rvCategoryCars.setLayoutManager(new LinearLayoutManager(getContext()));
        setupCollapseOnScroll();
        carAdapter = new ProfileCarAdapter(new ArrayList<>(), car -> {
            Intent intent = new Intent(getActivity(), CarDetailActivity.class);
            intent.putExtra("CAR_DATA", car);
            intent.putExtra("CAR_ID", car.getId());
            intent.putExtra("SELLER_ID", car.getSellerId());
            intent.putExtra("CAR_TYPE", car.getType());
            // Mang thời gian người dùng đã chọn ở Category sang form đặt xe có tài xế / thuê xe
            if (pickupTime != null) intent.putExtra("PICKUP_TIME", timeFmt.format(pickupTime.getTime()));
            if (returnTime != null) intent.putExtra("RETURN_TIME", timeFmt.format(returnTime.getTime()));
            startActivity(intent);
        });
        rvCategoryCars.setAdapter(carAdapter);

        TextView btnSearchCar = view.findViewById(R.id.btn_search_car);
        if (btnSearchCar != null) {
            btnSearchCar.setOnClickListener(v -> performSearch());
        }

        tvSearchLocation = view.findViewById(R.id.tv_search_location);
        if (tvSearchLocation != null) {
            tvSearchLocation.setText(LOCATION_ALL);
            tvSearchLocation.setOnClickListener(v -> openLocationPicker());
        }

        layoutTimeFilter = view.findViewById(R.id.layout_time_filter);
        tvTimeLabel = view.findViewById(R.id.tv_time_label);
        tvSearchTime = view.findViewById(R.id.tv_search_time);
        if (tvSearchTime != null) {
            tvSearchTime.setOnClickListener(v -> showTimePicker());
        }

        tvSortPrice = view.findViewById(R.id.tv_sort_price);
        if (tvSortPrice != null) {
            tvSortPrice.setOnClickListener(v -> showSortDialog());
            updateSortLabel();
        }

        setupCategoryActions();
        setupDefaultBrandFilters();
        loadCars();

        return view;
    }

    private void setupCollapseOnScroll() {
        rvCategoryCars.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                if (dy > 6) {
                    setFiltersCollapsed(true);
                } else if (dy < -6 || !recyclerView.canScrollVertically(-1)) {
                    setFiltersCollapsed(false);
                }
            }
        });
    }

    private void setFiltersCollapsed(boolean collapse) {
        if (filtersCollapsed == collapse) return;
        filtersCollapsed = collapse;

        ViewGroup parent = (ViewGroup) layoutCategoryBrowseContent;
        if (parent != null) {
            androidx.transition.TransitionManager.beginDelayedTransition(parent);
        }
        int visibility = collapse ? View.GONE : View.VISIBLE;
        if (cardSearchForm != null) cardSearchForm.setVisibility(visibility);
        if (tvBrandLabel != null) tvBrandLabel.setVisibility(visibility);
        if (scrollBrandFilters != null) scrollBrandFilters.setVisibility(visibility);
    }

    private void setupCategoryActions() {
        cardBuyCar.setOnClickListener(v -> selectCategory(CATEGORY_SALE, "Xe đang bán"));
        cardSelfDrive.setOnClickListener(v -> selectCategory(CATEGORY_RENTAL, "Xe thuê tự lái"));
        cardWithDriver.setOnClickListener(v -> selectCategory(CATEGORY_DRIVER, "Xe có tài xế"));
        cardSellCar.setOnClickListener(v -> showPostCarForm());

        updateSelectedCategory();
    }

    private void selectCategory(String category, String title) {
        currentCategory = category;
        currentTitle = title;
        showBrowseContent();
        updateSelectedCategory();
        applyFilter();
    }

    /** Mở form đăng tin bán xe (PostCarFragment) ngay trong tab "Bán xe". */
    private void showPostCarForm() {
        currentCategory = CATEGORY_SELL;
        updateSelectedCategory();

        layoutCategoryBrowseContent.setVisibility(View.GONE);
        postCarFragmentContainer.setVisibility(View.VISIBLE);

        if (getChildFragmentManager().findFragmentById(R.id.post_car_fragment_container) == null) {
            getChildFragmentManager().beginTransaction()
                    .replace(R.id.post_car_fragment_container, new PostCarFragment())
                    .commit();
        }
    }

    /** Hiện khu vực duyệt xe và ẩn form đăng tin. */
    private void showBrowseContent() {
        layoutCategoryBrowseContent.setVisibility(View.VISIBLE);
        postCarFragmentContainer.setVisibility(View.GONE);
    }

    /** Callback từ PostCarFragment: đăng tin xong thì nạp lại danh sách xe. */
    @Override
    public void onPostCarSubmitted() {
        loadCars();
    }

    /** Tô sáng tab đang chọn, bỏ chọn các tab còn lại và cập nhật hiển thị ô thời gian. */
    private void updateSelectedCategory() {
        cardBuyCar.setCardBackgroundColor(Color.TRANSPARENT);
        cardSellCar.setCardBackgroundColor(Color.TRANSPARENT);
        cardSelfDrive.setCardBackgroundColor(Color.TRANSPARENT);
        cardWithDriver.setCardBackgroundColor(Color.TRANSPARENT);

        setTabSelected(tabBuyCarContent, tvTabBuyCar, currentCategory.equals(CATEGORY_SALE));
        setTabSelected(tabSellCarContent, tvTabSellCar, currentCategory.equals(CATEGORY_SELL));
        setTabSelected(tabSelfDriveContent, tvTabSelfDrive, currentCategory.equals(CATEGORY_RENTAL));
        setTabSelected(tabWithDriverContent, tvTabWithDriver, currentCategory.equals(CATEGORY_DRIVER));

        updateTimeFilterVisibility();
    }

    private void updateTimeFilterVisibility() {
        boolean show = currentCategory.equals(CATEGORY_DRIVER) || currentCategory.equals(CATEGORY_RENTAL);
        if (layoutTimeFilter != null) {
            layoutTimeFilter.setVisibility(show ? View.VISIBLE : View.GONE);
        }
        if (tvTimeLabel != null) {
            tvTimeLabel.setText(currentCategory.equals(CATEGORY_DRIVER)
                    ? "Thời gian cần tài xế" : "Thời gian thuê");
        }
    }

    private void showTimePicker() {
        if (getContext() == null) return;
        final Calendar now = Calendar.getInstance();
        DatePickerDialog dateDialog = new DatePickerDialog(requireContext(), (dp, year, month, day) -> {
            final Calendar pick = Calendar.getInstance();
            pick.set(year, month, day, 0, 0, 0);
            pick.set(Calendar.MILLISECOND, 0);
            pickupTime = pick;
            promptReturnTime();
        }, now.get(Calendar.YEAR), now.get(Calendar.MONTH), now.get(Calendar.DAY_OF_MONTH));
        dateDialog.getDatePicker().setMinDate(now.getTimeInMillis());
        dateDialog.show();
    }

    private void promptReturnTime() {
        if (getContext() == null || pickupTime == null) return;
        final Calendar start = (Calendar) pickupTime.clone();
        DatePickerDialog dateDialog = new DatePickerDialog(requireContext(), (dp, year, month, day) -> {
            final Calendar ret = Calendar.getInstance();
            ret.set(year, month, day, 0, 0, 0);
            ret.set(Calendar.MILLISECOND, 0);
            if (ret.before(pickupTime)) {
                Toast.makeText(getContext(), "Ngày trả phải từ ngày đón trở đi", Toast.LENGTH_SHORT).show();
                return;
            }
            returnTime = ret;
            updateTimeDisplay();
            applyFilter();
        }, start.get(Calendar.YEAR), start.get(Calendar.MONTH), start.get(Calendar.DAY_OF_MONTH));
        dateDialog.getDatePicker().setMinDate(start.getTimeInMillis());
        dateDialog.show();
    }

    /** Cập nhật nhãn ô thời gian: "ngày đón → ngày trả" hoặc "Chọn thời gian" khi chưa chọn. */
    private void updateTimeDisplay() {
        if (tvSearchTime == null) return;
        if (pickupTime == null) {
            tvSearchTime.setText("Chọn thời gian");
            return;
        }
        String text = timeFmt.format(pickupTime.getTime());
        if (returnTime != null) {
            text += "  →  " + timeFmt.format(returnTime.getTime());
        }
        tvSearchTime.setText(text);
    }

    /** Đổi nền/màu chữ của một tab tuỳ trạng thái được chọn hay không. */
    private void setTabSelected(LinearLayout tabContent, TextView tabLabel, boolean selected) {
        if (selected) {
            tabContent.setBackgroundResource(R.drawable.bg_tab_active_pill);
            tabLabel.setTextColor(Color.parseColor("#2F54D4"));
        } else {
            tabContent.setBackground(null);
            tabLabel.setTextColor(Color.WHITE);
        }
    }

    /**
     * Nạp danh sách xe từ Firestore (collection "cars"). Chỉ giữ xe có status
     * "active" (đã duyệt) hoặc "holding" (đang đặt cọc); lỗi mạng thì dùng dữ liệu mẫu.
     */
    private void loadCars() {
        tvEmptyCategory.setText("Đang tải danh sách...");
        tvEmptyCategory.setVisibility(View.VISIBLE);
        rvCategoryCars.setVisibility(View.GONE);

        FirebaseFirestore.getInstance()
                .collection("cars")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {

                    if (!isAdded() || getActivity() == null) return;

                    allCars.clear();
                    for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                        String name = doc.getString("name");
                        if (name == null || name.trim().isEmpty()) continue;

                        String status = doc.getString("status");
                        if (!"active".equals(status) && !"holding".equals(status)) continue;

                        String price = doc.getString("price");
                        String info = doc.getString("info");
                        String type = doc.getString("type");
                        String brand = doc.getString("brand");
                        String imageUrl = doc.getString("imageUrl");
                        String sellerId = doc.getString("sellerId");
                        Car car = new Car(
                                name,
                                price != null ? price : "",
                                info != null ? info : "",
                                type != null ? type : "",
                                resolveBrand(name, info, brand),
                                android.R.drawable.ic_menu_gallery
                        );
                        car.setId(doc.getId());
                        car.setImageUrl(imageUrl != null ? imageUrl : "");
                        car.setSellerId(sellerId != null ? sellerId : "");

                        Double driverRating = doc.getDouble("driverRating");
                        Long driverReviewCount = doc.getLong("driverReviewCount");
                        car.setDriverRating(driverRating != null ? driverRating.floatValue() : 0f);
                        car.setDriverReviewCount(driverReviewCount != null ? driverReviewCount.intValue() : 0);

                        allCars.add(car);
                    }
                    updateBrandFilters();
                    applyFilter();
                })
                .addOnFailureListener(e -> {
                    // Đảm bảo an toàn khi thất bại
                    if (!isAdded() || getActivity() == null) return;
                    allCars.clear();
                    loadSampleCars();
                    updateBrandFilters();
                    applyFilter();
                });
    }

    /** Dữ liệu xe mẫu dùng khi không kết nối được Firestore, để UI không trống. */
    private void loadSampleCars() {
        allCars.add(new Car("Toyota Vios 2020", "450.000.000 VNĐ", "TP.HCM - 2020 - Tự động", "sale", "Toyota", android.R.drawable.ic_menu_gallery));
        allCars.add(new Car("Kia Morning 2021", "320.000.000 VNĐ", "Hà Nội - 2021 - Số sàn", "sale", "Kia", android.R.drawable.ic_menu_gallery));
        allCars.add(new Car("Hyundai Accent 2022", "800.000đ / ngày", "Quận 1, TP.HCM - Tự lái", "rental", "Hyundai", android.R.drawable.ic_menu_gallery));
        allCars.add(new Car("Toyota Fortuner 2023", "1.600.000đ / ngày", "TP.HCM - Có tài xế", "driver", "Toyota", android.R.drawable.ic_menu_gallery));
    }

    private void performSearch() {
        showBrowseContent();
        applyFilter();
        if (rvCategoryCars != null) rvCategoryCars.scrollToPosition(0);
        if (getContext() != null && tvCategoryCount != null) {
            Toast.makeText(getContext(), tvCategoryCount.getText(), Toast.LENGTH_SHORT).show();
        }
    }

    private void openLocationPicker() {
        if (getContext() == null) return;
        if (provincesCache != null) {
            showProvinceDialog(provincesCache);
            return;
        }
        if (tvSearchLocation != null) tvSearchLocation.setText("Đang tải khu vực...");
        VietnamLocationApi.fetchProvinces(new VietnamLocationApi.Callback() {
            @Override
            public void onResult(List<Place> places) {
                if (!isAdded()) return;
                provincesCache = places;
                restoreLocationLabel();
                showProvinceDialog(places);
            }

            @Override
            public void onError(String message) {
                if (!isAdded()) return;
                restoreLocationLabel();
                if (getContext() != null) {
                    Toast.makeText(getContext(), "Lỗi tải khu vực: " + message, Toast.LENGTH_LONG).show();
                }
            }
        });
    }

    private void restoreLocationLabel() {
        if (tvSearchLocation == null) return;
        tvSearchLocation.setText(
                (selectedLocation == null || selectedLocation.isEmpty()) ? LOCATION_ALL : selectedLocation);
    }
    private void showProvinceDialog(List<Place> places) {
        if (getContext() == null) return;
        final String[] items = new String[places.size() + 1];
        items[0] = LOCATION_ALL;
        for (int i = 0; i < places.size(); i++) {
            items[i + 1] = places.get(i).name;
        }
        new AlertDialog.Builder(requireContext())
                .setTitle("Chọn tỉnh/thành phố")
                .setItems(items, (dialog, which) -> {
                    if (which == 0) {
                        selectedLocation = "";
                        restoreLocationLabel();
                        applyFilter();
                    } else {
                        openWardPicker(places.get(which - 1));
                    }
                })
                .show();
    }

    private void openWardPicker(Place province) {
        if (getContext() == null) return;
        if (tvSearchLocation != null) tvSearchLocation.setText("Đang tải phường/xã...");
        VietnamLocationApi.fetchWards(province.code, new VietnamLocationApi.Callback() {
            @Override
            public void onResult(List<Place> wards) {
                if (!isAdded()) return;
                restoreLocationLabel();
                if (wards.isEmpty()) {
                    selectedLocation = province.name;
                    restoreLocationLabel();
                    applyFilter();
                    return;
                }
                showWardDialog(province, wards);
            }
            @Override
            public void onError(String message) {
                if (!isAdded()) return;
                // Lỗi tải phường/xã → vẫn lọc được theo tỉnh.
                selectedLocation = province.name;
                restoreLocationLabel();
                applyFilter();
            }
        });
    }
    private void showWardDialog(Place province, List<Place> wards) {
        if (getContext() == null) return;
        final String[] items = new String[wards.size() + 1];
        items[0] = "Toàn " + province.name;
        for (int i = 0; i < wards.size(); i++) {
            items[i + 1] = wards.get(i).name;
        }
        new AlertDialog.Builder(requireContext())
                .setTitle(province.name)
                .setItems(items, (dialog, which) -> {
                    selectedLocation = (which == 0)
                            ? province.name
                            : wards.get(which - 1).name + ", " + province.name;
                    restoreLocationLabel();
                    applyFilter();
                })
                .setNegativeButton("Quay lại", (d, w) -> showProvinceDialog(provincesCache))
                .show();
    }

    /**
     * @return true nếu xe khớp khu vực đang chọn. Khớp cả chuỗi trước; nếu không, bỏ các từ
     * chỉ đơn vị hành chính rồi khớp theo từng từ khoá còn lại. Chưa chọn khu vực thì luôn true.
     */
    private boolean matchesSelectedLocation(Car car) {
        if (selectedLocation == null || selectedLocation.isEmpty()) return true;
        String haystack = normalizeText((car.getInfo() != null ? car.getInfo() : "")
                + " " + (car.getName() != null ? car.getName() : ""));
        String needle = normalizeText(selectedLocation);
        if (needle.isEmpty()) return true;
        if (haystack.contains(needle)) return true;
        for (String token : needle.split("[\\s,]+")) {
            if (token.length() >= 3 && !LOCATION_STOPWORDS.contains(token) && haystack.contains(token)) {
                return true;
            }
        }
        return false;
    }

    private void showSortDialog() {
        if (getContext() == null) return;
        final String[] items = {"Mặc định", "Giá: thấp → cao", "Giá: cao → thấp"};
        new AlertDialog.Builder(requireContext())
                .setTitle("Sắp xếp")
                .setSingleChoiceItems(items, sortMode, (dialog, which) -> {
                    sortMode = which;
                    updateSortLabel();
                    applyFilter();
                    dialog.dismiss();
                })
                .show();
    }

    /** Cập nhật nhãn nút sắp xếp theo chế độ đang chọn. */
    private void updateSortLabel() {
        if (tvSortPrice == null) return;
        switch (sortMode) {
            case SORT_PRICE_ASC:
                tvSortPrice.setText("Giá thấp → cao");
                break;
            case SORT_PRICE_DESC:
                tvSortPrice.setText("Giá cao → thấp");
                break;
            default:
                tvSortPrice.setText("Sắp xếp");
        }
    }

    private long parsePrice(String price) {
        if (price == null) return 0L;
        String digits = price.replaceAll("[^0-9]", "");
        if (digits.isEmpty()) return 0L;
        try {
            return Long.parseLong(digits);
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    /**
     * Lọc {@link #allCars} theo danh mục + hãng + khu vực đang chọn, sắp xếp theo giá
     * (nếu có), rồi cập nhật danh sách, tiêu đề, số tin phù hợp và trạng thái rỗng.
     */
    private void applyFilter() {
        List<Car> filteredCars = new ArrayList<>();

        for (Car car : allCars) {
            String type = normalizeText(car.getType());
            boolean matchesCategory =
                    (currentCategory.equals(CATEGORY_SALE) && isSaleType(type))
                            || (currentCategory.equals(CATEGORY_RENTAL) && isRentalType(type) && !isDriverType(type))
                            || (currentCategory.equals(CATEGORY_DRIVER) && isDriverType(type));

            if (matchesCategory && matchesSelectedBrand(car) && matchesSelectedLocation(car)) {
                filteredCars.add(car);
            }
        }

        if (sortMode == SORT_PRICE_ASC) {
            java.util.Collections.sort(filteredCars,
                    (a, b) -> Long.compare(parsePrice(a.getPrice()), parsePrice(b.getPrice())));
        } else if (sortMode == SORT_PRICE_DESC) {
            java.util.Collections.sort(filteredCars,
                    (a, b) -> Long.compare(parsePrice(b.getPrice()), parsePrice(a.getPrice())));
        }

        carAdapter.updateList(filteredCars);
        tvResultTitle.setText(currentTitle);

        String countText = filteredCars.size() + " tin phù hợp";
        boolean timeRelevant = currentCategory.equals(CATEGORY_DRIVER) || currentCategory.equals(CATEGORY_RENTAL);
        if (timeRelevant && pickupTime != null) {
            countText += "  ·  " + timeFmt.format(pickupTime.getTime());
            if (returnTime != null) {
                countText += " → " + timeFmt.format(returnTime.getTime());
            }
        }
        tvCategoryCount.setText(countText);

        boolean isEmpty = filteredCars.isEmpty();
        rvCategoryCars.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
        tvEmptyCategory.setText("Chưa có xe trong danh mục này");
        tvEmptyCategory.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
    }

    /** Khởi tạo dải lọc hãng mặc định: "Tất cả" + danh sách hãng phổ biến. */
    private void setupDefaultBrandFilters() {
        brandFilters.clear();
        brandFilters.add(BRAND_ALL);
        for (String brand : KNOWN_BRANDS) {
            addBrandFilter(brand);
        }
        renderBrandFilters();
    }

    /** Dựng lại dải lọc hãng từ hãng mặc định cộng các hãng suy ra từ dữ liệu xe thực tế. */
    private void updateBrandFilters() {
        setupDefaultBrandFilters();
        for (Car car : allCars) {
            addBrandFilter(resolveBrand(car.getName(), car.getInfo(), car.getBrand()));
        }

        if (!brandFilters.contains(selectedBrand)) {
            selectedBrand = BRAND_ALL;
        }
        renderBrandFilters();
    }

    /** Thêm một hãng vào dải lọc nếu chưa tồn tại (so sánh sau khi chuẩn hoá, tránh trùng). */
    private void addBrandFilter(String brand) {
        if (brand == null || brand.trim().isEmpty()) return;

        for (String existingBrand : brandFilters) {
            if (normalizeText(existingBrand).equals(normalizeText(brand))) {
                return;
            }
        }
        brandFilters.add(brand.trim());
    }

    /** Vẽ lại các chip hãng theo {@link #brandFilters}; chip đang chọn được tô màu nổi bật. */
    private void renderBrandFilters() {
        if (layoutBrandFilters == null) return;

        if (!isAdded() || getContext() == null) return;

        layoutBrandFilters.removeAllViews();
        for (String brand : brandFilters) {
            boolean selected = selectedBrand.equals(brand);
            TextView chip = new TextView(requireContext());
            chip.setText(brand.equals(BRAND_ALL) ? "Tất cả" : brand);
            chip.setTextColor(selected ? Color.WHITE : Color.parseColor("#333333"));
            chip.setTextSize(13);
            chip.setGravity(android.view.Gravity.CENTER);
            chip.setPadding(dp(14), dp(8), dp(14), dp(8));
            chip.setBackground(createChipBackground(selected));
            chip.setOnClickListener(v -> {
                selectedBrand = brand;
                renderBrandFilters();
                applyFilter();
            });

            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            params.setMarginEnd(dp(8));
            layoutBrandFilters.addView(chip, params);
        }
    }

    /** Tạo nền bo góc cho chip hãng (màu xanh khi được chọn, trắng viền xám khi không). */
    private GradientDrawable createChipBackground(boolean selected) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);
        drawable.setCornerRadius(dp(18));
        drawable.setColor(selected ? Color.parseColor("#1976D2") : Color.WHITE);
        drawable.setStroke(dp(1), selected ? Color.parseColor("#1976D2") : Color.parseColor("#DADCE0"));
        return drawable;
    }

    /** @return true nếu xe khớp hãng đang chọn (hoặc đang chọn "Tất cả"). */
    private boolean matchesSelectedBrand(Car car) {
        if (selectedBrand.equals(BRAND_ALL)) return true;
        String carBrand = resolveBrand(car.getName(), car.getInfo(), car.getBrand());
        return normalizeText(carBrand).equals(normalizeText(selectedBrand));
    }

    /**
     * Xác định hãng xe: ưu tiên trường {@code brand}; nếu trống thì dò tên hãng trong
     * tên/mô tả xe; không khớp hãng nào thì trả về "Khác".
     */
    private String resolveBrand(String name, String info, String brand) {
        if (brand != null && !brand.trim().isEmpty()) {
            return brand.trim();
        }

        String source = normalizeText((name != null ? name : "") + " " + (info != null ? info : ""));
        for (String knownBrand : KNOWN_BRANDS) {
            if (!knownBrand.equals("Khác") && source.contains(normalizeText(knownBrand))) {
                return knownBrand;
            }
        }
        return "Khác";
    }

    /** @return true nếu chuỗi loại (đã chuẩn hoá) thuộc nhóm xe bán. */
    private boolean isSaleType(String type) {
        return type.contains("sale") || type.contains("ban") || type.contains("mua");
    }

    /** @return true nếu chuỗi loại (đã chuẩn hoá) thuộc nhóm xe cho thuê. */
    private boolean isRentalType(String type) {
        return type.contains("rental") || type.contains("rent") || type.contains("thue") || type.contains("tu lai");
    }

    /** @return true nếu chuỗi loại (đã chuẩn hoá) thuộc nhóm xe có tài xế. */
    private boolean isDriverType(String type) {
        return type.contains("driver") || type.contains("tai xe") || type.contains("co tai xe");
    }

    /** Chuẩn hoá chuỗi để so khớp "mềm": bỏ dấu tiếng Việt, chuyển thường, cắt khoảng trắng thừa. */
    private String normalizeText(String value) {
        if (value == null) return "";
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFD);
        return normalized.replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT).trim();
    }

    /** Đổi dp sang px theo mật độ màn hình hiện tại. */
    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}