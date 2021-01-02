/*
 * Copyright (C) 2008 The Android Open Source Project
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
package android.net;

import android.annotation.*;
import android.annotation.SdkConstant.SdkConstantType;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build.VERSION_CODES;
import android.os.*;
import android.util.SparseIntArray;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.net.InetAddress;
import java.util.HashMap;

/**
 * Class that answers queries about the state of network connectivity. It also
 * notifies applications when network connectivity changes.
 * <p>
 * The primary responsibilities of this class are to:
 * <ol>
 * <li>Monitor network connections (Wi-Fi, GPRS, UMTS, etc.)</li>
 * <li>Send broadcast intents when network connectivity changes</li>
 * <li>Attempt to "fail over" to another network when connectivity to a network
 * is lost</li>
 * <li>Provide an API that allows applications to query the coarse-grained or fine-grained
 * state of the available networks</li>
 * <li>Provide an API that allows applications to request and select networks for their data
 * traffic</li>
 * </ol>
 */
public class ConnectivityManager {
    public static final ConnectivityManager INSTANCE = new ConnectivityManager();

    private static final String TAG = "ConnectivityManager";

    /**
     * A change in network connectivity has occurred. A default connection has either
     * been established or lost. The NetworkInfo for the affected network is
     * sent as an extra; it should be consulted to see what kind of
     * connectivity event occurred.
     * <p/>
     * Apps targeting Android 7.0 (API level 24) and higher do not receive this
     * broadcast if they declare the broadcast receiver in their manifest. Apps
     * will still receive broadcasts if they register their
     * {@link android.content.BroadcastReceiver} with
     * {@link android.content.Context#registerReceiver Context.registerReceiver()}
     * and that context is still valid.
     * <p/>
     * If this is a connection that was the result of failing over from a
     * disconnected network, then the FAILOVER_CONNECTION boolean extra is
     * set to true.
     * <p/>
     * For a loss of connectivity, if the connectivity manager is attempting
     * to connect (or has already connected) to another network, the
     * NetworkInfo for the new network is also passed as an extra. This lets
     * any receivers of the broadcast know that they should not necessarily
     * tell the user that no data traffic will be possible. Instead, the
     * receiver should expect another broadcast soon, indicating either that
     * the failover attempt succeeded (and so there is still overall data
     * connectivity), or that the failover attempt failed, meaning that all
     * connectivity has been lost.
     * <p/>
     * For a disconnect event, the boolean extra EXTRA_NO_CONNECTIVITY
     * is set to {@code true} if there are no connected networks at all.
     *
     * @deprecated apps should use the more versatile {@link #requestNetwork},
     *             {@link #registerNetworkCallback} or {@link #registerDefaultNetworkCallback}
     *             functions instead for faster and more detailed updates about the network
     *             changes they care about.
     */
    @SdkConstant(SdkConstantType.BROADCAST_INTENT_ACTION)
    @Deprecated
    public static final String CONNECTIVITY_ACTION = "android.net.conn.CONNECTIVITY_CHANGE";

    /**
     * A temporary hack until SUPL system can get off the legacy APIS.
     * They do too many network requests and the long list of apps listening
     * and waking due to the CONNECTIVITY_ACTION broadcast makes it expensive.
     * Use this broadcast intent instead for SUPL requests.
     * @hide
     */
    public static final String CONNECTIVITY_ACTION_SUPL = "android.net.conn.CONNECTIVITY_CHANGE_SUPL";

    /**
     * The device has connected to a network that has presented a captive
     * portal, which is blocking Internet connectivity. The user was presented
     * with a notification that network sign in is required,
     * and the user invoked the notification's action indicating they
     * desire to sign in to the network. Apps handling this activity should
     * facilitate signing in to the network. This action includes a
     * {@link Network} typed extra called {@link #EXTRA_NETWORK} that represents
     * the network presenting the captive portal; all communication with the
     * captive portal must be done using this {@code Network} object.
     * <p/>
     * This activity includes a {@link CaptivePortal} extra named
     * {@link #EXTRA_CAPTIVE_PORTAL} that can be used to indicate different
     * outcomes of the captive portal sign in to the system:
     * <ul>
     * <li> When the app handling this action believes the user has signed in to
     * the network and the captive portal has been dismissed, the app should
     * call {@link CaptivePortal#reportCaptivePortalDismissed} so the system can
     * reevaluate the network. If reevaluation finds the network no longer
     * subject to a captive portal, the network may become the default active
     * data network.</li>
     * <li> When the app handling this action believes the user explicitly wants
     * to ignore the captive portal and the network, the app should call
     * {@link CaptivePortal#ignoreNetwork}. </li>
     * </ul>
     */
    @SdkConstant(SdkConstantType.ACTIVITY_INTENT_ACTION)
    public static final String ACTION_CAPTIVE_PORTAL_SIGN_IN = "android.net.conn.CAPTIVE_PORTAL";

    /**
     * The lookup key for a {@link NetworkInfo} object. Retrieve with
     * {@link android.content.Intent#getParcelableExtra(String)}.
     *
     * @deprecated Since {@link NetworkInfo} can vary based on UID, applications
     *             should always obtain network information through
     *             {@link #getActiveNetworkInfo()}.
     * @see #EXTRA_NETWORK_TYPE
     */
    @Deprecated
    public static final String EXTRA_NETWORK_INFO = "networkInfo";

    /**
     * Network type which triggered a {@link #CONNECTIVITY_ACTION} broadcast.
     *
     * @see android.content.Intent#getIntExtra(String, int)
     */
    public static final String EXTRA_NETWORK_TYPE = "networkType";

    /**
     * The lookup key for a boolean that indicates whether a connect event
     * is for a network to which the connectivity manager was failing over
     * following a disconnect on another network.
     * Retrieve it with {@link android.content.Intent#getBooleanExtra(String,boolean)}.
     */
    public static final String EXTRA_IS_FAILOVER = "isFailover";

    /**
     * The lookup key for a {@link NetworkInfo} object. This is supplied when
     * there is another network that it may be possible to connect to. Retrieve with
     * {@link android.content.Intent#getParcelableExtra(String)}.
     */
    public static final String EXTRA_OTHER_NETWORK_INFO = "otherNetwork";

    /**
     * The lookup key for a boolean that indicates whether there is a
     * complete lack of connectivity, i.e., no network is available.
     * Retrieve it with {@link android.content.Intent#getBooleanExtra(String,boolean)}.
     */
    public static final String EXTRA_NO_CONNECTIVITY = "noConnectivity";

    /**
     * The lookup key for a string that indicates why an attempt to connect
     * to a network failed. The string has no particular structure. It is
     * intended to be used in notifications presented to users. Retrieve
     * it with {@link android.content.Intent#getStringExtra(String)}.
     */
    public static final String EXTRA_REASON = "reason";

    /**
     * The lookup key for a string that provides optionally supplied
     * extra information about the network state. The information
     * may be passed up from the lower networking layers, and its
     * meaning may be specific to a particular network type. Retrieve
     * it with {@link android.content.Intent#getStringExtra(String)}.
     */
    public static final String EXTRA_EXTRA_INFO = "extraInfo";

    /**
     * The lookup key for an int that provides information about
     * our connection to the internet at large.  0 indicates no connection,
     * 100 indicates a great connection.  Retrieve it with
     * {@link android.content.Intent#getIntExtra(String, int)}.
     * {@hide}
     */
    public static final String EXTRA_INET_CONDITION = "inetCondition";

    /**
     * The lookup key for a {@link CaptivePortal} object included with the
     * {@link #ACTION_CAPTIVE_PORTAL_SIGN_IN} intent.  The {@code CaptivePortal}
     * object can be used to either indicate to the system that the captive
     * portal has been dismissed or that the user does not want to pursue
     * signing in to captive portal.  Retrieve it with
     * {@link android.content.Intent#getParcelableExtra(String)}.
     */
    public static final String EXTRA_CAPTIVE_PORTAL = "android.net.extra.CAPTIVE_PORTAL";

    /**
     * Key for passing a URL to the captive portal login activity.
     */
    public static final String EXTRA_CAPTIVE_PORTAL_URL = "android.net.extra.CAPTIVE_PORTAL_URL";

    /**
     * Key for passing a {@link android.net.captiveportal.CaptivePortalProbeSpec} to the captive
     * portal login activity.
     * {@hide}
     */
    public static final String EXTRA_CAPTIVE_PORTAL_PROBE_SPEC = "android.net.extra.CAPTIVE_PORTAL_PROBE_SPEC";

    /**
     * Key for passing a user agent string to the captive portal login activity.
     * {@hide}
     */
    public static final String EXTRA_CAPTIVE_PORTAL_USER_AGENT = "android.net.extra.CAPTIVE_PORTAL_USER_AGENT";

    /**
     * Broadcast action to indicate the change of data activity status
     * (idle or active) on a network in a recent period.
     * The network becomes active when data transmission is started, or
     * idle if there is no data transmission for a period of time.
     * {@hide}
     */
    @SdkConstant(SdkConstantType.BROADCAST_INTENT_ACTION)
    public static final String ACTION_DATA_ACTIVITY_CHANGE = "android.net.conn.DATA_ACTIVITY_CHANGE";

    /**
     * The lookup key for an enum that indicates the network device type on which this data activity
     * change happens.
     * {@hide}
     */
    public static final String EXTRA_DEVICE_TYPE = "deviceType";

    /**
     * The lookup key for a boolean that indicates the device is active or not. {@code true} means
     * it is actively sending or receiving data and {@code false} means it is idle.
     * {@hide}
     */
    public static final String EXTRA_IS_ACTIVE = "isActive";

    /**
     * The lookup key for a long that contains the timestamp (nanos) of the radio state change.
     * {@hide}
     */
    public static final String EXTRA_REALTIME_NS = "tsNanos";

    /**
     * Broadcast Action: The setting for background data usage has changed
     * values. Use {@link #getBackgroundDataSetting()} to get the current value.
     * <p>
     * If an application uses the network in the background, it should listen
     * for this broadcast and stop using the background data if the value is
     * {@code false}.
     * <p>
     *
     * @deprecated As of {@link VERSION_CODES#ICE_CREAM_SANDWICH}, availability
     *             of background data depends on several combined factors, and
     *             this broadcast is no longer sent. Instead, when background
     *             data is unavailable, {@link #getActiveNetworkInfo()} will now
     *             appear disconnected. During first boot after a platform
     *             upgrade, this broadcast will be sent once if
     *             {@link #getBackgroundDataSetting()} was {@code false} before
     *             the upgrade.
     */
    @SdkConstant(SdkConstantType.BROADCAST_INTENT_ACTION)
    @Deprecated
    public static final String ACTION_BACKGROUND_DATA_SETTING_CHANGED = "android.net.conn.BACKGROUND_DATA_SETTING_CHANGED";

    /**
     * Broadcast Action: The network connection may not be good
     * uses {@code ConnectivityManager.EXTRA_INET_CONDITION} and
     * {@code ConnectivityManager.EXTRA_NETWORK_INFO} to specify
     * the network and it's condition.
     * @hide
     */
    @SdkConstant(SdkConstantType.BROADCAST_INTENT_ACTION)
    public static final String INET_CONDITION_ACTION = "android.net.conn.INET_CONDITION_ACTION";

    /**
     * Broadcast Action: A tetherable connection has come or gone.
     * Uses {@code ConnectivityManager.EXTRA_AVAILABLE_TETHER},
     * {@code ConnectivityManager.EXTRA_ACTIVE_LOCAL_ONLY},
     * {@code ConnectivityManager.EXTRA_ACTIVE_TETHER}, and
     * {@code ConnectivityManager.EXTRA_ERRORED_TETHER} to indicate
     * the current state of tethering.  Each include a list of
     * interface names in that state (may be empty).
     * @hide
     */
    @SdkConstant(SdkConstantType.BROADCAST_INTENT_ACTION)
    public static final String ACTION_TETHER_STATE_CHANGED = "android.net.conn.TETHER_STATE_CHANGED";

    /**
     * @hide
     * gives a String[] listing all the interfaces configured for
     * tethering and currently available for tethering.
     */
    public static final String EXTRA_AVAILABLE_TETHER = "availableArray";

    /**
     * @hide
     * gives a String[] listing all the interfaces currently in local-only
     * mode (ie, has DHCPv4+IPv6-ULA support and no packet forwarding)
     */
    public static final String EXTRA_ACTIVE_LOCAL_ONLY = "localOnlyArray";

    /**
     * @hide
     * gives a String[] listing all the interfaces currently tethered
     * (ie, has DHCPv4 support and packets potentially forwarded/NATed)
     */
    public static final String EXTRA_ACTIVE_TETHER = "tetherArray";

    /**
     * @hide
     * gives a String[] listing all the interfaces we tried to tether and
     * failed.  Use {@link #getLastTetherError} to find the error code
     * for any interfaces listed here.
     */
    public static final String EXTRA_ERRORED_TETHER = "erroredArray";

    /**
     * Broadcast Action: The captive portal tracker has finished its test.
     * Sent only while running Setup Wizard, in lieu of showing a user
     * notification.
     * @hide
     */
    @SdkConstant(SdkConstantType.BROADCAST_INTENT_ACTION)
    public static final String ACTION_CAPTIVE_PORTAL_TEST_COMPLETED = "android.net.conn.CAPTIVE_PORTAL_TEST_COMPLETED";

    /**
     * The lookup key for a boolean that indicates whether a captive portal was detected.
     * Retrieve it with {@link android.content.Intent#getBooleanExtra(String,boolean)}.
     * @hide
     */
    public static final String EXTRA_IS_CAPTIVE_PORTAL = "captivePortal";

    /**
     * Action used to display a dialog that asks the user whether to connect to a network that is
     * not validated. This intent is used to start the dialog in settings via startActivity.
     *
     * @hide
     */
    public static final String ACTION_PROMPT_UNVALIDATED = "android.net.conn.PROMPT_UNVALIDATED";

    /**
     * Action used to display a dialog that asks the user whether to avoid a network that is no
     * longer validated. This intent is used to start the dialog in settings via startActivity.
     *
     * @hide
     */
    public static final String ACTION_PROMPT_LOST_VALIDATION = "android.net.conn.PROMPT_LOST_VALIDATION";

    /**
     * Invalid tethering type.
     * @see #startTethering(int, boolean, OnStartTetheringCallback)
     * @hide
     */
    public static final int TETHERING_INVALID = -1;

    /**
     * Wifi tethering type.
     * @see #startTethering(int, boolean, OnStartTetheringCallback)
     * @hide
     */
    @SystemApi
    public static final int TETHERING_WIFI = 0;

    /**
     * USB tethering type.
     * @see #startTethering(int, boolean, OnStartTetheringCallback)
     * @hide
     */
    @SystemApi
    public static final int TETHERING_USB = 1;

    /**
     * Bluetooth tethering type.
     * @see #startTethering(int, boolean, OnStartTetheringCallback)
     * @hide
     */
    @SystemApi
    public static final int TETHERING_BLUETOOTH = 2;

    /**
     * Extra used for communicating with the TetherService. Includes the type of tethering to
     * enable if any.
     * @hide
     */
    public static final String EXTRA_ADD_TETHER_TYPE = "extraAddTetherType";

    /**
     * Extra used for communicating with the TetherService. Includes the type of tethering for
     * which to cancel provisioning.
     * @hide
     */
    public static final String EXTRA_REM_TETHER_TYPE = "extraRemTetherType";

    /**
     * Extra used for communicating with the TetherService. True to schedule a recheck of tether
     * provisioning.
     * @hide
     */
    public static final String EXTRA_SET_ALARM = "extraSetAlarm";

    /**
     * Tells the TetherService to run a provision check now.
     * @hide
     */
    public static final String EXTRA_RUN_PROVISION = "extraRunProvision";

    /**
     * Extra used for communicating with the TetherService. Contains the {@link ResultReceiver}
     * which will receive provisioning results. Can be left empty.
     * @hide
     */
    public static final String EXTRA_PROVISION_CALLBACK = "extraProvisionCallback";

    /**
     * The absence of a connection type.
     * @hide
     */
    public static final int TYPE_NONE = -1;

    /**
     * A Mobile data connection. Devices may support more than one.
     *
     * @deprecated Applications should instead use {@link NetworkCapabilities#hasTransport} or
     *         {@link #requestNetwork(NetworkRequest, NetworkCallback)} to request an
     *         appropriate network. {@see NetworkCapabilities} for supported transports.
     */
    @Deprecated
    public static final int TYPE_MOBILE = 0;

    /**
     * A WIFI data connection. Devices may support more than one.
     *
     * @deprecated Applications should instead use {@link NetworkCapabilities#hasTransport} or
     *         {@link #requestNetwork(NetworkRequest, NetworkCallback)} to request an
     *         appropriate network. {@see NetworkCapabilities} for supported transports.
     */
    @Deprecated
    public static final int TYPE_WIFI = 1;

    /**
     * An MMS-specific Mobile data connection.  This network type may use the
     * same network interface as {@link #TYPE_MOBILE} or it may use a different
     * one.  This is used by applications needing to talk to the carrier's
     * Multimedia Messaging Service servers.
     *
     * @deprecated Applications should instead use {@link NetworkCapabilities#hasCapability} or
     *         {@link #requestNetwork(NetworkRequest, NetworkCallback)} to request a network that
     *         provides the {@link NetworkCapabilities#NET_CAPABILITY_MMS} capability.
     */
    @Deprecated
    public static final int TYPE_MOBILE_MMS = 2;

    /**
     * A SUPL-specific Mobile data connection.  This network type may use the
     * same network interface as {@link #TYPE_MOBILE} or it may use a different
     * one.  This is used by applications needing to talk to the carrier's
     * Secure User Plane Location servers for help locating the device.
     *
     * @deprecated Applications should instead use {@link NetworkCapabilities#hasCapability} or
     *         {@link #requestNetwork(NetworkRequest, NetworkCallback)} to request a network that
     *         provides the {@link NetworkCapabilities#NET_CAPABILITY_SUPL} capability.
     */
    @Deprecated
    public static final int TYPE_MOBILE_SUPL = 3;

    /**
     * A DUN-specific Mobile data connection.  This network type may use the
     * same network interface as {@link #TYPE_MOBILE} or it may use a different
     * one.  This is sometimes by the system when setting up an upstream connection
     * for tethering so that the carrier is aware of DUN traffic.
     *
     * @deprecated Applications should instead use {@link NetworkCapabilities#hasCapability} or
     *         {@link #requestNetwork(NetworkRequest, NetworkCallback)} to request a network that
     *         provides the {@link NetworkCapabilities#NET_CAPABILITY_DUN} capability.
     */
    @Deprecated
    public static final int TYPE_MOBILE_DUN = 4;

    /**
     * A High Priority Mobile data connection.  This network type uses the
     * same network interface as {@link #TYPE_MOBILE} but the routing setup
     * is different.
     *
     * @deprecated Applications should instead use {@link NetworkCapabilities#hasTransport} or
     *         {@link #requestNetwork(NetworkRequest, NetworkCallback)} to request an
     *         appropriate network. {@see NetworkCapabilities} for supported transports.
     */
    @Deprecated
    public static final int TYPE_MOBILE_HIPRI = 5;

    /**
     * A WiMAX data connection.
     *
     * @deprecated Applications should instead use {@link NetworkCapabilities#hasTransport} or
     *         {@link #requestNetwork(NetworkRequest, NetworkCallback)} to request an
     *         appropriate network. {@see NetworkCapabilities} for supported transports.
     */
    @Deprecated
    public static final int TYPE_WIMAX = 6;

    /**
     * A Bluetooth data connection.
     *
     * @deprecated Applications should instead use {@link NetworkCapabilities#hasTransport} or
     *         {@link #requestNetwork(NetworkRequest, NetworkCallback)} to request an
     *         appropriate network. {@see NetworkCapabilities} for supported transports.
     */
    @Deprecated
    public static final int TYPE_BLUETOOTH = 7;

    /**
     * Dummy data connection.  This should not be used on shipping devices.
     * @deprecated This is not used any more.
     */
    @Deprecated
    public static final int TYPE_DUMMY = 8;

    /**
     * An Ethernet data connection.
     *
     * @deprecated Applications should instead use {@link NetworkCapabilities#hasTransport} or
     *         {@link #requestNetwork(NetworkRequest, NetworkCallback)} to request an
     *         appropriate network. {@see NetworkCapabilities} for supported transports.
     */
    @Deprecated
    public static final int TYPE_ETHERNET = 9;

    /**
     * Over the air Administration.
     * @deprecated Use {@link NetworkCapabilities} instead.
     * {@hide}
     */
    @Deprecated
    public static final int TYPE_MOBILE_FOTA = 10;

    /**
     * IP Multimedia Subsystem.
     * @deprecated Use {@link NetworkCapabilities#NET_CAPABILITY_IMS} instead.
     * {@hide}
     */
    @Deprecated
    public static final int TYPE_MOBILE_IMS = 11;

    /**
     * Carrier Branded Services.
     * @deprecated Use {@link NetworkCapabilities#NET_CAPABILITY_CBS} instead.
     * {@hide}
     */
    @Deprecated
    public static final int TYPE_MOBILE_CBS = 12;

    /**
     * A Wi-Fi p2p connection. Only requesting processes will have access to
     * the peers connected.
     * @deprecated Use {@link NetworkCapabilities#NET_CAPABILITY_WIFI_P2P} instead.
     * {@hide}
     */
    @Deprecated
    public static final int TYPE_WIFI_P2P = 13;

    /**
     * The network to use for initially attaching to the network
     * @deprecated Use {@link NetworkCapabilities#NET_CAPABILITY_IA} instead.
     * {@hide}
     */
    @Deprecated
    public static final int TYPE_MOBILE_IA = 14;

    /**
     * Emergency PDN connection for emergency services.  This
     * may include IMS and MMS in emergency situations.
     * @deprecated Use {@link NetworkCapabilities#NET_CAPABILITY_EIMS} instead.
     * {@hide}
     */
    @Deprecated
    public static final int TYPE_MOBILE_EMERGENCY = 15;

    /**
     * The network that uses proxy to achieve connectivity.
     * @deprecated Use {@link NetworkCapabilities} instead.
     * {@hide}
     */
    @Deprecated
    public static final int TYPE_PROXY = 16;

    /**
     * A virtual network using one or more native bearers.
     * It may or may not be providing security services.
     * @deprecated Applications should use {@link NetworkCapabilities#TRANSPORT_VPN} instead.
     */
    @Deprecated
    public static final int TYPE_VPN = 17;

    /** {@hide} */
    public static final int MAX_RADIO_TYPE = 0;

    /** {@hide} */
    public static final int MAX_NETWORK_TYPE = 0;

    private static final int MIN_NETWORK_TYPE = 0;

    /**
     * If you want to set the default network preference,you can directly
     * change the networkAttributes array in framework's config.xml.
     *
     * @deprecated Since we support so many more networks now, the single
     *             network default network preference can't really express
     *             the hierarchy.  Instead, the default is defined by the
     *             networkAttributes in config.xml.  You can determine
     *             the current value by calling {@link #getNetworkPreference()}
     *             from an App.
     */
    @Deprecated
    public static final int DEFAULT_NETWORK_PREFERENCE = 0;

    /**
     * @hide
     */
    public static final int REQUEST_ID_UNSET = 0;

    /**
     * Static unique request used as a tombstone for NetworkCallbacks that have been unregistered.
     * This allows to distinguish when unregistering NetworkCallbacks those that were never
     * registered from those that were already unregistered.
     * @hide
     */
    private static final NetworkRequest ALREADY_UNREGISTERED = null;

    /**
     * A NetID indicating no Network is selected.
     * Keep in sync with bionic/libc/dns/include/resolv_netid.h
     * @hide
     */
    public static final int NETID_UNSET = 0;

    /**
     * Private DNS Mode values.
     *
     * The "private_dns_mode" global setting stores a String value which is
     * expected to be one of the following.
     */
    /**
     * @hide
     */
    public static final String PRIVATE_DNS_MODE_OFF = "off";

    /**
     * @hide
     */
    public static final String PRIVATE_DNS_MODE_OPPORTUNISTIC = "opportunistic";

    /**
     * @hide
     */
    public static final String PRIVATE_DNS_MODE_PROVIDER_HOSTNAME = "hostname";

    /**
     * The default Private DNS mode.
     *
     * This may change from release to release or may become dependent upon
     * the capabilities of the underlying platform.
     *
     * @hide
     */
    public static final String PRIVATE_DNS_DEFAULT_MODE = null;

    /**
     * A kludge to facilitate static access where a Context pointer isn't available, like in the
     * case of the static set/getProcessDefaultNetwork methods and from the Network class.
     * TODO: Remove this after deprecating the static methods in favor of non-static methods or
     * methods that take a Context argument.
     */
    private static ConnectivityManager sInstance;

    /**
     * Tests if a given integer represents a valid network type.
     * @param networkType the type to be tested
     * @return a boolean.  {@code true} if the type is valid, else {@code false}
     * @deprecated All APIs accepting a network type are deprecated. There should be no need to
     *             validate a network type.
     */
    @Deprecated
    public static boolean isNetworkTypeValid(int networkType) {
        return true;
    }

    /**
     * Returns a non-localized string representing a given network type.
     * ONLY used for debugging output.
     * @param type the type needing naming
     * @return a String for the given type, or a string version of the type ("87")
     * if no name is known.
     * @deprecated Types are deprecated. Use {@link NetworkCapabilities} instead.
     * {@hide}
     */
    @Deprecated
    public static String getNetworkTypeName(int type) {
        throw new RuntimeException("Stub!");
    }

    /**
     * Checks if a given type uses the cellular data connection.
     * This should be replaced in the future by a network property.
     * @param networkType the type to check
     * @return a boolean - {@code true} if uses cellular network, else {@code false}
     * @deprecated Types are deprecated. Use {@link NetworkCapabilities} instead.
     * {@hide}
     */
    @Deprecated
    public static boolean isNetworkTypeMobile(int networkType) {
        return false;
    }

    /**
     * Checks if the given network type is backed by a Wi-Fi radio.
     *
     * @deprecated Types are deprecated. Use {@link NetworkCapabilities} instead.
     * @hide
     */
    @Deprecated
    public static boolean isNetworkTypeWifi(int networkType) {
        return true;
    }

    /**
     * Specifies the preferred network type.  When the device has more
     * than one type available the preferred network type will be used.
     *
     * @param preference the network type to prefer over all others.  It is
     *         unspecified what happens to the old preferred network in the
     *         overall ordering.
     * @deprecated Functionality has been removed as it no longer makes sense,
     *             with many more than two networks - we'd need an array to express
     *             preference.  Instead we use dynamic network properties of
     *             the networks to describe their precedence.
     */
    @Deprecated
    public void setNetworkPreference(int preference) {
    }

    /**
     * Retrieves the current preferred network type.
     *
     * @return an integer representing the preferred network type
     *
     * @deprecated Functionality has been removed as it no longer makes sense,
     *             with many more than two networks - we'd need an array to express
     *             preference.  Instead we use dynamic network properties of
     *             the networks to describe their precedence.
     */
    @Deprecated
    @RequiresPermission(android.Manifest.permission.ACCESS_NETWORK_STATE)
    public int getNetworkPreference() {
        throw new RuntimeException("Stub!");
    }

    /**
     * Returns details about the currently active default data network. When
     * connected, this network is the default route for outgoing connections.
     * You should always check {@link NetworkInfo#isConnected()} before initiating
     * network traffic. This may return {@code null} when there is no default
     * network.
     * Note that if the default network is a VPN, this method will return the
     * NetworkInfo for one of its underlying networks instead, or null if the
     * VPN agent did not specify any. Apps interested in learning about VPNs
     * should use {@link #getNetworkInfo(android.net.Network)} instead.
     *
     * @return a {@link NetworkInfo} object for the current default network
     *        or {@code null} if no default network is currently active
     */
    @RequiresPermission(android.Manifest.permission.ACCESS_NETWORK_STATE)
    public NetworkInfo getActiveNetworkInfo() {
        throw new RuntimeException("Stub!");
    }

    /**
     * Returns a {@link Network} object corresponding to the currently active
     * default data network.  In the event that the current active default data
     * network disconnects, the returned {@code Network} object will no longer
     * be usable.  This will return {@code null} when there is no default
     * network.
     *
     * @return a {@link Network} object for the current default network or
     *        {@code null} if no default network is currently active
     */
    @RequiresPermission(android.Manifest.permission.ACCESS_NETWORK_STATE)
    public Network getActiveNetwork() {
        throw new RuntimeException("Stub!");
    }

    /**
     * Returns a {@link Network} object corresponding to the currently active
     * default data network for a specific UID.  In the event that the default data
     * network disconnects, the returned {@code Network} object will no longer
     * be usable.  This will return {@code null} when there is no default
     * network for the UID.
     *
     * @return a {@link Network} object for the current default network for the
     *         given UID or {@code null} if no default network is currently active
     *
     * @hide
     */
    public Network getActiveNetworkForUid(int uid) {
        throw new RuntimeException("Stub!");
    }

    /** {@hide} */
    public Network getActiveNetworkForUid(int uid, boolean ignoreBlocked) {
        throw new RuntimeException("Stub!");
    }

    /**
     * Checks if a VPN app supports always-on mode.
     *
     * In order to support the always-on feature, an app has to
     * <ul>
     *     <li>target {@link VERSION_CODES#N API 24} or above, and
     *     <li>not opt out through the {@link VpnService#SERVICE_META_DATA_SUPPORTS_ALWAYS_ON}
     *         meta-data field.
     * </ul>
     *
     * @param userId The identifier of the user for whom the VPN app is installed.
     * @param vpnPackage The canonical package name of the VPN app.
     * @return {@code true} if and only if the VPN app exists and supports always-on mode.
     * @hide
     */
    public boolean isAlwaysOnVpnPackageSupportedForUser(int userId, @Nullable String vpnPackage) {
        throw new RuntimeException("Stub!");
    }

    /**
     * Configures an always-on VPN connection through a specific application.
     * This connection is automatically granted and persisted after a reboot.
     *
     * <p>The designated package should declare a {@link VpnService} in its
     *    manifest guarded by {@link android.Manifest.permission.BIND_VPN_SERVICE},
     *    otherwise the call will fail.
     *
     * @param userId The identifier of the user to set an always-on VPN for.
     * @param vpnPackage The package name for an installed VPN app on the device, or {@code null}
     *                   to remove an existing always-on VPN configuration.
     * @param lockdownEnabled {@code true} to disallow networking when the VPN is not connected or
     *        {@code false} otherwise.
     * @return {@code true} if the package is set as always-on VPN controller;
     *         {@code false} otherwise.
     * @hide
     */
    public boolean setAlwaysOnVpnPackageForUser(int userId, @Nullable String vpnPackage, boolean lockdownEnabled) {
        throw new RuntimeException("Stub!");
    }

    /**
     * Returns the package name of the currently set always-on VPN application.
     * If there is no always-on VPN set, or the VPN is provided by the system instead
     * of by an app, {@code null} will be returned.
     *
     * @return Package name of VPN controller responsible for always-on VPN,
     *         or {@code null} if none is set.
     * @hide
     */
    public String getAlwaysOnVpnPackageForUser(int userId) {
        throw new RuntimeException("Stub!");
    }

    /**
     * Returns details about the currently active default data network
     * for a given uid.  This is for internal use only to avoid spying
     * other apps.
     *
     * @return a {@link NetworkInfo} object for the current default network
     *        for the given uid or {@code null} if no default network is
     *        available for the specified uid.
     *
     * {@hide}
     */
    public NetworkInfo getActiveNetworkInfoForUid(int uid) {
        throw new RuntimeException("Stub!");
    }

    /** {@hide} */
    public NetworkInfo getActiveNetworkInfoForUid(int uid, boolean ignoreBlocked) {
        throw new RuntimeException("Stub!");
    }

    /**
     * Returns connection status information about a particular
     * network type.
     *
     * @param networkType integer specifying which networkType in
     *        which you're interested.
     * @return a {@link NetworkInfo} object for the requested
     *        network type or {@code null} if the type is not
     *        supported by the device. If {@code networkType} is
     *        TYPE_VPN and a VPN is active for the calling app,
     *        then this method will try to return one of the
     *        underlying networks for the VPN or null if the
     *        VPN agent didn't specify any.
     *
     * @deprecated This method does not support multiple connected networks
     *             of the same type. Use {@link #getAllNetworks} and
     *             {@link #getNetworkInfo(android.net.Network)} instead.
     */
    @Deprecated
    @RequiresPermission(android.Manifest.permission.ACCESS_NETWORK_STATE)
    public NetworkInfo getNetworkInfo(int networkType) {
        throw new RuntimeException("Stub!");
    }

    /**
     * Returns connection status information about a particular
     * Network.
     *
     * @param network {@link Network} specifying which network
     *        in which you're interested.
     * @return a {@link NetworkInfo} object for the requested
     *        network or {@code null} if the {@code Network}
     *        is not valid.
     */
    @RequiresPermission(android.Manifest.permission.ACCESS_NETWORK_STATE)
    public NetworkInfo getNetworkInfo(Network network) {
        throw new RuntimeException("Stub!");
    }

    /** {@hide} */
    public NetworkInfo getNetworkInfoForUid(Network network, int uid, boolean ignoreBlocked) {
        throw new RuntimeException("Stub!");
    }

    /**
     * Returns connection status information about all network
     * types supported by the device.
     *
     * @return an array of {@link NetworkInfo} objects.  Check each
     * {@link NetworkInfo#getType} for which type each applies.
     *
     * @deprecated This method does not support multiple connected networks
     *             of the same type. Use {@link #getAllNetworks} and
     *             {@link #getNetworkInfo(android.net.Network)} instead.
     */
    @Deprecated
    @RequiresPermission(android.Manifest.permission.ACCESS_NETWORK_STATE)
    public NetworkInfo[] getAllNetworkInfo() {
        throw new RuntimeException("Stub!");
    }

    /**
     * Returns the {@link Network} object currently serving a given type, or
     * null if the given type is not connected.
     *
     * @hide
     * @deprecated This method does not support multiple connected networks
     *             of the same type. Use {@link #getAllNetworks} and
     *             {@link #getNetworkInfo(android.net.Network)} instead.
     */
    @Deprecated
    @RequiresPermission(android.Manifest.permission.ACCESS_NETWORK_STATE)
    public Network getNetworkForType(int networkType) {
        throw new RuntimeException("Stub!");
    }

    /**
     * Returns an array of all {@link Network} currently tracked by the
     * framework.
     *
     * @return an array of {@link Network} objects.
     */
    @RequiresPermission(android.Manifest.permission.ACCESS_NETWORK_STATE)
    public Network[] getAllNetworks() {
        throw new RuntimeException("Stub!");
    }

    /**
     * Returns an array of {@link android.net.NetworkCapabilities} objects, representing
     * the Networks that applications run by the given user will use by default.
     * @hide
     */
    public NetworkCapabilities[] getDefaultNetworkCapabilitiesForUser(int userId) {
        throw new RuntimeException("Stub!");
    }

    /**
     * Returns the IP information for the current default network.
     *
     * @return a {@link LinkProperties} object describing the IP info
     *        for the current default network, or {@code null} if there
     *        is no current default network.
     *
     * {@hide}
     */
    @RequiresPermission(android.Manifest.permission.ACCESS_NETWORK_STATE)
    public LinkProperties getActiveLinkProperties() {
        throw new RuntimeException("Stub!");
    }

    /**
     * Returns the IP information for a given network type.
     *
     * @param networkType the network type of interest.
     * @return a {@link LinkProperties} object describing the IP info
     *        for the given networkType, or {@code null} if there is
     *        no current default network.
     *
     * {@hide}
     * @deprecated This method does not support multiple connected networks
     *             of the same type. Use {@link #getAllNetworks},
     *             {@link #getNetworkInfo(android.net.Network)}, and
     *             {@link #getLinkProperties(android.net.Network)} instead.
     */
    @Deprecated
    @RequiresPermission(android.Manifest.permission.ACCESS_NETWORK_STATE)
    public LinkProperties getLinkProperties(int networkType) {
        throw new RuntimeException("Stub!");
    }

    /**
     * Get the {@link LinkProperties} for the given {@link Network}.  This
     * will return {@code null} if the network is unknown.
     *
     * @param network The {@link Network} object identifying the network in question.
     * @return The {@link LinkProperties} for the network, or {@code null}.
     */
    @RequiresPermission(android.Manifest.permission.ACCESS_NETWORK_STATE)
    public LinkProperties getLinkProperties(Network network) {
        throw new RuntimeException("Stub!");
    }

    /**
     * Get the {@link android.net.NetworkCapabilities} for the given {@link Network}.  This
     * will return {@code null} if the network is unknown.
     *
     * @param network The {@link Network} object identifying the network in question.
     * @return The {@link android.net.NetworkCapabilities} for the network, or {@code null}.
     */
    @RequiresPermission(android.Manifest.permission.ACCESS_NETWORK_STATE)
    public NetworkCapabilities getNetworkCapabilities(Network network) {
        throw new RuntimeException("Stub!");
    }

    /**
     * Gets the URL that should be used for resolving whether a captive portal is present.
     * 1. This URL should respond with a 204 response to a GET request to indicate no captive
     *    portal is present.
     * 2. This URL must be HTTP as redirect responses are used to find captive portal
     *    sign-in pages. Captive portals cannot respond to HTTPS requests with redirects.
     *
     * @hide
     */
    @SystemApi
    public String getCaptivePortalServerUrl() {
        throw new RuntimeException("Stub!");
    }

    /**
     * Tells the underlying networking system that the caller wants to
     * begin using the named feature. The interpretation of {@code feature}
     * is completely up to each networking implementation.
     *
     * <p>This method requires the caller to hold either the
     * {@link android.Manifest.permission#CHANGE_NETWORK_STATE} permission
     * or the ability to modify system settings as determined by
     * {@link android.provider.Settings.System#canWrite}.</p>
     *
     * @param networkType specifies which network the request pertains to
     * @param feature the name of the feature to be used
     * @return an integer value representing the outcome of the request.
     * The interpretation of this value is specific to each networking
     * implementation+feature combination, except that the value {@code -1}
     * always indicates failure.
     *
     * @deprecated Deprecated in favor of the cleaner
     *             {@link #requestNetwork(NetworkRequest, NetworkCallback)} API.
     *             In {@link VERSION_CODES#M}, and above, this method is unsupported and will
     *             throw {@code UnsupportedOperationException} if called.
     * @removed
     */
    @Deprecated
    public int startUsingNetworkFeature(int networkType, String feature) {
        throw new RuntimeException("Stub!");
    }

    /**
     * Tells the underlying networking system that the caller is finished
     * using the named feature. The interpretation of {@code feature}
     * is completely up to each networking implementation.
     *
     * <p>This method requires the caller to hold either the
     * {@link android.Manifest.permission#CHANGE_NETWORK_STATE} permission
     * or the ability to modify system settings as determined by
     * {@link android.provider.Settings.System#canWrite}.</p>
     *
     * @param networkType specifies which network the request pertains to
     * @param feature the name of the feature that is no longer needed
     * @return an integer value representing the outcome of the request.
     * The interpretation of this value is specific to each networking
     * implementation+feature combination, except that the value {@code -1}
     * always indicates failure.
     *
     * @deprecated Deprecated in favor of the cleaner
     *             {@link #unregisterNetworkCallback(NetworkCallback)} API.
     *             In {@link VERSION_CODES#M}, and above, this method is unsupported and will
     *             throw {@code UnsupportedOperationException} if called.
     * @removed
     */
    @Deprecated
    public int stopUsingNetworkFeature(int networkType, String feature) {
        throw new RuntimeException("Stub!");
    }

    private NetworkCapabilities networkCapabilitiesForFeature(int networkType, String feature) {
        throw new RuntimeException("Stub!");
    }

    /**
     * Guess what the network request was trying to say so that the resulting
     * network is accessible via the legacy (deprecated) API such as
     * requestRouteToHost.
     *
     * This means we should try to be fairly precise about transport and
     * capability but ignore things such as networkSpecifier.
     * If the request has more than one transport or capability it doesn't
     * match the old legacy requests (they selected only single transport/capability)
     * so this function cannot map the request to a single legacy type and
     * the resulting network will not be available to the legacy APIs.
     *
     * This code is only called from the requestNetwork API (L and above).
     *
     * Setting a legacy type causes CONNECTIVITY_ACTION broadcasts, which are expensive
     * because they wake up lots of apps - see http://b/23350688 . So we currently only
     * do this for SUPL requests, which are the only ones that we know need it. If
     * omitting these broadcasts causes unacceptable app breakage, then for backwards
     * compatibility we can send them:
     *
     * if (targetSdkVersion < Build.VERSION_CODES.M) &&        // legacy API unsupported >= M
     *     targetSdkVersion >= Build.VERSION_CODES.LOLLIPOP))  // requestNetwork not present < L
     *
     * TODO - This should be removed when the legacy APIs are removed.
     */
    private int inferLegacyTypeForNetworkCapabilities(NetworkCapabilities netCap) {
        throw new RuntimeException("Stub!");
    }

    private int legacyTypeForNetworkCapabilities(NetworkCapabilities netCap) {
        throw new RuntimeException("Stub!");
    }

    private static class LegacyRequest {

        NetworkCapabilities networkCapabilities;

        NetworkRequest networkRequest;

        int expireSequenceNumber;

        Network currentNetwork;

        int delay = -1;

        private void clearDnsBinding() {
            throw new RuntimeException("Stub!");
        }

        NetworkCallback networkCallback = null;
    }

    private static final HashMap<NetworkCapabilities, LegacyRequest> sLegacyRequests = null;

    private NetworkRequest findRequestForFeature(NetworkCapabilities netCap) {
        throw new RuntimeException("Stub!");
    }

    private void renewRequestLocked(LegacyRequest l) {
        throw new RuntimeException("Stub!");
    }

    private void expireRequest(NetworkCapabilities netCap, int sequenceNum) {
        throw new RuntimeException("Stub!");
    }

    private NetworkRequest requestNetworkForFeatureLocked(NetworkCapabilities netCap) {
        throw new RuntimeException("Stub!");
    }

    private void sendExpireMsgForFeature(NetworkCapabilities netCap, int seqNum, int delay) {
        throw new RuntimeException("Stub!");
    }

    private boolean removeRequestForFeature(NetworkCapabilities netCap) {
        throw new RuntimeException("Stub!");
    }

    private static final SparseIntArray sLegacyTypeToTransport = null;

    static {
    }

    private static final SparseIntArray sLegacyTypeToCapability = null;

    static {
    }

    /**
     * Given a legacy type (TYPE_WIFI, ...) returns a NetworkCapabilities
     * instance suitable for registering a request or callback.  Throws an
     * IllegalArgumentException if no mapping from the legacy type to
     * NetworkCapabilities is known.
     *
     * @deprecated Types are deprecated. Use {@link NetworkCallback} or {@link NetworkRequest}
     *     to find the network instead.
     * @hide
     */
    public static NetworkCapabilities networkCapabilitiesForType(int type) {
        throw new RuntimeException("Stub!");
    }

    /** @hide */
    public static class PacketKeepaliveCallback {

        /** The requested keepalive was successfully started. */
        public void onStarted() {
            throw new RuntimeException("Stub!");
        }

        /** The keepalive was successfully stopped. */
        public void onStopped() {
            throw new RuntimeException("Stub!");
        }

        /** An error occurred. */
        public void onError(int error) {
            throw new RuntimeException("Stub!");
        }
    }

    /**
     * Allows applications to request that the system periodically send specific packets on their
     * behalf, using hardware offload to save battery power.
     *
     * To request that the system send keepalives, call one of the methods that return a
     * {@link ConnectivityManager.PacketKeepalive} object, such as {@link #startNattKeepalive},
     * passing in a non-null callback. If the callback is successfully started, the callback's
     * {@code onStarted} method will be called. If an error occurs, {@code onError} will be called,
     * specifying one of the {@code ERROR_*} constants in this class.
     *
     * To stop an existing keepalive, call {@link PacketKeepalive#stop}. The system will call
     * {@link PacketKeepaliveCallback#onStopped} if the operation was successful or
     * {@link PacketKeepaliveCallback#onError} if an error occurred.
     *
     * @hide
     */
    public class PacketKeepalive {

        private static final String TAG = "PacketKeepalive";

        /** @hide */
        public static final int SUCCESS = 0;

        /** @hide */
        public static final int NO_KEEPALIVE = -1;

        /** @hide */
        public static final int BINDER_DIED = -10;

        /** The specified {@code Network} is not connected. */
        public static final int ERROR_INVALID_NETWORK = -20;

        /** The specified IP addresses are invalid. For example, the specified source IP address is
         * not configured on the specified {@code Network}. */
        public static final int ERROR_INVALID_IP_ADDRESS = -21;

        /** The requested port is invalid. */
        public static final int ERROR_INVALID_PORT = -22;

        /** The packet length is invalid (e.g., too long). */
        public static final int ERROR_INVALID_LENGTH = -23;

        /** The packet transmission interval is invalid (e.g., too short). */
        public static final int ERROR_INVALID_INTERVAL = -24;

        /** The hardware does not support this request. */
        public static final int ERROR_HARDWARE_UNSUPPORTED = -30;

        /** The hardware returned an error. */
        public static final int ERROR_HARDWARE_ERROR = -31;

        /** The NAT-T destination port for IPsec */
        public static final int NATT_PORT = 4500;

        /** The minimum interval in seconds between keepalive packet transmissions */
        public static final int MIN_INTERVAL = 10;

        private final Network mNetwork;

        private final PacketKeepaliveCallback mCallback;

        private final Looper mLooper;

        private final Messenger mMessenger;

        private volatile Integer mSlot;

        void stopLooper() {
            throw new RuntimeException("Stub!");
        }

        public void stop() {
            throw new RuntimeException("Stub!");
        }

        private PacketKeepalive(Network network, PacketKeepaliveCallback callback) {
            throw new RuntimeException("Stub!");
        }
    }

    /**
     * Starts an IPsec NAT-T keepalive packet with the specified parameters.
     *
     * @hide
     */
    public PacketKeepalive startNattKeepalive(Network network, int intervalSeconds, PacketKeepaliveCallback callback, InetAddress srcAddr, int srcPort, InetAddress dstAddr) {
        throw new RuntimeException("Stub!");
    }

    /**
     * Ensure that a network route exists to deliver traffic to the specified
     * host via the specified network interface. An attempt to add a route that
     * already exists is ignored, but treated as successful.
     *
     * <p>This method requires the caller to hold either the
     * {@link android.Manifest.permission#CHANGE_NETWORK_STATE} permission
     * or the ability to modify system settings as determined by
     * {@link android.provider.Settings.System#canWrite}.</p>
     *
     * @param networkType the type of the network over which traffic to the specified
     * host is to be routed
     * @param hostAddress the IP address of the host to which the route is desired
     * @return {@code true} on success, {@code false} on failure
     *
     * @deprecated Deprecated in favor of the
     *             {@link #requestNetwork(NetworkRequest, NetworkCallback)},
     *             {@link #bindProcessToNetwork} and {@link Network#getSocketFactory} API.
     *             In {@link VERSION_CODES#M}, and above, this method is unsupported and will
     *             throw {@code UnsupportedOperationException} if called.
     * @removed
     */
    @Deprecated
    public boolean requestRouteToHost(int networkType, int hostAddress) {
        throw new RuntimeException("Stub!");
    }

    /**
     * Ensure that a network route exists to deliver traffic to the specified
     * host via the specified network interface. An attempt to add a route that
     * already exists is ignored, but treated as successful.
     *
     * <p>This method requires the caller to hold either the
     * {@link android.Manifest.permission#CHANGE_NETWORK_STATE} permission
     * or the ability to modify system settings as determined by
     * {@link android.provider.Settings.System#canWrite}.</p>
     *
     * @param networkType the type of the network over which traffic to the specified
     * host is to be routed
     * @param hostAddress the IP address of the host to which the route is desired
     * @return {@code true} on success, {@code false} on failure
     * @hide
     * @deprecated Deprecated in favor of the {@link #requestNetwork} and
     *             {@link #bindProcessToNetwork} API.
     */
    @Deprecated
    public boolean requestRouteToHostAddress(int networkType, InetAddress hostAddress) {
        throw new RuntimeException("Stub!");
    }

    /**
     * Returns the value of the setting for background data usage. If false,
     * applications should not use the network if the application is not in the
     * foreground. Developers should respect this setting, and check the value
     * of this before performing any background data operations.
     * <p>
     * All applications that have background services that use the network
     * should listen to {@link #ACTION_BACKGROUND_DATA_SETTING_CHANGED}.
     * <p>
     * @deprecated As of {@link VERSION_CODES#ICE_CREAM_SANDWICH}, availability of
     * background data depends on several combined factors, and this method will
     * always return {@code true}. Instead, when background data is unavailable,
     * {@link #getActiveNetworkInfo()} will now appear disconnected.
     *
     * @return Whether background data usage is allowed.
     */
    @Deprecated
    public boolean getBackgroundDataSetting() {
        throw new RuntimeException("Stub!");
    }

    /**
     * Sets the value of the setting for background data usage.
     *
     * @param allowBackgroundData Whether an application should use data while
     *            it is in the background.
     *
     * @attr ref android.Manifest.permission#CHANGE_BACKGROUND_DATA_SETTING
     * @see #getBackgroundDataSetting()
     * @hide
     */
    @Deprecated
    public void setBackgroundDataSetting(boolean allowBackgroundData) {
        throw new RuntimeException("Stub!");
    }

    /**
     * @hide
     * @deprecated Talk to TelephonyManager directly
     */
    @Deprecated
    public boolean getMobileDataEnabled() {
        throw new RuntimeException("Stub!");
    }

    /**
     * Callback for use with {@link ConnectivityManager#addDefaultNetworkActiveListener}
     * to find out when the system default network has gone in to a high power state.
     */
    public interface OnNetworkActiveListener {

        /**
         * Called on the main thread of the process to report that the current data network
         * has become active, and it is now a good time to perform any pending network
         * operations.  Note that this listener only tells you when the network becomes
         * active; if at any other time you want to know whether it is active (and thus okay
         * to initiate network traffic), you can retrieve its instantaneous state with
         * {@link ConnectivityManager#isDefaultNetworkActive}.
         */
        void onNetworkActive();
    }

    /**
     * Start listening to reports when the system's default data network is active, meaning it is
     * a good time to perform network traffic.  Use {@link #isDefaultNetworkActive()}
     * to determine the current state of the system's default network after registering the
     * listener.
     * <p>
     * If the process default network has been set with
     * {@link ConnectivityManager#bindProcessToNetwork} this function will not
     * reflect the process's default, but the system default.
     *
     * @param l The listener to be told when the network is active.
     */
    public void addDefaultNetworkActiveListener(final OnNetworkActiveListener l) {
        throw new RuntimeException("Stub!");
    }

    /**
     * Remove network active listener previously registered with
     * {@link #addDefaultNetworkActiveListener}.
     *
     * @param l Previously registered listener.
     */
    public void removeDefaultNetworkActiveListener(OnNetworkActiveListener l) {
        throw new RuntimeException("Stub!");
    }

    /**
     * Return whether the data network is currently active.  An active network means that
     * it is currently in a high power state for performing data transmission.  On some
     * types of networks, it may be expensive to move and stay in such a state, so it is
     * more power efficient to batch network traffic together when the radio is already in
     * this state.  This method tells you whether right now is currently a good time to
     * initiate network traffic, as the network is already active.
     */
    public boolean isDefaultNetworkActive() {
        throw new RuntimeException("Stub!");
    }

    /** {@hide} */
    public static ConnectivityManager from(Context context) {
        throw new RuntimeException("Stub!");
    }

    /* TODO: These permissions checks don't belong in client-side code. Move them to
     * services.jar, possibly in com.android.server.net. */
    /** {@hide} */
    public static final void enforceChangePermission(Context context) {
        throw new RuntimeException("Stub!");
    }

    /** {@hide} */
    public static final void enforceTetherChangePermission(Context context, String callingPkg) {
        throw new RuntimeException("Stub!");
    }

    /**
     * @deprecated - use getSystemService. This is a kludge to support static access in certain
     *               situations where a Context pointer is unavailable.
     * @hide
     */
    @Deprecated
    static ConnectivityManager getInstanceOrNull() {
        throw new RuntimeException("Stub!");
    }

    /**
     * @deprecated - use getSystemService. This is a kludge to support static access in certain
     *               situations where a Context pointer is unavailable.
     * @hide
     */
    @Deprecated
    private static ConnectivityManager getInstance() {
        throw new RuntimeException("Stub!");
    }

    /**
     * Get the set of tetherable, available interfaces.  This list is limited by
     * device configuration and current interface existence.
     *
     * @return an array of 0 or more Strings of tetherable interface names.
     *
     * {@hide}
     */
    @RequiresPermission(android.Manifest.permission.ACCESS_NETWORK_STATE)
    public String[] getTetherableIfaces() {
        throw new RuntimeException("Stub!");
    }

    /**
     * Get the set of tethered interfaces.
     *
     * @return an array of 0 or more String of currently tethered interface names.
     *
     * {@hide}
     */
    @RequiresPermission(android.Manifest.permission.ACCESS_NETWORK_STATE)
    public String[] getTetheredIfaces() {
        throw new RuntimeException("Stub!");
    }

    /**
     * Get the set of interface names which attempted to tether but
     * failed.  Re-attempting to tether may cause them to reset to the Tethered
     * state.  Alternatively, causing the interface to be destroyed and recreated
     * may cause them to reset to the available state.
     * {@link ConnectivityManager#getLastTetherError} can be used to get more
     * information on the cause of the errors.
     *
     * @return an array of 0 or more String indicating the interface names
     *        which failed to tether.
     *
     * {@hide}
     */
    @RequiresPermission(android.Manifest.permission.ACCESS_NETWORK_STATE)
    public String[] getTetheringErroredIfaces() {
        throw new RuntimeException("Stub!");
    }

    /**
     * Get the set of tethered dhcp ranges.
     *
     * @return an array of 0 or more {@code String} of tethered dhcp ranges.
     * {@hide}
     */
    public String[] getTetheredDhcpRanges() {
        throw new RuntimeException("Stub!");
    }

    /**
     * Attempt to tether the named interface.  This will setup a dhcp server
     * on the interface, forward and NAT IP packets and forward DNS requests
     * to the best active upstream network interface.  Note that if no upstream
     * IP network interface is available, dhcp will still run and traffic will be
     * allowed between the tethered devices and this device, though upstream net
     * access will of course fail until an upstream network interface becomes
     * active.
     *
     * <p>This method requires the caller to hold either the
     * {@link android.Manifest.permission#CHANGE_NETWORK_STATE} permission
     * or the ability to modify system settings as determined by
     * {@link android.provider.Settings.System#canWrite}.</p>
     *
     * <p>WARNING: New clients should not use this function. The only usages should be in PanService
     * and WifiStateMachine which need direct access. All other clients should use
     * {@link #startTethering} and {@link #stopTethering} which encapsulate proper provisioning
     * logic.</p>
     *
     * @param iface the interface name to tether.
     * @return error a {@code TETHER_ERROR} value indicating success or failure type
     *
     * {@hide}
     */
    public int tether(String iface) {
        throw new RuntimeException("Stub!");
    }

    /**
     * Stop tethering the named interface.
     *
     * <p>This method requires the caller to hold either the
     * {@link android.Manifest.permission#CHANGE_NETWORK_STATE} permission
     * or the ability to modify system settings as determined by
     * {@link android.provider.Settings.System#canWrite}.</p>
     *
     * <p>WARNING: New clients should not use this function. The only usages should be in PanService
     * and WifiStateMachine which need direct access. All other clients should use
     * {@link #startTethering} and {@link #stopTethering} which encapsulate proper provisioning
     * logic.</p>
     *
     * @param iface the interface name to untether.
     * @return error a {@code TETHER_ERROR} value indicating success or failure type
     *
     * {@hide}
     */
    public int untether(String iface) {
        throw new RuntimeException("Stub!");
    }

    /**
     * Check if the device allows for tethering.  It may be disabled via
     * {@code ro.tether.denied} system property, Settings.TETHER_SUPPORTED or
     * due to device configuration.
     *
     * <p>If this app does not have permission to use this API, it will always
     * return false rather than throw an exception.</p>
     *
     * <p>If the device has a hotspot provisioning app, the caller is required to hold the
     * {@link android.Manifest.permission.TETHER_PRIVILEGED} permission.</p>
     *
     * <p>Otherwise, this method requires the caller to hold the ability to modify system
     * settings as determined by {@link android.provider.Settings.System#canWrite}.</p>
     *
     * @return a boolean - {@code true} indicating Tethering is supported.
     *
     * {@hide}
     */
    @SystemApi
    public boolean isTetheringSupported() {
        throw new RuntimeException("Stub!");
    }

    /**
     * Callback for use with {@link #startTethering} to find out whether tethering succeeded.
     * @hide
     */
    @SystemApi
    public abstract static class OnStartTetheringCallback {

        /**
         * Called when tethering has been successfully started.
         */
        public void onTetheringStarted() {
            throw new RuntimeException("Stub!");
        }

        /**
         * Called when starting tethering failed.
         */
        public void onTetheringFailed() {
            throw new RuntimeException("Stub!");
        }
    }

    /**
     * Convenient overload for
     * {@link #startTethering(int, boolean, OnStartTetheringCallback, Handler)} which passes a null
     * handler to run on the current thread's {@link Looper}.
     * @hide
     */
    @SystemApi
    public void startTethering(int type, boolean showProvisioningUi, final OnStartTetheringCallback callback) {
        throw new RuntimeException("Stub!");
    }

    /**
     * Runs tether provisioning for the given type if needed and then starts tethering if
     * the check succeeds. If no carrier provisioning is required for tethering, tethering is
     * enabled immediately. If provisioning fails, tethering will not be enabled. It also
     * schedules tether provisioning re-checks if appropriate.
     *
     * @param type The type of tethering to start. Must be one of
     *         {@link ConnectivityManager.TETHERING_WIFI},
     *         {@link ConnectivityManager.TETHERING_USB}, or
     *         {@link ConnectivityManager.TETHERING_BLUETOOTH}.
     * @param showProvisioningUi a boolean indicating to show the provisioning app UI if there
     *         is one. This should be true the first time this function is called and also any time
     *         the user can see this UI. It gives users information from their carrier about the
     *         check failing and how they can sign up for tethering if possible.
     * @param callback an {@link OnStartTetheringCallback} which will be called to notify the caller
     *         of the result of trying to tether.
     * @param handler {@link Handler} to specify the thread upon which the callback will be invoked.
     * @hide
     */
    @SystemApi
    public void startTethering(int type, boolean showProvisioningUi, final OnStartTetheringCallback callback, Handler handler) {
        throw new RuntimeException("Stub!");
    }

    /**
     * Stops tethering for the given type. Also cancels any provisioning rechecks for that type if
     * applicable.
     *
     * @param type The type of tethering to stop. Must be one of
     *         {@link ConnectivityManager.TETHERING_WIFI},
     *         {@link ConnectivityManager.TETHERING_USB}, or
     *         {@link ConnectivityManager.TETHERING_BLUETOOTH}.
     * @hide
     */
    @SystemApi
    public void stopTethering(int type) {
        throw new RuntimeException("Stub!");
    }

    /**
     * Get the list of regular expressions that define any tetherable
     * USB network interfaces.  If USB tethering is not supported by the
     * device, this list should be empty.
     *
     * @return an array of 0 or more regular expression Strings defining
     *        what interfaces are considered tetherable usb interfaces.
     *
     * {@hide}
     */
    @RequiresPermission(android.Manifest.permission.ACCESS_NETWORK_STATE)
    public String[] getTetherableUsbRegexs() {
        throw new RuntimeException("Stub!");
    }

    /**
     * Get the list of regular expressions that define any tetherable
     * Wifi network interfaces.  If Wifi tethering is not supported by the
     * device, this list should be empty.
     *
     * @return an array of 0 or more regular expression Strings defining
     *        what interfaces are considered tetherable wifi interfaces.
     *
     * {@hide}
     */
    @RequiresPermission(android.Manifest.permission.ACCESS_NETWORK_STATE)
    public String[] getTetherableWifiRegexs() {
        throw new RuntimeException("Stub!");
    }

    /**
     * Get the list of regular expressions that define any tetherable
     * Bluetooth network interfaces.  If Bluetooth tethering is not supported by the
     * device, this list should be empty.
     *
     * @return an array of 0 or more regular expression Strings defining
     *        what interfaces are considered tetherable bluetooth interfaces.
     *
     * {@hide}
     */
    @RequiresPermission(android.Manifest.permission.ACCESS_NETWORK_STATE)
    public String[] getTetherableBluetoothRegexs() {
        throw new RuntimeException("Stub!");
    }

    /**
     * Attempt to both alter the mode of USB and Tethering of USB.  A
     * utility method to deal with some of the complexity of USB - will
     * attempt to switch to Rndis and subsequently tether the resulting
     * interface on {@code true} or turn off tethering and switch off
     * Rndis on {@code false}.
     *
     * <p>This method requires the caller to hold either the
     * {@link android.Manifest.permission#CHANGE_NETWORK_STATE} permission
     * or the ability to modify system settings as determined by
     * {@link android.provider.Settings.System#canWrite}.</p>
     *
     * @param enable a boolean - {@code true} to enable tethering
     * @return error a {@code TETHER_ERROR} value indicating success or failure type
     *
     * {@hide}
     */
    public int setUsbTethering(boolean enable) {
        throw new RuntimeException("Stub!");
    }

    /** {@hide} */
    public static final int TETHER_ERROR_NO_ERROR = 0;

    /** {@hide} */
    public static final int TETHER_ERROR_UNKNOWN_IFACE = 1;

    /** {@hide} */
    public static final int TETHER_ERROR_SERVICE_UNAVAIL = 2;

    /** {@hide} */
    public static final int TETHER_ERROR_UNSUPPORTED = 3;

    /** {@hide} */
    public static final int TETHER_ERROR_UNAVAIL_IFACE = 4;

    /** {@hide} */
    public static final int TETHER_ERROR_MASTER_ERROR = 5;

    /** {@hide} */
    public static final int TETHER_ERROR_TETHER_IFACE_ERROR = 6;

    /** {@hide} */
    public static final int TETHER_ERROR_UNTETHER_IFACE_ERROR = 7;

    /** {@hide} */
    public static final int TETHER_ERROR_ENABLE_NAT_ERROR = 8;

    /** {@hide} */
    public static final int TETHER_ERROR_DISABLE_NAT_ERROR = 9;

    /** {@hide} */
    public static final int TETHER_ERROR_IFACE_CFG_ERROR = 10;

    /** {@hide} */
    public static final int TETHER_ERROR_PROVISION_FAILED = 11;

    /**
     * Get a more detailed error code after a Tethering or Untethering
     * request asynchronously failed.
     *
     * @param iface The name of the interface of interest
     * @return error The error code of the last error tethering or untethering the named
     *               interface
     *
     * {@hide}
     */
    @RequiresPermission(android.Manifest.permission.ACCESS_NETWORK_STATE)
    public int getLastTetherError(String iface) {
        throw new RuntimeException("Stub!");
    }

    /**
     * Report network connectivity status.  This is currently used only
     * to alter status bar UI.
     * <p>This method requires the caller to hold the permission
     * {@link android.Manifest.permission#STATUS_BAR}.
     *
     * @param networkType The type of network you want to report on
     * @param percentage The quality of the connection 0 is bad, 100 is good
     * @deprecated Types are deprecated. Use {@link #reportNetworkConnectivity} instead.
     * {@hide}
     */
    public void reportInetCondition(int networkType, int percentage) {
        throw new RuntimeException("Stub!");
    }

    /**
     * Report a problem network to the framework.  This provides a hint to the system
     * that there might be connectivity problems on this network and may cause
     * the framework to re-evaluate network connectivity and/or switch to another
     * network.
     *
     * @param network The {@link Network} the application was attempting to use
     *                or {@code null} to indicate the current default network.
     * @deprecated Use {@link #reportNetworkConnectivity} which allows reporting both
     *             working and non-working connectivity.
     */
    @Deprecated
    public void reportBadNetwork(Network network) {
        throw new RuntimeException("Stub!");
    }

    /**
     * Report to the framework whether a network has working connectivity.
     * This provides a hint to the system that a particular network is providing
     * working connectivity or not.  In response the framework may re-evaluate
     * the network's connectivity and might take further action thereafter.
     *
     * @param network The {@link Network} the application was attempting to use
     *                or {@code null} to indicate the current default network.
     * @param hasConnectivity {@code true} if the application was able to successfully access the
     *                        Internet using {@code network} or {@code false} if not.
     */
    public void reportNetworkConnectivity(Network network, boolean hasConnectivity) {
        throw new RuntimeException("Stub!");
    }

    /**
     * Set a network-independent global http proxy.  This is not normally what you want
     * for typical HTTP proxies - they are general network dependent.  However if you're
     * doing something unusual like general internal filtering this may be useful.  On
     * a private network where the proxy is not accessible, you may break HTTP using this.
     *
     * @param p A {@link ProxyInfo} object defining the new global
     *        HTTP proxy.  A {@code null} value will clear the global HTTP proxy.
     * @hide
     */
    public void setGlobalProxy(ProxyInfo p) {
        throw new RuntimeException("Stub!");
    }

    /**
     * Retrieve any network-independent global HTTP proxy.
     *
     * @return {@link ProxyInfo} for the current global HTTP proxy or {@code null}
     *        if no global HTTP proxy is set.
     * @hide
     */
    public ProxyInfo getGlobalProxy() {
        throw new RuntimeException("Stub!");
    }

    /**
     * Retrieve the global HTTP proxy, or if no global HTTP proxy is set, a
     * network-specific HTTP proxy.  If {@code network} is null, the
     * network-specific proxy returned is the proxy of the default active
     * network.
     *
     * @return {@link ProxyInfo} for the current global HTTP proxy, or if no
     *         global HTTP proxy is set, {@code ProxyInfo} for {@code network},
     *         or when {@code network} is {@code null},
     *         the {@code ProxyInfo} for the default active network.  Returns
     *         {@code null} when no proxy applies or the caller doesn't have
     *         permission to use {@code network}.
     * @hide
     */
    public ProxyInfo getProxyForNetwork(Network network) {
        throw new RuntimeException("Stub!");
    }

    /**
     * Get the current default HTTP proxy settings.  If a global proxy is set it will be returned,
     * otherwise if this process is bound to a {@link Network} using
     * {@link #bindProcessToNetwork} then that {@code Network}'s proxy is returned, otherwise
     * the default network's proxy is returned.
     *
     * @return the {@link ProxyInfo} for the current HTTP proxy, or {@code null} if no
     *        HTTP proxy is active.
     */
    public ProxyInfo getDefaultProxy() {
        throw new RuntimeException("Stub!");
    }

    /**
     * Returns true if the hardware supports the given network type
     * else it returns false.  This doesn't indicate we have coverage
     * or are authorized onto a network, just whether or not the
     * hardware supports it.  For example a GSM phone without a SIM
     * should still return {@code true} for mobile data, but a wifi only
     * tablet would return {@code false}.
     *
     * @param networkType The network type we'd like to check
     * @return {@code true} if supported, else {@code false}
     * @deprecated Types are deprecated. Use {@link NetworkCapabilities} instead.
     * @hide
     */
    @Deprecated
    @RequiresPermission(android.Manifest.permission.ACCESS_NETWORK_STATE)
    public boolean isNetworkSupported(int networkType) {
        return true;
    }

    /**
     * Returns if the currently active data network is metered. A network is
     * classified as metered when the user is sensitive to heavy data usage on
     * that connection due to monetary costs, data limitations or
     * battery/performance issues. You should check this before doing large
     * data transfers, and warn the user or delay the operation until another
     * network is available.
     *
     * @return {@code true} if large transfers should be avoided, otherwise
     *        {@code false}.
     */
    @RequiresPermission(android.Manifest.permission.ACCESS_NETWORK_STATE)
    public boolean isActiveNetworkMetered() {
        return false;
    }

    /**
     * If the LockdownVpn mechanism is enabled, updates the vpn
     * with a reload of its profile.
     *
     * @return a boolean with {@code} indicating success
     *
     * <p>This method can only be called by the system UID
     * {@hide}
     */
    public boolean updateLockdownVpn() {
        throw new RuntimeException("Stub!");
    }

    /**
     * Check mobile provisioning.
     *
     * @param suggestedTimeOutMs, timeout in milliseconds
     *
     * @return time out that will be used, maybe less that suggestedTimeOutMs
     * -1 if an error.
     *
     * {@hide}
     */
    public int checkMobileProvisioning(int suggestedTimeOutMs) {
        throw new RuntimeException("Stub!");
    }

    /**
     * Get the mobile provisioning url.
     * {@hide}
     */
    public String getMobileProvisioningUrl() {
        throw new RuntimeException("Stub!");
    }

    /**
     * Set sign in error notification to visible or in visible
     *
     * {@hide}
     * @deprecated Doesn't properly deal with multiple connected networks of the same type.
     */
    @Deprecated
    public void setProvisioningNotificationVisible(boolean visible, int networkType, String action) {
        throw new RuntimeException("Stub!");
    }

    /**
     * Set the value for enabling/disabling airplane mode
     *
     * @param enable whether to enable airplane mode or not
     *
     * @hide
     */
    public void setAirplaneMode(boolean enable) {
        throw new RuntimeException("Stub!");
    }

    /** {@hide} */
    public void registerNetworkFactory(Messenger messenger, String name) {
        throw new RuntimeException("Stub!");
    }

    /** {@hide} */
    public void unregisterNetworkFactory(Messenger messenger) {
        throw new RuntimeException("Stub!");
    }

    /**
     * Base class for {@code NetworkRequest} callbacks. Used for notifications about network
     * changes. Should be extended by applications wanting notifications.
     *
     * A {@code NetworkCallback} is registered by calling
     * {@link #requestNetwork(NetworkRequest, NetworkCallback)},
     * {@link #registerNetworkCallback(NetworkRequest, NetworkCallback)},
     * or {@link #registerDefaultNetworkCallback(NetworkCallback)}. A {@code NetworkCallback} is
     * unregistered by calling {@link #unregisterNetworkCallback(NetworkCallback)}.
     * A {@code NetworkCallback} should be registered at most once at any time.
     * A {@code NetworkCallback} that has been unregistered can be registered again.
     */
    public static class NetworkCallback {

        /**
         * Called when the framework connects to a new network to evaluate whether it satisfies this
         * request. If evaluation succeeds, this callback may be followed by an {@link #onAvailable}
         * callback. There is no guarantee that this new network will satisfy any requests, or that
         * the network will stay connected for longer than the time necessary to evaluate it.
         * <p>
         * Most applications <b>should not</b> act on this callback, and should instead use
         * {@link #onAvailable}. This callback is intended for use by applications that can assist
         * the framework in properly evaluating the network &mdash; for example, an application that
         * can automatically log in to a captive portal without user intervention.
         *
         * @param network The {@link Network} of the network that is being evaluated.
         *
         * @hide
         */
        public void onPreCheck(Network network) {
            throw new RuntimeException("Stub!");
        }

        /**
         * Called when the framework connects and has declared a new network ready for use.
         * This callback may be called more than once if the {@link Network} that is
         * satisfying the request changes.
         *
         * @param network The {@link Network} of the satisfying network.
         * @param networkCapabilities The {@link NetworkCapabilities} of the satisfying network.
         * @param linkProperties The {@link LinkProperties} of the satisfying network.
         * @hide
         */
        public void onAvailable(Network network, NetworkCapabilities networkCapabilities, LinkProperties linkProperties) {
            throw new RuntimeException("Stub!");
        }

        /**
         * Called when the framework connects and has declared a new network ready for use.
         * This callback may be called more than once if the {@link Network} that is
         * satisfying the request changes. This will always immediately be followed by a
         * call to {@link #onCapabilitiesChanged(Network, NetworkCapabilities)} then by a
         * call to {@link #onLinkPropertiesChanged(Network, LinkProperties)}.
         *
         * @param network The {@link Network} of the satisfying network.
         */
        public void onAvailable(Network network) {
            throw new RuntimeException("Stub!");
        }

        /**
         * Called when the network is about to be disconnected.  Often paired with an
         * {@link NetworkCallback#onAvailable} call with the new replacement network
         * for graceful handover.  This may not be called if we have a hard loss
         * (loss without warning).  This may be followed by either a
         * {@link NetworkCallback#onLost} call or a
         * {@link NetworkCallback#onAvailable} call for this network depending
         * on whether we lose or regain it.
         *
         * @param network The {@link Network} that is about to be disconnected.
         * @param maxMsToLive The time in ms the framework will attempt to keep the
         *                     network connected.  Note that the network may suffer a
         *                     hard loss at any time.
         */
        public void onLosing(Network network, int maxMsToLive) {
            throw new RuntimeException("Stub!");
        }

        /**
         * Called when the framework has a hard loss of the network or when the
         * graceful failure ends.
         *
         * @param network The {@link Network} lost.
         */
        public void onLost(Network network) {
            throw new RuntimeException("Stub!");
        }

        /**
         * Called if no network is found in the timeout time specified in
         * {@link #requestNetwork(NetworkRequest, NetworkCallback, int)} call. This callback is not
         * called for the version of {@link #requestNetwork(NetworkRequest, NetworkCallback)}
         * without timeout. When this callback is invoked the associated
         * {@link NetworkRequest} will have already been removed and released, as if
         * {@link #unregisterNetworkCallback(NetworkCallback)} had been called.
         */
        public void onUnavailable() {
            throw new RuntimeException("Stub!");
        }

        /**
         * Called when the network the framework connected to for this request
         * changes capabilities but still satisfies the stated need.
         *
         * @param network The {@link Network} whose capabilities have changed.
         * @param networkCapabilities The new {@link android.net.NetworkCapabilities} for this
         *                            network.
         */
        public void onCapabilitiesChanged(Network network, NetworkCapabilities networkCapabilities) {
            throw new RuntimeException("Stub!");
        }

        /**
         * Called when the network the framework connected to for this request
         * changes {@link LinkProperties}.
         *
         * @param network The {@link Network} whose link properties have changed.
         * @param linkProperties The new {@link LinkProperties} for this network.
         */
        public void onLinkPropertiesChanged(Network network, LinkProperties linkProperties) {
            throw new RuntimeException("Stub!");
        }

        /**
         * Called when the network the framework connected to for this request
         * goes into {@link NetworkInfo.State#SUSPENDED}.
         * This generally means that while the TCP connections are still live,
         * temporarily network data fails to transfer.  Specifically this is used
         * on cellular networks to mask temporary outages when driving through
         * a tunnel, etc.
         * @hide
         */
        public void onNetworkSuspended(Network network) {
            throw new RuntimeException("Stub!");
        }

        /**
         * Called when the network the framework connected to for this request
         * returns from a {@link NetworkInfo.State#SUSPENDED} state. This should always be
         * preceded by a matching {@link NetworkCallback#onNetworkSuspended} call.
         * @hide
         */
        public void onNetworkResumed(Network network) {
            throw new RuntimeException("Stub!");
        }

        private NetworkRequest networkRequest;
    }

    /**
     * Constant error codes used by ConnectivityService to communicate about failures and errors
     * across a Binder boundary.
     * @hide
     */
    public interface Errors {

        int TOO_MANY_REQUESTS = 1;
    }

    /** @hide */
    public static class TooManyRequestsException extends RuntimeException {
    }

    private static final int BASE = 0;

    /** @hide */
    public static final int CALLBACK_PRECHECK = 0;

    /** @hide */
    public static final int CALLBACK_AVAILABLE = 0;

    /** @hide arg1 = TTL */
    public static final int CALLBACK_LOSING = 0;

    /** @hide */
    public static final int CALLBACK_LOST = 0;

    /** @hide */
    public static final int CALLBACK_UNAVAIL = 0;

    /** @hide */
    public static final int CALLBACK_CAP_CHANGED = 0;

    /** @hide */
    public static final int CALLBACK_IP_CHANGED = 0;

    /** @hide obj = NetworkCapabilities, arg1 = seq number */
    private static final int EXPIRE_LEGACY_REQUEST = 0;

    /** @hide */
    public static final int CALLBACK_SUSPENDED = 0;

    /** @hide */
    public static final int CALLBACK_RESUMED = 0;

    /** @hide */
    public static String getCallbackName(int whichCallback) {
        throw new RuntimeException("Stub!");
    }

    private class CallbackHandler extends Handler {

        private static final String TAG = "ConnectivityManager.CallbackHandler";

        private static final boolean DBG = false;

        CallbackHandler(Looper looper) {
            throw new RuntimeException("Stub!");
        }

        CallbackHandler(Handler handler) {
            throw new RuntimeException("Stub!");
        }

        @Override
        public void handleMessage(Message message) {
            throw new RuntimeException("Stub!");
        }

        private <T> T getObject(Message msg, Class<T> c) {
            throw new RuntimeException("Stub!");
        }
    }

    private CallbackHandler getDefaultHandler() {
        throw new RuntimeException("Stub!");
    }

    private static final HashMap<NetworkRequest, NetworkCallback> sCallbacks = null;

    private static CallbackHandler sCallbackHandler;

    private static final int LISTEN = 1;

    private static final int REQUEST = 2;

    private NetworkRequest sendRequestForNetwork(NetworkCapabilities need, NetworkCallback callback, int timeoutMs, int action, int legacyType, CallbackHandler handler) {
        throw new RuntimeException("Stub!");
    }

    /**
     * Helper function to request a network with a particular legacy type.
     *
     * This is temporarily public @hide so it can be called by system code that uses the
     * NetworkRequest API to request networks but relies on CONNECTIVITY_ACTION broadcasts for
     * instead network notifications.
     *
     * TODO: update said system code to rely on NetworkCallbacks and make this method private.
     *
     * @hide
     */
    public void requestNetwork(NetworkRequest request, NetworkCallback networkCallback, int timeoutMs, int legacyType, Handler handler) {
        throw new RuntimeException("Stub!");
    }

    /**
     * Request a network to satisfy a set of {@link android.net.NetworkCapabilities}.
     *
     * This {@link NetworkRequest} will live until released via
     * {@link #unregisterNetworkCallback(NetworkCallback)} or the calling application exits. A
     * version of the method which takes a timeout is
     * {@link #requestNetwork(NetworkRequest, NetworkCallback, int)}.
     * Status of the request can be followed by listening to the various
     * callbacks described in {@link NetworkCallback}.  The {@link Network}
     * can be used to direct traffic to the network.
     * <p>It is presently unsupported to request a network with mutable
     * {@link NetworkCapabilities} such as
     * {@link NetworkCapabilities#NET_CAPABILITY_VALIDATED} or
     * {@link NetworkCapabilities#NET_CAPABILITY_CAPTIVE_PORTAL}
     * as these {@code NetworkCapabilities} represent states that a particular
     * network may never attain, and whether a network will attain these states
     * is unknown prior to bringing up the network so the framework does not
     * know how to go about satisfing a request with these capabilities.
     *
     * <p>This method requires the caller to hold either the
     * {@link android.Manifest.permission#CHANGE_NETWORK_STATE} permission
     * or the ability to modify system settings as determined by
     * {@link android.provider.Settings.System#canWrite}.</p>
     *
     * @param request {@link NetworkRequest} describing this request.
     * @param networkCallback The {@link NetworkCallback} to be utilized for this request. Note
     *                        the callback must not be shared - it uniquely specifies this request.
     *                        The callback is invoked on the default internal Handler.
     * @throws IllegalArgumentException if {@code request} specifies any mutable
     *         {@code NetworkCapabilities}.
     */
    public void requestNetwork(NetworkRequest request, NetworkCallback networkCallback) {
        throw new RuntimeException("Stub!");
    }

    /**
     * Request a network to satisfy a set of {@link android.net.NetworkCapabilities}.
     *
     * This {@link NetworkRequest} will live until released via
     * {@link #unregisterNetworkCallback(NetworkCallback)} or the calling application exits. A
     * version of the method which takes a timeout is
     * {@link #requestNetwork(NetworkRequest, NetworkCallback, int)}.
     * Status of the request can be followed by listening to the various
     * callbacks described in {@link NetworkCallback}.  The {@link Network}
     * can be used to direct traffic to the network.
     * <p>It is presently unsupported to request a network with mutable
     * {@link NetworkCapabilities} such as
     * {@link NetworkCapabilities#NET_CAPABILITY_VALIDATED} or
     * {@link NetworkCapabilities#NET_CAPABILITY_CAPTIVE_PORTAL}
     * as these {@code NetworkCapabilities} represent states that a particular
     * network may never attain, and whether a network will attain these states
     * is unknown prior to bringing up the network so the framework does not
     * know how to go about satisfying a request with these capabilities.
     *
     * <p>This method requires the caller to hold either the
     * {@link android.Manifest.permission#CHANGE_NETWORK_STATE} permission
     * or the ability to modify system settings as determined by
     * {@link android.provider.Settings.System#canWrite}.</p>
     *
     * @param request {@link NetworkRequest} describing this request.
     * @param networkCallback The {@link NetworkCallback} to be utilized for this request. Note
     *                        the callback must not be shared - it uniquely specifies this request.
     * @param handler {@link Handler} to specify the thread upon which the callback will be invoked.
     * @throws IllegalArgumentException if {@code request} specifies any mutable
     *         {@code NetworkCapabilities}.
     */
    public void requestNetwork(NetworkRequest request, NetworkCallback networkCallback, Handler handler) {
        throw new RuntimeException("Stub!");
    }

    /**
     * Request a network to satisfy a set of {@link android.net.NetworkCapabilities}, limited
     * by a timeout.
     *
     * This function behaves identically to the non-timed-out version
     * {@link #requestNetwork(NetworkRequest, NetworkCallback)}, but if a suitable network
     * is not found within the given time (in milliseconds) the
     * {@link NetworkCallback#onUnavailable()} callback is called. The request can still be
     * released normally by calling {@link #unregisterNetworkCallback(NetworkCallback)} but does
     * not have to be released if timed-out (it is automatically released). Unregistering a
     * request that timed out is not an error.
     *
     * <p>Do not use this method to poll for the existence of specific networks (e.g. with a small
     * timeout) - {@link #registerNetworkCallback(NetworkRequest, NetworkCallback)} is provided
     * for that purpose. Calling this method will attempt to bring up the requested network.
     *
     * <p>This method requires the caller to hold either the
     * {@link android.Manifest.permission#CHANGE_NETWORK_STATE} permission
     * or the ability to modify system settings as determined by
     * {@link android.provider.Settings.System#canWrite}.</p>
     *
     * @param request {@link NetworkRequest} describing this request.
     * @param networkCallback The {@link NetworkCallback} to be utilized for this request. Note
     *                        the callback must not be shared - it uniquely specifies this request.
     * @param timeoutMs The time in milliseconds to attempt looking for a suitable network
     *                  before {@link NetworkCallback#onUnavailable()} is called. The timeout must
     *                  be a positive value (i.e. >0).
     */
    public void requestNetwork(NetworkRequest request, NetworkCallback networkCallback, int timeoutMs) {
        throw new RuntimeException("Stub!");
    }

    /**
     * Request a network to satisfy a set of {@link android.net.NetworkCapabilities}, limited
     * by a timeout.
     *
     * This function behaves identically to the version without timeout, but if a suitable
     * network is not found within the given time (in milliseconds) the
     * {@link NetworkCallback#onUnavailable} callback is called. The request can still be
     * released normally by calling {@link #unregisterNetworkCallback(NetworkCallback)} but does
     * not have to be released if timed-out (it is automatically released). Unregistering a
     * request that timed out is not an error.
     *
     * <p>Do not use this method to poll for the existence of specific networks (e.g. with a small
     * timeout) - {@link #registerNetworkCallback(NetworkRequest, NetworkCallback)} is provided
     * for that purpose. Calling this method will attempt to bring up the requested network.
     *
     * <p>This method requires the caller to hold either the
     * {@link android.Manifest.permission#CHANGE_NETWORK_STATE} permission
     * or the ability to modify system settings as determined by
     * {@link android.provider.Settings.System#canWrite}.</p>
     *
     * @param request {@link NetworkRequest} describing this request.
     * @param networkCallback The {@link NetworkCallback} to be utilized for this request. Note
     *                        the callback must not be shared - it uniquely specifies this request.
     * @param handler {@link Handler} to specify the thread upon which the callback will be invoked.
     * @param timeoutMs The time in milliseconds to attempt looking for a suitable network
     *                  before {@link NetworkCallback#onUnavailable} is called.
     */
    public void requestNetwork(NetworkRequest request, NetworkCallback networkCallback, Handler handler, int timeoutMs) {
        throw new RuntimeException("Stub!");
    }

    /**
     * The lookup key for a {@link Network} object included with the intent after
     * successfully finding a network for the applications request.  Retrieve it with
     * {@link android.content.Intent#getParcelableExtra(String)}.
     * <p>
     * Note that if you intend to invoke {@link Network#openConnection(java.net.URL)}
     * then you must get a ConnectivityManager instance before doing so.
     */
    public static final String EXTRA_NETWORK = "android.net.extra.NETWORK";

    /**
     * The lookup key for a {@link NetworkRequest} object included with the intent after
     * successfully finding a network for the applications request.  Retrieve it with
     * {@link android.content.Intent#getParcelableExtra(String)}.
     */
    public static final String EXTRA_NETWORK_REQUEST = "android.net.extra.NETWORK_REQUEST";

    /**
     * Request a network to satisfy a set of {@link android.net.NetworkCapabilities}.
     *
     * This function behaves identically to the version that takes a NetworkCallback, but instead
     * of {@link NetworkCallback} a {@link PendingIntent} is used.  This means
     * the request may outlive the calling application and get called back when a suitable
     * network is found.
     * <p>
     * The operation is an Intent broadcast that goes to a broadcast receiver that
     * you registered with {@link Context#registerReceiver} or through the
     * &lt;receiver&gt; tag in an AndroidManifest.xml file
     * <p>
     * The operation Intent is delivered with two extras, a {@link Network} typed
     * extra called {@link #EXTRA_NETWORK} and a {@link NetworkRequest}
     * typed extra called {@link #EXTRA_NETWORK_REQUEST} containing
     * the original requests parameters.  It is important to create a new,
     * {@link NetworkCallback} based request before completing the processing of the
     * Intent to reserve the network or it will be released shortly after the Intent
     * is processed.
     * <p>
     * If there is already a request for this Intent registered (with the equality of
     * two Intents defined by {@link Intent#filterEquals}), then it will be removed and
     * replaced by this one, effectively releasing the previous {@link NetworkRequest}.
     * <p>
     * The request may be released normally by calling
     * {@link #releaseNetworkRequest(android.app.PendingIntent)}.
     * <p>It is presently unsupported to request a network with either
     * {@link NetworkCapabilities#NET_CAPABILITY_VALIDATED} or
     * {@link NetworkCapabilities#NET_CAPABILITY_CAPTIVE_PORTAL}
     * as these {@code NetworkCapabilities} represent states that a particular
     * network may never attain, and whether a network will attain these states
     * is unknown prior to bringing up the network so the framework does not
     * know how to go about satisfying a request with these capabilities.
     *
     * <p>This method requires the caller to hold either the
     * {@link android.Manifest.permission#CHANGE_NETWORK_STATE} permission
     * or the ability to modify system settings as determined by
     * {@link android.provider.Settings.System#canWrite}.</p>
     *
     * @param request {@link NetworkRequest} describing this request.
     * @param operation Action to perform when the network is available (corresponds
     *                  to the {@link NetworkCallback#onAvailable} call.  Typically
     *                  comes from {@link PendingIntent#getBroadcast}. Cannot be null.
     * @throws IllegalArgumentException if {@code request} contains either
     *         {@link NetworkCapabilities#NET_CAPABILITY_VALIDATED} or
     *         {@link NetworkCapabilities#NET_CAPABILITY_CAPTIVE_PORTAL}.
     */
    public void requestNetwork(NetworkRequest request, PendingIntent operation) {
        throw new RuntimeException("Stub!");
    }

    /**
     * Removes a request made via {@link #requestNetwork(NetworkRequest, android.app.PendingIntent)}
     * <p>
     * This method has the same behavior as
     * {@link #unregisterNetworkCallback(android.app.PendingIntent)} with respect to
     * releasing network resources and disconnecting.
     *
     * @param operation A PendingIntent equal (as defined by {@link Intent#filterEquals}) to the
     *                  PendingIntent passed to
     *                  {@link #requestNetwork(NetworkRequest, android.app.PendingIntent)} with the
     *                  corresponding NetworkRequest you'd like to remove. Cannot be null.
     */
    public void releaseNetworkRequest(PendingIntent operation) {
        throw new RuntimeException("Stub!");
    }

    private static void checkPendingIntentNotNull(PendingIntent intent) {
        throw new RuntimeException("Stub!");
    }

    private static void checkCallbackNotNull(NetworkCallback callback) {
        throw new RuntimeException("Stub!");
    }

    private static void checkTimeout(int timeoutMs) {
        throw new RuntimeException("Stub!");
    }

    /**
     * Registers to receive notifications about all networks which satisfy the given
     * {@link NetworkRequest}.  The callbacks will continue to be called until
     * either the application exits or link #unregisterNetworkCallback(NetworkCallback)} is called.
     *
     * @param request {@link NetworkRequest} describing this request.
     * @param networkCallback The {@link NetworkCallback} that the system will call as suitable
     *                        networks change state.
     *                        The callback is invoked on the default internal Handler.
     */
    @RequiresPermission(android.Manifest.permission.ACCESS_NETWORK_STATE)
    public void registerNetworkCallback(NetworkRequest request, NetworkCallback networkCallback) {
        throw new RuntimeException("Stub!");
    }

    /**
     * Registers to receive notifications about all networks which satisfy the given
     * {@link NetworkRequest}.  The callbacks will continue to be called until
     * either the application exits or link #unregisterNetworkCallback(NetworkCallback)} is called.
     *
     * @param request {@link NetworkRequest} describing this request.
     * @param networkCallback The {@link NetworkCallback} that the system will call as suitable
     *                        networks change state.
     * @param handler {@link Handler} to specify the thread upon which the callback will be invoked.
     */
    @RequiresPermission(android.Manifest.permission.ACCESS_NETWORK_STATE)
    public void registerNetworkCallback(NetworkRequest request, NetworkCallback networkCallback, Handler handler) {
        throw new RuntimeException("Stub!");
    }

    /**
     * Registers a PendingIntent to be sent when a network is available which satisfies the given
     * {@link NetworkRequest}.
     *
     * This function behaves identically to the version that takes a NetworkCallback, but instead
     * of {@link NetworkCallback} a {@link PendingIntent} is used.  This means
     * the request may outlive the calling application and get called back when a suitable
     * network is found.
     * <p>
     * The operation is an Intent broadcast that goes to a broadcast receiver that
     * you registered with {@link Context#registerReceiver} or through the
     * &lt;receiver&gt; tag in an AndroidManifest.xml file
     * <p>
     * The operation Intent is delivered with two extras, a {@link Network} typed
     * extra called {@link #EXTRA_NETWORK} and a {@link NetworkRequest}
     * typed extra called {@link #EXTRA_NETWORK_REQUEST} containing
     * the original requests parameters.
     * <p>
     * If there is already a request for this Intent registered (with the equality of
     * two Intents defined by {@link Intent#filterEquals}), then it will be removed and
     * replaced by this one, effectively releasing the previous {@link NetworkRequest}.
     * <p>
     * The request may be released normally by calling
     * {@link #unregisterNetworkCallback(android.app.PendingIntent)}.
     * @param request {@link NetworkRequest} describing this request.
     * @param operation Action to perform when the network is available (corresponds
     *                  to the {@link NetworkCallback#onAvailable} call.  Typically
     *                  comes from {@link PendingIntent#getBroadcast}. Cannot be null.
     */
    @RequiresPermission(android.Manifest.permission.ACCESS_NETWORK_STATE)
    public void registerNetworkCallback(NetworkRequest request, PendingIntent operation) {
        throw new RuntimeException("Stub!");
    }

    /**
     * Registers to receive notifications about changes in the system default network. The callbacks
     * will continue to be called until either the application exits or
     * {@link #unregisterNetworkCallback(NetworkCallback)} is called.
     *
     * @param networkCallback The {@link NetworkCallback} that the system will call as the
     *                        system default network changes.
     *                        The callback is invoked on the default internal Handler.
     */
    @RequiresPermission(android.Manifest.permission.ACCESS_NETWORK_STATE)
    public void registerDefaultNetworkCallback(NetworkCallback networkCallback) {
        throw new RuntimeException("Stub!");
    }

    /**
     * Registers to receive notifications about changes in the system default network. The callbacks
     * will continue to be called until either the application exits or
     * {@link #unregisterNetworkCallback(NetworkCallback)} is called.
     *
     * @param networkCallback The {@link NetworkCallback} that the system will call as the
     *                        system default network changes.
     * @param handler {@link Handler} to specify the thread upon which the callback will be invoked.
     */
    @RequiresPermission(android.Manifest.permission.ACCESS_NETWORK_STATE)
    public void registerDefaultNetworkCallback(NetworkCallback networkCallback, Handler handler) {
        throw new RuntimeException("Stub!");
    }

    /**
     * Requests bandwidth update for a given {@link Network} and returns whether the update request
     * is accepted by ConnectivityService. Once accepted, ConnectivityService will poll underlying
     * network connection for updated bandwidth information. The caller will be notified via
     * {@link ConnectivityManager.NetworkCallback} if there is an update. Notice that this
     * method assumes that the caller has previously called
     * {@link #registerNetworkCallback(NetworkRequest, NetworkCallback)} to listen for network
     * changes.
     *
     * @param network {@link Network} specifying which network you're interested.
     * @return {@code true} on success, {@code false} if the {@link Network} is no longer valid.
     */
    public boolean requestBandwidthUpdate(Network network) {
        throw new RuntimeException("Stub!");
    }

    /**
     * Unregisters a {@code NetworkCallback} and possibly releases networks originating from
     * {@link #requestNetwork(NetworkRequest, NetworkCallback)} and
     * {@link #registerNetworkCallback(NetworkRequest, NetworkCallback)} calls.
     * If the given {@code NetworkCallback} had previously been used with
     * {@code #requestNetwork}, any networks that had been connected to only to satisfy that request
     * will be disconnected.
     *
     * Notifications that would have triggered that {@code NetworkCallback} will immediately stop
     * triggering it as soon as this call returns.
     *
     * @param networkCallback The {@link NetworkCallback} used when making the request.
     */
    public void unregisterNetworkCallback(NetworkCallback networkCallback) {
        throw new RuntimeException("Stub!");
    }

    /**
     * Unregisters a callback previously registered via
     * {@link #registerNetworkCallback(NetworkRequest, android.app.PendingIntent)}.
     *
     * @param operation A PendingIntent equal (as defined by {@link Intent#filterEquals}) to the
     *                  PendingIntent passed to
     *                  {@link #registerNetworkCallback(NetworkRequest, android.app.PendingIntent)}.
     *                  Cannot be null.
     */
    public void unregisterNetworkCallback(PendingIntent operation) {
        throw new RuntimeException("Stub!");
    }

    /**
     * Informs the system whether it should switch to {@code network} regardless of whether it is
     * validated or not. If {@code accept} is true, and the network was explicitly selected by the
     * user (e.g., by selecting a Wi-Fi network in the Settings app), then the network will become
     * the system default network regardless of any other network that's currently connected. If
     * {@code always} is true, then the choice is remembered, so that the next time the user
     * connects to this network, the system will switch to it.
     *
     * @param network The network to accept.
     * @param accept Whether to accept the network even if unvalidated.
     * @param always Whether to remember this choice in the future.
     *
     * @hide
     */
    public void setAcceptUnvalidated(Network network, boolean accept, boolean always) {
        throw new RuntimeException("Stub!");
    }

    /**
     * Informs the system to penalize {@code network}'s score when it becomes unvalidated. This is
     * only meaningful if the system is configured not to penalize such networks, e.g., if the
     * {@code config_networkAvoidBadWifi} configuration variable is set to 0 and the {@code
     * NETWORK_AVOID_BAD_WIFI setting is unset}.
     *
     * @param network The network to accept.
     *
     * @hide
     */
    public void setAvoidUnvalidated(Network network) {
        throw new RuntimeException("Stub!");
    }

    /**
     * Requests that the system open the captive portal app on the specified network.
     *
     * @param network The network to log into.
     *
     * @hide
     */
    public void startCaptivePortalApp(Network network) {
        throw new RuntimeException("Stub!");
    }

    /**
     * It is acceptable to briefly use multipath data to provide seamless connectivity for
     * time-sensitive user-facing operations when the system default network is temporarily
     * unresponsive. The amount of data should be limited (less than one megabyte for every call to
     * this method), and the operation should be infrequent to ensure that data usage is limited.
     *
     * An example of such an operation might be a time-sensitive foreground activity, such as a
     * voice command, that the user is performing while walking out of range of a Wi-Fi network.
     */
    public static final int MULTIPATH_PREFERENCE_HANDOVER = 1 << 0;

    /**
     * It is acceptable to use small amounts of multipath data on an ongoing basis to provide
     * a backup channel for traffic that is primarily going over another network.
     *
     * An example might be maintaining backup connections to peers or servers for the purpose of
     * fast fallback if the default network is temporarily unresponsive or disconnects. The traffic
     * on backup paths should be negligible compared to the traffic on the main path.
     */
    public static final int MULTIPATH_PREFERENCE_RELIABILITY = 1 << 1;

    /**
     * It is acceptable to use metered data to improve network latency and performance.
     */
    public static final int MULTIPATH_PREFERENCE_PERFORMANCE = 1 << 2;

    /**
     * Return value to use for unmetered networks. On such networks we currently set all the flags
     * to true.
     * @hide
     */
    public static final int MULTIPATH_PREFERENCE_UNMETERED = 0;

    /** @hide */
    @Retention(RetentionPolicy.SOURCE)
    @IntDef(flag = true, value = { MULTIPATH_PREFERENCE_HANDOVER, MULTIPATH_PREFERENCE_RELIABILITY, MULTIPATH_PREFERENCE_PERFORMANCE })
    public @interface MultipathPreference {
    }

    /**
     * Provides a hint to the calling application on whether it is desirable to use the
     * multinetwork APIs (e.g., {@link Network#openConnection}, {@link Network#bindSocket}, etc.)
     * for multipath data transfer on this network when it is not the system default network.
     * Applications desiring to use multipath network protocols should call this method before
     * each such operation.
     *
     * @param network The network on which the application desires to use multipath data.
     *                If {@code null}, this method will return the a preference that will generally
     *                apply to metered networks.
     * @return a bitwise OR of zero or more of the  {@code MULTIPATH_PREFERENCE_*} constants.
     */
    @RequiresPermission(android.Manifest.permission.ACCESS_NETWORK_STATE)
    @MultipathPreference
    public int getMultipathPreference(Network network) {
        throw new RuntimeException("Stub!");
    }

    /**
     * Resets all connectivity manager settings back to factory defaults.
     * @hide
     */
    public void factoryReset() {
        throw new RuntimeException("Stub!");
    }

    /**
     * Binds the current process to {@code network}.  All Sockets created in the future
     * (and not explicitly bound via a bound SocketFactory from
     * {@link Network#getSocketFactory() Network.getSocketFactory()}) will be bound to
     * {@code network}.  All host name resolutions will be limited to {@code network} as well.
     * Note that if {@code network} ever disconnects, all Sockets created in this way will cease to
     * work and all host name resolutions will fail.  This is by design so an application doesn't
     * accidentally use Sockets it thinks are still bound to a particular {@link Network}.
     * To clear binding pass {@code null} for {@code network}.  Using individually bound
     * Sockets created by Network.getSocketFactory().createSocket() and
     * performing network-specific host name resolutions via
     * {@link Network#getAllByName Network.getAllByName} is preferred to calling
     * {@code bindProcessToNetwork}.
     *
     * @param network The {@link Network} to bind the current process to, or {@code null} to clear
     *                the current binding.
     * @return {@code true} on success, {@code false} if the {@link Network} is no longer valid.
     */
    public boolean bindProcessToNetwork(Network network) {
        throw new RuntimeException("Stub!");
    }

    /**
     * Binds the current process to {@code network}.  All Sockets created in the future
     * (and not explicitly bound via a bound SocketFactory from
     * {@link Network#getSocketFactory() Network.getSocketFactory()}) will be bound to
     * {@code network}.  All host name resolutions will be limited to {@code network} as well.
     * Note that if {@code network} ever disconnects, all Sockets created in this way will cease to
     * work and all host name resolutions will fail.  This is by design so an application doesn't
     * accidentally use Sockets it thinks are still bound to a particular {@link Network}.
     * To clear binding pass {@code null} for {@code network}.  Using individually bound
     * Sockets created by Network.getSocketFactory().createSocket() and
     * performing network-specific host name resolutions via
     * {@link Network#getAllByName Network.getAllByName} is preferred to calling
     * {@code setProcessDefaultNetwork}.
     *
     * @param network The {@link Network} to bind the current process to, or {@code null} to clear
     *                the current binding.
     * @return {@code true} on success, {@code false} if the {@link Network} is no longer valid.
     * @deprecated This function can throw {@link IllegalStateException}.  Use
     *             {@link #bindProcessToNetwork} instead.  {@code bindProcessToNetwork}
     *             is a direct replacement.
     */
    @Deprecated
    public static boolean setProcessDefaultNetwork(Network network) {
        throw new RuntimeException("Stub!");
    }

    /**
     * Returns the {@link Network} currently bound to this process via
     * {@link #bindProcessToNetwork}, or {@code null} if no {@link Network} is explicitly bound.
     *
     * @return {@code Network} to which this process is bound, or {@code null}.
     */
    public Network getBoundNetworkForProcess() {
        throw new RuntimeException("Stub!");
    }

    /**
     * Returns the {@link Network} currently bound to this process via
     * {@link #bindProcessToNetwork}, or {@code null} if no {@link Network} is explicitly bound.
     *
     * @return {@code Network} to which this process is bound, or {@code null}.
     * @deprecated Using this function can lead to other functions throwing
     *             {@link IllegalStateException}.  Use {@link #getBoundNetworkForProcess} instead.
     *             {@code getBoundNetworkForProcess} is a direct replacement.
     */
    @Deprecated
    public static Network getProcessDefaultNetwork() {
        throw new RuntimeException("Stub!");
    }

    private void unsupportedStartingFrom(int version) {
        throw new RuntimeException("Stub!");
    }

    // Checks whether the calling app can use the legacy routing API (startUsingNetworkFeature,
    // stopUsingNetworkFeature, requestRouteToHost), and if not throw UnsupportedOperationException.
    // TODO: convert the existing system users (Tethering, GnssLocationProvider) to the new APIs and
    // remove these exemptions. Note that this check is not secure, and apps can still access these
    // functions by accessing ConnectivityService directly. However, it should be clear that doing
    // so is unsupported and may break in the future. http://b/22728205
    private void checkLegacyRoutingApiAccess() {
        throw new RuntimeException("Stub!");
    }

    /**
     * Binds host resolutions performed by this process to {@code network}.
     * {@link #bindProcessToNetwork} takes precedence over this setting.
     *
     * @param network The {@link Network} to bind host resolutions from the current process to, or
     *                {@code null} to clear the current binding.
     * @return {@code true} on success, {@code false} if the {@link Network} is no longer valid.
     * @hide
     * @deprecated This is strictly for legacy usage to support {@link #startUsingNetworkFeature}.
     */
    @Deprecated
    public static boolean setProcessDefaultNetworkForHostResolution(Network network) {
        throw new RuntimeException("Stub!");
    }

    /**
     * Device is not restricting metered network activity while application is running on
     * background.
     */
    public static final int RESTRICT_BACKGROUND_STATUS_DISABLED = 1;

    /**
     * Device is restricting metered network activity while application is running on background,
     * but application is allowed to bypass it.
     * <p>
     * In this state, application should take action to mitigate metered network access.
     * For example, a music streaming application should switch to a low-bandwidth bitrate.
     */
    public static final int RESTRICT_BACKGROUND_STATUS_WHITELISTED = 2;

    /**
     * Device is restricting metered network activity while application is running on background.
     * <p>
     * In this state, application should not try to use the network while running on background,
     * because it would be denied.
     */
    public static final int RESTRICT_BACKGROUND_STATUS_ENABLED = 3;

    /**
     * A change in the background metered network activity restriction has occurred.
     * <p>
     * Applications should call {@link #getRestrictBackgroundStatus()} to check if the restriction
     * applies to them.
     * <p>
     * This is only sent to registered receivers, not manifest receivers.
     */
    @SdkConstant(SdkConstantType.BROADCAST_INTENT_ACTION)
    public static final String ACTION_RESTRICT_BACKGROUND_CHANGED = "android.net.conn.RESTRICT_BACKGROUND_CHANGED";

    /** @hide */
    @Retention(RetentionPolicy.SOURCE)
    @IntDef(flag = false, value = { RESTRICT_BACKGROUND_STATUS_DISABLED, RESTRICT_BACKGROUND_STATUS_WHITELISTED, RESTRICT_BACKGROUND_STATUS_ENABLED })
    public @interface RestrictBackgroundStatus {
    }

    /**
     * Determines if the calling application is subject to metered network restrictions while
     * running on background.
     *
     * @return {@link #RESTRICT_BACKGROUND_STATUS_DISABLED},
     * {@link #RESTRICT_BACKGROUND_STATUS_ENABLED},
     * or {@link #RESTRICT_BACKGROUND_STATUS_WHITELISTED}
     */
    @RestrictBackgroundStatus
    public int getRestrictBackgroundStatus() {
        throw new RuntimeException("Stub!");
    }
}
