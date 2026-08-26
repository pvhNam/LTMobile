package com.example.doanmb.core.config;

import android.content.Context;
import android.content.SharedPreferences;

import com.example.doanmb.BuildConfig;
import com.google.firebase.FirebaseApp;
import com.google.firebase.appcheck.FirebaseAppCheck;
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory;

public final class AppCheckInitializer {
    private AppCheckInitializer() {}

    public static void initialize(Context context) {
        FirebaseApp firebaseApp = FirebaseApp.initializeApp(context);
        installLocalDebugSecret(context, firebaseApp);
        FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
                DebugAppCheckProviderFactory.getInstance());
    }

    private static void installLocalDebugSecret(Context context, FirebaseApp firebaseApp) {
        String debugSecret = BuildConfig.APP_CHECK_DEBUG_TOKEN;
        if (debugSecret == null || debugSecret.trim().isEmpty()) return;

        String preferencesName = String.format(
                "com.google.firebase.appcheck.debug.store.%s",
                firebaseApp.getPersistenceKey());
        SharedPreferences preferences = context.getSharedPreferences(
                preferencesName, Context.MODE_PRIVATE);
        preferences.edit()
                .putString("com.google.firebase.appcheck.debug.DEBUG_SECRET", debugSecret)
                .apply();
    }
}
