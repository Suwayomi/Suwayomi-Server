/*
 * Copyright (C) 2013 The Android Open Source Project
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

import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.Socket;
import java.net.SocketException;
import java.net.URL;
import java.net.URLConnection;
import java.net.UnknownHostException;

import javax.net.SocketFactory;

/**
 * Identifies a {@code Network}.
 * <p>
 * Minimal JVM implementation for the Suwayomi AndroidCompat module. The emulated
 * environment has exactly one network (the host's default route), so all
 * per-network operations delegate to the JVM defaults. The {@code netId} still
 * uniquely identifies the {@code Network} instance for equals/hashCode purposes.
 */
public class Network implements Parcelable {
    /**
     * Identifier assigned by the framework. A real Android device assigns netIds
     * in the range 100-64535; the compat layer uses a single fixed id for its
     * one always-online network.
     */
    public final int netId;

    private static final int NETID_UNSET = 0;

    /**
     * Create a {@code Network} representing this network. This method is only for use
     * by {@link ConnectivityManager}.
     *
     * @param netId this network's identifier
     * @hide
     */
    public Network(int netId) {
        this.netId = netId;
    }

    /**
     * Create a {@code Network} with no associated netId.
     * @hide
     */
    public Network() {
        this.netId = NETID_UNSET;
    }

    /**
     * Returns the netId of this network, or {@link ConnectivityManager#NETID_UNSET} if
     * none was assigned.
     * @hide
     */
    public int getNetId() {
        return netId;
    }

    /** Returns a {@link SocketFactory} bound to this network (the JVM default). */
    public SocketFactory getSocketFactory() {
        return SocketFactory.getDefault();
    }

    public URLConnection openConnection(URL url) throws IOException {
        return url.openConnection();
    }

    public URLConnection openConnection(URL url, Proxy proxy) throws IOException {
        return url.openConnection(proxy);
    }

    /**
     * Binds the given {@link Socket} to this network. No-op on the compat layer:
     * there is exactly one network and all sockets already use it.
     */
    public void bindSocket(Socket socket) {
    }

    /**
     * Binds the given {@link DatagramSocket} to this network. No-op on the compat
     * layer (see {@link #bindSocket(Socket)}).
     */
    public void bindSocket(DatagramSocket socket) {
    }

    public InetAddress getByName(String host) throws UnknownHostException {
        return InetAddress.getByName(host);
    }

    public InetAddress[] getAllByName(String host) throws UnknownHostException {
        return InetAddress.getAllByName(host);
    }

    /** @hide */
    public static InetAddress[] getAllByName(Network network, String host) throws UnknownHostException {
        return InetAddress.getAllByName(host);
    }

    /** @hide */
    public static Network fromNetworkHandle(long networkHandle) {
        if (networkHandle == 0) {
            return null;
        }
        return new Network((int) networkHandle);
    }

    /** @hide */
    public long getNetworkHandle() {
        return netId;
    }

    /**
     * Operate the socket's connect on this network.
     * @hide
     */
    public void bindSocket(Socket socket, InetSocketAddress local) throws SocketException {
        bindSocket(socket);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(netId);
    }

    public static final Creator<Network> CREATOR =
            new Creator<Network>() {
                @Override
                public Network createFromParcel(Parcel in) {
                    return new Network(in.readInt());
                }

                @Override
                public Network[] newArray(int size) {
                    return new Network[size];
                }
            };

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof Network)) return false;
        Network other = (Network) obj;
        return other.netId == this.netId;
    }

    @Override
    public int hashCode() {
        return netId * 11;
    }

    @Override
    public String toString() {
        return Integer.toString(netId);
    }
}
