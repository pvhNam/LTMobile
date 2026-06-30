package com.example.doanmb.ui.car.view;

import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.RatingBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.doanmb.R;
import com.example.doanmb.data.model.Review;
import com.example.doanmb.ui.car.adapter.ReviewAdapter;
import com.example.doanmb.ui.car.viewmodel.ReviewListViewModel;

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
    private ReviewListViewModel viewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdgeUtil.enable(this, true);
        setContentView(R.layout.activity_review);
        EdgeToEdgeUtil.padContentForSystemBars(this, 0xFFFFFFFF);

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

        viewModel = new ViewModelProvider(this).get(ReviewListViewModel.class);
        observeViewModel();

        String driverId = getIntent().getStringExtra(EXTRA_DRIVER_ID);
        if (driverId != null && !driverId.isEmpty()) {
            viewModel.load(driverId);
        }
    }

    private void observeViewModel() {
        viewModel.getAvgRating().observe(this, avg -> {
            float avgVal = avg != null ? avg.floatValue() : 0f;
            if (tvAvgRating  != null) tvAvgRating.setText(String.format("%.1f", avgVal));
            if (ratingBarAvg != null) ratingBarAvg.setRating(avgVal);
        });

        viewModel.getReviewCount().observe(this, count -> {
            if (tvReviewCount != null)
                tvReviewCount.setText("(" + (count != null ? count : 0) + " đánh giá)");
        });

        viewModel.getReviews().observe(this, list -> {
            reviewList.clear();
            if (list != null) reviewList.addAll(list);
            reviewAdapter.notifyDataSetChanged();
            if (tvEmpty != null)
                tvEmpty.setVisibility(reviewList.isEmpty() ? View.VISIBLE : View.GONE);
        });
    }
}
