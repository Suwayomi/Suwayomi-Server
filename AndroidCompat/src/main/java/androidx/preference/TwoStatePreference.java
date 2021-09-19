package androidx.preference;

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import android.content.Context;
import com.fasterxml.jackson.annotation.JsonIgnore;

public class TwoStatePreference extends Preference {
    // Note: remove @JsonIgnore and implement methods if any extension ever uses these methods or the variables behind them

    public TwoStatePreference(Context context) {
        super(context);
        setDefaultValue(false);
    }

    @JsonIgnore
    public boolean isChecked() { throw new RuntimeException("Stub!"); }

    @JsonIgnore
    public void setChecked(boolean checked) { throw new RuntimeException("Stub!"); }

    @JsonIgnore
    public CharSequence getSummaryOn() { throw new RuntimeException("Stub!"); }

    @JsonIgnore
    public void setSummaryOn(CharSequence summary) { throw new RuntimeException("Stub!"); }

    @JsonIgnore
    public CharSequence getSummaryOff() { throw new RuntimeException("Stub!"); }

    @JsonIgnore
    public void setSummaryOff(CharSequence summary) { throw new RuntimeException("Stub!"); }

    @JsonIgnore
    public boolean getDisableDependentsState() { throw new RuntimeException("Stub!"); }

    @JsonIgnore
    public void setDisableDependentsState(boolean disableDependentsState) { throw new RuntimeException("Stub!"); }

    /** Tachidesk specific API */
    @Override
    public String getDefaultValueType() {
        return "Boolean";
    }
}