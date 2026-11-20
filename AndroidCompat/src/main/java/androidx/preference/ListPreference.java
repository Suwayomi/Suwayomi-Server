package androidx.preference;

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import android.content.Context;
import android.text.TextUtils;
import com.fasterxml.jackson.annotation.JsonIgnore;

public class ListPreference extends Preference {
    // reference: https://android.googlesource.com/platform/frameworks/support/+/996971f962fcd554339a7cb2859cef9ca89dbcb7/preference/preference/src/main/java/androidx/preference/ListPreference.java
    // Note: remove @JsonIgnore and implement methods if any extension ever uses these methods or the variables behind them

    private CharSequence[] entries;
    private CharSequence[] entryValues;

    public ListPreference(Context context) {
        super(context);
    }

    public CharSequence[] getEntries() {
        return entries;
    }

    public void setEntries(CharSequence[] entries) {
        this.entries = entries;
    }

    public int findIndexOfValue(String value) {
        if (value != null && entryValues != null) {
            for (int i = entryValues.length - 1; i >= 0; i--) {
                if (TextUtils.equals(entryValues[i].toString(), value)) {
                    return i;
                }
            }
        }
        return -1;
    }

    public CharSequence[] getEntryValues() {
        return entryValues;
    }

    public void setEntryValues(CharSequence[] entryValues) {
        this.entryValues = entryValues;
    }

    @JsonIgnore
    public void setValueIndex(int index) { throw new RuntimeException("Stub!"); }

    @JsonIgnore
    public String getValue() { throw new RuntimeException("Stub!"); }

    @JsonIgnore
    public void setValue(String value) { throw new RuntimeException("Stub!"); }

    /** Tachidesk specific API */
    @Override
    public String getDefaultValueType() {
        return "String";
    }
}
