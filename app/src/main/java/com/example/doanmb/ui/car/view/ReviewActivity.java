package com.example.doanmb.ui.car.view;

import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.RatingBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.doanmb.R;
import com.example.doanmb.data.model.Review;
import com.example.doanmb.ui.car.adapter.ReviewAdapter;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.List;
import com.example.doanmb.core.util.EdgeToEdgeUtil;

public class ReviewActivity extends AppCompatActivity {

    public static final String EXTRA_DRIVER_ID = "DRIVER_ID";

    private TextView tvAvgRating, tvReviewCount;
    private RatingBar ratingBarAvg;
    private RecyclerView rvReviews;
    private TextView tvEmpty;

    private ReviewAdapter reviewAdapter;
    private final List<Review> reviewList = new ArrayList<>();
    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdgeUtil.enable(this, true);
        setContentView(R.layout.activity_review);
        EdgeToEdgeUtil.padContentForSystemBars(this, 0xFFFFFFFF);

        db = FirebaseFirestore.getInstance();

        ImageView btnBack = findViewById(R.id.btn_back);
        if (btnBack != null) btnBack.setOnClickListener(v -> finish());

        tvAvgRating    = findViewById(R.id.tv_avg_rating);
        tvReviewCount  = findViewById(R.id.tv_review_count);
        ratingBarAvg   = findViewById(R.id.rating_bar_avg);
        rvReviews      = findViewById(R.id.rv_reviews);
        tvEmpty        = findViewById(R.id.tv_reviews_empty);

        reviewAdapter = new ReviewAdapter(reviewList);
        if (rvReviews != null) {
            rvReviews.setLayoutManager(new LinearLayoutManager(this));
            rvReviews.setAdapter(reviewAdapter);
        }

        String driverId = getIntent().getStringExtra(EXTRA_DRIVER_ID);
        if (driverId != null && !driverId.isEmpty()) {
            loadDriverStats(driverId);
            loadReviews(driverId);
        }
    }

    private void loadDriverStats(String driverId) {
        db.collection("drivers").document(driverId).get()
                .addOnSuccessListener(doc -> {
                    if (!doc.exists()) return;
                    Double avg = doc.getDouble("avgRating");
                    Long count = doc.getLong("reviewCount");

                    float avgVal  = avg != null ? avg.floatValue() : 0f;
                    int countVal  = count != null ? count.intValue() : 0;

                    if (tvAvgRating   != null) tvAvgRating.setText(String.format("%.1f", avgVal));
                    if (tvReviewCount != null) tvReviewCount.setText("(" + countVal + " đánh giá)");
                    if (ratingBarAvg  != null) ratingBarAvg.setRating(avgVal);
                });
    }

    private void loadReviews(String driverId) {
        db.collection("reviews")
                .whereEqualTo("driverId", driverId)
                .get()
                .addOnSuccessListener(snap -> {
                    reviewList.clear();
                    for (QueryDocumentSnapshot d : snap) {
                        Review r = d.toObject(Review.class);
                        r.setReviewId(d.getId());
                        reviewList.add(r);
                    }

                    reviewList.sort((a, b) -> {
                        if (a.getCreatedAt() == null && b.getCreatedAt() == null) return 0;
                        if (a.getCreatedAt() == null) return 1;
                        if (b.getCreatedAt() == null) return -1;
                        return b.getCreatedAt().compareTo(a.getCreatedAt());
                    });
                    reviewAdapter.notifyDataSetChanged();
                    if (tvEmpty != null)
                        tvEmpty.setVisibility(reviewList.isEmpty() ? View.VISIBLE : View.GONE);
                })
                .addOnFailureListener(e ->
                        android.util.Log.e("ReviewActivity", "loadReviews lỗi: " + e.getMessage()));
    }
}