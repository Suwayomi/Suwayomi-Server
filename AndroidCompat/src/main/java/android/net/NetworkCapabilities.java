/*
 * Copyright (C) 2014 The Android Open Source Project
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

import android.annotation.SuppressLint;
import android.os.Parcel;
import android.os.Parcelable;

/**
 * Represents the capabilities of a network.
 * <p>
 * Minimal JVM implementation for the Suwayomi AndroidCompat module. Capability and
 * transport sets are stored as bitfields; specifiers, bandwidth estimates and signal
 * strength are modeled with simple fields returning defaults.
 */
public final class NetworkCapabilities implements Parcelable {
    /**
     * Network capabilities.
     * Values for {@code NET_CAPABILITY_*} constants taken from AOSP.
     */
    public static final int NET_CAPABILITY_MMS = 0;
    public static final int NET_CAPABILITY_SUPL = 1;
    public static final int NET_CAPABILITY_DUN = 2;
    public static final int NET_CAPABILITY_FOTA = 3;
    public static final int NET_CAPABILITY_IMS = 4;
    public static final int NET_CAPABILITY_CBS = 5;
    public static final int NET_CAPABILITY_WIFI_P2P = 6;
    public static final int NET_CAPABILITY_IA = 7;
    public static final int NET_CAPABILITY_RCS = 8;
    public static final int NET_CAPABILITY_XCAP = 9;
    public static final int NET_CAPABILITY_EIMS = 10;
    public static final int NET_CAPABILITY_NOT_METERED = 11;
    public static final int NET_CAPABILITY_INTERNET = 12;
    public static final int NET_CAPABILITY_NOT_RESTRICTED = 13;
    public static final int NET_CAPABILITY_TRUSTED = 14;
    public static final int NET_CAPABILITY_NOT_VPN = 15;
    public static final int NET_CAPABILITY_VALIDATED = 16;
    public static final int NET_CAPABILITY_CAPTIVE_PORTAL = 17;
    public static final int NET_CAPABILITY_NOT_ROAMING = 18;
    public static final int NET_CAPABILITY_FOREGROUND = 19;
    public static final int NET_CAPABILITY_NOT_CONGESTED = 20;
    public static final int NET_CAPABILITY_NOT_SUSPENDED = 21;
    public static final int NET_CAPABILITY_OEM_PAID = 22;
    public static final int NET_CAPABILITY_MCX = 23;
    public static final int NET_CAPABILITY_PARTIAL_CONNECTIVITY = 24;
    public static final int NET_CAPABILITY_TEMPORARILY_NOT_METERED = 25;
    public static final int NET_CAPABILITY_OEM_PRIVATE = 26;
    public static final int NET_CAPABILITY_VEHICLE_INTERNAL = 27;
    public static final int NET_CAPABILITY_NOT_VCN_MANAGED = 28;
    public static final int NET_CAPABILITY_ENTERPRISE = 29;
    public static final int NET_CAPABILITY_VSIM = 30;
    public static final int NET_CAPABILITY_BIP = 31;
    public static final int NET_CAPABILITY_HEADS_UP = 32;

    /** @hide */
    public static final int MIN_NET_CAPABILITY = NET_CAPABILITY_MMS;
    /** @hide */
    public static final int MAX_NET_CAPABILITY = NET_CAPABILITY_HEADS_UP;

    /**
     * Network transport types. Values for {@code TRANSPORT_*} constants taken from AOSP.
     */
    public static final int TRANSPORT_CELLULAR = 0;
    public static final int TRANSPORT_WIFI = 1;
    public static final int TRANSPORT_BLUETOOTH = 2;
    public static final int TRANSPORT_ETHERNET = 3;
    public static final int TRANSPORT_VPN = 4;
    public static final int TRANSPORT_WIFI_AWARE = 5;
    public static final int TRANSPORT_LOWPAN = 6;
    public static final int TRANSPORT_TEST = 7;
    public static final int TRANSPORT_USB = 8;
    public static final int TRANSPORT_THREAD = 9;
    public static final int TRANSPORT_SATELLITE = 10;

    /** @hide */
    public static final int MIN_TRANSPORT = TRANSPORT_CELLULAR;
    /** @hide */
    public static final int MAX_TRANSPORT = TRANSPORT_SATELLITE;

    private static final long INVALID_UID = -1L;
    public static final int SIGNAL_STRENGTH_UNSPECIFIED = Integer.MIN_VALUE;

    private long mNetworkCapabilities;
    private long mTransportTypes;
    private int mLinkDownBandwidthKbps;
    private int mLinkUpBandwidthKbps;
    private int mSignalStrength;
    private String mNetworkSpecifier;
    private long mRequestorUid = INVALID_UID;

    public NetworkCapabilities() {
        clearAll();
        mNetworkCapabilities =
                (1L << NET_CAPABILITY_NOT_RESTRICTED) | (1L << NET_CAPABILITY_TRUSTED) |
                        (1L << NET_CAPABILITY_NOT_VPN);
    }

    public NetworkCapabilities(NetworkCapabilities nc) {
        if (nc != null) {
            mNetworkCapabilities = nc.mNetworkCapabilities;
            mTransportTypes = nc.mTransportTypes;
            mLinkDownBandwidthKbps = nc.mLinkDownBandwidthKbps;
            mLinkUpBandwidthKbps = nc.mLinkUpBandwidthKbps;
            mSignalStrength = nc.mSignalStrength;
            mNetworkSpecifier = nc.mNetworkSpecifier;
            mRequestorUid = nc.mRequestorUid;
        }
    }

    private void clearAll() {
        mNetworkCapabilities = mTransportTypes = 0;
        mLinkDownBandwidthKbps = mLinkUpBandwidthKbps = 0;
        mSignalStrength = SIGNAL_STRENGTH_UNSPECIFIED;
    }

    /**
     * Adds the given capability to this {@code NetworkCapabilities} instance.
     * @hide
     */
    public NetworkCapabilities addCapability(int capability) {
        if (capability < MIN_NET_CAPABILITY || capability > MAX_NET_CAPABILITY) {
            throw new IllegalArgumentException("NetworkCapability out of range");
        }
        mNetworkCapabilities |= 1L << capability;
        return this;
    }

    /**
     * Removes (if found) the given capability from this {@code NetworkCapabilities}.
     * @hide
     */
    public NetworkCapabilities removeCapability(int capability) {
        mNetworkCapabilities &= ~(1L << capability);
        return this;
    }

    /**
     * Gets the capabilities of this {@code NetworkCapabilities} instance.
     * @hide
     */
    public int[] getCapabilities() {
        return enumerateBits(mNetworkCapabilities);
    }

    /**
     * Adds the given transport type to this {@code NetworkCapabilities} instance.
     * @hide
     */
    public NetworkCapabilities addTransportType(int transportType) {
        if (transportType < MIN_TRANSPORT || transportType > MAX_TRANSPORT) {
            throw new IllegalArgumentException("TransportType out of range");
        }
        mTransportTypes |= 1L << transportType;
        return this;
    }

    /**
     * Removes (if found) the given transport type from this {@code NetworkCapabilities} instance.
     * @hide
     */
    public NetworkCapabilities removeTransportType(int transportType) {
        mTransportTypes &= ~(1L << transportType);
        return this;
    }

    /** @hide */
    void clearAllForBuilder() {
        mNetworkCapabilities = 0;
    }

    /** @hide */
    void clearTransportTypesForBuilder() {
        mTransportTypes = 0;
    }

    /**
     * Gets the transport types of this {@code NetworkCapabilities} instance.
     * @hide
     */
    public int[] getTransportTypes() {
        return enumerateBits(mTransportTypes);
    }

    private static int[] enumerateBits(long val) {
        int[] result = new int[Long.bitCount(val)];
        int index = 0;
        int resource = 0;
        while (val > 0) {
            if ((val & 1) == 1) result[index++] = resource;
            val = val >> 1;
            resource++;
        }
        return result;
    }

    /**
     * Tests for the presence of a {@code NET_CAPABILITY_*} capability on this instance.
     */
    public boolean hasCapability(int capability) {
        return capability >= MIN_NET_CAPABILITY && capability <= MAX_NET_CAPABILITY &&
                (mNetworkCapabilities & (1L << capability)) != 0;
    }

    /**
     * Tests for the presence of a {@code TRANSPORT_*} transport type on this instance.
     */
    public boolean hasTransport(int transportType) {
        return transportType >= MIN_TRANSPORT && transportType <= MAX_TRANSPORT &&
                (mTransportTypes & (1L << transportType)) != 0;
    }

    /**
     * Gets the network specifier. Only set if this instance was created with a specifier.
     */
    public String getNetworkSpecifier() {
        return mNetworkSpecifier;
    }

    /**
     * Sets the network specifier.
     * @hide
     */
    public void setNetworkSpecifier(String networkSpecifier) {
        mNetworkSpecifier = networkSpecifier;
    }

    /**
     * Gets the link downstream bandwidth in Kbps, or 0 if unknown.
     */
    public int getLinkDownstreamBandwidthKbps() {
        return mLinkDownBandwidthKbps;
    }

    /**
     * Sets the link downstream bandwidth.
     * @hide
     */
    public void setLinkDownstreamBandwidthKbps(int downKbps) {
        mLinkDownBandwidthKbps = downKbps;
    }

    /**
     * Gets the link upstream bandwidth in Kbps, or 0 if unknown.
     */
    public int getLinkUpstreamBandwidthKbps() {
        return mLinkUpBandwidthKbps;
    }

    /**
     * Sets the link upstream bandwidth.
     * @hide
     */
    public void setLinkUpstreamBandwidthKbps(int upKbps) {
        mLinkUpBandwidthKbps = upKbps;
    }

    /**
     * Gets the signal strength, or {@link #SIGNAL_STRENGTH_UNSPECIFIED} if none set.
     */
    public int getSignalStrength() {
        return mSignalStrength;
    }

    /**
     * Checks whether this instance has a signal strength set.
     */
    public boolean hasSignalStrength() {
        return mSignalStrength > SIGNAL_STRENGTH_UNSPECIFIED;
    }

    /**
     * Sets the signal strength.
     * @hide
     */
    public void setSignalStrength(int signalStrength) {
        mSignalStrength = signalStrength;
    }

    /**
     * Returns true if the given {@code NetworkCapabilities} describes the same
     * capabilities and transports as this instance.
     */
    public boolean equalsNetCapabilities(NetworkCapabilities nc) {
        return (nc != null && nc.mNetworkCapabilities == this.mNetworkCapabilities);
    }

    /**
     * Returns true if the given {@code NetworkCapabilities} describes the same
     * transports as this instance.
     */
    public boolean equalsTransportTypes(NetworkCapabilities nc) {
        return (nc != null && nc.mTransportTypes == this.mTransportTypes);
    }

    /**
     * Returns true if the given {@code NetworkCapabilities} describes the same
     * specifier as this instance.
     */
    public boolean equalsSpecifier(NetworkCapabilities nc) {
        if (nc != null) {
            return String.valueOf(mNetworkSpecifier).equals(String.valueOf(nc.mNetworkSpecifier))
                    || (mNetworkSpecifier == null && nc.mNetworkSpecifier == null);
        }
        return mNetworkSpecifier == null;
    }

    /**
     * Passive check as to whether an object that may have any of the capabilities
     * contained in this {@code NetworkCapabilities} would be able to satisfy the
     * request in {@code that}.
     * @hide
     */
    public boolean satisfiedByNetworkCapabilities(NetworkCapabilities nc) {
        return nc != null &&
                satisfiedByNetworkCapabilities(nc.mNetworkCapabilities, false) &&
                satisfiedByTransportTypes(nc.mTransportTypes);
    }

    private boolean satisfiedByNetworkCapabilities(long netCap, boolean onlyImmutable) {
        return (netCap & mNetworkCapabilities) == mNetworkCapabilities;
    }

    private boolean satisfiedByTransportTypes(long transportTypes) {
        return mTransportTypes == 0 || ((transportTypes & mTransportTypes) != 0);
    }

    /**
     * @hide
     */
    public boolean satisfiedByImmutableNetworkCapabilities(NetworkCapabilities nc) {
        return nc != null && satisfiedByNetworkCapabilities(nc);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null || (obj instanceof NetworkCapabilities == false)) return false;
        NetworkCapabilities that = (NetworkCapabilities) obj;
        return (equalsNetCapabilities(that)
                && equalsTransportTypes(that)
                && this.mLinkUpBandwidthKbps == that.mLinkUpBandwidthKbps
                && this.mLinkDownBandwidthKbps == that.mLinkDownBandwidthKbps
                && equalsSpecifier(that)
                && this.mSignalStrength == that.mSignalStrength);
    }

    @Override
    public int hashCode() {
        return ((int) (mNetworkCapabilities & 0xFFFFFFFF)
                + ((int) (mNetworkCapabilities >> 32) * 3)
                + ((int) (mTransportTypes & 0xFFFFFFFF) * 5)
                + ((int) (mTransportTypes >> 32) * 7)
                + (mLinkDownBandwidthKbps * 11)
                + (mLinkUpBandwidthKbps * 13)
                + (mSignalStrength * 19)
                + (mNetworkSpecifier == null ? 0 : mNetworkSpecifier.hashCode() * 17));
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeLong(mNetworkCapabilities);
        dest.writeLong(mTransportTypes);
        dest.writeInt(mLinkDownBandwidthKbps);
        dest.writeInt(mLinkUpBandwidthKbps);
        dest.writeInt(mSignalStrength);
        dest.writeString(mNetworkSpecifier);
    }

    public static final Creator<NetworkCapabilities> CREATOR =
            new Creator<NetworkCapabilities>() {
                @Override
                public NetworkCapabilities createFromParcel(Parcel in) {
                    NetworkCapabilities netCap = new NetworkCapabilities();
                    netCap.mNetworkCapabilities = in.readLong();
                    netCap.mTransportTypes = in.readLong();
                    netCap.mLinkDownBandwidthKbps = in.readInt();
                    netCap.mLinkUpBandwidthKbps = in.readInt();
                    netCap.mSignalStrength = in.readInt();
                    netCap.mNetworkSpecifier = in.readString();
                    return netCap;
                }

                @Override
                public NetworkCapabilities[] newArray(int size) {
                    return new NetworkCapabilities[size];
                }
            };

    private String[] transportNamesOf(int[] types) {
        String[] typesStrings = new String[types.length];
        for (int i = 0; i < types.length; i++) {
            typesStrings[i] = transportNameOf(types[i]);
        }
        return typesStrings;
    }

    /** @hide */
    public static String transportNameOf(int transport) {
        switch (transport) {
            case TRANSPORT_CELLULAR: return "CELLULAR";
            case TRANSPORT_WIFI: return "WIFI";
            case TRANSPORT_BLUETOOTH: return "BLUETOOTH";
            case TRANSPORT_ETHERNET: return "ETHERNET";
            case TRANSPORT_VPN: return "VPN";
            case TRANSPORT_WIFI_AWARE: return "WIFI_AWARE";
            case TRANSPORT_LOWPAN: return "LOWPAN";
            case TRANSPORT_TEST: return "TEST";
            case TRANSPORT_USB: return "USB";
            case TRANSPORT_THREAD: return "THREAD";
            case TRANSPORT_SATELLITE: return "SATELLITE";
            default: return "UNKNOWN(" + transport + ")";
        }
    }

    /** @hide */
    public static String capabilityNameOf(int capability) {
        switch (capability) {
            case NET_CAPABILITY_MMS: return "MMS";
            case NET_CAPABILITY_SUPL: return "SUPL";
            case NET_CAPABILITY_DUN: return "DUN";
            case NET_CAPABILITY_FOTA: return "FOTA";
            case NET_CAPABILITY_IMS: return "IMS";
            case NET_CAPABILITY_CBS: return "CBS";
            case NET_CAPABILITY_WIFI_P2P: return "WIFI_P2P";
            case NET_CAPABILITY_IA: return "IA";
            case NET_CAPABILITY_RCS: return "RCS";
            case NET_CAPABILITY_XCAP: return "XCAP";
            case NET_CAPABILITY_EIMS: return "EIMS";
            case NET_CAPABILITY_NOT_METERED: return "NOT_METERED";
            case NET_CAPABILITY_INTERNET: return "INTERNET";
            case NET_CAPABILITY_NOT_RESTRICTED: return "NOT_RESTRICTED";
            case NET_CAPABILITY_TRUSTED: return "TRUSTED";
            case NET_CAPABILITY_NOT_VPN: return "NOT_VPN";
            case NET_CAPABILITY_VALIDATED: return "VALIDATED";
            case NET_CAPABILITY_CAPTIVE_PORTAL: return "CAPTIVE_PORTAL";
            case NET_CAPABILITY_NOT_ROAMING: return "NOT_ROAMING";
            case NET_CAPABILITY_FOREGROUND: return "FOREGROUND";
            case NET_CAPABILITY_NOT_CONGESTED: return "NOT_CONGESTED";
            case NET_CAPABILITY_NOT_SUSPENDED: return "NOT_SUSPENDED";
            case NET_CAPABILITY_OEM_PAID: return "OEM_PAID";
            case NET_CAPABILITY_MCX: return "MCX";
            case NET_CAPABILITY_PARTIAL_CONNECTIVITY: return "PARTIAL_CONNECTIVITY";
            case NET_CAPABILITY_TEMPORARILY_NOT_METERED: return "TEMPORARILY_NOT_METERED";
            case NET_CAPABILITY_OEM_PRIVATE: return "OEM_PRIVATE";
            case NET_CAPABILITY_VEHICLE_INTERNAL: return "VEHICLE_INTERNAL";
            case NET_CAPABILITY_NOT_VCN_MANAGED: return "NOT_VCN_MANAGED";
            case NET_CAPABILITY_ENTERPRISE: return "ENTERPRISE";
            case NET_CAPABILITY_VSIM: return "VSIM";
            case NET_CAPABILITY_BIP: return "BIP";
            case NET_CAPABILITY_HEADS_UP: return "HEADS_UP";
            default: return Integer.toString(capability);
        }
    }

    @Override
    public String toString() {
        int[] types = getTransportTypes();
        String tn = (types.length > 0) ? " Transports: " : "";
        for (int i = 0; i < types.length; i++) {
            tn += transportNameOf(types[i]);
            if (i + 1 < types.length) tn += "|";
        }

        int[] capa = getCapabilities();
        String cn = (capa.length > 0) ? " Capabilities: " : "";
        for (int i = 0; i < capa.length; i++) {
            cn += capabilityNameOf(capa[i]);
            if (i + 1 < capa.length) cn += "&";
        }

        String up = (mLinkUpBandwidthKbps > 0) ? " LinkUpBandwidth>=" + mLinkUpBandwidthKbps + "Kbps" : "";
        String dn = (mLinkDownBandwidthKbps > 0) ? " LinkDnBandwidth>=" + mLinkDownBandwidthKbps + "Kbps" : "";

        String specifier = (mNetworkSpecifier == null) ?
                "" : " Specifier: <" + mNetworkSpecifier + ">";

        String signalStrength = (hasSignalStrength()) ? " SignalStrength: " + mSignalStrength : "";

        return "[" + tn + cn + up + dn + specifier + signalStrength + "]";
    }

    /**
     * A {@code NetworkCapabilities} representing all capabilities/transports that the
     * emulated always-online default network has.
     * @hide
     */
    @SuppressLint("MissingGetterMatchingBuilder")
    public static NetworkCapabilities createDefault() {
        NetworkCapabilities nc = new NetworkCapabilities();
        nc.addCapability(NET_CAPABILITY_INTERNET);
        nc.addCapability(NET_CAPABILITY_VALIDATED);
        nc.addCapability(NET_CAPABILITY_NOT_METERED);
        nc.addCapability(NET_CAPABILITY_NOT_ROAMING);
        nc.addCapability(NET_CAPABILITY_NOT_CONGESTED);
        nc.addCapability(NET_CAPABILITY_NOT_SUSPENDED);
        nc.addTransportType(TRANSPORT_WIFI);
        return nc;
    }
}
