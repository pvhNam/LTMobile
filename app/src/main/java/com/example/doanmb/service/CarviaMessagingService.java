package com.example.doanmb.service;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Shader;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.app.Person;
import androidx.core.app.NotificationCompat.MessagingStyle;
import androidx.core.graphics.drawable.IconCompat;

import com.example.doanmb.MainActivity;
import com.example.doanmb.R;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CarviaMessagingService extends FirebaseMessagingService {

    private static final String TAG          = "CarviaMsgSvc";
    private static final String CHANNEL_CHAT = "carvia_chat";
    private static final String CHANNEL_NAME = "Tin nhắn";

    // Cache tin nhắn theo roomId để gộp nhiều tin thành dropdown giống Messenger
    private static final Map<String, List<MessagingStyle.Message>> messageCache = new HashMap<>();
    private static final Map<String, String> senderNameCache = new HashMap<>();

    // Cache avatar bitmap theo senderId để tránh tải lại
    private static final Map<String, Bitmap> avatarCache = new HashMap<>();

    private static final ExecutorService executor = Executors.newCachedThreadPool();

    @Override
    public void onMessageReceived(@NonNull RemoteMessage remoteMessage) {
        super.onMessageReceived(remoteMessage);

        Map<String, String> data = remoteMessage.getData();
        if (data.isEmpty()) return;

        String senderName = data.getOrDefault("senderName", "");
        String roomId     = data.getOrDefault("roomId",     "");
        String senderId   = data.getOrDefault("senderId",   "");
        String carName    = data.getOrDefault("carName",    "");
        // title vẫn dùng từ FCM (đúng: "Tin nhắn từ Kachi")
        String title      = data.getOrDefault("title",      "Tin nhắn mới");

        // ── BƯỚC 1: Lấy thêm carType để phân loại ──
        String carType    = data.getOrDefault("carType",    "");

        // ── Rẽ nhánh: Nếu là thông báo đơn hàng → gọi UI đơn hàng rồi thoát luôn ──
        if (carType.startsWith("order_")) {
            String body = data.getOrDefault("body", "Bạn có yêu cầu mới");
            // Lưu ý: với thông báo order, biến roomId đang chứa orderId
            showOrderNotification(title, body, senderName, carName, roomId);
            return;
        }
        // ───────────────────────────────────────────────────────────────────────────

        if (roomId.isEmpty()) {
            // Không có roomId → không thể lấy tin nhắn, dùng body từ FCM
            String body = data.getOrDefault("body", "Bạn có tin nhắn mới");
            fetchAvatarThenShow(title, body, senderName, carName, roomId, senderId, carType);
            return;
        }

        // ── Bước 1: Lấy tin nhắn THẬT mới nhất từ Firestore ──────────────────
        // Không tin vào body trong FCM payload — luôn đọc trực tiếp từ DB
        FirebaseFirestore.getInstance()
                .collection("chat_rooms")
                .document(roomId)
                .collection("messages")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(1)
                .get()
                .addOnSuccessListener(snapshots -> {
                    String realBody;
                    if (snapshots != null && !snapshots.isEmpty()) {
                        QueryDocumentSnapshot latestMsg =
                                (QueryDocumentSnapshot) snapshots.getDocuments().get(0);

                        String msgType    = latestMsg.getString("messageType");
                        String content    = latestMsg.getString("content");
                        String imageUrl   = latestMsg.getString("imageUrl");
                        String videoUrl   = latestMsg.getString("videoUrl");
                        Boolean recalled  = latestMsg.getBoolean("recalled");

                        if (Boolean.TRUE.equals(recalled)) {
                            realBody = "Tin nhắn đã bị thu hồi";
                        } else if (videoUrl != null && !videoUrl.isEmpty()) {
                            realBody = "[Video]";
                        } else if (imageUrl != null && !imageUrl.isEmpty()) {
                            realBody = "[Hình ảnh]";
                        } else if (content != null && !content.isEmpty()) {
                            realBody = content;
                        } else {
                            realBody = "Đã gửi một tin nhắn";
                        }
                    } else {
                        // Chưa có tin nhắn nào → fallback
                        realBody = senderName.isEmpty()
                                ? "Muốn trao đổi về " + carName
                                : senderName + " muốn trao đổi về " + carName;
                    }

                    Log.d(TAG, "Real message body: " + realBody);
                    fetchAvatarThenShow(title, realBody, senderName, carName, roomId, senderId, carType);
                })
                .addOnFailureListener(e -> {
                    // Firestore thất bại → fallback dùng body từ FCM
                    Log.w(TAG, "Không lấy được tin nhắn từ Firestore: " + e.getMessage());
                    String fallbackBody = data.getOrDefault("body", "Bạn có tin nhắn mới");
                    fetchAvatarThenShow(title, fallbackBody, senderName, carName, roomId, senderId, carType);
                });
    }

    /**
     * Lấy avatar người gửi từ Firestore rồi hiển thị notification.
     */
    private void fetchAvatarThenShow(String title, String body,
                                     String senderName, String carName,
                                     String roomId, String senderId,
                                     String carType) { // ← Cập nhật chữ ký hàm
        if (senderId != null && !senderId.isEmpty() && avatarCache.containsKey(senderId)) {
            showNotification(title, body, senderName, carName, roomId, senderId,
                    avatarCache.get(senderId), carType); // ← Thêm carType
            return;
        }

        if (senderId == null || senderId.isEmpty()) {
            showNotification(title, body, senderName, carName, roomId, senderId, null, carType);
            return;
        }

        FirebaseFirestore.getInstance().collection("users").document(senderId).get()
                .addOnSuccessListener(doc -> {
                    String avatarUrl = doc.exists() ? doc.getString("avatarUrl") : null;
                    if (avatarUrl != null && !avatarUrl.isEmpty()) {
                        executor.execute(() -> {
                            Bitmap avatar = downloadBitmapCircle(avatarUrl);
                            if (avatar != null) avatarCache.put(senderId, avatar);
                            showNotification(title, body, senderName, carName,
                                    roomId, senderId, avatar, carType); // ← Thêm carType
                        });
                    } else {
                        showNotification(title, body, senderName, carName,
                                roomId, senderId, null, carType);
                    }
                })
                .addOnFailureListener(e -> showNotification(title, body, senderName,
                        carName, roomId, senderId, null, carType));
    }

    /**
     * Tải ảnh từ URL và cắt thành hình tròn.
     */
    private Bitmap downloadBitmapCircle(String imageUrl) {
        try {
            URL url = new URL(imageUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(8_000);
            conn.setReadTimeout(8_000);
            conn.setDoInput(true);
            conn.connect();
            InputStream input = conn.getInputStream();
            Bitmap raw = BitmapFactory.decodeStream(input);
            input.close();
            conn.disconnect();
            if (raw == null) return null;

            int size = Math.min(raw.getWidth(), raw.getHeight());
            Bitmap squared = Bitmap.createBitmap(raw,
                    (raw.getWidth() - size) / 2,
                    (raw.getHeight() - size) / 2, size, size);
            Bitmap circle = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(circle);
            Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            BitmapShader shader = new BitmapShader(squared,
                    Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
            paint.setShader(shader);
            canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint);
            return circle;
        } catch (Exception e) {
            Log.w(TAG, "downloadBitmapCircle failed: " + e.getMessage());
            return null;
        }
    }

    @Override
    public void onNewToken(@NonNull String token) {
        super.onNewToken(token);
        Log.d(TAG, "New FCM token: " + token);
        saveFcmToken(token);
    }

    private void showNotification(String title, String body,
                                  String senderName, String carName,
                                  String roomId, String senderId,
                                  Bitmap avatarBitmap, String carType) { // ← Thêm carType
        createNotificationChannel();

        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);

        // Phân biệt luồng notification dựa vào carType
        if (carType != null && carType.startsWith("order_")) {
            intent.putExtra("OPEN_TAB", "manage");
            intent.putExtra("ORDER_ID", roomId); // roomId đang tạm dùng chứa orderId
        } else {
            intent.putExtra("OPEN_TAB", "messages");
            intent.putExtra("ROOM_ID", roomId);
        }

        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, roomId.hashCode(), intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        String displayName  = (senderName != null && !senderName.isEmpty())
                ? senderName : "Người dùng";
        String messageText  = (body != null && !body.isEmpty())
                ? body : "Đã gửi một tin nhắn";

        senderNameCache.put(roomId, displayName);

        if (!messageCache.containsKey(roomId)) {
            messageCache.put(roomId, new ArrayList<>());
        }
        List<MessagingStyle.Message> messages = messageCache.get(roomId);

        // Build Person với avatar thật (nếu có)
        Person.Builder personBuilder = new Person.Builder().setName(displayName);
        if (avatarBitmap != null) {
            personBuilder.setIcon(IconCompat.createWithBitmap(avatarBitmap));
        } else {
            personBuilder.setIcon(
                    IconCompat.createWithResource(this, R.mipmap.ic_launcher_round));
        }
        Person sender = personBuilder.build();

        messages.add(new MessagingStyle.Message(
                messageText, System.currentTimeMillis(), sender));

        if (messages.size() > 5) messages.remove(0);

        Person me = new Person.Builder().setName("Bạn").build();
        MessagingStyle style = new MessagingStyle(me)
                .setConversationTitle(!carName.isEmpty() ? carName : null);
        for (MessagingStyle.Message msg : messages) {
            style.addMessage(msg);
        }

        int notifId = Math.abs(roomId.hashCode() % 10000) + 1000;

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_CHAT)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setStyle(style)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setShowWhen(true)
                .setOnlyAlertOnce(false);

        if (avatarBitmap != null) {
            builder.setLargeIcon(avatarBitmap);
        } else {
            builder.setLargeIcon(BitmapFactory.decodeResource(
                    getResources(), R.mipmap.ic_launcher_round));
        }

        NotificationManagerCompat manager = NotificationManagerCompat.from(this);
        try {
            manager.notify(notifId, builder.build());
        } catch (SecurityException e) {
            Log.w(TAG, "Notification permission denied: " + e.getMessage());
        }
    }

    private void showOrderNotification(String title, String body,
                                       String senderName, String carName,
                                       String orderId) {
        createNotificationChannel();

        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);

        // Gửi lệnh mở tab Manage thay vì tab Messages
        intent.putExtra("OPEN_TAB", "manage");
        intent.putExtra("ORDER_ID", orderId);

        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, orderId.hashCode(), intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        int notifId = Math.abs(orderId.hashCode() % 10000) + 2000;

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_CHAT)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title)
                .setContentText(body)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent);

        try {
            NotificationManagerCompat.from(this).notify(notifId, builder.build());
        } catch (SecurityException e) {
            Log.w(TAG, "Notification permission denied: " + e.getMessage());
        }
    }

    public static void clearNotificationCache(String roomId) {
        messageCache.remove(roomId);
        senderNameCache.remove(roomId);
    }

    public static void clearAvatarCache(String userId) {
        avatarCache.remove(userId);
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_CHAT, CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription("Thông báo tin nhắn Carvia");
            channel.enableLights(true);
            channel.enableVibration(true);
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(channel);
        }
    }

    public static void saveFcmToken(Context context, String token) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null || token == null) return;
        Map<String, Object> data = new HashMap<>();
        data.put("fcmToken", token);
        FirebaseFirestore.getInstance()
                .collection("users")
                .document(user.getUid())
                .update(data)
                .addOnFailureListener(e -> Log.w("CarviaMsgSvc", "saveFcmToken failed", e));
    }

    private void saveFcmToken(String token) {
        saveFcmToken(this, token);
    }
}