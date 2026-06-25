package com.example.doanmb.ui.media.view;

import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.MediaController;
import android.widget.ProgressBar;
import android.widget.Toast;
import android.widget.VideoView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.doanmb.R;

/**
 * Phát video trực tiếp trong app (không mở app ngoài).
 * Gọi bằng: Intent + extra "VIDEO_URL" (String)
 */
public class VideoPlayerActivity extends AppCompatActivity {

    public static final String EXTRA_VIDEO_URL = "VIDEO_URL";

    private VideoView videoView;
    private ProgressBar progressBar;
    private int savedPosition = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Toàn màn hình, giữ màn hình sáng khi đang phát
        getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN
        );
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setContentView(R.layout.activity_video_player);

        if (getSupportActionBar() != null) getSupportActionBar().hide();

        String videoUrl = getIntent().getStringExtra(EXTRA_VIDEO_URL);

        videoView    = findViewById(R.id.video_view);
        progressBar  = findViewById(R.id.progress_video);
        ImageView btnClose = findViewById(R.id.btn_close_video);

        btnClose.setOnClickListener(v -> finish());

        if (videoUrl == null || videoUrl.isEmpty()) {
            Toast.makeText(this, "Không tìm thấy video", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        setupVideoPlayer(videoUrl);
    }

    private void setupVideoPlayer(String url) {
        // MediaController hiển thị nút Play/Pause/Seek bar
        MediaController mediaController = new MediaController(this);
        mediaController.setAnchorView(videoView);
        videoView.setMediaController(mediaController);

        videoView.setVideoURI(Uri.parse(url));

        progressBar.setVisibility(View.VISIBLE);

        videoView.setOnPreparedListener(mp -> {
            progressBar.setVisibility(View.GONE);
            mp.setLooping(false);
            if (savedPosition > 0) {
                videoView.seekTo(savedPosition);
            }
            videoView.start();
        });

        videoView.setOnErrorListener((mp, what, extra) -> {
            progressBar.setVisibility(View.GONE);
            Toast.makeText(this, "Không thể phát video này", Toast.LENGTH_SHORT).show();
            finish();
            return true;
        });

        videoView.setOnCompletionListener(mp -> {
            // Tự quay về đầu khi xem xong (optional)
            savedPosition = 0;
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (videoView != null && videoView.isPlaying()) {
            savedPosition = videoView.getCurrentPosition();
            videoView.pause();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (videoView != null && savedPosition > 0) {
            videoView.seekTo(savedPosition);
            videoView.start();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (videoView != null) videoView.stopPlayback();
    }
}