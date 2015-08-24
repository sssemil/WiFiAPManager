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

/**
 * Recognized key management schemes.
 */
public class KeyMgmt {
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
