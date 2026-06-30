package com.example.doanmb.ui.admin.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.doanmb.data.repository.CarRepository;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

/**
 * ViewModel màn admin xem chi tiết 1 tài xế: hồ sơ, rating, số chuyến hoàn thành
 * và thu nhập. Chuyến của tài xế = order có sellerId == tài xế.
 */
public class AdminDriverProfileViewModel extends ViewModel {

    /** Điểm đánh giá trung bình + số lượt của tài xế. */
    public static class Rating {
        public final double avg;
        public final long count;
        public Rating(double avg, long count) { this.avg = avg; this.count = count; }
    }

    private final MutableLiveData<DocumentSnapshot> user = new MutableLiveData<>();
    private final MutableLiveData<Integer> trips = new MutableLiveData<>();
    private final MutableLiveData<Long> income = new MutableLiveData<>();
    private final MutableLiveData<Rating> rating = new MutableLiveData<>();
    private final MutableLiveData<String> message = new MutableLiveData<>();
    private final MutableLiveData<Boolean> finishEvent = new MutableLiveData<>();

    private String userId;

    public LiveData<DocumentSnapshot> getUser() { return user; }
    public LiveData<Integer> getTrips() { return trips; }
    public LiveData<Long> getIncome() { return income; }
    public LiveData<Rating> getRating() { return rating; }
    public LiveData<String> getMessage() { return message; }
    public LiveData<Boolean> getFinishEvent() { return finishEvent; }

    private static FirebaseFirestore db() { return FirebaseFirestore.getInstance(); }

    public void start(String userId) {
        if (this.userId != null) return; // đã tải
        this.userId = userId;
        loadUser();
        loadTripStats();
        CarRepository.loadDriverRating(userId, (avg, count) -> rating.setValue(new Rating(avg, count)));
    }

    private void loadUser() {
        db().collection("users").document(userId).get()
                .addOnSuccessListener(doc -> {
                    if (!doc.exists()) {
                        message.setValue("Tài xế không còn tồn tại");
                        finishEvent.setValue(true);
                        return;
                    }
                    user.setValue(doc);
                })
                .addOnFailureListener(e -> {
                    message.setValue("Lỗi tải tài xế: " + e.getMessage());
                    finishEvent.setValue(true);
                });
    }

    private void loadTripStats() {
        db().collection("orders").whereEqualTo("sellerId", userId).get()
                .addOnSuccessListener(snap -> {
                    int completed = 0;
                    long total = 0;
                    for (QueryDocumentSnapshot o : snap) {
                        if (!"completed".equals(o.getString("status"))) continue;
                        completed++;
                        total += orderAmount(o);
                    }
                    trips.setValue(completed);
                    income.setValue(total);
                })
                .addOnFailureListener(e -> {
                    trips.setValue(0);
                    income.setValue(0L);
                });
    }

    /** Giá trị 1 đơn: ưu tiên totalAmount (số), fallback parse carPrice dạng chuỗi. */
    private long orderAmount(DocumentSnapshot o) {
        Object total = o.get("totalAmount");
        if (total instanceof Number) return ((Number) total).longValue();
        String cp = o.getString("carPrice");
        if (cp != null) {
            String d = cp.replaceAll("[^0-9]", "");
            if (!d.isEmpty()) {
                try { return Long.parseLong(d); } catch (NumberFormatException ignored) {}
            }
        }
        return 0;
    }
}
