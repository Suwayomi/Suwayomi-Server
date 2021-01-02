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

package android.support.v4.os;

import android.os.Environment;

import java.io.File;

/**
 * Helper for accessing features in {@link Environment} introduced after API
 * level 4 in a backwards compatible fashion.
 */
public class EnvironmentCompat {
    /**
     * Unknown storage state, such as when a path isn't backed by known storage
     * media.
     *
     * @see #getStorageState(File)
     */
    public static final String MEDIA_UNKNOWN = "unknown";

    /**
     * Returns the current state of the storage device that provides the given
     * path.
     *
     * @return one of {@link #MEDIA_UNKNOWN}, {@link Environment#MEDIA_REMOVED},
     *         {@link Environment#MEDIA_UNMOUNTED},
     *         {@link Environment#MEDIA_CHECKING},
     *         {@link Environment#MEDIA_NOFS},
     *         {@link Environment#MEDIA_MOUNTED},
     *         {@link Environment#MEDIA_MOUNTED_READ_ONLY},
     *         {@link Environment#MEDIA_SHARED},
     *         {@link Environment#MEDIA_BAD_REMOVAL}, or
     *         {@link Environment#MEDIA_UNMOUNTABLE}.
     */
    public static String getStorageState(File path) {
        return Environment.getStorageState(path);
    }
}