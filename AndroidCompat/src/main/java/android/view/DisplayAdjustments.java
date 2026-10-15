/*
 * Copyright (C) 2011 The Android Open Source Project
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
package android.view;

import android.content.res.CompatibilityInfo;
import android.content.res.Configuration;

/**
 * Compile-only UI stub surface: this environment never executes UI/display
 * code paths, so adjustments are no-ops and {@link #DEFAULT_DISPLAY_ADJUSTMENTS}
 * is a plain default instance.
 * @hide
 */
public class DisplayAdjustments {

    public static final DisplayAdjustments DEFAULT_DISPLAY_ADJUSTMENTS = new DisplayAdjustments();

    private volatile CompatibilityInfo mCompatInfo = null;

    private Configuration mConfiguration = null;

    public DisplayAdjustments() {
    }

    public DisplayAdjustments(Configuration configuration) {
        mConfiguration = configuration;
    }

    public DisplayAdjustments(DisplayAdjustments daj) {
        if (daj != null) {
            mCompatInfo = daj.mCompatInfo;
            mConfiguration = daj.mConfiguration;
        }
    }

    public void setCompatibilityInfo(CompatibilityInfo compatInfo) {
        mCompatInfo = compatInfo;
    }

    public CompatibilityInfo getCompatibilityInfo() {
        return mCompatInfo;
    }

    public void setConfiguration(Configuration configuration) {
        mConfiguration = configuration;
    }

    public Configuration getConfiguration() {
        return mConfiguration;
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(mCompatInfo, mConfiguration);
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof DisplayAdjustments)) {
            return false;
        }
        DisplayAdjustments other = (DisplayAdjustments) o;
        return java.util.Objects.equals(mCompatInfo, other.mCompatInfo)
                && java.util.Objects.equals(mConfiguration, other.mConfiguration);
    }
}