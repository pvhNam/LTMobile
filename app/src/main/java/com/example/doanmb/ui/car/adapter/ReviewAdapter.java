package com.example.doanmb.ui.car.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RatingBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.doanmb.R;
import com.example.doanmb.data.model.Review;
import com.google.firebase.Timestamp;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import de.hdodenhof.circleimageview.CircleImageView;

public class ReviewAdapter extends RecyclerView.Adapter<ReviewAdapter.ReviewViewHolder> {

    private final List<Review> reviewList;
    private static final SimpleDateFormat DATE_FMT =
            new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());

    public ReviewAdapter(List<Review> reviewList) {
        this.reviewList = reviewList;
    }

    @NonNull
    @Override
    public ReviewViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_review, parent, false);
        return new ReviewViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ReviewViewHolder holder, int position) {
        Review review = reviewList.get(position);

        // Tên người đánh giá
        holder.tvBuyerName.setText(review.getBuyerName() != null ? review.getBuyerName() : "Ẩn danh");

        // Avatar
        String avatar = review.getBuyerAvatar();
        if (avatar != null && !avatar.isEmpty()) {
            Glide.with(holder.itemView.getContext()).load(avatar).into(holder.ivAvatar);
        } else {
            holder.ivAvatar.setImageResource(R.drawable.ic_person_placeholder);
        }

        // Rating
        holder.ratingBar.setRating(review.getRating());
        holder.tvRatingValue.setText(String.format(Locale.getDefault(), "%.1f", review.getRating()));

        // Comment
        String comment = review.getComment();
        if (comment != null && !comment.isEmpty()) {
            holder.tvComment.setText(comment);
            holder.tvComment.setVisibility(View.VISIBLE);
        } else {
            holder.tvComment.setVisibility(View.GONE);
        }

        // Ngày
        Timestamp ts = review.getCreatedAt();
        if (ts != null) {
            Date date = ts.toDate();
            holder.tvDate.setText(DATE_FMT.format(date));
        } else {
            holder.tvDate.setText("");
        }
    }

    @Override
    public int getItemCount() {
        return reviewList.size();
    }

    static class ReviewViewHolder extends RecyclerView.ViewHolder {
        CircleImageView ivAvatar;
        TextView tvBuyerName, tvComment, tvDate, tvRatingValue;
        RatingBar ratingBar;

        ReviewViewHolder(@NonNull View itemView) {
            super(itemView);
            ivAvatar      = itemView.findViewById(R.id.iv_reviewer_avatar);
            tvBuyerName   = itemView.findViewById(R.id.tv_reviewer_name);
            ratingBar     = itemView.findViewById(R.id.rating_bar_review);
            tvRatingValue = itemView.findViewById(R.id.tv_review_rating_value);
            tvComment     = itemView.findViewById(R.id.tv_review_comment);
            tvDate        = itemView.findViewById(R.id.tv_review_date);
        }
    }
}