package com.xhy.xp.softaphelper;

import android.content.Context;
import android.content.SharedPreferences;

public final class AppSettingsStore {
    private static SharedPreferences localPreferences;
    private static SharedPreferences remotePreferences;

    private AppSettingsStore() {
    }

    public static synchronized void initLocal(Context context) {
        if (localPreferences == null) {
            localPreferences = context.getApplicationContext()
                    .getSharedPreferences(AppSettings.PREF_GROUP, Context.MODE_PRIVATE);
        }
    }

    public static synchronized void setRemotePreferences(SharedPreferences preferences) {
        remotePreferences = preferences;
        copyLocalToRemote();
    }

    public static synchronized void clearRemotePreferences() {
        remotePreferences = null;
    }

    public static synchronized boolean isRemoteReady() {
        return remotePreferences != null;
    }

    public static synchronized String getString(Context context, String key, String defaultValue) {
        SharedPreferences preferences = getReadablePreferences(context);
        return preferences.getString(key, defaultValue);
    }

    public static synchronized boolean getBoolean(Context context, String key, boolean defaultValue) {
        SharedPreferences preferences = getReadablePreferences(context);
        return preferences.getBoolean(key, defaultValue);
    }

    public static synchronized void putString(Context context, String key, String value) {
        initLocal(context);
        if (remotePreferences != null) {
            remotePreferences.edit().putString(key, value).apply();
        }
        localPreferences.edit().putString(key, value).apply();
    }

    public static synchronized void putBoolean(Context context, String key, boolean value) {
        initLocal(context);
        if (remotePreferences != null) {
            remotePreferences.edit().putBoolean(key, value).apply();
        }
        localPreferences.edit().putBoolean(key, value).apply();
    }

    private static SharedPreferences getReadablePreferences(Context context) {
        initLocal(context);
        return remotePreferences != null ? remotePreferences : localPreferences;
    }

    private static void copyLocalToRemote() {
        if (localPreferences == null || remotePreferences == null) {
            return;
        }
        SharedPreferences.Editor editor = remotePreferences.edit();
        editor.putString(
                AppSettings.KEY_WIFI_CIDR,
                localPreferences.getString(AppSettings.KEY_WIFI_CIDR, AppSettings.DEFAULT_WIFI_CIDR)
        );
        editor.putBoolean(
                AppSettings.KEY_HIDE_LAUNCHER_ICON,
                localPreferences.getBoolean(AppSettings.KEY_HIDE_LAUNCHER_ICON, false)
        );
        editor.apply();
    }
}
