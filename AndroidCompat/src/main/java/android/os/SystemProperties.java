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
package android.os;

import kotlin.NotImplementedError;
import xyz.nulldev.androidcompat.config.SystemConfigModule;
import xyz.nulldev.ts.config.ConfigManager;
import xyz.nulldev.ts.config.GlobalConfigManager;

/**
 * Gives access to the system properties store.  The system properties
 * store contains a list of string key-value pairs.
 *
 * {@hide}
 */
public class SystemProperties {
    private static ConfigManager configManager = GlobalConfigManager.INSTANCE;
    private static SystemConfigModule configModule = configManager.module(SystemConfigModule.class);

    public static final int PROP_VALUE_MAX = 91;

    private static String native_get(String key) {
        return configModule.getStringProperty(key);
    }
    private static String native_get(String key, String def) {
        if(!configModule.hasProperty(key))
            return def;
        else
            return native_get(key);
    }
    private static int native_get_int(String key, int def) {
        if(configModule.hasProperty(key))
            return def;
        else
            return configModule.getIntProperty(key);
    }
    private static long native_get_long(String key, long def) {
        if(configModule.hasProperty(key))
            return def;
        else
            return configModule.getLongProperty(key);
    }
    private static boolean native_get_boolean(String key, boolean def) {
        if(configModule.hasProperty(key))
            return def;
        else
            return configModule.getBooleanProperty(key);
    }
    private static void native_set(String key, String def) {
        throw new NotImplementedError("TODO");
    }

    /**
     * Get the value for the given key.
     * @return an empty string if the key isn't found
     */
    public static String get(String key) {
        return native_get(key);
    }
    /**
     * Get the value for the given key.
     * @return if the key isn't found, return def if it isn't null, or an empty string otherwise
     */
    public static String get(String key, String def) {
        return native_get(key, def);
    }
    /**
     * Get the value for the given key, and return as an integer.
     * @param key the key to lookup
     * @param def a default value to return
     * @return the key parsed as an integer, or def if the key isn't found or
     *         cannot be parsed
     */
    public static int getInt(String key, int def) {
        return native_get_int(key, def);
    }
    /**
     * Get the value for the given key, and return as a long.
     * @param key the key to lookup
     * @param def a default value to return
     * @return the key parsed as a long, or def if the key isn't found or
     *         cannot be parsed
     */
    public static long getLong(String key, long def) {
        return native_get_long(key, def);
    }
    /**
     * Get the value for the given key, returned as a boolean.
     * Values 'n', 'no', '0', 'false' or 'off' are considered false.
     * Values 'y', 'yes', '1', 'true' or 'on' are considered true.
     * (case sensitive).
     * If the key does not exist, or has any other value, then the default
     * result is returned.
     * @param key the key to lookup
     * @param def a default value to return
     * @return the key parsed as a boolean, or def if the key isn't found or is
     *         not able to be parsed as a boolean.
     */
    public static boolean getBoolean(String key, boolean def) {
        return native_get_boolean(key, def);
    }
    /**
     * Set the value for the given key.
     * @throws IllegalArgumentException if the value exceeds 92 characters
     */
    public static void set(String key, String val) {
        if (val != null && val.length() > PROP_VALUE_MAX) {
            throw new IllegalArgumentException("val.length > " +
                PROP_VALUE_MAX);
        }
        native_set(key, val);
    }
}