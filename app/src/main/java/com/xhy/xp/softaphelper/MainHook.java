package com.xhy.xp.softaphelper;

import android.net.IpPrefix;
import android.net.LinkAddress;
import android.net.MacAddress;
import android.net.wifi.SoftApConfiguration;
import android.os.Build;
import android.util.Log;
import android.util.SparseIntArray;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam;
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam;
import io.github.libxposed.api.XposedModuleInterface.SystemServerStartingParam;

public class MainHook extends XposedModule {

    public static final String TAG = "SoftApHelper";

    private static final String className_P = "com.android.server.connectivity.tethering.TetherInterfaceStateMachine";
    private static final String className_Q = "android.net.ip.IpServer";

    private static final String methodName_P_Q = "getRandomWifiIPv4Address";
    private static final String methodName_R = "requestIpv4Address";

    private static final String callerMethodName_Q = "configureIPv4";

    private static final String WIFI_HOST_IFACE_ADDR = "192.168.43.1";

    // TetheringType
    public static final int TETHERING_INVALID = -1;
    public static final int TETHERING_WIFI = 0;
    public static final int TETHERING_USB = 1;
    public static final int TETHERING_BLUETOOTH = 2;
    public static final int TETHERING_WIFI_P2P = 3;
    public static final int TETHERING_NCM = 4;
    public static final int TETHERING_ETHERNET = 5;
    public static final int TETHERING_WIGIG = 6;

    private static final String WIFI_HOST_IFACE_ADDRESS = WIFI_HOST_IFACE_ADDR + "/24";
    private static final String USB_HOST_IFACE_ADDRESS = "192.168.42.1/24";
    private static final String BT_HOST_IFACE_ADDRESS = "192.168.44.1/24";
    private static final String P2P_HOST_IFACE_ADDRESS = "192.168.49.1/24";
    private static final String ETHERNET_HOST_IFACE_ADDRESS = "192.168.45.1/24";

    // staticBSSID Switch
    private static final boolean shouldStaticBSSID = false;

    private static HashMap<Integer, String> AddressMap = new HashMap<>();


    public static final int BAND_5GHZ = 1 << 1;
    public static final int CHANNEL_WIDTH_320MHZ = 11;
    // channel: 149,153,157,161,165
    // freq:    5745,5765,5785,5805,5825
    private static HashSet<Integer> AvailableChannelSet_LOW = new HashSet<>(Arrays.asList(36, 40, 44));
    private static HashSet<Integer> AvailableChannelSet_HIGH = new HashSet<>(Arrays.asList(149, 153, 157, 161, 165));
//    private static HashSet<Integer> AvailableChannelFreqSet = new HashSet<>(Arrays.asList(5745, 5765, 5785, 5805, 5825));

    private String processName = "";

    static {
        AddressMap.put(TETHERING_WIFI, WIFI_HOST_IFACE_ADDRESS);
        AddressMap.put(TETHERING_USB, USB_HOST_IFACE_ADDRESS);
        AddressMap.put(TETHERING_BLUETOOTH, BT_HOST_IFACE_ADDRESS);
        AddressMap.put(TETHERING_WIFI_P2P, P2P_HOST_IFACE_ADDRESS);
        AddressMap.put(TETHERING_ETHERNET, ETHERNET_HOST_IFACE_ADDRESS);
    }

    private void log(String msg) {
        log(Log.INFO, TAG, msg);
    }

    private boolean isConflictPrefix(Class<?> klass, Object thiz, IpPrefix prefix) throws Exception {
        Field field_mPrivateAddressCoordinator = ReflectUtils.findField(klass, "mPrivateAddressCoordinator");
        // Android 15+, bypass
        if(field_mPrivateAddressCoordinator == null){
            log("[Warning]: [" + WIFI_HOST_IFACE_ADDR + "] field_mPrivateAddressCoordinator not found.");
            return false;
        }
        Object mPrivateAddressCoordinator = field_mPrivateAddressCoordinator.get(thiz);
        Class<?> privateAddressCoordinator = mPrivateAddressCoordinator.getClass();
        // Android 12+
        Method m_getConflictPrefix = ReflectUtils.findMethod(privateAddressCoordinator, "getConflictPrefix");
        if (m_getConflictPrefix != null) {
            return m_getConflictPrefix.invoke(mPrivateAddressCoordinator, prefix) != null;
        }

        // Android 11
        Method m_isDownstreamPrefixInUse = ReflectUtils.findMethod(privateAddressCoordinator, "isDownstreamPrefixInUse");
        Method m_isConflictWithUpstream = ReflectUtils.findMethod(privateAddressCoordinator, "isConflictWithUpstream");
        if (m_isDownstreamPrefixInUse != null && m_isConflictWithUpstream != null) {
            return (boolean) m_isDownstreamPrefixInUse.invoke(mPrivateAddressCoordinator, prefix) ||
                    (boolean) m_isConflictWithUpstream.invoke(mPrivateAddressCoordinator, prefix);
        }

        log("[Error]: [isConflictPrefix] method not found.");
        return false;
    }

    @Override
    public void onModuleLoaded(ModuleLoadedParam param) {
        processName = param.getProcessName();
        log("[handleLoadPackage] module loaded in process: " + processName);
    }

    @Override
    public void onSystemServerStarting(SystemServerStartingParam param) {
        log("[handleLoadPackage] system server starting");
        handleLoad(param.getClassLoader());
    }

    @Override
    public void onPackageLoaded(PackageLoadedParam param) {
        handleLoad(param.getDefaultClassLoader());
    }

    private void handleLoad(ClassLoader classLoader) {
        // 固定热点ip
        final String className = Build.VERSION.SDK_INT <= Build.VERSION_CODES.P ? className_P :
                className_Q;
        final String methodName = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R ? methodName_R :
                methodName_P_Q;

        // 安卓9-10
        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.P ||
                Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) {
            try {
                Class<?> klass = classLoader.loadClass(className);
                Method method = ReflectUtils.findMethod(klass, methodName);
                if (method == null) {
                    log("[Error]: [" + methodName + "] not found in class " + klass.getName());
                    return;
                }
                log("[Success]: [" + methodName + "] found in " + processName);

                hook(method).intercept(chain -> WIFI_HOST_IFACE_ADDR);
            } catch (Exception exception) {
//                log("exception in " + processName + ": " + exception);
            }
        }
        // 安卓11+
        else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                Constructor<?> ctor_LinkAddress = LinkAddress.class.getDeclaredConstructor(String.class);
                Constructor<?> ctor_IpPrefix = IpPrefix.class.getDeclaredConstructor(String.class);

                Class<?> klass = classLoader.loadClass(className);
                Method method = ReflectUtils.findMethod(klass, methodName);
                if (method == null) {
                    log("[Error]: [" + methodName + "] not found in class " + klass.getName());
                    return;
                } else {
                    log("[Success]: [" + methodName + "] found in " + processName);
                }

                hook(method).intercept(chain -> {
                    Field field_mInterfaceType = ReflectUtils.findField(klass, "mInterfaceType");
                    int mInterfaceType = 0;
                    if(field_mInterfaceType == null){
                        // avoid exception
                        log("[Warning]: [" + WIFI_HOST_IFACE_ADDR + "] field_mInterfaceType not found.");
                    }else{
                        mInterfaceType = field_mInterfaceType.getInt(chain.getThisObject());
                    }

                    String address = AddressMap.get(mInterfaceType);

                    if (address != null && StackUtils.isCallingFrom(className, callerMethodName_Q)) {
                        final LinkAddress mLinkAddress = (LinkAddress) ctor_LinkAddress.newInstance(address);
                        final IpPrefix prefix = (IpPrefix) ctor_IpPrefix.newInstance(address);

                        if (isConflictPrefix(klass, chain.getThisObject(), prefix)) {
                            log("[Warning]: [" + WIFI_HOST_IFACE_ADDR + "] isConflictPrefix! do not replace.");
                        } else {
                            log("[Success Edit]:" + address);
                            return mLinkAddress;
                        }
                    }

                    return chain.proceed();
                });
            } catch (Exception exception) {
//                log("exception in " + processName + ": " + exception);
            }
        }

        //固定5G热点信道 (Android 9-11)
        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.P ||
                Build.VERSION.SDK_INT == Build.VERSION_CODES.Q ||
                        Build.VERSION.SDK_INT == Build.VERSION_CODES.R) {
            // TODO
        }
        // Android 12+
        else if (Build.VERSION.SDK_INT == Build.VERSION_CODES.S) {
            // TODO
        }
        // Android 13+
        else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S_V2) {
            try {
                for (Constructor<?> ctor : SoftApConfiguration.class.getDeclaredConstructors()) {
                    hook(ctor).intercept(chain -> {
                        Object[] args = chain.getArgs().toArray();

                        // staticBSSID
                        if(shouldStaticBSSID) {
                            args[1] = MacAddress.fromString("aa:bb:cc:dd:ee:ff");
                        }

                        SparseIntArray channels = (SparseIntArray) args[4];
                        int channel5gIndex = channels.indexOfKey(BAND_5GHZ);

                        Set<Integer> allowedAcsChannels5g = (Set<Integer>) args[21];
//                                    int maxChannelBandwidth = (int) args[23];

                        // config has set 5G channel
                        if (channel5gIndex >= 0) {
                            int channel = channels.get(BAND_5GHZ);

                            // 5GHz + allowedAcsChannels5g.size == 0
                            if (channel == 0 && allowedAcsChannels5g.size() == 0) {
                                // 5G ACS channels
                                args[21] = AvailableChannelSet_HIGH;
                                // max bandwidth
                                args[23] = CHANNEL_WIDTH_320MHZ;
                            }
                        }

                        return chain.proceed(args);
                    });
                }
            } catch (Exception exception) {
                log("exception in " + processName + ": " + exception);
            }
        }

        //隐藏热点类型 (Android 10+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                Class<?> klass = classLoader.loadClass("android.net.dhcp.DhcpServingParamsParcelExt");
                Method method = ReflectUtils.findMethod(klass, "setMetered");
                if (method == null) {
                    log("[Error]: [" + "setMetered" + "] not found in class " + klass.getName());
                }else {
                    log("[Success]: [" + "setMetered" + "] found in " + processName);

                    hook(method).intercept(chain -> {
                        Object[] args = chain.getArgs().toArray();
                        args[0] = false;
                        return chain.proceed(args);
                    });
                }


                Class<?> klassAccessPoint = classLoader.loadClass("android.net.wifi.WifiConfiguration");
                Method method_isMetered = ReflectUtils.findMethod(klassAccessPoint, "isMetered");
                if (method_isMetered == null) {
                    log("[Error]: [" + "isMetered" + "] not found in class " + klassAccessPoint.getName());
                }else {
                    log("[Success]: [" + "isMetered" + "] found in " + processName);

                    hook(method_isMetered).intercept(chain -> {
                        log("[Success]: [" + "isMetered" + "] stack:\n" + StackUtils.getStackTraceString());
                        return chain.proceed();
                    });
                }

            } catch (Exception exception) {
//                log("exception in " + processName + ": " + exception);
            }
        }
    }
}
