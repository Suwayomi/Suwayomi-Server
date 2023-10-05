package androidx.preference;

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import android.content.Context;
import android.content.SharedPreferences;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.Set;

/**
 * A minimal implementation of androidx.preference.Preference
 */
public class Preference {
    // reference: https://android.googlesource.com/platform/frameworks/support/+/996971f962fcd554339a7cb2859cef9ca89dbcb7/preference/preference/src/main/java/androidx/preference/Preference.java
    // Note: `Preference` doesn't actually hold or persist the value, `OnPreferenceChangeListener` is called and it's up to the extension to persist it.

    @JsonIgnore
    protected Context context;

    private boolean isVisible;
    private String key;
    private CharSequence title;
    private CharSequence summary;
    private Object defaultValue;

    /** Tachidesk specific API */
    @JsonIgnore
    private SharedPreferences sharedPreferences;

    @JsonIgnore
    public OnPreferenceChangeListener onChangeListener;

    public Preference(Context context) {
        this.context = context;
    }

    public Context getContext() {
        return context;
    }

    public void setOnPreferenceChangeListener(OnPreferenceChangeListener onPreferenceChangeListener) {
        this.onChangeListener = onPreferenceChangeListener;
    }

    public void setOnPreferenceClickListener(OnPreferenceClickListener onPreferenceClickListener) {
        throw new RuntimeException("Stub!");
    }

    public CharSequence getTitle() {
        return title;
    }

    public void setTitle(CharSequence title) {
        this.title = title;
    }

    public CharSequence getSummary() {
        return summary;
    }

    public void setSummary(CharSequence summary) {
        this.summary = summary;
    }

    public void setEnabled(boolean enabled) {
        throw new RuntimeException("Stub!");
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public void setDefaultValue(Object defaultValue) {
        this.defaultValue = defaultValue;
    }

    public boolean callChangeListener(Object newValue) {
        return onChangeListener == null || onChangeListener.onPreferenceChange(this, newValue);
    }

    public Object getDefaultValue() {
        return defaultValue;
    }

    /** Tachidesk specific API */
    public String getDefaultValueType() {
        return defaultValue.getClass().getSimpleName();
    }

    /** Tachidesk specific API */
    public SharedPreferences getSharedPreferences() {
        return sharedPreferences;
    }

    public void setVisible(boolean visible) {
        isVisible = visible;
    }

    public boolean getVisible() {
        return isVisible;
    }

    /** Tachidesk specific API */
    public void setSharedPreferences(SharedPreferences sharedPreferences) {
        this.sharedPreferences = sharedPreferences;
    }

    public interface OnPreferenceChangeListener {
        boolean onPreferenceChange(Preference preference, Object newValue);
    }

    public interface OnPreferenceClickListener {
        boolean onPreferenceClick(Preference preference);
    }

    /** Tachidesk specific API */
    @SuppressWarnings("unchecked")
    public Object getCurrentValue() {
        switch (getDefaultValueType()) {
            case "String":
                return sharedPreferences.getString(key, (String)defaultValue);
            case "Boolean":
                return sharedPreferences.getBoolean(key, (Boolean)defaultValue);
            case "Set<String>":
                return sharedPreferences.getStringSet(key, (Set<String>)defaultValue);
            default:
                throw new RuntimeException("Unsupported type");
        }
    }

    /** Tachidesk specific API */
    @SuppressWarnings("unchecked")
    public void saveNewValue(Object value) {
        switch (getDefaultValueType()) {
            case "String":
                sharedPreferences.edit().putString(key, (String)value).apply();
                break;
            case "Boolean":
                sharedPreferences.edit().putBoolean(key, (Boolean)value).apply();
                break;
            case "Set<String>":
                sharedPreferences.edit().putStringSet(key, (Set<String>)value).apply();
                break;
            default:
                throw new RuntimeException("Unsupported type");
        }
    }
}
