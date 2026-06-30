package com.example.doanmb.data.repository;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.doanmb.data.model.Car;
import com.example.doanmb.data.model.Review;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class CarRepository {

    private static final String COL_CARS    = "cars";
    private static final String COL_USERS   = "users";
    private static final String COL_REPORTS = "reports";

    private CarRepository() {}

    private static FirebaseFirestore db() { return FirebaseFirestore.getInstance(); }


    public interface OnCar { void onLoaded(DocumentSnapshot doc); void onError(String message); }

    public interface OnCars { void onLoaded(List<Car> cars); void onError(String message); }

    public interface OnIds { void onLoaded(List<String> carIds); }

    public interface OnOwner { void onLoaded(String ownerId); }

    public interface OnContact { void onLoaded(String name, String phone); void onError(String message); }

    public interface OnResult { void onSuccess(); void onError(String message); }

    public interface OnCreated { void onCreated(String carId); void onError(String message); }

    public interface OnReviews { void onLoaded(List<Review> reviews); }

    public interface OnRating { void onLoaded(double avgRating, long reviewCount); }

    public interface OnAvailable { void onResult(boolean available); }


    public static void loadCar(@Nullable String carId, @NonNull OnCar cb) {
        if (carId == null || carId.isEmpty()) { cb.onError("Thiếu mã xe"); return; }
        db().collection(COL_CARS).document(carId).get()
                .addOnSuccessListener(cb::onLoaded)
                .addOnFailureListener(e -> cb.onError(e.getMessage()));
    }

    public static void loadOwner(@Nullable String carId, @NonNull OnOwner cb) {
        if (carId == null || carId.isEmpty()) { cb.onLoaded(null); return; }
        db().collection(COL_CARS).document(carId).get()
                .addOnSuccessListener(doc -> {
                    String owner = doc != null && doc.exists() ? doc.getString("sellerId") : null;
                    if ((owner == null || owner.isEmpty()) && doc != null) owner = doc.getString("userId");
                    cb.onLoaded(owner != null && !owner.isEmpty() ? owner : null);
                })
                .addOnFailureListener(e -> cb.onLoaded(null));
    }

    public static void loadUserContact(@Nullable String uid, @NonNull OnContact cb) {
        if (uid == null || uid.isEmpty()) { cb.onError("Thiếu thông tin người dùng"); return; }
        db().collection(COL_USERS).document(uid).get()
                .addOnSuccessListener(doc -> {
                    if (doc == null || !doc.exists()) { cb.onError("Không tìm thấy người dùng"); return; }
                    cb.onLoaded(doc.getString("name"), doc.getString("phone"));
                })
                .addOnFailureListener(e -> cb.onError(e.getMessage()));
    }

    public static void loadMyPosts(@Nullable String userId, @NonNull OnCars cb) {
        if (userId == null || userId.isEmpty()) { cb.onLoaded(new ArrayList<>()); return; }
        db().collection(COL_CARS).whereEqualTo("userId", userId).get()
                .addOnSuccessListener(snapshots -> {
                    List<Car> cars = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : snapshots) {
                        String name = doc.getString("name");
                        if (name == null) continue;
                        String price = doc.getString("price");
                        String info = doc.getString("info");
                        String type = doc.getString("type");
                        String status = doc.getString("status");
                        String imageUrl = doc.getString("imageUrl");
                        String sellerId = doc.getString("sellerId");
                        if (sellerId == null) sellerId = doc.getString("userId");

                        Car car = new Car(name, price != null ? price : "",
                                info != null ? info : "", android.R.drawable.ic_menu_gallery);
                        if ("holding".equals(status)) {
                            car = new Car("⏳ " + name, price != null ? price : "",
                                    "Đang có người đặt cọc • " + (info != null ? info : ""),
                                    android.R.drawable.ic_menu_gallery);
                        }
                        car.setId(doc.getId());
                        car.setType(type != null ? type : "");
                        car.setImageUrl(imageUrl != null ? imageUrl : "");
                        car.setSellerId(sellerId != null ? sellerId : "");
                        cars.add(car);
                    }
                    cb.onLoaded(cars);
                })
                .addOnFailureListener(e -> cb.onError(e.getMessage()));
    }

    public static void loadMyCarIds(@Nullable String userId, @NonNull OnIds cb) {
        if (userId == null || userId.isEmpty()) { cb.onLoaded(new ArrayList<>()); return; }
        db().collection(COL_CARS).whereEqualTo("userId", userId).get()
                .addOnSuccessListener(snap -> {
                    List<String> ids = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : snap) ids.add(doc.getId());
                    cb.onLoaded(ids);
                })
                .addOnFailureListener(e -> cb.onLoaded(new ArrayList<>()));
    }

    public static void setStatus(@Nullable String carId, @NonNull String status,
                                 @Nullable OnResult cb) {
        if (carId == null || carId.isEmpty()) { if (cb != null) cb.onError("Thiếu mã xe"); return; }
        db().collection(COL_CARS).document(carId).update("status", status)
                .addOnSuccessListener(v -> { if (cb != null) cb.onSuccess(); })
                .addOnFailureListener(e -> { if (cb != null) cb.onError(e.getMessage()); });
    }

    public static void updatePost(@Nullable String carId, @NonNull Map<String, Object> fields,
                                  @NonNull OnResult cb) {
        if (carId == null || carId.isEmpty()) { cb.onError("Thiếu mã xe"); return; }
        db().collection(COL_CARS).document(carId).update(fields)
                .addOnSuccessListener(v -> cb.onSuccess())
                .addOnFailureListener(e -> cb.onError(e.getMessage()));
    }

    public static void deletePost(@Nullable String carId, @NonNull OnResult cb) {
        if (carId == null || carId.isEmpty()) { cb.onError("Thiếu mã xe"); return; }
        db().collection(COL_CARS).document(carId).delete()
                .addOnSuccessListener(v -> cb.onSuccess())
                .addOnFailureListener(e -> cb.onError(e.getMessage()));
    }

    public static void addCar(@NonNull Map<String, Object> car, @NonNull OnCreated cb) {
        db().collection(COL_CARS).add(car)
                .addOnSuccessListener(ref -> cb.onCreated(ref.getId()))
                .addOnFailureListener(e -> cb.onError(e.getMessage()));
    }

    public static void submitReport(@NonNull Map<String, Object> report, @NonNull OnResult cb) {
        db().collection(COL_REPORTS).add(report)
                .addOnSuccessListener(ref -> cb.onSuccess())
                .addOnFailureListener(e -> cb.onError(e.getMessage()));
    }

    public static void loadDriverReviews(@Nullable String driverId, @NonNull OnReviews cb) {
        if (driverId == null || driverId.isEmpty()) { cb.onLoaded(new ArrayList<>()); return; }
        db().collection("reviews")
                .whereEqualTo("driverId", driverId)
                // orderBy("createdAt") cần composite index — bỏ tạm để query chạy được
                .limit(5)
                .get()
                .addOnSuccessListener(snap -> {
                    List<Review> list = new ArrayList<>();
                    for (QueryDocumentSnapshot d : snap) {
                        Review r = d.toObject(Review.class);
                        r.setReviewId(d.getId());
                        list.add(r);
                    }
                    cb.onLoaded(list);
                })
                .addOnFailureListener(e -> {
                    android.util.Log.e("CarRepository", "loadDriverReviews loi: " + e.getMessage());
                    cb.onLoaded(new ArrayList<>());
                });
    }

    public static void loadDriverRating(@Nullable String driverId, @NonNull OnRating cb) {
        if (driverId == null || driverId.isEmpty()) { cb.onLoaded(0, 0); return; }
        db().collection("drivers").document(driverId).get()
                .addOnSuccessListener(doc -> {
                    if (doc == null || !doc.exists()) { cb.onLoaded(0, 0); return; }
                    Double avg = doc.getDouble("avgRating");
                    Long count = doc.getLong("reviewCount");
                    cb.onLoaded(avg != null ? avg : 0, count != null ? count : 0);
                })
                .addOnFailureListener(e -> cb.onLoaded(0, 0));
    }

    public static void loadDriverAvailability(@Nullable String driverId, @NonNull OnAvailable cb) {
        if (driverId == null || driverId.isEmpty()) { cb.onResult(true); return; }
        db().collection("drivers").document(driverId).get()
                .addOnSuccessListener(doc -> {
                    boolean available = doc == null || !doc.exists()
                            || !doc.contains("isAvailable")
                            || Boolean.TRUE.equals(doc.getBoolean("isAvailable"));
                    cb.onResult(available);
                })
                .addOnFailureListener(e -> cb.onResult(true));
    }
}