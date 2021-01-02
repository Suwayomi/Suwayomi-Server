/*
 * Copyright (C) 2017 The Android Open Source Project
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

package android.support.v7.preference;

import android.support.annotation.Nullable;

import java.util.Set;

/**
 * A data store interface to be implemented and provided to the Preferences framework. This can be
 * used to replace the default {@link android.content.SharedPreferences}, if needed.
 *
 * <p>In most cases you want to use {@link android.content.SharedPreferences} as it is automatically
 * backed up and migrated to new devices. However, providing custom data store to preferences can be
 * useful if your app stores its preferences in a local db, cloud or they are device specific like
 * "Developer settings". It might be also useful when you want to use the preferences UI but
 * the data are not supposed to be stored at all because they are valid per session only.
 *
 * <p>Once a put method is called it is full responsibility of the data store implementation to
 * safely store the given values. Time expensive operations need to be done in the background to
 * prevent from blocking the UI. You also need to have a plan on how to serialize the data in case
 * the activity holding this object gets destroyed.
 *
 * <p>By default, all "put" methods throw {@link UnsupportedOperationException}.
 */
public abstract class PreferenceDataStore {

    /**
     * Sets a {@link String} value to the data store.
     *
     * <p>Once the value is set the data store is responsible for holding it.
     *
     * @param key the name of the preference to modify
     * @param value the new value for the preference
     * @see #getString(String, String)
     */
    public void putString(String key, @Nullable String value) {
        throw new UnsupportedOperationException("Not implemented on this data store");
    }

    /**
     * Sets a set of Strings to the data store.
     *
     * <p>Once the value is set the data store is responsible for holding it.
     *
     * @param key the name of the preference to modify
     * @param values the set of new values for the preference
     * @see #getStringSet(String, Set<String>)
     */
    public void putStringSet(String key, @Nullable Set<String> values) {
        throw new UnsupportedOperationException("Not implemented on this data store");
    }

    /**
     * Sets an {@link Integer} value to the data store.
     *
     * <p>Once the value is set the data store is responsible for holding it.
     *
     * @param key the name of the preference to modify
     * @param value the new value for the preference
     * @see #getInt(String, int)
     */
    public void putInt(String key, int value) {
        throw new UnsupportedOperationException("Not implemented on this data store");
    }

    /**
     * Sets a {@link Long} value to the data store.
     *
     * <p>Once the value is set the data store is responsible for holding it.
     *
     * @param key the name of the preference to modify
     * @param value the new value for the preference
     * @see #getLong(String, long)
     */
    public void putLong(String key, long value) {
        throw new UnsupportedOperationException("Not implemented on this data store");
    }

    /**
     * Sets a {@link Float} value to the data store.
     *
     * <p>Once the value is set the data store is responsible for holding it.
     *
     * @param key the name of the preference to modify
     * @param value the new value for the preference
     * @see #getFloat(String, float)
     */
    public void putFloat(String key, float value) {
        throw new UnsupportedOperationException("Not implemented on this data store");
    }

    /**
     * Sets a {@link Boolean} value to the data store.
     *
     * <p>Once the value is set the data store is responsible for holding it.
     *
     * @param key the name of the preference to modify
     * @param value the new value for the preference
     * @see #getBoolean(String, boolean)
     */
    public void putBoolean(String key, boolean value) {
        throw new UnsupportedOperationException("Not implemented on this data store");
    }

    /**
     * Retrieves a {@link String} value from the data store.
     *
     * @param key the name of the preference to retrieve
     * @param defValue value to return if this preference does not exist in the storage
     * @return the value from the data store or the default return value
     * @see #putString(String, String)
     */
    @Nullable
    public String getString(String key, @Nullable String defValue) {
        return defValue;
    }

    /**
     * Retrieves a set of Strings from the data store.
     *
     * @param key the name of the preference to retrieve
     * @param defValues values to return if this preference does not exist in the storage
     * @return the values from the data store or the default return values
     * @see #putStringSet(String, Set<String>)
     */
    @Nullable
    public Set<String> getStringSet(String key, @Nullable Set<String> defValues) {
        return defValues;
    }

    /**
     * Retrieves an {@link Integer} value from the data store.
     *
     * @param key the name of the preference to retrieve
     * @param defValue value to return if this preference does not exist in the storage
     * @return the value from the data store or the default return value
     * @see #putInt(String, int)
     */
    public int getInt(String key, int defValue) {
        return defValue;
    }

    /**
     * Retrieves a {@link Long} value from the data store.
     *
     * @param key the name of the preference to retrieve
     * @param defValue value to return if this preference does not exist in the storage
     * @return the value from the data store or the default return value
     * @see #putLong(String, long)
     */
    public long getLong(String key, long defValue) {
        return defValue;
    }

    /**
     * Retrieves a {@link Float} value from the data store.
     *
     * @param key the name of the preference to retrieve
     * @param defValue value to return if this preference does not exist in the storage
     * @return the value from the data store or the default return value
     * @see #putFloat(String, float)
     */
    public float getFloat(String key, float defValue) {
        return defValue;
    }

    /**
     * Retrieves a {@link Boolean} value from the data store.
     *
     * @param key the name of the preference to retrieve
     * @param defValue value to return if this preference does not exist in the storage
     * @return the value from the data store or the default return value
     * @see #getBoolean(String, boolean)
     */
    public boolean getBoolean(String key, boolean defValue) {
        return defValue;
    }
}
