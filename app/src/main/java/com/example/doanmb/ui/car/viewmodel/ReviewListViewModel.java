package com.example.doanmb.ui.car.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.doanmb.data.model.Review;
import com.example.doanmb.data.repository.ReviewRepository;

import java.util.ArrayList;
import java.util.List;

/**
 * ViewModel màn xem toàn bộ đánh giá của 1 tài xế (ReviewActivity):
 * điểm trung bình + tổng lượt (header) và danh sách review.
 */
public class ReviewListViewModel extends ViewModel {

    private final MutableLiveData<Double>        avgRating   = new MutableLiveData<>(0.0);
    private final MutableLiveData<Long>          reviewCount = new MutableLiveData<>(0L);
    private final MutableLiveData<List<Review>>  reviews     = new MutableLiveData<>(new ArrayList<>());

    public LiveData<Double>       getAvgRating()   { return avgRating; }
    public LiveData<Long>         getReviewCount() { return reviewCount; }
    public LiveData<List<Review>> getReviews()     { return reviews; }

    private boolean loaded = false;

    public void load(String driverId) {
        if (loaded) return;
        loaded = true;
        ReviewRepository.loadDriverStats(driverId, (avg, count) -> {
            avgRating.setValue(avg);
            reviewCount.setValue(count);
        });
        ReviewRepository.loadReviews(driverId, reviews::setValue);
    }
}
