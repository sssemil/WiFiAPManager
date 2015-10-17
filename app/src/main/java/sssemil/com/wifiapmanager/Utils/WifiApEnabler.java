/*
 * Copyright (C) 2010 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package sssemil.com.wifiapmanager.Utils;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.preference.PreferenceManager;
import android.preference.SwitchPreference;
import android.provider.Settings;

import sssemil.com.wifiapmanager.R;

public class WifiApEnabler {
    private final Context mContext;
    private final SwitchPreference mSwitch;
    private final OnStateChangeListener mListener;
    private final IntentFilter mIntentFilter;
    private WifiManager mWifiManager;
    private WifiApManager mWifiApManager;
    /* Indicates if we have to wait for WIFI_STATE_CHANGED intent */
    private boolean mWaitForWifiStateChange;
    private WifiApClientsProgressCategory mClientsCategory;
    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if ("android.net.wifi.WIFI_AP_STATE_CHANGED".equals(action)) {
                handleWifiApStateChanged(mWifiApManager.getWifiApState());
            } else if (WifiManager.WIFI_STATE_CHANGED_ACTION.equals(action)) {
                if (mWaitForWifiStateChange) {
                    handleWifiStateChanged(intent.getIntExtra(
                            WifiManager.EXTRA_WIFI_STATE, WifiManager.WIFI_STATE_UNKNOWN));
                }
            } else if (Intent.ACTION_AIRPLANE_MODE_CHANGED.equals(action)) {
                enableWifiSwitch();
            }
        }
    };

    public WifiApEnabler(Context context, SwitchPreference switchPreference,
                         OnStateChangeListener listener, WifiApClientsProgressCategory clientsCategory) {
        mContext = context;
        mSwitch = switchPreference;
        mListener = listener;
        mClientsCategory = clientsCategory;
        mWaitForWifiStateChange = false;

        mWifiManager = (WifiManager) mContext.getSystemService(Context.WIFI_SERVICE);
        mWifiApManager = new WifiApManager(context);

        mIntentFilter = new IntentFilter("android.net.wifi.WIFI_AP_STATE_CHANGED");
        mIntentFilter.addAction("android.net.conn.TETHER_STATE_CHANGED");
        mIntentFilter.addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED);
        mIntentFilter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
    }

    public void resume() {
        mContext.registerReceiver(mReceiver, mIntentFilter);
        enableWifiSwitch();
    }

    public void pause() {
        mContext.unregisterReceiver(mReceiver);
    }

    private void enableWifiSwitch() {
        boolean isAirplaneMode;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            isAirplaneMode = Settings.Global.getInt(mContext.getContentResolver(),
                    Settings.Global.AIRPLANE_MODE_ON, 0) != 0;
        } else {
            isAirplaneMode = Settings.System.getInt(mContext.getContentResolver(),
                    Settings.System.AIRPLANE_MODE_ON, 0) != 0;
        }
        if (!isAirplaneMode) {
            mSwitch.setEnabled(true);
        } else {
            mClientsCategory.setEmptyTextRes(R.string.wifi_ap_client_ap_disabled);
            mSwitch.setEnabled(false);
        }
    }

    public void setSoftapEnabled(boolean enable) {
        int wifiSavedState = 0;
        /**
         * Disable Wifi if enabling tethering
         */
        int wifiState = mWifiManager.getWifiState();
        if (enable && ((wifiState == WifiManager.WIFI_STATE_ENABLING) ||
                (wifiState == WifiManager.WIFI_STATE_ENABLED))) {
            mWifiManager.setWifiEnabled(false);
            //Settings.Global.putInt(cr, "wifi_saved_state", 1);
            PreferenceManager.getDefaultSharedPreferences(mContext).edit()
                    .putInt("wifi_saved_state", 1).apply();
        }

        SharedPreferences sharedPreferences
                = PreferenceManager.getDefaultSharedPreferences(mContext);
        if (enable && sharedPreferences.getBoolean("auto_enable_mobile_net", false)) {
            sharedPreferences.edit().putBoolean("was_mobile_net_enabled",
                    Commons.MobileDataUtils.isMobileDataEnabled(mContext)).apply();
            Commons.MobileDataUtils.setMobileDataEnabled(mContext, true);
        } else if (!enable && sharedPreferences.getBoolean("auto_enable_mobile_net", false)) {
            Commons.MobileDataUtils.setMobileDataEnabled(mContext,
                    sharedPreferences.getBoolean("was_mobile_net_enabled", false));
        }

        /**
         * Check if we have to wait for the WIFI_STATE_CHANGED intent
         * before we re-enable the Checkbox.
         */
        if (!enable) {
            wifiSavedState = PreferenceManager.getDefaultSharedPreferences(mContext)
                    .getInt("wifi_saved_state", 0);

            if (wifiSavedState == 1) {
                mWaitForWifiStateChange = true;
            }
        }

        if (mWifiApManager.setWifiApEnabled(null, enable)) {
            if (mSwitch != null) {
                /* Disable here, enabled on receiving success broadcast */
                mSwitch.setEnabled(false);
            }
        } else {
            if (mSwitch != null) {
                mClientsCategory.setEmptyTextRes(R.string.error);
            }
        }

        /**
         * If needed, restore Wifi on tether disable
         */
        if (!enable) {
            if (wifiSavedState == 1) {
                mWifiManager.setWifiEnabled(true);
                PreferenceManager.getDefaultSharedPreferences(mContext)
                        .edit().putInt("wifi_saved_state", 0).apply();
            }
        }
    }

    private void handleWifiApStateChanged(Commons.WIFI_AP_STATE state) {
        if (state == Commons.WIFI_AP_STATE.WIFI_AP_STATE_ENABLING) {
            mClientsCategory.setEmptyTextRes(R.string.wifi_tether_starting);
            mSwitch.setEnabled(false);
        } else if (state == Commons.WIFI_AP_STATE.WIFI_AP_STATE_ENABLED) {
            updateState(true);
                /* Doesn't need the airplane check */
            mClientsCategory.setEmptyTextRes(R.string.wifi_ap_client_none_connected);
            mSwitch.setEnabled(true);
        } else if (state == Commons.WIFI_AP_STATE.WIFI_AP_STATE_DISABLING) {
            mClientsCategory.setEmptyTextRes(R.string.wifi_tether_stopping);
            mSwitch.setChecked(false);
            mSwitch.setEnabled(false);
        } else if (state == Commons.WIFI_AP_STATE.WIFI_AP_STATE_DISABLED) {
            updateState(false);
            mClientsCategory.setEmptyTextRes(R.string.wifi_ap_client_ap_disabled);
            if (!mWaitForWifiStateChange) {
                enableWifiSwitch();
            }
        } else {
            updateState(false);
            mClientsCategory.setEmptyTextRes(R.string.wifi_ap_client_ap_disabled);
            enableWifiSwitch();
        }
    }

    private void updateState(boolean enabled) {
        mSwitch.setChecked(enabled);
        if (mListener != null) {
            mListener.onStateChanged(enabled);
        }
    }

    private void handleWifiStateChanged(int state) {
        switch (state) {
            case WifiManager.WIFI_STATE_ENABLED:
            case WifiManager.WIFI_STATE_UNKNOWN:
                enableWifiSwitch();
                mWaitForWifiStateChange = false;
                break;
            default:
        }
    }

    public interface OnStateChangeListener {
        void onStateChanged(boolean enabled);
    }
}