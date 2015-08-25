package sssemil.com.wifiapmanager.Utils;

import android.content.Context;
import android.net.ConnectivityManager;
import android.os.Build;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Created by emil on 25/08/15.
 */
public class MobileDataUtils {

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
                    final ConnectivityManager conman = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
                    final Class conmanClass = Class.forName(conman.getClass().getName());
                    final Field connectivityManagerField = conmanClass.getDeclaredField("mService");
                    connectivityManagerField.setAccessible(true);
                    final Object connectivityManager = connectivityManagerField.get(conman);
                    final Class connectivityManagerClass = Class.forName(connectivityManager.getClass().getName());
                    final Method setMobileDataEnabledMethod = connectivityManagerClass.getDeclaredMethod("setMobileDataEnabled", Boolean.TYPE);
                    setMobileDataEnabledMethod.setAccessible(true);

                    setMobileDataEnabledMethod.invoke(connectivityManager, enabled);
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    setMobileNetworkfromLollipop(context, enabled);
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

    public static void setMobileNetworkfromLollipop(Context context, boolean enabled)
            throws InvocationTargetException, ClassNotFoundException,
            NoSuchMethodException, IllegalAccessException, NoSuchFieldException {
        String command = null;
        int state = 0;
        // Get the current state of the mobile network.
        state = isMobileDataEnabled(context) ? 0 : 1;
        // Get the value of the "TRANSACTION_setDataEnabled" field.
        String transactionCode = getTransactionCode(context);
        // Android 5.1+ (API 22) and later.
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.LOLLIPOP) {
            SubscriptionManager mSubscriptionManager = (SubscriptionManager) context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE);
            // Loop through the subscription list i.e. SIM list.
            for (int i = 0; i < mSubscriptionManager.getActiveSubscriptionInfoCountMax(); i++) {
                if (transactionCode != null && transactionCode.length() > 0) {
                    // Get the active subscription ID for a given SIM card.
                    int subscriptionId = mSubscriptionManager.getActiveSubscriptionInfoList().get(i).getSubscriptionId();
                    // Execute the command via `su` to turn off
                    // mobile network for a subscription service.
                    command = "service call phone " + transactionCode + " i32 " + subscriptionId + " i32 " + state;
                    ShellUtils.execCommand(command, true);
                }
            }
        } else if (Build.VERSION.SDK_INT == Build.VERSION_CODES.LOLLIPOP) {
            // Android 5.0 (API 21) only.
            if (transactionCode != null && transactionCode.length() > 0) {
                // Execute the command via `su` to turn off mobile network.
                command = "service call phone " + transactionCode + " i32 " + state;
                ShellUtils.execCommand(command, true);
            }
        }
    }

    public static String getTransactionCode(Context context)
            throws NoSuchFieldException, InvocationTargetException,
            IllegalAccessException, ClassNotFoundException, NoSuchMethodException {
        final TelephonyManager mTelephonyManager = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
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
