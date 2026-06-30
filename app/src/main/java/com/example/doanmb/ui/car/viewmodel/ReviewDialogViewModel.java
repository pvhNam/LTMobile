package com.example.doanmb.ui.car.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.doanmb.data.repository.ReviewRepository;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.HashMap;
import java.util.Map;

/**
 * ViewModel cho hộp thoại đánh giá tài xế: validate, đọc thông tin người đánh giá,
 * gửi đánh giá qua {@link ReviewRepository} (transaction) rồi đồng bộ điểm sang mọi xe.
 * Việc dismiss/setResult/Toast do View thực hiện khi observe các LiveData bên dưới.
 */
public class ReviewDialogViewModel extends ViewModel {

    private String orderId, driverId, carId, notificationId;

    private final MutableLiveData<String>  message    = new MutableLiveData<>();
    private final MutableLiveData<Boolean> submitting = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> success    = new MutableLiveData<>();

    public LiveData<String>  getMessage()    { return message; }
    public LiveData<Boolean> getSubmitting() { return submitting; }
    public LiveData<Boolean> getSuccess()    { return success; }

    /** Gọi 1 lần khi mở dialog để truyền tham số. */
    public void init(String orderId, String driverId, String carId, String notificationId) {
        this.orderId        = orderId;
        this.driverId       = driverId;
        this.carId          = carId;
        this.notificationId = notificationId != null ? notificationId : "";
    }

    public void submitReview(float rating, String comment) {
        if (rating == 0f) { message.setValue("Vui lòng chọn số sao"); return; }

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) { message.setValue("Bạn cần đăng nhập"); return; }

        submitting.setValue(true);
        final String uid = user.getUid();

        ReviewRepository.loadUserBrief(uid, (buyerName, buyerAvatar) -> {
            Map<String, Object> reviewData = new HashMap<>();
            reviewData.put("orderId",     orderId);
            reviewData.put("driverId",    driverId);
            reviewData.put("carId",       carId);
            reviewData.put("buyerId",     uid);
            reviewData.put("buyerName",   buyerName != null ? buyerName : "Ẩn danh");
            reviewData.put("buyerAvatar", buyerAvatar != null ? buyerAvatar : "");
            reviewData.put("rating",      rating);
            reviewData.put("comment",     comment != null ? comment.trim() : "");
            reviewData.put("createdAt",   Timestamp.now());
            reviewData.put("type",        "driver");

            ReviewRepository.submitReview(reviewData, orderId, driverId, carId, notificationId, rating,
                    new ReviewRepository.OnReviewSubmitted() {
                        @Override public void onSuccess(double newAvg, long newCount) {
                            message.setValue("✅ Cảm ơn bạn đã đánh giá!");
                            success.setValue(true);
                            // Đồng bộ điểm cho mọi xe của tài xế (chạy nền, không chặn UI).
                            ReviewRepository.syncAllDriverCars(driverId, newAvg, newCount);
                        }
                        @Override public void onError(String msg) {
                            submitting.setValue(false);
                            message.setValue("Lỗi: " + msg);
                        }
                    });
        });
    }
}
