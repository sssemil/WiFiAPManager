/*
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
import android.net.ConnectivityManager;
import android.os.Build;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;

public class Commons {
    public enum WIFI_AP_STATE {
        WIFI_AP_STATE_DISABLING,
        WIFI_AP_STATE_DISABLED,
        WIFI_AP_STATE_ENABLING,
        WIFI_AP_STATE_ENABLED,
        WIFI_AP_STATE_FAILED
    }

    public static class MobileDataUtils {

        public static boolean isMobileDataEnabled(Context context) {
            boolean mobileDataEnabled = false; // Assume disabled
            ConnectivityManager cm = (ConnectivityManager)
                    context.getSystemService(Context.CONNECTIVITY_SERVICE);
            try {
                Class cmClass = Class.forName(cm.getClass().getName());
                Method method = cmClass.getDeclaredMethod("getMobileDataEnabled");
                method.setAccessible(true); // Make the method callable
                // get the setting for "mobile data"
                mobileDataEnabled = (Boolean) method.invoke(cm);
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
            } catch (NoSuchMethodException e) {
                e.printStackTrace();
            } catch (InvocationTargetException e) {
                e.printStackTrace();
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            }
            return mobileDataEnabled;
        }

        public static void setMobileDataEnabled(Context context, boolean enabled) {
            try {
                if (isMobileDataEnabled(context) != enabled) {
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
                        final ConnectivityManager conman = (ConnectivityManager)
                                context.getSystemService(Context.CONNECTIVITY_SERVICE);
                        final Class conmanClass = Class.forName(conman.getClass().getName());
                        final Field connectivityManagerField
                                = conmanClass.getDeclaredField("mService");
                        connectivityManagerField.setAccessible(true);
                        final Object connectivityManager = connectivityManagerField.get(conman);
                        final Class connectivityManagerClass
                                = Class.forName(connectivityManager.getClass().getName());
                        final Method setMobileDataEnabledMethod
                                = connectivityManagerClass
                                .getDeclaredMethod("setMobileDataEnabled", Boolean.TYPE);
                        setMobileDataEnabledMethod.setAccessible(true);

                        setMobileDataEnabledMethod.invoke(connectivityManager, enabled);
                    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        setMobileNetworkfromLollipop(context);
                    }
                }
            } catch (NoSuchMethodException e) {
                e.printStackTrace();
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            } catch (NoSuchFieldException e) {
                e.printStackTrace();
            } catch (InvocationTargetException e) {
                e.printStackTrace();
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        public static void setMobileNetworkfromLollipop(Context context)
                throws InvocationTargetException, ClassNotFoundException,
                NoSuchMethodException, IllegalAccessException, NoSuchFieldException {
            String command;
            int state;
            // Get the current state of the mobile network.
            state = isMobileDataEnabled(context) ? 0 : 1;
            // Get the value of the "TRANSACTION_setDataEnabled" field.
            String transactionCode = getTransactionCode(context);
            // Android 5.1+ (API 22) and later.
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.LOLLIPOP) {
                SubscriptionManager mSubscriptionManager = (SubscriptionManager)
                        context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE);
                // Loop through the subscription list i.e. SIM list.
                for (int i = 0; i < mSubscriptionManager.getActiveSubscriptionInfoCountMax(); i++) {
                    if (transactionCode.length() > 0) {
                        // Get the active subscription ID for a given SIM card.
                        int subscriptionId = mSubscriptionManager.getActiveSubscriptionInfoList()
                                .get(i).getSubscriptionId();
                        // Execute the command via `su` to turn off
                        // mobile network for a subscription service.
                        command = "service call phone " + transactionCode + " i32 "
                                + subscriptionId + " i32 " + state;
                        ShellUtils.execCommand(command, true);
                    }
                }
            } else if (Build.VERSION.SDK_INT == Build.VERSION_CODES.LOLLIPOP) {
                // Android 5.0 (API 21) only.
                if (transactionCode.length() > 0) {
                    // Execute the command via `su` to turn off mobile network.
                    command = "service call phone " + transactionCode + " i32 " + state;
                    ShellUtils.execCommand(command, true);
                }
            }
        }

        public static String getTransactionCode(Context context)
                throws NoSuchFieldException, InvocationTargetException,
                IllegalAccessException, ClassNotFoundException, NoSuchMethodException {
            final TelephonyManager mTelephonyManager
                    = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
            final Class<?> mTelephonyClass = Class.forName(mTelephonyManager.getClass().getName());
            final Method mTelephonyMethod = mTelephonyClass.getDeclaredMethod("getITelephony");
            mTelephonyMethod.setAccessible(true);
            final Object mTelephonyStub = mTelephonyMethod.invoke(mTelephonyManager);
            final Class<?> mTelephonyStubClass = Class.forName(mTelephonyStub.getClass().getName());
            final Class<?> mClass = mTelephonyStubClass.getDeclaringClass();
            final Field field = mClass.getDeclaredField("TRANSACTION_setDataEnabled");
            field.setAccessible(true);
            return String.valueOf(field.getInt(null));
        }
    }

    /**
     * ShellUtils
     * <ul>
     * <strong>Execute command</strong>
     * <li>{@link ShellUtils#execCommand(String, boolean)}</li>
     * <li>{@link ShellUtils#execCommand(String[], boolean, boolean)}</li>
     * </ul>
     *
     * @author <a href="http://www.trinea.cn" target="_blank">Trinea</a> 2013-5-16
     */
    public static class ShellUtils {

        public static final String COMMAND_SU = "su";
        public static final String COMMAND_SH = "sh";
        public static final String COMMAND_EXIT = "exit\n";
        public static final String COMMAND_LINE_END = "\n";

        private ShellUtils() {
            throw new AssertionError();
        }

        /**
         * execute shell command, default return result msg
         *
         * @param command command
         * @param isRoot  whether need to run with root
         * @return ShellUtils#CommandResult
         * @see ShellUtils#execCommand(String[], boolean, boolean)
         */
        public static CommandResult execCommand(String command, boolean isRoot) {
            return execCommand(new String[]{command}, isRoot, true);
        }

        /**
         * execute shell commands
         *
         * @param commands        command array
         * @param isRoot          whether need to run with root
         * @param isNeedResultMsg whether need result msg
         * @return <ul>
         * <li>if isNeedResultMsg is false, {@link CommandResult#successMsg} is null and
         * {@link CommandResult#errorMsg} is null.</li>
         * <li>if {@link CommandResult#result} is -1, there maybe some excepiton.</li>
         * </ul>
         */
        public static CommandResult execCommand(String[] commands, boolean isRoot, boolean isNeedResultMsg) {
            int result = -1;
            if (commands == null || commands.length == 0) {
                return new CommandResult(result, null, null);
            }

            Process process = null;
            BufferedReader successResult = null;
            BufferedReader errorResult = null;
            StringBuilder successMsg = null;
            StringBuilder errorMsg = null;
            ArrayList<String> list = new ArrayList<>();

            DataOutputStream os = null;
            try {
                process = Runtime.getRuntime().exec(isRoot ? COMMAND_SU : COMMAND_SH);
                os = new DataOutputStream(process.getOutputStream());
                for (String command : commands) {
                    if (command == null) {
                        continue;
                    }

                    // donnot use os.writeBytes(commmand), avoid chinese charset error
                    os.write(command.getBytes());
                    os.writeBytes(COMMAND_LINE_END);
                    os.flush();
                }
                os.writeBytes(COMMAND_EXIT);
                os.flush();

                result = process.waitFor();
                // get command result
                if (isNeedResultMsg) {
                    successMsg = new StringBuilder();
                    errorMsg = new StringBuilder();
                    successResult = new BufferedReader(new InputStreamReader(process.getInputStream()));
                    errorResult = new BufferedReader(new InputStreamReader(process.getErrorStream()));
                    String s;
                    while ((s = successResult.readLine()) != null) {
                        successMsg.append(s);
                        if (!s.equals("") && !s.equals("\n")) {
                            list.add(s);
                        }
                    }
                    while ((s = errorResult.readLine()) != null) {
                        errorMsg.append(s);
                    }
                }
            } catch (IOException | InterruptedException e) {
                e.printStackTrace();
            } finally {
                try {
                    if (os != null) {
                        os.close();
                    }
                    if (successResult != null) {
                        successResult.close();
                    }
                    if (errorResult != null) {
                        errorResult.close();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }

                if (process != null) {
                    process.destroy();
                }
            }
            return new CommandResult(result, successMsg == null ? null : successMsg.toString(), errorMsg == null ? null
                    : errorMsg.toString(), list);
        }

        /**
         * result of command
         * <ul>
         * <li>{@link CommandResult#result} means result of command, 0 means normal, else means error, same to excute in
         * linux shell</li>
         * <li>{@link CommandResult#successMsg} means success message of command result</li>
         * <li>{@link CommandResult#errorMsg} means error message of command result</li>
         * </ul>
         *
         * @author <a href="http://www.trinea.cn" target="_blank">Trinea</a> 2013-5-16
         */
        public static class CommandResult {

            /**
             * result of command
             **/
            public int result;
            /**
             * success message of command result
             **/
            public String successMsg;
            /**
             * error message of command result
             **/
            public String errorMsg;

            ArrayList<String> list = new ArrayList<>();

            public CommandResult(int result, String successMsg, String errorMsg) {
                this.result = result;
                this.successMsg = successMsg;
                this.errorMsg = errorMsg;
            }

            public CommandResult(int result, String successMsg, String errorMsg, ArrayList<String> list) {
                this.result = result;
                this.successMsg = successMsg;
                this.errorMsg = errorMsg;
                this.list = list;
            }
        }
    }

    /**
     * Recognized key management schemes.
     */
    public static class KeyMgmt {
        /**
         * WPA is not used; plaintext or static WEP could be used.
         */
        public static final int NONE = 0;
        /**
         * WPA pre-shared key (requires {@code preSharedKey} to be specified).
         */
        public static final int WPA_PSK = 1;
        /**
         * WPA using EAP authentication. Generally used with an external authentication server.
         */
        public static final int WPA_EAP = 2;
        /**
         * IEEE 802.1X using EAP authentication and (optionally) dynamically
         * generated WEP keys.
         */
        public static final int IEEE8021X = 3;
        /**
         * WPA2 pre-shared key for use with soft access point
         * (requires {@code preSharedKey} to be specified).
         *
         * @hide
         */
        public static final int WPA2_PSK = 4;
        public static final String varName = "key_mgmt";
        public static final String[] strings = {"NONE", "WPA_PSK", "WPA_EAP", "IEEE8021X",
                "WPA2_PSK"};

        private KeyMgmt() {
        }
    }
}
