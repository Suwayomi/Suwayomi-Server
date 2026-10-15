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

import android.os.Parcel;
import android.os.Parcelable;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Defines a request for a network, made through {@link NetworkRequest.Builder} and
 * used to request a network via {@link ConnectivityManager#requestNetwork} or to
 * listen for network changes via {@link ConnectivityManager#registerNetworkCallback}.
 * <p>
 * Minimal JVM implementation for the Suwayomi AndroidCompat module.
 */
public class NetworkRequest implements Parcelable {
    /**
     * The capabilities of the requested network.
     */
    public final NetworkCapabilities networkCapabilities;

    /**
     * The legacy connectivity manager type of the requested network.
     * @hide
     */
    public final int legacyType;

    /**
     * The request id of the requested network.
     * @hide
     */
    public final int requestId;

    /**
     * The type of the request: a network request (actively requests a network)
     * or a track request (only listens for changes).
     * @hide
     */
    public enum Type {
        NONE, LISTEN, TRACK_DEFAULT, REQUEST, BACKGROUND_REQUEST, TRACK_SYSTEM_DEFAULT,
        LISTEN_FOR_BEST, TRACK_SHORT
    }

    /**
     * The type of this request.
     * @hide
     */
    public final Type type;

    private static final AtomicInteger nextRequestId = new AtomicInteger(1);

    /**
     * @hide
     */
    public NetworkRequest(NetworkCapabilities nc, int legacyType, int rId, Type type) {
        if (nc == null) {
            throw new NullPointerException();
        }
        requestId = rId;
        networkCapabilities = nc;
        this.legacyType = legacyType;
        this.type = type;
    }

    /**
     * @hide
     */
    public NetworkRequest(NetworkRequest that) {
        networkCapabilities = new NetworkCapabilities(that.networkCapabilities);
        legacyType = that.legacyType;
        requestId = that.requestId;
        type = that.type;
    }

    /**
     * @hide
     */
    public NetworkRequest(NetworkCapabilities nc, int legacyType, Type type) {
        this(nc, legacyType, nextRequestId(), type);
    }

    /** @hide */
    public static int nextRequestId() {
        return nextRequestId.getAndIncrement();
    }

    /**
     * Builder used to create {@link NetworkRequest} objects. Specify the Network features
     * needed in terms of {@link NetworkCapabilities} features.
     */
    public static class Builder {
        private final NetworkCapabilities mNetworkCapabilities;

        /**
         * Default builder.
         */
        public Builder() {
            mNetworkCapabilities = new NetworkCapabilities();
            mNetworkCapabilities.removeCapability(NetworkCapabilities.NET_CAPABILITY_TRUSTED);
            mNetworkCapabilities.removeCapability(
                    NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED);
            mNetworkCapabilities.removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN);
        }

        /**
         * Build a copy of a {@code NetworkRequest}.
         */
        public Builder(NetworkRequest request) {
            mNetworkCapabilities = new NetworkCapabilities(request.networkCapabilities);
        }

        /**
         * Build {@link NetworkRequest} give the current set of capabilities.
         */
        public NetworkRequest build() {
            // Make a copy of the NetworkCapabilities so we don't inadvertently remove
            // capabilities later.
            final NetworkCapabilities nc = new NetworkCapabilities(mNetworkCapabilities);
            NetworkRequest nr = new NetworkRequest(nc, ConnectivityManager.TYPE_NONE,
                    nextRequestId(), Type.NONE);
            return nr;
        }

        /**
         * Add the given capability requirement to this builder.
         */
        public Builder addCapability(int capability) {
            mNetworkCapabilities.addCapability(capability);
            return this;
        }

        /**
         * Removes (if found) the given capability from this builder instance.
         */
        public Builder removeCapability(int capability) {
            mNetworkCapabilities.removeCapability(capability);
            return this;
        }

        /**
         * Sets the capabilities to the set specified.
         */
        public Builder setCapabilities(int[] capabilities) {
            mNetworkCapabilities.clearAllForBuilder();
            for (int cap : capabilities) {
                mNetworkCapabilities.addCapability(cap);
            }
            return this;
        }

        /**
         * Set this {@code Builder} to include a transport requirement.
         */
        public Builder addTransportType(int transportType) {
            mNetworkCapabilities.addTransportType(transportType);
            return this;
        }

        /**
         * Removes (if found) the given transport from this builder instance.
         */
        public Builder removeTransportType(int transportType) {
            mNetworkCapabilities.removeTransportType(transportType);
            return this;
        }

        /**
         * Sets the transport types to the set specified.
         */
        public Builder setTransportTypes(int[] transportTypes) {
            mNetworkCapabilities.clearTransportTypesForBuilder();
            for (int t : transportTypes) {
                mNetworkCapabilities.addTransportType(t);
            }
            return this;
        }

        /**
         * Sets the optional bearer specific network specifier.
         */
        public Builder setNetworkSpecifier(String networkSpecifier) {
            mNetworkCapabilities.setNetworkSpecifier(networkSpecifier);
            return this;
        }
    }

    /**
     * Returns the legacy type constant matching the request, or
     * {@link ConnectivityManager#TYPE_NONE} if none applies.
     * @hide
     */
    public int getLegacyType() {
        return legacyType;
    }

    /**
     * Returns true if this request can be satisfied by the given
     * {@link NetworkCapabilities}.
     * @hide
     */
    public boolean canBeSatisfiedBy(NetworkCapabilities nc) {
        return nc.satisfiedByNetworkCapabilities(networkCapabilities);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        networkCapabilities.writeToParcel(dest, flags);
        dest.writeInt(legacyType);
        dest.writeInt(requestId);
        dest.writeInt(type.ordinal());
    }

    public static final Creator<NetworkRequest> CREATOR =
            new Creator<NetworkRequest>() {
                @Override
                public NetworkRequest createFromParcel(Parcel in) {
                    NetworkCapabilities netCap = NetworkCapabilities.CREATOR.createFromParcel(in);
                    int legacyType = in.readInt();
                    int requestId = in.readInt();
                    Type type = Type.values()[in.readInt()];
                    return new NetworkRequest(netCap, legacyType, requestId, type);
                }

                @Override
                public NetworkRequest[] newArray(int size) {
                    return new NetworkRequest[size];
                }
            };

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof NetworkRequest)) return false;
        NetworkRequest that = (NetworkRequest) obj;
        return (that.networkCapabilities.equals(this.networkCapabilities)
                && that.legacyType == this.legacyType
                && that.requestId == this.requestId);
    }

    @Override
    public int hashCode() {
        return networkCapabilities.hashCode() + (legacyType * 3) + (requestId * 11);
    }

    @Override
    public String toString() {
        return "NetworkRequest [ " + type + " id=" + requestId +
                (legacyType != ConnectivityManager.TYPE_NONE ?
                        ", legacyType - " + legacyType + "," : "") +
                " " + networkCapabilities + " ]";
    }
}
