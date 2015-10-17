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

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;

import java.util.ArrayList;

import sssemil.com.wifiapmanager.Utils.ClientsList;
import sssemil.com.wifiapmanager.Utils.WifiApManager;

public class MainService extends Service implements SharedPreferences.OnSharedPreferenceChangeListener {

    private Context mContext;
    private Looper mLooper;

    private int mLastWifiClientCount = -1;
    private HandlerThread mScanThread;
    private Handler mScanHandler;

    private int mIcon;

    private NotificationCompat.Builder mTetheredNotificationBuilder;

    private NotificationManager mNotificationManager;
    private SharedPreferences mSharedPreferences;
    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if ("android.net.wifi.WIFI_AP_STATE_CHANGED".equals(action)) {
                handleWifiApStateChanged();
            }
        }
    };

    public MainService() {
    }

    @Override
    public IBinder onBind(Intent intent) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public void onCreate() {
        super.onCreate();

        mContext = this;

        IntentFilter intentFilter = new IntentFilter("android.net.wifi.WIFI_AP_STATE_CHANGED");
        intentFilter.addAction("android.net.conn.TETHER_STATE_CHANGED");
        intentFilter.addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED);
        intentFilter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);

        mIcon = R.drawable.ic_tt;

        mContext.registerReceiver(mReceiver, intentFilter);

        mNotificationManager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);

        mSharedPreferences.registerOnSharedPreferenceChangeListener(this);

        mLooper = getMainLooper();
        mScanThread = new HandlerThread("WifiClientScanner");
        if (!mScanThread.isAlive()) {
            mScanThread.start();
            mScanHandler = new WifiClientScanner(mScanThread.getLooper());
            mScanHandler.sendEmptyMessage(0);
        }
    }

    private void handleWifiApStateChanged() {
        WifiApManager wifiApManager = new WifiApManager(mContext);

        if (wifiApManager.isWifiApEnabled()
                && mSharedPreferences.getBoolean("show_notification", false)) {
            showTetheredNotification();
            if (!mScanThread.isAlive()) {
                mScanThread = new HandlerThread("WifiClientScanner");
                mScanThread.start();
                mScanHandler = new WifiClientScanner(mScanThread.getLooper());
                mScanHandler.sendEmptyMessage(0);
            }
        } else {
            clearTetheredNotification();
            if (mScanThread.isAlive()) {
                mScanThread.quit();
            }
        }
    }

    private void showTetheredNotification() {
        if (mNotificationManager == null) {
            return;
        }

        Intent intent = new Intent();
        intent.setClassName("sssemil.com.wifiapmanager",
                "sssemil.com.wifiapmanager.MainActivity");
        intent.setFlags(Intent.FLAG_ACTIVITY_NO_HISTORY);

        PendingIntent pi = PendingIntent.getActivity(mContext, 0, intent, 0, null);

        CharSequence title = getText(R.string.tethered_notification_title);
        CharSequence message = getText(R.string.tethered_notification_no_device_message);

        if (mTetheredNotificationBuilder == null) {
            mTetheredNotificationBuilder = new NotificationCompat.Builder(this)
                    .setSmallIcon(mIcon);
        }

        mTetheredNotificationBuilder
                .setSmallIcon(mIcon)
                .setContentTitle(title)
                .setContentText(message)
                .setOngoing(true)
                .setContentIntent(pi);

        mNotificationManager.notify(mIcon, mTetheredNotificationBuilder.build());
    }

    private void clearTetheredNotification() {
        mNotificationManager =
                (NotificationManager) mContext.getSystemService(Context.NOTIFICATION_SERVICE);
        if (mNotificationManager != null && mTetheredNotificationBuilder != null) {
            mNotificationManager.cancel(null, mIcon);
            mTetheredNotificationBuilder = null;
        }
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        handleWifiApStateChanged();
    }

    private class WifiClientScanner extends Handler {

        public WifiClientScanner(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            (new DoScan()).execute();
            sendEmptyMessageDelayed(0, 2000);
        }
    }

    private class DoScan extends AsyncTask<String, Void, String> {

        private int mCurrentClientCount = 0;

        @Override
        protected String doInBackground(String... params) {
            ArrayList<ClientsList.ClientScanResult> currentClientList
                    = ClientsList.get(mContext);
            for (int n = 0; n < currentClientList.size(); n++) {
                if (currentClientList.get(n).isReachable) {
                    mCurrentClientCount++;
                }
            }
            return null;
        }

        @Override
        protected void onPostExecute(String result) {
            new Handler(mLooper).post(new Runnable() {
                @Override
                public void run() {
                    if ((mLastWifiClientCount != mCurrentClientCount
                            || mLastWifiClientCount == -1)
                            && mTetheredNotificationBuilder != null) {
                        mLastWifiClientCount = mCurrentClientCount;
                        Intent intent = new Intent();
                        intent.setClassName("sssemil.com.wifiapmanager",
                                "sssemil.com.wifiapmanager.MainActivity");
                        intent.setFlags(Intent.FLAG_ACTIVITY_NO_HISTORY);

                        PendingIntent pi = PendingIntent.getActivity(mContext, 0, intent, 0,
                                null);

                        CharSequence title = getText(R.string.tethered_notification_title);
                        CharSequence message;
                        if (mCurrentClientCount == 0) {
                            message = getString(R.string.tethered_notification_no_device_message);
                        } else if (mCurrentClientCount == 1) {
                            message = getString(R.string.tethered_notification_one_device_message,
                                    mCurrentClientCount);
                        } else if (mCurrentClientCount > 1) {
                            message = getString(R.string.tethered_notification_multi_device_message,
                                    mCurrentClientCount);
                        } else {
                            message = getString(R.string.tethered_notification_no_device_message);
                        }

                        mTetheredNotificationBuilder.setContentIntent(pi)
                                .setContentTitle(title)
                                .setContentText(message)
                                .setOngoing(true);

                        mNotificationManager.notify(mIcon,
                                mTetheredNotificationBuilder.build());
                    }
                }
            });
        }
    }
}