package com.example.doanmb.ui.car.view;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RatingBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.example.doanmb.R;
import com.google.android.material.chip.Chip;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

/**
 * Dialog đánh giá tài xế.
 *
 * Cách dùng:
 *   ReviewDialogFragment.newInstance(orderId, driverId, carId)
 *       .show(getSupportFragmentManager(), "review");
 */
public class ReviewDialogFragment extends DialogFragment {

    private static final String ARG_ORDER_ID       = "orderId";
    private static final String ARG_DRIVER_ID      = "driverId";
    private static final String ARG_CAR_ID         = "carId";
    private static final String ARG_NOTIFICATION_ID = "notificationId";

    private RatingBar ratingBar;
    private EditText etComment;
    private Button btnSubmit, btnCancel;

    private String orderId, driverId, carId, notificationId;
    private FirebaseFirestore db;

    /** Gọi từ OrderHistoryAdapter (không có notificationId) */
    public static ReviewDialogFragment newInstance(String orderId, String driverId, String carId) {
        return newInstance(orderId, driverId, carId, "");
    }

    /** Gọi từ NotifAdapter (có notificationId để cập nhật actionCompleted) */
    public static ReviewDialogFragment newInstance(String orderId, String driverId,
                                                   String carId, String notificationId) {
        ReviewDialogFragment f = new ReviewDialogFragment();
        Bundle args = new Bundle();
        args.putString(ARG_ORDER_ID,        orderId);
        args.putString(ARG_DRIVER_ID,       driverId);
        args.putString(ARG_CAR_ID,          carId);
        args.putString(ARG_NOTIFICATION_ID, notificationId != null ? notificationId : "");
        f.setArguments(args);
        return f;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            orderId        = getArguments().getString(ARG_ORDER_ID);
            driverId       = getArguments().getString(ARG_DRIVER_ID);
            carId          = getArguments().getString(ARG_CAR_ID);
            notificationId = getArguments().getString(ARG_NOTIFICATION_ID, "");
        }
        db = FirebaseFirestore.getInstance();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.dialog_review, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        ratingBar  = view.findViewById(R.id.rating_bar_input);
        etComment  = view.findViewById(R.id.et_review_comment);
        btnSubmit  = view.findViewById(R.id.btn_submit_review);
        btnCancel  = view.findViewById(R.id.btn_cancel_review);

        btnCancel.setOnClickListener(v -> dismiss());
        btnSubmit.setOnClickListener(v -> submitReview());

        // Chip gợi ý nhanh → append vào edittext
        setupChip(view, R.id.chip_ontime,   "Đúng giờ, đáng tin cậy.");
        setupChip(view, R.id.chip_safe,     "Lái xe an toàn, tốc độ ổn định.");
        setupChip(view, R.id.chip_friendly, "Tài xế thân thiện, nhiệt tình.");
        setupChip(view, R.id.chip_clean,    "Xe sạch sẽ, thoải mái.");
    }

    private void setupChip(View root, int chipId, String text) {
        Chip chip = root.findViewById(chipId);
        if (chip == null) return;
        chip.setOnClickListener(v -> {
            String current = etComment.getText() != null ? etComment.getText().toString().trim() : "";
            if (!current.contains(text)) {
                String appended = current.isEmpty() ? text : current + " " + text;
                etComment.setText(appended);
                etComment.setSelection(etComment.getText().length());
            }
        });
    }

    private void submitReview() {
        float rating = ratingBar.getRating();
        if (rating == 0f) {
            Toast.makeText(getContext(), "Vui lòng chọn số sao", Toast.LENGTH_SHORT).show();
            return;
        }

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            Toast.makeText(getContext(), "Bạn cần đăng nhập", Toast.LENGTH_SHORT).show();
            return;
        }

        btnSubmit.setEnabled(false);

        db.collection("users").document(user.getUid()).get()
                .addOnSuccessListener(userDoc -> {
                    String buyerName   = userDoc.getString("name");
                    String buyerAvatar = userDoc.getString("avatarUrl");

                    Map<String, Object> reviewData = new HashMap<>();
                    reviewData.put("orderId",     orderId);
                    reviewData.put("driverId",    driverId);
                    reviewData.put("carId",       carId);
                    reviewData.put("buyerId",     user.getUid());
                    reviewData.put("buyerName",   buyerName != null ? buyerName : "Ẩn danh");
                    reviewData.put("buyerAvatar", buyerAvatar != null ? buyerAvatar : "");
                    reviewData.put("rating",      rating);
                    reviewData.put("comment",     etComment.getText().toString().trim());
                    reviewData.put("createdAt",   Timestamp.now());
                    reviewData.put("type",        "driver");

                    DocumentReference reviewRef = db.collection("reviews").document();
                    DocumentReference orderRef  = db.collection("orders").document(orderId);
                    DocumentReference driverRef = db.collection("drivers").document(driverId);

                    // Dùng Transaction để atomic: thêm review + đánh dấu reviewed + cập nhật avgRating
                    db.runTransaction(tr -> {
                        // Lấy thông tin driver để tính avgRating mới
                        com.google.firebase.firestore.DocumentSnapshot driverSnap = tr.get(driverRef);
                        double currentAvg = driverSnap.contains("avgRating")
                                ? (driverSnap.getDouble("avgRating") != null ? driverSnap.getDouble("avgRating") : 0.0)
                                : 0.0;
                        long currentCount = driverSnap.contains("reviewCount")
                                ? (driverSnap.getLong("reviewCount") != null ? driverSnap.getLong("reviewCount") : 0L)
                                : 0L;

                        long newCount  = currentCount + 1;
                        double newAvg  = ((currentAvg * currentCount) + rating) / newCount;

                        // 1. Thêm review
                        tr.set(reviewRef, reviewData);
                        // 2. Đánh dấu đơn đã reviewed
                        tr.update(orderRef, "reviewed", true);
                        // 3. Cập nhật driver avgRating + reviewCount
                        tr.update(driverRef, "avgRating", newAvg, "reviewCount", newCount);

                        // 4. Đồng bộ cache vào cars (nếu carId có)
                        if (carId != null && !carId.isEmpty()) {
                            tr.update(db.collection("cars").document(carId),
                                    "driverRating",      newAvg,
                                    "driverReviewCount", newCount);
                        }

                        // 5. Cập nhật Notification actionCompleted (nếu mở từ Notification)
                        if (notificationId != null && !notificationId.isEmpty()) {
                            tr.update(db.collection("notifications").document(notificationId),
                                    "actionCompleted", true,
                                    "read",            true);
                        }

                        return null;
                    }).addOnSuccessListener(x -> {
                        if (!isAdded()) return;
                        Toast.makeText(getContext(), "✅ Cảm ơn bạn đã đánh giá!", Toast.LENGTH_SHORT).show();
                        dismiss();
                        // Thông báo activity reload
                        if (getActivity() != null) {
                            getActivity().setResult(android.app.Activity.RESULT_OK);
                        }
                    }).addOnFailureListener(e -> {
                        if (!isAdded()) return;
                        btnSubmit.setEnabled(true);
                        Toast.makeText(getContext(), "Lỗi: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    });
                })
                .addOnFailureListener(e -> {
                    btnSubmit.setEnabled(true);
                    Toast.makeText(getContext(), "Không lấy được thông tin người dùng", Toast.LENGTH_SHORT).show();
                });
    }
}