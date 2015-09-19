/*
 * Copyright 2013 WhiteByte (Nick Russler, Ahmet Yueksektepe)
 * Copyright 2015 Emil Suleymanov <suleymanovemil8@gmail.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package sssemil.com.wifiapmanager.Utils;

import android.content.Context;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.util.Log;

import java.lang.reflect.Method;


public class WifiApManager {
    private final WifiManager mWifiManager;

    public WifiApManager(Context context) {
        mWifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
    }

    /**
     * Start AccessPoint mode with the specified
     * configuration. If the radio is already running in
     * AP mode, update the new configuration
     * Note that starting in access point mode disables station
     * mode operation
     *
     * @param wifiConfig SSID, security and channel details as part of WifiConfiguration
     * @return {@code true} if the operation succeeds, {@code false} otherwise
     */
    public boolean setWifiApEnabled(WifiConfiguration wifiConfig, boolean enabled) {
        try {
            if (enabled) {
                mWifiManager.setWifiEnabled(false);
            }

            Method method = mWifiManager.getClass().getMethod("setWifiApEnabled",
                    WifiConfiguration.class, boolean.class);
            return (Boolean) method.invoke(mWifiManager, wifiConfig, enabled);
        } catch (Exception e) {
            Log.e(this.getClass().toString(), "", e);
            return false;
        }
    }

    /**
     * Gets the WiFi enabled state.
     *
     * @return {@link Commons.WIFI_AP_STATE}
     * @see #isWifiApEnabled()
     */
    public Commons.WIFI_AP_STATE getWifiApState() {
        try {
            Method method = mWifiManager.getClass().getMethod("getWifiApState");

            int tmp = ((Integer) method.invoke(mWifiManager));

            // Fix for Android 4
            if (tmp >= 10) {
                tmp = tmp - 10;
            }

            return Commons.WIFI_AP_STATE.class.getEnumConstants()[tmp];
        } catch (Exception e) {
            Log.e(this.getClass().toString(), "", e);
            return Commons.WIFI_AP_STATE.WIFI_AP_STATE_FAILED;
        }
    }

    /**
     * Return whether WiFi AP is enabled or disabled.
     *
     * @return {@code true} if WiFi AP is enabled
     */
    public boolean isWifiApEnabled() {
        return getWifiApState() == Commons.WIFI_AP_STATE.WIFI_AP_STATE_ENABLED;
    }

    /**
     * Gets the WiFi AP Configuration.
     *
     * @return AP details in {@link WifiConfiguration}
     */
    public WifiConfiguration getWifiApConfiguration() {
        try {
            Method method = mWifiManager.getClass().getMethod("getWifiApConfiguration");
            return (WifiConfiguration) method.invoke(mWifiManager);
        } catch (Exception e) {
            Log.e(this.getClass().toString(), "", e);
            return null;
        }
    }

    /**
     * Sets the WiFi AP Configuration.
     *
     * @return {@code true} if the operation succeeded, {@code false} otherwise
     */
    public boolean setWifiApConfiguration(WifiConfiguration wifiConfig) {
        try {
            Method method = mWifiManager.getClass().getMethod("setWifiApConfiguration",
                    WifiConfiguration.class);
            return (Boolean) method.invoke(mWifiManager, wifiConfig);
        } catch (Exception e) {
            Log.e(this.getClass().toString(), "", e);
            return false;
        }
    }

    /**
     * Stop and start AP if it was running with previous configuration.
     *
     * @return {@code true} if the operation succeeds, {@code false} otherwise
     */
    public boolean restartWifiAp() {
        if (isWifiApEnabled()) {
            WifiConfiguration wifiConfiguration = getWifiApConfiguration();
            setWifiApEnabled(null, false);
            return setWifiApEnabled(wifiConfiguration, true);
        }
        return false;
    }
}