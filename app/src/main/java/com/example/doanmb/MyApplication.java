package com.example.doanmb;

import android.app.Application;

import androidx.appcompat.app.AppCompatDelegate;

import com.cloudinary.android.MediaManager;
import com.example.doanmb.core.config.AppCheckInitializer;
import java.util.HashMap;
import java.util.Map;

public class MyApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();

        // Khoá app luôn ở chế độ sáng, không đổi theo dark mode của máy.
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        AppCheckInitializer.initialize(this);

        Map<String, String> config = new HashMap<>();
        config.put("cloud_name", "doanmb");

        MediaManager.init(this, config);
    }
}
