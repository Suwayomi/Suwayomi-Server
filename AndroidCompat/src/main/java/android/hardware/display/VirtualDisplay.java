package android.hardware.display;

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import android.view.Display;
import android.view.Surface;

/** A display without a screen behind it. */
public final class VirtualDisplay {
    public abstract static class Callback {
        public void onPaused() {
        }

        public void onResumed() {
        }

        public void onStopped() {
        }
    }

    VirtualDisplay() {
    }

    public Display getDisplay() {
        return null;
    }

    public Surface getSurface() {
        return null;
    }

    public void setSurface(Surface surface) {
    }

    public void resize(int width, int height, int densityDpi) {
    }

    public void release() {
    }
}
