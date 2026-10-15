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
    private boolean mChecked;
    private boolean mDisableDependentsState;

    public TwoStatePreference(Context context) {
        super(context);
        setDefaultValue(false);
    }

    @JsonIgnore
    public boolean isChecked() {
        android.content.SharedPreferences prefs = getSharedPreferences();
        if (prefs != null && getKey() != null) {
            return prefs.getBoolean(getKey(), mChecked);
        }
        return mChecked;
    }

    @JsonIgnore
    public void setChecked(boolean checked) {
        if (isChecked() == checked) {
            return;
        }
        if (!callChangeListener(checked)) {
            return;
        }
        mChecked = checked;
        android.content.SharedPreferences prefs = getSharedPreferences();
        if (prefs != null && getKey() != null) {
            prefs.edit().putBoolean(getKey(), checked).apply();
        }
    }

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

        final boolean checked = isChecked();
        if (checked && mSummaryOn != null) {
            return mSummaryOn;
        } else if (!checked && mSummaryOff != null) {
            return mSummaryOff;
        }

        return null;
    }

    @JsonIgnore
    public boolean getDisableDependentsState() { return mDisableDependentsState; }

    @JsonIgnore
    public void setDisableDependentsState(boolean disableDependentsState) { mDisableDependentsState = disableDependentsState; }

    /** Tachidesk specific API */
    @Override
    public String getDefaultValueType() {
        return "Boolean";
    }
}