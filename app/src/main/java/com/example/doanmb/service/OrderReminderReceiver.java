package com.example.doanmb.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

public class OrderReminderReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        if (!OrderReminderService.ACTION_CHECK_REMINDER.equals(intent.getAction())) return;

        Log.d("OrderReminderReceiver", "Nhận alarm, chuyển sang Service...");
        Intent serviceIntent = new Intent(context, OrderReminderService.class);
        Bundle extras = intent.getExtras();
        if (extras != null) serviceIntent.putExtras(extras);
        context.startService(serviceIntent);
    }
}