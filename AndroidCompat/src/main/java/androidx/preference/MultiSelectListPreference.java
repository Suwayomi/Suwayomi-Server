package androidx.preference;

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import android.content.Context;
import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.HashSet;
import java.util.Set;

public class MultiSelectListPreference extends DialogPreference {
    // reference: https://android.googlesource.com/platform/frameworks/support/+/996971f962fcd554339a7cb2859cef9ca89dbcb7/preference/preference/src/main/java/androidx/preference/MultiSelectListPreference.java
    // Note: remove @JsonIgnore and implement methods if any extension ever uses these methods or the variables behind them

    private CharSequence[] entries;
    private CharSequence[] entryValues;
    private Set<String> mValues = new HashSet<>();

    public MultiSelectListPreference(Context context) {
        super(context);
    }

    public void setEntries(CharSequence[] entries) {
        this.entries = entries;
    }

    public CharSequence[] getEntries() {
        return entries;
    }

    public void setEntryValues(CharSequence[] entryValues) {
        this.entryValues = entryValues;
    }

    public CharSequence[] getEntryValues() {
        return entryValues;
    }

    @JsonIgnore
    public void setValues(Set<String> values) {
        if (values == null) {
            values = new HashSet<>();
        }
        if (!callChangeListener(values)) {
            return;
        }
        mValues = new HashSet<>(values);
        saveNewValue(mValues);
    }

    @JsonIgnore
    public Set<String> getValues() {
        return mValues;
    }

    public int findIndexOfValue(String value) {
        if (value != null && entryValues != null) {
            for (int i = entryValues.length - 1; i >= 0; i--) {
                if (entryValues[i].toString().equals(value)) {
                    return i;
                }
            }
        }
        return -1;
    }

    /** Tachidesk specific API */
    @Override
    public String getDefaultValueType() {
        return "Set<String>";
    }
}