package com.example.doanmb.ui.chat.adapter;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.doanmb.R;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class MediaPickerAdapter extends RecyclerView.Adapter<MediaPickerAdapter.VH> {

    public static class MediaItem {
        public final Uri uri;
        public final boolean isVideo;
        public final long duration;

        public MediaItem(Uri uri, boolean isVideo, long duration) {
            this.uri      = uri;
            this.isVideo  = isVideo;
            this.duration = duration;
        }
    }

    public interface OnMediaSelectedListener {
        void onSelectionChanged(List<MediaItem> selected);
    }

    private final List<MediaItem>         items    = new ArrayList<>();
    private final Set<Uri>                selected = new LinkedHashSet<>();
    private       OnMediaSelectedListener listener;

    public void setOnMediaSelectedListener(OnMediaSelectedListener l) {
        this.listener = l;
    }

    /**
     * Load ảnh hoặc video từ MediaStore theo mode.
     * imageOnly=true  → chỉ load ảnh
     * imageOnly=false → chỉ load video
     * PHẢI gọi từ background thread.
     */
    public void loadFromDevice(Context context, boolean videoMode) {
        items.clear();

        if (!videoMode) {
            // Chỉ load ảnh
            String[] imgCols = {MediaStore.Images.Media._ID};
            try (Cursor c = context.getContentResolver().query(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    imgCols, null, null,
                    MediaStore.Images.Media.DATE_ADDED + " DESC")) {
                if (c != null) {
                    int idCol = c.getColumnIndexOrThrow(MediaStore.Images.Media._ID);
                    while (c.moveToNext()) {
                        long id  = c.getLong(idCol);
                        Uri  uri = Uri.withAppendedPath(
                                MediaStore.Images.Media.EXTERNAL_CONTENT_URI, String.valueOf(id));
                        items.add(new MediaItem(uri, false, 0));
                    }
                }
            } catch (Exception ignored) {}
        } else {
            // Chỉ load video
            String[] vidCols = {MediaStore.Video.Media._ID, MediaStore.Video.Media.DURATION};
            try (Cursor c = context.getContentResolver().query(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                    vidCols, null, null,
                    MediaStore.Video.Media.DATE_ADDED + " DESC")) {
                if (c != null) {
                    int idCol  = c.getColumnIndexOrThrow(MediaStore.Video.Media._ID);
                    int durCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION);
                    while (c.moveToNext()) {
                        long id  = c.getLong(idCol);
                        long dur = c.getLong(durCol);
                        Uri  uri = Uri.withAppendedPath(
                                MediaStore.Video.Media.EXTERNAL_CONTENT_URI, String.valueOf(id));
                        items.add(new MediaItem(uri, true, dur));
                    }
                }
            } catch (Exception ignored) {}
        }
        // KHÔNG gọi notifyDataSetChanged() ở đây — đang ở background thread
    }

    /**
     * Overload cũ: load cả ảnh lẫn video (dùng cho tương thích ngược nếu cần).
     */
    public void loadFromDevice(Context context) {
        loadFromDevice(context, false);
    }

    public List<MediaItem> getSelectedItems() {
        List<MediaItem> result = new ArrayList<>();
        for (MediaItem item : items) {
            if (selected.contains(item.uri)) result.add(item);
        }
        return result;
    }

    public void clearSelection() {
        selected.clear();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_media_picker, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        MediaItem item = items.get(position);

        Glide.with(h.ivThumb.getContext())
                .load(item.uri)
                .centerCrop()
                .thumbnail(0.3f)
                .into(h.ivThumb);

        h.ivPlay.setVisibility(item.isVideo ? View.VISIBLE : View.GONE);

        if (item.isVideo && item.duration > 0) {
            h.tvDuration.setVisibility(View.VISIBLE);
            h.tvDuration.setText(formatDuration(item.duration));
        } else {
            h.tvDuration.setVisibility(View.GONE);
        }

        int order = getSelectionOrder(item.uri);
        if (order > 0) {
            h.overlay.setVisibility(View.VISIBLE);
            h.tvOrder.setVisibility(View.VISIBLE);
            h.tvOrder.setText(String.valueOf(order));
            h.overlay.setBackgroundColor(0x441976D2);
        } else {
            h.overlay.setVisibility(View.GONE);
            h.tvOrder.setVisibility(View.GONE);
        }

        h.itemView.setOnClickListener(v -> {
            if (selected.contains(item.uri)) {
                selected.remove(item.uri);
            } else {
                selected.add(item.uri);
            }
            notifyDataSetChanged();
            if (listener != null) listener.onSelectionChanged(getSelectedItems());
        });
    }

    @Override
    public int getItemCount() { return items.size(); }

    private int getSelectionOrder(Uri uri) {
        int i = 1;
        for (Uri u : selected) {
            if (u.equals(uri)) return i;
            i++;
        }
        return 0;
    }

    private String formatDuration(long ms) {
        long min = TimeUnit.MILLISECONDS.toMinutes(ms);
        long sec = TimeUnit.MILLISECONDS.toSeconds(ms) % 60;
        return String.format("%d:%02d", min, sec);
    }

    static class VH extends RecyclerView.ViewHolder {
        ImageView ivThumb, ivPlay;
        View      overlay;
        TextView  tvOrder, tvDuration;

        VH(@NonNull View v) {
            super(v);
            ivThumb    = v.findViewById(R.id.iv_picker_thumb);
            ivPlay     = v.findViewById(R.id.iv_picker_play);
            overlay    = v.findViewById(R.id.view_picker_overlay);
            tvOrder    = v.findViewById(R.id.tv_picker_order);
            tvDuration = v.findViewById(R.id.tv_picker_duration);
        }
    }
}