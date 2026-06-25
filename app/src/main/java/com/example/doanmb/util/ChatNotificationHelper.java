package com.example.doanmb.util;

import android.content.Context;
import android.util.Log;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.FirebaseFirestore;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Helper gửi thông báo qua FCM V1 API.
 * Hỗ trợ cả thông báo chat và thông báo đơn hàng (mua/thuê xe).
 */
public final class ChatNotificationHelper {

    private static final String TAG        = "ChatNotifHelper";
    private static final String PROJECT_ID = "doanmb-a73a9";
    private static final String FCM_URL    =
            "https://fcm.googleapis.com/v1/projects/" + PROJECT_ID + "/messages:send";
    private static final String TOKEN_URL  = "https://oauth2.googleapis.com/token";
    private static final String SCOPE      = "https://www.googleapis.com/auth/firebase.messaging";

    private static String cachedAccessToken    = null;
    private static long   tokenExpiryTimeMillis = 0;

    private static final ExecutorService executor = Executors.newCachedThreadPool();

    private ChatNotificationHelper() {}

    /**
     * Gọi khi app khởi động để cache access token sẵn.
     */
    public static void warmUpAccessToken(Context context) {
        executor.execute(() -> getAccessToken(context));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // THÔNG BÁO CHAT (giữ nguyên, không thay đổi)
    // ─────────────────────────────────────────────────────────────────────────

    public static void sendChatNotification(Context context,
                                            String receiverId,
                                            String senderId,
                                            String senderName,
                                            String carName,
                                            String carType,
                                            String messagePreview,
                                            String roomId) {
        if (receiverId == null || receiverId.isEmpty()) return;

        FirebaseFirestore db = FirebaseFirestore.getInstance();

        String title = buildTitle(senderName);
        String body  = buildBody(senderName, carName, carType, messagePreview);

        String notifDocId = receiverId + "_" + (roomId != null ? roomId : "");

        Map<String, Object> notif = new HashMap<>();
        notif.put("userId",     receiverId);
        notif.put("senderId",   senderId);
        notif.put("title",      title);
        notif.put("body",       body);
        notif.put("type",       "chat");
        notif.put("roomId",     roomId     != null ? roomId     : "");
        notif.put("carName",    carName    != null ? carName    : "");
        notif.put("carType",    carType    != null ? carType    : "sale");
        notif.put("senderName", senderName != null ? senderName : "");
        notif.put("read",       false);
        notif.put("createdAt",  Timestamp.now());

        db.collection("notifications").document(notifDocId).set(notif)
                .addOnSuccessListener(v -> Log.d(TAG, "Chat notif upserted: " + notifDocId))
                .addOnFailureListener(e -> Log.w(TAG, "Failed to upsert chat notif", e));

        final String finalTitle = title;
        final String finalBody  = body;

        db.collection("users").document(receiverId).get()
                .addOnSuccessListener(doc -> {
                    if (!doc.exists()) return;
                    String fcmToken = doc.getString("fcmToken");
                    if (fcmToken == null || fcmToken.isEmpty()) {
                        Log.d(TAG, "No FCM token for: " + receiverId);
                        return;
                    }
                    executor.execute(() ->
                            sendFcmV1(context, fcmToken, finalTitle, finalBody,
                                    senderName, carName, carType, roomId, senderId));
                })
                .addOnFailureListener(e -> Log.w(TAG, "Failed to get FCM token", e));
    }

    @Deprecated
    public static void sendChatNotification(String receiverId,
                                            String senderId,
                                            String senderName,
                                            String carName,
                                            String carType,
                                            String messagePreview,
                                            String roomId) {
        throw new IllegalStateException(
                "Phải truyền Context vào sendChatNotification. " +
                        "Dùng overload: sendChatNotification(context, receiverId, ...)");
    }

    @Deprecated
    public static void sendChatNotification(String receiverId,
                                            String senderId,
                                            String senderName,
                                            String carName,
                                            String messagePreview,
                                            String roomId) {
        throw new IllegalStateException(
                "Phải truyền Context vào sendChatNotification. " +
                        "Dùng overload: sendChatNotification(context, receiverId, ...)");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // THÔNG BÁO ĐƠN HÀNG — MỚI
    // type: "order_sent" | "order_confirmed" | "order_rejected"
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Gửi thông báo liên quan đến đơn mua/thuê xe.
     *
     * @param context     Context của Activity/Fragment gọi
     * @param receiverId  UID người nhận thông báo
     * @param senderId    UID người gửi (trigger sự kiện)
     * @param senderName  Tên người gửi (hiển thị trong thông báo)
     * @param carName     Tên xe liên quan
     * @param type        "order_sent" | "order_confirmed" | "order_rejected"
     * @param orderId     ID của đơn hàng trong Firestore
     */
    public static void sendOrderNotification(Context context,
                                             String receiverId,
                                             String senderId,
                                             String senderName,
                                             String carName,
                                             String carId,
                                             String type,
                                             String orderId) {
        if (receiverId == null || receiverId.isEmpty()) return;

        // Xây dựng title/body theo loại sự kiện
        String title, body;
        switch (type != null ? type : "") {
            case "order_confirmed":
                title = "✅ Yêu cầu được chấp nhận";
                body  = "Đơn của bạn cho xe \"" + safe(carName) + "\" đã được xác nhận!";
                break;
            case "order_rejected":
                title = "❌ Yêu cầu bị từ chối";
                body  = "Đơn của bạn cho xe \"" + safe(carName) + "\" đã bị từ chối.";
                break;
            default: // order_sent
                title = "📋 Yêu cầu mới từ " + safe(senderName);
                body  = safe(senderName) + " muốn đặt xe \"" + safe(carName) + "\" của bạn.";
                break;
        }

        FirebaseFirestore db = FirebaseFirestore.getInstance();

        // docId cố định theo buyer + car → mỗi cặp chỉ có 1 doc duy nhất
        // Gửi lại sẽ update read=false + createdAt=now → nổi lên đầu, không tạo thêm doc mới
        String notifDocId = receiverId + "_order_" + safe(senderId) + "_" + safe(carId);

        Map<String, Object> notif = new HashMap<>();
        notif.put("userId",     receiverId);
        notif.put("senderId",   safe(senderId));
        notif.put("title",      title);
        notif.put("body",       body);
        notif.put("type",       type != null ? type : "order_sent");
        notif.put("orderId",    safe(orderId));
        notif.put("carName",    safe(carName));
        notif.put("carId",      safe(carId));
        notif.put("senderName", safe(senderName));
        notif.put("read",       false);          // luôn reset về chưa đọc
        notif.put("createdAt",  Timestamp.now()); // reset thời gian → nổi lên đầu

        db.collection("notifications").document(notifDocId).set(notif)
                .addOnSuccessListener(v -> Log.d(TAG, "Order notif upserted: " + notifDocId))
                .addOnFailureListener(e -> Log.w(TAG, "Failed to save order notif", e));

        // 2. Lấy FCM token → gửi push notification
        final String finalTitle = title;
        final String finalBody  = body;
        final String finalType  = type != null ? type : "order_sent";

        db.collection("users").document(receiverId).get()
                .addOnSuccessListener(doc -> {
                    if (!doc.exists()) return;
                    String fcmToken = doc.getString("fcmToken");
                    if (fcmToken == null || fcmToken.isEmpty()) {
                        Log.d(TAG, "No FCM token for order notif: " + receiverId);
                        return;
                    }
                    executor.execute(() ->
                            sendFcmV1(context, fcmToken, finalTitle, finalBody,
                                    senderName, carName,
                                    finalType,
                                    safe(orderId),
                                    senderId));
                })
                .addOnFailureListener(e -> Log.w(TAG, "Failed to get FCM token for order", e));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // FCM V1 API — gọi từ background thread (dùng chung cho chat + order)
    // ─────────────────────────────────────────────────────────────────────────

    private static void sendFcmV1(Context context,
                                  String deviceToken,
                                  String title, String body,
                                  String senderName, String carName, String carType,
                                  String roomId, String senderId) {
        try {
            String accessToken = getAccessToken(context);
            if (accessToken == null) {
                Log.e(TAG, "Không lấy được access token");
                return;
            }

            JSONObject dataObj = new JSONObject();
            dataObj.put("title",      title);
            dataObj.put("body",       body);
            dataObj.put("senderName", safe(senderName));
            dataObj.put("carName",    safe(carName));
            dataObj.put("carType",    safe(carType));
            dataObj.put("roomId",     safe(roomId));
            dataObj.put("senderId",   safe(senderId));

            JSONObject androidConfig = new JSONObject();
            androidConfig.put("priority", "high");

            JSONObject messageObj = new JSONObject();
            messageObj.put("token",   deviceToken);
            messageObj.put("data",    dataObj);
            messageObj.put("android", androidConfig);

            JSONObject payload = new JSONObject();
            payload.put("message", messageObj);

            URL url = new URL(FCM_URL);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json; UTF-8");
            conn.setRequestProperty("Authorization", "Bearer " + accessToken);
            conn.setDoOutput(true);
            conn.setConnectTimeout(15_000);
            conn.setReadTimeout(15_000);

            try (OutputStream os = conn.getOutputStream()) {
                os.write(payload.toString().getBytes(StandardCharsets.UTF_8));
            }

            int code = conn.getResponseCode();
            if (code == 200) {
                Log.d(TAG, "FCM V1 gửi thành công!");
            } else {
                InputStream err = conn.getErrorStream();
                String errBody = err != null ? new BufferedReader(new InputStreamReader(err))
                        .lines().reduce("", (a, b) -> a + b) : "";
                Log.w(TAG, "FCM V1 lỗi " + code + ": " + errBody);
            }
            conn.disconnect();

        } catch (Exception e) {
            Log.e(TAG, "sendFcmV1 error: " + e.getMessage(), e);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
// THÔNG BÁO NHẮC NHỞ — gửi lại mỗi 10 phút nếu đơn vẫn pending
// ─────────────────────────────────────────────────────────────────────────

    public static void sendReminderNotification(Context context,
                                                String sellerId,
                                                String buyerId,
                                                String buyerName,
                                                String carName,
                                                String carId,
                                                String orderId) {
        if (sellerId == null || sellerId.isEmpty()) return;

        String title = "⏰ Nhắc nhở: Có yêu cầu chờ xử lý!";
        String body  = safe(buyerName) + " vẫn đang chờ bạn phản hồi về xe \""
                + safe(carName) + "\". Hãy xử lý sớm nhé!";

        FirebaseFirestore db = FirebaseFirestore.getInstance();

        // Dùng cùng docId với order_sent → ghi đè lên notification cũ
        // createdAt=now() → nổi lên đầu, read=false → chấm xanh xuất hiện lại
        String notifDocId = sellerId + "_order_" + safe(buyerId) + "_" + safe(carId);

        Map<String, Object> notif = new HashMap<>();
        notif.put("userId",     sellerId);
        notif.put("senderId",   safe(buyerId));
        notif.put("title",      title);
        notif.put("body",       body);
        notif.put("type",       "order_sent");
        notif.put("orderId",    safe(orderId));
        notif.put("carName",    safe(carName));
        notif.put("carId",      safe(carId));
        notif.put("senderName", safe(buyerName));
        notif.put("read",       false);
        notif.put("createdAt",  Timestamp.now());

        db.collection("notifications").document(notifDocId).set(notif)
                .addOnSuccessListener(v -> Log.d(TAG, "Reminder upserted: " + notifDocId))
                .addOnFailureListener(e -> Log.w(TAG, "Failed reminder notif", e));

        final String fTitle = title, fBody = body;
        db.collection("users").document(sellerId).get()
                .addOnSuccessListener(doc -> {
                    if (!doc.exists()) return;
                    String fcmToken = doc.getString("fcmToken");
                    if (fcmToken == null || fcmToken.isEmpty()) return;
                    executor.execute(() -> sendFcmV1(context, fcmToken, fTitle, fBody,
                            buyerName, carName, "order_sent", safe(orderId), buyerId));
                });
    }


    // ─────────────────────────────────────────────────────────────────────────
    // OAUTH2 ACCESS TOKEN
    // ─────────────────────────────────────────────────────────────────────────

    private static synchronized String getAccessToken(Context context) {
        if (cachedAccessToken != null &&
                System.currentTimeMillis() < tokenExpiryTimeMillis - 5 * 60 * 1000) {
            return cachedAccessToken;
        }

        try {
            Context appContext = context != null ? context.getApplicationContext() : null;
            if (appContext == null) {
                Log.e(TAG, "Context null, không đọc được service-account.json");
                return null;
            }

            InputStream is = appContext.getAssets().open("service-account.json");
            StringBuilder sb = new StringBuilder();
            BufferedReader reader = new BufferedReader(new InputStreamReader(is));
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
            is.close();

            JSONObject sa          = new JSONObject(sb.toString());
            String     clientEmail = sa.getString("client_email");
            String     privateKeyPem = sa.getString("private_key");

            PrivateKey privateKey = parsePrivateKey(privateKeyPem);
            String jwt = buildJwt(clientEmail, privateKey);
            String accessToken = exchangeJwtForToken(jwt);

            if (accessToken != null) {
                cachedAccessToken     = accessToken;
                tokenExpiryTimeMillis = System.currentTimeMillis() + 3600 * 1000;
            }
            return accessToken;

        } catch (Exception e) {
            Log.e(TAG, "getAccessToken error: " + e.getMessage(), e);
            return null;
        }
    }

    private static PrivateKey parsePrivateKey(String pem) throws Exception {
        String clean = pem
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s+", "");
        byte[] decoded = Base64.getDecoder().decode(clean);
        return KeyFactory.getInstance("RSA")
                .generatePrivate(new PKCS8EncodedKeySpec(decoded));
    }

    private static String buildJwt(String clientEmail, PrivateKey key) throws Exception {
        long now = System.currentTimeMillis() / 1000;

        JSONObject header = new JSONObject();
        header.put("alg", "RS256");
        header.put("typ", "JWT");
        String headerB64 = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(header.toString().getBytes(StandardCharsets.UTF_8));

        JSONObject claims = new JSONObject();
        claims.put("iss",   clientEmail);
        claims.put("scope", SCOPE);
        claims.put("aud",   TOKEN_URL);
        claims.put("iat",   now);
        claims.put("exp",   now + 3600);
        String claimsB64 = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(claims.toString().getBytes(StandardCharsets.UTF_8));

        String signingInput = headerB64 + "." + claimsB64;
        Signature sig = Signature.getInstance("SHA256withRSA");
        sig.initSign(key);
        sig.update(signingInput.getBytes(StandardCharsets.UTF_8));
        String sigB64 = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(sig.sign());

        return signingInput + "." + sigB64;
    }

    private static String exchangeJwtForToken(String jwt) throws Exception {
        String body = "grant_type=" +
                java.net.URLEncoder.encode("urn:ietf:params:oauth:grant-type:jwt-bearer", "UTF-8") +
                "&assertion=" + java.net.URLEncoder.encode(jwt, "UTF-8");

        URL url = new URL(TOKEN_URL);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        conn.setDoOutput(true);
        conn.setConnectTimeout(15_000);
        conn.setReadTimeout(15_000);

        try (OutputStream os = conn.getOutputStream()) {
            os.write(body.getBytes(StandardCharsets.UTF_8));
        }

        int code = conn.getResponseCode();
        if (code == 200) {
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(conn.getInputStream()));
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) response.append(line);
            JSONObject json = new JSONObject(response.toString());
            return json.getString("access_token");
        } else {
            Log.e(TAG, "exchangeJwt failed: " + code);
            return null;
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // HELPERS
    // ─────────────────────────────────────────────────────────────────────────

    public static String buildTitle(String senderName) {
        if (senderName != null && !senderName.isEmpty())
            return "Tin nhắn từ " + senderName;
        return "Tin nhắn mới";
    }

    /** @deprecated Dùng buildTitle(senderName) */
    public static String buildTitle(String senderName, String carName) {
        return buildTitle(senderName);
    }

    public static String buildBody(String senderName, String carName,
                                   String carType, String messagePreview) {
        if (messagePreview != null && !messagePreview.isEmpty()) {
            return messagePreview.length() > 70
                    ? messagePreview.substring(0, 70) + "…"
                    : messagePreview;
        }
        if (carName != null && !carName.isEmpty()) {
            String who    = (senderName != null && !senderName.isEmpty()) ? senderName : "Ai đó";
            String action = "rental".equalsIgnoreCase(carType) ? "thuê" : "mua";
            return who + " muốn " + action + " xe " + carName;
        }
        return "Bạn có tin nhắn mới";
    }

    public static String buildBody(String senderName, String carName, String messagePreview) {
        return buildBody(senderName, carName, "sale", messagePreview);
    }

    /** Trả về "" nếu giá trị null, tránh NullPointerException khi build payload. */
    private static String safe(String s) {
        return s != null ? s : "";
    }
}