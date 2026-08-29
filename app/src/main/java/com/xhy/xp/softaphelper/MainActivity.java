package com.xhy.xp.softaphelper;

import android.app.Activity;
import android.content.ComponentName;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Arrays;

public class MainActivity extends Activity {
    private TextView subtitleText;
    private TextView statusText;
    private TextView cidrMessageText;
    private EditText cidrEdit;
    private Switch hideIconSwitch;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        AppSettingsStore.initLocal(this);
        subtitleText = findViewById(R.id.subtitle_text);
        statusText = findViewById(R.id.status_text);
        cidrMessageText = findViewById(R.id.cidr_message_text);
        cidrEdit = findViewById(R.id.cidr_edit);
        hideIconSwitch = findViewById(R.id.hide_icon_switch);
        Button saveCidrButton = findViewById(R.id.save_cidr_button);
        Button resetCidrButton = findViewById(R.id.reset_cidr_button);

        String savedCidr = getSavedWifiCidr();
        cidrEdit.setText(savedCidr);
        updateCidrMessage(savedCidr);

        boolean hideLauncherIcon = AppSettingsStore.getBoolean(this, AppSettings.KEY_HIDE_LAUNCHER_ICON, false);
        hideIconSwitch.setChecked(hideLauncherIcon);
        hideIconSwitch.setOnCheckedChangeListener(this::onHideIconChanged);

        cidrEdit.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                updateCidrMessage(String.valueOf(s));
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        saveCidrButton.setOnClickListener(v -> saveWifiCidr());
        resetCidrButton.setOnClickListener(v -> resetWifiCidr());

        refreshStatus();
    }

    private void onHideIconChanged(CompoundButton buttonView, boolean isChecked) {
        setLauncherAliasEnabled(!isChecked);
        AppSettingsStore.putBoolean(this, AppSettings.KEY_HIDE_LAUNCHER_ICON, isChecked);
        refreshStatus();
    }

    private void saveWifiCidr() {
        String value = cidrEdit.getText().toString().trim();
        if (!CidrUtils.isValidIpv4Cidr(value)) {
            cidrMessageText.setTextColor(getColorCompat(R.color.color_error));
            cidrMessageText.setText("CIDR 格式无效，请输入类似 192.168.43.1/24 的 IPv4/CIDR。");
            return;
        }

        AppSettingsStore.putString(this, AppSettings.KEY_WIFI_CIDR, value);
        updateCidrMessage(value);
        refreshStatus();
        Toast.makeText(this, buildSavedToast(), Toast.LENGTH_SHORT).show();
    }

    private void resetWifiCidr() {
        cidrEdit.setText(AppSettings.DEFAULT_WIFI_CIDR);
        AppSettingsStore.putString(this, AppSettings.KEY_WIFI_CIDR, AppSettings.DEFAULT_WIFI_CIDR);
        updateCidrMessage(AppSettings.DEFAULT_WIFI_CIDR);
        refreshStatus();
    }

    private void setLauncherAliasEnabled(boolean enabled) {
        PackageManager packageManager = getPackageManager();
        ComponentName componentName = new ComponentName(this, AppSettings.LAUNCHER_ALIAS);
        int state = enabled
                ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                : PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
        packageManager.setComponentEnabledSetting(
                componentName,
                state,
                PackageManager.DONT_KILL_APP
        );
    }

    private void refreshStatus() {
        String savedCidr = getSavedWifiCidr();
        subtitleText.setText("Wi-Fi 热点地址 " + savedCidr + " · API " + Build.VERSION.SDK_INT);

        ArrayList<String> pkgNameList = new ArrayList<>(
                Arrays.asList(
                        "com.android.networkstack.tethering.inprocess",
                        "com.android.networkstack.tethering",
                        "com.google.android.networkstack.tethering.inprocess",
                        "com.google.android.networkstack.tethering"
                ));

        StringBuilder sb = new StringBuilder();
        sb.append("已检测到的 Tethering 组件：\n");
        int installedCount = 0;
        for (String pkgName : pkgNameList) {
            if (isInstalled(pkgName)) {
                installedCount++;
                sb.append("- ").append(pkgName).append('\n');
            }
        }
        if (installedCount == 0) {
            sb.append("- 未检测到独立 Tethering 包，可在 LSPosed 中选择 system/Android 作用域\n");
        }

        sb.append('\n');
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S_V2) {
            sb.append("5G 信道和频宽锁定：已支持");
        } else {
            sb.append("5G 信道和频宽锁定：仅 Android 13+ 支持");
        }
        sb.append('\n');
        sb.append("LSPosed 配置服务：").append(AppSettingsStore.isRemoteReady() ? "已连接" : "未连接");
        sb.append('\n');
        sb.append("桌面图标：").append(hideIconSwitch.isChecked() ? "已隐藏" : "已显示");
        sb.append('\n');
        sb.append("当前 Wi-Fi CIDR：").append(savedCidr);

        statusText.setText(sb.toString());
    }

    private void updateCidrMessage(String value) {
        if (CidrUtils.isValidIpv4Cidr(value)) {
            cidrMessageText.setTextColor(getColorCompat(R.color.color_on_surface_variant));
            cidrMessageText.setText("将使用 " + CidrUtils.normalizeIpv4Cidr(value) + " 作为 Wi-Fi 热点地址段。");
        } else {
            cidrMessageText.setTextColor(getColorCompat(R.color.color_error));
            cidrMessageText.setText("请输入有效 IPv4/CIDR，前缀范围 1-30。");
        }
    }

    private String getSavedWifiCidr() {
        return CidrUtils.normalizeIpv4Cidr(
                AppSettingsStore.getString(this, AppSettings.KEY_WIFI_CIDR, AppSettings.DEFAULT_WIFI_CIDR)
        );
    }

    private String buildSavedToast() {
        if (AppSettingsStore.isRemoteReady()) {
            return "CIDR 已保存，重启 Tethering 或设备后生效";
        }
        return "CIDR 已保存到本地，LSPosed 配置服务连接后才会写入模块配置";
    }

    public boolean isInstalled(String pkgName) {
        PackageManager packageManager = this.getApplicationContext().getPackageManager();
        try {
            packageManager.getApplicationInfo(pkgName, PackageManager.GET_UNINSTALLED_PACKAGES);
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
        return true;
    }

    private int getColorCompat(int colorRes) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return getColor(colorRes);
        }
        return getResources().getColor(colorRes);
    }
}
