package com.example.doanmb.ui.car.viewmodel;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.doanmb.data.model.Car;
import com.example.doanmb.data.model.Review;
import com.example.doanmb.data.repository.CarRepository;
import com.example.doanmb.data.repository.FavoriteRepository;
import com.example.doanmb.data.repository.OrderRepository;
import com.example.doanmb.data.repository.WalletRepository;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class CarDetailViewModel extends ViewModel {

    public static class CarDetail {
        public List<String> images = new ArrayList<>();
        public String fuel, condition;
        public long pricePerDay, pricePerKm;
    }

    public static class Contact {
        public String name, phone;
        public Contact(String name, String phone) { this.name = name; this.phone = phone; }
    }

    public static class TypeInfo {
        public String type;
        public boolean isOwner;
        public TypeInfo(String type, boolean isOwner) { this.type = type; this.isOwner = isOwner; }
    }

    public enum Kind { BUY, RENT, TRIP }

    public static class OrderSent {
        public Kind kind;
        public String orderId;
        public String customerName;
        public boolean scheduleReminder;
        public String message;
    }

    public static class PostEdited {
        public String name, price, info;
        public PostEdited(String n, String p, String i) { name = n; price = p; info = i; }
    }

    private static final int REVIEW_PAGE_SIZE = 3;
    private com.google.firebase.firestore.DocumentSnapshot lastReviewSnapshot = null;
    private boolean reviewLoading = false;
    private boolean reviewAllLoaded = false;

    private final androidx.lifecycle.MutableLiveData<Boolean> reviewLoadingMore =
            new androidx.lifecycle.MutableLiveData<>(false);
    private final androidx.lifecycle.MutableLiveData<Boolean> reviewNoMore =
            new androidx.lifecycle.MutableLiveData<>(false);

    public androidx.lifecycle.LiveData<Boolean> getReviewLoadingMore() { return reviewLoadingMore; }
    public androidx.lifecycle.LiveData<Boolean> getReviewNoMore()      { return reviewNoMore; }
    public static class RentForm {
        public String renterName, renterPhone, renterCccd, startDate, note;
        public int days;
        public boolean tripMode;
        public String pickup, dest;
        public double distanceKm;
        public String paymentMethod = "cash";
    }

    private final MutableLiveData<CarDetail> detail = new MutableLiveData<>();
    private final MutableLiveData<Contact> contact = new MutableLiveData<>();
    private final MutableLiveData<TypeInfo> typeInfo = new MutableLiveData<>();
    private final MutableLiveData<Long> walletBalance = new MutableLiveData<>(0L);
    private final MutableLiveData<Boolean> isFavorite = new MutableLiveData<>(false);
    private final MutableLiveData<String> message = new MutableLiveData<>();
    private final MutableLiveData<Boolean> needLogin = new MutableLiveData<>();
    private final MutableLiveData<Boolean> sendEnabled = new MutableLiveData<>(true);
    private final MutableLiveData<OrderSent> orderSent = new MutableLiveData<>();
    private final MutableLiveData<PostEdited> postEdited = new MutableLiveData<>();
    private final MutableLiveData<Boolean> finishEvent = new MutableLiveData<>();
    private final MutableLiveData<List<Review>> reviews = new MutableLiveData<>();
    private final MutableLiveData<String> ratingText = new MutableLiveData<>();
    private final MutableLiveData<Boolean> showReviews = new MutableLiveData<>(false);

    public LiveData<CarDetail> getDetail() { return detail; }
    public LiveData<Contact> getContact() { return contact; }
    public LiveData<TypeInfo> getTypeInfo() { return typeInfo; }
    public LiveData<Long> getWalletBalance() { return walletBalance; }
    public LiveData<Boolean> getIsFavorite() { return isFavorite; }
    public LiveData<String> getMessage() { return message; }
    public LiveData<Boolean> getNeedLogin() { return needLogin; }
    public LiveData<Boolean> getSendEnabled() { return sendEnabled; }
    public LiveData<OrderSent> getOrderSent() { return orderSent; }
    public LiveData<PostEdited> getPostEdited() { return postEdited; }
    public LiveData<Boolean> getFinishEvent() { return finishEvent; }
    public LiveData<List<Review>> getReviews() { return reviews; }
    public LiveData<String> getRatingText() { return ratingText; }
    public LiveData<Boolean> getShowReviews() { return showReviews; }

    private Car car;
    private String carId, sellerId, sellerName = "", carType;
    private String carStatus = "", statusBeforeHide = "";
    private boolean initialized = false;

    public boolean isHidden() { return "hidden".equals(carStatus); }

    public long currentWalletBalance() {
        Long b = walletBalance.getValue();
        return b != null ? b : 0L;
    }

    public void init(Car car, String carId, String sellerId, String carType) {
        if (initialized) return;
        initialized = true;
        this.car = car;
        this.carId = carId;
        this.sellerId = sellerId;
        this.carType = carType;

        if ((this.carId == null || this.carId.isEmpty()) && car != null && car.getId() != null) {
            this.carId = car.getId();
        }
        if ((this.sellerId == null || this.sellerId.isEmpty()) && car != null && car.getSellerId() != null) {
            this.sellerId = car.getSellerId();
        }

        if (this.carId != null && !this.carId.isEmpty()) loadCarDetail();

        if (this.sellerId != null && !this.sellerId.isEmpty()) {
            loadSellerInfo();
        } else {
            contact.setValue(new Contact(null, null));
            emitTypeInfo(carType);
            loadDriverReviewsAndRating();
        }

        loadWalletBalance();
        loadFavorite();
    }

    private void loadDriverReviewsAndRating() {
        if (!isDriverType(carType) || sellerId == null || sellerId.isEmpty()) {
            showReviews.setValue(false);
            return;
        }
        showReviews.setValue(true);
        CarRepository.loadDriverReviews(sellerId, list -> {
            if (list != null) {
                list.sort((a, b) -> {
                    com.google.firebase.Timestamp ta = a.getCreatedAt();
                    com.google.firebase.Timestamp tb = b.getCreatedAt();
                    if (ta == null && tb == null) return 0;
                    if (ta == null) return 1;
                    if (tb == null) return -1;
                    return tb.compareTo(ta);
                });
            }
            reviews.setValue(list);
        });
        CarRepository.loadDriverRating(sellerId, (avg, count) -> {
            if (count > 0) ratingText.setValue(String.format(Locale.US, "⭐ %.1f  (%d đánh giá)", avg, count));
        });
    }

    private void loadCarDetail() {
        CarRepository.loadCar(carId, new CarRepository.OnCar() {
            @Override public void onLoaded(DocumentSnapshot doc) {
                if (doc == null || !doc.exists()) return;

                String type = doc.getString("type");
                if (type != null) carType = type;
                String status = doc.getString("status");
                carStatus = status != null ? status : "";
                String prev = doc.getString("statusBeforeHide");
                statusBeforeHide = prev != null ? prev : "";

                Long ppd = doc.getLong("pricePerDay");
                Long ppk = doc.getLong("pricePerKm");

                CarDetail d = new CarDetail();
                d.pricePerDay = ppd != null ? ppd : parseMoney(doc.getString("price"));
                d.pricePerKm  = ppk != null ? ppk : 0L;
                d.fuel = doc.getString("fuel");
                d.condition = doc.getString("condition");
                d.images = extractImageUrls(doc.get("imageUrls"));
                if (d.images.isEmpty()) {
                    String imageUrl = doc.getString("imageUrl");
                    if (imageUrl != null && !imageUrl.isEmpty()) d.images.add(imageUrl);
                }
                detail.setValue(d);

                String sName = doc.getString("sellerName");
                String sPhone = doc.getString("sellerPhone");
                if (sName != null && !sName.isEmpty()) sellerName = sName;
                contact.setValue(new Contact(
                        sName != null && !sName.isEmpty() ? sName : currentContactName(),
                        sPhone != null && !sPhone.isEmpty() ? sPhone : currentContactPhone()));

                emitTypeInfo(carType);
                loadDriverReviewsAndRating();
            }
            @Override public void onError(String msg) { }
        });
    }

    private String currentContactName() {
        Contact c = contact.getValue();
        return c != null ? c.name : null;
    }

    private String currentContactPhone() {
        Contact c = contact.getValue();
        return c != null ? c.phone : null;
    }

    private void loadSellerInfo() {
        CarRepository.loadUserContact(sellerId, new CarRepository.OnContact() {
            @Override public void onLoaded(String name, String phone) {
                if (name != null && !name.isEmpty()) sellerName = name;
                contact.setValue(new Contact(name, phone));
            }
            @Override public void onError(String msg) {  }
        });
        if (carType != null) loadDriverReviewsAndRating();
    }

    public void loadFirstReviews(String driverId) {
        if (driverId == null || driverId.isEmpty()) return;
        lastReviewSnapshot = null;
        reviewAllLoaded    = false;
        reviewLoading      = false;
        showReviews.setValue(true);

        com.google.firebase.firestore.FirebaseFirestore.getInstance()
                .collection("reviews")
                .whereEqualTo("driverId", driverId)
                // Bỏ orderBy để tránh lỗi thiếu Firestore composite index
                .limit(REVIEW_PAGE_SIZE)
                .get()
                .addOnSuccessListener(snap -> {
                    List<Review> list = new ArrayList<>();
                    for (com.google.firebase.firestore.QueryDocumentSnapshot d : snap) {
                        Review r = d.toObject(Review.class);
                        r.setReviewId(d.getId());
                        list.add(r);
                    }
                    list.sort((a, b) -> {
                        com.google.firebase.Timestamp ta = a.getCreatedAt();
                        com.google.firebase.Timestamp tb = b.getCreatedAt();
                        if (ta == null && tb == null) return 0;
                        if (ta == null) return 1;
                        if (tb == null) return -1;
                        return tb.compareTo(ta); // DESC
                    });
                    if (!snap.isEmpty())
                        lastReviewSnapshot = snap.getDocuments()
                                .get(snap.size() - 1);
                    if (snap.size() < REVIEW_PAGE_SIZE) {
                        reviewAllLoaded = true;
                        reviewNoMore.postValue(true);
                    }
                    reviews.postValue(list);
                })
                .addOnFailureListener(e -> {
                    android.util.Log.e("CarDetailVM", "loadFirstReviews loi: " + e.getMessage());
                    // fallback: query đơn giản, sort client-side
                    com.example.doanmb.data.repository.CarRepository.loadDriverReviews(driverId, list -> {
                        if (list != null) {
                            list.sort((a, b) -> {
                                com.google.firebase.Timestamp ta = a.getCreatedAt();
                                com.google.firebase.Timestamp tb = b.getCreatedAt();
                                if (ta == null && tb == null) return 0;
                                if (ta == null) return 1;
                                if (tb == null) return -1;
                                return tb.compareTo(ta);
                            });
                        }
                        reviews.postValue(list);
                    });
                });
    }

    public void loadMoreReviews(String driverId) {
        if (reviewLoading || reviewAllLoaded || lastReviewSnapshot == null) return;
        reviewLoading = true;
        reviewLoadingMore.postValue(true);

        com.google.firebase.firestore.FirebaseFirestore.getInstance()
                .collection("reviews")
                .whereEqualTo("driverId", driverId)
                .orderBy("createdAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .startAfter(lastReviewSnapshot)
                .limit(REVIEW_PAGE_SIZE)
                .get()
                .addOnSuccessListener(snap -> {
                    reviewLoading = false;
                    reviewLoadingMore.postValue(false);

                    if (snap.isEmpty()) {
                        reviewAllLoaded = true;
                        reviewNoMore.postValue(true);
                        return;
                    }

                    lastReviewSnapshot = snap.getDocuments().get(snap.size() - 1);
                    if (snap.size() < REVIEW_PAGE_SIZE) {
                        reviewAllLoaded = true;
                        reviewNoMore.postValue(true);
                    }

                    List<Review> current = reviews.getValue();
                    List<Review> merged  = new ArrayList<>(current != null ? current : new ArrayList<>());
                    for (com.google.firebase.firestore.QueryDocumentSnapshot d : snap) {
                        Review r = d.toObject(Review.class);
                        r.setReviewId(d.getId());
                        merged.add(r);
                    }
                    merged.sort((a, b) -> {
                        com.google.firebase.Timestamp ta = a.getCreatedAt();
                        com.google.firebase.Timestamp tb = b.getCreatedAt();
                        if (ta == null && tb == null) return 0;
                        if (ta == null) return 1;
                        if (tb == null) return -1;
                        return tb.compareTo(ta);
                    });
                    reviews.postValue(merged);
                })
                .addOnFailureListener(e -> {
                    reviewLoading = false;
                    reviewLoadingMore.postValue(false);
                });
    }

    private void loadWalletBalance() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;
        WalletRepository.loadBalance(user.getUid(), new WalletRepository.BalanceCallback() {
            @Override public void onLoaded(long value) { walletBalance.setValue(value); }
            @Override public void onError(String msg) { /* giữ 0 */ }
        });
    }

    private void emitTypeInfo(String type) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        boolean isOwner = user != null && sellerId != null && user.getUid().equals(sellerId);
        typeInfo.setValue(new TypeInfo(type, isOwner));
    }

    private void loadFavorite() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null || carId == null || carId.isEmpty()) return;
        FavoriteRepository.contains(user.getUid(), carId, fav -> isFavorite.setValue(fav));
    }

    public void toggleFavorite() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) { message.setValue("Đăng nhập để lưu xe yêu thích"); return; }
        if (carId == null || carId.isEmpty()) return;
        boolean fav = !Boolean.TRUE.equals(isFavorite.getValue());
        if (fav) FavoriteRepository.add(user.getUid(), carId);
        else FavoriteRepository.remove(user.getUid(), carId);
        isFavorite.setValue(fav);
    }

    public void sendBuyRequest(String note) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) { needLogin.setValue(true); return; }

        OrderRepository.hasPendingOrder(user.getUid(), carId, hasPending -> {
            if (hasPending) {
                message.setValue("⏳ Đã gửi yêu cầu. Vui lòng chờ người bán phản hồi.");
                return;
            }
            doSendBuyRequest(user, note);
        });
    }

    private void doSendBuyRequest(FirebaseUser user, String note) {
        long total   = parseMoney(car != null ? car.getPrice() : null);
        long deposit = WalletRepository.deposit(total); // cọc 50% giá xe

        if (total <= 0) { message.setValue("Giá xe không hợp lệ"); return; }
        if (currentWalletBalance() < deposit) {
            message.setValue("Số dư ví không đủ để đặt cọc " + money(deposit)
                    + " đ. Vui lòng nhờ admin nạp tiền.");
            return;
        }

        Map<String, Object> order = new HashMap<>();
        order.put("buyerId",  user.getUid());
        order.put("sellerId", sellerId != null ? sellerId : "");
        order.put("sellerName", sellerName);
        order.put("carId",    carId != null ? carId : "");
        order.put("carName",  car != null ? car.getName() : "");
        order.put("carPrice", car != null ? car.getPrice() : "");
        order.put("type",     "Mua xe");
        order.put("note",     note != null ? note.trim() : "");
        order.put("totalAmount",   total);
        order.put("depositAmount", deposit);
        order.put("paymentMethod", "cash");
        order.put("depositStatus", "held");
        order.put("status",   "pending");
        order.put("createdAt", Timestamp.now());

        sendEnabled.setValue(false);
        OrderRepository.createOrder(order, new OrderRepository.OnCreated() {
            @Override public void onCreated(String orderId) {
                // Giữ cọc: trừ tiền ví người mua
                WalletRepository.holdDeposit(user.getUid(), deposit, orderId,
                        new WalletRepository.Callback() {
                            @Override public void onSuccess() {
                                walletBalance.setValue(currentWalletBalance() - deposit);
                                sendEnabled.setValue(true);
                                String buyerName = user.getDisplayName();
                                if (buyerName == null || buyerName.isEmpty()) buyerName = "Khách hàng";
                                emitOrderSent(Kind.BUY, orderId, buyerName, true,
                                        "✅ Đã gửi yêu cầu mua! Giữ cọc " + money(deposit) + " đ.");
                            }
                            @Override public void onError(String msg) {
                                OrderRepository.deleteOrder(orderId);
                                sendEnabled.setValue(true);
                                message.setValue("Không giữ được cọc: " + msg);
                            }
                        });
            }
            @Override public void onError(String msg) {
                sendEnabled.setValue(true);
                message.setValue("Lỗi tạo đơn: " + msg);
            }
        });
    }

    public void sendRentRequest(RentForm form) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) { message.setValue("Vui lòng đăng nhập!"); return; }

        // Xe có tài xế → kiểm tra tài xế đang online trước khi tạo đơn
        if (isDriverType(carType) && sellerId != null && !sellerId.isEmpty()) {
            CarRepository.loadDriverAvailability(sellerId, available -> {
                if (!available) {
                    message.setValue("Tài xế hiện offline hoặc tạm thời không nhận chuyến. Vui lòng chọn tài xế khác.");
                    return;
                }
                proceedRent(user, form);
            });
        } else {
            proceedRent(user, form);
        }
    }

    private void proceedRent(FirebaseUser user, RentForm form) {
        if (form.tripMode) { sendTripRequest(user, form); return; }

        OrderRepository.hasPendingOrder(user.getUid(), carId, hasPending -> {
            if (hasPending) {
                message.setValue("⏳ Đã gửi yêu cầu. Vui lòng chờ người cho thuê phản hồi.");
                return;
            }
            doSendRentRequest(user, form);
        });
    }

    private void doSendRentRequest(FirebaseUser user, RentForm form) {
        if (form.renterName.isEmpty() || form.renterPhone.isEmpty() || form.renterCccd.isEmpty()) {
            message.setValue("Vui lòng nhập đầy đủ thông tin người thuê");
            return;
        }
        if (form.days <= 0) {
            message.setValue("Vui lòng nhập số ngày thuê hợp lệ");
            return;
        }

        long pricePerDay = parseMoney(car != null ? car.getPrice() : null);
        long total = pricePerDay * form.days;
        boolean needDeposit = WalletRepository.requiresDeposit(form.days);
        long deposit = needDeposit ? WalletRepository.deposit(total) : 0L;

        if (needDeposit && currentWalletBalance() < deposit) {
            message.setValue("Số dư ví không đủ để đặt cọc " + money(deposit)
                    + " đ. Vui lòng nhờ admin nạp tiền.");
            return;
        }

        Map<String, Object> order = new HashMap<>();
        order.put("buyerId",      user.getUid());
        order.put("renterName",   form.renterName);
        order.put("renterPhone",  form.renterPhone);
        order.put("renterCccd",   form.renterCccd);
        order.put("sellerId",     sellerId != null ? sellerId : "");
        order.put("sellerName",   sellerName);
        order.put("carId",        carId != null ? carId : "");
        order.put("carName",      car != null ? car.getName() : "");
        order.put("carPrice",     car != null ? car.getPrice() : "");
        order.put("type",         isDriverType(carType) ? "Có tài xế" : "Thuê xe");
        order.put("days",         String.valueOf(form.days));
        order.put("startDate",    form.startDate != null ? form.startDate.trim() : "");
        order.put("note",         form.note != null ? form.note.trim() : "");
        order.put("totalAmount",  total);
        order.put("depositAmount", deposit);
        order.put("paymentMethod", form.paymentMethod);
        order.put("depositStatus", needDeposit ? "held" : "none");
        order.put("status",       "pending");
        order.put("createdAt",    Timestamp.now());

        sendEnabled.setValue(false);
        OrderRepository.createOrder(order, new OrderRepository.OnCreated() {
            @Override public void onCreated(String orderId) {
                if (!needDeposit) {
                    sendEnabled.setValue(true);
                    emitOrderSent(Kind.RENT, orderId, form.renterName, true,
                            "✅ Gửi yêu cầu thuê xe thành công!");
                    return;
                }
                // Thuê dài ngày → giữ cọc 50% (trừ ví khách) giống đơn mua.
                WalletRepository.holdDeposit(user.getUid(), deposit, orderId,
                        new WalletRepository.Callback() {
                            @Override public void onSuccess() {
                                walletBalance.setValue(currentWalletBalance() - deposit);
                                sendEnabled.setValue(true);
                                emitOrderSent(Kind.RENT, orderId, form.renterName, true,
                                        "✅ Đặt xe thành công! Đã giữ cọc " + money(deposit) + " đ.");
                            }
                            @Override public void onError(String msg) {
                                OrderRepository.deleteOrder(orderId);
                                sendEnabled.setValue(true);
                                message.setValue("Không giữ được cọc: " + msg);
                            }
                        });
            }
            @Override public void onError(String msg) {
                sendEnabled.setValue(true);
                message.setValue("Lỗi tạo đơn: " + msg);
            }
        });
    }

    private void sendTripRequest(FirebaseUser user, RentForm form) {
        if (form.renterName.isEmpty() || form.renterPhone.isEmpty()) {
            message.setValue("Vui lòng nhập họ tên và số điện thoại");
            return;
        }
        if (form.distanceKm <= 0 || form.pickup == null || form.dest == null) {
            message.setValue("Hãy chọn điểm đón & đến trên bản đồ");
            return;
        }
        long pricePerKm = detailPricePerKm();
        if (pricePerKm <= 0) {
            message.setValue("Tài xế này không nhận đặt theo chuyến");
            return;
        }

        long total = Math.round(form.distanceKm * pricePerKm);
        String tripNote = "Chuyến: " + form.pickup + " → " + form.dest
                + " (" + form.distanceKm + " km). Tổng: " + money(total) + " đ."
                + (form.note != null && !form.note.trim().isEmpty() ? " Ghi chú: " + form.note.trim() : "");

        Map<String, Object> order = new HashMap<>();
        order.put("buyerId",      user.getUid());
        order.put("renterName",   form.renterName);
        order.put("renterPhone",  form.renterPhone);
        order.put("sellerId",     sellerId != null ? sellerId : "");
        order.put("sellerName",   sellerName);
        order.put("carId",        carId != null ? carId : "");
        order.put("carName",      car != null ? car.getName() : "");
        order.put("carPrice",     money(pricePerKm) + " đ/km");
        order.put("type",         "Có tài xế");
        order.put("rentMode",     "distance");
        order.put("pickup",       form.pickup);
        order.put("destination",  form.dest);
        order.put("distanceKm",   form.distanceKm);
        order.put("note",         tripNote);
        order.put("totalAmount",  total);
        order.put("depositAmount", 0L);
        order.put("paymentMethod", form.paymentMethod);
        order.put("depositStatus", "none");
        order.put("status",       "pending");
        order.put("createdAt",    Timestamp.now());

        sendEnabled.setValue(false);
        OrderRepository.createOrder(order, new OrderRepository.OnCreated() {
            @Override public void onCreated(String orderId) {
                sendEnabled.setValue(true);
                emitOrderSent(Kind.TRIP, orderId, form.renterName, false,
                        "✅ Đã gửi yêu cầu đặt chuyến! Chờ tài xế xác nhận.");
            }
            @Override public void onError(String msg) {
                sendEnabled.setValue(true);
                message.setValue("Lỗi tạo đơn: " + msg);
            }
        });
    }

    private long detailPricePerKm() {
        CarDetail d = detail.getValue();
        return d != null ? d.pricePerKm : 0L;
    }

    private void emitOrderSent(Kind kind, String orderId, String customerName,
                               boolean scheduleReminder, String msg) {
        OrderSent e = new OrderSent();
        e.kind = kind;
        e.orderId = orderId;
        e.customerName = customerName;
        e.scheduleReminder = scheduleReminder;
        e.message = msg;
        orderSent.setValue(e);
    }

    public void editPost(String name, String price, String info) {
        if (carId == null || carId.isEmpty()) return;
        if (name.isEmpty() || price.isEmpty()) {
            message.setValue("Tiêu đề và giá không được để trống!");
            return;
        }
        Map<String, Object> update = new HashMap<>();
        update.put("name", name);
        update.put("price", price);
        update.put("info", info);
        CarRepository.updatePost(carId, update, new CarRepository.OnResult() {
            @Override public void onSuccess() {
                postEdited.setValue(new PostEdited(name, price, info));
                message.setValue("✅ Đã cập nhật bài viết!");
            }
            @Override public void onError(String msg) { message.setValue("Lỗi: " + msg); }
        });
    }

    public void toggleHidePost() {
        if (carId == null || carId.isEmpty()) return;
        if (isHidden()) {
            String restored = statusBeforeHide.isEmpty() ? "active" : statusBeforeHide;
            Map<String, Object> update = new HashMap<>();
            update.put("status", restored);
            update.put("statusBeforeHide", FieldValue.delete());
            CarRepository.updatePost(carId, update, new CarRepository.OnResult() {
                @Override public void onSuccess() {
                    carStatus = restored;
                    statusBeforeHide = "";
                    message.setValue("✅ Bài viết đã hiển thị trở lại!");
                }
                @Override public void onError(String msg) { message.setValue("Lỗi: " + msg); }
            });
        } else {
            final String prev = carStatus;
            Map<String, Object> update = new HashMap<>();
            update.put("status", "hidden");
            update.put("statusBeforeHide", prev);
            CarRepository.updatePost(carId, update, new CarRepository.OnResult() {
                @Override public void onSuccess() {
                    statusBeforeHide = prev;
                    carStatus = "hidden";
                    message.setValue("🙈 Đã ẩn bài viết khỏi danh sách!");
                }
                @Override public void onError(String msg) { message.setValue("Lỗi: " + msg); }
            });
        }
    }

    public void deletePost() {
        if (carId == null || carId.isEmpty()) return;
        CarRepository.deletePost(carId, new CarRepository.OnResult() {
            @Override public void onSuccess() {
                message.setValue("🗑 Đã xóa bài viết!");
                finishEvent.setValue(true);
            }
            @Override public void onError(String msg) { message.setValue("Lỗi: " + msg); }
        });
    }

    public void submitReport(String reason) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;
        Map<String, Object> report = new HashMap<>();
        report.put("reporterId", user.getUid());
        report.put("targetId", carId);
        report.put("reason", reason);
        report.put("status", "pending");
        report.put("createdAt", Timestamp.now());
        CarRepository.submitReport(report, new CarRepository.OnResult() {
            @Override public void onSuccess() { message.setValue("✅ Đã gửi báo cáo!"); }
            @Override public void onError(String msg) { /* im lặng như bản gốc */ }
        });
    }

    public String getSellerId() { return sellerId; }
    public String getCarId() { return carId; }
    public Car getCar() { return car; }


    public static boolean isDriverType(String type) {
        if (type == null) return false;
        String t = type.toLowerCase();
        return t.contains("driver") || t.contains("tai xe") || t.contains("tài xế");
    }

    public static boolean isRentalType(String type) {
        if (type == null) return false;
        String t = type.toLowerCase();
        return t.contains("rental") || t.contains("rent") || t.contains("thue") || t.contains("thuê") || t.contains("tu lai");
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

    private static long parseMoney(String s) {
        if (s == null) return 0;
        String d = s.replaceAll("[^0-9]", "");
        if (d.isEmpty()) return 0;
        try { return Long.parseLong(d); } catch (NumberFormatException e) { return 0; }
    }

    private static String money(long amount) {
        return java.text.NumberFormat.getInstance(new Locale("vi", "VN")).format(amount);
    }
}