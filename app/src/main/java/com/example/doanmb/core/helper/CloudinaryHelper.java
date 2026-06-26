package com.example.doanmb.core.helper;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;

import com.cloudinary.android.MediaManager;
import com.cloudinary.android.callback.ErrorInfo;
import com.cloudinary.android.callback.UploadCallback;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class CloudinaryHelper {

    private static final String UPLOAD_PRESET = "doanmb_preset";
    private static final int MAX_SIDE = 1024;

    // ── Callback chung ───────────────────────────────────────────────────────

    public interface OnUploadCallback {
        void onSuccess(String url);
        void onFailure(String error);
    }

    public interface OnMultiUploadCallback {
        void onSuccess(List<String> imageUrls);
        void onFailure(String error);
    }

    // ── Upload nhiều ảnh (giữ đúng thứ tự) ───────────────────────────────────

    public static void uploadImages(Context context, List<Uri> uris, OnMultiUploadCallback callback) {
        if (uris == null || uris.isEmpty()) {
            callback.onSuccess(new ArrayList<>());
            return;
        }
        final int total = uris.size();
        final String[] results = new String[total];
        final AtomicInteger remaining = new AtomicInteger(total);
        final AtomicBoolean failed = new AtomicBoolean(false);

        for (int i = 0; i < total; i++) {
            final int index = i;
            uploadImage(context, uris.get(i), new OnUploadCallback() {
                @Override
                public void onSuccess(String imageUrl) {
                    results[index] = imageUrl;
                    if (remaining.decrementAndGet() == 0 && !failed.get()) {
                        List<String> urls = new ArrayList<>();
                        for (String url : results) {
                            if (url != null) urls.add(url);
                        }
                        callback.onSuccess(urls);
                    }
                }

                @Override
                public void onFailure(String error) {
                    if (failed.compareAndSet(false, true)) {
                        callback.onFailure(error);
                    }
                }
            });
        }
    }

    // ── Upload ảnh ───────────────────────────────────────────────────────────

    // ── Upload ảnh ───────────────────────────────────────────────────────────

    public static void uploadImage(Context context, Uri imageUri, OnUploadCallback callback) {
        new Thread(() -> {
            File tempFile = null;
            try {
                tempFile = compressAndRotateImage(context, imageUri);
                if (tempFile == null) {
                    callback.onFailure("Lỗi xử lý ảnh");
                    return;
                }

                final File fileToUpload = tempFile;   // Phải final/effectively final

                MediaManager.get().upload(fileToUpload.getAbsolutePath())
                        .unsigned(UPLOAD_PRESET)
                        .option("resource_type", "image")
                        .option("timeout", 60000)           // 60 giây
                        .option("chunk_size", 4_000_000)
                        .callback(new UploadCallback() {
                            @Override
                            public void onStart(String requestId) {
                                // Có thể thêm callback tiến độ nếu cần
                            }

                            @Override
                            public void onProgress(String requestId, long bytes, long totalBytes) {
                                // Có thể cập nhật progress bar nếu muốn
                            }

                            @Override
                            public void onSuccess(String requestId, java.util.Map resultData) {
                                fileToUpload.delete();
                                String url = (String) resultData.get("secure_url");
                                if (url != null) {
                                    callback.onSuccess(url);
                                } else {
                                    callback.onFailure("Không lấy được URL");
                                }
                            }

                            @Override
                            public void onError(String requestId, ErrorInfo error) {
                                fileToUpload.delete();
                                callback.onFailure(error != null ? error.getDescription() : "Lỗi upload không xác định");
                            }

                            @Override
                            public void onReschedule(String requestId, ErrorInfo error) {
                                // Thử lại nếu cần
                            }
                        }).dispatch(context);

            } catch (Exception e) {
                if (tempFile != null) tempFile.delete();
                callback.onFailure("Lỗi hệ thống: " + e.getMessage());
            }
        }).start();
    }

    // ── Upload video ─────────────────────────────────────────────────────────

    public static void uploadVideo(Context context, Uri videoUri, OnUploadCallback callback) {
        new Thread(() -> {
            File tempFile = null;
            try {
                tempFile = copyUriToTempFile(context, videoUri, "upload_video_", ".mp4");
                if (tempFile == null) { callback.onFailure("Không đọc được video"); return; }

                final File fileToUpload = tempFile;
                MediaManager.get().upload(fileToUpload.getAbsolutePath())
                        .unsigned(UPLOAD_PRESET)
                        .option("resource_type", "video")
                        .option("chunk_size", 6_000_000)
                        .callback(new UploadCallback() {
                            @Override public void onStart(String requestId) {}
                            @Override public void onProgress(String requestId, long bytes, long totalBytes) {}
                            @Override
                            public void onSuccess(String requestId, java.util.Map resultData) {
                                fileToUpload.delete();
                                callback.onSuccess((String) resultData.get("secure_url"));
                            }
                            @Override
                            public void onError(String requestId, ErrorInfo error) {
                                fileToUpload.delete();
                                callback.onFailure(error.getDescription());
                            }
                            @Override public void onReschedule(String requestId, ErrorInfo error) {}
                        }).dispatch(context);

            } catch (Exception e) {
                if (tempFile != null) tempFile.delete();
                callback.onFailure("Lỗi upload video: " + e.getMessage());
            }
        }).start();
    }

    /**
     * Tạo thumbnail URL từ video URL Cloudinary.
     * Ví dụ: .../upload/v123/abc.mp4 → .../upload/so_0,w_400,c_fill,q_auto/v123/abc.jpg
     */
    public static String getVideoThumbnailUrl(String videoUrl) {
        if (videoUrl == null || !videoUrl.contains("cloudinary.com")) return null;
        return videoUrl
                .replace("/upload/", "/upload/so_0,w_400,c_fill,q_auto/")
                .replaceAll("\\.(mp4|mov|avi|mkv|webm)$", ".jpg");
    }

    /**
     * Chèn transform Cloudinary để tải ảnh đã resize + nén (q_auto,f_auto) → nhẹ hơn nhiều, load nhanh hơn.
     * URL không phải ảnh Cloudinary hoặc đã có transform thì giữ nguyên.
     */
    public static String optimizeImageUrl(String url, int width) {
        if (url == null || url.isEmpty()) return url;
        if (!url.contains("/image/upload/")) return url;
        if (url.contains("/upload/w_") || url.contains("/upload/c_") || url.contains("/upload/q_auto")) {
            return url;
        }
        return url.replace("/image/upload/",
                "/image/upload/w_" + width + ",c_limit,q_auto,f_auto/");
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    private static File copyUriToTempFile(Context context, Uri uri,
                                          String prefix, String suffix) throws IOException {
        File file = File.createTempFile(prefix, suffix, context.getCacheDir());
        try (InputStream in = context.getContentResolver().openInputStream(uri);
             FileOutputStream out = new FileOutputStream(file)) {
            if (in == null) return null;
            byte[] buf = new byte[8192];
            int len;
            while ((len = in.read(buf)) != -1) out.write(buf, 0, len);
        }
        return file;
    }

    private static File compressAndRotateImage(Context context, Uri uri) throws IOException {
        int rotation = 0;
        try (InputStream in = context.getContentResolver().openInputStream(uri)) {
            if (in != null) {
                ExifInterface exif = new ExifInterface(in);
                int orientation = exif.getAttributeInt(
                        ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
                switch (orientation) {
                    case ExifInterface.ORIENTATION_ROTATE_90:  rotation = 90;  break;
                    case ExifInterface.ORIENTATION_ROTATE_180: rotation = 180; break;
                    case ExifInterface.ORIENTATION_ROTATE_270: rotation = 270; break;
                }
            }
        }

        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        try (InputStream input = context.getContentResolver().openInputStream(uri)) {
            BitmapFactory.decodeStream(input, null, options);
        }

        // Tối ưu kích thước
        int maxSide = 1080; // Sửa từ 1280 thành 1080
        int sampleSize = 1;
        while (Math.max(options.outWidth, options.outHeight) / sampleSize > maxSide) {
            sampleSize *= 2;
        }

        options.inJustDecodeBounds = false;
        options.inSampleSize = sampleSize;

        Bitmap bitmap;
        try (InputStream input = context.getContentResolver().openInputStream(uri)) {
            bitmap = BitmapFactory.decodeStream(input, null, options);
        }

        if (bitmap == null) return null;

        // Rotate nếu cần
        if (rotation != 0) {
            Matrix matrix = new Matrix();
            matrix.postRotate(rotation);
            Bitmap rotated = Bitmap.createBitmap(bitmap, 0, 0,
                    bitmap.getWidth(), bitmap.getHeight(), matrix, true);
            bitmap.recycle();
            bitmap = rotated;
        }

        File file = File.createTempFile("upload_", ".jpg", context.getCacheDir());
        try (FileOutputStream out = new FileOutputStream(file)) {
            // Sửa chất lượng từ 78 xuống 70
            bitmap.compress(Bitmap.CompressFormat.JPEG, 70, out);
        } finally {
            bitmap.recycle();
        }
        return file;
    }

    /** URL video đã tối ưu (q_auto,f_auto) giúp load nhanh hơn và tương thích codec tốt hơn. */
    public static String getOptimizedVideoUrl(String videoUrl) {
        if (videoUrl == null || !videoUrl.contains("cloudinary.com")) return videoUrl;

        if (videoUrl.contains("/upload/q_auto")) return videoUrl;

        return videoUrl.replace("/upload/", "/upload/q_auto:good,f_auto,vc_auto,dl_2,so_0/");
    }
}