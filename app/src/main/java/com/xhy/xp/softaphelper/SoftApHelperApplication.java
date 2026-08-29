package com.xhy.xp.softaphelper;

import android.app.Application;
import android.content.SharedPreferences;
import android.util.Log;

import io.github.libxposed.service.XposedService;
import io.github.libxposed.service.XposedServiceHelper;

public class SoftApHelperApplication extends Application {
    private static final String TAG = "SoftApHelper";

    @Override
    public void onCreate() {
        super.onCreate();
        AppSettingsStore.initLocal(this);
        XposedServiceHelper.registerListener(new XposedServiceHelper.OnServiceListener() {
            @Override
            public void onServiceBind(XposedService service) {
                SharedPreferences preferences = service.getRemotePreferences(AppSettings.PREF_GROUP);
                AppSettingsStore.setRemotePreferences(preferences);
                Log.i(TAG, "Xposed service connected, remote preferences ready.");
            }

            @Override
            public void onServiceDied(XposedService service) {
                AppSettingsStore.clearRemotePreferences();
                Log.w(TAG, "Xposed service disconnected, remote preferences unavailable.");
            }
        });
    }
}
