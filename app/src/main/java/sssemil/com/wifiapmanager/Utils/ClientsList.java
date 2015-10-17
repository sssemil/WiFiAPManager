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

package sssemil.com.wifiapmanager.Utils;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.util.Log;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;

import sssemil.com.wifiapmanager.R;

public class ClientsList {

    private static final String TAG = "ClientsList";

    /**
     * Gets a list of the clients connected to the Hotspot, reachable deadline(-w) is 3(sec)
     *
     * @return ArrayList of {@link ClientScanResult}
     */
    public static ArrayList<ClientScanResult> get(Context context) {
        BufferedReader br = null;
        ArrayList<ClientScanResult> result = new ArrayList<>();

        try {
            br = new BufferedReader(new FileReader("/proc/net/arp"));
            String line;

            while ((line = br.readLine()) != null) {
                String[] splitted = line.split(" +");

                if (splitted.length >= 6) {
                    // Basic sanity check
                    String mac = splitted[3];

                    if (mac.matches("..:..:..:..:..:..")) {
                        boolean isReachable = isReachableByPing(splitted[0]);
                        ClientScanResult client = new ClientScanResult();
                        client.ipAddr = splitted[0];
                        if (mac.equals("00:00:00:00:00:00")) {
                            client.hwAddr = "---:---:---:---:---:---";
                        } else {
                            client.hwAddr = mac.toUpperCase();
                        }
                        client.device = splitted[5];
                        client.isReachable = isReachable;
                        client.vendor = getVendor(mac, context);
                        result.add(client);

                    }
                }
            }
        } catch (FileNotFoundException e) {
            Log.d(TAG, "catch FileNotFoundException hit in run", e);
        } catch (IOException e) {
            Log.d(TAG, "catch IOException hit in run", e);
        } catch (XmlPullParserException e) {
            Log.d(TAG, "catch XmlPullParserException hit in run", e);
        } finally {
            try {
                if (br != null) {
                    br.close();
                }
            } catch (IOException e) {
                Log.e(TAG, e.getMessage());
            }
        }

        return result;
    }

    public static String getVendor(String mac, Context context)
            throws XmlPullParserException, IOException {
        class Item {
            public String mac;
            public String vendor;
        }
        String[] macS = mac.split(":");
        mac = macS[0] + ":" + macS[1] + ":" + macS[2];
        XmlPullParser parser = context.getResources().getXml(R.xml.vendors);

        int eventType = parser.getEventType();
        Item currentProduct = null;

        while (eventType != XmlPullParser.END_DOCUMENT) {
            String name;
            switch (eventType) {
                case XmlPullParser.START_TAG:
                    name = parser.getName();
                    if (name.equals("item")) {
                        currentProduct = new Item();
                    } else if (currentProduct != null) {
                        if (name.equals("item-mac")) {
                            currentProduct.mac = parser.nextText();
                        } else if (name.equals("item-vendor")) {
                            currentProduct.vendor = parser.nextText();
                        }
                    }
                    break;
                case XmlPullParser.END_TAG:
                    name = parser.getName();
                    if (name.equalsIgnoreCase("item") && currentProduct != null) {
                        if (currentProduct.mac.equalsIgnoreCase(mac)) return currentProduct.vendor;
                    }
            }
            eventType = parser.next();
        }
        return "";
    }

    private static boolean isReachableByPing(String client) {
        try {
            Runtime runtime = Runtime.getRuntime();
            Process mIpAddrProcess = runtime.exec("/system/bin/ping -c 1 -w 3 " + client);
            int mExitValue = mIpAddrProcess.waitFor();
            return (mExitValue == 0);
        } catch (InterruptedException | IOException e) {
            // Ignore
        }
        return false;
    }


    public static void fixPermissions(Context context) {
        File file = new File("/data/misc/wifi/hostapd.deny");
        final String filePermissions = "su -c \"chmod 666 " + file.getPath() + "\"";
        boolean do_fix = false;
        boolean found = true;

        if (!file.canRead() || !file.canWrite()) {
            do_fix = true;
            try {
                Runtime.getRuntime().exec("su");
            } catch (IOException e) {
                found = false;
            }

            if (!found) {
                AlertDialog.Builder adb = new AlertDialog.Builder(context);
                adb.setTitle(context.getString(R.string.error));
                adb.setMessage(context.getString(R.string.no_root));
                adb.setPositiveButton(context.getString(android.R.string.ok), null);
                adb.show();
            }
        }

        if (do_fix) {
            Log.i(TAG, "Fixing permissions");
            final AlertDialog.Builder adbF = new AlertDialog.Builder(context);
            adbF.setTitle(context.getString(R.string.warning));
            adbF.setMessage(context.getString(R.string.bad_perm));
            adbF.setPositiveButton(context.getString(android.R.string.ok),
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            try {
                                Runtime.getRuntime().exec(filePermissions);
                                Process process = Runtime.getRuntime().exec("id");
                                BufferedReader bufferedReader = new BufferedReader(
                                        new InputStreamReader(process.getInputStream()));

                                String line;
                                while ((line = bufferedReader.readLine()) != null) {
                                    if (line.length() > 0) {
                                        Log.i(TAG, line);
                                    }
                                }
                                Log.d(TAG, filePermissions);
                            } catch (IOException e) {
                                Log.d(TAG, "catch " + e.toString() + " hit in run", e);
                            }
                        }
                    }
            );

            adbF.setNegativeButton(context.getString(android.R.string.cancel), null);
            adbF.show();
        }
    }

    public static ArrayList<String> getDeniedMACList() throws IOException {
        /*ArrayList<String> list = new ArrayList<>();
        Process process = Runtime.getRuntime().exec("su -c \"cat /default.prop\"");
        BufferedReader bufferedReader = new BufferedReader(
                new InputStreamReader(process.getInputStream()));

        String line;
        while ((line = bufferedReader.readLine()) != null) {
            Log.i(TAG, line);
            if (line.length() > 0 && !String.valueOf(line.charAt(0)).equals("#")) {
                list.add(line);
            }
        }*/

        Commons.ShellUtils.CommandResult result = Commons.ShellUtils.execCommand(
                "cat /data/misc/wifi/hostapd.deny", true);
        return result.list;
    }

    public static void setDeniedMACList(ArrayList<String> list)
            throws IOException, InterruptedException {
        clearMACList();
        for (int n = 0; n < list.size(); n++) {
            String command = "echo \"" +
                    list.get(n) + "\" >> /data/misc/wifi/hostapd.deny\n";
            Log.e("command", command);
            Commons.ShellUtils.execCommand(command, true);
        }
    }

    public static void addDeniedMACAddr(String mac)
            throws IOException, InterruptedException {
        String command = "echo \"" + mac + "\" >> /data/misc/wifi/hostapd.deny";
        Log.e("command", command);
        Commons.ShellUtils.execCommand(command, true);
    }

    public static void clearMACList() throws IOException, InterruptedException {
        String command = "echo \"\" > /data/misc/wifi/hostapd.deny";
        Log.e("command", command);
        Commons.ShellUtils.execCommand(command, true);
    }

    public static class ClientScanResult {
        public String ipAddr;
        public String hwAddr;
        public String device;
        public String vendor;
        public boolean isReachable;
    }
}
