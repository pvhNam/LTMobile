package com.example.doanmb.ui.media.view;

import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.viewpager2.widget.ViewPager2;

import com.bumptech.glide.Glide;
import com.example.doanmb.R;
import com.example.doanmb.core.helper.CloudinaryHelper;
import com.github.chrisbanes.photoview.PhotoView;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.PlaybackException;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.ui.PlayerView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class FullscreenMediaActivity extends AppCompatActivity {

    public static final String EXTRA_URLS = "MEDIA_URLS";
    public static final String EXTRA_IS_VIDEOS = "MEDIA_IS_VIDEOS";
    public static final String EXTRA_START_POS = "MEDIA_START_POS";

    private ViewPager2 viewPager;
    private TextView tvCounter;
    private ImageView btnClose;

    private ArrayList<String> urls;
    private ArrayList<Boolean> isVideos;

    // Quản lý ExoPlayer theo vị trí
    private final Map<Integer, ExoPlayer> activePlayers = new HashMap<>();
    private int currentPosition = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_fullscreen_media);

        if (getSupportActionBar() != null) getSupportActionBar().hide();

        urls = getIntent().getStringArrayListExtra(EXTRA_URLS);
        isVideos = (ArrayList<Boolean>) getIntent().getSerializableExtra(EXTRA_IS_VIDEOS);
        int start = getIntent().getIntExtra(EXTRA_START_POS, 0);

        if (urls == null || urls.isEmpty()) {
            finish();
            return;
        }
        if (isVideos == null) isVideos = new ArrayList<>();

        currentPosition = start;

        viewPager = findViewById(R.id.viewpager_media);
        tvCounter = findViewById(R.id.tv_media_counter);
        btnClose = findViewById(R.id.btn_close_media);

        btnClose.setOnClickListener(v -> finish());

        viewPager.setAdapter(new MediaPagerAdapter());
        viewPager.setCurrentItem(start, false);
        updateCounter(start);

        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                currentPosition = position;
                updateCounter(position);
                releaseInactivePlayers(position);
            }
        });
    }

    private void updateCounter(int pos) {
        if (urls.size() <= 1) {
            tvCounter.setVisibility(View.GONE);
        } else {
            tvCounter.setVisibility(View.VISIBLE);
            tvCounter.setText((pos + 1) + " / " + urls.size());
        }
    }

    private void releaseInactivePlayers(int current) {
        for (Map.Entry<Integer, ExoPlayer> entry : activePlayers.entrySet()) {
            if (entry.getKey() != current && entry.getValue() != null) {
                entry.getValue().pause();
            }
        }
    }

    private class MediaPagerAdapter extends androidx.recyclerview.widget.RecyclerView.Adapter<MediaPagerAdapter.VH> {

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull android.view.ViewGroup parent, int viewType) {
            View v = getLayoutInflater().inflate(R.layout.item_media_page, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            String url = urls.get(position);
            boolean isVideo = position < isVideos.size() && Boolean.TRUE.equals(isVideos.get(position));

            if (isVideo) {
                setupVideo(holder, position, url);
            } else {
                setupImage(holder, url);
            }
        }

        private void setupVideo(VH h, int position, String url) {
            h.photoView.setVisibility(View.GONE);
            h.playerView.setVisibility(View.VISIBLE);
            h.progressBar.setVisibility(View.VISIBLE);
            h.ivPlayBtn.setVisibility(View.GONE);

            // Release player cũ nếu có
            releasePlayer(position);

            ExoPlayer player = new ExoPlayer.Builder(FullscreenMediaActivity.this).build();
            h.playerView.setPlayer(player);
            activePlayers.put(position, player);

            String optimizedUrl = CloudinaryHelper.getOptimizedVideoUrl(url);
            MediaItem mediaItem = MediaItem.fromUri(optimizedUrl);

            player.setMediaItem(mediaItem);
            player.prepare();
            player.setPlayWhenReady(true); // Tự động play khi load xong

            player.addListener(new Player.Listener() {
                @Override
                public void onPlaybackStateChanged(int state) {
                    if (state == Player.STATE_BUFFERING) {
                        h.progressBar.setVisibility(View.VISIBLE);
                    } else if (state == Player.STATE_READY) {
                        h.progressBar.setVisibility(View.GONE);
                    } else if (state == Player.STATE_ENDED) {
                        h.progressBar.setVisibility(View.GONE);
                    }
                }

                @Override
                public void onPlayerError(PlaybackException error) {
                    h.progressBar.setVisibility(View.GONE);
                    Toast.makeText(FullscreenMediaActivity.this, "Không thể phát video: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                }
            });
        }

        private void setupImage(VH h, String url) {
            releasePlayer(h.getBindingAdapterPosition());

            h.playerView.setVisibility(View.GONE);
            h.photoView.setVisibility(View.VISIBLE);
            h.progressBar.setVisibility(View.GONE);
            h.ivPlayBtn.setVisibility(View.GONE);

            String optimized = url.contains("cloudinary.com")
                    ? url.replaceAll("/upload/[^/]+/", "/upload/")
                    : url;

            Glide.with(h.photoView.getContext())
                    .load(optimized)
                    .placeholder(android.R.drawable.ic_menu_gallery)
                    .into(h.photoView);
        }

        private void releasePlayer(int position) {
            ExoPlayer player = activePlayers.remove(position);
            if (player != null) {
                player.release();
            }
        }

        @Override
        public void onViewRecycled(@NonNull VH holder) {
            super.onViewRecycled(holder);
            int pos = holder.getBindingAdapterPosition();
            if (pos != -1) {
                releasePlayer(pos);
            }
        }

        @Override
        public int getItemCount() {
            return urls.size();
        }

        class VH extends androidx.recyclerview.widget.RecyclerView.ViewHolder {
            PhotoView photoView;
            PlayerView playerView;
            ProgressBar progressBar;
            ImageView ivPlayBtn;

            VH(@NonNull View v) {
                super(v);
                photoView = v.findViewById(R.id.photo_view_page);
                playerView = v.findViewById(R.id.player_view_page);
                progressBar = v.findViewById(R.id.progress_page);
                ivPlayBtn = v.findViewById(R.id.iv_play_page);
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        for (ExoPlayer player : activePlayers.values()) {
            if (player != null) player.pause();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        for (ExoPlayer player : activePlayers.values()) {
            if (player != null) player.release();
        }
        activePlayers.clear();
    }
}