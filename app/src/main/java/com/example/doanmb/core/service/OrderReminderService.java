package com.example.doanmb.core.service;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;

import com.example.doanmb.core.helper.ChatNotificationHelper;
import com.google.firebase.firestore.FirebaseFirestore;

public class OrderReminderService extends Service {

    private static final String TAG = "OrderReminderSvc";
    public static final String ACTION_CHECK_REMINDER = "com.example.doanmb.ACTION_CHECK_REMINDER";
    public static final String EXTRA_ORDER_ID   = "orderId";
    public static final String EXTRA_SELLER_ID  = "sellerId";
    public static final String EXTRA_BUYER_ID   = "buyerId";
    public static final String EXTRA_BUYER_NAME = "buyerName";
    public static final String EXTRA_CAR_NAME   = "carName";
    public static final String EXTRA_CAR_ID     = "carId";

    private static final long INTERVAL_MS = 10 * 60 * 1000L; // 30 giây để test

    public static void schedule(Context context, String orderId, String sellerId,
                                String buyerId, String buyerName,
                                String carName, String carId) {
        if (orderId == null || orderId.isEmpty()) return;
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) return;

        // Android 12+ cần check quyền exact alarm
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            if (!alarmManager.canScheduleExactAlarms()) {
                Log.e(TAG, "❌ Chưa có quyền SCHEDULE_EXACT_ALARM — dùng inexact thay");
                PendingIntent pi = buildPendingIntent(context, orderId, sellerId, buyerId, buyerName, carName, carId);
                long triggerAt = System.currentTimeMillis() + INTERVAL_MS;
                alarmManager.setInexactRepeating(AlarmManager.RTC_WAKEUP, triggerAt, INTERVAL_MS, pi);
                return;
            }
        }

        PendingIntent pi = buildPendingIntent(context, orderId, sellerId, buyerId, buyerName, carName, carId);
        long triggerAt = System.currentTimeMillis() + INTERVAL_MS;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi);
        } else {
            alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAt, pi);
        }
        Log.d(TAG, "✅ Schedule alarm sau " + INTERVAL_MS/1000 + "s, orderId=" + orderId);
    }

    public static void cancel(Context context, String orderId) {
        if (orderId == null || orderId.isEmpty()) return;
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) return;

        Intent intent = new Intent(context, OrderReminderReceiver.class);
        intent.setAction(ACTION_CHECK_REMINDER);
        intent.putExtra(EXTRA_ORDER_ID, orderId);

        PendingIntent pi = PendingIntent.getBroadcast(
                context, orderId.hashCode(), intent,
                PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);
        if (pi != null) {
            alarmManager.cancel(pi);
            pi.cancel();
            Log.d(TAG, "✅ Đã hủy nhắc nhở cho orderId=" + orderId);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "onStartCommand được gọi");
        if (intent == null) { stopSelf(startId); return START_NOT_STICKY; }

        String orderId   = intent.getStringExtra(EXTRA_ORDER_ID);
        String sellerId  = intent.getStringExtra(EXTRA_SELLER_ID);
        String buyerId   = intent.getStringExtra(EXTRA_BUYER_ID);
        String buyerName = intent.getStringExtra(EXTRA_BUYER_NAME);
        String carName   = intent.getStringExtra(EXTRA_CAR_NAME);
        String carId     = intent.getStringExtra(EXTRA_CAR_ID);

        Log.d(TAG, "orderId=" + orderId + " sellerId=" + sellerId);

        if (orderId == null || orderId.isEmpty()) { stopSelf(startId); return START_NOT_STICKY; }

        checkAndRemind(orderId, sellerId, buyerId, buyerName, carName, carId, startId);
        return START_NOT_STICKY;
    }

    private void checkAndRemind(String orderId, String sellerId, String buyerId,
                                String buyerName, String carName, String carId, int startId) {
        FirebaseFirestore.getInstance().collection("orders").document(orderId).get()
                .addOnSuccessListener(doc -> {
                    if (!doc.exists()) {
                        Log.d(TAG, "Đơn không tồn tại, dừng nhắc.");
                        stopSelf(startId);
                        return;
                    }

                    String status = doc.getString("status");
                    Log.d(TAG, "Status đơn: " + status);

                    if (!"pending".equals(status)) {
                        Log.d(TAG, "Đơn đã xử lý, dừng nhắc.");
                        stopSelf(startId);
                        return;
                    }

                    String rSellerId  = sellerId  != null ? sellerId  : doc.getString("sellerId");
                    String rBuyerId   = buyerId   != null ? buyerId   : doc.getString("buyerId");
                    String rBuyerName = buyerName != null ? buyerName : "Khách hàng";
                    String rCarName   = carName   != null ? carName   : doc.getString("carName");
                    String rCarId     = carId     != null ? carId     : doc.getString("carId");

                    if (rSellerId == null || rSellerId.isEmpty()) {
                        Log.e(TAG, "sellerId rỗng, dừng nhắc.");
                        stopSelf(startId);
                        return;
                    }

                    ChatNotificationHelper.sendReminderNotification(
                            getApplicationContext(),
                            rSellerId,
                            rBuyerId   != null ? rBuyerId   : "",
                            rBuyerName,
                            rCarName   != null ? rCarName   : "",
                            rCarId     != null ? rCarId     : "",
                            orderId
                    );
                    Log.d(TAG, "✅ Đã gửi nhắc nhở, schedule lần tiếp theo...");

                    // Re-schedule lần tiếp theo vì setExactAndAllowWhileIdle không tự lặp
                    schedule(getApplicationContext(), orderId, rSellerId, rBuyerId,
                            rBuyerName, rCarName, rCarId);

                    stopSelf(startId);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Lỗi Firestore: " + e.getMessage());
                    stopSelf(startId);
                });
    }

    private static PendingIntent buildPendingIntent(Context context, String orderId,
                                                    String sellerId, String buyerId,
                                                    String buyerName, String carName, String carId) {
        Intent intent = new Intent(context, OrderReminderReceiver.class);
        intent.setAction(ACTION_CHECK_REMINDER);
        intent.putExtra(EXTRA_ORDER_ID,   orderId);
        intent.putExtra(EXTRA_SELLER_ID,  sellerId  != null ? sellerId  : "");
        intent.putExtra(EXTRA_BUYER_ID,   buyerId   != null ? buyerId   : "");
        intent.putExtra(EXTRA_BUYER_NAME, buyerName != null ? buyerName : "");
        intent.putExtra(EXTRA_CAR_NAME,   carName   != null ? carName   : "");
        intent.putExtra(EXTRA_CAR_ID,     carId     != null ? carId     : "");
        return PendingIntent.getBroadcast(context, orderId.hashCode(), intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) { return null; }
}