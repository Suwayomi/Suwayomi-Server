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
    private CharSequence mSummaryOn;
    private CharSequence mSummaryOff;

    public TwoStatePreference(Context context) {
        super(context);
        setDefaultValue(false);
    }

    @JsonIgnore
    public boolean isChecked() { throw new RuntimeException("Stub!"); }

    @JsonIgnore
    public void setChecked(boolean checked) { throw new RuntimeException("Stub!"); }

    @JsonIgnore
    public CharSequence getSummaryOn() {
        return mSummaryOn;
    }

    @JsonIgnore
    public void setSummaryOn(CharSequence summary) {
        this.mSummaryOn = summary;
    }

    @JsonIgnore
    public CharSequence getSummaryOff() {
        return mSummaryOff;
    }

    @JsonIgnore
    public void setSummaryOff(CharSequence summary) {
        this.mSummaryOff = summary;
    }

    @Override
    public CharSequence getSummary() {
        final CharSequence summary = super.getSummary();
        if (summary != null) {
            return summary;
        }

        final boolean checked = (Boolean) getCurrentValue();
        if (checked && mSummaryOn != null) {
            return mSummaryOn;
        } else if (!checked && mSummaryOff != null) {
            return mSummaryOff;
        }

        return null;
    }

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