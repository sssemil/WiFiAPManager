/*
 * Copyright (C) 2015 Emil Suleymanov <suleymanovemil8@gmail.com>
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

package sssemil.com.wifiapmanager;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.wifi.WifiConfiguration;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.preference.Preference;
import android.preference.PreferenceScreen;
import android.preference.SwitchPreference;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.CompoundButton;

import java.io.IOException;
import java.util.ArrayList;

import sssemil.com.wifiapmanager.Utils.AppCompatPreferenceActivity;
import sssemil.com.wifiapmanager.Utils.ClientsList;
import sssemil.com.wifiapmanager.Utils.WIFI_AP_STATE;
import sssemil.com.wifiapmanager.Utils.WifiApClientsProgressCategory;
import sssemil.com.wifiapmanager.Utils.WifiApDialog;
import sssemil.com.wifiapmanager.Utils.WifiApEnabler;
import sssemil.com.wifiapmanager.Utils.WifiApManager;

public class MainActivity extends AppCompatPreferenceActivity implements
        DialogInterface.OnClickListener, CompoundButton.OnCheckedChangeListener,
        WifiApEnabler.OnStateChangeListener, Preference.OnPreferenceChangeListener {

    private static final String WIFI_AP_SSID_AND_SECURITY = "wifi_ap_ssid_and_security";
    private static final String CONNECTED_CLIENTS = "connected_clients";

    private static final int DIALOG_AP_SETTINGS = 1;
    private static final int PROVISION_REQUEST = 0;
    private static final String ENABLE_WIFI_AP = "enable_wifi_ap";
    private WifiApEnabler mWifiApEnabler;
    private String[] mSecurityType;
    private Preference mCreateNetwork;
    private WifiApClientsProgressCategory mClientsCategory;
    private ArrayList<ClientsList.ClientScanResult> mLastClientList;
    private boolean mApEnabled;
    private WifiApDialog mDialog;
    private WifiConfiguration mWifiConfig = null;
    private Handler mHandler = new ClientUpdateHandler();
    private Handler mScanHandler;
    private HandlerThread mScanThread;
    private boolean mIsRestarting = false;
    /* Stores the package name and the class name of the provisioning app */
    private String[] mProvisionApp;
    private WifiApManager mWifiApManager;
    private Activity mActivity;
    private SwitchPreference mEnableWifiAp;

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        addPreferencesFromResource(R.xml.wifi_ap_prefs);

        sendBroadcast(new Intent("sssemil.com.wifiapmanager.action.STARTED"));

        mActivity = this;

        mClientsCategory = (WifiApClientsProgressCategory) findPreference(CONNECTED_CLIENTS);

        mWifiApManager = new WifiApManager(this);

        mEnableWifiAp =
                (SwitchPreference) findPreference(ENABLE_WIFI_AP);

        final boolean wifiAvailable = getPackageManager().hasSystemFeature(PackageManager.FEATURE_WIFI);

        if (wifiAvailable) {
            mWifiApEnabler = new WifiApEnabler(mActivity, mEnableWifiAp, this, mClientsCategory);
            //mEnableWifiAp.setOnCheckedChangeListener(this);
            initWifiTethering();
        }

        mProvisionApp = getResources().getStringArray(R.array.config_mobile_hotspot_provision_app);
    }

    private void initWifiTethering() {
        mWifiConfig = mWifiApManager.getWifiApConfiguration();
        mSecurityType = getResources().getStringArray(R.array.wifi_ap_security);

        mCreateNetwork = findPreference(WIFI_AP_SSID_AND_SECURITY);

        if (mWifiConfig == null) {
            final String s = mActivity.getString(R.string.wifi_tether_configure_ssid_default);
            final String summary = String.format(getString(R.string.wifi_tether_configure_subtext),
                    s, mSecurityType[WifiApDialog.OPEN_INDEX]);
            mCreateNetwork.setSummary(summary);
        } else {
            int index = WifiApDialog.getSecurityTypeIndex(mWifiConfig);
            final String summary = String.format(getString(R.string.wifi_tether_configure_subtext),
                    mWifiConfig.SSID, mSecurityType[index]);
            mCreateNetwork.setSummary(summary);
        }
    }

    private void updateClientPreferences() {
        mClientsCategory.removeAll();

        if (mApEnabled) {
            mClientsCategory.setProgress(mLastClientList == null);
            mClientsCategory.setEmptyTextRes(R.string.wifi_ap_client_none_connected);
            if ((mLastClientList != null) && (mActivity != null)) {
                for (ClientsList.ClientScanResult client : mLastClientList) {
                    Preference preference = new Preference(mActivity);
                    preference.setTitle(client.hwAddr);
                    preference.setSummary(client.ipAddr + "   " + client.vendor);
                    mClientsCategory.addPreference(preference);
                }
            }
        } else {
            mClientsCategory.setProgress(false);
            mClientsCategory.setEmptyTextRes(R.string.wifi_ap_client_ap_disabled);
        }
    }

    @Override
    public Dialog onCreateDialog(int id) {
        if (id == DIALOG_AP_SETTINGS) {
            mDialog = new WifiApDialog(mActivity, this, mWifiConfig);
            return mDialog;
        }

        return null;
    }

    @Override
    public void onStart() {
        super.onStart();

        if (mWifiApEnabler != null) {
            mEnableWifiAp.setOnPreferenceChangeListener(this);
            mWifiApEnabler.resume();
        }

        mScanThread = new HandlerThread("WifiApClientScan");
        mScanThread.start();
        mScanHandler = new ClientScanHandler(mScanThread.getLooper());
        if (mApEnabled) {
            mScanHandler.sendEmptyMessage(0);
        }
        updateClientPreferences();
    }

    @Override
    public void onStop() {
        super.onStop();
        if (mWifiApEnabler != null) {
            mEnableWifiAp.setOnPreferenceChangeListener(null);
            mWifiApEnabler.pause();
        }

        mHandler.removeCallbacksAndMessages(null);
        mScanHandler.removeCallbacksAndMessages(null);
        mScanThread.quit();
        mScanHandler = null;
        mScanThread = null;
    }

    @Override
    public void onStateChanged(boolean enabled) {
        mApEnabled = enabled;
        mLastClientList = null;
        updateClientPreferences();
        if (enabled) {
            mScanHandler.sendEmptyMessage(0);
        }
    }

    boolean isProvisioningNeeded() {
        return mProvisionApp.length == 2;
    }

    private void startProvisioningIfNecessary() {
        if (isProvisioningNeeded()) {
            Intent intent = new Intent(Intent.ACTION_MAIN);
            intent.setClassName(mProvisionApp[0], mProvisionApp[1]);
            startActivityForResult(intent, PROVISION_REQUEST);
        } else {
            startTethering();
        }
    }

    public void onActivityResult(int requestCode, int resultCode, Intent intent) {
        super.onActivityResult(requestCode, resultCode, intent);
        if (requestCode == PROVISION_REQUEST) {
            if (resultCode == Activity.RESULT_OK) {
                startTethering();
            }
        }
    }

    public void startTethering() {
        mWifiApEnabler.setSoftapEnabled(true);
    }

    @Override
    public boolean onPreferenceTreeClick(PreferenceScreen screen, Preference preference) {
        if (preference == mCreateNetwork) {
            showDialog(DIALOG_AP_SETTINGS);
        } else {
            final String mac = String.valueOf(preference.getTitle());
            Log.i("PREF", mac);
            if(mac.matches("..:..:..:..:..:..")) {
                final AlertDialog.Builder adb = new AlertDialog.Builder(MainActivity.this);
                adb.setTitle(getString(R.string.warning));
                adb.setMessage(getString(R.string.block_dev));
                adb.setPositiveButton(getString(android.R.string.ok),
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                try {
                                    ClientsList.addDeniedMACAddr(mac);
                                    new Thread(new Runnable() {
                                        @Override
                                        public void run() {
                                            mWifiApManager.restartWifiAp();
                                        }
                                    }).start();
                                } catch (IOException | InterruptedException e) {
                                    e.printStackTrace();
                                }
                            }
                        }
                );
                adb.setNegativeButton(getString(android.R.string.cancel), null);
                adb.show();
            }
        }
        return super.onPreferenceTreeClick(screen, preference);
    }

    @Override
    public void onClick(DialogInterface dialogInterface, int button) {
        if (button == DialogInterface.BUTTON_POSITIVE) {
            mWifiConfig = mDialog.getConfig();
            if (mWifiConfig != null) {
                mWifiApManager.setWifiApConfiguration(mWifiConfig);
                if (mWifiApManager.getWifiApState() == WIFI_AP_STATE.WIFI_AP_STATE_ENABLED) {
                    mIsRestarting = true;
                    mWifiApEnabler.setSoftapEnabled(false);
                    mWifiApEnabler.setSoftapEnabled(true);
                }
                int index = WifiApDialog.getSecurityTypeIndex(mWifiConfig);
                String summary = String.format(getString(R.string.wifi_tether_configure_subtext),
                        mWifiConfig.SSID, mSecurityType[index]);
                mCreateNetwork.setSummary(summary);
            }
        }
    }

    public boolean onPreferenceChange(Preference preference, Object value) {
        boolean enable = (Boolean) value;

        if (enable) {
            startProvisioningIfNecessary();
        } else {
            if (!mIsRestarting) {
                mWifiApEnabler.setSoftapEnabled(false);
            } else {
                mIsRestarting = false;
            }
            mScanHandler.removeCallbacksAndMessages(null);
        }
        return false;
    }

    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        if (isChecked) {
            startProvisioningIfNecessary();
        } else {
            if (!mIsRestarting) {
                mWifiApEnabler.setSoftapEnabled(false);
            } else {
                mIsRestarting = false;
            }
            mScanHandler.removeCallbacksAndMessages(null);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu items for use in the action bar
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main_activity_actions, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle presses on the action bar items
        switch (item.getItemId()) {
            case R.id.action_settings:
                startActivity(new Intent(this, Settings.class));
                return true;
            case R.id.action_blocked:
                startActivity(new Intent(this, BlockedActivity.class));
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private final class ClientUpdateHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            ArrayList<ClientsList.ClientScanResult> clients
                    = (ArrayList<ClientsList.ClientScanResult>) msg.obj;
            if (!clients.equals(mLastClientList)) {
                mLastClientList = clients;
                updateClientPreferences();
            }
            if (mScanHandler != null) {
                mScanHandler.sendEmptyMessageDelayed(0, 2000);
            }
        }
    }

    private final class ClientScanHandler extends Handler {
        public ClientScanHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            ArrayList<ClientsList.ClientScanResult> clients
                    = ClientsList.get(true, mActivity);
            mHandler.obtainMessage(0, clients).sendToTarget();
        }
    }
}
