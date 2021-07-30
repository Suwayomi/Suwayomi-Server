package androidx.preference;

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import android.content.Context;
import com.fasterxml.jackson.annotation.JsonIgnore;

/** A minimal implementation of androidx.preference.Preference */
public class Preference {
    // reference: https://android.googlesource.com/platform/frameworks/support/+/996971f962fcd554339a7cb2859cef9ca89dbcb7/preference/preference/src/main/java/androidx/preference/Preference.java
    // Note: `Preference` doesn't actually hold or persist the value, `OnPreferenceChangeListener` is called and it's up to the extension to persist it.

    @JsonIgnore
    protected Context context;

    private String key;
    private CharSequence title;
    private Object defaultValue;

    @JsonIgnore
    public OnPreferenceChangeListener onChangeListener;

    public Preference(Context context) {
        this.context = context;
    }

    public Context getContext() {
        return context;
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

    public CharSequence getTitle() {
        return title;
    }

    public void setOnPreferenceChangeListener(OnPreferenceChangeListener onPreferenceChangeListener) {
        this.onChangeListener = onPreferenceChangeListener;
    }

    public boolean callChangeListener(Object newValue) {
        return onChangeListener == null || onChangeListener.onPreferenceChange(this, newValue);
    }

    public Object getDefaultValue() {
        return defaultValue;
    }

    public interface OnPreferenceChangeListener {
        boolean onPreferenceChange(Preference preference, Object newValue);
    }

}
