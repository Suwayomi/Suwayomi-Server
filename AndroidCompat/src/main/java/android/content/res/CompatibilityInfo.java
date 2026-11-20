/*
 * Copyright (C) 2006 The Android Open Source Project
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
package android.content.res;

import android.content.pm.ApplicationInfo;
import android.graphics.Canvas;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.Region;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.DisplayMetrics;
import android.view.MotionEvent;
import android.view.WindowManager;
import android.view.WindowManager.LayoutParams;

/**
 * CompatibilityInfo class keeps the information about compatibility mode that the application is
 * running under.
 * 
 *  {@hide} 
 */
public class CompatibilityInfo implements Parcelable {

    /** default compatibility info object for compatible applications */
    public static final CompatibilityInfo DEFAULT_COMPATIBILITY_INFO = null;

    /**
     * This is the number of pixels we would like to have along the
     * short axis of an app that needs to run on a normal size screen.
     */
    public static final int DEFAULT_NORMAL_SHORT_DIMENSION = 320;

    /**
     * This is the maximum aspect ratio we will allow while keeping
     * applications in a compatible screen size.
     */
    public static final float MAXIMUM_ASPECT_RATIO = (854f/480f);

    /**
     *  A compatibility flags
     */
    private final int mCompatibilityFlags;

    /**
     * A flag mask to tell if the application needs scaling (when mApplicationScale != 1.0f)
     * {@see compatibilityFlag}
     */
    private static final int SCALING_REQUIRED = 1;

    /**
     * Application must always run in compatibility mode?
     */
    private static final int ALWAYS_NEEDS_COMPAT = 2;

    /**
     * Application never should run in compatibility mode?
     */
    private static final int NEVER_NEEDS_COMPAT = 4;

    /**
     * Set if the application needs to run in screen size compatibility mode.
     */
    private static final int NEEDS_SCREEN_COMPAT = 8;

    /**
     * The effective screen density we have selected for this application.
     */
    public final int applicationDensity;

    /**
     * Application's scale.
     */
    public final float applicationScale;

    /**
     * Application's inverted scale.
     */
    public final float applicationInvertedScale;

    public CompatibilityInfo(ApplicationInfo appInfo, int screenLayout, int sw, boolean forceCompat) {
        throw new RuntimeException("Stub!");
    }

    private CompatibilityInfo(int compFlags, int dens, float scale, float invertedScale) {
        throw new RuntimeException("Stub!");
    }

    private CompatibilityInfo() {
        throw new RuntimeException("Stub!");
    }

    /**
     * @return true if the scaling is required
     */
    public boolean isScalingRequired() {
        throw new RuntimeException("Stub!");
    }

    public boolean supportsScreen() {
        throw new RuntimeException("Stub!");
    }

    public boolean neverSupportsScreen() {
        throw new RuntimeException("Stub!");
    }

    public boolean alwaysSupportsScreen() {
        throw new RuntimeException("Stub!");
    }

    /**
     * Returns the translator which translates the coordinates in compatibility mode.
     * @param params the window's parameter
     */
    public Translator getTranslator() {
        throw new RuntimeException("Stub!");
    }

    /**
     * A helper object to translate the screen and window coordinates back and forth.
     * @hide
     */
    public class Translator {

        public final float applicationScale;

        public final float applicationInvertedScale;

        private Rect mContentInsetsBuffer = null;

        private Rect mVisibleInsetsBuffer = null;

        private Region mTouchableAreaBuffer = null;

        Translator(float applicationScale, float applicationInvertedScale) {
            throw new RuntimeException("Stub!");
        }

        Translator() {
            throw new RuntimeException("Stub!");
        }

        /**
         * Translate the screen rect to the application frame.
         */
        public void translateRectInScreenToAppWinFrame(Rect rect) {
            throw new RuntimeException("Stub!");
        }

        /**
         * Translate the region in window to screen. 
         */
        public void translateRegionInWindowToScreen(Region transparentRegion) {
            throw new RuntimeException("Stub!");
        }

        /**
         * Apply translation to the canvas that is necessary to draw the content.
         */
        public void translateCanvas(Canvas canvas) {
            throw new RuntimeException("Stub!");
        }

        /**
         * Translate the motion event captured on screen to the application's window.
         */
        public void translateEventInScreenToAppWindow(MotionEvent event) {
            throw new RuntimeException("Stub!");
        }

        /**
         * Translate the window's layout parameter, from application's view to
         * Screen's view.
         */
        public void translateWindowLayout(WindowManager.LayoutParams params) {
            throw new RuntimeException("Stub!");
        }

        /**
         * Translate a Rect in application's window to screen.
         */
        public void translateRectInAppWindowToScreen(Rect rect) {
            throw new RuntimeException("Stub!");
        }

        /**
         * Translate a Rect in screen coordinates into the app window's coordinates.
         */
        public void translateRectInScreenToAppWindow(Rect rect) {
            throw new RuntimeException("Stub!");
        }

        /**
         * Translate a Point in screen coordinates into the app window's coordinates.
         */
        public void translatePointInScreenToAppWindow(PointF point) {
            throw new RuntimeException("Stub!");
        }

        /**
         * Translate the location of the sub window.
         * @param params
         */
        public void translateLayoutParamsInAppWindowToScreen(LayoutParams params) {
            throw new RuntimeException("Stub!");
        }

        /**
         * Translate the content insets in application window to Screen. This uses
         * the internal buffer for content insets to avoid extra object allocation.
         */
        public Rect getTranslatedContentInsets(Rect contentInsets) {
            throw new RuntimeException("Stub!");
        }

        /**
         * Translate the visible insets in application window to Screen. This uses
         * the internal buffer for visible insets to avoid extra object allocation.
         */
        public Rect getTranslatedVisibleInsets(Rect visibleInsets) {
            throw new RuntimeException("Stub!");
        }

        /**
         * Translate the touchable area in application window to Screen. This uses
         * the internal buffer for touchable area to avoid extra object allocation.
         */
        public Region getTranslatedTouchableArea(Region touchableArea) {
            throw new RuntimeException("Stub!");
        }
    }

    public void applyToDisplayMetrics(DisplayMetrics inoutDm) {
        throw new RuntimeException("Stub!");
    }

    public void applyToConfiguration(int displayDensity, Configuration inoutConfig) {
        throw new RuntimeException("Stub!");
    }

    /**
     * Compute the frame Rect for applications runs under compatibility mode.
     *
     * @param dm the display metrics used to compute the frame size.
     * @param outDm If non-null the width and height will be set to their scaled values.
     * @return Returns the scaling factor for the window.
     */
    public static float computeCompatibleScaling(DisplayMetrics dm, DisplayMetrics outDm) {
        throw new RuntimeException("Stub!");
    }

    @Override
    public boolean equals(Object o) {
        throw new RuntimeException("Stub!");
    }

    @Override
    public String toString() {
        throw new RuntimeException("Stub!");
    }

    @Override
    public int hashCode() {
        throw new RuntimeException("Stub!");
    }

    @Override
    public int describeContents() {
        throw new RuntimeException("Stub!");
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        throw new RuntimeException("Stub!");
    }

    public static final Parcelable.Creator<CompatibilityInfo> CREATOR = null;

    private CompatibilityInfo(Parcel source) {
        throw new RuntimeException("Stub!");
    }
}
