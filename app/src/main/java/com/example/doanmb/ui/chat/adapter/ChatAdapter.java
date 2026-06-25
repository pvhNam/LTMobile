package com.example.doanmb.ui.chat.adapter;

import android.graphics.Typeface;
import android.net.Uri;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.BackgroundColorSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.example.doanmb.R;
import com.example.doanmb.data.model.ChatMessage;
import com.google.firebase.auth.FirebaseAuth;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ChatAdapter extends ListAdapter<ChatMessage, RecyclerView.ViewHolder> {

    private static final int TYPE_SENT     = 1;
    private static final int TYPE_RECEIVED = 2;

    private final String currentUserId;
    private final SimpleDateFormat dateFormat =
            new SimpleDateFormat("HH:mm", Locale.getDefault());

    private String searchQuery = "";

    // ── Listeners ─────────────────────────────────────────────────────────────

    public interface OnMediaClickListener {
        void onMediaClick(ChatMessage message);
    }

    public interface OnMessageActionListener {
        void onRecall(String messageId, ChatMessage message);
        void onForward(ChatMessage message);
        void onReportMessage(ChatMessage message);
    }

    /** Callback khi user bấm "Thử lại" trên tin nhắn upload thất bại */
    public interface OnRetryUploadListener {
        void onRetry(ChatMessage failedMessage);
    }

    private OnMediaClickListener      mediaClickListener;
    private OnMessageActionListener   messageActionListener;
    private OnRetryUploadListener     retryUploadListener;

    public void setOnMediaClickListener(OnMediaClickListener l)          { this.mediaClickListener = l; }
    public void setOnMessageActionListener(OnMessageActionListener l)    { this.messageActionListener = l; }
    public void setOnRetryUploadListener(OnRetryUploadListener l)        { this.retryUploadListener = l; }

    public void setSearchQuery(String query) {
        this.searchQuery = query != null ? query : "";
    }

    // ── Constructor ───────────────────────────────────────────────────────────

    public ChatAdapter() {
        super(new MessageDiffCallback());
        this.currentUserId = FirebaseAuth.getInstance().getCurrentUser() != null
                ? FirebaseAuth.getInstance().getCurrentUser().getUid() : "";
    }

    public void submitList(List<ChatMessage> list, Runnable commitCallback) {
        super.submitList(list, commitCallback);
    }

    // ── ViewType ──────────────────────────────────────────────────────────────

    @Override
    public int getItemViewType(int position) {
        return getItem(position).getSenderId().equals(currentUserId) ? TYPE_SENT : TYPE_RECEIVED;
    }

    // ── onCreateViewHolder ────────────────────────────────────────────────────

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inf = LayoutInflater.from(parent.getContext());
        if (viewType == TYPE_SENT) {
            return new SentVH(inf.inflate(R.layout.item_message_sent, parent, false));
        } else {
            return new ReceivedVH(inf.inflate(R.layout.item_message_received, parent, false));
        }
    }

    // ── onBindViewHolder ──────────────────────────────────────────────────────

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        ChatMessage msg = getItem(position);

        String time = msg.getTimestamp() != null
                ? dateFormat.format(msg.getTimestamp().toDate())
                : dateFormat.format(new Date());

        if (holder instanceof SentVH) {
            bindSent((SentVH) holder, msg, time);
        } else {
            bindReceived((ReceivedVH) holder, msg, time);
        }
    }

    private void bindSent(SentVH h, ChatMessage msg, String time) {
        h.tvTime.setText(time);

        if (msg.isRecalled()) {
            // ── Thu hồi ──
            h.tvMessage.setText("Tin nhắn đã bị thu hồi");
            h.tvMessage.setTypeface(null, Typeface.ITALIC);
            h.tvMessage.setTextColor(0xFFBDBDBD);
            h.tvMessage.setVisibility(View.VISIBLE);
            h.tvStatus.setText("");
            h.cardImage.setVisibility(View.GONE);
            h.btnMore.setVisibility(View.GONE);
            hideUploadOverlays(h);
            return;
        }

        // ── Bình thường ──
        h.tvMessage.setTypeface(null, Typeface.NORMAL);
        h.tvMessage.setTextColor(0xFFFFFFFF);

        String content = msg.getContent();
        if (content != null && !content.isEmpty()) {
            h.tvMessage.setVisibility(View.VISIBLE);
            h.tvMessage.setText(highlightText(content, 0xFFFFFF00));
        } else {
            h.tvMessage.setVisibility(View.GONE);
        }

        // ── Trạng thái gửi ──
        if (msg.isUploading()) {
            h.tvStatus.setText("• Đang gửi...");
        } else if (msg.isUploadFailed()) {
            h.tvStatus.setText("• Gửi thất bại");
        } else if (msg.getTimestamp() == null) {
            h.tvStatus.setText("• Đang gửi...");
        } else {
            if      (msg.getStatus() == 0) h.tvStatus.setText("• Đã gửi");
            else if (msg.getStatus() == 1) h.tvStatus.setText("• Đã nhận");
            else if (msg.getStatus() == 2) h.tvStatus.setText("• Đã xem");
        }

        // ── Media: ưu tiên localUri khi đang/chưa upload xong ──
        if (msg.isUploading() || msg.isUploadFailed()) {
            // Dùng ảnh/video local để preview ngay
            String localUri = msg.getLocalUri();
            if (localUri != null && !localUri.isEmpty()) {
                loadMedia(h.cardImage, h.ivImage, h.ivPlayIcon, localUri, msg.isVideo());
            } else {
                h.cardImage.setVisibility(View.GONE);
            }
        } else if (msg.isVideo()) {
            loadMedia(h.cardImage, h.ivImage, h.ivPlayIcon,
                    msg.getThumbnailUrl() != null ? msg.getThumbnailUrl()
                            : cloudinaryThumbnail(msg.getVideoUrl()), true);
        } else {
            loadMedia(h.cardImage, h.ivImage, h.ivPlayIcon, msg.getImageUrl(), false);
        }

        // ── Overlay: Đang tải / Thất bại ──
        if (msg.isUploading()) {
            h.layoutUploading.setVisibility(View.VISIBLE);
            h.layoutUploadFailed.setVisibility(View.GONE);
        } else if (msg.isUploadFailed()) {
            h.layoutUploading.setVisibility(View.GONE);
            h.layoutUploadFailed.setVisibility(View.VISIBLE);
            // Nút thử lại
            if (h.btnRetry != null) {
                h.btnRetry.setOnClickListener(v -> {
                    if (retryUploadListener != null) retryUploadListener.onRetry(msg);
                });
            }
        } else {
            hideUploadOverlays(h);
        }

        bindMediaClick(h.cardImage, msg);

        // Menu 3 chấm: ẩn khi đang upload (chưa có messageId thật)
        boolean isTemp = msg.getMessageId() != null && msg.getMessageId().startsWith("local_");
        h.btnMore.setVisibility(isTemp ? View.GONE : View.VISIBLE);
        if (!isTemp) {
            h.btnMore.setOnClickListener(v -> showSentMenu(v, msg));
        }
    }

    private void hideUploadOverlays(SentVH h) {
        h.layoutUploading.setVisibility(View.GONE);
        h.layoutUploadFailed.setVisibility(View.GONE);
    }

    private void bindReceived(ReceivedVH h, ChatMessage msg, String time) {
        h.tvTime.setText(time);

        if (msg.isRecalled()) {
            h.tvMessage.setText("Tin nhắn đã bị thu hồi");
            h.tvMessage.setTypeface(null, Typeface.ITALIC);
            h.tvMessage.setTextColor(0xFF9E9E9E);
            h.tvMessage.setVisibility(View.VISIBLE);
            h.cardImage.setVisibility(View.GONE);
            h.btnMore.setVisibility(View.GONE);
            return;
        }

        h.tvMessage.setTypeface(null, Typeface.NORMAL);
        h.tvMessage.setTextColor(0xFF1A1A2E);

        String content = msg.getContent();
        if (content != null && !content.isEmpty()) {
            h.tvMessage.setVisibility(View.VISIBLE);
            h.tvMessage.setText(highlightText(content, 0xFFFFEB3B));
        } else {
            h.tvMessage.setVisibility(View.GONE);
        }

        if (msg.isVideo()) {
            loadMedia(h.cardImage, h.ivImage, h.ivPlayIcon,
                    msg.getThumbnailUrl() != null ? msg.getThumbnailUrl()
                            : cloudinaryThumbnail(msg.getVideoUrl()), true);
        } else {
            loadMedia(h.cardImage, h.ivImage, h.ivPlayIcon, msg.getImageUrl(), false);
        }
        bindMediaClick(h.cardImage, msg);

        h.btnMore.setVisibility(View.VISIBLE);
        h.btnMore.setOnClickListener(v -> showReceivedMenu(v, msg));
    }

    // ── Menus ─────────────────────────────────────────────────────────────────

    private void showSentMenu(View anchor, ChatMessage msg) {
        PopupMenu menu = new PopupMenu(anchor.getContext(), anchor);
        menu.getMenu().add(0, 1, 0, "Gỡ");
        menu.getMenu().add(0, 2, 1, "Chuyển tiếp");
        menu.setOnMenuItemClickListener(item -> {
            if (messageActionListener == null) return true;
            switch (item.getItemId()) {
                case 1:
                    if (msg.getMessageId() != null)
                        messageActionListener.onRecall(msg.getMessageId(), msg);
                    return true;
                case 2:
                    messageActionListener.onForward(msg);
                    return true;
                default: return false;
            }
        });
        menu.show();
    }

    private void showReceivedMenu(View anchor, ChatMessage msg) {
        PopupMenu menu = new PopupMenu(anchor.getContext(), anchor);
        menu.getMenu().add(0, 1, 0, "Chuyển tiếp");
        menu.getMenu().add(0, 2, 1, "Báo cáo");
        menu.setOnMenuItemClickListener(item -> {
            if (messageActionListener == null) return true;
            switch (item.getItemId()) {
                case 1: messageActionListener.onForward(msg);        return true;
                case 2: messageActionListener.onReportMessage(msg);  return true;
                default: return false;
            }
        });
        menu.show();
    }

    // ── Highlight từ khoá ─────────────────────────────────────────────────────

    private CharSequence highlightText(String text, int highlightColor) {
        if (searchQuery == null || searchQuery.isEmpty()) return text;

        String lowerText  = text.toLowerCase(Locale.ROOT);
        String lowerQuery = searchQuery.toLowerCase(Locale.ROOT);

        SpannableString spannable = new SpannableString(text);
        int start = 0;
        while (true) {
            int idx = lowerText.indexOf(lowerQuery, start);
            if (idx == -1) break;
            spannable.setSpan(
                    new BackgroundColorSpan(highlightColor),
                    idx, idx + lowerQuery.length(),
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            start = idx + lowerQuery.length();
        }
        return spannable;
    }

    // ── Media helpers ─────────────────────────────────────────────────────────

    private void bindMediaClick(CardView card, ChatMessage msg) {
        if (msg.isRecalled() || msg.isUploading() || msg.isUploadFailed()) {
            card.setOnClickListener(null);
            card.setClickable(false);
            return;
        }
        boolean hasMedia =
                (msg.isVideo()  && msg.getVideoUrl()  != null && !msg.getVideoUrl().isEmpty())
                        || (!msg.isVideo() && msg.getImageUrl() != null && !msg.getImageUrl().isEmpty());
        if (!hasMedia) {
            card.setOnClickListener(null);
            card.setClickable(false);
            return;
        }
        card.setOnClickListener(v -> {
            if (mediaClickListener != null) mediaClickListener.onMediaClick(msg);
        });
    }

    /**
     * Load ảnh/thumbnail vào ImageView.
     * Tự động nhận dạng local URI (content:// / file://) và Cloudinary URL.
     */
    private void loadMedia(CardView card, ImageView iv, ImageView playIcon,
                           String url, boolean isVideo) {
        if (url == null || url.isEmpty()) {
            card.setVisibility(View.GONE);
            if (playIcon != null) playIcon.setVisibility(View.GONE);
            return;
        }

        card.setVisibility(View.VISIBLE);

        boolean isLocalUri = url.startsWith("content://") || url.startsWith("file://");
        Object  glideSource;

        if (isLocalUri) {
            // URI local → Glide load trực tiếp, không dùng Cloudinary transform
            glideSource = Uri.parse(url);
        } else if (url.contains("cloudinary.com")) {
            // Cloudinary URL → áp transform
            if (isVideo) {
                String loadUrl = (!url.contains("/upload/so_0") && !url.contains("/upload/w_"))
                        ? url.replace("/upload/", "/upload/so_0,w_420,c_fill,q_80/")
                        .replaceAll("\\.(mp4|mov|avi|mkv|webm)$", ".jpg")
                        : url;
                glideSource = loadUrl;
            } else {
                glideSource = url.replace("/upload/", "/upload/w_600,c_limit,q_85,f_auto/");
            }
        } else {
            glideSource = url;
        }

        Glide.with(iv.getContext())
                .load(glideSource)
                .diskCacheStrategy(isLocalUri ? DiskCacheStrategy.NONE : DiskCacheStrategy.ALL)
                .override(420, 420)
                .placeholder(android.R.drawable.ic_menu_gallery)
                .error(R.drawable.ic_broken_image)
                .centerCrop()
                .into(iv);

        // Icon play chỉ hiện khi đã upload xong (không phải local temp)
        if (playIcon != null) {
            playIcon.setVisibility(isVideo && !isLocalUri ? View.VISIBLE : View.GONE);
        }
    }

    private String cloudinaryThumbnail(String videoUrl) {
        if (videoUrl == null || !videoUrl.contains("cloudinary.com")) return null;
        return videoUrl
                .replace("/upload/", "/upload/so_0,w_420,c_fill,q_80/")
                .replaceAll("\\.(mp4|mov|avi|mkv|webm)$", ".jpg");
    }

    // ── ViewHolders ───────────────────────────────────────────────────────────

    static class SentVH extends RecyclerView.ViewHolder {
        TextView  tvMessage, tvTime, tvStatus, btnMore;
        ImageView ivImage, ivPlayIcon;
        CardView  cardImage;
        View      layoutUploading, layoutUploadFailed;
        TextView  btnRetry;

        SentVH(@NonNull View v) {
            super(v);
            tvMessage          = v.findViewById(R.id.tv_message);
            tvTime             = v.findViewById(R.id.tv_time);
            tvStatus           = v.findViewById(R.id.tv_status);
            ivImage            = v.findViewById(R.id.iv_message_image);
            ivPlayIcon         = v.findViewById(R.id.iv_play_icon);
            cardImage          = v.findViewById(R.id.card_image);
            btnMore            = v.findViewById(R.id.btn_message_more);
            layoutUploading    = v.findViewById(R.id.layout_uploading);
            layoutUploadFailed = v.findViewById(R.id.layout_upload_failed);
            btnRetry           = v.findViewById(R.id.btn_retry_upload);
        }
    }

    static class ReceivedVH extends RecyclerView.ViewHolder {
        TextView  tvMessage, tvTime, btnMore;
        ImageView ivImage, ivPlayIcon;
        CardView  cardImage;

        ReceivedVH(@NonNull View v) {
            super(v);
            tvMessage  = v.findViewById(R.id.tv_message);
            tvTime     = v.findViewById(R.id.tv_time);
            ivImage    = v.findViewById(R.id.iv_message_image);
            ivPlayIcon = v.findViewById(R.id.iv_play_icon);
            cardImage  = v.findViewById(R.id.card_image);
            btnMore    = v.findViewById(R.id.btn_message_more);
        }
    }

    // ── DiffCallback ──────────────────────────────────────────────────────────

    static class MessageDiffCallback extends DiffUtil.ItemCallback<ChatMessage> {
        @Override
        public boolean areItemsTheSame(@NonNull ChatMessage a, @NonNull ChatMessage b) {
            if (a.getMessageId() != null && b.getMessageId() != null) {
                return a.getMessageId().equals(b.getMessageId());
            }
            return false;
        }

        @Override
        public boolean areContentsTheSame(@NonNull ChatMessage a, @NonNull ChatMessage b) {
            // Tin nhắn temp (timestamp null) luôn re-bind để phản ánh trạng thái upload
            if (a.getTimestamp() == null || b.getTimestamp() == null) return false;

            return a.getStatus() == b.getStatus()
                    && a.isRecalled() == b.isRecalled()
                    && a.isUploading() == b.isUploading()
                    && a.isUploadFailed() == b.isUploadFailed()
                    && a.getTimestamp().equals(b.getTimestamp())
                    && (a.getImageUrl()    != null ? a.getImageUrl().equals(b.getImageUrl())         : b.getImageUrl()    == null)
                    && (a.getVideoUrl()    != null ? a.getVideoUrl().equals(b.getVideoUrl())         : b.getVideoUrl()    == null)
                    && (a.getThumbnailUrl() != null ? a.getThumbnailUrl().equals(b.getThumbnailUrl()) : b.getThumbnailUrl() == null);
        }
    }
}