package androidx.preference;

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import android.content.Context;
import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.Set;

public class MultiSelectListPreference extends DialogPreference {
    // Note: remove @JsonIgnore and implement methods if any extension ever uses these methods or the variables behind them

    public MultiSelectListPreference(Context context) { super(context); }

    @JsonIgnore
    public void setEntries(CharSequence[] entries) { throw new RuntimeException("Stub!"); }

    @JsonIgnore
    public CharSequence[] getEntries() { throw new RuntimeException("Stub!"); }

    @JsonIgnore
    public void setEntryValues(CharSequence[] entryValues) { throw new RuntimeException("Stub!"); }

    @JsonIgnore
    public CharSequence[] getEntryValues() { throw new RuntimeException("Stub!"); }

    @JsonIgnore
    public void setValues(Set<String> values) { throw new RuntimeException("Stub!"); }

    @JsonIgnore
    public Set<String> getValues() { throw new RuntimeException("Stub!"); }

    public int findIndexOfValue(String value) { throw new RuntimeException("Stub!"); }

    /** Tachidesk specific API */
    @Override
    public String getDefaultValueType() {
        return "Set";
    }
}