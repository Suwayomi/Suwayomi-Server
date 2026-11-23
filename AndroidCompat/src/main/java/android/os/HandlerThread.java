package android.os;

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

/** A thread that runs a {@link Looper}. */
public class HandlerThread extends Thread {
    private final int priority;
    private Looper looper;
    private Handler handler;

    public HandlerThread(String name) {
        this(name, 0);
    }

    public HandlerThread(String name, int priority) {
        super(name);
        this.priority = priority;
        setDaemon(true);
    }

    protected void onLooperPrepared() {
    }

    @Override
    public void run() {
        Looper.prepare();
        synchronized (this) {
            looper = Looper.myLooper();
            notifyAll();
        }
        onLooperPrepared();
        Looper.loop();
    }

    public Looper getLooper() {
        if (!isAlive()) {
            return null;
        }
        boolean wasInterrupted = false;
        synchronized (this) {
            while (isAlive() && looper == null) {
                try {
                    wait();
                } catch (InterruptedException e) {
                    wasInterrupted = true;
                }
            }
        }
        if (wasInterrupted) {
            Thread.currentThread().interrupt();
        }
        return looper;
    }

    public Handler getThreadHandler() {
        if (handler == null) {
            handler = new Handler(getLooper());
        }
        return handler;
    }

    public boolean quit() {
        Looper looper = getLooper();
        if (looper == null) {
            return false;
        }
        looper.mQueue.quit(false);
        return true;
    }

    public boolean quitSafely() {
        Looper looper = getLooper();
        if (looper == null) {
            return false;
        }
        looper.mQueue.quit(true);
        return true;
    }

    public int getThreadId() {
        return (int) getId();
    }
}
